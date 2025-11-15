# OpenDroidPDF Refactor & Rebrand Plan

## Phase 0 – Housekeeping & Baseline
- Audit repo clutter (screenshots, UI dumps, Gradle wrapper copies); decide what stays checked in and script removal of noise.
- Run baseline smoke tests (`./gradlew assembleDebug`, Genymotion pen/color/text workflows) to lock current behaviour for regressions.
- Freeze F-Droid metadata/automation scripts so renaming and package changes can be coordinated.

## Phase 1 – Rebrand & Build Configuration
- Rename `com.cgogolin.penandpdf` → `org.opendroidpdf` (or final reverse-DNS choice) using IDE refactor to update manifests, code, XML, JNI prefixes.
- Update Gradle modules and output naming to reflect OpenDroidPDF; refresh F-Droid metadata and deployment scripts.
- Replace user-facing branding (app label, launcher icons, README, license headers) with OpenDroidPDF equivalents.

## Phase 2 – Architectural Decomposition (App Layer)
- Split `PenAndPDFActivity` into dedicated components: dashboard/home fragment, document reader host, separate settings activity.
- Extract helpers (`IntentRouter`, `StoragePermissionHelper`, `ExportController`, `PenSettingsController`, `ToolbarStateController`) into packages under `com.opendroidpdf.app`.
- Break drawing responsibilities into focused classes (overlay, annotation manager, gesture handler, undo stack) within a `drawing` package.
- Centralize preferences in a `PenPreferences` abstraction (SharedPreferences/DataStore) decoupled from UI listeners.
- Migrate custom `CancellableAsyncTask` usage to coroutines or Lifecycle-aware components; introduce lightweight dependency injection for core services.

## Phase 3 – Resource & UI Cleanup
- Group layouts/menus/styles by feature; remove duplicate dialog definitions and harmonize naming.
- Normalize string/color resources (pen palette, slider limits, text styles) for clarity and reuse.
- Refine toolbar/menu wiring so gesture bindings live with their owning UI components.

## Phase 4 – Native Layer Restructure
- Split `platform/android/jni/mupdf.c` into logical units (`ink.c`, `text_annot.c`, `document_io.c`, `render.c`, `utils.c`) with shared headers.
- Update JNI naming macros to the new package namespace and document the bridge surface in `jni/README.md`.
- Ensure `Core.mk`/`ThirdParty.mk` match the new file layout; expose a Java/Kotlin façade (`MuPdfRepository`) to isolate JNI usage.

## Phase 5 – Configuration & Build Variants
- Centralize build config (paths, keystore, ABI flags) in `gradle.properties` and/or `buildSrc` constants.
- Evaluate splitting into Gradle modules (`app`, `core`, `feature-drawing`, `feature-text`) once Java packages are separated.
- Enable R8/ProGuard with curated rules after refactor stabilizes; verify trimmed APK size goals remain intact.

## Phase 6 – Testing & Tooling
- Add instrumentation tests for pen/text workflows (gesture simulation, undo, export) plus unit tests for preferences and undo stack.
- Configure CI (e.g., GitHub Actions) to run lint, `assembleRelease`, and key emulator tests.
- Update F-Droid deployment to new package/version paths and automate changelog generation.

## Phase 7 – Documentation & Transition
- Document module map, build instructions, coding conventions, and native build notes under `docs/`.
- Update LICENSE, CONTRIBUTING, README with OpenDroidPDF governance and architecture overview.
- Plan migration for existing installs (preference key migration, package rename handling) and communicate changes to downstream users.
