# Baseline Smoke Coverage – 2025-11-15

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

