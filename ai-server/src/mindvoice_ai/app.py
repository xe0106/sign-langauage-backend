from __future__ import annotations

import asyncio
import json
import os
from pathlib import Path

import numpy as np
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from pydantic import ValidationError

from . import __version__
from .contracts import (
    BufferStatus,
    ErrorMessage,
    LandmarkFrame,
    PredictionMessage,
)
from .inference.predictor import KerasPredictor, Predictor
from .inference.stability import PredictionStabilizer
from .session import SessionStore
from .settings import SEQUENCE_LENGTH

MODEL_DIR_ENV = "MINDVOICE_MODEL_DIR"


def create_app(
    predictor: Predictor | None = None, *, auto_load_model: bool = True
) -> FastAPI:
    model_error: str | None = None
    if predictor is None and auto_load_model:
        configured_dir = os.getenv(MODEL_DIR_ENV)
        if configured_dir:
            try:
                predictor = KerasPredictor(Path(configured_dir))
            except Exception as error:  # health endpoint exposes a safe summary
                model_error = f"{type(error).__name__}: {error}"

    application = FastAPI(title="Mind Voice AI Service", version=__version__)
    sessions = SessionStore()
    application.state.predictor = predictor
    application.state.model_error = model_error

    @application.get("/health")
    async def health() -> dict[str, object]:
        active_predictor = application.state.predictor
        response: dict[str, object] = {
            "status": "ok",
            "service": "mindvoice-ai",
            "version": __version__,
            "modelStatus": "available" if active_predictor else "unavailable",
        }
        if active_predictor:
            response["modelVersion"] = active_predictor.metadata.modelVersion
        if application.state.model_error:
            response["modelError"] = application.state.model_error
        return response

    @application.websocket("/ws/inference")
    async def inference_socket(websocket: WebSocket) -> None:
        await websocket.accept()
        active_session_id = None

        try:
            while True:
                try:
                    payload = await websocket.receive_json()
                except json.JSONDecodeError:
                    response = ErrorMessage(
                        code="INVALID_JSON",
                        message="message must contain valid JSON",
                    )
                    await websocket.send_json(response.model_dump(mode="json"))
                    continue
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

                if not buffer.ready:
                    response = BufferStatus(
                        status="warming_up",
                        bufferedFrames=len(buffer.frames),
                        requiredFrames=SEQUENCE_LENGTH,
                    )
                    await websocket.send_json(response.model_dump(mode="json"))
                    continue

                active_predictor = application.state.predictor
                if active_predictor is None:
                    response = BufferStatus(
                        status="model_unavailable",
                        bufferedFrames=len(buffer.frames),
                        requiredFrames=SEQUENCE_LENGTH,
                    )
                    await websocket.send_json(response.model_dump(mode="json"))
                    continue

                if buffer.stabilizer is None:
                    buffer.stabilizer = PredictionStabilizer(
                        confidence_threshold=active_predictor.metadata.confidenceThreshold,
                        stable_window_count=active_predictor.metadata.stableWindowCount,
                        no_sign_stable_key=active_predictor.metadata.noSignStableKey,
                    )
                try:
                    window = np.asarray(
                        [item.features for item in buffer.frames], dtype=np.float32
                    )
                    prediction = await asyncio.to_thread(
                        active_predictor.predict, window
                    )
                    stable_prediction = buffer.stabilizer.observe(prediction)
                except Exception as error:
                    response = ErrorMessage(
                        code="INFERENCE_FAILED",
                        message=f"{type(error).__name__}: {error}",
                    )
                    await websocket.send_json(response.model_dump(mode="json"))
                    continue

                if stable_prediction is None:
                    response = BufferStatus(
                        status="analyzing",
                        bufferedFrames=len(buffer.frames),
                        requiredFrames=SEQUENCE_LENGTH,
                    )
                else:
                    first_frame = buffer.frames[0]
                    last_frame = buffer.frames[-1]
                    response = PredictionMessage(
                        sessionId=last_frame.sessionId,
                        callId=last_frame.callId,
                        classId=stable_prediction.class_id,
                        label=stable_prediction.label,
                        confidence=stable_prediction.confidence,
                        startTimeMs=first_frame.timestampMs,
                        endTimeMs=last_frame.timestampMs,
                        modelVersion=active_predictor.metadata.modelVersion,
                    )
                await websocket.send_json(response.model_dump(mode="json"))
        except WebSocketDisconnect:
            if active_session_id is not None:
                sessions.remove(active_session_id)

    return application


app = create_app()
