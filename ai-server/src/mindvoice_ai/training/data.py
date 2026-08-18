from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np

from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH

VALID_SPLITS = ("train", "validation", "test")


@dataclass(frozen=True)
class TrainingDataset:
    features: np.ndarray
    labels: np.ndarray
    splits: np.ndarray
    signer_ids: np.ndarray
    sample_ids: np.ndarray
    label_keys: tuple[str, ...]
    label_names: tuple[str, ...]

    def split(self, name: str) -> tuple[np.ndarray, np.ndarray]:
        if name not in VALID_SPLITS:
            raise ValueError(f"unknown split: {name}")
        mask = self.splits == name
        return self.features[mask], self.labels[mask]


def load_training_dataset(
    path: Path, *, require_test_split: bool = True
) -> TrainingDataset:
    if not path.is_file():
        raise FileNotFoundError(f"training dataset not found: {path}")

    required = {
        "features",
        "labels",
        "splits",
        "signer_ids",
        "sample_ids",
        "label_keys",
        "label_names",
    }
    with np.load(path, allow_pickle=False) as archive:
        missing = required - set(archive.files)
        if missing:
            raise ValueError(f"training dataset is missing arrays: {sorted(missing)}")
        dataset = TrainingDataset(
            features=archive["features"].astype(np.float32),
            labels=archive["labels"].astype(np.int64),
            splits=archive["splits"].astype(str),
            signer_ids=archive["signer_ids"].astype(str),
            sample_ids=archive["sample_ids"].astype(str),
            label_keys=tuple(archive["label_keys"].astype(str)),
            label_names=tuple(archive["label_names"].astype(str)),
        )
    validate_training_dataset(dataset, require_test_split=require_test_split)
    return dataset


def validate_training_dataset(
    dataset: TrainingDataset, *, require_test_split: bool = True
) -> None:
    sample_count = dataset.features.shape[0]
    if dataset.features.ndim != 3 or dataset.features.shape[1:] != (
        SEQUENCE_LENGTH,
        FEATURE_DIMENSION,
    ):
        raise ValueError(
            f"features must have shape (samples, {SEQUENCE_LENGTH}, {FEATURE_DIMENSION})"
        )
    for name, values in (
        ("labels", dataset.labels),
        ("splits", dataset.splits),
        ("signer_ids", dataset.signer_ids),
        ("sample_ids", dataset.sample_ids),
    ):
        if values.ndim != 1 or len(values) != sample_count:
            raise ValueError(f"{name} must contain one value per sample")
    if not np.isfinite(dataset.features).all():
        raise ValueError("features contain non-finite values")
    if not dataset.label_keys or len(dataset.label_keys) != len(dataset.label_names):
        raise ValueError("label key and name mappings must be non-empty and equal length")
    if len(set(dataset.label_keys)) != len(dataset.label_keys):
        raise ValueError("label keys must be unique")
    if np.any(dataset.labels < 0) or np.any(dataset.labels >= len(dataset.label_keys)):
        raise ValueError("labels contain an out-of-range class ID")
    unknown_splits = set(dataset.splits) - set(VALID_SPLITS)
    if unknown_splits:
        raise ValueError(f"unknown dataset splits: {sorted(unknown_splits)}")
    required_splits = VALID_SPLITS if require_test_split else ("train", "validation")
    missing_splits = [name for name in required_splits if not np.any(dataset.splits == name)]
    if missing_splits:
        raise ValueError(f"dataset has no samples for splits: {missing_splits}")
    expected_classes = set(range(len(dataset.label_keys)))
    for split in required_splits:
        present_classes = set(dataset.labels[dataset.splits == split].tolist())
        missing_classes = sorted(expected_classes - present_classes)
        if missing_classes:
            raise ValueError(
                f"split {split} has no samples for class IDs: {missing_classes}"
            )
    if len(set(dataset.sample_ids)) != sample_count:
        raise ValueError("sample IDs must be unique")

    signer_splits: dict[str, str] = {}
    for signer_id, split in zip(dataset.signer_ids, dataset.splits, strict=True):
        previous = signer_splits.setdefault(signer_id, split)
        if previous != split:
            raise ValueError(f"signer {signer_id} appears in multiple splits")
