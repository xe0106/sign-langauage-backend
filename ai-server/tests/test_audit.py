import csv
import json
from pathlib import Path

import numpy as np

from mindvoice_ai.dataset.audit import VideoInfo, audit_dataset


FIELDS = [
    "sample_id",
    "input_path",
    "class_id",
    "label",
    "stable_key",
    "signer_id",
    "split",
]


def write_manifest(path: Path, rows: list[list[object]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.writer(destination)
        writer.writerow(FIELDS)
        writer.writerows(rows)
    for row in rows:
        (path.parent / str(row[1])).touch()


def inspector(path: Path) -> VideoInfo:
    return VideoInfo(1280, 720, 30.0, 90, 3.0, 100, path.stem)


def complete_rows() -> list[list[object]]:
    return [
        ["s1-hello", "s1-hello.mp4", 0, "안녕하세요", "HELLO", "S001", "train"],
        ["s1-none", "s1-none.mp4", 1, "비수어", "NO_SIGN", "S001", "train"],
        ["s2-hello", "s2-hello.mp4", 0, "안녕하세요", "HELLO", "S002", "test"],
        ["s2-none", "s2-none.mp4", 1, "비수어", "NO_SIGN", "S002", "test"],
    ]


def test_audit_passes_complete_balanced_manifest(tmp_path: Path) -> None:
    manifest = tmp_path / "manifest.csv"
    write_manifest(manifest, complete_rows())

    report = audit_dataset(
        manifest, expected_repetitions=1, video_inspector=inspector
    )

    assert report["passed"] is True
    assert report["summary"]["samples"] == 4
    assert report["summary"]["splitSigners"] == {"test": 1, "train": 1}


def test_audit_reports_missing_repetition_and_duplicate(tmp_path: Path) -> None:
    manifest = tmp_path / "manifest.csv"
    rows = complete_rows()[:-1]
    write_manifest(manifest, rows)

    def duplicate_inspector(path: Path) -> VideoInfo:
        file_hash = "duplicate" if "hello" in path.stem else path.stem
        return VideoInfo(1280, 720, 30.0, 90, 3.0, 100, file_hash)

    report = audit_dataset(
        manifest, expected_repetitions=1, video_inspector=duplicate_inspector
    )
    codes = {issue["code"] for issue in report["issues"]}

    assert report["passed"] is False
    assert "REPETITION_COUNT_MISMATCH" in codes
    assert "DUPLICATE_VIDEO" in codes


def test_audit_adds_mediapipe_presence_rates(tmp_path: Path) -> None:
    manifest = tmp_path / "manifest.csv"
    rows = complete_rows()
    write_manifest(manifest, rows)
    processed_root = tmp_path / "processed"
    processed_root.mkdir()
    records = []
    for row in rows:
        sample_id = str(row[0])
        relative = Path(f"{sample_id}.npz")
        presence = np.ones((3, 3), dtype=np.bool_)
        np.savez_compressed(processed_root / relative, presence=presence)
        records.append({"sampleId": sample_id, "processedPath": relative.as_posix()})
    index = processed_root / "dataset-index.jsonl"
    index.write_text(
        "".join(json.dumps(record) + "\n" for record in records), encoding="utf-8"
    )

    report = audit_dataset(
        manifest,
        processed_index=index,
        expected_repetitions=1,
        video_inspector=inspector,
    )

    assert report["videos"][0]["presenceRates"]["pose"] == 1.0
