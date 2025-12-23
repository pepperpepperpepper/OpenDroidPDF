OpenDroidPDF – TODO / Status Report (2025-12-23)
================================================

Repo
- Path: `/mnt/subtitled/repos/penandpdf`
- Branch: `master`
- Key shipped commits to reference:
  - `a898ad6d` (EPUB: restore viewport via reflowLocation; harden smoke)
  - `9cfca1fd` (docs: record the slice in `plan.md` + `docs/housekeeping/baseline_smoke.md`)

Right now (what’s happening)
----------------------------
- Repo is clean and fully pushed; the viewport-restore hardening slice is shipped.

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

What just changed (shipped)
---------------------------
Goal (now DONE):
- Make EPUB restore use `ViewportSnapshot.reflowLocation()` even on cold start when the active reflow layout profile id
  may not be available yet (so we don’t fall back to stale page indices).

Implementation:
- `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/document/DocumentViewportController.java`
  now prefers `reflowLocation` whenever present:
  - if reflow location decodes to a valid page, and either:
    - a known layout mismatch exists, or
    - pagination changed (location→page differs from snapshot.page),
    then we restore by page-from-location and skip full snapshot.
  - otherwise we keep full snapshot restore but still normalize the page to the location-derived page.
- `/mnt/subtitled/repos/penandpdf/scripts/geny_epub_viewport_restore_smoke.sh` is hardened:
  - mutates `shared_prefs/OpenDroidPDF.xml` to clear `docprogress`/`page` and force bogus `layoutProfileId`,
    so the restore path must use `reflowLocation`.

Result:
- The strict viewport restore smoke now PASSes again.

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
1) Continue plan.md E5 work:
   - True DOM-range/CFI-style anchors for EPUB highlights (current is TextQuoteSelector).

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
