from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class PageChunk:
    page: int
    text: str


@dataclass(frozen=True)
class DocumentContext:
    context_id: str
    document_title: str | None
    chunks: list[PageChunk]

    def retrieve(self, query: str, *, max_chunks: int = 4, max_chars: int = 6000) -> str:
        tokens = _keywords(query)
        if not tokens:
            return self._format_chunks(self.chunks[:max_chunks], max_chars=max_chars)

        scored: list[tuple[int, PageChunk]] = []
        for chunk in self.chunks:
            score = _score(chunk.text, tokens)
            if score > 0:
                scored.append((score, chunk))

        scored.sort(key=lambda x: x[0], reverse=True)
        top = [c for _, c in scored[:max_chunks]] if scored else self.chunks[:max_chunks]
        return self._format_chunks(top, max_chars=max_chars)

    def _format_chunks(self, chunks: list[PageChunk], *, max_chars: int) -> str:
        out: list[str] = []
        remaining = max_chars
        for c in chunks:
            if remaining <= 0:
                break
            header = f"[Page {c.page}]\n"
            body = c.text.strip()
            if len(body) > remaining:
                body = body[: max(0, remaining - 1)].rstrip() + "…"
            out.append(header + body)
            remaining -= len(out[-1])
        return "\n\n".join(out).strip()


def load_context(*, contexts_dir: Path, context_id: str) -> DocumentContext | None:
    path = contexts_dir / f"{context_id}.json"
    if not path.exists():
        return None
    payload = json.loads(path.read_text(encoding="utf-8"))
    return _parse_context_payload(payload)


def parse_context_id_from_room(room_name: str, *, prefix: str) -> str | None:
    if not room_name.startswith(prefix):
        return None
    candidate = room_name[len(prefix) :].strip()
    if not candidate:
        return None
    if not _VALID_CONTEXT_ID_RE.match(candidate):
        return None
    return candidate


def contexts_dir_from_env() -> Path:
    default_data_dir = Path(__file__).resolve().parent / ".data"
    data_dir = Path(os.environ.get("ODP_ASSISTANT_DATA_DIR", str(default_data_dir))).expanduser().resolve()
    return (data_dir / "contexts").resolve()


def _parse_context_payload(payload: dict[str, Any]) -> DocumentContext:
    context_id = str(payload.get("contextId", ""))
    document_title = payload.get("documentTitle")

    chunks: list[PageChunk] = []
    pages = payload.get("pages")
    if isinstance(pages, list):
        for item in pages:
            try:
                page = int(item.get("page"))
                text = str(item.get("text", ""))
            except Exception:
                continue
            if page > 0 and text.strip():
                chunks.append(PageChunk(page=page, text=text))

    # Fallback for payloads that were ingested as a single "text" blob.
    if not chunks:
        blob = str(payload.get("text", "") or "")
        chunks = _split_text_blob(blob)

    return DocumentContext(context_id=context_id, document_title=document_title, chunks=chunks)


_PAGE_MARKER_RE = re.compile(r"^\\s*-{2,}\\s*page\\s+(\\d+)\\s*-{2,}\\s*$", re.IGNORECASE | re.MULTILINE)
_VALID_CONTEXT_ID_RE = re.compile(r"^[a-zA-Z0-9_-]{1,64}$")


def _split_text_blob(blob: str) -> list[PageChunk]:
    blob = blob.strip()
    if not blob:
        return []

    markers = list(_PAGE_MARKER_RE.finditer(blob))
    if not markers:
        return [PageChunk(page=1, text=blob)]

    chunks: list[PageChunk] = []
    for idx, m in enumerate(markers):
        page = int(m.group(1))
        start = m.end()
        end = markers[idx + 1].start() if idx + 1 < len(markers) else len(blob)
        text = blob[start:end].strip()
        if text:
            chunks.append(PageChunk(page=page, text=text))
    return chunks


_WORD_RE = re.compile(r"[a-zA-Z0-9]{3,}")


def _keywords(query: str) -> list[str]:
    words = [w.lower() for w in _WORD_RE.findall(query)]
    # de-dupe while preserving order
    seen: set[str] = set()
    out: list[str] = []
    for w in words:
        if w in seen:
            continue
        seen.add(w)
        out.append(w)
    return out[:12]


def _score(text: str, tokens: list[str]) -> int:
    if not text:
        return 0
    haystack = text.lower()
    return sum(haystack.count(t) for t in tokens)
