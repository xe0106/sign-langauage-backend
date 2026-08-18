from __future__ import annotations

import argparse
import json
from pathlib import Path

from mindvoice_ai.dataset.manifest import load_manifest
from mindvoice_ai.preprocessing.video import extract_video, save_sequence


def preprocess_manifest(
    manifest_path: Path,
    output_root: Path,
    model_path: Path,
    *,
    target_fps: float = 30.0,
    overwrite: bool = False,
) -> list[dict]:
    entries = load_manifest(manifest_path)
    output_root.mkdir(parents=True, exist_ok=True)
    records: list[dict] = []

    for entry in entries:
        output_path = (
            output_root
            / entry.split
            / entry.stable_key
            / entry.signer_id
            / f"{entry.sample_id}.npz"
        )
        if output_path.exists() and not overwrite:
            raise FileExistsError(
                f"output already exists (use --overwrite): {output_path}"
            )

        features, presence, timestamps_ms, metadata = extract_video(
            entry.input_path, model_path, target_fps=target_fps
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
        records.append(
            {
                **metadata,
                "processedPath": output_path.relative_to(output_root).as_posix(),
            }
        )

    index_path = output_root / "dataset-index.jsonl"
    with index_path.open("w", encoding="utf-8", newline="\n") as destination:
        for record in records:
            destination.write(json.dumps(record, ensure_ascii=False) + "\n")
    return records


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Preprocess every video in a signer-safe dataset manifest."
    )
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--output-root", required=True, type=Path)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--target-fps", type=float, default=30.0)
    parser.add_argument("--overwrite", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    records = preprocess_manifest(
        args.manifest,
        args.output_root,
        args.model,
        target_fps=args.target_fps,
        overwrite=args.overwrite,
    )
    print(
        json.dumps(
            {
                "processedSamples": len(records),
                "index": str(args.output_root / "dataset-index.jsonl"),
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
