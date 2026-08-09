from __future__ import annotations

import argparse
import json
import random
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import numpy as np
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score
from sklearn.utils.class_weight import compute_class_weight

from mindvoice_ai.training.data import TrainingDataset, load_training_dataset
from mindvoice_ai.training.model import build_baseline_model


@dataclass(frozen=True)
class SignerFold:
    test_signer: str
    validation_signer: str
    train_signers: tuple[str, ...]


def make_signer_folds(signer_ids: np.ndarray) -> list[SignerFold]:
    signers = sorted(set(signer_ids.astype(str)))
    if len(signers) < 3:
        raise ValueError("signer cross-validation requires at least three signers")
    folds = []
    for index, test_signer in enumerate(signers):
        validation_signer = signers[(index + 1) % len(signers)]
        train_signers = tuple(
            signer
            for signer in signers
            if signer not in {test_signer, validation_signer}
        )
        folds.append(SignerFold(test_signer, validation_signer, train_signers))
    return folds


def validate_signer_class_coverage(dataset: TrainingDataset) -> None:
    expected_classes = set(range(len(dataset.label_keys)))
    for signer in sorted(set(dataset.signer_ids)):
        present = set(dataset.labels[dataset.signer_ids == signer].tolist())
        missing = sorted(expected_classes - present)
        if missing:
            raise ValueError(f"signer {signer} has no samples for class IDs: {missing}")


def _select(
    dataset: TrainingDataset, signers: tuple[str, ...]
) -> tuple[np.ndarray, np.ndarray]:
    mask = np.isin(dataset.signer_ids, signers)
    return dataset.features[mask], dataset.labels[mask]


def cross_validate_signers(
    dataset_path: Path,
    output_path: Path,
    *,
    epochs: int = 100,
    batch_size: int = 32,
    patience: int = 12,
    seed: int = 42,
) -> dict[str, Any]:
    if epochs <= 0 or batch_size <= 0 or patience < 0:
        raise ValueError("epochs and batch_size must be positive; patience cannot be negative")

    import tensorflow as tf

    dataset = load_training_dataset(dataset_path)
    validate_signer_class_coverage(dataset)
    folds = make_signer_folds(dataset.signer_ids)
    class_ids = np.arange(len(dataset.label_keys))
    fold_reports: list[dict[str, Any]] = []

    for fold_index, fold in enumerate(folds):
        fold_seed = seed + fold_index
        random.seed(fold_seed)
        np.random.seed(fold_seed)
        tf.keras.utils.set_random_seed(fold_seed)
        tf.keras.backend.clear_session()

        x_train, y_train = _select(dataset, fold.train_signers)
        x_validation, y_validation = _select(
            dataset, (fold.validation_signer,)
        )
        x_test, y_test = _select(dataset, (fold.test_signer,))
        weights = compute_class_weight(
            class_weight="balanced", classes=class_ids, y=y_train
        )
        model = build_baseline_model(len(dataset.label_keys))
        history = model.fit(
            x_train,
            y_train,
            validation_data=(x_validation, y_validation),
            epochs=epochs,
            batch_size=batch_size,
            class_weight={index: float(value) for index, value in enumerate(weights)},
            callbacks=[
                tf.keras.callbacks.EarlyStopping(
                    monitor="val_loss",
                    patience=patience,
                    restore_best_weights=True,
                )
            ],
            verbose=0,
        )
        start = time.perf_counter()
        probabilities = model.predict(x_test, batch_size=batch_size, verbose=0)
        inference_ms = (time.perf_counter() - start) * 1000.0 / len(x_test)
        predictions = probabilities.argmax(axis=1)
        fold_reports.append(
            {
                **asdict(fold),
                "trainSamples": len(x_train),
                "validationSamples": len(x_validation),
                "testSamples": len(x_test),
                "epochsRun": len(history.history["loss"]),
                "macroF1": float(
                    f1_score(
                        y_test,
                        predictions,
                        labels=class_ids,
                        average="macro",
                        zero_division=0,
                    )
                ),
                "accuracy": float(accuracy_score(y_test, predictions)),
                "meanInferenceMsPerWindow": inference_ms,
                "confusionMatrix": confusion_matrix(
                    y_test, predictions, labels=class_ids
                ).tolist(),
            }
        )

    macro_scores = np.asarray(
        [report["macroF1"] for report in fold_reports], dtype=np.float64
    )
    report = {
        "dataset": str(dataset_path),
        "signers": len(folds),
        "labels": list(dataset.label_keys),
        "meanMacroF1": float(macro_scores.mean()),
        "stdMacroF1": float(macro_scores.std()),
        "minMacroF1": float(macro_scores.min()),
        "maxMacroF1": float(macro_scores.max()),
        "folds": fold_reports,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run leave-one-signer-out evaluation with rotating validation signers."
    )
    parser.add_argument("--dataset", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--patience", type=int, default=12)
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    report = cross_validate_signers(
        args.dataset,
        args.output,
        epochs=args.epochs,
        batch_size=args.batch_size,
        patience=args.patience,
        seed=args.seed,
    )
    print(
        json.dumps(
            {
                "signers": report["signers"],
                "meanMacroF1": report["meanMacroF1"],
                "stdMacroF1": report["stdMacroF1"],
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
