# OpenDroidPDF Refactor & Rebrand Plan

## Phase 0 – Housekeeping & Baseline
- ✅ (2025-11-28) Keep workspace tidy: documented what stays vs. goes in `docs/housekeeping/untracked_inventory.md`, expanded `.gitignore`, and extended `scripts/cleanup_workspace.sh` to purge dumps/screenshots/PDF leftovers automatically.
- ✅ (2025-11-28) Capture a reproducible baseline (`./gradlew assembleDebug`, Genymotion pen-size/export workflow) for regression comparison – see `docs/housekeeping/baseline_smoke.md`.
- ✅ (2025-11-28) Freeze the existing F-Droid metadata/automation scripts (`fdroid/metadata/org.opendroidpdf.yml`, `DEPLOYMENT-FDROID.md`) so later package-name and branding changes stay coordinated.

## Phase 1 – Rebrand & Build Configuration (complete)
- ✅ (2025-11-28) Gradle outputs and F-Droid metadata now target `org.opendroidpdf` (new `opendroidpdfAbi` override, `/mnt/subtitled/opendroidpdf-android-build` output path, and tracked metadata at `fdroid/metadata/org.opendroidpdf.yml`).
- ✅ (2025-12-01) User-facing branding, launcher icons, README/licensing text, deployment docs, and settings/About flows now read “OpenDroidPDF” everywhere while preserving upstream attribution. Legacy identifiers remain only where migrations demand (e.g., preference keys, storage dirs) and are documented in `docs/housekeeping/baseline_smoke.md`.
- ✅ (2025-12-01) Deployment docs/scripts/F-Droid metadata reference the final naming scheme so future releases stay in sync without extra cleanup.

## Phase 2 – Architectural Decomposition (App Layer)
- Split `OpenDroidPDFActivity` (formerly `PenAndPDFActivity`) into targeted components (dashboard fragment, document reader host, standalone settings activity).
- Extract helpers such as `IntentRouter`, `StoragePermissionHelper`, `ExportController`, `PenSettingsController`, `ToolbarStateController` under an `org.opendroidpdf.app` package.
- Break drawing logic into overlay/annotation manager/gesture handler/undo stack classes and introduce a central `PenPreferences` abstraction.
- Replace custom async utilities with coroutines or other lifecycle-aware constructs and add lightweight dependency injection for shared services.

## Phase 3 – Resource & UI Cleanup
- Group layouts/menus/styles by feature, dedupe dialog/layout variants, and normalize strings/colors for pen palette, slider limits, text styles, etc.
- Keep toolbar/menu/gesture bindings with their owning UI code for clarity and easier maintenance.

## Phase 4 – Native Layer Restructure (in progress)
- ✅ (2025-12-02) Split the former 3.5 k-line `mupdf.c` into logical units (`document_io.c`, `render.c`, `ink.c`, `text.c`, `text_annot.c`, `widgets.c`, `utils.c`) with a shared header (`mupdf_native.h`). `Android.mk` now builds the new sources, `MuPDFCore_gotoPageInternal` is declared in the header for cross-file callers, and the new structure compiles (`./gradlew assembleDebug`).
- 🚧 Next: peel off the remaining native helpers (alerts/cookies/proof/seps/text-selection glue) into their own translation units, align `Core.mk`/`ThirdParty.mk` documentation, and sketch the future JVM façade (`MuPdfRepository`).

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
