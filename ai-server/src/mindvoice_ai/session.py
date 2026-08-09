from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
from typing import Protocol
from uuid import UUID

from .settings import SEQUENCE_LENGTH


class SequencedFrame(Protocol):
    sessionId: UUID
    sequence: int


@dataclass
class SessionBuffer:
    frames: deque[SequencedFrame] = field(
        default_factory=lambda: deque(maxlen=SEQUENCE_LENGTH)
    )
    last_sequence: int = -1
    stabilizer: object | None = None

    def append(self, frame: SequencedFrame) -> bool:
        if frame.sequence <= self.last_sequence:
            return False
        self.frames.append(frame)
        self.last_sequence = frame.sequence
        return True

    @property
    def ready(self) -> bool:
        return len(self.frames) == SEQUENCE_LENGTH


class SessionStore:
    def __init__(self) -> None:
        self._sessions: dict[UUID, SessionBuffer] = {}

    def append(self, frame: SequencedFrame) -> tuple[SessionBuffer, bool]:
        buffer = self._sessions.setdefault(frame.sessionId, SessionBuffer())
        accepted = buffer.append(frame)
        return buffer, accepted

    def remove(self, session_id: UUID) -> None:
        self._sessions.pop(session_id, None)
