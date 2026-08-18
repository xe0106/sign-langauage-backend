from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import cv2
import mediapipe as mp
import numpy as np

from mindvoice_ai.preprocessing.features import extract_features
from mindvoice_ai.settings import FEATURE_DIMENSION


def create_landmarker(model_path: Path) -> Any:
    if not model_path.is_file():
        raise FileNotFoundError(f"MediaPipe model not found: {model_path}")
    options = mp.tasks.vision.HolisticLandmarkerOptions(
        base_options=mp.tasks.BaseOptions(model_asset_path=str(model_path)),
        running_mode=mp.tasks.vision.RunningMode.VIDEO,
        min_face_detection_confidence=0.5,
        min_face_landmarks_confidence=0.5,
        min_pose_detection_confidence=0.5,
        min_pose_landmarks_confidence=0.5,
        min_hand_landmarks_confidence=0.5,
        output_face_blendshapes=False,
        output_segmentation_mask=False,
    )
    return mp.tasks.vision.HolisticLandmarker.create_from_options(options)


def extract_video(
    input_path: Path,
    model_path: Path,
    *,
    target_fps: float = 30.0,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, dict[str, Any]]:
    if target_fps <= 0:
        raise ValueError("target_fps must be positive")
    if not input_path.is_file():
        raise FileNotFoundError(f"input video not found: {input_path}")

    capture = cv2.VideoCapture(str(input_path))
    if not capture.isOpened():
        raise ValueError(f"could not open video: {input_path}")

    source_fps = float(capture.get(cv2.CAP_PROP_FPS))
    if not np.isfinite(source_fps) or source_fps <= 0:
        source_fps = target_fps
    frame_step_ms = 1000.0 / target_fps
    next_sample_ms = 0.0

    feature_rows: list[np.ndarray] = []
    presence_rows: list[np.ndarray] = []
    timestamps: list[int] = []
    source_frame_index = 0

    try:
        with create_landmarker(model_path) as landmarker:
            while True:
                success, bgr_frame = capture.read()
                if not success:
                    break
                source_time_ms = source_frame_index * 1000.0 / source_fps
                source_frame_index += 1
                if source_time_ms + 1e-6 < next_sample_ms:
                    continue

                timestamp_ms = int(round(source_time_ms))
                rgb_frame = cv2.cvtColor(bgr_frame, cv2.COLOR_BGR2RGB)
                image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
                result = landmarker.detect_for_video(image, timestamp_ms)
                features, presence = extract_features(
                    result.pose_landmarks,
                    result.left_hand_landmarks,
                    result.right_hand_landmarks,
                )
                feature_rows.append(features)
                presence_rows.append(presence)
                timestamps.append(timestamp_ms)
                next_sample_ms += frame_step_ms
    finally:
        capture.release()

    if not feature_rows:
        raise ValueError(f"video produced no frames: {input_path}")

    features_array = np.stack(feature_rows).astype(np.float32, copy=False)
    presence_array = np.stack(presence_rows).astype(np.bool_, copy=False)
    timestamp_array = np.asarray(timestamps, dtype=np.int64)
    metadata = {
        "sourceFile": input_path.name,
        "sourceFps": source_fps,
        "targetFps": target_fps,
        "sourceFrameCount": source_frame_index,
        "sampledFrameCount": len(feature_rows),
        "featureDimension": FEATURE_DIMENSION,
        "featureOrder": "pose_33x(x,y,z,visibility),left_hand_21x(x,y,z),right_hand_21x(x,y,z)",
        "normalization": "shoulder_center_and_width_v1",
    }
    return features_array, presence_array, timestamp_array, metadata


def save_sequence(
    output_path: Path,
    features: np.ndarray,
    presence: np.ndarray,
    timestamps_ms: np.ndarray,
    metadata: dict[str, Any],
) -> None:
    if features.ndim != 2 or features.shape[1] != FEATURE_DIMENSION:
        raise ValueError(f"features must have shape (frames, {FEATURE_DIMENSION})")
    if presence.shape != (features.shape[0], 3):
        raise ValueError("presence must have shape (frames, 3)")
    if timestamps_ms.shape != (features.shape[0],):
        raise ValueError("timestamps_ms must have shape (frames,)")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(
        output_path,
        features=features.astype(np.float32, copy=False),
        presence=presence.astype(np.bool_, copy=False),
        timestamps_ms=timestamps_ms.astype(np.int64, copy=False),
        metadata=np.asarray(json.dumps(metadata, ensure_ascii=False)),
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Extract normalized MediaPipe Holistic landmarks from a video."
    )
    parser.add_argument("--input", required=True, type=Path, help="input video path")
    parser.add_argument("--output", required=True, type=Path, help="output .npz path")
    parser.add_argument("--model", required=True, type=Path, help="Holistic .task path")
    parser.add_argument("--label", required=True, help="Korean sign label")
    parser.add_argument("--stable-key", required=True, help="stable ASCII class key")
    parser.add_argument("--signer-id", required=True, help="anonymous signer ID")
    parser.add_argument("--target-fps", type=float, default=30.0)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    features, presence, timestamps_ms, metadata = extract_video(
        args.input, args.model, target_fps=args.target_fps
    )
    metadata.update(
        {
            "label": args.label,
            "stableKey": args.stable_key,
            "signerId": args.signer_id,
        }
    )
    save_sequence(args.output, features, presence, timestamps_ms, metadata)
    print(
        json.dumps(
            {
                "output": str(args.output),
                "shape": list(features.shape),
                "detectedPoseFrames": int(presence[:, 0].sum()),
                "detectedLeftHandFrames": int(presence[:, 1].sum()),
                "detectedRightHandFrames": int(presence[:, 2].sum()),
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
