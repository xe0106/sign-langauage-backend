"""Merge incrementally prepared signer datasets into one trainable dataset."""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np

from mindvoice_ai.training.data import TrainingDataset, validate_training_dataset


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Merge per-signer dataset.npz files for final training."
    )
    parser.add_argument("--input-root", required=True, type=Path)
    parser.add_argument("--signers", nargs="+", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--allow-missing-test", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    signer_ids = [signer_id.upper() for signer_id in args.signers]
    if len(set(signer_ids)) != len(signer_ids):
        raise ValueError("signers must not contain duplicates")

    datasets = []
    required = {
        "features",
        "labels",
        "splits",
        "signer_ids",
        "sample_ids",
        "label_keys",
        "label_names",
    }
    for signer_id in signer_ids:
        path = args.input_root / "signers" / signer_id / "dataset.npz"
        with np.load(path, allow_pickle=False) as archive:
            missing = required - set(archive.files)
            if missing:
                raise ValueError(f"dataset for {signer_id} is missing arrays: {sorted(missing)}")
            datasets.append(
                TrainingDataset(
                    features=archive["features"].astype(np.float32),
                    labels=archive["labels"].astype(np.int64),
                    splits=archive["splits"].astype(str),
                    signer_ids=archive["signer_ids"].astype(str),
                    sample_ids=archive["sample_ids"].astype(str),
                    label_keys=tuple(archive["label_keys"].astype(str)),
                    label_names=tuple(archive["label_names"].astype(str)),
                )
            )
    reference = datasets[0]
    for signer_id, dataset in zip(signer_ids[1:], datasets[1:], strict=True):
        if dataset.label_keys != reference.label_keys or dataset.label_names != reference.label_names:
            raise ValueError(f"label mapping differs for signer {signer_id}")

    merged = {
        "features": np.concatenate([dataset.features for dataset in datasets]),
        "labels": np.concatenate([dataset.labels for dataset in datasets]),
        "splits": np.concatenate([dataset.splits for dataset in datasets]),
        "signer_ids": np.concatenate([dataset.signer_ids for dataset in datasets]),
        "sample_ids": np.concatenate([dataset.sample_ids for dataset in datasets]),
        "label_keys": np.asarray(reference.label_keys),
        "label_names": np.asarray(reference.label_names),
    }
    merged_dataset = TrainingDataset(
        features=merged["features"],
        labels=merged["labels"],
        splits=merged["splits"],
        signer_ids=merged["signer_ids"],
        sample_ids=merged["sample_ids"],
        label_keys=reference.label_keys,
        label_names=reference.label_names,
    )
    validate_training_dataset(
        merged_dataset, require_test_split=not args.allow_missing_test
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(args.output, **merged)
    print(
        {
            "samples": int(len(merged["labels"])),
            "signers": signer_ids,
            "shape": list(merged["features"].shape),
        }
    )


if __name__ == "__main__":
    main()
