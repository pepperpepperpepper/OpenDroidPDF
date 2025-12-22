# Baseline Smoke Coverage – 2025-11-15

## Update – 2025-12-22 (Centralize reopen-for-edit picker)
- Commit: `d25a4332`.
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion PDF smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_smoke.sh` – **PASS**.
- Genymotion Save permission downgrade smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_save_permission_downgrade_smoke.sh` – **PASS**.
- Genymotion PDF read-only export smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_pdf_readonly_export_smoke.sh` – **PASS**.

## Update – 2025-12-22 (PDF sidecar export smoke: preserve extractable text)
- Commit: `8f3aa35b`.
- Genymotion PDF read-only export smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_pdf_readonly_export_smoke.sh` – **PASS** (now pulls the exported PDF and asserts `pdftotext` finds “quick brown fox”, proving we did not flatten rasterize the PDF).

## Update – 2025-12-22 (Centralize SAF intent shapes)
- Commit: `65d60f5b`.
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion PDF smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_smoke.sh` – **PASS**.
- Genymotion EPUB smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_smoke.sh` – **PASS**.
- Genymotion Save permission downgrade smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_save_permission_downgrade_smoke.sh` – **PASS**.

## Update – 2025-12-21 (E5: highlight TextQuoteSelector anchors)
- Commit: `10a31431`.
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion EPUB highlight reanchor smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_highlight_reanchor_smoke.sh` – **PASS** (store `quote_prefix`/`quote_suffix` and re-anchor highlight after layout-affecting relayout without showing mismatch banner).
- Genymotion EPUB smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_smoke.sh` – **PASS**.
- Genymotion PDF smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_smoke.sh` – **PASS**.

## Update – 2025-12-21 (DocId is content-based; survives rename/move)
- Commit: `e080a1c6`.
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion docId rename smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_docid_rename_smoke.sh` – **PASS** (open → draw/commit → rename file → reopen → draw/commit → assert ink rows still belong to a single `sha256:*` doc id).
- Genymotion PDF smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_smoke.sh` – **PASS**.
- Genymotion EPUB smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_smoke.sh` – **PASS**.
- Genymotion EPUB viewport restore smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_viewport_restore_smoke.sh` – **PASS**.

## Update – 2025-12-21 (EPUB ink locks layout controls)
- Commit: `1d24ac23`.
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion EPUB ink layout-lock smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_ink_layout_lock_smoke.sh` – **PASS** (draw+accept ink → Reading settings shows “layout locked” notice + disables font/margins/line-spacing while keeping theme changes).

## Update – 2025-12-21 (EPUB theme is paint-only)
- Commit: `acad4597`.
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion EPUB theme smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_theme_paint_only_smoke.sh` – **PASS** (create sidecar note → change theme Light→Dark → relaunch → assert no “annotations hidden / layout mismatch” banner, i.e. theme did not change `layoutProfileId`).

## Update – 2025-12-21 (EPUB image fixture + image-render smoke)
- Commit: `9efe116f`.
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion EPUB image smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_image_smoke.sh` – **PASS** (open `test_assets/image.epub` → assert non-blank render + “magenta image” pixel presence).

## Update – 2025-12-21 (EPUB3 nav.xhtml TOC + TOC smoke hardening)
- Commit: `69d52282` (plus `482f17ed` for the EPUB3 fixture/parser).
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion PDF smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_smoke.sh` – **PASS**.
- Genymotion EPUB smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_smoke.sh` – **PASS**.
- Genymotion Edge EPUB relayout smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_edge_relayout_smoke.sh` – **PASS**.
- Genymotion EPUB TOC smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_toc_smoke.sh` – **PASS** (now prefers toolbar page-indicator change over screenshot diffs).
- Genymotion EPUB3 `nav.xhtml` TOC smoke (Pixel 6 / Android 13 @ `localhost:42865`): `EPUB_LOCAL=test_assets/nav.epub TOC_ENTRY_TEXT='Nav Chapter 2' ./scripts/geny_epub_toc_smoke.sh` – **PASS**.
- Genymotion PDF text+search smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_pdf_text_search_smoke.sh` – **PASS**.

## Update – 2025-12-21 (EPUB search parity + relayout safety)
- Commit: `24ac25d3`.
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Change: reflow relayout now cancels search tasks and clears stale search results before recreating the adapter, so search overlays don’t drift across pagination changes.
- Genymotion PDF smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_smoke.sh` – **PASS**.
- Genymotion EPUB smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_smoke.sh` – **PASS**.
- Genymotion Edge EPUB relayout smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_edge_relayout_smoke.sh` – **PASS**.
- Genymotion PDF text+search smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_pdf_text_search_smoke.sh` – **PASS**.
- Genymotion EPUB text+search + relayout smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_text_search_smoke.sh` – **PASS**.

## Update – 2025-12-21 (EPUB viewport restore)
- Commit: `d7627f96`.
- Genymotion EPUB viewport restore smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_viewport_restore_smoke.sh` – **PASS** (TOC nav → HOME → force-stop → relaunch restores page indicator).

## Update – 2025-12-21 (E5 highlight reanchor)
- Commit: `6bf4ca68`.
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion PDF smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_smoke.sh` – **PASS**.
- Genymotion EPUB smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_smoke.sh` – **PASS** (sidecar annotate + export sanity).
- Genymotion EPUB highlight reanchor smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_highlight_reanchor_smoke.sh` – **PASS** (create highlight → change font size → highlight row re-anchored to new `layoutProfileId`; no mismatch banner).
- Genymotion EPUB layout mismatch smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_layout_mismatch_smoke.sh` – **PASS** (asserts Share/Print are blocked under layout mismatch until switching back to the annotated layout, then export succeeds).
- Genymotion PDF (read-only/sidecar) export smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_pdf_readonly_export_smoke.sh` – **PASS** (asserts “sidecar PDF share/export” prefers embedded annotations and avoids rasterized/flattened output via a size heuristic).
- Genymotion PDF save-permission downgrade smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_save_permission_downgrade_smoke.sh` – **PASS** (forces a save-to-current-URI failure and asserts UI downgrades to export mode and provides an “Enable saving” permission re-request action).

## Update – 2025-12-21 (Edge EPUB relayout)
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion Edge EPUB relayout smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_edge_relayout_smoke.sh` – **PASS** (open `test_assets/edge.epub` → assert non-blank render → change font size + Apply twice → assert non-blank render after each relayout).

## Update – 2025-12-21 (EPUB TOC)
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion EPUB TOC smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_toc_smoke.sh` – **PASS** (open `test_assets/edge.epub` → Contents → tap “Long Paragraphs” → assert screen changes).

## Update – 2025-12-21 (PDF text search)
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion PDF text+search smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_pdf_text_search_smoke.sh` – **PASS** (open `test_assets/pdf_with_text.pdf` → assert non-blank render → Search “quick” → assert search highlight overlay appears).

## Update – 2025-12-21
- Commit: `b4986f90`.
- Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` – **PASS**.
- Genymotion PDF smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_smoke.sh` – **PASS**.
- Genymotion EPUB smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_smoke.sh` – **PASS** (sidecar annotate + export sanity).
- Genymotion EPUB layout mismatch smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_layout_mismatch_smoke.sh` – **PASS** (asserts Share/Print are blocked under layout mismatch until switching back to the annotated layout, then export succeeds).
- Genymotion PDF (read-only/sidecar) export smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_pdf_readonly_export_smoke.sh` – **PASS** (asserts “sidecar PDF share/export” prefers embedded annotations and avoids rasterized/flattened output via a size heuristic).
- Genymotion PDF save-permission downgrade smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_save_permission_downgrade_smoke.sh` – **PASS** (forces a save-to-current-URI failure and asserts UI downgrades to export mode and provides an “Enable saving” permission re-request action).

## Update – 2025-12-20
- Build: `./gradlew testDebugUnitTest assembleDebug -x lint` (from `platform/android/`) – **PASS** after adding the EPUB sidecar annotation session/store wiring.
- Genymotion EPUB smoke (Pixel 6 / Android 13 @ `localhost:42865`): `./scripts/geny_epub_smoke.sh` – **PASS** (open EPUB → verify “Save” hidden → add note → draw/commit → undo → draw/commit → erase → relaunch → share/export). Script exports the sidecar SQLite DB (including WAL) and asserts row counts (`notes`, `ink_strokes`) and also pulls the exported PDF from `cache/tmpfiles/` to assert it’s a non-trivial PDF (header + size threshold).

## Update – 2025-12-14
- Phase 2 refactor slice verified: ServiceLocator now fronts navigation/permission/export paths and OpenDroidPDFActivity is ~665 LOC. MuPDFReaderView sits at ~380 LOC after gesture/page trims; architecture snapshot captured in `docs/architecture.md`.
- Build: `./gradlew assembleDebug -x lint` (platform/android) – **PASS** on the refactored tree.
- Genymotion smoke (Pixel 6 / Android 13 @ `localhost:42865`): installed latest debug APK, launched `test_blank.pdf`, drew + undo, invoked search, and tapped share via `scripts/geny_smoke.sh`. Logcat tail shows normal rendering; no `AndroidRuntime`/`FATAL` entries, and export/share UI opened without crashes.
- Autotest helper updated: `DebugAutotestRunner` supports `autotest_full` and `MuPdfRepository.forceMarkDirty()` to assert dirty-state handling; last run emitted `AUTOTEST_HAS_CHANGES=true` and exported `/data/user/0/org.opendroidpdf/files/autotest-output.pdf` (~609 bytes).
- Annotation UI delegation (2025-12-14): moved markup/text/edit/delete wiring into `AnnotationUiController`; `./gradlew assembleDebug -x lint` pass and `scripts/geny_smoke.sh` pass (same expected persistable-URI warning when opening `test_blank.pdf`).
- Ink isolation (2025-12-14): added `InkController` to own stroke/undo/commit pipeline; `./gradlew assembleDebug -x lint` pass and `scripts/geny_smoke.sh` pass.
- Reader gestures (2025-12-14): introduced `ReaderGestureController` so `MuPDFReaderView` delegates tap/scroll/fling/scale/touch; build + `scripts/geny_smoke.sh` pass.


## Update – 2025-12-08 (PM) – Phase 6 kickoff
- Added instrumentation coverage for settings/pen persistence (`PreferencesMigrationTest`, `PenPreferencesTest`) under `platform/android/tests/androidTest`. Both rely on the namespace migration helper and `PenPreferences` to verify ink thickness/color survive defaults and legacy copies.
- CI is now wired via GitHub Actions (`.github/workflows/android-ci.yml`) to run lint + `assembleRelease` and `connectedDebugAndroidTest` on an API 30 x86_64 emulator using the `opendroidpdfAbi` override. Build output is redirected to `${{ github.workspace }}/.opendroidpdf-build` to conserve runner space.
- Emulator smoke for the new tests is pending on the hosted runners; Genymotion manual checks remain unchanged from the earlier entries below.
- Added export/persistence coverage (`InkColorExportInstrumentedTest`) and fixed native save logic (`export_share.c` now uses `pdf_save_document`, ink/text annotations call `pdf_dirty_annot` + `pdf_update_page`). `connectedDebugAndroidTest` now passes end-to-end on Genymotion Pixel 6 (Android 13, `localhost:42865`).

## Update – 2025-12-15
- `./gradlew assembleDebug -x lint` – **PASS** (service-interface refactor slice)
- `./scripts/geny_smoke.sh` – **PASS** on Genymotion Pixel 6 @ `localhost:42865` (open → draw → undo → search/share). No functional regressions observed; logcat still shows benign `hiddenapi` warnings from MuPDF JNI.

## Update – 2025-12-08

### Phase 5 wrap: core split + R8 release build
- App now builds successfully with the :core library (Java-only shared types) and R8/resource shrinking enabled; release artifact lives at `/mnt/subtitled/opendroidpdf-android-build/outputs/apk/release/OpenDroidPDF-release-unsigned.apk`.
- Signed/zipaligned the release APK with the fdroidrepo key as `org.opendroidpdf_99.apk` (+ `.idsig`) and published via `/home/arch/fdroid/scripts/update_and_deploy.sh`; `https://fdroid.uh-oh.wtf/repo/index-v1.json` now advertises `versionName=1.3.38` / `versionCode=99`.
- Quick sanity on Genymotion Pixel 6 (`localhost:42865`): installed the signed release (`adb install -r /tmp/OpenDroidPDF-release-signed.apk`), launched the main activity (`am start -n org.opendroidpdf/.OpenDroidPDFActivity`); no immediate crashes in logcat. Full pen-size/undo/export smoke to be redone on the minified build during the next QA pass.

## Update – 2025-12-07

### Build config centralization + R8 enablement (Phase 5)
- Release builds now enable R8 shrinking/obfuscation with `proguard-rules.pro` keeping JNI-facing classes (`MuPDFCore`, `Annotation`, `LinkInfo*`, etc.) and `SignatureState`. Debug builds remain unminified.
- Build configuration (app id/namespace, SDK levels, version code/name, ABI filters, NDK version, and buildDir) is now sourced from `platform/android/gradle.properties` via `-Popendroidpdf.*` overrides. Example: `./gradlew assembleRelease -Popendroidpdf.versionCode=98 -Popendroidpdf.versionName=1.3.37 -Popendroidpdf.abi=arm64-v8a`.
- Default build output path is still `/mnt/subtitled/opendroidpdf-android-build`; adjust with `-Popendroidpdf.buildDir=/custom/path` if space is tight.

### Widget/dialog AsyncTasks now live in Kotlin controllers
- `core/WidgetController.kt` gained async helpers (text, choice, widget-area loading) wrapped in `WidgetJob`, and `core/SignatureController.kt` added async signing/report helpers. `MuPDFPageView` no longer instantiates `AsyncTask` for widget/text/signature flows; instead it asks the controllers for cancellable jobs so both UI and instrumentation layers stay on the façade and releaseResources() only talks to controller jobs.
- Search orchestration dropped the bespoke `AsyncTask`: `core/SearchController` now exposes `startSearch(...)` which runs the page sweep on a single-thread executor, posts callbacks to the main thread, and returns a cancellable `SearchJob`. `SearchTaskManager` simply wires `SearchCallbacks` to the progress dialog, `onTextFound`, and `goToResult`, so no UI code touches `AsyncTask`/`MuPDFCore` directly anymore.

### Build + smoke
- `GRADLE_USER_HOME=/tmp/opendroidpdf-gradle ./gradlew assembleDebug` (platform/android) – **PASS** after the async controller migration.
- Genymotion Pixel 6 (`localhost:42865`) smoke: installed the new debug APK, launched `test_blank.pdf`, invoked the search toolbar (`adb shell input keyevent 84`, typed “search”, pressed Enter). Captured screenshots `tmp_phase4_async_smoke.png` + `tmp_phase4_async_search.png`, UI dump `tmp_phase4_async_dump.xml`, and log tail `tmp_phase4_async_logcat.txt` (no `AndroidRuntime`/`FATAL` entries) to confirm the new search runner doesn’t ANR and the doc view still renders.

### Annotation + ink AsyncTasks migrated to AnnotationController
- `core/AnnotationController.kt` owns the background executors for markup, text, ink, and delete operations, returning cancellable `AnnotationJob`s with optional `AnnotationCallback`s for UI updates.
- `MuPDFPageView` replaces the remaining annotation AsyncTasks (`mAddMarkupAnnotation`, `mAddTextAnnotation`, `mDeleteAnnotation`) with controller jobs so releasing the view simply cancels those jobs, and the legacy `awaitInkCommit()` shim is now a no-op because ink commits run synchronously via `MuPdfController`.
- The controller posts `loadAnnotations()` callbacks on the main thread, so annotation refreshes still happen immediately after the repository call completes without leaking `AsyncTask` instances.

### Pass-click/widget AsyncTask removal
- `core/WidgetController.kt` now exposes `passClickAsync(...)` plus a `WidgetPassClickCallback`, so widget hit-tests reuse the same executor + main-thread handler as other widget operations.
- `MuPDFPageView` drops the last pass-click `AsyncTask`, storing the cancellable `WidgetJob` instead. The visitor callbacks still drive text/choice/signature dialogs after the repository reports a result, and releasing the view simply cancels the job.

### PageView text/link/annotation loaders moved to DocumentContentController
- New `core/DocumentContentController` wraps the MuPdfController for text, link, and annotation loading, returning cancellable `DocumentJob`s that post results back on the main thread. PageView’s helper methods now request content through this façade instead of spinning `AsyncTask`s per view.
- `MuPDFPageView` passes the controller into the PageView base class, so reset/release simply cancels outstanding jobs; `loadText()` no longer risks recreating tasks per selection, and annotation/link refreshes are centralized.

### Genymotion smoke – annotation controller
- Reused the freshly built `OpenDroidPDF-debug.apk`; installed on Genymotion Pixel 6 (`localhost:42865`), launched `file:///sdcard/Download/test_blank.pdf` via `adb shell am start …`, tapped Draw (`adb shell input tap 968 80`), then opened the toolbar “INK COLOR” dialog (`adb shell input tap 875 80`).
- Captured screenshots `tmp_phase4_annotation_smoke.png` (document view) and `tmp_phase4_annotation_ink.png` (Ink color dialog) plus log snapshot `tmp_phase4_annotation_logcat.txt`. No `AndroidRuntime`/`FATAL` entries after the annotation controller swap, so async job removal didn’t regress the toolbar path. (Manual highlight/delete flows still to be exercised during the next interactive QA pass.)

### Genymotion smoke – pass-click cleanup
- Installed the latest debug APK, launched `test_blank.pdf` via intent, and captured screenshot `tmp_phase4_passclick_smoke.png` plus `tmp_phase4_passclick_logcat.txt`. No runtime errors surfaced while exercising the toolbar, so the WidgetController-backed pass-click flow is stable on-device (widget/contact forms to be tested once an interactive sample doc is available).

### Genymotion smoke – content controller
- After migrating PageView’s loaders, rebuilt/install the debug APK, launched `test_blank.pdf`, and grabbed `tmp_phase4_content_smoke.png` plus `tmp_phase4_content_logcat.txt`. No `AndroidRuntime` entries, so the DocumentContentController swap didn’t affect document rendering (text selection/link highlighting still draw once QA exercises them on richer PDFs).

### Alert/save AsyncTask retirement + text/link/widget smoke (2025-12-07)
- Alert handling now runs through `core/AlertController.kt`, which waits on `muPdfRepository.waitForAlert()` from a dedicated executor, posts dialog prompts on the main thread, and forwards button presses/cancellations via `alertController.reply(...)`. `OpenDroidPDFActivity` no longer allocates per-alert AsyncTasks or leaks waiters when pausing.
- Save/copy/export dialogs use the new `core/SaveController.kt` instead of the legacy `mSaveAsOrSaveTask`. The controller serializes `Callable<Exception?>` jobs on a shared executor, posts completions on the UI thread, and lets `callInBackgroundAndShowDialog(...)` cancel or chain operations deterministically.
- `platform/android/src/org/opendroidpdf/CancellableAsyncTask.java` now wraps a shared `ExecutorService` + main-thread `Handler`, so HQ patches/thumbnails keep cancellable jobs without Android’s deprecated `AsyncTask`. `MuPDFPageAdapter` prefetches page sizes on a single-thread executor guarded by `pageSizeLock`, and the dashboard thumbnail preview simply spawns a short-lived thread before calling `runOnUiThread`, eliminating the last direct `AsyncTask` usage in Java sources.
- Build: `GRADLE_USER_HOME=/tmp/opendroidpdf-gradle ./gradlew assembleDebug` (platform/android) – **PASS** post-refactor.
- Genymotion Pixel 6 @ `localhost:42865` smoke using richer PDFs:
  1. **Text selection** – `adb shell am start … two_page_sample.pdf`, long-press + drag (`adb shell input swipe 540 1500 880 900 400`). Evidence: `tmp_phase4_content_select.png`, `tmp_phase4_content_select_dump.xml`.
  2. **Link highlight** – Opened `annotation_link_text_popup.pdf`, tapped link targets twice (`adb shell input tap 540 1100` / `540 900`). Evidence: `tmp_phase4_content_link.png`, `tmp_phase4_content_link_dump.xml`.
  3. **Widget entry** – Opened `annotation_text_widget.pdf`, tapped the text widget (`adb shell input tap 540 1200`), typed `Sample` via `adb shell input text Sample`. Evidence: `tmp_phase4_content_widget.png`, `tmp_phase4_content_widget_dump.xml`.
  4. Captured log snapshot `tmp_phase4_content_logcat_full.txt`; no `AndroidRuntime`/`FATAL` entries or DocumentContentController cancellation warnings while mixing text selection, link, and widget interactions, so the single-thread executor keeps up under the new façade.

## Update – 2025-12-09

### Instrumentation/tests now consume MuPdfRepository + controllers
- `InkUndoInstrumentedTest`, `UndoWorkflowInstrumentedTest`, and `FontFallbackInstrumentedTest` instantiate `OpenDroidPDFCore` via `Uri.fromFile(...)`, wrap it with `MuPdfRepository`, and interact through `MuPdfController`. They no longer access `MuPDFCore` directly for ink annotations, undo assertions, or render checks, which keeps test coverage aligned with the façade.
- Undo/export verification now reopens the exported `Uri` through a fresh repository before counting annotations, mirroring how the app checks for dirty state after saves. Helper methods were updated to count annotations via `repository.loadAnnotations(...)` rather than touching JNI bindings.
- Widget/signature AsyncTasks in `MuPDFPageView` now route through new Kotlin helpers (`core/WidgetController.kt`, `core/SignatureController.kt`), so both instrumentation and UI code share the same Kotlin control surface.

### Build verification
- `GRADLE_USER_HOME=/tmp/opendroidpdf-gradle ./gradlew assembleDebug` (platform/android) – **PASS** after the test refactor; confirms the new Kotlin controllers and repository-backed tests compile against the façade.
- Release build/deploy: `GRADLE_USER_HOME=/tmp/opendroidpdf-gradle ./gradlew clean assembleRelease` → `zipalign -f -p 4 …/OpenDroidPDF-release-unsigned.apk …-aligned.apk` → `apksigner sign --ks ~/fdroid/keystore.jks --ks-key-alias fdroidrepo … --out ~/fdroid/repo/org.opendroidpdf_97.apk`. Ran `/home/arch/fdroid/scripts/update_and_deploy.sh`, then verified `curl -fsSL https://fdroid.uh-oh.wtf/repo/index-v1.json` reports `versionName=1.3.36 versionCode=97` and captured SHA-256 `e1c230be3abacaa534e4facdaf7184214269ebe98e4efdcffc16dec63564b416` for the published APK.
- Smoke (Pixel 6 @ `localhost:42865`): launched `test_blank.pdf`, entered draw mode, and opened “Ink Color” via the toolbar; screenshots `tmp_phase4_repo_smoke4.png` + `tmp_phase4_repo_inkdialog3.png`, UI dump `tmp_phase4_repo_inkdialog3.xml`, and log snippet `tmp_phase4_repo_logcat3.txt` show the dialog rendering without crashes on the controller-backed build.

## Update – 2025-12-08

### Rendering/patch + pass-click flow owned by MuPdfController
- `MuPDFPageAdapter` and `MuPDFPageView` now receive only `MuPdfController`; page counts/sizes, widget hits, and annotation lists all funnel through the façade so neither class touches `MuPDFCore` directly.
- `MuPDFPageView`’s render/update paths (HQ patches, undo refresh, `passClickEvent`) call `MuPdfController`, which in turn invokes `MuPdfRepository` helpers (`drawPage()`, `updatePage()`, `passClick()`, `links()`, `newRenderCookie()`). The legacy inner `PassClickResult*` classes plus `SignatureState` now live in standalone files so both the repository and the page view reuse them.
- `MuPDFCancellableTaskDefinition` obtains cookies from the repository, letting both the thumbnail generator and HQ patch tasks cancel/destroy cookies without instantiating `MuPDFCore`. Recent-file thumbnail rendering now guards against a null façade before scheduling background work.

### Genymotion smoke – Ink Color via controller-backed toolbar
- Build: `GRADLE_USER_HOME=/tmp/opendroidpdf-gradle ./gradlew assembleDebug` (platform/android) – **PASS** after cleaning the Gradle/NDK outputs.
- Device: Genymotion Pixel 6 (Android 13, `localhost:42865`); copied `test_blank.pdf` to `/sdcard/Download/test_blank.pdf`.
- Steps: `adb shell am start -n org.opendroidpdf/.OpenDroidPDFActivity -d file:///sdcard/Download/test_blank.pdf`, tapped Draw (`adb shell input tap 968 77`), then Ink Color (`adb shell input tap 875 77`). Captured screenshot `tmp_phase4_repo_inkdialog2.png` plus hierarchy dump `tmp_phase4_repo_inkdialog2.xml` showing the “Ink color” dialog, and log snapshot `tmp_phase4_repo_logcat_afterink2.txt` (no `AndroidRuntime`/`FATAL EXCEPTION` entries). Additional doc-view screenshot stored as `tmp_phase4_repo_smoke3.png`.

### Search/thumbnail flows on Kotlin controllers
- Introduced `core/SearchController` so `SearchTaskManager` no longer wraps `MuPDFCore` directly; `OpenDroidPDFActivity` now instantiates the controller alongside `MuPdfController`, and the search task receives it instead of building ad-hoc repositories. This keeps AsyncTask plumbing on the façade while we decide how to modernize the UI flow.
- `PdfThumbnailManager` now renders via `MuPdfController` (capturing page size up front) rather than talking to `OpenDroidPDFCore` directly, so thumbnail generation benefits from the same cookie lifecycle used elsewhere.

### Release 1.3.35 (96) deployed via self-hosted F-Droid
- Version bump: `platform/android/build.gradle` + `AndroidManifest.xml` now advertise `versionName 1.3.35`, `versionCode 96`; both the repo copy and `/home/arch/fdroid/metadata/org.opendroidpdf.yml` were updated in lockstep so deployment scripts ingest the new build metadata (`Builds[0]` now references the 1.3.35 artifact).
- Build/sign: `GRADLE_USER_HOME=/tmp/opendroidpdf-gradle ./gradlew clean assembleRelease` (platform/android) generated `/mnt/subtitled/opendroidpdf-android-build/outputs/apk/release/OpenDroidPDF-release-unsigned.apk`, which was aligned (`zipalign -f -p 4 … unsigned.apk … aligned.apk`) and signed with the fdroidrepo key (`apksigner sign --ks ~/fdroid/keystore.jks … --out ~/fdroid/repo/org.opendroidpdf_96.apk`). `apksigner verify --print-certs` confirms the SHA-256 `0b6530f0d3…824807` fingerprint.
- Deployment: `/home/arch/fdroid/scripts/update_and_deploy.sh` regenerated indexes, uploaded `org.opendroidpdf_96.apk`/`.idsig`, and invalidated the CloudFront cache. Remote verification via `curl -fsSL https://fdroid.uh-oh.wtf/repo/index-v1.json | jq …` reports `versionName=1.3.35 versionCode=96 apk=org.opendroidpdf_96.apk`.
- Checksum: downloaded `https://fdroid.uh-oh.wtf/repo/org.opendroidpdf_96.apk` to `/tmp/org.opendroidpdf_96.apk` and recorded `sha256sum = e496d8a8bdae524868c4a25755e79e50d051a8891af3eeebf8a7a2ed645b54a0` for reference.

## Update – 2025-12-07

### Widget/text annotation façade + MuPDF controller
- `MuPdfRepository` now exposes annotation/widget helpers (`loadAnnotations()`, `addMarkupAnnotation()`, `addTextAnnotation()`, `deleteAnnotation()`, `getWidgetAreas()`, `setWidgetText()`, `setWidgetChoice()`, `checkFocusedSignature()`, `signFocusedSignature()`, `javascriptSupported()`).  The new Kotlin `core/MuPdfController` wraps those methods so UI code can stay unaware of `MuPDFCore` internals.
- `MuPDFPageView`/`MuPDFPageAdapter` accept the controller and no longer invoke `MuPDFCore` directly for annotation CRUD, ink commits, widget text/choice updates, or signature prompts.  Undo snapshots/commits run through the controller (`markDocumentDirty()`), so repository state stays consistent with annotation operations triggered from the page view.
- `OpenDroidPDFActivity#setCoreInstance()` instantiates both the repository and controller, and the adapter is recreated with the façade whenever the document view resets.

### MuPdfRepository adoption – export/share + annotation flows
- `org.opendroidpdf.core.MuPdfRepository` now wraps the remaining document toolbar actions: insert blank page, export/share, save-in-place, and ink annotation helpers all route through new façade methods (`insertBlankPageAtEnd()`, `exportDocument()`, `addInkAnnotation()`, `markDocumentDirty()`, `refreshAnnotationAppearance()`). `OpenDroidPDFActivity` delegates toolbar events to the repository, so the UI no longer calls `MuPDFCore` directly for share/print/save or ink commits.
- `shareDoc()` and `printDoc()` now issue exports via the repository and reuse `currentDocumentName()` for chooser labels/print job titles, ensuring pending ink is committed before invoking the façade.
- `commitPendingInkToCoreBlocking()` hands pending strokes to `MuPdfRepository` (which marks the document dirty and refreshes annotation appearance streams) before triggering UI refresh, so annotation flows remain consistent regardless of the caller (toolbar buttons, autotest, or background save).

### Genymotion smoke – toolbar ink color via repository
- Build: `GRADLE_USER_HOME=/tmp/opendroidpdf-gradle ./gradlew assembleDebug` (platform/android) – **PASS**; resulting APK `OpenDroidPDF-debug.apk` installed with `adb -s localhost:42865 install -t -r …`.
- Device: Genymotion Pixel 6 (Android 13) @ `localhost:42865`; staged `test_blank.pdf` to `/sdcard/Download/`.
- Steps: `adb shell am start … file:///sdcard/Download/test_blank.pdf`, toggled draw mode (`adb shell input tap 970 80`), opened toolbar “INK COLOR” (`adb shell input tap 875 80`). Captured screenshots `tmp_phase4_repo_smoke2.png` + `tmp_phase4_repo_inkdialog.png`, UI dumps `tmp_phase4_repo_dump2.xml` + `tmp_phase4_repo_inkdialog.xml`, and log snippet `tmp_phase4_repo_logcat_afterink.txt` (filtered `logcat -d OpenDroidPDFActivity:D MuPDFPageView:D *:S`) – no crashes or `AndroidRuntime` entries observed.

## Update – 2025-12-02

### Phase 4 JNI split – native modules + verification
- `platform/android/jni/mupdf.c` is now fully decomposed: document/session glue (`document_io.c`), rendering (`render.c`), ink/pen helpers (`ink.c`), text selection/export (`text_selection.c`, `export_share.c`), annotation utilities (`text_annot.c`), widget/forms glue (`widgets.c`), signature helpers (`widgets_signature.c`), and shared helpers (`utils.c`) all include `mupdf_native.h`, and `Android.mk` compiles the new units directly. `MuPDFCore_gotoPageInternal` is declared in the header so modules can jump pages without relying on implicit prototypes.
- JVM façade groundwork: `org.opendroidpdf.core.MuPdfRepository` now wraps common `MuPDFCore` entry points (search/text/html/export/save), so upper layers can begin targeting the façade instead of JNI-bound classes directly.
- Alerts/cookie/proof/separation helpers now live in their own compilation units (`alerts.c`, `cookies.c`, `proof.c`, `separations.c`), so `document_io.c` only handles document/session lifecycle and `utils.c` shrank back to cross-cutting helpers. `Android.mk` was updated accordingly after freeing ~1.6 GB by deleting `/mnt/subtitled/opendroidpdf-android-build` (release artifacts can be regenerated on demand).
- Build: `./gradlew assembleDebug` (platform/android) – **PASS** after the split, covering `arm64-v8a` and `armeabi-v7a` ndk-build invocations. NDK warnings are unchanged (OpenSSL static libs).
- Smoke (2025-12-02, Genymotion Pixel 6 @ `localhost:42865`): pushed `test_blank.pdf` to `/sdcard/Download/`, launched via `adb shell am start … file:///sdcard/Download/test_blank.pdf`, toggled draw mode (`adb shell input tap 970 80`), and opened the toolbar “INK COLOR” dialog (`adb shell input tap 540 1900`). Screenshots `tmp_phase4_doc.png`, `tmp_phase4_ink_dialog_after2.png`, and dumps `ink_color_after2.xml` confirm the UI renders, while `tmp_phase4_logcat_after2.txt` contains no `AndroidRuntime` or signal entries. The earlier crash (log `tmp_phase4_logcat_after.txt`) was due to returning the wrong array rank in `MuPDFCore_text`; restoring the exact upstream implementation in `text_selection.c` resolved it.

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

## Update – 2025-12-08
- UI scheduling now runs through `AppCoroutines` (main/IO scopes plus lifecycle helpers) instead of ad-hoc Handlers/shared executors. File browser/note browser refreshes, long-press gestures, progress indicators, and intent routing all post via coroutines; lifecycle scopes take care of cleanup when fragments/views detach.

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

## Quick Emulator Smoke (`scripts/geny_smoke.sh`)
- Date: 2025-12-15, Device: Genymotion Pixel 6 @ `localhost:42865`
- Flow: install debug → grant storage → open `test_blank.pdf` → draw → undo → search → share tap
- Result: **PASS** (logcat snippet captured by script); confirms search/drawing/export wiring still good after ServiceLocator interface additions.

## Release build sanity (Phase 5)
- Date: 2025-12-15
- Command: `./gradlew assembleRelease -x lint` (from `platform/android/`)
- Result: **PASS** with R8/shrink on; warnings limited to legacy MuPDF NDK macros/format strings.
- Notes: :app and :core now share `gradle/core-sources.gradle`; deploy helper `scripts/fdroid_build.sh` consumes `scripts/fdroid.env` for buildDir/ABI/signing defaults.

## Instrumented UI Smoke
- Command: `ANDROID_SERIAL=localhost:42865 ./gradlew connectedDebugAndroidTest`  
  - Result: **Blocked**  
    - Failure: `INSTALL_FAILED_UPDATE_INCOMPATIBLE` when Gradle tries to push the debug APK to the Genymotion device. The device has the signed F-Droid build installed, which uses a different signing key.
    - Next action: Uninstall the production build (`adb -s localhost:42865 shell pm uninstall org.opendroidpdf`) before running tests, or install/run tests on a fresh emulator image. If MANAGE_EXTERNAL_STORAGE was granted manually, it will need to be re-applied after reinstall.

## Manual Pen / Color / Text Workflow
- Not executed yet (blocked by the same signature conflict).  
- Suggested checklist is preserved in `docs/housekeeping/fdroid_rebrand_notes.md` for quick follow-up once the device is reset.

## Update – 2025-12-21

### Builds
- `./gradlew testDebugUnitTest assembleDebug -x lint` (from `platform/android/`) – **PASS**

### Quick Emulator Smokes
- Device: Genymotion Pixel 6 (Android 13) @ `localhost:42865`
- `scripts/geny_smoke.sh` (PDF open → draw → undo → search → share) – **PASS**
- `scripts/geny_pdf_save_embeds_ink_smoke.sh` (writable PDF: draw → accept → Save → pull + `pdftoppm` render diff) – **PASS**
- `scripts/geny_epub_smoke.sh` (EPUB open → settings → note/draw/undo + DB assertions) – **PASS**
- `scripts/geny_epub_drm_smoke.sh` (DRM/encrypted EPUB → specific error dialog) – **PASS**
