from __future__ import annotations

import argparse
import html
import json
from pathlib import Path
from urllib.parse import quote

import numpy as np

from mindvoice_ai.training.data import load_training_dataset


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True, type=Path)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--report-name", default="test-report")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    import tensorflow as tf

    dataset = load_training_dataset(args.dataset)
    mask = dataset.splits == "test"
    probabilities = tf.keras.models.load_model(args.model).predict(dataset.features[mask], verbose=0)
    source_by_id = {path.stem: path for path in args.source_root.rglob("*.webm")}
    rows = []
    for sample_id, class_id, probability in zip(dataset.sample_ids[mask], dataset.labels[mask], probabilities, strict=True):
        predicted_id = int(np.argmax(probability))
        source = source_by_id[str(sample_id)]
        rows.append(
            {
                "sampleId": str(sample_id),
                "video": str(source),
                "expected": dataset.label_keys[int(class_id)],
                "predicted": dataset.label_keys[predicted_id],
                "confidence": round(float(probability[predicted_id]), 4),
                "correct": predicted_id == int(class_id),
            }
        )
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / f"{args.report_name}-predictions.json").write_text(
        json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    correct = sum(row["correct"] for row in rows)
    cards = []
    for row in rows:
        relative_video = Path("../..") / Path(row["video"])
        video_url = quote(relative_video.as_posix(), safe="/._-")
        state = "correct" if row["correct"] else "wrong"
        cards.append(
            f'''<article class="card {state}"><video controls preload="metadata" src="{video_url}"></video>
<div class="meta"><b>{html.escape(row["sampleId"])}</b><span>정답: {html.escape(row["expected"])}</span>
<span>예측: {html.escape(row["predicted"])}</span><span>신뢰도: {row["confidence"]:.1%}</span></div></article>'''
        )
    page = f'''<!doctype html><html lang="ko"><meta charset="utf-8"><title>수화 인식 테스트 리포트</title>
<style>body{{font-family:Arial,sans-serif;margin:28px;background:#f4f7f9;color:#15202b}}h1{{margin:0}}p{{color:#455a64}}.grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:16px}}.card{{background:white;border:2px solid #cfd8dc;border-radius:6px;overflow:hidden}}.card.correct{{border-color:#2e7d32}}.card.wrong{{border-color:#c62828}}video{{width:100%;display:block;background:#111}}.meta{{display:grid;gap:5px;padding:12px;font-size:14px}}.meta b{{font-size:15px}}</style>
<body><h1>수화 인식 테스트</h1><p>보지 않은 테스트 영상 {len(rows)}개 중 {correct}개 정답 ({correct / len(rows):.1%}). 녹색은 정답, 빨간색은 오답입니다.</p><div class="grid">{''.join(cards)}</div></body></html>'''
    (args.output_dir / f"{args.report_name}-report.html").write_text(page, encoding="utf-8")
    print(f"{correct}/{len(rows)} correct")


if __name__ == "__main__":
    main()
