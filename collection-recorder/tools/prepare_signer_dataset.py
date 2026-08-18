"""Prepare one signer's recordings so collection can be processed incrementally."""

from __future__ import annotations

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path

import numpy as np

from mindvoice_ai.dataset.manifest import load_manifest
from mindvoice_ai.dataset.sequences import build_training_dataset
from mindvoice_ai.preprocessing.video import extract_video, save_sequence


VALID_SPLITS = ("train", "validation", "test")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Preprocess and package one signer's videos from a dataset manifest."
    )
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--signer-id", required=True)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--output-root", required=True, type=Path)
    parser.add_argument(
        "--split",
        choices=VALID_SPLITS,
        help="Override the signer's split for an isolated experiment.",
    )
    parser.add_argument("--target-fps", type=float, default=30.0)
    parser.add_argument(
        "--max-videos",
        type=int,
        help="Process at most this many pending videos, then save resumable progress.",
    )
    parser.add_argument(
        "--max-per-class",
        type=int,
        help="Use at most this many recordings per class for a compact experiment.",
    )
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def write_signer_manifest(
    manifest_path: Path,
    signer_id: str,
    output_path: Path,
    split_override: str | None,
    max_per_class: int | None,
) -> int:
    entries = load_manifest(manifest_path)
    selected = [entry for entry in entries if entry.signer_id == signer_id]
    if not selected:
        raise ValueError(f"no manifest samples found for signer {signer_id}")
    if max_per_class is not None:
        if max_per_class <= 0:
            raise ValueError("max-per-class must be positive")
        selected_by_class: dict[str, list[object]] = defaultdict(list)
        for entry in selected:
            if len(selected_by_class[entry.stable_key]) < max_per_class:
                selected_by_class[entry.stable_key].append(entry)
        selected = [entry for stable_key in sorted(selected_by_class) for entry in selected_by_class[stable_key]]

    output_path.parent.mkdir(parents=True, exist_ok=True)
    fields = [
        "sample_id",
        "input_path",
        "class_id",
        "label",
        "stable_key",
        "signer_id",
        "split",
    ]
    with output_path.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(destination, fieldnames=fields)
        writer.writeheader()
        for entry in selected:
            writer.writerow(
                {
                    "sample_id": entry.sample_id,
                    "input_path": entry.input_path,
                    "class_id": entry.class_id,
                    "label": entry.label,
                    "stable_key": entry.stable_key,
                    "signer_id": entry.signer_id,
                    "split": split_override or entry.split,
                }
            )
    return len(selected)


def processed_path(output_root: Path, entry: object) -> Path:
    return (
        output_root
        / entry.split
        / entry.stable_key
        / entry.signer_id
        / f"{entry.sample_id}.npz"
    )


def load_processed_record(output_root: Path, entry: object) -> dict:
    path = processed_path(output_root, entry)
    with np.load(path, allow_pickle=False) as archive:
        metadata = json.loads(str(archive["metadata"].item()))
    return {
        **metadata,
        "processedPath": path.relative_to(output_root).as_posix(),
    }


def write_index(output_root: Path, entries: list[object]) -> int:
    records = [
        load_processed_record(output_root, entry)
        for entry in entries
        if processed_path(output_root, entry).is_file()
    ]
    index_path = output_root / "dataset-index.jsonl"
    index_path.parent.mkdir(parents=True, exist_ok=True)
    with index_path.open("w", encoding="utf-8", newline="\n") as destination:
        for record in records:
            destination.write(json.dumps(record, ensure_ascii=False) + "\n")
    return len(records)


def main() -> None:
    args = parse_args()
    signer_id = args.signer_id.upper()
    signer_root = args.output_root / "signers" / signer_id
    signer_manifest = args.output_root / "manifests" / f"{signer_id}.csv"
    count = write_signer_manifest(
        args.manifest, signer_id, signer_manifest, args.split, args.max_per_class
    )
    processed_root = signer_root / "processed"
    entries = load_manifest(signer_manifest)
    if args.max_videos is not None and args.max_videos <= 0:
        raise ValueError("max-videos must be positive")

    processed_now = 0
    for entry in entries:
        output_path = processed_path(processed_root, entry)
        if output_path.is_file() and not args.overwrite:
            continue
        if args.max_videos is not None and processed_now >= args.max_videos:
            break
        features, presence, timestamps_ms, metadata = extract_video(
            entry.input_path, args.model, target_fps=args.target_fps
        )
        metadata.update(
            {
                "sampleId": entry.sample_id,
                "classId": entry.class_id,
                "label": entry.label,
                "stableKey": entry.stable_key,
                "signerId": entry.signer_id,
                "split": entry.split,
            }
        )
        save_sequence(output_path, features, presence, timestamps_ms, metadata)
        processed_now += 1

    completed = write_index(processed_root, entries)
    result = {
        "signerId": signer_id,
        "split": args.split or "from_manifest",
        "manifestSamples": count,
        "processedNow": processed_now,
        "processedTotal": completed,
        "remaining": count - completed,
    }
    if completed == count:
        result.update(
            build_training_dataset(processed_root / "dataset-index.jsonl", signer_root / "dataset.npz")
        )
    print(result)


if __name__ == "__main__":
    main()
