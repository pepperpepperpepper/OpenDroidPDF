from __future__ import annotations

import asyncio
import os
from pathlib import Path

from livekit.agents import llm
from livekit.agents import cli, worker
from livekit.agents.voice import Agent, AgentSession
from livekit.agents.voice.room_io import RoomOptions
from livekit.plugins import cartesia, openai, silero

from doc_context import contexts_dir_from_env, load_context, parse_context_id_from_room


ROOM_PREFIX = os.environ.get("ODP_ASSISTANT_ROOM_PREFIX", "odpp-assistant-")


def _llm_model() -> str:
    # Keep the default plugin model if unset.
    return os.environ.get("ODP_LLM_MODEL", "").strip()


def _llm_base_url() -> str:
    return os.environ.get("ODP_LLM_BASE_URL", "").strip()


def _llm_api_key() -> str:
    # Prefer our app-specific variable, fall back to OpenAI's.
    return (os.environ.get("ODP_LLM_API_KEY") or os.environ.get("OPENAI_API_KEY") or "").strip()


def _cartesia_stt() -> cartesia.STT:
    model = os.environ.get("ODP_CARTESIA_STT_MODEL", "").strip() or "ink-whisper"
    language = os.environ.get("ODP_CARTESIA_STT_LANGUAGE", "").strip() or "en"
    return cartesia.STT(model=model, language=language)


def _cartesia_tts() -> cartesia.TTS:
    kwargs: dict[str, object] = {}

    model = os.environ.get("ODP_CARTESIA_TTS_MODEL", "").strip()
    if model:
        kwargs["model"] = model

    language = os.environ.get("ODP_CARTESIA_TTS_LANGUAGE", "").strip()
    if language:
        kwargs["language"] = language

    voice = os.environ.get("ODP_CARTESIA_TTS_VOICE", "").strip()
    if voice:
        kwargs["voice"] = voice

    speed = os.environ.get("ODP_CARTESIA_TTS_SPEED", "").strip()
    if speed:
        try:
            kwargs["speed"] = float(speed)
        except ValueError:
            pass

    return cartesia.TTS(**kwargs)


class DocumentAgent(Agent):
    def __init__(self, *, doc_context_id: str | None, contexts_dir: Path) -> None:
        self._doc_context_id = doc_context_id
        self._contexts_dir = contexts_dir
        super().__init__(
            instructions=(
                "You are OpenDroidPDF Assistant.\n"
                "You are in a 1:1 voice call with a user.\n"
                "Before answering, call the tool `retrieve_context` with the user's question to fetch relevant excerpts.\n"
                "When referencing content, mention page numbers.\n"
                "If the excerpts do not contain the answer, say what is missing.\n"
            )
        )

    @llm.function_tool(
        name="retrieve_context",
        description=(
            "Retrieve relevant excerpts from the current document, including page numbers. "
            "Use this before answering questions about the document."
        ),
    )
    def retrieve_context(self, query: str) -> str:
        if not self._doc_context_id:
            return "No document context is attached to this session."

        ctx = load_context(contexts_dir=self._contexts_dir, context_id=self._doc_context_id)
        if ctx is None:
            return f"Document context not found: {self._doc_context_id}"
        return ctx.retrieve(query)


def prewarm(proc: worker.JobProcess) -> None:
    # Load Silero VAD once per worker process.
    proc.userdata["vad"] = silero.VAD.load()


async def entrypoint(ctx: worker.JobContext) -> None:
    await ctx.connect()

    participant = await ctx.wait_for_participant()

    room_name = getattr(ctx.job.room, "name", "") or ""
    doc_context_id = parse_context_id_from_room(room_name, prefix=ROOM_PREFIX)
    contexts_dir = contexts_dir_from_env()

    vad = ctx.proc.userdata.get("vad")

    llm_model = _llm_model()
    llm_kwargs = {}
    if llm_model:
        llm_kwargs["model"] = llm_model
    llm_api_key = _llm_api_key()
    if llm_api_key:
        llm_kwargs["api_key"] = llm_api_key
    llm_base_url = _llm_base_url()
    if llm_base_url:
        llm_kwargs["base_url"] = llm_base_url

    session = AgentSession(
        vad=vad,
        stt=_cartesia_stt(),
        tts=_cartesia_tts(),
        llm=openai.LLM(**llm_kwargs),
    )

    agent = DocumentAgent(doc_context_id=doc_context_id, contexts_dir=contexts_dir)

    await session.start(
        agent,
        room=ctx.room,
        room_options=RoomOptions(participant_identity=participant.identity),
    )

    session.say("Hi — I’m ready. What do you want to know about this document?")

    disconnected = asyncio.Event()
    ctx.room.on("disconnected", lambda *_: disconnected.set())
    await disconnected.wait()


if __name__ == "__main__":
    ws_url = os.environ.get("LIVEKIT_URL")
    api_key = os.environ.get("LIVEKIT_API_KEY")
    api_secret = os.environ.get("LIVEKIT_API_SECRET")
    if not ws_url or not api_key or not api_secret:
        raise SystemExit("LIVEKIT_URL / LIVEKIT_API_KEY / LIVEKIT_API_SECRET must be set")

    cli.run_app(
        worker.WorkerOptions(
            entrypoint_fnc=entrypoint,
            prewarm_fnc=prewarm,
            ws_url=ws_url,
            api_key=api_key,
            api_secret=api_secret,
            agent_name=os.environ.get("ODP_AGENT_NAME", "opendroidpdf-assistant"),
        )
    )
