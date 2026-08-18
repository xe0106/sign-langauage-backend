from __future__ import annotations

from dataclasses import dataclass

from mindvoice_ai.inference.predictor import RawPrediction


@dataclass
class PredictionStabilizer:
    confidence_threshold: float
    stable_window_count: int
    no_sign_stable_key: str = "NO_SIGN"
    candidate_class_id: int | None = None
    candidate_count: int = 0
    last_emitted_class_id: int | None = None

    def observe(self, prediction: RawPrediction) -> RawPrediction | None:
        if prediction.confidence < self.confidence_threshold:
            self.candidate_class_id = None
            self.candidate_count = 0
            return None
        if prediction.stable_key == self.no_sign_stable_key:
            self.candidate_class_id = None
            self.candidate_count = 0
            self.last_emitted_class_id = None
            return None

        if self.candidate_class_id == prediction.class_id:
            self.candidate_count += 1
        else:
            self.candidate_class_id = prediction.class_id
            self.candidate_count = 1

        if self.candidate_count < self.stable_window_count:
            return None
        if self.last_emitted_class_id == prediction.class_id:
            return None
        self.last_emitted_class_id = prediction.class_id
        return prediction
