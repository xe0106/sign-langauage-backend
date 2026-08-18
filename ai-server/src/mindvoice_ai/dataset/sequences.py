from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import numpy as np

from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH


def resample_features(features: np.ndarray, length: int = SEQUENCE_LENGTH) -> np.ndarray:
    if features.ndim != 2 or features.shape[1] != FEATURE_DIMENSION:
        raise ValueError(f"features must have shape (frames, {FEATURE_DIMENSION})")
    if features.shape[0] == 0:
        raise ValueError("features contain no frames")
    if length <= 0:
        raise ValueError("length must be positive")
    if features.shape[0] == length:
        return features.astype(np.float32, copy=True)
    if features.shape[0] == 1:
        return np.repeat(features.astype(np.float32), length, axis=0)

    source_positions = np.arange(features.shape[0], dtype=np.float32)
    target_positions = np.linspace(0, features.shape[0] - 1, length, dtype=np.float32)
    result = np.empty((length, FEATURE_DIMENSION), dtype=np.float32)
    for feature_index in range(FEATURE_DIMENSION):
        result[:, feature_index] = np.interp(
            target_positions, source_positions, features[:, feature_index]
        )
    return result


def load_index(index_path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with index_path.open(encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            if line.strip():
                try:
                    records.append(json.loads(line))
                except json.JSONDecodeError as error:
                    raise ValueError(f"invalid index JSON on line {line_number}") from error
    if not records:
        raise ValueError("dataset index contains no samples")
    return records


def build_training_dataset(
    index_path: Path,
    output_path: Path,
    *,
    sequence_length: int = SEQUENCE_LENGTH,
) -> dict[str, Any]:
    records = load_index(index_path)
    signer_splits: dict[str, str] = {}
    class_definitions: dict[int, tuple[str, str]] = {}
    stable_key_classes: dict[str, int] = {}
    for record in records:
        class_id = record["classId"]
        if not isinstance(class_id, int) or class_id < 0:
            raise ValueError(f"invalid classId for sample {record.get('sampleId')}")
        definition = (record["stableKey"], record["label"])
        previous_definition = class_definitions.setdefault(class_id, definition)
        if previous_definition != definition:
            raise ValueError(f"classId {class_id} has conflicting definitions")
        previous_class_id = stable_key_classes.setdefault(record["stableKey"], class_id)
        if previous_class_id != class_id:
            raise ValueError(f"stableKey {record['stableKey']} maps to multiple class IDs")

    expected_class_ids = list(range(len(class_definitions)))
    if sorted(class_definitions) != expected_class_ids:
        raise ValueError(
            "class IDs must be contiguous and start at 0; "
            f"found {sorted(class_definitions)}"
        )
    label_keys = [class_definitions[index][0] for index in expected_class_ids]
    label_names = [class_definitions[index][1] for index in expected_class_ids]

    sequences: list[np.ndarray] = []
    labels: list[int] = []
    splits: list[str] = []
    signer_ids: list[str] = []
    sample_ids: list[str] = []

    for record in records:
        signer_id = record["signerId"]
        split = record["split"]
        previous_split = signer_splits.setdefault(signer_id, split)
        if previous_split != split:
            raise ValueError(f"signer {signer_id} appears in multiple splits")

        processed_path = index_path.parent / record["processedPath"]
        with np.load(processed_path) as archive:
            features = archive["features"].astype(np.float32)
        sequences.append(resample_features(features, sequence_length))
        labels.append(record["classId"])
        splits.append(split)
        signer_ids.append(signer_id)
        sample_ids.append(record["sampleId"])

    output_path.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(
        output_path,
        features=np.stack(sequences),
        labels=np.asarray(labels, dtype=np.int64),
        splits=np.asarray(splits),
        signer_ids=np.asarray(signer_ids),
        sample_ids=np.asarray(sample_ids),
        label_keys=np.asarray(label_keys),
        label_names=np.asarray(label_names),
    )
    return {
        "samples": len(sequences),
        "shape": [len(sequences), sequence_length, FEATURE_DIMENSION],
        "labels": {key: index for index, key in enumerate(label_keys)},
        "splitCounts": {
            name: splits.count(name) for name in ("train", "validation", "test")
        },
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build fixed-length model training arrays from processed NPZ files."
    )
    parser.add_argument("--index", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--sequence-length", type=int, default=SEQUENCE_LENGTH)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    summary = build_training_dataset(
        args.index, args.output, sequence_length=args.sequence_length
    )
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
