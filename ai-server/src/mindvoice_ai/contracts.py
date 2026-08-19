from __future__ import annotations

import math
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator

from .settings import FEATURE_DIMENSION, SEQUENCE_LENGTH


class LandmarkFrame(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["landmark_frame"]
    sessionId: UUID
    callId: UUID | None = None
    sequence: int = Field(ge=0)
    timestampMs: int = Field(ge=0)
    features: list[float] = Field(min_length=FEATURE_DIMENSION, max_length=FEATURE_DIMENSION)

    @field_validator("features")
    @classmethod
    def require_finite_features(cls, values: list[float]) -> list[float]:
        if not all(math.isfinite(value) for value in values):
            raise ValueError("features must contain only finite numbers")
        return values


class SessionEnd(BaseModel):
    """Marks the end of one sign utterance and requests final inference."""

    model_config = ConfigDict(extra="forbid")

    type: Literal["session_end"]
    sessionId: UUID
    callId: UUID | None = None
    timestampMs: int = Field(ge=0)


class BufferStatus(BaseModel):
    type: Literal["status"] = "status"
    status: Literal["collecting", "analyzing", "completed_no_prediction", "model_unavailable"]
    sessionId: UUID
    callId: UUID | None = None
    bufferedFrames: int = Field(ge=0)
    requiredFrames: int = SEQUENCE_LENGTH


class ErrorMessage(BaseModel):
    type: Literal["error"] = "error"
    code: str
    message: str
    sessionId: UUID | None = None
    callId: UUID | None = None


class PredictionMessage(BaseModel):
    type: Literal["prediction"] = "prediction"
    sessionId: UUID
    callId: UUID | None = None
    classId: int = Field(ge=0)
    label: str
    confidence: float = Field(ge=0.0, le=1.0)
    stable: Literal[True] = True
    startTimeMs: int = Field(ge=0)
    endTimeMs: int = Field(ge=0)
    modelVersion: str
