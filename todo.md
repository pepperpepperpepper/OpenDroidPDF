OpenDroidPDF – TODO / Status Report (2025-12-23)
================================================

Repo
- Path: `/mnt/subtitled/repos/penandpdf`
- Branch: `master`
- Pushed HEAD: `740970ad` (docs for last slice)
- Last code slice: `d5d6442e` (MuPDF reflow-location viewport restore + smokes device autodetect)

Right now (what’s happening)
----------------------------
- The repo is currently dirty because I’m tightening `scripts/geny_epub_viewport_restore_smoke.sh` so it *proves*
  we restore EPUB viewports via MuPDF’s reflow `fz_location` (not via weaker fallbacks).
- The stricter smoke currently FAILS (details below). The likely fix is an app-side change:
  **for EPUB, prefer `reflowLocation` restore whenever present**, not only under “layout mismatch”.

What’s shipped (pushed to origin/master)
----------------------------------------
1) EPUB viewport restore across relayout is more stable:
   - We now persist MuPDF’s stable reflow location (`fz_location` = chapter/page) into viewport snapshots and use it to restore when the EPUB reflow `layoutProfileId` mismatches.
   - Implementation details:
     - JNI: `/mnt/subtitled/repos/penandpdf/platform/android/jni/document_io.c`
       - `MuPDFCore_locationFromPageNumberInternal(int pageNumber)` → encoded `(chapter<<32)|(page&0xffffffff)`
       - `MuPDFCore_pageNumberFromLocationInternal(long encodedLocation)`
     - Java API: `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/MuPDFCore.java`
       - `locationFromPageNumber(int)` / `pageNumberFromLocation(long)`
     - Repository wrapper: `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/core/MuPdfRepository.java`
     - Persisted in recents viewport snapshot:
       - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/services/recent/ViewportSnapshot.java` (`reflowLocation`)
       - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/services/recent/SharedPreferencesRecentFilesStore.java`
     - Used at restore/save time:
       - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/document/DocumentViewportController.java`
       - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/document/DocumentViewDelegate.java`
       - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/reflow/ReflowSettingsController.java`

2) Genymotion smoke scripts are less brittle about ADB device serials:
   - Many `scripts/geny_*.sh` now use:
     - `DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"`
   - `scripts/geny_uia.sh` will also auto-pick a connected device if none is provided.

Verification results (last pushed slice)
---------------------------------------
Build (PASS):
- `cd /mnt/subtitled/repos/penandpdf/platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint`

Genymotion smokes (PASS):
- `/mnt/subtitled/repos/penandpdf/scripts/geny_smoke.sh`
- `/mnt/subtitled/repos/penandpdf/scripts/geny_epub_smoke.sh`
- `/mnt/subtitled/repos/penandpdf/scripts/geny_epub_viewport_restore_smoke.sh`
- `/mnt/subtitled/repos/penandpdf/scripts/geny_epub_edge_relayout_smoke.sh`

Progress bookkeeping (already updated + pushed)
-----------------------------------------------
- Plan progress entry:
  - `/mnt/subtitled/repos/penandpdf/plan.md` (Recent progress → 2025-12-23 entry; commit `d5d6442e`)
- Smoke log:
  - `/mnt/subtitled/repos/penandpdf/docs/housekeeping/baseline_smoke.md` (Update – 2025-12-23; commit `d5d6442e`)

What’s happening right now (local, not committed)
-------------------------------------------------
Working tree is NOT clean:
- Modified: `/mnt/subtitled/repos/penandpdf/scripts/geny_epub_viewport_restore_smoke.sh`

Goal of the in-progress change:
- Make the viewport-restore smoke *prove* we restore via `reflowLocation` (not the weaker page-index/docProgress fallback).

What I tried:
- After navigating via TOC and saving viewport, mutate the stored viewport snapshot in
  `shared_prefs/OpenDroidPDF.xml` to force:
  - `layoutProfileId{docId} = "bogus_layout_for_smoke"` (guarantee mismatch)
  - `docprogress{docId} = -1.0` and `page{docId} = 0` (remove fallbacks)
  - Keep `reflowLocation{docId}` intact so restore *must* use location.
- Then relaunch and assert the page indicator is still `2/2`.

Current result:
- The stricter smoke currently fails: it relaunches to `1/2` instead of `2/2`.
- This suggests: in that forced scenario the app is *not* successfully restoring from `reflowLocation`.

Likely causes (hypotheses)
--------------------------
1) Restore timing / missing “active layout” at restore time:
   - `DocumentViewportController.restoreViewport()` only tries `reflowLocation` inside the “layout mismatch” branch.
   - If `currentReflowLayoutProfileIdOrNull()` returns null early on startup, we never enter the mismatch branch,
     so we fall through to `ViewportHelper.applySnapshot(...)` (page=0), which explains the `1/2` relaunch.

2) Layout profile id is computed later than viewport restore:
   - The adapter/view may not have a layout profile id ready when restore runs.
   - If so, the “mismatch guard” can’t run, which again prevents the location-based restore path.

Top TODOs (next slice candidates)
--------------------------------
1) Make `reflowLocation` restore unconditional for EPUB (or at least not gated on “layout mismatch”):
   - Update `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/document/DocumentViewportController.java`
     to attempt:
     - if docType == EPUB and snapshot.reflowLocation != -1 → `page = repo.pageNumberFromLocation(loc)` → `doc.setDisplayedViewIndex(page)`
     - then fallback to docProgress/page snapshot.
   - Keep it safe: only apply if `pageFromLoc >= 0`.

2) Ensure the adapter-recreate restore also prefers location:
   - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/document/DocumentViewDelegate.java`
     already prefers `reflowLocation`, but confirm it runs in all relayout paths and is not bypassed.

3) Fix the viewport smoke so it reliably exercises the location restore path:
   - Finish the “forced mismatch + cleared fallback” test in:
     - `/mnt/subtitled/repos/penandpdf/scripts/geny_epub_viewport_restore_smoke.sh`
   - Once the app restore is corrected, this smoke should go back to PASS and becomes a real regression guard.

4) Keep the slice loop discipline:
   - After implementing the app fix:
     - Run build + relevant smokes
     - Update `/mnt/subtitled/repos/penandpdf/plan.md` + `/mnt/subtitled/repos/penandpdf/docs/housekeeping/baseline_smoke.md`
     - Commit code, commit docs, push

Longer-term (plan.md E5 still pending)
--------------------------------------
- EPUB highlights are only “v1 text anchors” (quote + prefix/suffix re-derivation). True DOM-range/CFI anchoring
  is still not implemented (explicitly called out in `/mnt/subtitled/repos/penandpdf/plan.md`).

Next slice (proposed, small + shippable)
----------------------------------------
Goal: “EPUB reflowLocation restore is unconditional when present.”

Changes:
- Prefer `ViewportSnapshot.reflowLocation()` for EPUB restores even when `activeLayoutProfileId` is null early in startup.
- Keep existing safeguards:
  - only apply location restore if the computed page index is valid (>= 0)
  - fall back to docProgress/page snapshot when location decode fails

Acceptance criteria:
- `./scripts/geny_epub_viewport_restore_smoke.sh` PASS with forced pref mutations:
  - `layoutProfileId{docId}` set to bogus
  - `docprogress{docId}` unset/invalid
  - `page{docId}` forced to 0
  - `reflowLocation{docId}` kept intact
- Baseline smokes still PASS:
  - `./scripts/geny_smoke.sh`
  - `./scripts/geny_epub_smoke.sh`
- Build still PASS:
  - `cd /mnt/subtitled/repos/penandpdf/platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint`

Files likely involved:
- `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/document/DocumentViewportController.java`
- (maybe) `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/document/DocumentViewDelegate.java`
- `/mnt/subtitled/repos/penandpdf/scripts/geny_epub_viewport_restore_smoke.sh`
