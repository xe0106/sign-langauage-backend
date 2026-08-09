from __future__ import annotations

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from pydantic import ValidationError

from . import __version__
from .contracts import BufferStatus, ErrorMessage, LandmarkFrame
from .session import SessionStore
from .settings import SEQUENCE_LENGTH

app = FastAPI(title="Mind Voice AI Service", version=__version__)
sessions = SessionStore()


@app.get("/health")
async def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": "mindvoice-ai",
        "version": __version__,
        "modelStatus": "unavailable",
    }


@app.websocket("/ws/inference")
async def inference_socket(websocket: WebSocket) -> None:
    await websocket.accept()
    active_session_id = None

    try:
        while True:
            payload = await websocket.receive_json()
            try:
                frame = LandmarkFrame.model_validate(payload)
            except ValidationError as error:
                response = ErrorMessage(
                    code="INVALID_LANDMARK_FRAME",
                    message=error.errors(include_url=False)[0]["msg"],
                )
                await websocket.send_json(response.model_dump(mode="json"))
                continue

            active_session_id = frame.sessionId
            buffer, accepted = sessions.append(frame)
            if not accepted:
                response = ErrorMessage(
                    code="OUT_OF_ORDER_FRAME",
                    message="sequence must be greater than the last accepted sequence",
                )
                await websocket.send_json(response.model_dump(mode="json"))
                continue

            status = "model_unavailable" if buffer.ready else "warming_up"
            response = BufferStatus(
                status=status,
                bufferedFrames=len(buffer.frames),
                requiredFrames=SEQUENCE_LENGTH,
            )
            await websocket.send_json(response.model_dump(mode="json"))
    except WebSocketDisconnect:
        if active_session_id is not None:
            sessions.remove(active_session_id)
