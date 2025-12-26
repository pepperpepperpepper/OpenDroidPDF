# TODO — Fix GitHub “Android CI” failures

Problem
- GitHub Actions workflow `.github/workflows/android-ci.yml` is emailing failures.
- Root causes seen so far:
  - `build` job runs `./gradlew lint assembleRelease` and failed on `MissingTranslation` for new strings.
  - Once `build` is green, `connected` will run `./gradlew connectedDebugAndroidTest` and our instrumentation tests are currently out of date with recent refactors.

## A) Make CI “build” green (lint + release)
- [x] Reproduce CI build locally: `cd platform/android && ./gradlew lint assembleRelease -PopendroidpdfAbi=x86_64 -Popendroidpdf.buildDir=/tmp/opendroidpdf-ci-build`
- [x] Fix MissingTranslation by adding `de/es` strings for sidecar import/export.
  - Landed as commit `f29372fd` (already pushed).
- [ ] Fix GitHub build runner deps (likely missing NDK/sdk packages): install via `sdkmanager` in `.github/workflows/android-ci.yml`.
- [ ] Confirm the latest GitHub “Android CI / build” run is green (no new alerts).

## B) Make CI “connected” green (instrumentation)
Goal: `./gradlew connectedDebugAndroidTest` compiles + runs without failures on CI’s emulator (API 30 x86_64).

### Fix compilation breakages in androidTest (due to refactors)
- [x] Update `platform/android/tests/androidTest/java/org/opendroidpdf/PenPreferencesTest.kt`:
  - Replace removed `PenPreferences` usage with `PenPreferencesServiceImpl` + `SharedPreferencesPenPrefsStore`.
- [x] Update `platform/android/tests/androidTest/java/org/opendroidpdf/PreferencesMigrationTest.java`:
  - Replace removed `SettingsActivity.ensurePreferencesNamespace(...)` with `PreferencesNamespaceMigrator.ensureMigrated(...)`.
- [x] Update `platform/android/tests/androidTest/java/org/opendroidpdf/UndoWorkflowInstrumentedTest.java`:
  - `MuPDFPageView` now requires `ReaderComposition` in the constructor; create a minimal `ReaderComposition` for the test.
  - Ensure any `ReaderComposition` construction that touches `Handler/Looper` happens on the UI thread.
- [x] Update `platform/android/tests/androidTest/java/org/opendroidpdf/DebugBroadcastInstrumentationTest.java`:
  - `DebugActionsController.runExportTest(...)` now takes a `DebugActionsController.Host`; use `DebugActionsHostAdapter`.

### Run the same commands CI runs
- [x] Compile androidTest APK (CI ABI): `cd platform/android && ./gradlew assembleDebugAndroidTest -PopendroidpdfAbi=x86_64 -Popendroidpdf.buildDir=/tmp/opendroidpdf-connected-ci`
- [x] Run tests locally (ARM device, no ABI override): `cd platform/android && ./gradlew connectedDebugAndroidTest -Popendroidpdf.buildDir=/tmp/opendroidpdf-connected-full2`
- [ ] If any tests are flaky, fix determinism or mark as ignored only if justified.

### Land the fix
- [x] Land androidTest refactor fix (commit `b8b61d21` pushed).
- [ ] Confirm GitHub “Android CI / build” and “Android CI / connected” both succeed.
