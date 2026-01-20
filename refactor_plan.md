# Refactor Plan (Phase 3): De-monolith Text Annotation UI + Controllers

This file is the **current** forward-looking refactor plan.

Archives:
- `refactor_plan_2026-01-15.md` (Phase 1 vocabulary + controller naming notes)

## Current status (as of 2026-01-20)
- Phase 2 goal (**no in-tree Android Java file >800 LOC**) is complete.
- Remaining “largest” app-side files are now in the ~600–800 LOC range, mainly in `app/annotation`.
- Phase 3 targets (A–D below) are now split into 3 files each and compile cleanly (`compileDebugJavaWithJavac`).
- `platform/android/src/org/opendroidpdf/app/drawing/InkController.java` is now split into 3 files and compiles cleanly (2026-01-20).
- `lintDebug` and `lintRelease` pass locally (2026-01-20).
- `compileDebugAndroidTestSources` passes locally (2026-01-20).
- `testDebugUnitTest` passes locally (2026-01-20).
- `assembleDebug` passes locally (2026-01-20).

## Goals
- Reduce “monolith” file sizes so changes are safer and reviewable.
- Keep **single ownership**: each end-to-end workflow has a single controller/owner that owns invariants and side effects.
- Avoid behavior changes during the split (pure structure refactor first).

## Scope (Phase 3)
- Focus: `platform/android/src/org/opendroidpdf/app/**` (feature controllers + UI glue).
- Exclude: `platform/android/src/org/opendroidpdf/*View*` / `MuPDF*` view-layer files unless they block progress (those are upstream-ish and high-risk to restructure).

## Repeatable split rule
Each target becomes **3 files total**:
1) **Orchestrator**: keeps the existing public class name to avoid cascading call-site edits.
2) **Ops/Service**: pure-ish logic + repository/session calls (no dialogs, minimal Android deps).
3) **UI/Model**: dialog/view construction or state/helpers (no side effects).

### Size targets
- Hard cap: keep each file **< 800 LOC**.
- Soft target: extracted modules **~250–600 LOC** (avoid lots of tiny files).

### Safety constraints
- No package moves on the first pass (same package, package-private helpers).
- No renames on the first pass (rename `*Ops` → `*Controller` later, after stable).
- After each extraction (fast): `./platform/android/gradlew -p platform/android compileDebugJavaWithJavac`
- Before merging (CI parity): `./platform/android/gradlew -p platform/android lintDebug` (and `lintRelease` if CI runs release lint)

---

## Phase 2 summary (completed splits)
- `platform/android/src/org/opendroidpdf/OpenDroidPDFCore.java` → extracted open/save helpers:
  - `platform/android/src/org/opendroidpdf/OpenDroidPDFOpenOps.java`
  - `platform/android/src/org/opendroidpdf/OpenDroidPDFSaveOps.java`
- `platform/android/src/org/opendroidpdf/ReaderView.java` → extracted gesture + layout helpers:
  - `platform/android/src/org/opendroidpdf/ReaderViewGestureController.java`
  - `platform/android/src/org/opendroidpdf/ReaderViewLayoutEngine.java`
- `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationEmbeddedFreeTextOps.java` → extracted repository + undo helpers:
  - `platform/android/src/org/opendroidpdf/app/annotation/EmbeddedFreeTextRepositoryOps.java`
  - `platform/android/src/org/opendroidpdf/app/annotation/EmbeddedFreeTextUndo.java`
- `platform/android/src/org/opendroidpdf/app/document/OrganizePagesController.java` → extracted UI + ops:
  - `platform/android/src/org/opendroidpdf/app/document/OrganizePagesUi.java`
  - `platform/android/src/org/opendroidpdf/app/document/OrganizePagesOps.java`
- `platform/android/src/org/opendroidpdf/app/document/ExportController.java` → extracted UI + ops:
  - `platform/android/src/org/opendroidpdf/app/document/ExportUi.java`
  - `platform/android/src/org/opendroidpdf/app/document/ExportOps.java`
- `platform/android/src/org/opendroidpdf/app/sidecar/SidecarAnnotationSession.java` → extracted undo helpers:
  - `platform/android/src/org/opendroidpdf/app/sidecar/SidecarAnnotationUndo.java`
  - `platform/android/src/org/opendroidpdf/app/sidecar/SidecarAnnotationUndoOps.java`

---

## Phase 3 targets (annotation package)

Targets are listed with approximate LOC as of 2026-01-20.

### A) `TextAnnotationStyleUi.java` (~736)
Problem: one file builds and wires a very large “text style” dialog (state + widgets + swatches + persistence).

Completed 3-file split (current LOC):
1) `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationStyleUi.java` (15)
2) `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationStyleDialogBinder.java` (647)
3) `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationStyleSwatches.java` (114)

Acceptance:
- ✅ `TextAnnotationStyleUi.java` shrank substantially.
- ☐ Manual smoke: dialog still updates the same prefs + selected annotations.
  - Suggested: `scripts/geny_pdf_text_annot_smoke.sh` (broad coverage) and `scripts/geny_pdf_text_annot_background_smoke.sh` (style fields).

### B) `TextAnnotationPageDelegate.java` (~671)
Problem: mixes embedded markup creation (async), clipboard/selection routing, and embedded-vs-sidecar branching.

Completed 3-file split (current LOC):
1) `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationPageDelegate.java` (353)
2) `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationPageEmbeddedMarkupOps.java` (217)
3) `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationPageClipboardAndSelection.java` (212)

Acceptance:
- ✅ `TextAnnotationPageDelegate.java` <500 LOC.
- ☐ Manual smoke: markup add/delete/copy/paste still works for both embedded and sidecar modes.
  - Suggested: `scripts/geny_pdf_text_annot_smoke.sh` (includes markup actions).

### C) `TextAnnotationMultiSelectController.java` (~608)
Problem: mixes UI prompt strings/dialogs and geometry ops (align/distribute) in one file.

Completed 3-file split (current LOC):
1) `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationMultiSelectController.java` (496)
2) `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationMultiSelectUi.java` (55)
3) `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationMultiSelectGeometry.java` (122)

Acceptance:
- ✅ Controller shrank and is <800 LOC.
- ☐ Manual smoke: align/distribute works and continues to skip locked items.
  - Suggested: `scripts/geny_pdf_text_annot_multiselect_smoke.sh`.

### D) `TextAnnotationSidecarNoteOps.java` (~600)
Problem: mixes sidecar session mutations, auto-fit bounds math, and UI glue (toasts + selection refresh).

Completed 3-file split (current LOC):
1) `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationSidecarNoteOps.java` (489)
2) `platform/android/src/org/opendroidpdf/app/annotation/SidecarNoteRepositoryOps.java` (233)
3) `platform/android/src/org/opendroidpdf/app/annotation/SidecarNoteAutoFit.java` (49)

Acceptance:
- ✅ `TextAnnotationSidecarNoteOps.java` is <800 LOC and auto-fit logic is extracted.
- ☐ Manual smoke: edit sidecar note text still auto-fits until user-resize; locks still enforced.
  - Suggested: `scripts/geny_pdf_text_annot_autofit_smoke.sh`.

---

## Deferred (non-annotation)
None currently.

## Completed (non-annotation)
- `platform/android/src/org/opendroidpdf/app/drawing/InkController.java` → extracted commit + existing-ink erase logic:
  - `platform/android/src/org/opendroidpdf/app/drawing/InkCommitOps.java`
  - `platform/android/src/org/opendroidpdf/app/drawing/InkExistingInkEraser.java`
- `platform/android/src/org/opendroidpdf/app/comments/CommentsListController.java` → extracted UI + ops:
  - `platform/android/src/org/opendroidpdf/app/comments/CommentsListUi.java`
  - `platform/android/src/org/opendroidpdf/app/comments/CommentsListOps.java`
