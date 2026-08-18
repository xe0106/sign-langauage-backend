from __future__ import annotations

from dataclasses import asdict, dataclass

import numpy as np

from mindvoice_ai.preprocessing.features import HAND_LANDMARK_COUNT, POSE_LANDMARK_COUNT
from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH


@dataclass(frozen=True)
class LandmarkAugmentationConfig:
    """Small landmark-only transforms used exclusively for the train split."""

    copies: int = 0
    jitter_std: float = 0.01
    max_frame_shift: int = 3
    min_speed: float = 0.9
    max_speed: float = 1.1

    def validate(self) -> None:
        if self.copies < 0:
            raise ValueError("augmentation copies cannot be negative")
        if self.jitter_std < 0:
            raise ValueError("augmentation jitter_std cannot be negative")
        if self.max_frame_shift < 0:
            raise ValueError("augmentation max_frame_shift cannot be negative")
        if self.min_speed <= 0 or self.max_speed <= 0:
            raise ValueError("augmentation speeds must be positive")
        if self.min_speed > self.max_speed:
            raise ValueError("augmentation min_speed cannot exceed max_speed")

    def metadata(self) -> dict[str, float | int]:
        return asdict(self)


def augment_training_features(
    features: np.ndarray, labels: np.ndarray, *, config: LandmarkAugmentationConfig, seed: int
) -> tuple[np.ndarray, np.ndarray]:
    """Append deterministic augmented copies while leaving original samples intact."""
    config.validate()
    if features.ndim != 3 or features.shape[1:] != (SEQUENCE_LENGTH, FEATURE_DIMENSION):
        raise ValueError(
            f"features must have shape (samples, {SEQUENCE_LENGTH}, {FEATURE_DIMENSION})"
        )
    if labels.ndim != 1 or len(labels) != len(features):
        raise ValueError("labels must contain one value per feature sequence")
    if config.copies == 0:
        return features, labels

    rng = np.random.default_rng(seed)
    augmented = [features]
    for _ in range(config.copies):
        augmented.append(
            np.stack(
                [_augment_sequence(sequence, config=config, rng=rng) for sequence in features],
                axis=0,
            )
        )
    return np.concatenate(augmented, axis=0), np.tile(labels, config.copies + 1)


def _augment_sequence(
    sequence: np.ndarray, *, config: LandmarkAugmentationConfig, rng: np.random.Generator
) -> np.ndarray:
    speed = float(rng.uniform(config.min_speed, config.max_speed))
    augmented = _resample_temporally(sequence, speed)
    if config.max_frame_shift:
        shift = int(rng.integers(-config.max_frame_shift, config.max_frame_shift + 1))
        augmented = _shift_frames(augmented, shift)
    if config.jitter_std:
        augmented = _add_coordinate_jitter(augmented, config.jitter_std, rng)
    return augmented.astype(np.float32, copy=False)


def _resample_temporally(sequence: np.ndarray, speed: float) -> np.ndarray:
    target = np.arange(SEQUENCE_LENGTH, dtype=np.float32)
    center = (SEQUENCE_LENGTH - 1) / 2.0
    source = np.clip(center + (target - center) * speed, 0, SEQUENCE_LENGTH - 1)
    lower = np.floor(source).astype(np.int64)
    upper = np.ceil(source).astype(np.int64)
    weight = (source - lower).reshape(-1, 1)
    return sequence[lower] * (1.0 - weight) + sequence[upper] * weight


def _shift_frames(sequence: np.ndarray, shift: int) -> np.ndarray:
    source = np.clip(np.arange(SEQUENCE_LENGTH) - shift, 0, SEQUENCE_LENGTH - 1)
    return sequence[source]


def _add_coordinate_jitter(
    sequence: np.ndarray, jitter_std: float, rng: np.random.Generator
) -> np.ndarray:
    result = sequence.copy()
    pose = result[:, : POSE_LANDMARK_COUNT * 4].reshape(SEQUENCE_LENGTH, POSE_LANDMARK_COUNT, 4)
    pose_present = pose[:, :, 3] > 0
    pose_noise = rng.normal(0.0, jitter_std, size=pose[:, :, :3].shape).astype(np.float32)
    pose[:, :, :3] += pose_noise * pose_present[:, :, None]

    hand_start = POSE_LANDMARK_COUNT * 4
    for start in (hand_start, hand_start + HAND_LANDMARK_COUNT * 3):
        hand = result[:, start : start + HAND_LANDMARK_COUNT * 3].reshape(
            SEQUENCE_LENGTH, HAND_LANDMARK_COUNT, 3
        )
        hand_present = np.any(hand != 0.0, axis=2)
        hand_noise = rng.normal(0.0, jitter_std, size=hand.shape).astype(np.float32)
        hand += hand_noise * hand_present[:, :, None]
    return result
