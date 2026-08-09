# Mind Voice AI Service

This directory contains the Python service for MediaPipe landmark validation, session buffering, training, evaluation, and real-time inference.

The current scaffold deliberately does not return fabricated sign predictions. Until a versioned model artifact is installed, a full 30-frame window returns a `model_unavailable` status.

## Local setup

```bash
python -m venv .venv
python -m pip install -e ".[dev]"
uvicorn mindvoice_ai.app:app --app-dir src --reload
```

Health endpoint:

```text
GET http://localhost:8000/health
```

Inference socket:

```text
ws://localhost:8000/ws/inference
```

See `docs/AI_MVP_SPEC.md` for the feature order and message contract.

## Video preprocessing

Place the official MediaPipe `holistic_landmarker.task` file under `models/`, then run:

```bash
python -m mindvoice_ai.preprocessing.video \
  --input sample.mp4 \
  --output data/HELLO/S001/sample-001.npz \
  --model models/holistic_landmarker.task \
  --label "안녕하세요" \
  --stable-key HELLO \
  --signer-id S001
```

Each NPZ contains normalized `features` with shape `(frames, 258)`, landmark presence flags, timestamps, and JSON metadata. Raw videos and generated datasets are ignored by Git.

## Dataset pipeline

Copy `manifests/example.csv` and register every recording with an explicit
`class_id`, stable key, anonymous signer ID, and split. A signer must belong to
only one of `train`, `validation`, or `test`; the loader rejects leakage.

Preprocess every registered video and create an index:

```bash
python -m mindvoice_ai.dataset.batch \
  --manifest manifests/dataset.csv \
  --output-root data/processed \
  --model models/holistic_landmarker.task
```

Convert variable-length recordings into fixed 30-frame model input arrays:

```bash
python -m mindvoice_ai.dataset.sequences \
  --index data/processed/dataset-index.jsonl \
  --output data/training/dataset.npz
```

The final NPZ stores `features`, integer `labels`, split and signer metadata,
plus the class ID to stable-key and Korean-label mappings. Class IDs must begin
at 0 and be contiguous so training and serving cannot silently disagree.

## Baseline training

Train the 1D-CNN baseline after the dataset contains signer-disjoint train,
validation, and test samples for every class:

```bash
python -m mindvoice_ai.training.train \
  --dataset data/training/dataset.npz \
  --output-dir artifacts/ksl-word-v0.1.0 \
  --model-version ksl-word-v0.1.0
```

The version directory contains `model.keras`, `metadata.json`, and
`evaluation.json`. Metadata fixes the input contract and class mapping and
includes the model SHA-256 digest. Evaluation reports macro F1, per-class
metrics, the confusion matrix, and mean CPU inference time per window. These
generated artifacts are intentionally ignored by Git. Existing version files
are not overwritten unless `--overwrite` is supplied explicitly.

## Serving a trained model

Set the model package directory before starting the API service:

```bash
export MINDVOICE_MODEL_DIR=artifacts/ksl-word-v0.1.0
uvicorn mindvoice_ai.app:app --app-dir src
```

On Windows PowerShell, use `$env:MINDVOICE_MODEL_DIR` instead of `export`.
`GET /health` reports the loaded model version or the package validation error.
After the 30-frame warm-up, the WebSocket reports `analyzing` until the same
confident public class is observed for the configured number of windows. A
stable prediction is emitted once, and remains suppressed until `NO_SIGN` or a
different stable class releases it.

## KCISA daily sign-language catalog

The KCISA API key must be supplied only through the environment. Never place it
in a manifest, command argument, source file, or commit:

```powershell
$env:KCISA_API_KEY = "your-issued-key"
python -m mindvoice_ai.sources.kcisa `
  --targets configs/kcisa-targets.json `
  --output-dir data/kcisa-reference
```

This creates a curated manifest and `sources.jsonl` provenance record. Add
`--download` only after confirming that the approved use and each item's rights
permit downloading and model training. The catalog does not expose signer IDs,
so generated rows use `KCISA_UNKNOWN` and train-only assignment. These dictionary
clips are useful references and seed samples, but cannot provide a valid
signer-independent validation or test set by themselves.

## Dataset quality audit

Audit collected videos before and after MediaPipe preprocessing:

```bash
python -m mindvoice_ai.dataset.audit \
  --manifest data/collection/manifest.csv \
  --processed-index data/processed/dataset-index.jsonl \
  --expected-repetitions 20 \
  --output data/reports/dataset-audit.json
```

The report checks per-signer class counts, duplicate content, duration,
resolution, FPS, split distribution, missing processed samples, and pose/hand
detection rates. Errors block dataset acceptance; warnings identify clips for
manual review or re-recording.

## Signer cross-validation

With six complete signers, evaluate every person as an unseen signer while a
second person rotates through validation:

```bash
python -m mindvoice_ai.training.cross_validate \
  --dataset data/training/dataset.npz \
  --output data/reports/signer-cross-validation.json
```

The six folds each use four train signers, one validation signer, and one test
signer. The report includes per-signer macro F1, accuracy, confusion matrices,
inference latency, and aggregate mean, standard deviation, minimum, and maximum
macro F1. This supplements the fixed 4/1/1 split; it does not replace a larger
independent test population.
