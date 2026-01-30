from __future__ import annotations

import json
import os
import time
import uuid
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from livekit import api as livekit_api


def _data_dir() -> Path:
    default_dir = Path(__file__).resolve().parent / ".data"
    return Path(os.environ.get("ODP_ASSISTANT_DATA_DIR", str(default_dir))).expanduser().resolve()


DATA_DIR = _data_dir()
CONTEXTS_DIR = DATA_DIR / "contexts"


def _ensure_dirs() -> None:
    CONTEXTS_DIR.mkdir(parents=True, exist_ok=True)


def _room_prefix() -> str:
    return os.environ.get("ODP_ASSISTANT_ROOM_PREFIX", "odpp-assistant-")


def _context_id() -> str:
    return f"ctx_{uuid.uuid4().hex[:12]}"


class PageText(BaseModel):
    page: int = Field(..., ge=1)
    text: str


class SessionCreateRequest(BaseModel):
    documentTitle: str | None = None
    pages: list[PageText] | None = None
    text: str | None = None


class SessionCreateResponse(BaseModel):
    contextId: str
    room: str


class LiveKitTokenRequest(BaseModel):
    room: str
    identity: str | None = None
    name: str | None = None


class LiveKitTokenResponse(BaseModel):
    url: str
    room: str
    identity: str
    token: str


app = FastAPI(title="OpenDroidPDF Assistant Backend (prototype)")


@app.on_event("startup")
def _startup() -> None:
    _ensure_dirs()


@app.post("/v1/assistant/sessions", response_model=SessionCreateResponse)
def create_session(req: SessionCreateRequest) -> SessionCreateResponse:
    if (req.pages is None or len(req.pages) == 0) and (req.text is None or not req.text.strip()):
        raise HTTPException(status_code=400, detail="Provide either non-empty pages[] or text")

    context_id = _context_id()
    room = f"{_room_prefix()}{context_id}"

    payload: dict[str, Any] = {
        "contextId": context_id,
        "room": room,
        "createdAtUnix": int(time.time()),
        "documentTitle": req.documentTitle,
    }
    if req.pages is not None:
        payload["pages"] = [{"page": p.page, "text": p.text} for p in req.pages]
    if req.text is not None:
        payload["text"] = req.text

    path = CONTEXTS_DIR / f"{context_id}.json"
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

    return SessionCreateResponse(contextId=context_id, room=room)


@app.post("/v1/livekit/token", response_model=LiveKitTokenResponse)
def mint_livekit_token(req: LiveKitTokenRequest) -> LiveKitTokenResponse:
    url = os.environ.get("LIVEKIT_URL", "").strip()
    if not url:
        raise HTTPException(status_code=500, detail="LIVEKIT_URL must be set")

    if not req.room or not req.room.strip():
        raise HTTPException(status_code=400, detail="room must be non-empty")

    identity = (req.identity or f"user_{uuid.uuid4().hex[:10]}").strip()
    if not identity:
        raise HTTPException(status_code=400, detail="identity must be non-empty")

    try:
        token = (
            livekit_api.AccessToken()
            .with_identity(identity)
            .with_name(req.name or identity)
            .with_grants(
                livekit_api.VideoGrants(
                    room_join=True,
                    room=req.room,
                    room_create=True,
                    can_publish=True,
                    can_subscribe=True,
                    can_publish_data=True,
                )
            )
            .to_jwt()
        )
    except ValueError as e:
        raise HTTPException(status_code=500, detail=str(e)) from e

    return LiveKitTokenResponse(url=url, room=req.room, identity=identity, token=token)


@app.get("/v1/assistant/contexts/{context_id}")
def debug_get_context(context_id: str) -> dict[str, Any]:
    # Debug-only endpoint (handy for integration testing).
    # Do not expose publicly without auth.
    path = CONTEXTS_DIR / f"{context_id}.json"
    if not path.exists():
        raise HTTPException(status_code=404, detail="context not found")
    return json.loads(path.read_text(encoding="utf-8"))
