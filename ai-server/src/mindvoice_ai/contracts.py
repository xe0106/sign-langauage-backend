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


class BufferStatus(BaseModel):
    type: Literal["status"] = "status"
    status: Literal["warming_up", "model_unavailable"]
    bufferedFrames: int = Field(ge=0)
    requiredFrames: int = SEQUENCE_LENGTH


class ErrorMessage(BaseModel):
    type: Literal["error"] = "error"
    code: str
    message: str
