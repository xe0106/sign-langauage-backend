import pytest

from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH
from mindvoice_ai.training.model import build_baseline_model


def test_build_baseline_model_has_contract_shape() -> None:
    model = build_baseline_model(3)

    assert model.input_shape == (None, SEQUENCE_LENGTH, FEATURE_DIMENSION)
    assert model.output_shape == (None, 3)


def test_build_baseline_model_requires_multiple_classes() -> None:
    with pytest.raises(ValueError, match="at least two"):
        build_baseline_model(1)
