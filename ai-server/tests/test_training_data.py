from pathlib import Path

import numpy as np
import pytest

from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH
from mindvoice_ai.training.data import load_training_dataset


def save_dataset(path: Path, *, signer_ids: list[str] | None = None) -> None:
    splits = np.asarray(
        ["train", "train", "validation", "validation", "test", "test"]
    )
    np.savez_compressed(
        path,
        features=np.zeros(
            (len(splits), SEQUENCE_LENGTH, FEATURE_DIMENSION), dtype=np.float32
        ),
        labels=np.asarray([0, 1, 0, 1, 0, 1]),
        splits=splits,
        signer_ids=np.asarray(
            signer_ids or ["S001", "S001", "S002", "S002", "S003", "S003"]
        ),
        sample_ids=np.asarray(["a", "b", "c", "d", "e", "f"]),
        label_keys=np.asarray(["HELLO", "NO_SIGN"]),
        label_names=np.asarray(["안녕하세요", "비수어"]),
    )


def test_load_training_dataset_and_select_split(tmp_path: Path) -> None:
    path = tmp_path / "dataset.npz"
    save_dataset(path)

    dataset = load_training_dataset(path)
    features, labels = dataset.split("train")

    assert features.shape == (2, SEQUENCE_LENGTH, FEATURE_DIMENSION)
    np.testing.assert_array_equal(labels, [0, 1])
    assert dataset.label_keys == ("HELLO", "NO_SIGN")


def test_load_training_dataset_rejects_signer_leakage(tmp_path: Path) -> None:
    path = tmp_path / "dataset.npz"
    save_dataset(path, signer_ids=["S001", "S001", "S001", "S002", "S003", "S003"])

    with pytest.raises(ValueError, match="multiple splits"):
        load_training_dataset(path)


def test_load_training_dataset_rejects_non_finite_features(tmp_path: Path) -> None:
    path = tmp_path / "dataset.npz"
    save_dataset(path)
    with np.load(path) as archive:
        arrays = {name: archive[name] for name in archive.files}
    arrays["features"][0, 0, 0] = np.nan
    np.savez_compressed(path, **arrays)

    with pytest.raises(ValueError, match="non-finite"):
        load_training_dataset(path)
