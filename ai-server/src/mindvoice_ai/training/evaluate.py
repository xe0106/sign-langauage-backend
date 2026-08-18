from __future__ import annotations

import argparse
import json
import time
from pathlib import Path
from typing import Any

import numpy as np
from sklearn.metrics import classification_report, confusion_matrix, f1_score

from mindvoice_ai.inference.package import load_model_package
from mindvoice_ai.training.data import load_training_dataset


def evaluate_model(
    dataset_path: Path, model_dir: Path, output_path: Path, *, batch_size: int = 32
) -> dict[str, Any]:
    if batch_size <= 0:
        raise ValueError("batch_size must be positive")
    dataset = load_training_dataset(dataset_path)
    package = load_model_package(model_dir)
    label_keys = tuple(label.stableKey for label in package.metadata.labels)
    if dataset.label_keys != label_keys:
        raise ValueError("dataset labels do not match the model package")
    x_test, y_test = dataset.split("test")
    start = time.perf_counter()
    probabilities = package.model.predict(x_test, batch_size=batch_size, verbose=0)
    elapsed_ms = (time.perf_counter() - start) * 1000.0
    predictions = probabilities.argmax(axis=1)
    class_ids = list(range(len(dataset.label_keys)))
    report = {
        "modelVersion": package.metadata.modelVersion,
        "testSamples": len(x_test),
        "macroF1": float(
            f1_score(y_test, predictions, labels=class_ids, average="macro", zero_division=0)
        ),
        "accuracy": float(np.mean(predictions == y_test)),
        "meanInferenceMsPerWindow": elapsed_ms / len(x_test),
        "classificationReport": classification_report(
            y_test,
            predictions,
            labels=class_ids,
            target_names=list(dataset.label_keys),
            output_dict=True,
            zero_division=0,
        ),
        "confusionMatrix": confusion_matrix(y_test, predictions, labels=class_ids).tolist(),
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Evaluate a trained model against a held-out test dataset."
    )
    parser.add_argument("--dataset", required=True, type=Path)
    parser.add_argument("--model-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--batch-size", type=int, default=32)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    report = evaluate_model(
        args.dataset, args.model_dir, args.output, batch_size=args.batch_size
    )
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
