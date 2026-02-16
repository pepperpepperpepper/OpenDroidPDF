# Monolith Split Plan — `AssistantSheetUi` + `OpenDroidPDFActivity`

This is a **structure-only** refactor plan to break up two large Android files into smaller, reviewable units **without behavior changes**.

Targets:
- `platform/android/src/org/opendroidpdf/app/assistant/AssistantSheetUi.java` (~3559 LOC)
- `platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java` (~1695 LOC)

## Ground rules (first pass)
- No behavior changes (pure extraction / delegation).
- No package moves on the first pass:
  - Extracted helpers for `AssistantSheetUi` stay in `org.opendroidpdf.app.assistant`.
  - Extracted helpers for `OpenDroidPDFActivity` stay in `org.opendroidpdf`.
- Avoid renames on the first pass (rename later once stable).
- Keep each new file **< 800 LOC** (soft target ~250–600 LOC).
- After each extraction (fast): `./platform/android/gradlew -p platform/android compileDebugJavaWithJavac`
- Before merging (CI parity): `./platform/android/gradlew -p platform/android lintDebug lintRelease`

---

## A) Split plan: `AssistantSheetUi` (keep `AssistantSheetUi` as orchestrator)

### 1) Dialog construction + view wiring
**New file:** `platform/android/src/org/opendroidpdf/app/assistant/AssistantSheetDialogBinder.java`

Moves (from `AssistantSheetUi`):
- Bottom-sheet creation/inflation, `findViewById`, initial UI state setup.
- `BottomSheetBehavior` setup (peek/half/expanded, callbacks).
- Popup/menu wiring + provider line wiring.
- Produces a `Views` handle (all view refs) and callback hooks (send/stop/attach/setup).

### 2) Chat UI rendering
**New file:** `platform/android/src/org/opendroidpdf/app/assistant/AssistantSheetChatUi.java`

Moves:
- Chat bubble creation (user/assistant/pending), “thinking” bubble lifecycle.
- Citation + related-questions rendering.
- “Copy” UI action + scroll-to-bottom behavior.

### 3) Attachments UI + context text building
**New file:** `platform/android/src/org/opendroidpdf/app/assistant/AssistantSheetAttachmentsUi.java`

Moves:
- Attachments row rendering (chips/rows, remove buttons).
- Intent URI collection (single + multi-select), persisted permission flags handling.
- Attachment text extraction/truncation + budget logic (currently in `AssistantSheetUi`).

### 4) Document/selection context builder
**New file:** `platform/android/src/org/opendroidpdf/app/assistant/AssistantSheetContextBuilder.java`

Moves:
- Preview/context extraction for `Scope.SELECTION`, `PAGE`, `TOC_SECTION`, `DOCUMENT`.
- Page range assembly, max-chars truncation, “show sources” gating.

### 5) Ask runner (async orchestration + cancellation)
**New file:** `platform/android/src/org/opendroidpdf/app/assistant/AssistantSheetAskRunner.java`

Moves:
- Executor orchestration + cancellation plumbing (`AtomicBoolean`, active `Call` references).
- Calls into `AssistantLlmClient` (ask/summarize/test).
- UI-thread posting back into `AssistantSheetChatUi` / binder callbacks.

### 6) Keep `AssistantSheetUi.java` as public API
`AssistantSheetUi` remains the stable entrypoint:
- `show()`, `showAskForSelection()`, `showSummaryForSelection()`, `showSummaryForTocSection()`
- `dismissIfOpen()`
- `updateReadAloudUi()` (may delegate to extracted helpers)

---

## B) Split plan: `OpenDroidPDFActivity` (keep `OpenDroidPDFActivity` as lifecycle orchestrator)

Note: to obey “no package moves” in the first pass, extracted files live in `org.opendroidpdf` even if they conceptually belong under `app/*`. We can relocate later once stable.

### 1) Reader chrome + bottom bars binder
**New file:** `platform/android/src/org/opendroidpdf/OpenDroidPDFReaderChromeController.java`

Moves (from `OpenDroidPDFActivity`):
- The “bind UI chrome” cluster currently triggered by `setTitle()`:
  - page indicator
  - page scrubber + preview
  - navigation menu button
  - reader bottom bars binding (quick actions / selection / annotate / add-text / read-aloud bar wiring *except* the read-aloud engine itself)
- `toggleReaderChrome()` (Acrobat-style chrome hide/show) + related visibility state handling.

### 2) Read-aloud ownership/coordinator
**New file:** `platform/android/src/org/opendroidpdf/OpenDroidPDFReadAloudCoordinator.java`

Moves:
- `ensureReadAloudController()` creation/wiring.
- `toggleReadAloudPlayPause()`, `stopReadAloudIfActive()`, `isReadAloudActive()`, `readAloudCursorOrNull()`
- Read-aloud bar state updates (icon/label/visibility), and forwarding cursor updates to `AssistantSheetUi.updateReadAloudUi(...)`.

### 3) Retained-core restore helper
**New file:** `platform/android/src/org/opendroidpdf/OpenDroidPDFRetainedCoreRestorer.java`

Moves:
- `restoreFromLastNonConfig(...)` + retained-policy/origin sync logic (keep signatures identical).
- Any “restore” helper logic currently embedded inside the Activity.

### 4) Pending text-annotation insert buffer
**New file:** `platform/android/src/org/opendroidpdf/OpenDroidPDFPendingTextInsertBuffer.java`

Moves:
- TTL-backed one-shot text buffer:
  - `setPendingTextAnnotationInsertText(...)`
  - `consumePendingTextAnnotationInsertTextOrNull()`
  - `clearPendingTextAnnotationInsertText()`

### 5) Keep `OpenDroidPDFActivity.java` as orchestrator
`OpenDroidPDFActivity` remains responsible for:
- Lifecycle (`onCreate/onResume/onPause/onStop/onDestroy/onNewIntent`)
- Composition wiring (`ActivityComposition`, delegates/controllers)
- Thin forwarding methods (wrappers call the extracted controllers)

---

## Acceptance / rollout checklist
- After each extraction:
  - `./platform/android/gradlew -p platform/android compileDebugJavaWithJavac`
- Before merge:
  - `./platform/android/gradlew -p platform/android lintDebug lintRelease`
  - Manual smoke:
    - Open any PDF → open Assistant sheet → ask question → add/remove attachment → stop ask
    - Toggle reader chrome (tap) and ensure scrubber/page indicator/bottom bars behave as before
    - Start/pause/stop Read Aloud (and verify Assistant sheet reflects cursor updates)

