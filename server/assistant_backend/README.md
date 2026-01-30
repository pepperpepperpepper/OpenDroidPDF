# OpenDroidPDF Assistant backend (prototype)

This is a small backend to support **1:1 LiveKit voice chat** (user + agent) with **document text loaded as context**.

It provides:
- `POST /v1/assistant/sessions` to ingest document text into a temporary context store and mint a unique LiveKit room name.
- `POST /v1/livekit/token` to mint a **user** access token for that room.
- `voice_agent_worker.py` which runs a LiveKit Agents worker using **Cartesia STT/TTS** and an **OpenAI-compatible LLM**.

No secrets are stored in this repo. Use environment variables (you can source your `~/.api-keys` locally).

## Requirements

- Python 3.11+ recommended
- A LiveKit deployment (Cloud or self-hosted)
- Cartesia API key (STT + TTS)
- An OpenAI-compatible LLM API (OpenAI, OpenRouter, Ollama, etc.)

## Environment variables

Required:
- `LIVEKIT_URL` (e.g. `wss://<your-livekit-host>` or `https://<your-livekit-host>`)
- `LIVEKIT_API_KEY`
- `LIVEKIT_API_SECRET`
- `CARTESIA_API_KEY`

LLM (OpenAI-compatible):
- `ODP_LLM_API_KEY` (or `OPENAI_API_KEY`)
- `ODP_LLM_BASE_URL` (optional; defaults to OpenAI)
- `ODP_LLM_MODEL` (optional; defaults chosen by plugin)

Optional:
- `ODP_ASSISTANT_DATA_DIR` (defaults to `server/assistant_backend/.data`)
- `ODP_ASSISTANT_ROOM_PREFIX` (defaults to `odpp-assistant-`)
- `ODP_AGENT_NAME` (defaults to `opendroidpdf-assistant`)
- Cartesia overrides (optional):
  - `ODP_CARTESIA_STT_MODEL`, `ODP_CARTESIA_STT_LANGUAGE`
  - `ODP_CARTESIA_TTS_MODEL`, `ODP_CARTESIA_TTS_LANGUAGE`, `ODP_CARTESIA_TTS_VOICE`, `ODP_CARTESIA_TTS_SPEED`

## Setup

```bash
cd server/assistant_backend
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
```

## Run (dev)

Terminal 1 (HTTP API):
```bash
cd server/assistant_backend
source .venv/bin/activate
uvicorn app:app --host 0.0.0.0 --port 8787
```

Terminal 2 (voice agent worker):
```bash
cd server/assistant_backend
source .venv/bin/activate
python voice_agent_worker.py
```

## API usage

1) Ingest document text (from the app) to create a session:
```bash
curl -fsSL -X POST http://127.0.0.1:8787/v1/assistant/sessions \
  -H 'content-type: application/json' \
  -d '{
    "documentTitle": "Example",
    "pages": [
      {"page": 1, "text": "Hello page one"},
      {"page": 2, "text": "Hello page two"}
    ]
  }'
```

Response:
```json
{ "contextId": "ctx_....", "room": "odpp-assistant-ctx_...." }
```

2) Mint a LiveKit token for the **user** to join that room:
```bash
curl -fsSL -X POST http://127.0.0.1:8787/v1/livekit/token \
  -H 'content-type: application/json' \
  -d '{ "room": "odpp-assistant-ctx_...." }'
```

Response:
```json
{ "url": "wss://...", "room": "...", "identity": "user_....", "token": "..." }
```

The Android app should:
- join `url` with `token`
- publish microphone audio
- play agent audio from the room

The agent uses the room name prefix to locate the stored `contextId` and loads the ingested text as retrievable context.
