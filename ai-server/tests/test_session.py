import unittest
from dataclasses import dataclass
from uuid import uuid4

from mindvoice_ai.session import SessionStore
from mindvoice_ai.settings import SEQUENCE_LENGTH


@dataclass
class Frame:
    sessionId: object
    sequence: int


def make_frame(session_id, sequence: int) -> Frame:
    return Frame(sessionId=session_id, sequence=sequence)


class SessionStoreTest(unittest.TestCase):
    def test_buffer_becomes_ready_at_sequence_length(self) -> None:
        store = SessionStore()
        session_id = uuid4()

        for sequence in range(SEQUENCE_LENGTH):
            buffer, accepted = store.append(make_frame(session_id, sequence))
            self.assertTrue(accepted)

        self.assertTrue(buffer.ready)
        self.assertEqual(SEQUENCE_LENGTH, len(buffer.frames))

    def test_rejects_duplicate_or_older_frames(self) -> None:
        store = SessionStore()
        session_id = uuid4()

        _, first_accepted = store.append(make_frame(session_id, 2))
        _, duplicate_accepted = store.append(make_frame(session_id, 2))
        buffer, older_accepted = store.append(make_frame(session_id, 1))

        self.assertTrue(first_accepted)
        self.assertFalse(duplicate_accepted)
        self.assertFalse(older_accepted)
        self.assertEqual(1, len(buffer.frames))


if __name__ == "__main__":
    unittest.main()
