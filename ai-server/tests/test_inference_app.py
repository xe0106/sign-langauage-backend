from uuid import uuid4
import asyncio
import threading
import time

import numpy as np

from fastapi.testclient import TestClient

from mindvoice_ai.app import _predict_with_limits, create_app, mirror_input_x_coordinates
from mindvoice_ai.inference.package import ModelMetadata
from mindvoice_ai.inference.predictor import RawPrediction
from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH


class FakePredictor:
    def __init__(self, predictions: list[RawPrediction]) -> None:
        self.predictions = iter(predictions)
        self.inputs: list[np.ndarray] = []
        self.metadata = ModelMetadata.model_validate(
            {
                "modelVersion": "fake-v1",
                "sequenceLength": SEQUENCE_LENGTH,
                "featureDimension": FEATURE_DIMENSION,
                "featureOrder": "test",
                "normalization": "test",
                "labels": [
                    {"classId": 0, "stableKey": "HELLO", "label": "안녕하세요"},
                    {"classId": 1, "stableKey": "NO_SIGN", "label": "비수어"},
                ],
                "modelFile": "model.keras",
                "modelSha256": "0" * 64,
                "confidenceThreshold": 0.9,
                "stableWindowCount": 3,
                "noSignStableKey": "NO_SIGN",
            }
        )

    def predict(self, features: np.ndarray) -> RawPrediction:
        assert features.shape == (SEQUENCE_LENGTH, FEATURE_DIMENSION)
        self.inputs.append(features.copy())
        return next(self.predictions)


def frame_payload(session_id: str, sequence: int, *, call_id: str | None = None) -> dict:
    payload = {
        "type": "landmark_frame",
        "sessionId": session_id,
        "sequence": sequence,
        "timestampMs": sequence * 33,
        "features": [float(sequence)] * FEATURE_DIMENSION,
    }
    if call_id is not None:
        payload["callId"] = call_id
    return payload


def session_end_payload(session_id: str, *, call_id: str | None = None) -> dict:
    payload = {"type": "session_end", "sessionId": session_id, "timestampMs": 10_000}
    if call_id is not None:
        payload["callId"] = call_id
    return payload


def test_health_reports_loaded_model_version() -> None:
    predictor = FakePredictor([])
    client = TestClient(create_app(predictor, auto_load_model=False))

    response = client.get("/health").json()

    assert response["modelStatus"] == "available"
    assert response["modelVersion"] == "fake-v1"
    assert client.get("/ready").status_code == 200


def test_mirror_input_x_coordinates_only_changes_x_components() -> None:
    features = np.arange(2 * FEATURE_DIMENSION, dtype=np.float32).reshape(2, FEATURE_DIMENSION)

    mirrored = mirror_input_x_coordinates(features)

    for offset, stride, count in (
        (0, 4, 33),
        (132, 3, 21),
        (195, 3, 21),
    ):
        x_indices = offset + np.arange(count) * stride
        assert np.array_equal(mirrored[:, x_indices], -features[:, x_indices])
    unchanged = [1, 2, 3, 133, 134, 196, 197]
    assert np.array_equal(mirrored[:, unchanged], features[:, unchanged])


def test_websocket_predicts_once_after_session_end_with_full_utterance() -> None:
    predictor = FakePredictor([RawPrediction(0, "HELLO", "안녕하세요", 0.96)])
    client = TestClient(create_app(predictor, auto_load_model=False))
    session_id = str(uuid4())
    call_id = str(uuid4())

    with client.websocket_connect("/ws/inference") as websocket:
        for sequence in range(45):
            websocket.send_json(frame_payload(session_id, sequence, call_id=call_id))
            response = websocket.receive_json()
            assert response["status"] == "collecting"
            assert response["bufferedFrames"] == sequence + 1
        websocket.send_json(session_end_payload(session_id, call_id=call_id))
        emitted = websocket.receive_json()
        websocket.send_json(session_end_payload(session_id, call_id=call_id))
        duplicate = websocket.receive_json()

    assert emitted["type"] == "prediction"
    assert emitted["sessionId"] == session_id
    assert emitted["callId"] == call_id
    assert emitted["label"] == "안녕하세요"
    assert emitted["stable"] is True
    assert duplicate["code"] == "UNKNOWN_SESSION"
    assert len(predictor.inputs) == 1
    assert predictor.inputs[0][0, 0] == 0.0
    assert predictor.inputs[0][-1, 0] == 44.0


def test_no_sign_or_low_confidence_ends_without_prediction() -> None:
    predictor = FakePredictor([RawPrediction(1, "NO_SIGN", "비수어", 0.99)])
    client = TestClient(create_app(predictor, auto_load_model=False))
    session_id = str(uuid4())

    with client.websocket_connect("/ws/inference") as websocket:
        for sequence in range(3):
            websocket.send_json(frame_payload(session_id, sequence))
            websocket.receive_json()
        websocket.send_json(session_end_payload(session_id))
        response = websocket.receive_json()

    assert response["status"] == "completed_no_prediction"
    assert response["sessionId"] == session_id


def test_invalid_json_does_not_close_websocket() -> None:
    client = TestClient(create_app(auto_load_model=False))
    session_id = str(uuid4())

    with client.websocket_connect("/ws/inference") as websocket:
        websocket.send_text("not-json")
        error = websocket.receive_json()
        websocket.send_json(frame_payload(session_id, 0))
        status = websocket.receive_json()

    assert error["code"] == "INVALID_JSON"
    assert status["status"] == "collecting"


def test_slow_inference_returns_timeout_after_session_end() -> None:
    predictor = FakePredictor([RawPrediction(0, "HELLO", "안녕하세요", 0.96)])
    original_predict = predictor.predict

    def slow_predict(features):
        time.sleep(0.05)
        return original_predict(features)

    predictor.predict = slow_predict
    client = TestClient(create_app(predictor, auto_load_model=False, inference_timeout_seconds=0.01))
    session_id = str(uuid4())

    with client.websocket_connect("/ws/inference") as websocket:
        for sequence in range(3):
            websocket.send_json(frame_payload(session_id, sequence))
            websocket.receive_json()
        websocket.send_json(session_end_payload(session_id))
        response = websocket.receive_json()

    assert response["code"] == "INFERENCE_TIMEOUT"


def test_inference_semaphore_limits_worker_threads() -> None:
    class CountingPredictor:
        def __init__(self) -> None:
            self.active = 0
            self.maximum = 0
            self.lock = threading.Lock()

        def predict(self, features):
            with self.lock:
                self.active += 1
                self.maximum = max(self.maximum, self.active)
            time.sleep(0.02)
            with self.lock:
                self.active -= 1
            return features

    predictor = CountingPredictor()

    async def run_predictions():
        slots = asyncio.Semaphore(1)
        window = np.zeros((SEQUENCE_LENGTH, FEATURE_DIMENSION), dtype=np.float32)
        await asyncio.gather(
            _predict_with_limits(predictor, window, slots, 1.0),
            _predict_with_limits(predictor, window, slots, 1.0),
        )

    asyncio.run(run_predictions())
    assert predictor.maximum == 1
