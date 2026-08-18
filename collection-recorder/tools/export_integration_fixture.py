from __future__ import annotations

import argparse
import json
from pathlib import Path
from uuid import UUID

import numpy as np


def export_fixture(
    dataset_path: Path,
    sample_id: str,
    output_dir: Path,
    *,
    session_id: UUID,
    call_id: UUID,
    fps: int = 15,
    trailing_repeat_frames: int = 2,
) -> dict[str, object]:
    if fps <= 0:
        raise ValueError("fps must be positive")
    if trailing_repeat_frames < 0:
        raise ValueError("trailing_repeat_frames cannot be negative")

    with np.load(dataset_path, allow_pickle=False) as archive:
        sample_ids = archive["sample_ids"].astype(str)
        matches = np.flatnonzero(sample_ids == sample_id)
        if len(matches) != 1:
            raise ValueError(f"sample ID not found exactly once: {sample_id}")
        index = int(matches[0])
        sequence = archive["features"][index].astype(np.float32)
        class_id = int(archive["labels"][index])
        label_keys = archive["label_keys"].astype(str)
        label_names = archive["label_names"].astype(str)
        signer_id = str(archive["signer_ids"][index])
        split = str(archive["splits"][index])

    if sequence.shape != (30, 258):
        raise ValueError(f"expected a 30 x 258 sequence, received {sequence.shape}")
    stream = np.concatenate(
        (sequence, np.repeat(sequence[-1:], trailing_repeat_frames, axis=0)), axis=0
    )
    timestamp_step_ms = round(1000 / fps)
    ai_messages = []
    spring_messages = []
    for sequence_number, features in enumerate(stream):
        timestamp_ms = sequence_number * timestamp_step_ms
        common = {
            "sessionId": str(session_id),
            "callId": str(call_id),
            "timestampMs": timestamp_ms,
            "features": [float(value) for value in features],
        }
        ai_messages.append(
            {
                "type": "landmark_frame",
                "sequence": sequence_number,
                **common,
            }
        )
        spring_messages.append({"seq": sequence_number, **common})

    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "ai-inference-frames.jsonl").write_text(
        "\n".join(json.dumps(message, ensure_ascii=False) for message in ai_messages) + "\n",
        encoding="utf-8",
    )
    (output_dir / "spring-landmark-frames.jsonl").write_text(
        "\n".join(json.dumps(message, ensure_ascii=False) for message in spring_messages)
        + "\n",
        encoding="utf-8",
    )
    metadata = {
        "sourceDataset": str(dataset_path),
        "sampleId": sample_id,
        "signerId": signer_id,
        "split": split,
        "expectedStableKey": str(label_keys[class_id]),
        "expectedLabel": str(label_names[class_id]),
        "featureShape": [30, 258],
        "streamMessages": len(ai_messages),
        "fps": fps,
        "trailingRepeatedFrames": trailing_repeat_frames,
        "sessionId": str(session_id),
        "callId": str(call_id),
    }
    (output_dir / "metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (output_dir / "README.md").write_text(
        "# Spring-AI Integration Fixture\n\n"
        "This fixture is extracted from a real video and uses the same normalized 258-value "
        "feature vectors used during model training.\n\n"
        "- `ai-inference-frames.jsonl`: 32 messages in the exact AI WebSocket contract.\n"
        "- `spring-landmark-frames.jsonl`: relay-oriented messages using `seq`, matching the "
        "Spring-side naming. Spring must map `seq` to the AI field `sequence`.\n"
        "- Send one JSON line at a time in file order, preserving the same session and call IDs.\n"
        "- The final two repeated frames let the AI service satisfy its three-window stability rule.\n"
        "- Both IDs are UUIDs. Do not use values such as `call-123` when sending to the AI server.\n",
        encoding="utf-8",
    )
    return metadata


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export a real 30-frame landmark sequence for Spring-AI integration testing."
    )
    parser.add_argument("--dataset", required=True, type=Path)
    parser.add_argument("--sample-id", required=True)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument(
        "--session-id", type=UUID, default=UUID("11111111-1111-1111-1111-111111111111")
    )
    parser.add_argument(
        "--call-id", type=UUID, default=UUID("22222222-2222-2222-2222-222222222222")
    )
    parser.add_argument("--fps", type=int, default=15)
    parser.add_argument("--trailing-repeat-frames", type=int, default=2)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    metadata = export_fixture(
        args.dataset,
        args.sample_id,
        args.output_dir,
        session_id=args.session_id,
        call_id=args.call_id,
        fps=args.fps,
        trailing_repeat_frames=args.trailing_repeat_frames,
    )
    print(json.dumps(metadata, ensure_ascii=False))


if __name__ == "__main__":
    main()
