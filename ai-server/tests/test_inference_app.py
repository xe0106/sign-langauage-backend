from uuid import uuid4

from fastapi.testclient import TestClient

from mindvoice_ai.app import create_app
from mindvoice_ai.inference.package import ModelMetadata
from mindvoice_ai.inference.predictor import RawPrediction
from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH


class FakePredictor:
    def __init__(self, predictions: list[RawPrediction]) -> None:
        self.predictions = iter(predictions)
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

    def predict(self, features) -> RawPrediction:
        assert features.shape == (SEQUENCE_LENGTH, FEATURE_DIMENSION)
        return next(self.predictions)


def frame_payload(session_id: str, sequence: int) -> dict:
    return {
        "type": "landmark_frame",
        "sessionId": session_id,
        "sequence": sequence,
        "timestampMs": sequence * 33,
        "features": [0.0] * FEATURE_DIMENSION,
    }


def test_health_reports_loaded_model_version() -> None:
    predictor = FakePredictor([])
    client = TestClient(create_app(predictor, auto_load_model=False))

    response = client.get("/health").json()

    assert response["modelStatus"] == "available"
    assert response["modelVersion"] == "fake-v1"


def test_websocket_emits_only_stable_prediction() -> None:
    hello = RawPrediction(0, "HELLO", "안녕하세요", 0.96)
    predictor = FakePredictor([hello, hello, hello, hello])
    client = TestClient(create_app(predictor, auto_load_model=False))
    session_id = str(uuid4())

    with client.websocket_connect("/ws/inference") as websocket:
        responses = []
        for sequence in range(SEQUENCE_LENGTH + 3):
            websocket.send_json(frame_payload(session_id, sequence))
            responses.append(websocket.receive_json())

    assert responses[SEQUENCE_LENGTH - 1]["status"] == "analyzing"
    assert responses[SEQUENCE_LENGTH]["status"] == "analyzing"
    emitted = responses[SEQUENCE_LENGTH + 1]
    assert emitted["type"] == "prediction"
    assert emitted["classId"] == 0
    assert emitted["label"] == "안녕하세요"
    assert emitted["stable"] is True
    assert emitted["modelVersion"] == "fake-v1"
    assert responses[SEQUENCE_LENGTH + 2]["status"] == "analyzing"


def test_invalid_json_does_not_close_websocket() -> None:
    client = TestClient(create_app(auto_load_model=False))
    session_id = str(uuid4())

    with client.websocket_connect("/ws/inference") as websocket:
        websocket.send_text("not-json")
        error = websocket.receive_json()
        websocket.send_json(frame_payload(session_id, 0))
        status = websocket.receive_json()

    assert error["code"] == "INVALID_JSON"
    assert status["status"] == "warming_up"
