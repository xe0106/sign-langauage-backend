FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    MINDVOICE_MODEL_DIR=/models/current \
    MINDVOICE_INFERENCE_TIMEOUT_SECONDS=1.0 \
    MINDVOICE_MAX_CONCURRENT_INFERENCES=2 \
    MINDVOICE_SESSION_TTL_SECONDS=300 \
    MINDVOICE_MIRROR_INPUT_X=false

WORKDIR /app

RUN apt-get update \
    && apt-get install --yes --no-install-recommends libglib2.0-0 libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml README.md ./
COPY src ./src

RUN python -m pip install --no-cache-dir ".[serving]" \
    && useradd --create-home --uid 10001 mindvoice

USER mindvoice
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/ready', timeout=3)"]

CMD ["uvicorn", "mindvoice_ai.app:app", "--app-dir", "src", "--host", "0.0.0.0", "--port", "8000"]
