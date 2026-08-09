from __future__ import annotations

import argparse
import csv
import json
import mimetypes
import os
import re
import threading
import webbrowser
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parent
STATIC_ROOT = ROOT / "web"
RECORDINGS_ROOT = ROOT / "recordings"
VIDEO_ROOT = RECORDINGS_ROOT / "videos"
MANIFEST_PATH = RECORDINGS_ROOT / "manifest.csv"
MAX_VIDEO_BYTES = 50 * 1024 * 1024
SAFE_SIGNER = re.compile(r"^S00[1-6]$")
SAFE_KEY = re.compile(r"^[A-Z][A-Z0-9_]*$")
SAMPLE_NAME = re.compile(
    r"^(?P<signer>s00[1-6])-(?P<key>[a-z0-9_]+)-(?P<repetition>\d{3})\.(?P<extension>webm|mp4)$"
)
MANIFEST_FIELDS = [
    "sample_id",
    "input_path",
    "class_id",
    "label",
    "stable_key",
    "signer_id",
    "split",
]
CLASSES = [
    {"classId": 0, "label": "안녕하세요", "stableKey": "HELLO"},
    {"classId": 1, "label": "감사합니다", "stableKey": "THANK_YOU"},
    {"classId": 2, "label": "미안합니다", "stableKey": "SORRY"},
    {"classId": 3, "label": "괜찮습니다", "stableKey": "OKAY"},
    {"classId": 4, "label": "좋습니다", "stableKey": "GOOD"},
    {"classId": 5, "label": "싫습니다", "stableKey": "DISLIKE"},
    {"classId": 6, "label": "나", "stableKey": "ME"},
    {"classId": 7, "label": "너", "stableKey": "YOU"},
    {"classId": 8, "label": "만나다", "stableKey": "MEET"},
    {"classId": 9, "label": "가다", "stableKey": "GO"},
    {"classId": 10, "label": "비수어 동작", "stableKey": "NO_SIGN"},
]
CLASS_BY_KEY = {item["stableKey"]: item for item in CLASSES}
manifest_lock = threading.Lock()


def split_for_signer(signer_id: str) -> str:
    if signer_id in {"S001", "S002", "S003", "S004"}:
        return "train"
    if signer_id == "S005":
        return "validation"
    if signer_id == "S006":
        return "test"
    raise ValueError("signerId must be one of S001-S006")


def validate_recording_fields(
    signer_id: str, stable_key: str, repetition_text: str
) -> tuple[int, dict]:
    if not SAFE_SIGNER.fullmatch(signer_id):
        raise ValueError("invalid signerId")
    if not SAFE_KEY.fullmatch(stable_key) or stable_key not in CLASS_BY_KEY:
        raise ValueError("invalid stableKey")
    try:
        repetition = int(repetition_text)
    except ValueError as error:
        raise ValueError("repetition must be an integer") from error
    if not 1 <= repetition <= 20:
        raise ValueError("repetition must be between 1 and 20")
    return repetition, CLASS_BY_KEY[stable_key]


def extension_for_content_type(content_type: str) -> str:
    media_type = content_type.split(";", 1)[0].strip().lower()
    if media_type == "video/webm":
        return "webm"
    if media_type == "video/mp4":
        return "mp4"
    raise ValueError("only video/webm and video/mp4 recordings are accepted")


def recording_path(
    signer_id: str, stable_key: str, repetition: int, extension: str
) -> Path:
    split = split_for_signer(signer_id)
    filename = f"{signer_id.lower()}-{stable_key.lower()}-{repetition:03d}.{extension}"
    return VIDEO_ROOT / signer_id / split / stable_key / filename


def rebuild_manifest() -> int:
    rows = []
    if VIDEO_ROOT.exists():
        for path in VIDEO_ROOT.rglob("*"):
            if not path.is_file():
                continue
            match = SAMPLE_NAME.fullmatch(path.name)
            if match is None:
                continue
            signer_id = match.group("signer").upper()
            stable_key = match.group("key").upper()
            class_info = CLASS_BY_KEY.get(stable_key)
            if class_info is None:
                continue
            repetition = int(match.group("repetition"))
            split = split_for_signer(signer_id)
            sample_id = f"{signer_id.lower()}-{stable_key.lower()}-{repetition:03d}"
            rows.append(
                {
                    "sample_id": sample_id,
                    "input_path": path.relative_to(RECORDINGS_ROOT).as_posix(),
                    "class_id": class_info["classId"],
                    "label": class_info["label"],
                    "stable_key": stable_key,
                    "signer_id": signer_id,
                    "split": split,
                }
            )
    rows.sort(key=lambda row: (row["signer_id"], row["class_id"], row["sample_id"]))
    RECORDINGS_ROOT.mkdir(parents=True, exist_ok=True)
    temporary = MANIFEST_PATH.with_suffix(".csv.tmp")
    with temporary.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(destination, fieldnames=MANIFEST_FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    os.replace(temporary, MANIFEST_PATH)
    return len(rows)


def progress_for_signer(signer_id: str) -> dict[str, list[int]]:
    split_for_signer(signer_id)
    progress = {item["stableKey"]: [] for item in CLASSES}
    signer_root = VIDEO_ROOT / signer_id
    if not signer_root.exists():
        return progress
    for path in signer_root.rglob("*"):
        if not path.is_file():
            continue
        match = SAMPLE_NAME.fullmatch(path.name)
        if match is None:
            continue
        key = match.group("key").upper()
        if key in progress:
            progress[key].append(int(match.group("repetition")))
    return {key: sorted(set(values)) for key, values in progress.items()}


class RecorderHandler(SimpleHTTPRequestHandler):
    server_version = "MindVoiceRecorder/1.0"

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(STATIC_ROOT), **kwargs)

    def _send_json(self, status: HTTPStatus, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _query(self) -> dict[str, str]:
        parsed = urlparse(self.path)
        return {
            key: values[0]
            for key, values in parse_qs(parsed.query, keep_blank_values=True).items()
        }

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/api/config":
            self._send_json(
                HTTPStatus.OK,
                {
                    "classes": CLASSES,
                    "signerSplits": {
                        signer: split_for_signer(signer)
                        for signer in (f"S00{index}" for index in range(1, 7))
                    },
                    "repetitions": 20,
                },
            )
            return
        if parsed.path == "/api/progress":
            try:
                signer_id = self._query().get("signerId", "")
                progress = progress_for_signer(signer_id)
            except ValueError as error:
                self._send_json(HTTPStatus.BAD_REQUEST, {"error": str(error)})
                return
            self._send_json(HTTPStatus.OK, {"signerId": signer_id, "progress": progress})
            return
        if parsed.path == "/api/health":
            self._send_json(HTTPStatus.OK, {"status": "ok"})
            return
        super().do_GET()

    def do_POST(self) -> None:
        if urlparse(self.path).path != "/api/recording":
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "not found"})
            return
        try:
            signer_id = self.headers.get("X-Signer-Id", "")
            stable_key = self.headers.get("X-Stable-Key", "")
            repetition, _ = validate_recording_fields(
                signer_id, stable_key, self.headers.get("X-Repetition", "")
            )
            extension = extension_for_content_type(
                self.headers.get("Content-Type", "")
            )
            content_length = int(self.headers.get("Content-Length", "0"))
            if not 1_000 <= content_length <= MAX_VIDEO_BYTES:
                raise ValueError("recording size must be between 1KB and 50MB")
            overwrite = self.headers.get("X-Overwrite", "false").lower() == "true"
            destination = recording_path(
                signer_id, stable_key, repetition, extension
            )
            alternative = destination.with_suffix(
                ".mp4" if destination.suffix == ".webm" else ".webm"
            )
            if (destination.exists() or alternative.exists()) and not overwrite:
                self._send_json(
                    HTTPStatus.CONFLICT,
                    {"error": "recording already exists; confirm re-recording"},
                )
                return
            body = self.rfile.read(content_length)
            if len(body) != content_length:
                raise ValueError("incomplete recording upload")
            destination.parent.mkdir(parents=True, exist_ok=True)
            temporary = destination.with_suffix(destination.suffix + ".tmp")
            temporary.write_bytes(body)
            if alternative.exists():
                alternative.unlink()
            os.replace(temporary, destination)
            with manifest_lock:
                sample_count = rebuild_manifest()
        except (ValueError, OSError) as error:
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": str(error)})
            return
        self._send_json(
            HTTPStatus.CREATED,
            {
                "saved": destination.relative_to(RECORDINGS_ROOT).as_posix(),
                "sampleCount": sample_count,
            },
        )

    def do_DELETE(self) -> None:
        if urlparse(self.path).path != "/api/recording":
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "not found"})
            return
        try:
            query = self._query()
            signer_id = query.get("signerId", "")
            stable_key = query.get("stableKey", "")
            repetition, _ = validate_recording_fields(
                signer_id, stable_key, query.get("repetition", "")
            )
            removed = False
            for extension in ("webm", "mp4"):
                path = recording_path(signer_id, stable_key, repetition, extension)
                if path.exists():
                    path.unlink()
                    removed = True
            with manifest_lock:
                rebuild_manifest()
        except (ValueError, OSError) as error:
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": str(error)})
            return
        if not removed:
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "recording not found"})
            return
        self._send_json(HTTPStatus.OK, {"deleted": True})

    def log_message(self, format: str, *args) -> None:
        print(f"[{self.log_date_time_string()}] {format % args}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the local MindVoice recorder.")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--no-browser", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not 1 <= args.port <= 65535:
        raise SystemExit("port must be between 1 and 65535")
    RECORDINGS_ROOT.mkdir(parents=True, exist_ok=True)
    with manifest_lock:
        rebuild_manifest()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), RecorderHandler)
    url = f"http://127.0.0.1:{args.port}"
    print(f"MindVoice recorder: {url}")
    print(f"Recordings directory: {RECORDINGS_ROOT}")
    print("Press Ctrl+C to stop.")
    if not args.no_browser:
        threading.Timer(0.5, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nRecorder stopped.")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
