import csv
import json
from pathlib import Path

import numpy as np

from mindvoice_ai.dataset import batch
from mindvoice_ai.settings import FEATURE_DIMENSION


def test_preprocess_manifest_writes_npz_and_index(
    tmp_path: Path, monkeypatch
) -> None:
    video = tmp_path / "video.mp4"
    video.touch()
    manifest = tmp_path / "manifest.csv"
    with manifest.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.writer(destination)
        writer.writerow(
            [
                "sample_id",
                "input_path",
                "class_id",
                "label",
                "stable_key",
                "signer_id",
                "split",
            ]
        )
        writer.writerow(
            ["hello-s001-001", "video.mp4", 0, "안녕하세요", "HELLO", "S001", "train"]
        )

    def fake_extract_video(*args, **kwargs):
        return (
            np.ones((2, FEATURE_DIMENSION), dtype=np.float32),
            np.ones((2, 3), dtype=np.bool_),
            np.asarray([0, 33], dtype=np.int64),
            {"sampledFrameCount": 2},
        )

    monkeypatch.setattr(batch, "extract_video", fake_extract_video)
    output_root = tmp_path / "processed"
    records = batch.preprocess_manifest(
        manifest, output_root, tmp_path / "holistic.task"
    )

    output = output_root / "train" / "HELLO" / "S001" / "hello-s001-001.npz"
    assert output.is_file()
    assert records[0]["classId"] == 0
    index_record = json.loads(
        (output_root / "dataset-index.jsonl").read_text(encoding="utf-8")
    )
    assert index_record["processedPath"] == (
        "train/HELLO/S001/hello-s001-001.npz"
    )
