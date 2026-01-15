# Refactor Plan: Single-Owner Controllers (De-monolith)

## Goals
- Reduce “monolith” file sizes to make changes safer and reviewable.
- Enforce **single ownership**: every end-to-end “process” has **one owning controller** that owns state, invariants, and side effects.
- Separate UI (View) concerns from feature/flow logic (controllers own the logic; views forward events).
- Replace ad-hoc “delegate/helper/ops” sharding with explicit **Controller** vs **Service/Codec** boundaries.
- Keep the public surface area stable initially (call sites shouldn’t need to change in the first pass).
- Enable tighter, more deterministic testing (unit tests for pure logic; instrumentation for UI glue).

## Architecture: Ownership + Controllers

### Vocabulary
- **Process**: a user-visible workflow that spans more than one function call (often stateful), e.g. “edit FreeText contents”, “inline widget editor”, “sidecar bundle import/export”.
- **Owner**: the single place that is allowed to change the state for a process and enforce invariants.
- **Controller**: the process owner; may be stateful; coordinates repos/services; performs side effects.
- **Service/Codec**: stateless helper used by a controller; does not own process state; does not initiate side effects outside its inputs/outputs.
- **View**: Android UI class (`View`, `Activity`) that forwards events and renders state; it is not the owner of business rules.

### Rules (to avoid “delegate soup”)
1. **One process → one controller (owner).** All state mutations for that process funnel through the controller.
2. Views should be **composition roots**: wire dependencies and forward events; avoid embedding workflow rules.
3. Extracted code must be either:
   - `*Controller` (owns a process), or
   - `*Service` / `*Codec` (pure helper).
4. Controllers may use **narrow Host/Ui interfaces** to interact with Android; services must not depend on Android UI.
5. Prefer **feature packages** (`org.opendroidpdf.app.<feature>`) for controllers/services; keep `org.opendroidpdf.*View*` as view-layer only.
6. New code should avoid names like `Delegate`, `Helper`, or `Ops` unless it is clearly an internal service; prefer `Controller` / `Service`.

### Ownership map (current, 2026-01-15)
- **Page View composition (UI only):** `MuPDFPageView`
- **Widgets + signature + inline widget editor (process owner):** `MuPDFPageViewWidgets` *(rename candidate: `PageWidgetsController`)*
- **Text annotation interaction UX (process owner):** `MuPDFPageViewTextAnnotations` *(rename candidate: `PageTextAnnotationsController`)*
- **Text annotation domain routing (process owner):** `TextAnnotationPageDelegate` *(rename candidate: `TextAnnotationPageController`)*
- **Embedded FreeText mutation + undo (process owner):** `TextAnnotationEmbeddedFreeTextOps` *(rename candidate: `EmbeddedFreeTextController`)*
- **Sidecar note mutation (process owner):** `TextAnnotationSidecarNoteOps` *(rename candidate: `SidecarNoteController`)*
- **Text annotation prompt/edit UI (process owner):** `TextAnnotationController`
- **Sidecar CRUD + undo/redo (process owner):** `SidecarAnnotationSession`
- **Sidecar bundle JSON (codec):** `SidecarBundleJson` *(used by session; not a process owner)*
- **Sidecar highlight reanchor (service):** `SidecarHighlightReanchorer` *(used by session; not a process owner)*
- **Genymotion text-annot smoke UI flow (process owner):** `scripts/lib/geny_pdf_text_annot_steps.sh`
- **Genymotion smoke OCR (service):** `scripts/lib/geny_pdf_smoke_ocr.sh`

## Scope
This plan proposes **multiple separate 3-file splits** (each target becomes **3 files total** by extracting
two cohesive controllers/services while keeping a small “orchestrator” (view/controller) file):
- ✅ `platform/android/src/org/opendroidpdf/MuPDFPageView.java`
- ✅ `platform/android/src/org/opendroidpdf/app/sidecar/SidecarAnnotationSession.java`
- ✅ `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationPageDelegate.java`
- ✅ `platform/common/pp_core_pdf_annots_freetext.c`
- ✅ `scripts/geny_pdf_text_annot_smoke.sh`

## Current Status (2026-01-15)
- ✅ `MuPDFPageView` split into 3 files (`MuPDFPageView.java` now ~769 LOC; was ~1593). Switched most callers to use `MuPDFPageView.textAnnotationDelegate()` directly to keep the view “thin”.
- ✅ `SidecarAnnotationSession` split into 3 files (`SidecarAnnotationSession.java` now ~892 LOC; was ~1366). Moved the reflow `PageTextProvider` interface and annotated-layout recording helpers out of the session owner.
- ✅ `TextAnnotationPageDelegate` split into 3 files (`TextAnnotationPageDelegate.java` now ~477 LOC; was ~2541).
- ✅ `pp_core_pdf_annots_freetext.c` split into 3 compilation units (`pp_core_pdf_annots_freetext.c` now ~489 LOC; was ~1553).
- ✅ `geny_pdf_text_annot_smoke.sh` split into orchestrator + 2 libs (`geny_pdf_text_annot_smoke.sh` now ~60 LOC; was ~1614).
- ✅ Verified `platform/android/gradlew -p platform/android compileDebugJavaWithJavac`.

### Follow-ups (optional)
- Rename/move extracted components to match the controller/service vocabulary (e.g., `*Controller`, `*Service`, feature packages) to reduce “delegate/ops” confusion.
- Add focused tests: `SidecarBundleJson` roundtrip + `SidecarHighlightReanchorer` regression (synthetic `PageTextProvider`).
- Run `scripts/geny_pdf_text_annot_smoke.sh` end-to-end once a Genymotion/adb target is reachable.

### Controller Naming + Packaging (detailed)
Goal: avoid “delegate/ops soup” by making process-owners read as controllers, and keep view-only classes as views.

#### Naming changes (planned; do after audit)
These are **pure naming refactors** (no behavior change). Apply **one rename at a time**, run a fast compile, then proceed.

- ☐ `MuPDFPageViewWidgets` → `PageWidgetsController`
  - File: `platform/android/src/org/opendroidpdf/MuPDFPageViewWidgets.java`
  - Rationale: owns the “widgets + signature + inline widget editor” process; `MuPDFPageView` should only forward view events.
- ☐ `MuPDFPageViewTextAnnotations` → `PageTextAnnotationsController`
  - File: `platform/android/src/org/opendroidpdf/MuPDFPageViewTextAnnotations.java`
  - Rationale: owns the page-level selection UX + inline editor state; routes domain mutations via `TextAnnotationPageDelegate`.
- ☐ `TextAnnotationPageDelegate` → `TextAnnotationPageController`
  - File: `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationPageDelegate.java`
  - Rationale: the “router/owner” for embedded-vs-sidecar behavior; owns the decision and the “single funnel” for state mutations.
- ☐ `TextAnnotationEmbeddedFreeTextOps` → `EmbeddedFreeTextController`
  - File: `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationEmbeddedFreeTextOps.java`
  - Rationale: owns embedded FreeText mutation + undo stack integration; this is a process owner (not a generic helper).
- ☐ `TextAnnotationSidecarNoteOps` → `SidecarNoteController`
  - File: `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationSidecarNoteOps.java`
  - Rationale: owns sidecar note mutation + auto-fit rules; it is a process owner (not a generic helper).

Validation (fast, after each rename): `./platform/android/gradlew -p platform/android compileDebugJavaWithJavac`

#### Packaging changes (planned; do *after* naming audit)
Problem: the extracted controllers currently still live next to view types under `org.opendroidpdf` (same package as the view),
which makes it easy for view-only concerns and process logic to leak into each other.

Plan (small, low-risk steps; no behavior changes):
1. Decide the target feature packages for page-level controllers:
   - `PageWidgetsController` → `org.opendroidpdf.app.reader.widgets` (or `org.opendroidpdf.app.widgets`)
   - `PageTextAnnotationsController` → `org.opendroidpdf.app.reader.textannot` (or `org.opendroidpdf.app.annotation.page`)
2. Move **one controller at a time**, and keep `MuPDFPageView` as the composition root that wires hosts.
3. To avoid a cascading import diff across the codebase, prefer one of these migration patterns:
   - **A) Wrapper class (recommended for large moves):**
     - Keep the old package class name as a thin `@Deprecated` wrapper that instantiates/forwards into the new controller.
     - Remove the wrapper only once all call sites are updated and stable.
   - **B) Direct move (OK for low fan-out):**
     - Move file + update package declaration + fix imports; run `compileDebugJavaWithJavac`.
4. After each move: run a targeted smoke script (when Genymotion is reachable) or at least the fast Gradle compile.

Acceptance (packaging phase):
- `MuPDFPageView` remains view-only (composition + forwarding).
- Page-level processes have a single clear owner in `app/...` feature packages.
- No new “Delegate/Helper/Ops” names introduced.

---

# A) `MuPDFPageView.java` (now ~945 LOC; was ~1593)

## Current Problems
- Too many responsibilities in one class: widget UI, signature flow, selection routing, text annotation editing, ink, overlay invalidation, hit routing, inline editors, etc.
- Hard to reason about lifecycle ordering (loading annotations vs selection state vs inline editor state).
- High merge conflict risk: many features touch the same file.

## Proposed 3-file split

### 1) `MuPDFPageView.java` (keep as the View + composition root)
**Keep here**
- Constructor wiring and owned dependencies (`MuPdfController`, `ReaderComposition`, `InkController`, etc.).
- Core View lifecycle overrides and rendering plumbing inherited from `PageView`.
- Minimal “surface area” methods called by other components (for now), delegating into controllers.
- Ink draw/erase entry points (`startDraw/continueDraw/finishDraw/...`) can remain here (or move later).

**Becomes responsible for**
- Instantiating feature controllers and providing them with narrow Host interfaces.
- Forwarding view events (touch/layout/page-changes) to the correct controller.

### 2) `MuPDFPageViewWidgets.java` (controller: widgets + signature + inline widget editor)
Controller focused on form widgets and inline widget editing.

**Move from `MuPDFPageView` into this file**
- `WidgetUiBridge.InlineTextEditorHost` implementation and fields:
  - `inlineWidgetEditor`, `inlineWidgetEditorBoundsPx`
  - `showInlineTextEditor(...)`, `hideInlineTextEditor(...)`
- Widget dialog launchers / handlers:
  - `invokeTextDialog(...)`, `invokeChoiceDialog(...)`
  - `setWidgetFieldNavigationRequester(...)`, `widgetAreasForNavigation(...)`, etc.
- Signature flow glue:
  - `SignatureFlowController` interactions (`invokeSigningDialog`, `invokeSignatureCheckingDialog`, “no support” warnings)
- Widget areas loading lifecycle:
- `mWidgetAreas`, `WidgetAreasLoader` usage, and any callbacks (e.g. `onResult(RectF[] areas)`).

**Public API approach**
- Keep existing `MuPDFPageView` public methods, but make them forward into `MuPDFPageViewWidgets`.
- This avoids a cascade of edits across `MuPDFReaderView`, controllers, and tests.

**Host surface for the controller**
- A small `MuPDFPageViewWidgets.Host` interface implemented by `MuPDFPageView`:
  - `Context context()`
  - `float scale()`, `int viewLeft()`, `int viewTop()`
  - `RectF widgetAreaAt(docRelX, docRelY)` / `Rect widgetBoundsDocToViewPx(RectF)`
  - `void requestLayoutSafe()`, `void addViewSafe(View)`, `void removeViewSafe(View)`
  - `void reportChange()` (wrap `muPdfController.markDocumentDirty()` + `changeReporter.run()`)

### 3) `MuPDFPageViewTextAnnotations.java` (controller: page-level text annotation interaction)
Controller focused on page-level text annotation interaction (selection UX + inline editor UI state), while delegating
domain mutations to the text-annotation domain controller (`TextAnnotationPageDelegate`).

**Move from `MuPDFPageView` into this file**
- Inline text-annotation editor state + fields:
  - `inlineTextAnnotEditor`, `inlineTextAnnotEditorBoundsPx`
  - `InlineTextAnnotState`, `inlineTextAnnotSubmitting`, `suppressInlineTextAnnotFocusLoss`
- “Forward to editor” logic:
  - `forwardTextAnnotation(...)` and all “selected text annotation …” getter/setter wrappers that currently just call `textAnnotationDelegate`.
- Any text-annotation clipboard / quick-actions glue that is currently routed through the page view.
- Selection-key tracking related to text annotation resize handles:
  - `textResizeHandlesEnabled`, `lastSelectionKey`, and selection-change reset logic.

**Keep `TextAnnotationPageDelegate`**
- The text-annotation domain controller already exists and encapsulates embedded-vs-sidecar decision-making.
- This controller becomes the “page-view facing owner” for interaction UX that:
  - Owns inline editor UI state
  - Coordinates with `TextAnnotationPageDelegate`, `SelectionUiBridge`, and `SidecarSelectionController`

**Host surface for the controller**
- A small `MuPDFPageViewTextAnnotations.Host` interface implemented by `MuPDFPageView`:
  - Accessors needed to map doc↔view coords and invalidate:
    - `float scale()`, `int viewWidthPx()`, `int viewHeightPx()`, `int pageNumber()`
    - `void invalidateOverlay()`, `void discardRenderedPage()`, `void loadAnnotations()`
  - Selection + sidecar dependencies:
    - `@Nullable SidecarAnnotationSession sidecarSessionOrNull()`
    - `SidecarSelectionController sidecarSelectionController()`
    - `AnnotationSelectionManager selectionManager()`
  - “Open editor” callback:
    - `void requestTextAnnotationFromUserInput(Annotation draft)`

## Step-by-step migration (low risk)
1. **Introduce controllers** with host interfaces, but do not change behavior.
2. Move widget fields/methods into `MuPDFPageViewWidgets` and forward calls from `MuPDFPageView`.
3. Move inline text annotation editor and wrapper methods into `MuPDFPageViewTextAnnotations`.
4. Reduce `MuPDFPageView` to “composition root + lifecycle + controller forwarding”.
5. Run:
   - `platform/android/gradlew -p platform/android lint` (optional)
   - `platform/android/gradlew -p platform/android connectedDebugAndroidTest` (at least `PagingAxisInstrumentedTest`)
   - Relevant Genymotion smokes (`geny_pdf_text_annot_smoke.sh`, widget-focused script).

## Acceptance Criteria
- No functional change (golden behavior preserved).
- `MuPDFPageView.java` becomes meaningfully smaller (target: **< 900 LOC**).
- New controllers/modules are cohesive and testable (less Android/UI-only state leaked into core flows).

---

# B) `SidecarAnnotationSession.java` (now ~943 LOC; was ~1366)

## Current Problems
- Mixes three large concerns:
  1) bundle import/export JSON
  2) highlight re-anchoring across reflow/layout changes
  3) CRUD + caches + undo/redo across ink/highlights/notes
- The re-anchor algorithm is complex and difficult to test in isolation.
- Bundle IO logic is mostly pure and should not live inside the session object.

## Proposed 3-file split

### 1) `SidecarAnnotationSession.java` (controller: session + CRUD + caches + undo/redo)
**Keep here**
- Caches (`inkCache`, `highlightCache`, `noteCache`) and store plumbing.
- CRUD methods for ink/highlights/notes and undo/redo stack management.
- Small “has any annotations?” queries and “current layout” filtering rules.

**Change here**
- Replace “big blocks” of JSON IO and highlight reanchor with calls into services/codecs.

### 2) `SidecarBundleJson.java` (codec/service: bundle model + JSON IO + import)
Service/codec focused on bundle serialization and import.

**Move into this file**
- `SidecarBundle` model and parsing/writing:
  - current `writeBundleJson(OutputStream)`
  - current `readBundleJson(InputStream)`
- `ImportStats` model.
- Import logic that maps bundle items into store rows.

**API sketch**
- `SidecarBundleJson.write(docId, store, outputStream)`
- `SidecarBundleJson.read(inputStream): SidecarBundle`
- `SidecarBundleJson.importIntoDoc(docId, store, bundle): ImportStats`

**Session impact**
- `SidecarAnnotationSession.writeBundleJson(...)` becomes a thin forwarding call.
- `SidecarAnnotationSession.readBundleJson(...)` becomes either:
  - a forwarder static method (to keep call sites stable), or
  - a direct new call site update (second pass).

### 3) `SidecarHighlightReanchorer.java` (service: reflow/layout highlight re-anchor algorithm)
Service focused on the algorithm (no session state; no UI).

**Move into this file**
- `reanchorHighlightsForCurrentLayout(PageTextProvider pageText)` and its helpers:
  - layout profile parsing (`LAYOUT_ID_PAGE_SIZE` usage)
  - scoring/context window rules (`HIGHLIGHT_REANCHOR_RADIUS_PAGES`, min score)
  - any text tokenization/normalization and matching loops

**API sketch**
- `SidecarHighlightReanchorer.reanchor(docId, layoutProfileId, store, reflowPrefsStore, reflowPrefsSnapshot, pageText): int`
  - returns number of highlights updated/reanchored

**Session impact**
- `SidecarAnnotationSession.reanchorHighlightsForCurrentLayout(...)` becomes a thin forwarding call, then clears/rebuilds caches as needed.

## Step-by-step migration (low risk)
1. Extract bundle IO into `SidecarBundleJson` keeping method signatures stable via forwarding methods.
2. Extract highlight reanchor algorithm into `SidecarHighlightReanchorer`.
3. Ensure caches remain correct:
   - after bundle import or reanchor, clear relevant caches (`highlightCache`) and rely on next `highlightsForPage()` call to repopulate.
4. Add focused tests (preferred):
   - JVM/unit test for `SidecarBundleJson` roundtrip using in-memory store (or a fake store).
   - Algorithm regression test for `SidecarHighlightReanchorer` using a small synthetic `PageTextProvider`.

## Acceptance Criteria
- No functional change for existing sidecar operations (CRUD + undo/redo).
- `SidecarAnnotationSession.java` target: **< 900 LOC** after extraction.
- Bundle IO + reanchor logic are independently testable without a full Android UI stack.

---

# C) `TextAnnotationPageDelegate.java` (now ~477 LOC; was ~2541)

## Current Problems
- One class owns too many “text annotation” responsibilities:
  - embedded FreeText mutation (MuPDF/raw repository calls)
  - sidecar note mutation (SQLite store + overlay invalidation + selection updates)
  - style/locks/background/border/rotation/alignment/paragraph behaviors
  - undo/redo (embedded), duplication, clipboard copy/paste, auto-fit bounds behavior
- High merge-conflict risk and hard reviewability for any text-annotation feature.

## Proposed 3-file split

### 1) `TextAnnotationPageDelegate.java` (controller: selection + dispatch/routing)
**Keep here**
- The public API used by page/UI controllers (`applyTextStyleToSelectedTextAnnotation`, `fitSelectedTextAnnotationToText`, etc.).
- Selection accessors:
  - “What is selected?” across embedded vs sidecar
  - routing helpers (`selectedEmbeddedAnnotationOrNull`, `sidecarNoteById`, etc.)
- Clipboard entry points (`copySelectedTextAnnotationToClipboard`, `pasteTextAnnotationFromClipboard`) can stay here initially,
  but should route “build payload”/“apply payload” work through the domain controllers (embedded vs sidecar).

**Becomes responsible for**
- Constructing two domain controllers (embedded vs sidecar) and routing based on `sidecarSessionOrNull()`.
- Owning the “which backend?” decision so it stays single-owner and consistent.

### 2) `TextAnnotationEmbeddedFreeTextOps.java` (controller: embedded PDF FreeText ops + undo)
This is the single owner for embedded FreeText mutation + undo/redo. Consider renaming to
`EmbeddedFreeTextController` once the surface stabilizes.

**Move into this file**
- All `MuPdfController.rawRepository()` interactions for FreeText:
  - update rect, text, style (font size/color), background, border, locks, alignment, rotation
  - getters for current FreeText attributes (alignment/font size/family/style flags/paragraph/rotation/locks)
- Embedded-only operations:
  - add FreeText (object-id resolution), delete FreeText, duplicate FreeText
  - `fitSelectedTextAnnotationToText()` internals (bounds fitter)
- Embedded undo/redo stack and snapshots:
  - `EmbeddedFreeTextSnapshot`, `EmbeddedFreeTextPresenceOp`, snapshot/apply helpers

**API sketch**
- `boolean commitBounds(long objectId, RectF boundsDoc, boolean markUserResized)`
- `boolean updateText(long objectId, String text)`
- `boolean applyStyle(...)`, `boolean applyBackground(...)`, `boolean applyBorder(...)`, `boolean applyLocks(...)`
- `FreeTextStyleSnapshot readStyle(long objectId)` (or discrete getters, whichever is less invasive)

### 3) `TextAnnotationSidecarNoteOps.java` (controller: sidecar note ops + auto-fit)
This is the single owner for sidecar note mutation behaviors (bounds/text/style/locks/rotation + auto-fit policy).
Consider renaming to `SidecarNoteController` once stable.

**Move into this file**
- Sidecar selection routing helpers:
  - select note/highlight by id
- Sidecar note mutation:
  - commit bounds/text/style/font family/style flags/paragraph/background/border/locks/rotation
  - auto-fit bounds behavior after text edits (and “respect width after user resize” policy)
- Sidecar duplication + paste application:
  - “create note, apply style bundle, select new note”

**API sketch**
- `boolean commitBounds(String noteId, RectF boundsDoc, boolean markUserResized)`
- `boolean updateText(String noteId, String text)`
- `boolean applyStyleToSelected(...)` (or selection-independent methods with id)

## Step-by-step migration (low risk)
1. Introduce both domain controllers with minimal host interfaces; no behavior change.
2. Move embedded FreeText implementation blocks into `TextAnnotationEmbeddedFreeTextOps`, keep `TextAnnotationPageDelegate` methods as thin routing calls.
3. Move sidecar note implementation blocks into `TextAnnotationSidecarNoteOps`, keep thin routing calls.
4. Ensure clipboard stays stable:
   - `TextAnnotationClipboard.Payload` creation and application should be implemented by the domain controllers (router decides which).
5. Verify:
   - `platform/android/gradlew -p platform/android compileDebugJavaWithJavac`
   - run the most relevant connected tests/smokes for text annotation workflows.

## Acceptance Criteria
- No functional behavior changes (embedded FreeText and sidecar note workflows both behave the same).
- `TextAnnotationPageDelegate.java` becomes the routing controller (target: **< 900–1100 LOC** after extraction).
- Embedded FreeText operations can be reasoned about without reading sidecar code, and vice versa.
- Follow-up rename/package move reduces “delegate/ops” terminology in favor of `*Controller` / `*Service`.

---

# D) `platform/common/pp_core_pdf_annots_freetext.c` (now ~489 LOC; was ~1553)

## Current Problems
- Mixes low-level parsing/building (DA/DS), “business rules” (marker/flags), and exported API wrappers.
- Hard to review changes to one operation (e.g., paragraph) without understanding every DS helper.

## Proposed 3-file split

### 1) `pp_core_pdf_annots_freetext.c` (module: exported API + bridge glue)
**Keep here**
- Public entry points and their MuPDF-bridge wrappers:
  - `pp_pdf_update_freetext_*_by_object_id(...)`
  - `pp_pdf_update_freetext_*_by_object_id_mupdf(...)`
  - `pp_pdf_get_freetext_*_by_object_id(...)` wrappers
- Minimal validation/argument normalization, then forward into “impl” functions in internal modules.

### 2) `pp_core_pdf_annots_freetext_ds.c` (DA/DS parsing/building + style utilities)
Internal module focused on the PDF string parsing/building bits.

**Move into this file**
- `/DA` parsing + helpers:
  - `opd_parse_default_appearance(...)`, `opd_rgb_from_default_appearance(...)`
- `/DS` marker + property parsing/building:
  - `opd_ds_has_marker`, `opd_ds_float_property`, `opd_text_style_flags_from_ds`
  - `opd_build_freetext_ds(...)` and any “font key ↔ full font name” helpers
- Other pure string utilities (`opd_strcasestr`, `opd_pdf_string_dup`, etc.)

### 3) `pp_core_pdf_annots_freetext_ops.c` (FreeText mutation/get “impl” functions)
Internal module focused on the actual MuPDF object mutation (still “core”, but separated from parsing/building).

**Move into this file**
- The `*_impl(...)` bodies:
  - `pp_pdf_update_freetext_style_by_object_id_impl`
  - `pp_pdf_update_freetext_background_by_object_id_impl`
  - `pp_pdf_update_freetext_border_by_object_id_impl`
  - `pp_pdf_update_freetext_alignment_by_object_id_impl`
  - `pp_pdf_update_freetext_rotation_by_object_id_impl`
  - `pp_pdf_get/update_freetext_style_flags_by_object_id_impl`
  - `pp_pdf_get/update_freetext_paragraph_by_object_id_impl`
- `ops.c` should call into `ds.c` for parsing/building and stay free of string-scanning details where possible.

## Build wiring
- Add the two new compilation units to the appropriate build lists (Makefiles/Android.mk as needed).
- Ensure any formerly-`static` functions used across compilation units become `static` within their new file, or `pp_`-prefixed internal functions declared in `pp_core_pdf_annots_internal.h`.

## Acceptance Criteria
- No change in output PDFs for existing text annotation operations.
- `pp_core_pdf_annots_freetext.c` becomes “API-only” (target: **< 400–600 LOC**).
- Parsing/building logic becomes independently testable in a small C harness (optional).

---

# E) `scripts/geny_pdf_text_annot_smoke.sh` (now ~60 LOC; was ~1614)

## Current Problems
- One script mixes three concerns:
  1) UI automation steps (Genymotion + uiautomator flows)
  2) OCR/rendering utilities (Poppler + Tesseract + image preprocessing)
  3) orchestration/reporting/debug artifact capture
- Hard to reuse OCR or UI steps in other smokes without copy/paste.

## Proposed 3-file split

### 1) `scripts/geny_pdf_text_annot_smoke.sh` (scenario runner / single owner)
**Keep here**
- Environment parsing (`DEVICE`, `APK`, tokens, feature toggles).
- Dependency checks (`pdftoppm`, `tesseract`).
- `main()` that calls into the two libs and manages “save artifacts on failure” behavior.

### 2) `scripts/lib/geny_pdf_text_annot_steps.sh` (module: UI automation steps)
Module of “actions” (no OCR). Treat this as the single owner of the UI flow for this smoke.

**Move into this file**
- All UI-driven flows:
  - install/launch
  - open document (content:// via DocumentsUI)
  - create text annotation, edit, resize/wrap interactions, quick-actions flows
  - save and re-open behaviors
- Should depend on `geny_uia.sh` for primitives (tap, swipe, dump-uia, etc).

### 3) `scripts/lib/geny_pdf_smoke_ocr.sh` (service: render + OCR + token detection)
Service library of PDF→PNG rendering and OCR assertions (stateless; reusable across smokes).

**Move into this file**
- `_render_pdf_to_png`, `_ocr_png`, token assertion helpers (fuzzy + bbox-based).
- Image preprocessing helpers (crop/threshold) and any embedded python/PIL blocks.
- Wrap/resize OCR assertions used by the text-annot smoke (and potentially other smokes).

## Step-by-step migration (low risk)
1. Extract OCR functions into `scripts/lib/geny_pdf_smoke_ocr.sh` and source it from the main script.
2. Extract UI step functions into `scripts/lib/geny_pdf_text_annot_steps.sh` (UI flow owner).
3. Keep `scripts/geny_pdf_text_annot_smoke.sh` argument/env surface stable so CI callers don’t break.
4. Verify by running the smoke end-to-end (same device/apk/pdf inputs).

## Acceptance Criteria
- The top-level smoke remains one command with the same env vars.
- Shared OCR and UI step code becomes reusable by other scripts (reduces future duplication).
- The main script becomes readable “glue” (target: **< 300–500 LOC**).
