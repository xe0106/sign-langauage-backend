import json
from pathlib import Path

import numpy as np
import pytest

from mindvoice_ai.dataset.sequences import build_training_dataset, resample_features
from mindvoice_ai.settings import FEATURE_DIMENSION


def test_resample_features_interpolates_to_requested_length() -> None:
    features = np.zeros((2, FEATURE_DIMENSION), dtype=np.float32)
    features[1] = 2.0

    result = resample_features(features, 3)

    assert result.shape == (3, FEATURE_DIMENSION)
    np.testing.assert_allclose(result[:, 0], [0.0, 1.0, 2.0])


def write_processed(root: Path, name: str, frames: int) -> str:
    relative = Path("train") / f"{name}.npz"
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(
        path, features=np.ones((frames, FEATURE_DIMENSION), dtype=np.float32)
    )
    return relative.as_posix()


def test_build_training_dataset_preserves_explicit_class_ids(tmp_path: Path) -> None:
    records = [
        {
            "sampleId": "hello-1",
            "classId": 0,
            "label": "안녕하세요",
            "stableKey": "HELLO",
            "signerId": "S001",
            "split": "train",
            "processedPath": write_processed(tmp_path, "hello-1", 2),
        },
        {
            "sampleId": "thanks-1",
            "classId": 1,
            "label": "감사합니다",
            "stableKey": "THANKS",
            "signerId": "S002",
            "split": "train",
            "processedPath": write_processed(tmp_path, "thanks-1", 5),
        },
    ]
    index = tmp_path / "dataset-index.jsonl"
    index.write_text(
        "".join(json.dumps(record, ensure_ascii=False) + "\n" for record in records),
        encoding="utf-8",
    )

    output = tmp_path / "training.npz"
    summary = build_training_dataset(index, output, sequence_length=3)

    with np.load(output) as archive:
        assert archive["features"].shape == (2, 3, FEATURE_DIMENSION)
        np.testing.assert_array_equal(archive["labels"], [0, 1])
        np.testing.assert_array_equal(archive["label_keys"], ["HELLO", "THANKS"])
    assert summary["labels"] == {"HELLO": 0, "THANKS": 1}


def test_build_training_dataset_rejects_non_contiguous_classes(tmp_path: Path) -> None:
    record = {
        "sampleId": "thanks-1",
        "classId": 1,
        "label": "감사합니다",
        "stableKey": "THANKS",
        "signerId": "S001",
        "split": "train",
        "processedPath": write_processed(tmp_path, "thanks-1", 2),
    }
    index = tmp_path / "dataset-index.jsonl"
    index.write_text(json.dumps(record) + "\n", encoding="utf-8")

    with pytest.raises(ValueError, match="contiguous"):
        build_training_dataset(index, tmp_path / "training.npz")
