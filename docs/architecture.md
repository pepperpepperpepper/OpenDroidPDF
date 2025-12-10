# OpenDroidPDF Architecture & Build Notes

## Modules
- **app (platform/android)** — Android application, UI/controllers, activities/fragments, Kotlin coroutines, dependency wiring via `AppServices`.
- **core (platform/android/core)** — Shared JVM types (Annotation/Link/PassClickResult/etc.) and keep rules for downstream consumers.
- **jni (platform/android/jni)** — MuPDF-based native glue split by concern (`document_io.c`, `render.c`, `ink.c`, `text_annot.c`, `export_share.c`, `widgets*.c`, `alerts.c`, `utils.c`, `mupdf_native.h`).

## Key packages
- `org.opendroidpdf.app.*` — Controllers (drawing, undo, toolbar state, export, permissions), fragments (`DashboardFragment`, `DocumentHostFragment`), intent routing, service locator (`AppServices`). Includes `DocumentSetupController` for core/docView/search wiring and `DocViewFactory` for creating `MuPDFReaderView`.
- `org.opendroidpdf.core.*` — `MuPdfRepository` façade over `MuPDFCore`, Kotlin `MuPdfController`, async controllers (Annotation/Search/Widget/Signature/DocumentContent/Save), coroutine helpers (`AppCoroutines`). `AnnotationController` now emits debug logs for add/delete to aid emulator validation.
- `org.opendroidpdf` — Activity (`OpenDroidPDFActivity`), page view/adapters, legacy data migrations, JNI bridge classes (`OpenDroidPDFCore`, `MuPDFCore`).

## Build & targets
- Default build dir: `/mnt/subtitled/opendroidpdf-android-build` (override with `-Popendroidpdf.buildDir=...`).
- Build/debug: `./gradlew assembleDebug`
- Build/release (with R8): `./gradlew clean assembleRelease`
- ABI override: `-Popendroidpdf.abi=arm64-v8a,armeabi-v7a`
- Version override: `-Popendroidpdf.versionCode=101 -Popendroidpdf.versionName=1.3.40`

## Tests
- Unit/robolectric (none currently); androidTest: `./gradlew connectedDebugAndroidTest`
- Notable suites: `PreferencesMigrationTest`, `PenPreferencesTest`, `InkUndoControllerTest`, `InkColorExportInstrumentedTest`, `UndoWorkflowInstrumentedTest`, `FontFallbackInstrumentedTest`.
- CI: `.github/workflows/android-ci.yml` runs lint + assembleRelease + connected tests on API 30 x86_64 emulator.

## Native notes
- Rendering/ink/text/export are isolated per file; all JNI entry points declared in `mupdf_native.h`.
- Ink/Text annotation writers now mark annotations dirty (`pdf_dirty_annot`) and call `pdf_update_page` to ensure exports persist edits.
- Saving uses `pdf_save_document` for PDFs (fallback to `fz_new_pdf_writer` for non-PDF).
- Keep JNI-visible classes in `proguard-rules.pro` + `core/proguard-rules.pro`.

## Coding conventions (brief)
- Kotlin-first for new controllers; Java kept where JNI or legacy surface requires.
- Coroutine scopes via `AppCoroutines` (main/io); avoid AsyncTask/Handler in new code. The legacy `CancellableAsyncTask` shim has been removed.
- UI delegates through controllers; activities/fragments own wiring, not business logic.
- All direct `MuPDFCore` calls should flow through `MuPdfRepository/MuPdfController` (and native through the `jni/` split).
