# Contributing to OpenDroidPDF

Thanks for helping improve OpenDroidPDF! Please follow these guidelines to keep reviews fast and releases reproducible.

## License & DCO
- All contributions are licensed under **AGPL-3.0-only**. By submitting code you agree to this licensing.
- Use a “Signed-off-by” line in commit messages (Developer Certificate of Origin, https://developercertificate.org). Example:
  ```
  Signed-off-by: Your Name <you@example.com>
  ```

## Getting started
1. Clone: `git clone https://github.com/pepperpepperpepper/OpenDroidPDF.git`
2. Build debug: `cd platform/android && ./gradlew assembleDebug`
3. Tests: `./gradlew connectedDebugAndroidTest` (emulator or Genymotion; set `ANDROID_SERIAL` if multiple devices).

## Coding guidelines
- Kotlin first for new app-layer code; keep JNI-visible types stable.
- Route MuPDF interactions through `MuPdfRepository`/`MuPdfController`; avoid new direct `MuPDFCore` calls.
- Prefer coroutines (`AppCoroutines`) over `AsyncTask`/Handlers.
- Add/extend instrumentation tests for user-visible fixes (ink, export, undo, widgets).

## Branch & PR style
- Keep changes focused; one feature/fix per PR.
- Update docs when behavior changes (`docs/architecture.md`, `docs/transition.md`, `README`).
- If you touch JNI, note the entry point in `mupdf_native.h` and keep `proguard-rules.pro` in sync.

## Release hygiene
- Bump `versionCode`/`versionName` in `platform/android/build.gradle` and `AndroidManifest.xml`.
- Update `fdroid/metadata/org.opendroidpdf.yml`; run `scripts/update_and_deploy.sh` if you publish.
- Let the deploy script generate `site/changelog/org.opendroidpdf.txt` (auto-run inside the script).

## Reporting issues
- Include device model, Android version, steps to reproduce, and if possible attach a sample PDF that triggers the issue.
- For export/undo bugs, attach `logcat` with `OpenDroidPDF` tag filtering and note pen size/color used.
