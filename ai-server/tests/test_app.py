from uuid import uuid4

from fastapi.testclient import TestClient

from mindvoice_ai.app import app
from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH

client = TestClient(app)


def frame_payload(session_id: str, sequence: int) -> dict:
    return {
        "type": "landmark_frame",
        "sessionId": session_id,
        "sequence": sequence,
        "timestampMs": sequence * 33,
        "features": [0.0] * FEATURE_DIMENSION,
    }


def test_health_reports_model_unavailable() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["modelStatus"] == "unavailable"


def test_readiness_fails_without_model() -> None:
    response = client.get("/ready")

    assert response.status_code == 503


def test_websocket_reports_missing_model_when_a_session_ends() -> None:
    session_id = str(uuid4())

    with client.websocket_connect("/ws/inference") as websocket:
        for sequence in range(3):
            websocket.send_json(frame_payload(session_id, sequence))
            websocket.receive_json()
        websocket.send_json(
            {"type": "session_end", "sessionId": session_id, "timestampMs": 100}
        )
        response = websocket.receive_json()

    assert response["type"] == "status"
    assert response["status"] == "model_unavailable"
    assert response["bufferedFrames"] == 3
    assert response["sessionId"] == session_id


def test_websocket_rejects_wrong_feature_dimension() -> None:
    payload = frame_payload(str(uuid4()), 0)
    payload["features"] = [0.0]

    with client.websocket_connect("/ws/inference") as websocket:
        websocket.send_json(payload)
        response = websocket.receive_json()

    assert response["type"] == "error"
    assert response["code"] == "INVALID_INFERENCE_MESSAGE"
