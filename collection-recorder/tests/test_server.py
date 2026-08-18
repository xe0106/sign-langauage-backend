from __future__ import annotations

import csv
import json
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import server  # noqa: E402


class RecorderServerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        root = Path(self.temporary.name)
        self.recordings = root / "recordings"
        self.video_root = self.recordings / "videos"
        self.manifest = self.recordings / "manifest.csv"
        self.patches = [
            patch.object(server, "RECORDINGS_ROOT", self.recordings),
            patch.object(server, "VIDEO_ROOT", self.video_root),
            patch.object(server, "MANIFEST_PATH", self.manifest),
        ]
        for item in self.patches:
            item.start()

    def tearDown(self) -> None:
        for item in reversed(self.patches):
            item.stop()
        self.temporary.cleanup()

    def test_split_assignment_is_fixed_by_signer(self) -> None:
        self.assertEqual("train", server.split_for_signer("S001"))
        self.assertEqual("train", server.split_for_signer("S005"))
        self.assertEqual("train", server.split_for_signer("S006"))
        self.assertEqual("validation", server.split_for_signer("S007"))
        self.assertEqual("test", server.split_for_signer("S008"))
        with self.assertRaises(ValueError):
            server.split_for_signer("S009")

    def test_manifest_is_rebuilt_from_recording_files(self) -> None:
        path = server.recording_path("S001", "HELLO", 1, "webm")
        path.parent.mkdir(parents=True)
        path.write_bytes(b"video")

        count = server.rebuild_manifest()

        with self.manifest.open(encoding="utf-8", newline="") as source:
            rows = list(csv.DictReader(source))
        self.assertEqual(1, count)
        self.assertEqual("s001-hello-001", rows[0]["sample_id"])
        self.assertEqual("train", rows[0]["split"])
        self.assertEqual("videos/S001/train/HELLO/s001-hello-001.webm", rows[0]["input_path"])

    def test_progress_detects_completed_repetitions(self) -> None:
        for repetition in (1, 2, 11):
            path = server.recording_path("S008", "NO_SIGN", repetition, "mp4")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"video")

        progress = server.progress_for_signer("S008")

        self.assertEqual([1, 2, 11], progress["NO_SIGN"])

    def test_http_upload_conflict_overwrite_and_delete(self) -> None:
        httpd = server.ThreadingHTTPServer(("127.0.0.1", 0), server.RecorderHandler)
        thread = threading.Thread(target=httpd.serve_forever, daemon=True)
        thread.start()
        base_url = f"http://127.0.0.1:{httpd.server_port}"
        try:
            with urllib.request.urlopen(f"{base_url}/api/config") as response:
                config = json.loads(response.read())
            self.assertEqual(11, len(config["classes"]))
            self.assertEqual(
                {
                    "S001": "train",
                    "S002": "train",
                    "S003": "train",
                    "S004": "train",
                    "S005": "train",
                    "S006": "train",
                    "S007": "validation",
                    "S008": "test",
                },
                config["signerSplits"],
            )

            request = urllib.request.Request(
                f"{base_url}/api/recording",
                data=b"x" * 2_000,
                method="POST",
                headers={
                    "Content-Type": "video/webm",
                    "X-Signer-Id": "S001",
                    "X-Stable-Key": "HELLO",
                    "X-Repetition": "1",
                },
            )
            with urllib.request.urlopen(request) as response:
                self.assertEqual(201, response.status)
            with self.assertRaises(urllib.error.HTTPError) as conflict:
                urllib.request.urlopen(request)
            self.assertEqual(409, conflict.exception.code)

            overwrite = urllib.request.Request(
                f"{base_url}/api/recording",
                data=b"y" * 2_100,
                method="POST",
                headers={
                    "Content-Type": "video/webm",
                    "X-Signer-Id": "S001",
                    "X-Stable-Key": "HELLO",
                    "X-Repetition": "1",
                    "X-Overwrite": "true",
                },
            )
            with urllib.request.urlopen(overwrite) as response:
                self.assertEqual(201, response.status)

            delete = urllib.request.Request(
                f"{base_url}/api/recording?signerId=S001&stableKey=HELLO&repetition=1",
                method="DELETE",
            )
            with urllib.request.urlopen(delete) as response:
                self.assertEqual(200, response.status)
            self.assertEqual([], server.progress_for_signer("S001")["HELLO"])
        finally:
            httpd.shutdown()
            httpd.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
