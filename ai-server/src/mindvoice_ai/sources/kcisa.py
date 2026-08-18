from __future__ import annotations

import argparse
import csv
import json
import os
import shutil
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import BinaryIO, Callable

API_KEY_ENV = "KCISA_API_KEY"
DEFAULT_ENDPOINT = (
    "https://api.kcisa.kr/openapi/service/rest/meta13/getCTE01701"
)


@dataclass(frozen=True)
class CatalogItem:
    title: str
    detail_url: str
    video_url: str
    thumbnail_url: str
    sign_description: str
    rights: str
    copyright_others: str


@dataclass(frozen=True)
class TargetClass:
    class_id: int
    label: str
    stable_key: str
    catalog_titles: tuple[str, ...]


def _text(element: ET.Element, name: str) -> str:
    child = element.find(name)
    return "" if child is None or child.text is None else child.text.strip()


def parse_catalog_xml(payload: bytes) -> tuple[list[CatalogItem], int]:
    try:
        root = ET.fromstring(payload)
    except ET.ParseError as error:
        raise ValueError("KCISA response is not valid XML") from error
    code = root.findtext("./header/resultCode", default="")
    message = root.findtext("./header/resultMsg", default="")
    if code != "0000":
        raise ValueError(f"KCISA API error {code}: {message}")

    items = [
        CatalogItem(
            title=_text(item, "title"),
            detail_url=_text(item, "url").replace("http://", "https://", 1),
            video_url=_text(item, "subDescription").replace(
                "http://", "https://", 1
            ),
            thumbnail_url=_text(item, "referenceIdentifier").replace(
                "http://", "https://", 1
            ),
            sign_description=_text(item, "signDescription"),
            rights=_text(item, "rights"),
            copyright_others=_text(item, "copyrightOthers"),
        )
        for item in root.findall("./body/items/item")
    ]
    total_count = int(root.findtext("./body/totalCount", default=str(len(items))))
    return items, total_count


def fetch_catalog(
    api_key: str,
    *,
    endpoint: str = DEFAULT_ENDPOINT,
    rows: int = 4000,
    opener: Callable[..., BinaryIO] = urllib.request.urlopen,
) -> list[CatalogItem]:
    if not api_key.strip():
        raise ValueError(f"API key is empty; set {API_KEY_ENV}")
    query = urllib.parse.urlencode(
        {"serviceKey": api_key, "numOfRows": rows, "pageNo": 1}
    )
    request = urllib.request.Request(f"{endpoint}?{query}")
    with opener(request, timeout=60) as response:
        items, total_count = parse_catalog_xml(response.read())
    if len(items) != total_count:
        raise ValueError(
            f"incomplete KCISA catalog: received {len(items)} of {total_count} items"
        )
    return items


def load_targets(path: Path) -> list[TargetClass]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    targets = [
        TargetClass(
            class_id=item["classId"],
            label=item["label"],
            stable_key=item["stableKey"],
            catalog_titles=tuple(item["catalogTitles"]),
        )
        for item in raw
    ]
    if [target.class_id for target in targets] != list(range(len(targets))):
        raise ValueError("target class IDs must be ordered and contiguous from 0")
    return targets


def select_target_items(
    catalog: list[CatalogItem], targets: list[TargetClass]
) -> list[tuple[TargetClass, CatalogItem]]:
    by_title: dict[str, list[CatalogItem]] = {}
    for item in catalog:
        by_title.setdefault(item.title, []).append(item)

    selected: list[tuple[TargetClass, CatalogItem]] = []
    for target in targets:
        seen_urls: set[str] = set()
        for title in target.catalog_titles:
            for item in by_title.get(title, []):
                if item.video_url and item.video_url not in seen_urls:
                    selected.append((target, item))
                    seen_urls.add(item.video_url)
        if not seen_urls:
            raise ValueError(
                f"no KCISA video found for {target.stable_key}: {target.catalog_titles}"
            )
    return selected


def save_selection(
    selected: list[tuple[TargetClass, CatalogItem]], output_dir: Path
) -> dict[str, int]:
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = output_dir / "manifest.csv"
    sources_path = output_dir / "sources.jsonl"
    retrieved_at = datetime.now(timezone.utc).isoformat()

    with manifest_path.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(
            destination,
            fieldnames=[
                "sample_id",
                "input_path",
                "class_id",
                "label",
                "stable_key",
                "signer_id",
                "split",
            ],
        )
        writer.writeheader()
        per_class_index: dict[str, int] = {}
        for target, item in selected:
            index = per_class_index.get(target.stable_key, 0) + 1
            per_class_index[target.stable_key] = index
            filename = f"{target.stable_key.lower()}-{index:03d}.mp4"
            writer.writerow(
                {
                    "sample_id": f"kcisa-{target.stable_key.lower()}-{index:03d}",
                    "input_path": f"videos/{filename}",
                    "class_id": target.class_id,
                    "label": target.label,
                    "stable_key": target.stable_key,
                    "signer_id": "KCISA_UNKNOWN",
                    "split": "train",
                }
            )

    with sources_path.open("w", encoding="utf-8", newline="\n") as destination:
        for target, item in selected:
            destination.write(
                json.dumps(
                    {
                        "classId": target.class_id,
                        "stableKey": target.stable_key,
                        "retrievedAt": retrieved_at,
                        "collection": "KCISA 일상생활수어",
                        **asdict(item),
                    },
                    ensure_ascii=False,
                )
                + "\n"
            )
    return {"classes": len({target.class_id for target, _ in selected}), "videos": len(selected)}


def download_selection(
    selected: list[tuple[TargetClass, CatalogItem]], output_dir: Path
) -> None:
    video_dir = output_dir / "videos"
    video_dir.mkdir(parents=True, exist_ok=True)
    per_class_index: dict[str, int] = {}
    for target, item in selected:
        index = per_class_index.get(target.stable_key, 0) + 1
        per_class_index[target.stable_key] = index
        destination = video_dir / f"{target.stable_key.lower()}-{index:03d}.mp4"
        request = urllib.request.Request(item.video_url)
        with urllib.request.urlopen(request, timeout=60) as response:
            content_type = response.headers.get_content_type()
            if content_type != "video/mp4":
                raise ValueError(
                    f"expected video/mp4 for {item.video_url}, got {content_type}"
                )
            with destination.open("wb") as output:
                shutil.copyfileobj(response, output)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Select curated Korean Sign Language videos from the KCISA catalog."
    )
    parser.add_argument("--targets", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--download", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    api_key = os.getenv(API_KEY_ENV, "")
    catalog = fetch_catalog(api_key)
    targets = load_targets(args.targets)
    selected = select_target_items(catalog, targets)
    summary = save_selection(selected, args.output_dir)
    if args.download:
        download_selection(selected, args.output_dir)
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
