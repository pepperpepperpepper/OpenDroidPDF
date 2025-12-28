# Text Annotation Editing – Implementation Plan

Intent: make “text annotations” behave like a real margin-notes tool: **no opaque background**, **reliable re-edit by tapping**, **no crashes**, and **ONE OWNER** for the add/edit/commit pipeline across PDF + sidecar docs (EPUB / read‑only PDFs).

## Requirements (user-facing)
- Text annotation renders with **transparent (or near-transparent) background** by default (does not white-out the page).
- Tapping a text annotation reliably allows **select + re-edit** (no “can’t access it”).
- Editing text annotations does **not crash** (even after waiting/idle).
- Behavior is consistent across:
  - PDF writable (embedded annotations)
  - PDF read‑only / EPUB (sidecar)
- “ONE OWNER”: no Activity/view-owned ad-hoc state; one controller owns the flow and uses stable IDs.

## Scope
### In
- FreeText appearance (background/border) in native layer
- Robust edit pipeline (no delete+re-add races)
- Consistent tap semantics for embedded + sidecar text annotations
- Sidecar feature parity for “visible text” (not just a note marker)
- Deterministic smokes that reproduce/guard regressions (no OCR as primary oracle)

### Out (for this plan)
- Full inline text box resizing/drag handles
- Rich text, fonts, per-annotation styling UI
- EPUB text-anchored highlights work (separate track)

## What I found (current behavior + root causes)
### 1) Opaque white background is intentional today
- `platform/common/pp_core.c` (`pp_pdf_add_annot_impl`, `PDF_ANNOT_FREE_TEXT`) sets `/C` to **solid white** explicitly to avoid the earlier “black bar / invisible text” failure mode.
- This matches the observed “text shows, but with a white opaque rectangle behind it”.

### 2) Re-edit is fragile because editing is “delete selected + add new”
- Current UI flow lives in `platform/android/src/org/opendroidpdf/DocViewFactory.java` (`addTextAnnotFromUserInput`):
  - Always calls `pageView.deleteSelectedAnnotation()` then `pageView.addTextAnnotation(annot)` (even for *new* annotations).
  - Delete/add operations are async and can race (order not guaranteed).
  - Selection uses “index in current annotations array”, not a stable ID.
- Result: inconsistent re-edit + occasional crash depending on timing/lifecycle.

### 3) Sidecar “notes” are not visible text today
- Sidecar “text” is stored as `SidecarNote(text, bounds)` but rendered only as a small marker in:
  - `platform/android/src/org/opendroidpdf/app/overlay/SidecarAnnotationRenderer.java`
- This prevents “margin writing” parity on EPUB / read-only PDFs.

## Proposed design (ONE OWNER)
Introduce a document-scoped `TextAnnotationController` that owns:
- When a tap should **select** vs **edit**
- The edit UI (dialog/sheet)
- The “apply” operation routed to the correct backend using **stable identifiers**

### Canonical data model
Use one conceptual object for both embedded and sidecar:
- `TextAnnotationRef`
  - `pageIndex`
  - `boundsDoc` (top-left origin, doc units)
  - `text`
  - `id`:
    - Embedded PDF: `objectNumber` (already present on `Annotation`)
    - Sidecar: `noteId`

### Backend interface (no UI types)
`TextAnnotationBackend` (document-scoped):
- `create(ref) -> id`
- `update(id, newText)`
- `delete(id)`
- `list(pageIndex)` (only if needed by UI; otherwise selection provides the ref)

Implementations:
- `EmbeddedPdfTextBackend` (MuPDF): update by `objectNumber` (new JNI API).
- `SidecarTextBackend`: update by `noteId` in SQLite sidecar store.

### Tap semantics (consistent + predictable)
- First tap on text annotation: **select** (shows selection box and enables toolbar actions).
- Second tap on the same selected text annotation (or explicit “Edit” action): **open editor**.
  - This mirrors existing sidecar note behavior and avoids accidental editor popups.

## Implementation steps (ordered slices)

### Slice A — Fix FreeText background (native)
[ ] Update `platform/common/pp_core.c` FreeText creation to default to **transparent background**:
  - Keep `/DA` as the text color.
  - Set background fill alpha to `0` (or low, e.g. `0.12`) without reintroducing the “black bar”.
  - If needed: extend `pp_pdf_set_annot_color_opacity_dict` to write `CA/ca` when opacity is `0`.
[ ] Add a micro-render smoke (Android + desktop) to ensure FreeText remains visible (not black bar) with the new background rules.

### Slice B — Make text edits “update in place” (no delete+add)
[ ] Add JNI + Java plumbing to update FreeText contents by object number:
  - `MuPDFCore_updateAnnotationContentsByObjectNumberInternal(long objectNumber, String text)`
  - Surface through:
    - `platform/android/src/org/opendroidpdf/MuPDFCore.java`
    - `platform/android/src/org/opendroidpdf/core/MuPdfRepository.java`
    - `platform/android/src/org/opendroidpdf/core/MuPdfController.kt`
    - `platform/android/src/org/opendroidpdf/core/AnnotationController.kt` (async wrapper)
[ ] Replace `DocViewFactory.addTextAnnotFromUserInput` “delete+add” with:
  - If `annot.objectNumber != -1`: call update API
  - Else: create new FreeText
[ ] Ensure the page refresh path is explicit after update (invalidate/refresh appearance + redraw).

### Slice C — Move UI ownership out of `DocViewFactory` (ONE OWNER)
[ ] Introduce `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationController.java`:
  - Owns “open editor for new vs existing” and commit routing.
  - Receives a small Host interface (Context + page accessors) but does not reach into Activity directly.
[ ] Wire it via per-document composition (similar to `AnnotationUiController`):
  - `ReaderComposition` owns a `TextAnnotationController`
  - `MuPDFPageView` forwards “text annotation tapped” events to it
[ ] Reduce `DocViewFactory` to wiring only (no annotation business logic).

### Slice D — Make sidecar text visible (feature parity)
[ ] Extend sidecar rendering to draw text, not just a marker:
  - Add a renderer using `TextPaint` + `StaticLayout` inside `SidecarAnnotationRenderer` (or a dedicated `SidecarTextRenderer`).
  - Default: transparent background, no border; optional subtle shadow for readability.
[ ] Update `SidecarSelectionController` hitboxes if needed (bounds already exist).
[ ] Ensure export paths include sidecar text:
  - Flatten exporter already composites overlay; it will pick up rendered text automatically.
  - Embed exporter (`SidecarPdfEmbedExporter`) should map the same “transparent background” behavior when creating embedded FreeText.

### Slice E — Add smokes that catch “can’t re-edit” + crashes
[ ] Add/extend Genymotion smoke:
  - Open a PDF fixture
  - Add text annotation
  - Tap to select, tap again to edit, change text, save
  - Wait idle 60s
  - Repeat edit
  - Assert:
    - annotation list contains the updated contents (via `MuPDFCore_getAnnotationsInternal`)
    - no fatal logcat signals
[ ] Keep OCR optional (last resort). Prefer deterministic state + render non-blank heuristics.

## Testing and validation
- Local build: `cd /mnt/subtitled/repos/penandpdf/platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint`
- Emulator smokes:
  - `/mnt/subtitled/repos/penandpdf/scripts/geny_smoke.sh`
  - Add `/mnt/subtitled/repos/penandpdf/scripts/geny_text_annot_smoke.sh` (new) or extend existing.
- Manual acceptance:
  - Create/edit text annotation in margin; background does not obscure page
  - Tap to select, tap again to edit
  - No crash after save; no crash after ~60s idle

## Risks / edge cases
- MuPDF FreeText appearance generation quirks (the “black bar” regression): must verify render after background changes.
- Annotation identity stability:
  - Embedded updates should use `objectNumber`, not list index.
  - Sidecar updates should use `noteId`, not bounds matching.
- Lifecycle races: editor commit callback should verify the page/document is still active before forcing redraws.
- Coordinate mismatches between “doc units” and PDF coords in exporters (validate with a fixture PDF + export + reopen).

## Open questions (pick defaults now)
1) Background default: fully transparent (`0.0`) vs subtle (`~0.12`)?
2) Tap UX: “tap selects, second tap edits” (recommended) vs “single tap edits immediately”?

