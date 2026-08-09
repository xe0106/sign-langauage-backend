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
