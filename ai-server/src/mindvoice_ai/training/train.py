from __future__ import annotations

import argparse
import hashlib
import json
import random
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
from sklearn.utils.class_weight import compute_class_weight

from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH
from mindvoice_ai.training.augmentation import (
    LandmarkAugmentationConfig,
    augment_training_features,
)
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
    augmentation_copies: int = 0,
    augmentation_jitter_std: float = 0.01,
    augmentation_max_frame_shift: int = 3,
    augmentation_min_speed: float = 0.9,
    augmentation_max_speed: float = 1.1,
    architecture: str = "1d_cnn_v1",
    learning_rate: float = 1e-3,
    dropout: float = 0.3,
    l2_weight_decay: float = 0.0,
    reduce_lr_patience: int = 0,
    reduce_lr_factor: float = 0.5,
    refit_validation: bool = False,
) -> dict[str, Any]:
    if not model_version.strip():
        raise ValueError("model_version must not be empty")
    if epochs <= 0 or batch_size <= 0 or patience < 0:
        raise ValueError("epochs and batch_size must be positive; patience cannot be negative")
    if reduce_lr_patience < 0 or not 0.0 < reduce_lr_factor < 1.0:
        raise ValueError("reduce_lr_patience must be non-negative and factor must be in (0, 1)")
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
    dataset = load_training_dataset(dataset_path, require_test_split=False)
    if "NO_SIGN" not in dataset.label_keys:
        raise ValueError("training dataset must contain the NO_SIGN class")
    x_train, y_train = dataset.split("train")
    x_validation, y_validation = dataset.split("validation")
    if refit_validation:
        x_train = np.concatenate((x_train, x_validation), axis=0)
        y_train = np.concatenate((y_train, y_validation), axis=0)

    augmentation = LandmarkAugmentationConfig(
        copies=augmentation_copies,
        jitter_std=augmentation_jitter_std,
        max_frame_shift=augmentation_max_frame_shift,
        min_speed=augmentation_min_speed,
        max_speed=augmentation_max_speed,
    )
    augmentation.validate()
    x_train, y_train = augment_training_features(
        x_train, y_train, config=augmentation, seed=seed
    )

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
    model = build_baseline_model(
        len(dataset.label_keys),
        architecture=architecture,
        learning_rate=learning_rate,
        dropout=dropout,
        l2_weight_decay=l2_weight_decay,
    )
    callbacks: list[Any] = []
    if not refit_validation:
        callbacks.extend(
            (
                tf.keras.callbacks.EarlyStopping(
                    monitor="val_loss", patience=patience, restore_best_weights=True
                ),
                tf.keras.callbacks.ModelCheckpoint(
                    filepath=model_path, monitor="val_loss", save_best_only=True
                ),
            )
        )
    if reduce_lr_patience and not refit_validation:
        callbacks.append(
            tf.keras.callbacks.ReduceLROnPlateau(
                monitor="val_loss",
                factor=reduce_lr_factor,
                patience=reduce_lr_patience,
                min_lr=1e-5,
            )
        )
    history = model.fit(
        x_train,
        y_train,
        validation_data=None if refit_validation else (x_validation, y_validation),
        epochs=epochs,
        batch_size=batch_size,
        class_weight=class_weights,
        callbacks=callbacks,
        verbose=2,
    )
    model.save(model_path)

    report = {
        "modelVersion": model_version,
        "evaluationStatus": "not_run",
        "message": (
            "Refit using train and validation splits. Evaluate the frozen model with "
            "a held-out test dataset separately."
            if refit_validation
            else "Train/validation only. Evaluate the frozen model with a held-out test dataset separately."
        ),
        "history": {
            key: [float(value) for value in values]
            for key, values in history.history.items()
        },
    }
    metadata = {
        "modelVersion": model_version,
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "architecture": architecture,
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
            "landmarkAugmentation": augmentation.metadata(),
            "learningRate": learning_rate,
            "dropout": dropout,
            "l2WeightDecay": l2_weight_decay,
            "reduceLrPatience": reduce_lr_patience,
            "reduceLrFactor": reduce_lr_factor,
            "refitValidation": refit_validation,
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
        description="Train the 1D-CNN baseline using train and validation splits."
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
    parser.add_argument(
        "--augmentation-copies",
        type=int,
        default=0,
        help="Augmented train-only copies per original sample; 0 disables augmentation.",
    )
    parser.add_argument("--augmentation-jitter-std", type=float, default=0.01)
    parser.add_argument("--augmentation-max-frame-shift", type=int, default=3)
    parser.add_argument("--augmentation-min-speed", type=float, default=0.9)
    parser.add_argument("--augmentation-max-speed", type=float, default=1.1)
    parser.add_argument(
        "--architecture",
        choices=("1d_cnn_v1", "bigru_v1", "temporal_cnn_v2"),
        default="1d_cnn_v1",
    )
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    parser.add_argument("--dropout", type=float, default=0.3)
    parser.add_argument("--l2-weight-decay", type=float, default=0.0)
    parser.add_argument("--reduce-lr-patience", type=int, default=0)
    parser.add_argument("--reduce-lr-factor", type=float, default=0.5)
    parser.add_argument(
        "--refit-validation",
        action="store_true",
        help="Train once on train+validation using a preselected fixed epoch count.",
    )
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
        augmentation_copies=args.augmentation_copies,
        augmentation_jitter_std=args.augmentation_jitter_std,
        augmentation_max_frame_shift=args.augmentation_max_frame_shift,
        augmentation_min_speed=args.augmentation_min_speed,
        augmentation_max_speed=args.augmentation_max_speed,
        architecture=args.architecture,
        learning_rate=args.learning_rate,
        dropout=args.dropout,
        l2_weight_decay=args.l2_weight_decay,
        reduce_lr_patience=args.reduce_lr_patience,
        reduce_lr_factor=args.reduce_lr_factor,
        refit_validation=args.refit_validation,
    )
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
