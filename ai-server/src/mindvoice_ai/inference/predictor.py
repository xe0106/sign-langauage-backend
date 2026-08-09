from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import numpy as np

from mindvoice_ai.inference.package import ModelMetadata, load_model_package
from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH


@dataclass(frozen=True)
class RawPrediction:
    class_id: int
    stable_key: str
    label: str
    confidence: float


class Predictor(Protocol):
    metadata: ModelMetadata

    def predict(self, features: np.ndarray) -> RawPrediction: ...


class KerasPredictor:
    def __init__(self, package_dir: Path) -> None:
        package = load_model_package(package_dir)
        self._model = package.model
        self.metadata = package.metadata

    def predict(self, features: np.ndarray) -> RawPrediction:
        if features.shape != (SEQUENCE_LENGTH, FEATURE_DIMENSION):
            raise ValueError(
                f"features must have shape ({SEQUENCE_LENGTH}, {FEATURE_DIMENSION})"
            )
        if not np.isfinite(features).all():
            raise ValueError("features contain non-finite values")
        probabilities = np.asarray(
            self._model(np.expand_dims(features.astype(np.float32), axis=0), training=False)
        )[0]
        if probabilities.shape != (len(self.metadata.labels),):
            raise ValueError("model returned an invalid probability vector")
        class_id = int(np.argmax(probabilities))
        label = self.metadata.labels[class_id]
        return RawPrediction(
            class_id=class_id,
            stable_key=label.stableKey,
            label=label.label,
            confidence=float(probabilities[class_id]),
        )
