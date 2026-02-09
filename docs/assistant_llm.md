# Assistant (LLM) integration (Android)

OpenDroidPDF includes an Acrobat-style Assistant sheet that can run **Ask** (Q&A) and **Summary** against document text using a configurable third-party LLM provider (OpenAI-compatible HTTP).

## Code map

- Assistant sheet UI: `platform/android/src/org/opendroidpdf/app/assistant/AssistantSheetUi.java`
- Context extraction (Selection/Page/Document → `Page N:` blocks): `platform/android/src/org/opendroidpdf/app/assistant/AssistantContextTextExtractor.java`
- Ask transcript (in-memory, per document session): `platform/android/src/org/opendroidpdf/app/assistant/AssistantAskTranscriptStore.java`
- Multi-doc attachments store (in-memory, per document session): `platform/android/src/org/opendroidpdf/app/assistant/AssistantAttachmentsStore.java`
- Provider config + defaults: `platform/android/src/org/opendroidpdf/app/assistant/AssistantLlmProviderConfig.java`, `platform/android/src/org/opendroidpdf/app/assistant/AssistantLlmProvidersStore.java`
- API key storage: `platform/android/src/org/opendroidpdf/app/assistant/AssistantSecrets.java`
- HTTP request/response + citations parsing: `platform/android/src/org/opendroidpdf/app/assistant/AssistantLlmClient.java`

## Scopes

- `Selection`: enabled when there is a text selection.
  - Shortcut: the text selection toolbar includes **Explain** (Ask + Selection) and **Summarize** (Summary + Selection) buttons.
- `This page`: extracts the current page’s text.
- `This section (TOC)`: extracts a TOC heading’s page range (start page → end page). Quick entry: **Contents** → (⋯) → **Summarize section**.
- `Whole document`: Ask supports it (cancelable extraction + privacy preview). Summary supports it with an extra safety confirmation and progressive summarization (chunk → combine) when the document is too large to send in one request.
- Multi-doc context (Ask): tap **Add documents** (+) to attach additional PDFs/EPUBs. Attached docs are appended to the Ask context as an `Attachments` section (excerpted, no page markers). Citations are still restricted to the current document’s page numbers. Remove an attachment via its (×) chip, or **Assistant options → Clear attachments**.

## Read aloud (Assistant)

Assistant’s `Read aloud` mode controls the existing **System TTS** read-aloud player (no LLM provider required):

- Starts from the selected scope:
  - `Selection`: reads the current text selection.
  - `This page`: reads from the current page.
  - `This section (TOC)`: reads from section start → end page.
  - `Whole document`: reads from the first page → end (skipping initial pages with no extractable text).
- While playing:
  - The document view highlights the currently spoken line and auto-scrolls to follow.
  - The Assistant sheet shows Play/Pause + Stop and a “Now reading: p. X” cursor excerpt.

## Voice prompt (Assistant)

The Assistant sheet includes a mic icon (**Ask** mode) that captures a voice question and inserts it into the Ask input:

- Uses **Cartesia STT** (requires setting a Cartesia API key in **Settings → Assistant**).
- Auto-starts recording when launched from the mic button.
- Respects **Wi‑Fi only** (when enabled, voice prompt is blocked unless connected to Wi‑Fi).

## Provider protocol (OpenAI-compatible)

- Endpoint: `POST <baseUrl>/v1/chat/completions` (see `AssistantLlmClient.chatCompletionsUrl(...)`)
- Auth: `Authorization: Bearer <apiKey>`
- Ask request format:
  - `messages[0]` is a `system` instruction that requires a **single JSON object** response:
    - `answerText: string`
    - `citations: int[]` (1-based page numbers that appear in the provided context)
  - Optional: bounded prior transcript messages (for follow-ups)
  - Final: a `user` message containing `QUESTION:` and `CONTEXT:` (the extracted `Page N:` blocks)

## Follow-ups (bounded chat history)

Each Ask request includes a bounded portion of the existing transcript so follow-up questions behave conversationally:

- Max history messages: `12`
- Max history characters: `4000`
- History content: user/assistant message text (+ a small `Sources: p. ...` line derived from prior citations when present)
- The current request still provides fresh `CONTEXT:`; prior context blocks are not resent as part of history.
- The privacy preview shows the outgoing content, including chat history when present.

## Ask answer actions

Each assistant answer bubble includes action chips:

- Copy: copies the answer text to the clipboard.
- Save as note: writes a PDF note to the Notes directory via `AssistantNoteDocumentCreator.createAnswerNotePdf(...)` and opens it in-app.
- Insert into document…: prompts for a page number (defaults to the current page), collapses the sheet, enters “tap to place” mode, and inserts the text as a FreeText annotation.
- Export…: shares the answer as plain text via the Android share sheet.

Summary mode includes:

- Insert into document…: same placement flow (Selection / Page / TOC section).
- Export…: creates a temporary PDF “summary note” and opens the existing **Export…** sheet to share/save/print it.

## Build

- Android debug build: `cd platform/android && ./gradlew assembleDebug`
