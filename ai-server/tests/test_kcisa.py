import csv
from pathlib import Path

import pytest

from mindvoice_ai.sources.kcisa import (
    CatalogItem,
    TargetClass,
    parse_catalog_xml,
    save_selection,
    select_target_items,
)


def item(title: str, video_url: str = "https://example.test/video.mp4") -> CatalogItem:
    return CatalogItem(
        title=title,
        detail_url="https://example.test/detail",
        video_url=video_url,
        thumbnail_url="https://example.test/image.jpg",
        sign_description="description",
        rights="",
        copyright_others="",
    )


def test_parse_catalog_xml_extracts_video_and_upgrades_https() -> None:
    payload = b"""<response>
      <header><resultCode>0000</resultCode><resultMsg>OK</resultMsg></header>
      <body><items><item>
        <title>HELLO</title>
        <url>http://example.test/detail</url>
        <subDescription>http://example.test/video.mp4</subDescription>
        <referenceIdentifier>http://example.test/image.jpg</referenceIdentifier>
        <signDescription>movement</signDescription>
        <rights>public</rights><copyrightOthers></copyrightOthers>
      </item></items><totalCount>1</totalCount></body>
    </response>"""

    items, total = parse_catalog_xml(payload)

    assert total == 1
    assert items[0].video_url == "https://example.test/video.mp4"
    assert items[0].sign_description == "movement"


def test_select_target_items_matches_exact_titles_and_deduplicates_urls() -> None:
    target = TargetClass(0, "안녕하세요", "HELLO", ("hello title",))
    catalog = [
        item("hello title"),
        item("hello title"),
        item("unrelated", "https://example.test/other.mp4"),
    ]

    selected = select_target_items(catalog, [target])

    assert len(selected) == 1
    assert selected[0][0].stable_key == "HELLO"


def test_select_target_items_rejects_missing_class() -> None:
    target = TargetClass(0, "안녕하세요", "HELLO", ("missing",))

    with pytest.raises(ValueError, match="no KCISA video"):
        select_target_items([], [target])


def test_save_selection_writes_training_manifest_and_provenance(tmp_path: Path) -> None:
    target = TargetClass(0, "안녕하세요", "HELLO", ("hello",))

    summary = save_selection([(target, item("hello"))], tmp_path)

    with (tmp_path / "manifest.csv").open(encoding="utf-8", newline="") as source:
        rows = list(csv.DictReader(source))
    assert rows[0]["input_path"] == "videos/hello-001.mp4"
    assert rows[0]["signer_id"] == "KCISA_UNKNOWN"
    assert (tmp_path / "sources.jsonl").is_file()
    assert summary == {"classes": 1, "videos": 1}
