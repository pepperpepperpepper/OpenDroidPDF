# TODO — Current Refactor + Editor Bugs (OpenDroidPDF)

Date: 2025-12-17  
Branch: `master` (dirty working tree; changes below are **NOT committed yet**)  

This document is the authoritative “what we did / what’s next” checklist for:
- The pen-size + pen-color settings dialog flows
- The eraser behavior (pending ink + committed ink)
- Genymotion scripted smoke tests
- Known pitfalls discovered while debugging

---

## 0) Current Git State (IMPORTANT)

Uncommitted changes right now:
- Modified: `platform/android/jni/text_annot.c`
- Modified: `platform/android/src/org/opendroidpdf/DrawingController.java`
- Modified: `platform/android/src/org/opendroidpdf/MuPDFPageView.java`
- Modified: `platform/android/src/org/opendroidpdf/MuPDFReaderView.java`
- Modified: `platform/android/src/org/opendroidpdf/SelectionUiBridge.java`
- Modified: `platform/android/src/org/opendroidpdf/app/annotation/PenSettingsController.java`
- Modified: `platform/android/src/org/opendroidpdf/app/hosts/ActionBarHostAdapter.java`
- Modified: `platform/android/src/org/opendroidpdf/app/services/DrawingServiceImpl.java`
- Modified: `scripts/auto_draw_smoke.sh`
- Modified: `scripts/geny_pen_settings_smoke.sh`
- Modified: `scripts/geny_smoke.sh`
- New: `scripts/geny_uia.sh`
- New: `scripts/geny_eraser_smoke.sh`

Current status:
- `./gradlew assembleDebug -x lint` succeeds.
- `scripts/geny_pen_settings_smoke.sh` passes (and now verifies “one-tap swatch apply”).
- `scripts/geny_eraser_smoke.sh` passes (pending ink erase + committed ink erase).
- `scripts/geny_smoke.sh` passes.

---

## 1) What’s already fixed (and how we know)

### 1.1 Pen settings no longer delete the previous stroke (core regression)

Symptom (original bug):
- Draw stroke A
- Open pen settings and change size/color
- Stroke A disappears / gets “forgotten”

Status:
- **Fixed for the intended flow** (stroke A remains visible after pen settings change, then stroke B can be drawn).

Evidence:
- `scripts/geny_pen_settings_smoke.sh` passes on Genymotion.
  - It verifies:
    - First stroke adds “ink pixels”
    - Changing size+color does **not** reduce ink pixels significantly (i.e., stroke doesn’t disappear)
    - Second stroke increases ink pixels further

Artifacts:
- Script output: `/tmp/opendroidpdf_pen_settings_smoke/report.txt`
- Screenshots: `/tmp/opendroidpdf_pen_settings_smoke/*.png`

Command:
- `DEVICE=localhost:42865 APK=/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk ./scripts/geny_pen_settings_smoke.sh`

Notes:
- This script assumes the pen settings dialog is reachable from the Annot toolbar and uses a mix of UIA bounds and fallback taps.

---

## 2) User-reported editor issues (fixed locally; pending commit)

### 2.1 “Eraser doesn’t work”

User report:
- Switching to eraser does not erase ink as expected.

Important clarification discovered while debugging:
- There are *two* erase cases:
  1) **Erase pending ink** (still in DrawingController overlay, not committed)
  2) **Erase committed ink annotation** (already saved into PDF as `PDF_ANNOT_INK`)

Status:
- **Fixed** for both cases:
  - pending-ink erase (overlay strokes)
  - committed-ink erase (PDF ink annotations)

What we implemented (uncommitted):

1) **Committed-ink erasing “edit + erase + re-save” path (MuPDFPageView)**
   - File: `platform/android/src/org/opendroidpdf/MuPDFPageView.java`
   - Change:
     - On `startErase(x,y)`:
       - If there’s no pending ink (`getDrawingSize() == 0`) and the tap would hit an ink annotation:
         - Select it
         - `editSelectedAnnotation()` (loads arcs into DrawingController and deletes the original annotation)
         - Switch mode back to Erasing (because edit forces Drawing mode)
         - Mark internal flag `erasingExistingInkAnnotation = true`
      - On `continueErase(x,y)`:
        - Same “begin erasing existing ink” probe, so erasing works even if ACTION_DOWN starts off-stroke and then swipes across it.
     - On `finishErase(x,y)`:
       - If we were erasing an existing ink annotation:
         - If strokes remain, `saveDraw()` to commit the edited ink
         - Else `cancelDraw()` to delete it entirely
   - Also added a *best-effort* refresh of annotations before hit-testing:
     - `mAnnotations = getAnnotations()` to avoid stale `mAnnotations` when ink commits race the async loader

2) **Erase algorithm reliability in DrawingController**
   - File: `platform/android/src/org/opendroidpdf/DrawingController.java`
   - Change:
     - Replaced a fragile intersection-based erase algorithm with a simpler stroke splitting strategy:
       - A point is “erased” if it is within radius `r`
       - Also checks point-to-segment distance for sparse points
       - Breaks the stroke at erased points and prunes segments shorter than 2 points
   - Motivation:
     - Intersection logic tended to no-op and led to “eraser does nothing” complaints.

3) **Fix ink annotation rect coordinates (JNI)**
- File: `platform/android/jni/text_annot.c`
- Change:
  - Stop flipping the annotation bounding rect’s Y axis after `pdf_bound_annot + fz_transform_rect`.
  - Reason: other geometry sources (links, text search boxes) are already in the same top-left “page pixel” coordinate space as touch/docRel; the extra flip made ink annotations un-hittable.

4) **Fix NPE when editing ink during erase**
- File: `platform/android/src/org/opendroidpdf/SelectionUiBridge.java`
- Change:
  - Resolve `MuPDFReaderView` from `pageView.mParent` at call-time in `setModeDrawing()` instead of capturing a possibly-null `readerView` in the constructor.

How we tested:
- Added: `scripts/geny_eraser_smoke.sh`
  - Opens `test_assets/pdf_with_text.pdf`
  - Uses OCR gate (tesseract) to confirm text is rendered before drawing/erasing.
  - Tests:
    - pending-ink erase (no Accept)
    - committed-ink erase (Accept first)
  - PASS on latest run.

---

### 2.2 “Pen color requires two taps”

User report:
- Selecting a color swatch requires two taps (first tap focuses; second tap applies).

What we implemented (uncommitted):
- File: `platform/android/src/org/opendroidpdf/app/annotation/PenSettingsController.java`
- Change:
  - Removed:
    - `swatch.setFocusable(true);`
    - `swatch.setFocusableInTouchMode(true);`
  - Rationale:
    - A focusable view can consume the first tap just to gain focus, especially inside dialogs/grids.
    - Swatches should be “tap once = apply”.

Status:
- **Fixed and validated on Genymotion**:
  - `scripts/geny_pen_settings_smoke.sh` now asserts that the “Blue” swatch becomes `selected=true` after a *single* tap via a post-click UIAutomator dump.

---

## 3) Critical debugging pitfall (handled in scripts; still relevant)

### Toolbar coordinate mismatch (Main menu vs Annot menu)

Observed:
- Depending on current `ActionBarMode`, the same “top-right” coordinates can hit different buttons:
  - In Main mode: can hit `menu_open` (document picker/dashboard)
  - In Annot mode: can hit draw/erase/ink-color/accept

Consequence:
- Some “manual” tap-based scripts can inadvertently trigger “Open”, which swaps the UI to the dashboard (large icons) and makes screenshots look like the PDF disappeared.

Concrete example:
- A UI dump showed that in Main mode, the draw button lives at:
  - `org.opendroidpdf:id/draw_image_button` bounds `[935,44][1002,110]`
  - but a tap around ~`785,77` could hit `menu_open` depending on menu configuration.

Resolution:
- All current Genymotion smoke scripts use `scripts/geny_uia.sh` (UIAutomator dump + resource-id/content-desc/text taps).
- Scripts also gate on “we’re actually in document view” (`uia_assert_in_document_view`) to avoid false positives from the dashboard/launcher.

---

## 4) Test Assets (PDFs) and “Text renders” checks

### 4.1 Built-in test PDF with text
- File: `test_assets/pdf_with_text.pdf`
- Local render proof:
  - `pdftoppm -png test_assets/pdf_with_text.pdf /tmp/pdf_with_text`
  - `/tmp/pdf_with_text-1.png` contains visible text.

### 4.2 Blank PDF
- File: `test_blank.pdf`
- Note:
  - It’s truly blank (content stream length 0). Anything non-white seen on screen is from overlays (debug thumbnails, icons, ink strokes, etc.), not PDF content.

TODO:
- Keep both:
  - `test_blank.pdf` for ink pixel delta tests
  - `test_assets/pdf_with_text.pdf` for render/search/ocr sanity tests

---

## 5) Smoke scripts inventory and TODOs

### 5.1 Existing scripts

- `scripts/geny_pen_settings_smoke.sh`
  - Status: PASS on latest run
  - Validates: pen settings finalize doesn’t delete previous stroke
  - Also validates: “one tap color swatch applies” (checks UIAutomator `selected=true` after tapping Blue)

- `scripts/auto_draw_smoke.sh`
  - Status: updated to use UIAutomator-driven taps; basic draw + screenshot delta works
  - TODO:
    - Decide what “Save” should do in current UX and make export validation reliable (the script is best-effort here).

- `scripts/geny_smoke.sh`
  - Status: UIA-driven; PASS on latest run (open PDF → draw → undo → cancel → search/share best-effort)

### 5.2 New script added (uncommitted)

- `scripts/geny_eraser_smoke.sh`
  - Status: PASS on latest run (pending erase + committed erase), uses OCR gate + UIA taps + document-view gate

---

## 6) Next concrete action list (priority order)

### P0 — Make the eraser and pen-color changes real/reliable

- [x] `scripts/geny_eraser_smoke.sh` uses UIA id-based taps (draw/accept/erase) + document-view gate + OCR render gate
- [x] `scripts/geny_eraser_smoke.sh` covers both pending-ink erase and committed-ink erase
- [x] “pen color one tap” validated in `scripts/geny_pen_settings_smoke.sh`

### P1 — Stabilize automation + remove known broken script issues

- [x] Repair `scripts/auto_draw_smoke.sh` env var bug (`KeyError: 'BEFORE'`) and switch to UIA where possible
- [x] Update `scripts/geny_smoke.sh` to UIA-driven taps
- [ ] Optional: standardize all smokes on `uia_assert_in_document_view` and OCR gates where appropriate

### P2 — Release + deployment (only after user approval)

- [ ] Commit the current fixes (files listed in section 0)
- [ ] Bump versionName/versionCode
- [ ] Build `assembleRelease`, sign, deploy to F-Droid

---

## 7) References / Key Files

Core work in this TODO:
- `platform/android/src/org/opendroidpdf/app/annotation/PenSettingsController.java`
  - Swatch focus removal (one-tap color selection)
- `platform/android/src/org/opendroidpdf/MuPDFPageView.java`
  - Committed ink erase support via “edit ink annotation then erase”
- `platform/android/src/org/opendroidpdf/SelectionUiBridge.java`
  - Fix mode switching during edit when parent is late-bound
- `platform/android/jni/text_annot.c`
  - Fix ink annotation rect coordinate space
- `platform/android/src/org/opendroidpdf/DrawingController.java`
  - More reliable erase algorithm
- `scripts/geny_pen_settings_smoke.sh`
  - Pen-settings regression test (PASS)
- `scripts/geny_eraser_smoke.sh`
  - Eraser regression test (PASS)
- `scripts/geny_uia.sh`
  - UIAutomator helpers used by the smokes
