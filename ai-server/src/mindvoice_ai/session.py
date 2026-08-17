from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
import time
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
    last_activity: float = 0.0

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
    def __init__(self, *, ttl_seconds: float = 300.0, clock=time.monotonic) -> None:
        if ttl_seconds <= 0:
            raise ValueError("ttl_seconds must be positive")
        self._sessions: dict[UUID, SessionBuffer] = {}
        self._ttl_seconds = ttl_seconds
        self._clock = clock

    def append(self, frame: SequencedFrame) -> tuple[SessionBuffer, bool]:
        now = self._clock()
        self.remove_stale(now=now)
        buffer = self._sessions.setdefault(frame.sessionId, SessionBuffer())
        accepted = buffer.append(frame)
        if accepted:
            buffer.last_activity = now
        return buffer, accepted

    def remove(self, session_id: UUID) -> None:
        self._sessions.pop(session_id, None)

    def remove_stale(self, *, now: float | None = None) -> int:
        current = self._clock() if now is None else now
        stale_ids = [
            session_id
            for session_id, buffer in self._sessions.items()
            if current - buffer.last_activity >= self._ttl_seconds
        ]
        for session_id in stale_ids:
            self._sessions.pop(session_id, None)
        return len(stale_ids)

    def __len__(self) -> int:
        return len(self._sessions)
