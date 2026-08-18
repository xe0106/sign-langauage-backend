from dataclasses import dataclass

import numpy as np
import pytest

from mindvoice_ai.preprocessing.features import extract_features
from mindvoice_ai.settings import FEATURE_DIMENSION


@dataclass
class Landmark:
    x: float
    y: float
    z: float
    visibility: float = 0.0


def test_missing_landmarks_produce_zero_vector() -> None:
    features, presence = extract_features(None, None, None)

    assert features.shape == (FEATURE_DIMENSION,)
    assert features.dtype == np.float32
    assert np.count_nonzero(features) == 0
    assert presence.tolist() == [False, False, False]


def test_normalizes_present_landmarks_by_shoulders() -> None:
    pose = [Landmark(1.0, 1.0, 0.0, 0.8) for _ in range(33)]
    pose[11] = Landmark(0.0, 1.0, 0.0, 0.9)
    pose[12] = Landmark(2.0, 1.0, 0.0, 0.9)
    left_hand = [Landmark(3.0, 1.0, 0.0) for _ in range(21)]

    features, presence = extract_features(pose, left_hand, None)

    # Pose is flattened as x, y, z, visibility. The first pose point is at origin.
    assert features[:4].tolist() == pytest.approx([0.0, 0.0, 0.0, 0.8])
    # The first left-hand x follows 33 * 4 pose values: (3 - 1) / 2 = 1.
    assert features[132:135].tolist() == pytest.approx([1.0, 0.0, 0.0])
    assert np.count_nonzero(features[195:]) == 0
    assert presence.tolist() == [True, True, False]


def test_rejects_wrong_landmark_count() -> None:
    with pytest.raises(ValueError, match="expected 33 landmarks"):
        extract_features([Landmark(0.0, 0.0, 0.0)], None, None)
