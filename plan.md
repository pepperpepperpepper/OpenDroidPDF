# OpenDroidPDF Refactor & Rebrand Plan

## Phase 0 – Housekeeping & Baseline
- ✅ (2025-11-28) Keep workspace tidy: documented what stays vs. goes in `docs/housekeeping/untracked_inventory.md`, expanded `.gitignore`, and extended `scripts/cleanup_workspace.sh` to purge dumps/screenshots/PDF leftovers automatically.
- ✅ (2025-11-28) Capture a reproducible baseline (`./gradlew assembleDebug`, Genymotion pen-size/export workflow) for regression comparison – see `docs/housekeeping/baseline_smoke.md`.
- ✅ (2025-11-28) Freeze the existing F-Droid metadata/automation scripts (`fdroid/metadata/org.opendroidpdf.yml`, `DEPLOYMENT-FDROID.md`) so later package-name and branding changes stay coordinated.

## Phase 1 – Rebrand & Build Configuration (in progress)
- ✅ (2025-11-28) Gradle outputs and F-Droid metadata now target `org.opendroidpdf` (new `opendroidpdfAbi` override, `/mnt/subtitled/opendroidpdf-android-build` output path, and tracked metadata at `fdroid/metadata/org.opendroidpdf.yml`).
- 🚧 (2025-11-28) Replace all user-facing branding (app name, toolbar title, launcher icons, README, license headers, About screen) with OpenDroidPDF wording while retaining upstream attribution. (About screen + settings copy + OpenDroidPDFNotes migration complete; launcher icons + residual documentation still pending.)
- Ensure deployment docs, scripts, and environment variables use the ODP naming scheme.

## Phase 2 – Architectural Decomposition (App Layer)
- Split `OpenDroidPDFActivity` (formerly `PenAndPDFActivity`) into targeted components (dashboard fragment, document reader host, standalone settings activity).
- Extract helpers such as `IntentRouter`, `StoragePermissionHelper`, `ExportController`, `PenSettingsController`, `ToolbarStateController` under an `org.opendroidpdf.app` package.
- Break drawing logic into overlay/annotation manager/gesture handler/undo stack classes and introduce a central `PenPreferences` abstraction.
- Replace custom async utilities with coroutines or other lifecycle-aware constructs and add lightweight dependency injection for shared services.

## Phase 3 – Resource & UI Cleanup
- Group layouts/menus/styles by feature, dedupe dialog/layout variants, and normalize strings/colors for pen palette, slider limits, text styles, etc.
- Keep toolbar/menu/gesture bindings with their owning UI code for clarity and easier maintenance.

## Phase 4 – Native Layer Restructure
- Split `platform/android/jni/mupdf.c` into logical units (`ink.c`, `text_annot.c`, `document_io.c`, `render.c`, `utils.c`) plus shared headers, and update JNI naming to the new namespace.
- Align `Core.mk`/`ThirdParty.mk` with the new layout and document the JNI bridge surface; expose a Java/Kotlin façade (`MuPdfRepository`) to isolate native details.

## Phase 5 – Configuration & Build Variants
- Centralize build configuration (paths, keystore, ABI flags) via `gradle.properties`/`buildSrc` constants.
- Consider splitting into Gradle modules (`app`, `core`, `feature-drawing`, `feature-text`) once packages are separated.
- Enable R8/ProGuard with curated rules after the refactor stabilizes and ensure APK size goals remain intact.

## Phase 6 – Testing & Tooling
- Add instrumentation tests for pen/color/text workflows (gestures, undo, export) and unit tests for preferences/undo stack.
- Stand up CI (e.g., GitHub Actions) for lint, `assembleRelease`, and key emulator scenarios.
- Update F-Droid deployment scripts to the new package name/version paths and automate changelog generation.

## Phase 7 – Documentation & Transition
- Document module layout, build steps, coding conventions, and native build notes under `docs/`.
- Refresh LICENSE, CONTRIBUTING, README, and other governance docs with OpenDroidPDF details.
- Plan migration guidance for existing installs (preference key moves, package rename impact) and communicate to downstream users.
