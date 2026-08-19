"""Replay landmark fixtures through Spring STOMP instead of directly to AI."""

from __future__ import annotations

import argparse
import asyncio
import json
import time
from uuid import uuid4
from pathlib import Path


def stomp_frame(command: str, headers: dict[str, str] | None = None, body: str = "") -> str:
    header_lines = [command]
    for key, value in (headers or {}).items():
        header_lines.append(f"{key}:{value}")
    return "\n".join(header_lines) + "\n\n" + body + "\x00"


def parse_stomp_frame(raw: str) -> tuple[str, dict[str, str], str]:
    frame = raw.rstrip("\x00")
    header_block, _, body = frame.partition("\n\n")
    lines = header_block.splitlines()
    command = lines[0] if lines else ""
    headers = dict(line.split(":", 1) for line in lines[1:] if ":" in line)
    return command, headers, body


def load_frames(
    path: Path, call_id: str, sender_id: int, session_id: str
) -> list[dict[str, object]]:
    frames: list[dict[str, object]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        source = json.loads(line)
        features = source.get("features")
        if not isinstance(features, list) or len(features) != 258:
            raise ValueError(f"line {line_number}: features must contain 258 values")
        sequence = source.get("sequence", source.get("seq"))
        if not isinstance(sequence, int):
            raise ValueError(f"line {line_number}: sequence or seq must be an integer")
        source_session_id = source.get("sessionId")
        if not isinstance(source_session_id, str) or not source_session_id:
            raise ValueError(f"line {line_number}: sessionId is required")
        frames.append(
            {
                "type": "landmark_frame",
                "sessionId": session_id,
                "callId": call_id,
                "senderId": sender_id,
                "sequence": sequence,
                "timestampMs": int(source.get("timestampMs", time.time() * 1000)),
                "features": features,
            }
        )
    if not frames:
        raise ValueError("fixture contains no frames")
    return frames


async def receive_messages(websocket: object) -> None:
    async for raw in websocket:  # type: ignore[attr-defined]
        command, headers, body = parse_stomp_frame(raw)
        if command == "MESSAGE":
            print(json.dumps({"destination": headers.get("destination"), "body": json.loads(body)}, ensure_ascii=False))
        elif command == "ERROR":
            raise RuntimeError(f"Spring STOMP error: {body or headers}")


async def replay(args: argparse.Namespace) -> None:
    from websockets.asyncio.client import connect

    interval_seconds = 1.0 / args.fps
    session_ids: list[str] = []
    sent_frames = 0

    async with connect(args.url) as websocket:
        await websocket.send(
            stomp_frame(
                "CONNECT",
                {"accept-version": "1.2", "host": args.host, "heart-beat": "0,0"},
            )
        )
        command, _, body = parse_stomp_frame(await websocket.recv())
        if command != "CONNECTED":
            raise RuntimeError(f"STOMP connection failed: {command} {body}")

        await websocket.send(
            stomp_frame("SUBSCRIBE", {"id": "subtitle-0", "destination": f"/sub/call/{args.call_id}", "ack": "auto"})
        )
        await websocket.send(
            stomp_frame("SUBSCRIBE", {"id": "error-0", "destination": f"/sub/errors/{args.sender_id}", "ack": "auto"})
        )

        receiver = asyncio.create_task(receive_messages(websocket))
        try:
            for utterance_index in range(args.repeat):
                session_id = args.session_id if utterance_index == 0 and args.session_id else str(uuid4())
                session_ids.append(session_id)
                frames = load_frames(args.frames, args.call_id, args.sender_id, session_id)

                for frame in frames:
                    body = json.dumps(frame, ensure_ascii=False, separators=(",", ":"))
                    await websocket.send(
                        stomp_frame(
                            "SEND",
                            {
                                "destination": "/pub/ai/features",
                                "content-type": "application/json",
                                "content-length": str(len(body.encode("utf-8"))),
                            },
                            body,
                        )
                    )
                    sent_frames += 1
                    await asyncio.sleep(interval_seconds)

                end_message = {
                    "type": "session_end",
                    "sessionId": session_id,
                    "callId": args.call_id,
                    "senderId": args.sender_id,
                    "timestampMs": int(time.time() * 1000),
                }
                body = json.dumps(end_message, ensure_ascii=False, separators=(",", ":"))
                await websocket.send(
                    stomp_frame(
                        "SEND",
                        {
                            "destination": "/pub/ai/features",
                            "content-type": "application/json",
                            "content-length": str(len(body.encode("utf-8"))),
                        },
                        body,
                    )
                )

                if utterance_index < args.repeat - 1:
                    await asyncio.sleep(args.inter_utterance_seconds)
            await asyncio.sleep(args.wait_seconds)
        finally:
            receiver.cancel()
            await asyncio.gather(receiver, return_exceptions=True)

    print(
        json.dumps(
            {
                "sentFrames": sent_frames,
                "utterances": args.repeat,
                "callId": args.call_id,
                "senderId": args.sender_id,
                "sessionIds": session_ids,
            },
            ensure_ascii=False,
        )
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Replay 258-feature fixture frames through Spring STOMP.")
    parser.add_argument("--url", required=True, help="Raw Spring STOMP WebSocket URL, for example ws://host/ws-stomp")
    parser.add_argument("--host", default="localhost", help="STOMP host header")
    parser.add_argument("--frames", required=True, type=Path)
    parser.add_argument("--call-id", required=True)
    parser.add_argument("--sender-id", required=True, type=int)
    parser.add_argument("--session-id", help="Optional UUID. A new UUID is generated by default.")
    parser.add_argument("--fps", type=float, default=15.0)
    parser.add_argument("--repeat", type=int, default=1, help="Number of sequential utterances to replay.")
    parser.add_argument(
        "--inter-utterance-seconds",
        type=float,
        default=1.0,
        help="Idle time between repeated utterances.",
    )
    parser.add_argument("--wait-seconds", type=float, default=3.0)
    args = parser.parse_args()
    if args.fps <= 0:
        parser.error("--fps must be positive")
    if args.repeat <= 0:
        parser.error("--repeat must be positive")
    if args.inter_utterance_seconds < 0:
        parser.error("--inter-utterance-seconds must be zero or greater")
    asyncio.run(replay(args))


if __name__ == "__main__":
    main()
