import json
from pathlib import Path

import numpy as np

from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH
from mindvoice_ai.training.train import train_model


def test_train_model_creates_versioned_artifact_package(tmp_path: Path) -> None:
    rng = np.random.default_rng(7)
    labels = np.asarray([0, 1, 0, 1, 0, 1, 0, 1])
    splits = np.asarray(
        ["train", "train", "train", "train", "validation", "validation", "test", "test"]
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
            ["TR1", "TR1", "TR2", "TR2", "VA1", "VA1", "TE1", "TE1"]
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
    assert report["testSamples"] == 2
