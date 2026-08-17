import csv
from pathlib import Path

import pytest

from mindvoice_ai.dataset.manifest import load_manifest


FIELDS = [
    "sample_id",
    "input_path",
    "class_id",
    "label",
    "stable_key",
    "signer_id",
    "split",
]


def write_manifest(path: Path, rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(destination, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def row(**overrides: str) -> dict[str, str]:
    value = {
        "sample_id": "hello-s001-001",
        "input_path": "video.mp4",
        "class_id": "0",
        "label": "안녕하세요",
        "stable_key": "HELLO",
        "signer_id": "S001",
        "split": "train",
    }
    value.update(overrides)
    return value


def test_load_manifest_resolves_paths_and_class_id(tmp_path: Path) -> None:
    video = tmp_path / "video.mp4"
    video.touch()
    manifest = tmp_path / "manifest.csv"
    write_manifest(manifest, [row()])

    entries = load_manifest(manifest)

    assert entries[0].input_path == video.resolve()
    assert entries[0].class_id == 0


def test_load_manifest_rejects_signer_split_leakage(tmp_path: Path) -> None:
    manifest = tmp_path / "manifest.csv"
    write_manifest(
        manifest,
        [
            row(),
            row(sample_id="hello-s001-002", split="test"),
        ],
    )

    with pytest.raises(ValueError, match="multiple splits"):
        load_manifest(manifest, require_files=False)


def test_load_manifest_rejects_conflicting_class_mapping(tmp_path: Path) -> None:
    manifest = tmp_path / "manifest.csv"
    write_manifest(
        manifest,
        [
            row(),
            row(
                sample_id="thanks-s002-001",
                class_id="0",
                label="감사합니다",
                stable_key="THANKS",
                signer_id="S002",
            ),
        ],
    )

    with pytest.raises(ValueError, match="conflicting definitions"):
        load_manifest(manifest, require_files=False)
