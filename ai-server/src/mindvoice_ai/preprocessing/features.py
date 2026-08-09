from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

import numpy as np

from mindvoice_ai.settings import FEATURE_DIMENSION

POSE_LANDMARK_COUNT = 33
HAND_LANDMARK_COUNT = 21
LEFT_SHOULDER_INDEX = 11
RIGHT_SHOULDER_INDEX = 12
MIN_SHOULDER_SCALE = 1e-6


class LandmarkLike(Protocol):
    x: float
    y: float
    z: float


def _xyz(landmarks: Sequence[LandmarkLike] | None, expected: int) -> np.ndarray:
    if not landmarks:
        return np.zeros((expected, 3), dtype=np.float32)
    if len(landmarks) != expected:
        raise ValueError(f"expected {expected} landmarks, received {len(landmarks)}")
    return np.asarray(
        [[point.x, point.y, point.z] for point in landmarks], dtype=np.float32
    )


def _visibility(landmarks: Sequence[LandmarkLike] | None) -> np.ndarray:
    if not landmarks:
        return np.zeros((POSE_LANDMARK_COUNT, 1), dtype=np.float32)
    return np.asarray(
        [[getattr(point, "visibility", 0.0)] for point in landmarks],
        dtype=np.float32,
    )


def normalize_landmarks(
    pose_xyz: np.ndarray,
    left_hand_xyz: np.ndarray,
    right_hand_xyz: np.ndarray,
    *,
    pose_present: bool,
    left_hand_present: bool,
    right_hand_present: bool,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Normalize present landmarks around the shoulder center and shoulder width."""
    if not pose_present:
        return pose_xyz, left_hand_xyz, right_hand_xyz

    left_shoulder = pose_xyz[LEFT_SHOULDER_INDEX]
    right_shoulder = pose_xyz[RIGHT_SHOULDER_INDEX]
    origin = (left_shoulder + right_shoulder) / 2.0
    scale = float(np.linalg.norm(left_shoulder - right_shoulder))
    if not np.isfinite(scale) or scale < MIN_SHOULDER_SCALE:
        scale = 1.0

    pose_xyz = (pose_xyz - origin) / scale
    if left_hand_present:
        left_hand_xyz = (left_hand_xyz - origin) / scale
    if right_hand_present:
        right_hand_xyz = (right_hand_xyz - origin) / scale
    return pose_xyz, left_hand_xyz, right_hand_xyz


def extract_features(
    pose_landmarks: Sequence[LandmarkLike] | None,
    left_hand_landmarks: Sequence[LandmarkLike] | None,
    right_hand_landmarks: Sequence[LandmarkLike] | None,
    *,
    normalize: bool = True,
) -> tuple[np.ndarray, np.ndarray]:
    """Return a 258-value feature vector and pose/left/right presence flags."""
    presence = np.asarray(
        [
            bool(pose_landmarks),
            bool(left_hand_landmarks),
            bool(right_hand_landmarks),
        ],
        dtype=np.bool_,
    )
    pose_xyz = _xyz(pose_landmarks, POSE_LANDMARK_COUNT)
    left_xyz = _xyz(left_hand_landmarks, HAND_LANDMARK_COUNT)
    right_xyz = _xyz(right_hand_landmarks, HAND_LANDMARK_COUNT)
    visibility = _visibility(pose_landmarks)

    if normalize:
        pose_xyz, left_xyz, right_xyz = normalize_landmarks(
            pose_xyz,
            left_xyz,
            right_xyz,
            pose_present=bool(presence[0]),
            left_hand_present=bool(presence[1]),
            right_hand_present=bool(presence[2]),
        )

    pose = np.concatenate((pose_xyz, visibility), axis=1).reshape(-1)
    features = np.concatenate((pose, left_xyz.reshape(-1), right_xyz.reshape(-1)))
    features = features.astype(np.float32, copy=False)

    if features.shape != (FEATURE_DIMENSION,):
        raise RuntimeError(f"unexpected feature shape: {features.shape}")
    if not np.isfinite(features).all():
        raise ValueError("landmarks produced non-finite feature values")
    return features, presence
