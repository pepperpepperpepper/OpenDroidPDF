# Text Annotations (FreeText) — Editing + Direct Manipulation Plan

Intent: make text annotations behave like a real margin-notes tool: **transparent background**, **stable selection + re-edit**, **move/resize with visible bounds**, **no crashes**, and **ONE OWNER** of text-annotation interaction state (no view/activity-owned ad‑hoc flags).

This plan is written against the current tree state (Android MuPDF **1.27** APIs + existing `TextAnnotationController` + selection/gesture routers). It focuses on PDF FreeText first, then sidecar parity (EPUB/read‑only PDFs) using the same interaction model.

## Requirements (user-facing)
- Create a text annotation (FreeText) and see it immediately (no opaque white background).
- Single tap selects (shows bounding box); second tap edits text; explicit “Edit” action also works.
- Selected text annotation shows an obvious bounding box and corner handles (move/resize affordance).
- Move/resize works reliably and never blocks normal navigation:
  - Two-finger pinch zoom always works.
  - After zooming in, one-finger drag pans/scrolls the page normally.
  - Move/resize only activates on an intentional gesture (handles or long-press).
- No crashes when selecting/editing/moving text, including after idle + zoom.

## Current status (what’s already in-tree, and what’s next)
- Text edit UI exists: `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationController.java`.
- Embedded edit API exists: `MuPDFCore.updateAnnotationContentsByObjectNumber()` (JNI → `pp_pdf_update_annot_contents_by_object_id_*`).
- Direct manipulation plumbing exists (committed on `master`):
  - `platform/android/src/org/opendroidpdf/app/reader/gesture/TextAnnotationManipulationGestureHandler.java`
  - `platform/android/src/org/opendroidpdf/app/overlay/ItemSelectionHandles.java`
  - Handle rendering in `platform/android/src/org/opendroidpdf/app/overlay/ItemSelectionRenderer.java`
- Text style UI exists: overflow “Style” opens a dialog to adjust FreeText font size + color (separate prefs, correct font-size range).
- The recent blocker was **rect space mismatch on commit**: we were updating `/Rect` in the wrong coordinate space for MuPDF 1.27, which mirrored “move/resize” (drag down would commit as move up).
- Genymotion: FreeText create → select → edit → move → resize → save is now working end-to-end (see “Latest reproduction”).
- Optional refinements (deferred; see “Optional refinements” at the end of this doc):
  - Add a dedicated move handle (so move doesn’t require long-press).
  - Optional collapsed “marker-only/snippet” presentation for sidecar notes (on-screen only; export still includes full text).
  - Text readability options (e.g., subtle halo/outline, or an optional low-alpha background fill) defaulting to “no fill”.

### Latest reproduction (2025-12-29) — PASS (Genymotion)
- Command: `DEVICE=localhost:35329 POST_SAVE_HOME_WAIT_S=0 POST_EDIT_IDLE_TAP_S=0 UI_OCR_TIMEOUT_S=18 OUT_PREFIX=tmp_geny_pdf_text_annot_verify4 /mnt/subtitled/repos/penandpdf/scripts/geny_pdf_text_annot_smoke.sh`
- Result: **PASS** — creates FreeText, selects + re-edits (appends `_EDIT`), move-drags, handle-resizes, saves in-place, and OCR finds `ODPTEXTSMOKE_EDIT` in the pulled PDF render.
- Artifacts:
  - `/mnt/subtitled/repos/penandpdf/tmp_geny_pdf_text_annot_verify4_ui.png`
  - `/mnt/subtitled/repos/penandpdf/tmp_geny_pdf_text_annot_verify4_render.png`
  - `/mnt/subtitled/repos/penandpdf/tmp_geny_pdf_text_annot_verify4.pdf`
- What changed (why this used to fail at step [8/14]):
  - `ReaderView.slideViewOntoScreen*` can apply a small “settle” correction between tap gestures, so the second tap can land just outside the previous selection bounds.
  - Fix: `AnnotationHitHelper` now treats a near-miss second tap as an “edit” request when it’s within a time window + doc-space slop and a text annotation is already selected.
  - Fix: `TextAnnotationManipulationGestureHandler` now tolerates small start-drift after arming move (same settle correction) so the follow-up drag becomes a move instead of a pan.

### Latest verification (2025-12-30) — PASS (Genymotion)
- Build: `cd /mnt/subtitled/repos/penandpdf/platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint`
- Smokes:
  - `DEVICE=localhost:35329 /mnt/subtitled/repos/penandpdf/scripts/geny_pinch_zoom_smoke.sh` – **PASS** (`changed_ratio=0.1275`), confirms pinch-zoom doesn’t crash and one-finger pan changes viewport.
  - `DEVICE=localhost:35329 OUT_PREFIX=tmp_geny_pdf_text_annot_verify5 /mnt/subtitled/repos/penandpdf/scripts/geny_pdf_text_annot_smoke.sh` – **PASS** (includes `[9.8/14]` “pinch zoom + one-finger pan while text selected”).
- Note: `geny_pdf_text_annot_smoke.sh` step `[10.5/14]` (“tap-after-idle”) is a crash regression check; it may not reopen the dialog if the viewport moved during the pan test.

### Root cause + fix (2025-12-29): `pdf_set_annot_rect` expects *page-space* rects (MuPDF 1.27)
MuPDF 1.27 `pdf_set_annot_rect(ctx, annot, rect)` takes a rect in **page space** and internally applies the page→PDF transform before writing `/Rect`.

Our previous update path did:
- page-pixel → page → **PDF** conversion in `pp_pdf_update_annot_rect_by_object_id_impl(...)`
- then called `pdf_set_annot_rect(...)`, which applied page→PDF again

That double-transform mirrored Y and inverted move/resize commits.

Fix implemented:
- `platform/common/pp_core.c`: for `PP_MUPDF_API_NEW`, `pp_pdf_update_annot_rect_by_object_id_impl(...)` now passes a **page-space** rect into `pdf_set_annot_rect` (no pre-conversion to PDF).

### Secondary hypothesis (still possible): “two pixel spaces” mismatch (render vs annotation geometry)
Even with native transforms fixed, we still need to ensure we use one canonical pixel basis end-to-end:
- Render requests use the *current view area* (`sizeX/sizeY`).
- Annotation list/update JNI paths currently use `pc->width/height`.
If those differ after zoom/patch rendering, selection boxes can still drift; the fix would be to unify the basis or scale rects consistently in Java.

## Ownership model (ONE OWNER)
Text-annotation interaction is a document-scoped state machine owned by a single controller, not by views:

- **Owner:** `ReaderGestureController` + `AnnotationHitHelper` + `TextAnnotationController` + a dedicated “text manipulation” handler (move/resize).
- **Views:** `MuPDFPageView` / `PageOverlayView` only render (selection box, handles), and provide a small surface to commit rect/style changes into the backend.

Concrete rules:
- `MuPDFPageView` must not keep one-off “pending move” flags (`pendingTextAnnotationMove` etc.). Those belong in the gesture handler.
- Selection is keyed by a stable identifier:
  - Embedded PDF: `Annotation.objectNumber` (not array index)
  - Sidecar: `noteId` / `textBoxId` (future; see parity section)

## Decisions to lock now (UX)
1) **Select vs edit:** first tap selects; second tap edits (within a window). This already exists in `AnnotationHitHelper`.
2) **Move gesture:** must require an intentional action so normal panning never turns into moving.
   - Default (current): **long-press + release to arm**, then drag to move (system long-press timeout).
   - Next UX improvement: add a **dedicated move handle** so moving doesn’t require long-press.
   - Defer “long-press + drag to move” unless user feedback demands it; if implemented later, keep arm+drag as an automation/accessibility fallback.
3) **Resize gesture:** drag corner handles (no long press needed).
4) **Pan/scroll after zoom:** always allowed with one finger unless actively dragging a handle or in long-press move mode.

## Implementation slices (small, build+smoke after each)

### Slice 0 — Stabilize reproduction + logging (no behavior change)
[x] Add a dedicated Genymotion script for PDF FreeText interaction (or fix the current WIP one):
    - open PDF fixture
    - add FreeText
    - tap select (verify bbox visible)
    - tap again edit (verify contents changed)
    - pinch zoom in, then one-finger pan (verify viewport changed, no crash)
    - long-press move, drag, release (verify bbox + text moved)
    - resize via handle, release (verify bbox resized and text stays inside)
    - fail on fatal logcat signals
[x] Extend the script to cover handle-resize assertions.
[x] Add a dedicated pinch-zoom + one-finger pan smoke (`scripts/geny_pinch_zoom_smoke.sh`).
[x] Add “pan after zoom while a text box is selected” coverage to `scripts/geny_pdf_text_annot_smoke.sh`.
[ ] (Optional; defer unless drift returns) Add a debug-only rect dump for FreeText selection:
    - On select: log `objectNumber`, page index, reported rect, `PageView.getScale()`, and the current view-area size used for rendering.
    - On commit move/resize: log old/new rect and success/failure.
    - If cheap in JNI: log `/Rect`, `annot->rect`, `annot->pagerect`, and returned `rect_pix` for the selected `objectNumber` to catch stale-cache/drift.

Success criteria: scripted smokes reliably detect regressions; add rect-dump logs only if drift returns.

#### Update (2025-12-29): pinch-zoom + pan smoke is PASS (Genymotion)
- `scripts/geny_pinch_zoom_smoke.sh` now includes a screenshot-diff assertion that **one-finger pan changes the viewport** after progressive pinch zoom.
- Update: this smoke is **PASS** on Genymotion now.
  - Command: `DEVICE=localhost:35329 OUT_PREFIX=tmp_geny_pinch_zoom_verify /mnt/subtitled/repos/penandpdf/scripts/geny_pinch_zoom_smoke.sh`
  - Result: `changed_ratio=0.0576` and no crash.
  - Important gotcha: `scripts/geny_pinch_zoom_smoke.sh` installs a prebuilt APK from `/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk` and does **not** run Gradle.

### Slice 1 — Fix FreeText bounds source-of-truth (native)
Goal: the rect returned by `MuPDFCore_getAnnotationsInternal` matches what is rendered.

[x] Rect update: `platform/common/pp_core.c` `pp_pdf_update_annot_rect_by_object_id_impl(...)` now passes a **page-space** rect into `pdf_set_annot_rect` (MuPDF 1.27 semantics).
[x] Validate list/update rect semantics end-to-end via smoke:
    - `scripts/geny_pdf_text_annot_smoke.sh` PASS (select/move/resize encloses the rendered text before/after reload).
[ ] (Optional; defer unless drift returns) Add a temporary debug log in `pp_pdf_list_annots_impl` for FreeText:
    - `/Rect` from the annot dict
    - `annot->rect`
    - `annot->pagerect`
    - the returned `rect_pix`
[ ] (Optional; defer unless drift returns) Audit whether FreeText list/update rect conversion is using the same size basis as rendering:
    - Render uses view-area `sizeX/sizeY`; list/update currently use `pc->width/height`.
    - Either unify the bases, or explicitly convert between them in Java before drawing/hit-testing.
[ ] (Optional; defer unless drift returns) Fix any stale-cache cases:
    - Ensure `/Rect` updates keep `annot->rect` + `annot->pagerect` synchronized on MuPDF 1.8 (this is the most likely drift source).
    - Ensure `pdf_update_annot` / `pdf_update_page` ordering is correct for FreeText.

Success criteria:
- After creating or editing FreeText, selection box encloses the text on the next annotation reload.
- Hit-testing a tap on the text actually selects that annotation (not a “phantom” rect elsewhere).

### Slice 2 — Gesture policy: restore pan-after-zoom + intentional move/resize
Goal: zoom + one-finger pan always works; move/resize only when intentional.

[x] Update regression test and repro notes:
    - `scripts/geny_pinch_zoom_smoke.sh` PASS on Genymotion (see “Slice 0” update above; `changed_ratio=0.0576`).
    - Important gotcha: this smoke installs a prebuilt APK from `/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk` and does **not** run Gradle.

[x] Add “pan after zoom while a text box is selected” smoke:
    - Implemented as step **[9.8/14]** in `scripts/geny_pdf_text_annot_smoke.sh`.
    - Runs a pinch-out-only UIAutomator test (`ZoomPinchTest#testPinchOutOnlyDoesNotCrash`) and then performs a one-finger swipe.
    - Assertion is **log-based** (stable): requires `GestureRouter: onScroll` with `scrollDisabled=false`, and forbids `TextAnnotGesture: start MOVE`
      (move must only activate after long-press arm).

[ ] (Optional; defer unless it regresses) Identify the single “snap-back owner” (the thing undoing the pan):
    - Candidates observed during debugging:
      - `ReaderView.slideViewOntoScreenBridge(..., 400)` correction animation
      - `GestureRouter.onFling(...)` (strong fling velocity after the pan swipe)
      - `LayoutSwitchHelper` “settle correction” clamping after interaction
    - Add temporary DEBUG-gated logs/counters to prove which one runs after the one-finger pan gesture, then
      delete/throttle once fixed.

[x] Change `TextAnnotationManipulationGestureHandler`:
    - RESIZE starts only when a handle is hit.
    - MOVE starts only when selection is active AND the user has intentionally armed move
      (currently: long-press + release inside the selection box, then drag within an arm window).
    - Otherwise return `false` so `MuPDFReaderView` pans normally.
[x] Remove legacy “tap-to-move” plumbing owned by `MuPDFPageView` (`pendingTextAnnotationMove*`) once the new flow is stable.
    - Deleted the unused pending-move fields + one-shot move-on-tap path so move/resize is owned solely by `TextAnnotationManipulationGestureHandler`.
    - Commit: `99b13aeb`.

Success criteria:
- After pinch zoom, one-finger drag pans the page (even with text selected).
- Dragging a handle resizes and does not pan.
- Long-press then drag moves and does not pan.

### Slice 3 — Re-edit + crash fixes (embedded FreeText)
Goal: selecting and editing never crashes; re-edit is always reachable.

[x] Verify the select→edit path:
    - `AnnotationHitHelper` second-tap → `MuPDFPageView.forwardTextAnnotation()` → `TextAnnotationController`.
[x] Ensure editing uses stable IDs (embedded FreeText uses `objectNumber`):
    - Update contents by `objectNumber` for embedded FreeText.
[x] Ensure edits always become visible immediately after Save:
    - Force a full redraw + reload after FreeText edits (`MuPDFPageView.updateTextAnnotationContentsByObjectNumber(...)` calls `requestFullRedrawAfterNextAnnotationLoad()` and reloads annotations).
[x] Fix crash-by-stale-selection:
    - On annotation reload, selection is re-resolved by `objectNumber` in `MuPDFPageView.onAnnotationsLoaded(...)` so selection/edit/move never targets a different annotation after refresh.

Success criteria:
- Tap select, tap again edit, Save: text updates and is visible.
- Re-tap after idle: still editable.
- No crash (logcat clean).

### Slice 4 — Style & box controls (minimum viable)
Goal: user can adjust text color + size and see it immediately.

[x] Expose a small “Text style” UI when a text annotation is selected:
    - Overflow “Style” opens a dialog with a font-size slider + palette color and applies to the selected FreeText.
    - Backed by `TextStylePreferencesService` (separate from pen thickness so the slider maps to actual FreeText font size).
[x] Native style update preserves rect:
    - `platform/common/pp_core.c`: `pp_pdf_update_freetext_style_by_object_id_impl(...)` updates default appearance + border width without touching `/Rect`, then runs `pdf_update_annot` + `pdf_update_page`.

Success criteria:
- Changing size/color updates the selected text without moving it or changing its box unexpectedly.

### Slice 5 — Sidecar parity (EPUB / read-only PDFs)
Goal: same UX + interaction model, but backed by sidecar store.

[x] Sidecar text boxes: reuse the existing `SidecarNote` rows (stable `id`, `bounds`, `text`) and extend with per-note style:
    - `color` + `font_size` persisted in `notes` (SQLite `sidecar_annotations.db` schema v5).
[x] Render sidecar text boxes on the overlay (marker + visible text) using stored style.
[x] Selection + gesture parity:
    - Sidecar selection box uses the note bounds (not just the marker rect).
    - Corner handles render for sidecar-selected notes.
    - `TextAnnotationManipulationGestureHandler` now supports move/resize for sidecar notes via `MuPDFPageView.commitSidecarNoteBounds(...)`.
[x] Re-edit + style parity:
    - Sidecar note edits update in-place (stable id) via `MuPDFPageView.updateSelectedSidecarNoteText(...)`.
    - “Text style” dialog applies to sidecar notes via `MuPDFPageView.applyTextStyleToSelectedTextAnnotation(...)`.
[x] Export includes text:
    - Flatten exporter composites the same overlay renderer, so sidecar note text/style is included.
    - Sidecar bundle JSON includes optional `color`/`fontSize` fields; export smoke handles the note-text dialog.

Success criteria:
- EPUB: add visible text box, select, edit, move/resize, reopen, still there.

## Testing and validation (always run per slice)
- Local build: `cd /mnt/subtitled/repos/penandpdf/platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint`
- Smokes:
  - `/mnt/subtitled/repos/penandpdf/scripts/geny_smoke.sh`
  - `/mnt/subtitled/repos/penandpdf/scripts/geny_epub_smoke.sh`
  - `/mnt/subtitled/repos/penandpdf/scripts/geny_pdf_text_annot_smoke.sh` (deterministic PASS on Genymotion)
  - `/mnt/subtitled/repos/penandpdf/scripts/geny_pinch_zoom_smoke.sh` (regression: pan-after-zoom must persist)
- Crash gating:
  - smoke fails on fatal signal / FATAL EXCEPTION in logcat

## Risks / edge cases
- MuPDF 1.27 coordinate semantics: page↔PDF matrix direction and `pdf_bound_annot` return space must be handled consistently, or selection/move will drift and invert.
- Selection identity: index-based selection breaks after reload; must key selection by `objectNumber`.
- OCR flakiness: use OCR only as a supplement; primary checks should be “state + expected redraws + non-blank render”.
- Gesture conflicts: if move starts too easily, it will break panning; long-press + handle-only is the safest default.

## Decisions (resolved defaults)
These were the remaining “product” choices after the engineering work landed. They’re now resolved
as follows (and the codebase is already aligned with these defaults):

### Decisions (defaults, 2025-12-30)
1) **Move arming gesture + timing**
   - Default: **long-press + release to arm move**, then drag to move (the “armed move” window).
   - Timing: use the **system long-press timeout** (`ViewConfiguration.getLongPressTimeout()`), not a hard-coded
     250ms/350ms.
   - Why (UX + architecture): keeps **one-finger pan-after-zoom reliable** (no accidental moves during reading),
     respects platform accessibility settings, and preserves the current deterministic automation path in
     `scripts/geny_pdf_text_annot_smoke.sh` (which long-presses to arm move).

2) **Resize minimum size policy**
   - Default: keep the current **screen-space min-edge clamp** (24dp) and fixed-size handles (16dp squares),
     enforced in `TextAnnotationManipulationGestureHandler#clampAndNormalize(...)` via `ItemSelectionHandles.minEdgePx(...)`.
   - Why: tiny boxes become un-selectable/un-resizable (handles overlap and touch targets collapse). The dp clamp
     keeps interaction usable at any zoom and avoids doc-space-dependent “can’t resize because you zoomed” behavior.

3) **Sidecar docs: “note marker” vs “text box”**
   - Default: treat sidecar notes as **visible text boxes** (with a small marker) for parity with PDF FreeText and
     immediate feedback (“I typed text, I can see it”).
   - Why: a marker-only model makes “did it save?” ambiguous and pushes UX into popovers/menus. The current model
     keeps the interaction pipeline unified (same selection/move/resize/re-edit owner) and keeps export stable
     (flatten export includes what the user sees).

4) **UI copy: move/resize instruction text**
   - Current code (as of `master`): move requires **long-press + release to arm**, then drag (or drag a corner handle
     to resize). However, the help copy `R.string.tap_to_move_annotation` still says “Drag to move…”.
   - Default plan:
     - Short-term: update the copy to reflect reality (“Long-press then drag to move. Drag corners to resize.”).
     - Long-term: once we add a dedicated move handle, we can restore simpler “Drag the move handle to move…” copy.

### Optional refinements (defer; keep ONE OWNER)
If we improve UX further, keep the same ownership boundaries (no new view/activity-owned flags; no duplicated
gesture state):

- **Add a dedicated “move handle”** so users can move without long-press, while keeping long-press arm as the
  safe/automation fallback.
  - Implementation fit (current architecture): extend `ItemSelectionHandles` with a new handle type (e.g.
    `MOVE_TOP_CENTER`) and draw it in `ItemSelectionRenderer` at a fixed dp size; `TextAnnotationManipulationGestureHandler`
    should start MOVE immediately when a drag begins on that handle (no “arming” required).
  - Why this is likely the next best slice: user feedback dislikes the long-press arm UX, and the current edit-menu
    “Move” help copy (`R.string.tap_to_move_annotation`) says “Drag to move…”, while the actual behavior is “drag pans
    unless move is armed”. A visible move handle lets the copy be literally true again (drag the move handle to move),
    without breaking pan-after-zoom.
  - UX win: eliminates the “long-press feels weird” feedback while preserving one-finger panning everywhere else.
  - Test win: scripted move becomes deterministic without time-based long-press sensitivity (still keep the arm path as fallback).

- **Re-edit timing (tap-to-edit window)** — consider removing the strict “second tap within N ms” rule.
  - Current behavior: `AnnotationHitHelper` treats “second tap” as edit only within `TEXT_DOUBLE_TAP_WINDOW_MS`.
  - Proposed UX refinement: once a text annotation is selected, a later tap on that same selected annotation should open
    the editor regardless of time (still keep the explicit “Edit” toolbar action).
  - Why: matches user expectation (“I can come back later and tap-to-edit”), avoids needing users to discover the menu,
    and keeps ONE OWNER (the decision lives in `AnnotationHitHelper`, not ad-hoc UI flags).
  - If we keep a time window for “double tap” at all, prefer the platform value (`ViewConfiguration.getDoubleTapTimeout()`)
    over a hard-coded constant, for accessibility consistency.

- **Collapsed note presentation** for sidecar docs (marker-only / marker+snippet) as a *presentation toggle* on the
  same underlying note type (avoid introducing a second “text system”).
  - Critical semantic rule: collapse is **on-screen presentation only**. Export must still include the full note text.
    - Concretely: `FlattenedPdfExporter` should always render the “expanded” note view regardless of the UI toggle.
  - Suggested UX: global toggle in overflow (“Show note contents”) + per-note expansion when selected.
  - Ownership: preference lives in `EditorPreferences` (or a dedicated view-model/controller), and only the overlay renderer reads it.

- **Text readability options** (prefer halo/outline over background fill) defaulting to “no fill”.
  - Sidecar path: implement halo/shadow at render time (e.g., `TextPaint.setShadowLayer(...)`) so the text remains legible
    on photos/scans without obscuring the page.
  - Embedded PDF path: keep FreeText background transparent by default; add an optional “outline/halo” style only if we can
    do it without fighting MuPDF appearance streams.

- **Sidecar note render performance (avoid per-frame `StaticLayout` churn)**.
  - Current behavior: `SidecarAnnotationRenderer` builds a new `StaticLayout` per note, per draw.
  - Proposed refinement: render note text in doc-space (unscaled), then `canvas.scale(scale, scale)` during drawing so the
    layout is stable across zoom, and cache `StaticLayout` by `(noteId, bounds.width, fontSize, text)` until invalidated.
  - Why: reduces GC + jank when panning/zooming on pages with many notes, without changing persistence or gesture owners.
