# TODO.md — OpenDroidPDF: ink/eraser correctness + performance investigation

This file is the running “what we know / what we changed / what’s still unclear” log.
Per request, the previous contents of `TODO.md` were deleted and replaced with this report.

Last updated: 2025-12-18
Repo: `/mnt/subtitled/repos/penandpdf`
Branch: `master` (dirty locally right now; see “Working tree” below)

---

## 0) User-reported problems (current)

### A) Eraser only erases the most recent mark
Observed behavior (your report):
- Draw stroke A.
- Change pen size and/or ink color.
- Draw stroke B.
- Switch to eraser: eraser only affects the last stroke (B); stroke A can’t be erased.

### B) Performance feels worse / slower to load
Observed behavior (your report):
- App feels slow loading documents / generally sluggish.
- We need to avoid regressions from expensive loops, annotation scans, or noisy logging/dumping.

---

## 1) What I reproduced + why the older stroke can become “un-erasable”

### Repro case 1 (already covered previously)
“Two committed strokes; erase both”
- Stroke A: draw → ✓ accept/commit.
- Change pen size/color.
- Stroke B: draw → ✓ accept/commit.
- Switch to eraser; erase A then B.

This case is already automated by `scripts/geny_multi_eraser_smoke.sh` and passes on Genymotion.

### Repro case 2 (NOT covered before; matches the “only last stroke” symptom)
“One committed stroke; one pending stroke; erase both”
- Stroke A: draw → ✓ accept/commit.
- Change pen size/color.
- Stroke B: draw (DO NOT press ✓ accept; leave it pending).
- Switch to eraser.
- Try to erase stroke A → previously failed (0 changed pixels); only stroke B could be affected.

This is a realistic user workflow (“I draw, tweak pen, draw more, erase”) and it explains why earlier automation
could pass while the phone still feels broken.

Root cause (high confidence):
- When switching into eraser mode, if there is pending ink still in the overlay, the eraser operates on that
pending overlay drawing rather than the committed annotations on the page.
- If we don’t commit the pending stroke before erasing, the user perceives “eraser only works on the last mark”.

---

## 2) Other critical correctness hazard discovered: annotation deletion by index

The eraser flow for committed ink works by:
1) Find the ink annotation under the eraser.
2) Load its arcs into the DrawingController to make it editable.
3) Delete the original ink annotation from the PDF.
4) Let the overlay + DrawingController show the edited geometry; commit on erase end.

Previously we deleted by “annotation index”.

Problem:
- Annotation ordering can change between loads (or while async annotation reload is happening).
- “Delete by index” can delete the wrong annotation (especially after pen size/color changes that may change
appearance streams/rects, or after any async reload).
- Deleting the wrong annotation creates extremely confusing symptoms (“I can only erase the newest one”, or
“erasing changes the wrong stroke”, etc.).

Mitigation implemented:
- Delete by a stable identifier: `Annotation.objectNumber` (the PDF object number+generation packed into a `long`).
- JNI implementation scans current annotations and deletes the one matching that object id.
- If `objectNumber` is unavailable (< 0), we fall back to the old “delete by index” behavior.

---

## 3) What I changed (currently uncommitted)

### 3.1 Fix: delete ink by stable object id (reduces “only last stroke” + wrong deletion)
- `platform/android/src/org/opendroidpdf/MuPDFCore.java`
  - Added native binding `deleteAnnotationByObjectNumberInternal(long objectNumber)`.
  - Added Java wrapper `deleteAnnotationByObjectNumber(int page, long objectNumber)`.
- `platform/android/jni/text_annot.c`
  - Implemented `MuPDFCore_deleteAnnotationByObjectNumberInternal`.
  - Walks `pdf_first_annot/pdf_next_annot`, computes `(num<<32)|gen`, deletes match, then updates page.
- `platform/android/src/org/opendroidpdf/core/MuPdfRepository.java`
  - Added `deleteAnnotationByObjectNumber(pageIndex, objectNumber)` façade.
- `platform/android/src/org/opendroidpdf/core/MuPdfController.kt`
  - Added `deleteAnnotationByObjectNumber(pageIndex, objectNumber)` and marks doc dirty.
- `platform/android/src/org/opendroidpdf/MuPDFPageView.java`
  - Uses object-number deletion when available:
    - `muPdfController.deleteAnnotationByObjectNumber(mPageNumber, target.objectNumber)`
    - fallback: `deleteAnnotation(mPageNumber, inkIndex)`

### 3.2 Fix: eraser should auto-commit pending ink when switching to eraser mode
This specifically fixes the “stroke A committed, stroke B pending, eraser can’t erase A” workflow.

- `platform/android/src/org/opendroidpdf/app/annotation/AnnotationToolbarController.java`
  - In `menu_erase` handling:
    - If `pageView.getDrawingSize() > 0` (pending overlay ink exists), call `pageView.saveDraw()` first.
    - This commits the pending stroke before entering eraser mode, so the eraser can operate on committed ink.
    - Also updates stroke count via `host.notifyStrokeCountChanged(...)`.
    - If commit fails, show a user-facing info message.

### 3.3 Performance: throttle heavy annotation scans during erase MOVE
Arc-proximity erasing needs to occasionally scan annotations and compute distances. Doing this every ACTION_MOVE
on a real device can be expensive.

- `platform/android/src/org/opendroidpdf/MuPDFPageView.java`
  - Added `lastEraseInkHitAttemptUptimeMs`.
  - In `continueErase(...)`, only attempt “begin erasing existing ink” at most every ~80ms.
  - Keeps eraser responsive while still “snapping” onto committed ink when you swipe over it.

### 3.4 Performance: gate debug spam / dumps behind DEBUG
The codebase had several “debug only” logs/dumps that can create real overhead (I/O to Downloads, logcat spam).

- `platform/android/src/org/opendroidpdf/core/MuPdfRepository.java`
  - Wrap draw/update debug logs behind `BuildConfig.DEBUG`.
  - Make `maybeDumpOnce(...)` no-op in non-debug builds.
- `platform/android/src/org/opendroidpdf/app/drawing/InkController.java`
  - `LOG_UNDO` is now `BuildConfig.DEBUG` to reduce runtime overhead/log spam in release.

### 3.5 Strings (needed for the new “commit failed” message)
- `platform/android/res/values/strings_editor.xml`
- `platform/android/res/values-de/strings_editor.xml`
- `platform/android/res/values-es/strings_editor.xml`
  - Added `cannot_commit_ink`

### 3.6 New scripted regression to prevent reintroducing the “pending stroke” bug
- `scripts/geny_eraser_autocommit_smoke.sh` (NEW)
  - Draw A (accept), change pen size, draw B (no accept), enter eraser, erase A then B.
  - Screenshot diff validates both regions whiten/change.

---

## 4) Current working tree (important)

Locally-modified (uncommitted) files right now:
- `platform/android/jni/text_annot.c`
- `platform/android/src/org/opendroidpdf/MuPDFCore.java`
- `platform/android/src/org/opendroidpdf/MuPDFPageView.java`
- `platform/android/src/org/opendroidpdf/app/annotation/AnnotationToolbarController.java`
- `platform/android/src/org/opendroidpdf/app/drawing/InkController.java`
- `platform/android/src/org/opendroidpdf/core/MuPdfController.kt`
- `platform/android/src/org/opendroidpdf/core/MuPdfRepository.java`
- `platform/android/res/values/strings_editor.xml`
- `platform/android/res/values-de/strings_editor.xml`
- `platform/android/res/values-es/strings_editor.xml`

New file:
- `scripts/geny_eraser_autocommit_smoke.sh`

Nothing has been committed/pushed yet for this new round.

---

## 5) Tests I ran (Genymotion)

Build:
- `cd platform/android && ./gradlew assembleDebug -x lint` ✅

Unit tests:
- `cd platform/android && ./gradlew testDebugUnitTest` ✅
  - Note: `MenuStateEvaluatorTest` needed updating because `saveEnabled` is now intentionally `true` whenever a
    document is open (Save is no longer gated on “unsaved changes”).

Automated smoke:
- `./scripts/geny_multi_eraser_smoke.sh` ✅ PASS (two committed strokes; erase both)
- `./scripts/geny_eraser_autocommit_smoke.sh` ✅ PASS (committed + pending; eraser auto-commit then erase)

The key thing: the “pending stroke B” scenario used to fail and now passes (on Genymotion).

---

## 6) Remaining uncertainties / “mysteries” to keep in mind

### A) Device-specific behavior vs emulator
Even with Genymotion passing, the Pixel Fold report matters.
Potential differences:
- Different storage permission behavior and “open from” flows (SAF vs file:// vs content://).
- Different refresh timing / annotation reload ordering.
- Performance differences: real hardware may expose O(N annotations) loops much more clearly.

Action:
- Install the newly built APK on the Pixel Fold and repeat the same workflows.
- Capture `adb logcat` around erase begin/end, especially the “begin erase ink … obj=… totalAnnots=…” lines.

### B) Annotation ordering/reload churn
We attempted to stabilize deletion via objectNumber, but ordering can still change.
We need to be careful about:
- When `mAnnotations` is stale vs freshly loaded.
- Starting an erase while an async load is in-flight.

### C) Performance on heavily annotated pages
Our arc-distance scan is O(total points). It’s correct, but we should keep it bounded:
- Throttling helps.
- We may need further optimizations (e.g., bounding-box prefilter, sampling, caching per annotation).

### D) “Eraser should act like a canvas”
Long-term, erasing should:
- Work on any past strokes without special mode gymnastics.
- Not require a “commit first” mental model.

The current approach (load one ink annot → delete → edit → re-add) is workable, but it can still be surprising if
the user expects partial erasing across multiple ink annotations in one swipe. If that’s desired, we’ll need a
multi-annotation edit/merge strategy.

---

## 7) Next steps (recommended)

1) Confirm on Pixel Fold with the new APK:
   - Draw A (accept), change size, draw B (don’t accept), switch to eraser, erase A and B.
   - Draw A/B/C across multiple changes; ensure eraser can hit any of them.

2) If still broken on device:
   - Add a debug-only overlay showing:
     - current mode (Viewing/Drawing/Erasing)
     - pending stroke count
     - total annotation count on page
   - This makes “pending vs committed” immediately visible while testing.

3) If performance still feels bad:
   - Profile where time is spent:
     - JNI drawPage/updatePage
     - annotation loads
     - eraser hit scans (point distance loops)
   - Consider: cache per-annotation coarse bounds and only do arc-distance on nearby candidates.

4) After device confirmation:
   - Commit + push.
   - Bump version + build release + F-Droid deploy.

---

## 8) Useful commands (copy/paste)

Build debug:
- `cd platform/android && ./gradlew assembleDebug -x lint`

Run Genymotion tests:
- `./scripts/geny_multi_eraser_smoke.sh`
- `./scripts/geny_eraser_autocommit_smoke.sh`

Install APK to a device:
- `adb install -r /mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk`

Grab logcat filtered:
- `adb logcat | rg -n "MuPDFPageView|InkController|MuPdfRepository|libmupdf"`
