# Baseline Smoke Coverage – 2025-11-15

## Update – 2025-11-29

### Preference / Notes Namespace Migration
- First launch after installing the OpenDroidPDF rebrand now calls `SettingsActivity.ensurePreferencesNamespace()` before any preference access. The helper copies every entry from the legacy `PenAndPDF.xml` store into the new `OpenDroidPDF.xml` file (without overwriting keys that already exist) and marks the migration via `__opendroidpdf_namespace_migrated__`.
- Verification steps:
  1. Install the new OpenDroidPDF build over an older Pen&PDF-based install (still the canonical test for preference migration).
  2. Launch OpenDroidPDF once so the migration runs (Settings and Recent Files both call the helper).
  3. Check `adb shell run-as org.opendroidpdf ls files/../shared_prefs` and confirm `OpenDroidPDF.xml` exists; `grep` the file (or `run-as` + `cat`) to ensure recently used keys (e.g., `pref_ink_thickness`) migrated with their prior values. The legacy `PenAndPDF.xml` can remain on disk but is no longer read.
  4. (2025-11-29) Confirmed on Genymotion by seeding `PenAndPDF.xml` with `pref_ink_thickness=7.5` and `pref_save_on_destroy=false` before first launch; `OpenDroidPDF.xml` picked up both values after migration.
  5. Migration cleanup now clears the legacy `PenAndPDF.xml` and deletes the old shared_prefs XML once the copy succeeds, so subsequent runs cannot regress back to the legacy namespace.

### Notes Provider Identifier Refresh
- The DocumentsProvider root id is now `OpenDroidPDFNotesProvider`, matching the user-facing branding for "OpenDroidPDF Notes" in the Android file picker.
- Compatibility: `queryRecentDocuments()` still honors requests that arrive with the legacy `PenAndPDFNotesProvider` root id so previously granted SAF permissions continue working. No user interface now exposes the Pen&PDF name; it only appears in migration logs.
- After a successful migration pass the old `PenAndPDFNotes` folder (and the private `context.getDir("notes", ...)` store) are deleted if they’re empty, keeping Storage Access tidy.

### Workspace Cleanup
- `scripts/cleanup_workspace.sh` has dropped the `penandpdf_*` screenshot patterns; only the `opendroidpdf_*`/`geny_*` naming remains. Delete any leftover Pen&PDF-era captures manually if needed before running the script so new evidence stays branded correctly.

### Resource / Pen Palette Normalization
- Dashboard and editor strings are now split into dedicated resource files (`res/values*/strings_dashboard.xml`, `strings_editor.xml`) so localization diffs stay scoped to the relevant feature. Keep new copy there instead of piling more entries into the base `strings.xml`.
- Pen-related dimensions/colors/palette data moved to `values/dimens_editor.xml`, `values/colors_editor.xml`, and `values/arrays_editor.xml`. Slider limits (min/max/step) now come from resources, so QA can tweak defaults without touching Java.
- `OpenDroidPDFApp` (declared in the manifest) exposes global `Resources` access for `ColorPalette`, which now reads from the `pen_palette_colors` array before falling back to the legacy constants. When testing custom palettes, drop an updated array into `values*/arrays_editor.xml`, rebuild, and verify the entries populate in Settings → Ink color.

## Update – 2025-11-28

### Builds
- `./gradlew assembleDebug` (from `platform/android/`) – **PASS**  
  - Output: `app/build/outputs/apk/debug/app-debug.apk`  
  - Notes: Still see the `_LARGEFILE_SOURCE` and `%ld` vs `%lld` warnings from MuPDF’s NDK build; defer cleanup to the native refactor.

### Manual Pen / Size / Export Workflow
- Device: Genymotion Pixel 6 (Android 13) at `localhost:42865`.
- Scenario:
  1. Open `test_blank.pdf`, enter draw mode, lay down a default pen stroke, and accept.
  2. Open the pen size slider, drag to the minimum (auto-saves at `pref_ink_thickness=0.50`), draw/accept a stroke.
  3. Reopen the slider, drag to the maximum (auto-saves at `pref_ink_thickness=11.60`), draw/accept another stroke.
  4. Export via *Save… → Save*, pull `/sdcard/Download/test_blank.pdf`, rasterize at 600 dpi for measurement.
- Results:
  - The exported PDF contains two ink components with identical rasterized thickness (~9 px tall at 600 dpi) despite the slider change.
  - Confirms the UI persists the preference and finalizes in-progress strokes correctly, but the native `addInkAnnotation` path still applies MuPDF’s default stroke width.
- Follow-up:
  - Track this as part of the pen-size robustness work (Phase 1/3); native layer needs to call `pdf_set_annot_border` or similar when committing ink annotations.

Instrumentation smoke remains pending until we restore a separate emulator slot (uninstalling the release build unblocked the manual steps above). Existing guidance below is still accurate for the earlier blockage scenario.

## Builds
- `./gradlew assembleDebug` (from `platform/android/`) – **PASS**  
  - Notes: MuPDF NDK warnings about `_LARGEFILE_SOURCE` redefinition and `%ld` vs `%lld` format strings remain; track for cleanup during native rework.

## Instrumented UI Smoke
- Command: `ANDROID_SERIAL=localhost:42865 ./gradlew connectedDebugAndroidTest`  
  - Result: **Blocked**  
    - Failure: `INSTALL_FAILED_UPDATE_INCOMPATIBLE` when Gradle tries to push the debug APK to the Genymotion device. The device has the signed F-Droid build installed, which uses a different signing key.
    - Next action: Uninstall the production build (`adb -s localhost:42865 shell pm uninstall org.opendroidpdf`) before running tests, or install/run tests on a fresh emulator image. If MANAGE_EXTERNAL_STORAGE was granted manually, it will need to be re-applied after reinstall.

## Manual Pen / Color / Text Workflow
- Not executed yet (blocked by the same signature conflict).  
- Suggested checklist is preserved in `docs/housekeeping/fdroid_rebrand_notes.md` for quick follow-up once the device is reset.
