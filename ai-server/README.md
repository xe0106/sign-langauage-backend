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
