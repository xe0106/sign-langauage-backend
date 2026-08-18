import json
from pathlib import Path

import numpy as np

from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH
from mindvoice_ai.inference.package import load_model_package
from mindvoice_ai.inference.predictor import KerasPredictor
from mindvoice_ai.training.evaluate import evaluate_model
from mindvoice_ai.training.augmentation import (
    LandmarkAugmentationConfig,
    augment_training_features,
)
from mindvoice_ai.training.train import train_model


def test_train_model_creates_versioned_artifact_package(tmp_path: Path) -> None:
    rng = np.random.default_rng(7)
    labels = np.asarray([0, 1, 0, 1, 0, 1])
    splits = np.asarray(
        ["train", "train", "train", "train", "validation", "validation"]
    )
    features = rng.normal(
        size=(len(labels), SEQUENCE_LENGTH, FEATURE_DIMENSION)
    ).astype(np.float32)
    features[labels == 1] += 0.5
    dataset_path = tmp_path / "dataset.npz"
    np.savez_compressed(
        dataset_path,
        features=features,
        labels=labels,
        splits=splits,
        signer_ids=np.asarray(
            ["TR1", "TR1", "TR2", "TR2", "VA1", "VA1"]
        ),
        sample_ids=np.asarray([f"sample-{index}" for index in range(len(labels))]),
        label_keys=np.asarray(["HELLO", "NO_SIGN"]),
        label_names=np.asarray(["안녕하세요", "비수어"]),
    )

    output_dir = tmp_path / "artifact"
    report = train_model(
        dataset_path,
        output_dir,
        model_version="test-v1",
        epochs=1,
        batch_size=2,
        patience=0,
    )

    assert (output_dir / "model.keras").is_file()
    assert (output_dir / "evaluation.json").is_file()
    metadata = json.loads((output_dir / "metadata.json").read_text(encoding="utf-8"))
    assert metadata["modelVersion"] == "test-v1"
    assert len(metadata["modelSha256"]) == 64
    assert len(metadata["datasetSha256"]) == 64
    assert metadata["labels"][1]["stableKey"] == "NO_SIGN"
    assert report["evaluationStatus"] == "not_run"
    assert metadata["trainingParameters"]["landmarkAugmentation"]["copies"] == 0
    assert metadata["trainingParameters"]["learningRate"] == 1e-3
    assert metadata["trainingParameters"]["l2WeightDecay"] == 0.0
    assert metadata["trainingParameters"]["reduceLrPatience"] == 0
    assert metadata["trainingParameters"]["refitValidation"] is False
    package = load_model_package(output_dir)
    assert package.metadata.modelVersion == "test-v1"
    predictor = KerasPredictor(output_dir)
    prediction = predictor.predict(features[0])
    assert prediction.stable_key in {"HELLO", "NO_SIGN"}

    test_dataset_path = tmp_path / "test-dataset.npz"
    np.savez_compressed(
        test_dataset_path,
        features=np.concatenate((features, features[:2])),
        labels=np.concatenate((labels, labels[:2])),
        splits=np.asarray([*splits, "test", "test"]),
        signer_ids=np.asarray(["TR1", "TR1", "TR2", "TR2", "VA1", "VA1", "TE1", "TE1"]),
        sample_ids=np.asarray([f"test-sample-{index}" for index in range(8)]),
        label_keys=np.asarray(["HELLO", "NO_SIGN"]),
        label_names=np.asarray(["안녕하세요", "비수어"]),
    )
    evaluation_path = output_dir / "test-evaluation.json"
    evaluation = evaluate_model(test_dataset_path, output_dir, evaluation_path, batch_size=2)
    assert evaluation_path.is_file()
    assert evaluation["testSamples"] == 2


def test_augmentation_adds_train_only_copies_without_changing_visibility() -> None:
    features = np.zeros((1, SEQUENCE_LENGTH, FEATURE_DIMENSION), dtype=np.float32)
    features[:, :, 3] = 0.8
    features[:, :, 0] = 0.2
    features[:, :, 132] = 0.4
    labels = np.asarray([1])

    augmented, augmented_labels = augment_training_features(
        features,
        labels,
        config=LandmarkAugmentationConfig(copies=1),
        seed=42,
    )

    assert augmented.shape == (2, SEQUENCE_LENGTH, FEATURE_DIMENSION)
    np.testing.assert_array_equal(augmented[0], features[0])
    np.testing.assert_array_equal(augmented_labels, [1, 1])
    np.testing.assert_array_equal(augmented[1, :, 3], features[0, :, 3])
    np.testing.assert_array_equal(augmented[1, :, 195:], 0.0)
