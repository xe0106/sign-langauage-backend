from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, model_validator

from mindvoice_ai.settings import FEATURE_DIMENSION, SEQUENCE_LENGTH


class ModelLabel(BaseModel):
    model_config = ConfigDict(extra="forbid")

    classId: int = Field(ge=0)
    stableKey: str = Field(min_length=1)
    label: str = Field(min_length=1)


class ModelMetadata(BaseModel):
    model_config = ConfigDict(extra="allow")

    modelVersion: str = Field(min_length=1)
    sequenceLength: int
    featureDimension: int
    featureOrder: str
    normalization: str
    labels: list[ModelLabel] = Field(min_length=2)
    modelFile: str
    modelSha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    confidenceThreshold: float = Field(ge=0.0, le=1.0)
    stableWindowCount: int = Field(ge=1)
    noSignStableKey: str = "NO_SIGN"

    @model_validator(mode="after")
    def validate_contract(self) -> "ModelMetadata":
        if self.sequenceLength != SEQUENCE_LENGTH:
            raise ValueError(f"sequenceLength must be {SEQUENCE_LENGTH}")
        if self.featureDimension != FEATURE_DIMENSION:
            raise ValueError(f"featureDimension must be {FEATURE_DIMENSION}")
        class_ids = [item.classId for item in self.labels]
        if class_ids != list(range(len(self.labels))):
            raise ValueError("label class IDs must be ordered and contiguous from 0")
        stable_keys = [item.stableKey for item in self.labels]
        if len(set(stable_keys)) != len(stable_keys):
            raise ValueError("label stable keys must be unique")
        if self.noSignStableKey not in stable_keys:
            raise ValueError("labels must include the configured NO_SIGN class")
        if Path(self.modelFile).name != self.modelFile:
            raise ValueError("modelFile must be a file name, not a path")
        return self


@dataclass(frozen=True)
class LoadedModelPackage:
    model: Any
    metadata: ModelMetadata


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_model_package(package_dir: Path) -> LoadedModelPackage:
    metadata_path = package_dir / "metadata.json"
    if not metadata_path.is_file():
        raise FileNotFoundError(f"model metadata not found: {metadata_path}")
    try:
        raw_metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"invalid model metadata JSON: {metadata_path}") from error
    metadata = ModelMetadata.model_validate(raw_metadata)

    model_path = package_dir / metadata.modelFile
    if not model_path.is_file():
        raise FileNotFoundError(f"model file not found: {model_path}")
    actual_hash = file_sha256(model_path)
    if actual_hash != metadata.modelSha256:
        raise ValueError(
            f"model SHA-256 mismatch: expected {metadata.modelSha256}, got {actual_hash}"
        )

    import tensorflow as tf

    model = tf.keras.models.load_model(model_path, compile=False)
    expected_input = (None, SEQUENCE_LENGTH, FEATURE_DIMENSION)
    expected_output = (None, len(metadata.labels))
    if tuple(model.input_shape) != expected_input:
        raise ValueError(
            f"model input shape must be {expected_input}, got {model.input_shape}"
        )
    if tuple(model.output_shape) != expected_output:
        raise ValueError(
            f"model output shape must be {expected_output}, got {model.output_shape}"
        )
    return LoadedModelPackage(model=model, metadata=metadata)
