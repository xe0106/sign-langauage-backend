from __future__ import annotations

import argparse
import asyncio
import json
from pathlib import Path
from typing import Any


def load_frames(path: Path) -> list[dict[str, Any]]:
    if not path.is_file():
        raise FileNotFoundError(f"fixture file not found: {path}")
    frames = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
    if not frames:
        raise ValueError("fixture file contains no frames")
    for expected_sequence, frame in enumerate(frames):
        sequence = frame.get("sequence")
        if sequence != expected_sequence:
            raise ValueError(
                f"frame {expected_sequence} must use sequence {expected_sequence}, received {sequence}"
            )
        if len(frame.get("features", [])) != 258:
            raise ValueError(f"frame {expected_sequence} must contain 258 features")
    return frames


async def replay(url: str, frames: list[dict[str, Any]], speed: float) -> list[dict[str, Any]]:
    if speed <= 0:
        raise ValueError("speed must be positive")

    from websockets.asyncio.client import connect

    responses: list[dict[str, Any]] = []
    async with connect(url) as websocket:
        previous_timestamp = frames[0]["timestampMs"]
        for index, frame in enumerate(frames):
            if index:
                delay_ms = max(0, frame["timestampMs"] - previous_timestamp)
                await asyncio.sleep(delay_ms / 1000 / speed)
            await websocket.send(json.dumps(frame, ensure_ascii=False))
            response = json.loads(await websocket.recv())
            responses.append(response)
            print(json.dumps({"sentSequence": frame["sequence"], "response": response}, ensure_ascii=False))
            previous_timestamp = frame["timestampMs"]
        session_end = {
            "type": "session_end",
            "sessionId": frames[-1]["sessionId"],
            "timestampMs": frames[-1]["timestampMs"],
        }
        if frames[-1].get("callId") is not None:
            session_end["callId"] = frames[-1]["callId"]
        await websocket.send(json.dumps(session_end, ensure_ascii=False))
        response = json.loads(await websocket.recv())
        responses.append(response)
        print(json.dumps({"sentType": "session_end", "response": response}, ensure_ascii=False))
    return responses


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Replay a real landmark fixture to the AI inference WebSocket."
    )
    parser.add_argument("--url", required=True, help="Example: ws://AI_PUBLIC_IP:8000/ws/inference")
    parser.add_argument("--frames", required=True, type=Path)
    parser.add_argument(
        "--speed",
        type=float,
        default=1.0,
        help="Replay multiplier; 1.0 follows fixture timestamps and 10.0 is ten times faster.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    frames = load_frames(args.frames)
    responses = asyncio.run(replay(args.url, frames, args.speed))
    prediction_count = sum(response.get("type") == "prediction" for response in responses)
    error_count = sum(response.get("type") == "error" for response in responses)
    print(
        json.dumps(
            {
                "sentFrames": len(frames),
                "sentSessionEnd": True,
                "predictionResponses": prediction_count,
                "errorResponses": error_count,
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
