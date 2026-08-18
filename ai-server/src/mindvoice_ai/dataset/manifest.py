from __future__ import annotations

import csv
import re
from dataclasses import dataclass
from pathlib import Path

ALLOWED_SPLITS = {"train", "validation", "test"}
REQUIRED_COLUMNS = {
    "sample_id",
    "input_path",
    "class_id",
    "label",
    "stable_key",
    "signer_id",
    "split",
}
SAFE_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]*$")
STABLE_KEY = re.compile(r"^[A-Z][A-Z0-9_]*$")


@dataclass(frozen=True)
class ManifestEntry:
    sample_id: str
    input_path: Path
    class_id: int
    label: str
    stable_key: str
    signer_id: str
    split: str


def load_manifest(path: Path, *, require_files: bool = True) -> list[ManifestEntry]:
    if not path.is_file():
        raise FileNotFoundError(f"manifest not found: {path}")

    entries: list[ManifestEntry] = []
    sample_ids: set[str] = set()
    signer_splits: dict[str, str] = {}
    class_definitions: dict[int, tuple[str, str]] = {}
    stable_key_classes: dict[str, int] = {}

    with path.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        missing = REQUIRED_COLUMNS - set(reader.fieldnames or [])
        if missing:
            raise ValueError(f"manifest is missing columns: {sorted(missing)}")

        for line_number, row in enumerate(reader, start=2):
            values = {key: (row.get(key) or "").strip() for key in REQUIRED_COLUMNS}
            if not all(values.values()):
                raise ValueError(f"manifest line {line_number} contains an empty field")
            if not SAFE_ID.fullmatch(values["sample_id"]):
                raise ValueError(f"invalid sample_id on line {line_number}")
            if not SAFE_ID.fullmatch(values["signer_id"]):
                raise ValueError(f"invalid signer_id on line {line_number}")
            if not STABLE_KEY.fullmatch(values["stable_key"]):
                raise ValueError(f"invalid stable_key on line {line_number}")
            if values["split"] not in ALLOWED_SPLITS:
                raise ValueError(f"invalid split on line {line_number}: {values['split']}")
            if values["sample_id"] in sample_ids:
                raise ValueError(f"duplicate sample_id: {values['sample_id']}")
            try:
                class_id = int(values["class_id"])
            except ValueError as error:
                raise ValueError(
                    f"invalid class_id on line {line_number}: {values['class_id']}"
                ) from error
            if class_id < 0:
                raise ValueError(f"class_id must be non-negative on line {line_number}")

            definition = (values["stable_key"], values["label"])
            previous_definition = class_definitions.setdefault(class_id, definition)
            if previous_definition != definition:
                raise ValueError(f"class_id {class_id} has conflicting definitions")
            previous_class_id = stable_key_classes.setdefault(
                values["stable_key"], class_id
            )
            if previous_class_id != class_id:
                raise ValueError(
                    f"stable_key {values['stable_key']} maps to multiple class IDs"
                )

            previous_split = signer_splits.setdefault(
                values["signer_id"], values["split"]
            )
            if previous_split != values["split"]:
                raise ValueError(
                    f"signer {values['signer_id']} appears in multiple splits"
                )

            input_path = Path(values["input_path"])
            if not input_path.is_absolute():
                input_path = (path.parent / input_path).resolve()
            if require_files and not input_path.is_file():
                raise FileNotFoundError(
                    f"input video on line {line_number} not found: {input_path}"
                )

            entries.append(
                ManifestEntry(
                    sample_id=values["sample_id"],
                    input_path=input_path,
                    class_id=class_id,
                    label=values["label"],
                    stable_key=values["stable_key"],
                    signer_id=values["signer_id"],
                    split=values["split"],
                )
            )
            sample_ids.add(values["sample_id"])

    if not entries:
        raise ValueError("manifest contains no samples")
    return entries
