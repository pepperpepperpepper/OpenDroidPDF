# Assistant (LLM) integration (Android)

OpenDroidPDF includes an Acrobat-style Assistant sheet that can run **Ask** (Q&A) and **Summary** against document text using a configurable third-party LLM provider (OpenAI-compatible HTTP).

## Code map

- Assistant sheet UI: `platform/android/src/org/opendroidpdf/app/assistant/AssistantSheetUi.java`
- Context extraction (Selection/Page/Document → `Page N:` blocks): `platform/android/src/org/opendroidpdf/app/assistant/AssistantContextTextExtractor.java`
- Ask transcript (in-memory, per document session): `platform/android/src/org/opendroidpdf/app/assistant/AssistantAskTranscriptStore.java`
- Provider config + defaults: `platform/android/src/org/opendroidpdf/app/assistant/AssistantLlmProviderConfig.java`, `platform/android/src/org/opendroidpdf/app/assistant/AssistantLlmProvidersStore.java`
- API key storage: `platform/android/src/org/opendroidpdf/app/assistant/AssistantSecrets.java`
- HTTP request/response + citations parsing: `platform/android/src/org/opendroidpdf/app/assistant/AssistantLlmClient.java`

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
- Insert into document…: UI is present; insertion behavior is tracked separately in `plan.md`.
- Export…: shares the answer as plain text via the Android share sheet.

## Build

- Android debug build: `cd platform/android && ./gradlew assembleDebug`
