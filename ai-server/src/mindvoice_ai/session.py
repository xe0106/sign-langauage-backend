from __future__ import annotations

from dataclasses import dataclass, field
import time
from typing import Protocol
from uuid import UUID

class SequencedFrame(Protocol):
    sessionId: UUID
    sequence: int


@dataclass
class SessionBuffer:
    # Keep the whole utterance. It is resampled to the model's fixed length only
    # after the Android client explicitly ends this session.
    frames: list[SequencedFrame] = field(default_factory=list)
    last_sequence: int = -1
    last_activity: float = 0.0

    def append(self, frame: SequencedFrame) -> bool:
        if frame.sequence <= self.last_sequence:
            return False
        self.frames.append(frame)
        self.last_sequence = frame.sequence
        return True

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

    def pop(self, session_id: UUID) -> SessionBuffer | None:
        """Finish a session so a UUID cannot produce a second prediction."""
        return self._sessions.pop(session_id, None)

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
