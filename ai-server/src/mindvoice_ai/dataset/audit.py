from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Callable

import cv2
import numpy as np

from mindvoice_ai.dataset.manifest import ManifestEntry, load_manifest


@dataclass(frozen=True)
class VideoInfo:
    width: int
    height: int
    fps: float
    frame_count: int
    duration_seconds: float
    bytes: int
    sha256: str


@dataclass(frozen=True)
class AuditIssue:
    severity: str
    code: str
    message: str
    sample_id: str | None = None


def _file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inspect_video(path: Path) -> VideoInfo:
    capture = cv2.VideoCapture(str(path))
    if not capture.isOpened():
        raise ValueError("video cannot be opened")
    try:
        width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
        fps = float(capture.get(cv2.CAP_PROP_FPS))
        frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT))
    finally:
        capture.release()
    if width <= 0 or height <= 0 or not np.isfinite(fps) or fps <= 0 or frame_count <= 0:
        raise ValueError("video has invalid stream metadata")
    return VideoInfo(
        width=width,
        height=height,
        fps=fps,
        frame_count=frame_count,
        duration_seconds=frame_count / fps,
        bytes=path.stat().st_size,
        sha256=_file_sha256(path),
    )


def _processed_presence(
    index_path: Path,
) -> dict[str, tuple[float, float, float, float]]:
    root = index_path.parent
    results: dict[str, tuple[float, float, float, float]] = {}
    with index_path.open(encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
                with np.load(root / record["processedPath"]) as archive:
                    presence = archive["presence"].astype(bool)
            except (json.JSONDecodeError, KeyError, OSError, ValueError) as error:
                raise ValueError(
                    f"invalid processed index entry on line {line_number}"
                ) from error
            if presence.ndim != 2 or presence.shape[1] != 3 or len(presence) == 0:
                raise ValueError(
                    f"invalid presence array for sample {record.get('sampleId')}"
                )
            results[record["sampleId"]] = (
                float(presence[:, 0].mean()),
                float(presence[:, 1].mean()),
                float(presence[:, 2].mean()),
                float(np.logical_or(presence[:, 1], presence[:, 2]).mean()),
            )
    return results


def audit_dataset(
    manifest_path: Path,
    *,
    processed_index: Path | None = None,
    expected_repetitions: int = 20,
    min_duration: float = 2.0,
    max_duration: float = 6.0,
    min_width: int = 640,
    min_height: int = 360,
    min_pose_rate: float = 0.9,
    min_any_hand_rate: float = 0.25,
    video_inspector: Callable[[Path], VideoInfo] = inspect_video,
) -> dict:
    entries = load_manifest(manifest_path)
    issues: list[AuditIssue] = []
    video_rows: list[dict] = []
    hashes: dict[str, list[ManifestEntry]] = defaultdict(list)

    for entry in entries:
        try:
            info = video_inspector(entry.input_path)
        except ValueError as error:
            issues.append(AuditIssue("error", "UNREADABLE_VIDEO", str(error), entry.sample_id))
            continue
        hashes[info.sha256].append(entry)
        if not min_duration <= info.duration_seconds <= max_duration:
            issues.append(
                AuditIssue(
                    "warning",
                    "DURATION_OUT_OF_RANGE",
                    f"duration {info.duration_seconds:.2f}s is outside {min_duration:.2f}-{max_duration:.2f}s",
                    entry.sample_id,
                )
            )
        if info.width < min_width or info.height < min_height:
            issues.append(
                AuditIssue(
                    "warning",
                    "LOW_RESOLUTION",
                    f"resolution {info.width}x{info.height} is below {min_width}x{min_height}",
                    entry.sample_id,
                )
            )
        if abs(info.fps - 30.0) > 1.0:
            issues.append(
                AuditIssue(
                    "warning",
                    "NON_STANDARD_FPS",
                    f"fps {info.fps:.3f} differs from the 30fps collection target",
                    entry.sample_id,
                )
            )
        video_rows.append(
            {
                "sampleId": entry.sample_id,
                "stableKey": entry.stable_key,
                "signerId": entry.signer_id,
                "split": entry.split,
                **asdict(info),
            }
        )

    for duplicate_entries in hashes.values():
        if len(duplicate_entries) > 1:
            sample_ids = [entry.sample_id for entry in duplicate_entries]
            issues.append(
                AuditIssue(
                    "error",
                    "DUPLICATE_VIDEO",
                    f"identical video content: {sample_ids}",
                )
            )

    class_keys = sorted({entry.stable_key for entry in entries})
    signer_ids = sorted({entry.signer_id for entry in entries})
    repetition_counts = Counter((entry.signer_id, entry.stable_key) for entry in entries)
    for signer_id in signer_ids:
        for stable_key in class_keys:
            count = repetition_counts[(signer_id, stable_key)]
            if count != expected_repetitions:
                issues.append(
                    AuditIssue(
                        "error",
                        "REPETITION_COUNT_MISMATCH",
                        f"{signer_id}/{stable_key} has {count} samples; expected {expected_repetitions}",
                    )
                )

    presence_by_sample = (
        _processed_presence(processed_index) if processed_index is not None else {}
    )
    if processed_index is not None:
        for entry in entries:
            rates = presence_by_sample.get(entry.sample_id)
            if rates is None:
                issues.append(
                    AuditIssue(
                        "error",
                        "MISSING_PROCESSED_SAMPLE",
                        "sample is absent from the processed dataset index",
                        entry.sample_id,
                    )
                )
                continue
            pose_rate, left_rate, right_rate, any_hand_rate = rates
            if pose_rate < min_pose_rate:
                issues.append(
                    AuditIssue(
                        "warning",
                        "LOW_POSE_DETECTION",
                        f"pose detection rate is {pose_rate:.1%}",
                        entry.sample_id,
                    )
                )
            if any_hand_rate < min_any_hand_rate:
                issues.append(
                    AuditIssue(
                        "warning",
                        "LOW_HAND_DETECTION",
                        f"any-hand detection rate is {any_hand_rate:.1%}",
                        entry.sample_id,
                    )
                )
            for row in video_rows:
                if row["sampleId"] == entry.sample_id:
                    row["presenceRates"] = {
                        "pose": pose_rate,
                        "leftHand": left_rate,
                        "rightHand": right_rate,
                        "anyHand": any_hand_rate,
                    }
                    break

    split_counts = Counter(entry.split for entry in entries)
    signer_split_counts = Counter(
        next(entry.split for entry in entries if entry.signer_id == signer_id)
        for signer_id in signer_ids
    )
    issue_counts = Counter(issue.severity for issue in issues)
    return {
        "passed": issue_counts["error"] == 0,
        "summary": {
            "samples": len(entries),
            "signers": len(signer_ids),
            "classes": len(class_keys),
            "splitSamples": dict(sorted(split_counts.items())),
            "splitSigners": dict(sorted(signer_split_counts.items())),
            "errors": issue_counts["error"],
            "warnings": issue_counts["warning"],
        },
        "issues": [asdict(issue) for issue in issues],
        "videos": video_rows,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Audit collected sign-language videos.")
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--processed-index", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--expected-repetitions", type=int, default=20)
    parser.add_argument("--min-duration", type=float, default=2.0)
    parser.add_argument("--max-duration", type=float, default=6.0)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    report = audit_dataset(
        args.manifest,
        processed_index=args.processed_index,
        expected_repetitions=args.expected_repetitions,
        min_duration=args.min_duration,
        max_duration=args.max_duration,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(report["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
