import numpy as np
import pytest

from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH
from mindvoice_ai.training.cross_validate import (
    cross_validate_signers,
    make_signer_folds,
    validate_signer_class_coverage,
)
from mindvoice_ai.training.data import TrainingDataset


def dataset(labels: list[int]) -> TrainingDataset:
    count = len(labels)
    return TrainingDataset(
        features=np.zeros((count, SEQUENCE_LENGTH, FEATURE_DIMENSION), dtype=np.float32),
        labels=np.asarray(labels),
        splits=np.asarray(["train"] * count),
        signer_ids=np.asarray(["S001", "S001", "S002", "S002", "S003", "S003"]),
        sample_ids=np.asarray([f"sample-{index}" for index in range(count)]),
        label_keys=("HELLO", "NO_SIGN"),
        label_names=("안녕하세요", "비수어"),
    )


def test_make_signer_folds_rotates_test_and_validation() -> None:
    folds = make_signer_folds(
        np.asarray(["S001", "S001", "S002", "S003", "S004", "S005", "S006"])
    )

    assert len(folds) == 6
    assert {fold.test_signer for fold in folds} == {
        "S001",
        "S002",
        "S003",
        "S004",
        "S005",
        "S006",
    }
    for fold in folds:
        assert fold.test_signer != fold.validation_signer
        assert len(fold.train_signers) == 4
        assert fold.test_signer not in fold.train_signers
        assert fold.validation_signer not in fold.train_signers


def test_make_signer_folds_requires_three_signers() -> None:
    with pytest.raises(ValueError, match="at least three"):
        make_signer_folds(np.asarray(["S001", "S002"]))


def test_validate_signer_class_coverage_accepts_complete_signers() -> None:
    validate_signer_class_coverage(dataset([0, 1, 0, 1, 0, 1]))


def test_validate_signer_class_coverage_rejects_missing_class() -> None:
    with pytest.raises(ValueError, match="S003"):
        validate_signer_class_coverage(dataset([0, 1, 0, 1, 0, 0]))


def test_cross_validate_signers_runs_every_signer_fold(tmp_path) -> None:
    rng = np.random.default_rng(3)
    labels = np.asarray([0, 1, 0, 1, 0, 1])
    path = tmp_path / "dataset.npz"
    np.savez_compressed(
        path,
        features=rng.normal(
            size=(6, SEQUENCE_LENGTH, FEATURE_DIMENSION)
        ).astype(np.float32),
        labels=labels,
        splits=np.asarray(["train", "train", "validation", "validation", "test", "test"]),
        signer_ids=np.asarray(["S001", "S001", "S002", "S002", "S003", "S003"]),
        sample_ids=np.asarray([f"sample-{index}" for index in range(6)]),
        label_keys=np.asarray(["HELLO", "NO_SIGN"]),
        label_names=np.asarray(["안녕하세요", "비수어"]),
    )
    output = tmp_path / "report.json"

    report = cross_validate_signers(
        path, output, epochs=1, batch_size=2, patience=0
    )

    assert report["signers"] == 3
    assert len(report["folds"]) == 3
    assert output.is_file()
