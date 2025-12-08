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
- ✅ Extracted pen settings persistence/UI into `PenPreferences` + `PenSettingsController` so the pen dialog no longer lives in `OpenDroidPDFActivity`.
- ✅ Split `OpenDroidPDFActivity` (formerly `PenAndPDFActivity`) into targeted components (dashboard fragment, document reader host, standalone settings activity).
- ✅ Extracted helpers such as `IntentRouter`, `StoragePermissionHelper`, `ExportController`, `PenSettingsController`, `ToolbarStateController` under an `org.opendroidpdf.app` package.
- ✅ Broke committed-ink undo into `app/annotation/InkUndoController` and delegated activity toolbar enablement to an expanded `ToolbarStateController`; overlay drawing still lives in `PageView` but now reports through the controller for undo state.
- ✅ Moved the drawing/gesture pipeline into a dedicated `DrawingController` host so `PageView` focuses on rendering; added `AppServices` service locator and cleaned up duplicate inline toolbar handling.
- ✅ (2025-12-08) Replaced the shared Handler/executor helpers with `AppCoroutines` (main/IO scopes, lifecycle-aware helpers); UI scheduling in readers, file browsers, search, and intent routing now runs on coroutines.

## Phase 3 – Resource & UI Cleanup
- ✅ (2025-12-09) Dashboard cards/headings now use shared text/image styles/dimens; dialog padding is unified across annotation/progress/permission/text-entry surfaces; pen palette labeling/strings normalized.

## Phase 4 – Native Layer Restructure (in progress)
- ✅ (2025-12-02) Split the former 3.5 k-line `mupdf.c` into logical units (`document_io.c`, `render.c`, `ink.c`, `text_selection.c`, `export_share.c`, `text_annot.c`, `widgets.c`, `widgets_signature.c`, `utils.c`) with a shared header (`mupdf_native.h`). `Android.mk` now builds the new sources, `MuPDFCore_gotoPageInternal` is declared in the header for cross-file callers, and the new structure compiles (`./gradlew assembleDebug`).
- ✅ (2025-12-02) Peeled off the alert loop, cookie helpers, proof/separation utilities, and JNI alert glue into `alerts.c`, `cookies.c`, `proof.c`, and `separations.c`, keeping `document_io.c` focused on document/session lifecycle logic and shrinking `utils.c` back to common helpers only.
- ✅ (2025-12-07) Moved the text search/export helpers into `text_selection.c`/`export_share.c` and split signature plumbing into `widgets_signature.c`, so `widgets.c` now handles generic form widgets while the new module owns certificate/sign operations.
- ✅ (2025-12-07) Introduced a `org.opendroidpdf.core.MuPdfRepository` façade that wraps core search/text/export/save flows so upcoming UI refactors can depend on a stable JVM surface instead of direct JNI calls.
- ✅ (2025-12-07) Routed document toolbar actions (insert page, save, share/print) and annotation ink commits through `MuPdfRepository` (`OpenDroidPDFActivity`, `MuPdfRepository`). Export/share dialogs now ask the façade for file names/URIs, and `commitPendingInkToCoreBlocking()` uses the repository’s `addInkAnnotation()/refreshAnnotationAppearance()` helpers.
- ✅ (2025-12-07) Extended the façade to cover widget/text-annotation helpers (`loadAnnotations`, `getWidgetAreas`, `setWidgetText/Choice`, signature checks) and added the Kotlin `MuPdfController`, allowing `MuPDFPageView`/`MuPDFPageAdapter` to consume annotation/widget operations without touching `MuPDFCore`. `platform/android/jni/README.md` now documents the final module layout and how new JNI calls must flow through the repository/controller pair.
- ✅ (2025-12-08) Finished migrating the rendering/patch and widget hit paths: `MuPDFPageView`, `MuPDFPageAdapter`, and the thumbnail generator now obtain cookies, draw/update pages, and run `passClickEvent` via `MuPdfController`/`MuPdfRepository`. `MuPDFCancellableTaskDefinition` also pulls cookies from the façade, so UI code no longer instantiates `MuPDFCore` directly for patches.
- ✅ (2025-12-08) Added a Kotlin `SearchController` so `SearchTaskManager`/toolbar search paths consume the façade instead of instantiating repositories on the fly, and rewired `PdfThumbnailManager` to render through `MuPdfController` for thumb generation consistency.
- ✅ (2025-12-07) Cut release **1.3.35 (96)** with the MuPDF façade work: bumped Gradle/manifest + F-Droid metadata, built/signed the APK, ran `/home/arch/fdroid/scripts/update_and_deploy.sh`, and verified `index-v1.json` reports `versionName=1.3.35 versionCode=96`.
- ✅ (2025-12-09) Shipped **1.3.36 (97)** after wiring widget/signature controllers + repository-backed tests: version bump, F-Droid metadata sync, release build/sign, `/home/arch/fdroid/scripts/update_and_deploy.sh`, and `index-v1.json` now reports `versionName=1.3.36 versionCode=97`.
- ✅ (2025-12-09) Instrumentation/tests (`InkUndo`, `UndoWorkflow`, `FontFallback`) now instantiate `OpenDroidPDFCore` exclusively via `MuPdfRepository`/`MuPdfController`, and new Kotlin `WidgetController`/`SignatureController` keep widget + signature flows off raw `MuPDFCore` APIs.
- ✅ (2025-12-07) Retired the final UI `AsyncTask`s: alert waiters run through `core/AlertController`, save/export dialogs use `core/SaveController`, `CancellableAsyncTask` now rides a shared executor + main-thread handler, `MuPDFPageAdapter` prefetches page sizes via a guarded executor, and dashboard thumbnails load on plain threads. Text selection/link/widget smokes (two_page_sample + pdf.js annotation samples) on Genymotion confirm the single-thread DocumentContentController keeps up without cancellations.
- 🚧 Next: stage the post-façade release (changelog + metadata + F-Droid push), then roll into Phase 5 by consolidating Gradle/build-config knobs now that the JNI + controller surfaces are fully modernized.

## Phase 5 – Configuration & Build Variants (complete)
- ✅ (2025-12-07) Centralized build configuration (buildDir, SDK/NDK levels, version codes/names, ABI overrides) via `gradle.properties` + property helpers in `build.gradle`, and enabled release R8/ProGuard with JNI-safe keep rules.
- ✅ (2025-12-09) Introduced a separate `:core` Android library module for shared drawing/math/data types (`Annotation`, `LinkInfo*`, `PassClickResult*`, `PointFMath`, `DrawingController`, etc.) and wired the app module to consume it; kept build output segregation and consumer ProGuard keep rules in `core/proguard-rules.pro`.
- ✅ (2025-12-08) Module-level R8 configuration reviewed post-split: release now minifies/shrinks via app rules plus the new core consumer rules; `assembleRelease` passes and the signed `org.opendroidpdf_99.apk` is published to the self-hosted F-Droid repo.

## Phase 6 – Testing & Tooling
- Add instrumentation tests for pen/color/text workflows (gestures, undo, export) and unit tests for preferences/undo stack.
- Stand up CI (e.g., GitHub Actions) for lint, `assembleRelease`, and key emulator scenarios.
- Update F-Droid deployment scripts to the new package name/version paths and automate changelog generation.

## Phase 7 – Documentation & Transition
- Document module layout, build steps, coding conventions, and native build notes under `docs/`.
- Refresh LICENSE, CONTRIBUTING, README, and other governance docs with OpenDroidPDF details.
- Plan migration guidance for existing installs (preference key moves, package rename impact) and communicate to downstream users.
