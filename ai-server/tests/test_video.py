import json
from pathlib import Path

import cv2
import numpy as np
import pytest

from mindvoice_ai.preprocessing.video import extract_video, save_sequence
from mindvoice_ai.settings import FEATURE_DIMENSION

MODEL_PATH = Path(__file__).parents[1] / "models" / "holistic_landmarker.task"


def test_save_sequence_round_trip(tmp_path: Path) -> None:
    output = tmp_path / "sample.npz"
    features = np.zeros((2, FEATURE_DIMENSION), dtype=np.float32)
    presence = np.zeros((2, 3), dtype=np.bool_)
    timestamps = np.asarray([0, 33], dtype=np.int64)
    metadata = {"label": "안녕하세요", "signerId": "S001"}

    save_sequence(output, features, presence, timestamps, metadata)

    with np.load(output) as archive:
        assert archive["features"].shape == (2, FEATURE_DIMENSION)
        assert archive["presence"].shape == (2, 3)
        assert archive["timestamps_ms"].tolist() == [0, 33]
        assert json.loads(str(archive["metadata"])) == metadata


@pytest.mark.skipif(not MODEL_PATH.exists(), reason="local MediaPipe model is absent")
def test_extracts_black_video_with_real_mediapipe(tmp_path: Path) -> None:
    video_path = tmp_path / "black.avi"
    writer = cv2.VideoWriter(
        str(video_path), cv2.VideoWriter_fourcc(*"MJPG"), 30.0, (64, 64)
    )
    if not writer.isOpened():
        pytest.skip("OpenCV MJPG writer is unavailable")
    for _ in range(3):
        writer.write(np.zeros((64, 64, 3), dtype=np.uint8))
    writer.release()

    features, presence, timestamps, metadata = extract_video(
        video_path, MODEL_PATH, target_fps=30.0
    )

    assert features.shape == (3, FEATURE_DIMENSION)
    assert presence.shape == (3, 3)
    assert not presence.any()
    assert timestamps.tolist() == [0, 33, 67]
    assert metadata["sampledFrameCount"] == 3
