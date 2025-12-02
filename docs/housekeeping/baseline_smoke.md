# Baseline Smoke Coverage – 2025-11-15

## Update – 2025-12-01

### Phase 3 resource sweep – signature + dialog polish
- The signature toolchain now pulls every bit of copy from resources: `strings_annotation.xml` carries the certificate prompt, report title, sign action label, and the "no signature support" warning (plus localized mirrors in `values-de`/`values-es`), while `strings_document.xml` exposes the formatted "Error opening link" toast for the MuPDF reader. This removes the last bits of hard-coded English left in `MuPDFPageView`/`MuPDFReaderView` and keeps future localization runs scoped per feature.
- `dialog_pen_size.xml` no longer encodes a literal 4 dp top margin—the layout uses the new `@dimen/annotation_dialog_value_compact_spacing`, so spacing tweaks flow through `values/dimens_annotation.xml` alongside the rest of the annotation dialog metrics.
- ClipBoard labels from the text-selection helper now reference `R.string.app_name` instead of the old "MuPDF" literal, keeping the copy consistent with the OpenDroidPDF branding regardless of locale.
- Export/share toolbar + overflow entries verified in-app; the chooser TabLayout and permission prompts obey the new shared styles, and overflow menu dumps (`overflow2_dump.xml`, `overflow3_dump.xml`) confirm the localized strings from `strings_menu.xml` are the ones rendered.
- Export/share UI now shares the same toolbar theming as the rest of the app: the TabLayout in `chooser.xml` references the new `Widget.OpenDroidPDF.Toolbar.TabLayout` style (backed by `values/dimens_toolbar.xml`), so its elevation, indicator colors, and ripple state live alongside the other toolbar definitions instead of being hard-coded in the layout.
- Permission rationale dialogs rely entirely on `Widget.OpenDroidPDF.Dialog.Message` after dropping the last inline `android:textAppearance`, which keeps padding/typography consistent with the shared dialog container and simplifies future localization passes.

### Genymotion smoke – Ink color toolbar path + export/share/print
- Build: `./gradlew assembleDebug` (platform/android) – **PASS** (`OpenDroidPDF-debug.apk`). Installed fresh after uninstalling the release build.
- Device: Genymotion Pixel 6 (Android 13) @ `localhost:42865`, MANAGE_EXTERNAL_STORAGE granted via `adb appops`. Test asset staged at `/sdcard/Download/test_blank.pdf`.
- Steps: (1) `adb shell am start … file:///sdcard/Download/test_blank.pdf` to load the blank PDF. (2) `adb shell input tap 970 80` to enter draw mode. (3) `adb shell input tap 875 80` to invoke the toolbar "INK COLOR" action. (4) Captured screenshot `tmp_phase3_ink_dialog.png` plus `uiautomator` dumps before/after (`ink_color_toolbar.xml`) and logcat snapshot `tmp_logcat_geny_latest.txt`. (5) Re-opened the overflow menu via `adb shell input keyevent 82`, captured menu hierarchy (`overflow2_dump.xml`, `overflow3_dump.xml`), triggered Share… (`tmp_geny_share.png`, `share_sheet.xml`) and Print (`tmp_geny_print.png`, `print_sheet.xml`) to validate the export flows. (6) Saved filtered logs to `tmp_logcat_phase3_smoke.txt`.
- Result: Ink color dialog renders without crashes; Share… launches the Android chooser with no `AndroidRuntime` noise, and Print hands off to `android.printservice` successfully. No `OpenDroidPDFActivity` errors in `tmp_logcat_phase3_smoke.txt`, so the resource/menu refactors did not regress export/share.

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
- Annotation/toolbar resources have joined the split: shared dialog padding/typography lives in `values/dimens_annotation.xml` and `values/styles_annotation.xml`, while all toolbar widgets consume `values/styles_toolbar.xml`. Layouts such as `dialog_pen_size.xml`, `chooser.xml`, `settings.xml`, and `main.xml` now point to these shared styles so future tweaks (padding, fonts, colors) happen once instead of per layout.
- Annotation-specific menu/gesture wiring moved out of `OpenDroidPDFActivity` into `org.opendroidpdf.app.annotation.AnnotationToolbarController`. The activity now delegates `annot_menu`/`edit_menu` inflation and menu handling through that controller, which centralizes the draw/erase buttons, undo enablement, and the cancel action view logic alongside the rest of the annotation feature code.
- Document export/share copy has its own bucket (`values/strings_export.xml`) so localized strings for print/share dialogs no longer live inside the generic document strings file.
- The main action-bar toolbar now flows through `org.opendroidpdf.app.document.DocumentToolbarController`, so add/print/share/save/go-to-page/link-back behaviors live with the document feature instead of sitting in the activity. When verifying future regressions, confirm the controller properly hides menu items when no document is loaded (share/print/add page should disappear on the dashboard, and “Delete this note” should only appear for files under `OpenDroidPDFNotes`).
- File pickers/noise-free recent lists now share a single set of entry styles. `picker_entry.xml`, the file list footer, and the `recentfiles.xml` shim all reference `values/styles_filebrowser.xml` + `values/dimens_filebrowser.xml`, so icon/text paddings live in one place and no layout carries literal dp values any longer.
- Action bar search inflation is owned by `org.opendroidpdf.app.search.SearchToolbarController`. `OpenDroidPDFActivity` just flips into `ActionBarMode.Search` and the controller wires up the SearchView (listeners, searchable config, restoring the previous query) without sprinkling MenuItem plumbing through the activity.
- Dialogs now lean on shared resources too: `values/dimens_dialogs.xml` + `values/styles_dialogs.xml` define the padding/min-height for annotation text entry and progress/preparation states. `dialog_text_input.xml` is the single text-entry wrapper used by both the annotation tool and the MuPDF form helper (the old `textentry.xml` stub has been removed), the new `dialog_progress.xml` handles all save/share/export spinners, and the storage-permission rationale inflates `dialog_permission_rationale.xml` so its copy sits inside the same styled container as the other dialogs. Permission text continues to live in `values/strings_permissions.xml`, keeping storage prompts separate from the general file-browser copy.
- The password prompt now reuses `dialog_text_input.xml`, so every text-entry surface (annotations, go-to-page, new document, password) inherits the same padding and typography.
- Legacy Google Cloud Print plumbing (`PrintDialogActivity` + `print_dialog.xml`) has been removed entirely. Printing now always flows through Android's `PrintManager`, so there is no separate WebView preview activity to maintain.

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
