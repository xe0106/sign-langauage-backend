from mindvoice_ai.inference.predictor import RawPrediction
from mindvoice_ai.inference.stability import PredictionStabilizer


def prediction(class_id: int, key: str, confidence: float = 0.95) -> RawPrediction:
    return RawPrediction(
        class_id=class_id,
        stable_key=key,
        label=key,
        confidence=confidence,
    )


def test_emits_only_after_consecutive_confident_windows() -> None:
    stabilizer = PredictionStabilizer(0.9, 3)

    assert stabilizer.observe(prediction(0, "HELLO")) is None
    assert stabilizer.observe(prediction(0, "HELLO")) is None
    assert stabilizer.observe(prediction(0, "HELLO")).stable_key == "HELLO"


def test_low_confidence_breaks_consecutive_candidate() -> None:
    stabilizer = PredictionStabilizer(0.9, 2)

    assert stabilizer.observe(prediction(0, "HELLO")) is None
    assert stabilizer.observe(prediction(0, "HELLO", 0.5)) is None
    assert stabilizer.observe(prediction(0, "HELLO")) is None


def test_duplicate_requires_no_sign_release() -> None:
    stabilizer = PredictionStabilizer(0.9, 1)

    assert stabilizer.observe(prediction(0, "HELLO")) is not None
    assert stabilizer.observe(prediction(0, "HELLO")) is None
    assert stabilizer.observe(prediction(1, "NO_SIGN")) is None
    assert stabilizer.observe(prediction(0, "HELLO")) is not None
