from __future__ import annotations

import argparse
import hashlib
import json
import random
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
from sklearn.metrics import classification_report, confusion_matrix, f1_score
from sklearn.utils.class_weight import compute_class_weight

from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH
from mindvoice_ai.training.data import load_training_dataset
from mindvoice_ai.training.model import build_baseline_model


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def train_model(
    dataset_path: Path,
    output_dir: Path,
    *,
    model_version: str,
    epochs: int = 100,
    batch_size: int = 32,
    patience: int = 12,
    seed: int = 42,
    overwrite: bool = False,
    confidence_threshold: float = 0.9,
    stable_window_count: int = 3,
) -> dict[str, Any]:
    if not model_version.strip():
        raise ValueError("model_version must not be empty")
    if epochs <= 0 or batch_size <= 0 or patience < 0:
        raise ValueError("epochs and batch_size must be positive; patience cannot be negative")
    if not 0.0 <= confidence_threshold <= 1.0:
        raise ValueError("confidence_threshold must be between 0 and 1")
    if stable_window_count <= 0:
        raise ValueError("stable_window_count must be positive")

    artifact_paths = [
        output_dir / "model.keras",
        output_dir / "metadata.json",
        output_dir / "evaluation.json",
    ]
    existing = [path for path in artifact_paths if path.exists()]
    if existing and not overwrite:
        raise FileExistsError(
            f"model artifact already exists (use --overwrite): {existing[0]}"
        )

    import tensorflow as tf

    random.seed(seed)
    np.random.seed(seed)
    tf.keras.utils.set_random_seed(seed)
    dataset = load_training_dataset(dataset_path)
    if "NO_SIGN" not in dataset.label_keys:
        raise ValueError("training dataset must contain the NO_SIGN class")
    x_train, y_train = dataset.split("train")
    x_validation, y_validation = dataset.split("validation")
    x_test, y_test = dataset.split("test")

    present_classes = np.unique(y_train)
    expected_classes = np.arange(len(dataset.label_keys))
    if not np.array_equal(present_classes, expected_classes):
        raise ValueError("training split must contain at least one sample for every class")
    weights = compute_class_weight(
        class_weight="balanced", classes=expected_classes, y=y_train
    )
    class_weights = {index: float(weight) for index, weight in enumerate(weights)}

    output_dir.mkdir(parents=True, exist_ok=True)
    model_path = output_dir / "model.keras"
    model = build_baseline_model(len(dataset.label_keys))
    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss", patience=patience, restore_best_weights=True
        ),
        tf.keras.callbacks.ModelCheckpoint(
            filepath=model_path, monitor="val_loss", save_best_only=True
        ),
    ]
    history = model.fit(
        x_train,
        y_train,
        validation_data=(x_validation, y_validation),
        epochs=epochs,
        batch_size=batch_size,
        class_weight=class_weights,
        callbacks=callbacks,
        verbose=2,
    )
    model.save(model_path)

    start = time.perf_counter()
    probabilities = model.predict(x_test, batch_size=batch_size, verbose=0)
    elapsed_ms = (time.perf_counter() - start) * 1000.0
    predictions = probabilities.argmax(axis=1)
    class_ids = list(range(len(dataset.label_keys)))
    report = {
        "modelVersion": model_version,
        "testSamples": len(x_test),
        "macroF1": float(
            f1_score(y_test, predictions, labels=class_ids, average="macro", zero_division=0)
        ),
        "meanInferenceMsPerWindow": elapsed_ms / len(x_test),
        "classificationReport": classification_report(
            y_test,
            predictions,
            labels=class_ids,
            target_names=list(dataset.label_keys),
            output_dict=True,
            zero_division=0,
        ),
        "confusionMatrix": confusion_matrix(
            y_test, predictions, labels=class_ids
        ).tolist(),
        "history": {
            key: [float(value) for value in values]
            for key, values in history.history.items()
        },
    }
    metadata = {
        "modelVersion": model_version,
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "architecture": "1d_cnn_v1",
        "sequenceLength": SEQUENCE_LENGTH,
        "featureDimension": FEATURE_DIMENSION,
        "featureOrder": "pose_33x(x,y,z,visibility),left_hand_21x(x,y,z),right_hand_21x(x,y,z)",
        "normalization": "shoulder_center_and_width_v1",
        "confidenceThreshold": confidence_threshold,
        "stableWindowCount": stable_window_count,
        "noSignStableKey": "NO_SIGN",
        "labels": [
            {"classId": index, "stableKey": key, "label": dataset.label_names[index]}
            for index, key in enumerate(dataset.label_keys)
        ],
        "modelFile": model_path.name,
        "modelSha256": file_sha256(model_path),
        "datasetSha256": file_sha256(dataset_path),
        "trainingSeed": seed,
        "trainingParameters": {
            "epochsRequested": epochs,
            "batchSize": batch_size,
            "earlyStoppingPatience": patience,
        },
    }
    (output_dir / "evaluation.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (output_dir / "metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Train and evaluate the signer-independent 1D-CNN baseline."
    )
    parser.add_argument("--dataset", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--model-version", required=True)
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--patience", type=int, default=12)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument("--confidence-threshold", type=float, default=0.9)
    parser.add_argument("--stable-window-count", type=int, default=3)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    report = train_model(
        args.dataset,
        args.output_dir,
        model_version=args.model_version,
        epochs=args.epochs,
        batch_size=args.batch_size,
        patience=args.patience,
        seed=args.seed,
        overwrite=args.overwrite,
        confidence_threshold=args.confidence_threshold,
        stable_window_count=args.stable_window_count,
    )
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
