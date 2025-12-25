OpenDroidPDF – Codebase Simplification Plan (Revised Dec 20, 2025)
==================================================================

Purpose
Make the project easier to understand, change, and ship by simplifying structure—not just shrinking files. Every step reduces coupling, clarifies ownership, and removes redundancy while keeping behavior stable and builds green.

What “simpler” means here (detailed goals)
- Clear layering: screens/fragments orchestrate only; controllers implement flows; services own cross-cutting capabilities; core/adapters talk to MuPDF/native.
- Explicit dependencies: no hidden singletons, no Activity lookups from deep layers. Dependencies are constructor-provided or scope-provided via a small, typed locator.
- Small, named contracts: APIs are capability-oriented (Drawing, Export, Search, Permissions, Recents), not “where code lives.”
- Reader pipeline clarity: ReaderView lays out children and hosts; PageView renders; gesture/selection/drawing logic lives in dedicated routers/controllers. Avoid duplicated state across view/controller.
- Annotation pipeline clarity: one tool pipeline across PDF/EPUB; persistence backends are swappable (PDF-in-file vs sidecar) without tool logic forking.
- Build hygiene: clean :app / :core split; deterministic Gradle/R8; F-Droid scripts pull config from one place.
- Safety net: after each refactor slice, run a fast smoke (open → draw → undo → search → export) and keep a baseline log.

Non-goals / constraints
- Do not delete user data or untracked assets without explicit approval.
- No broad style rewrites or Kotlin-first conversions unless they serve the above goals.
- Keep F-Droid deployment intact after each structural change; version bumps only when shipping behavior changes.

Guiding principles
- One layer, one job: UI orchestrates; controllers implement flows; core/repo talks to MuPDF/native; utilities stay pure.
- Explicit scope: app-scope vs document-scope vs view-scope objects are separated and never leaked across scopes.
- Stable boundaries: capability interfaces are small and named; no “misc helper” buckets.
- Safety net: quick emulator smoke + targeted tests after each slice; keep F-Droid pipeline working.

Progress tracking (living)
- This plan is a living checklist: after each slice, update this section with:
  - date (YYYY-MM-DD),
  - short description of the slice,
  - the commit(s) that landed it,
  - the smoke scripts run (and whether they passed),
  - and any follow-ups created.
- Canonical references this plan expects to stay aligned with code:
  - `docs/architecture.md` (ownership map + dependency direction)
  - `docs/transition.md` (migration/compatibility notes)
  - `docs/housekeeping/baseline_smoke.md` (dated smoke log)

Status dashboard (as of 2025-12-24)
- EPUB track (E0–E5):
  - [x] E0 Plumbing (open/gating/intent filters)
  - [x] E1 Reading baseline (TOC + reading settings + theme paint-only)
  - [x] E2 Sidecar annotations (SQLite store + session + overlay rendering)
  - [x] E3 Export annotated PDF for sidecar docs (flatten)
  - [x] E4 PDF “Save vs export” robustness (Save embeds ink + permission downgrade; export prefers embed with flatten fallback; includes SAF/DocumentsUI coverage)
  - [x] E5 Text-anchored EPUB highlights (v2: persist reflow location + word-range anchor + quote context; re-anchor across relayouts).
- Core refactor track (Phases 1–7):
  - [x] Phase 1 (baseline): publish ownership map + hotspots (`docs/architecture.md`) and keep it current via follow-up slices (e.g., `3932f81c`).
  - [x] Phase 2: finish slimming `OpenDroidPDFActivity` into a thin host (life-cycle + wiring only).
  - [ ] Phase 3: keep moving gesture/selection/drawing plumbing out of `MuPDFReaderView` / `MuPDFPageView` into dedicated routers/controllers.
  - [x] Phase 4 (baseline): unified annotation pipeline + persistence backends (PDF commit vs sidecar) and shared overlay rendering (E2–E5).
  - [ ] Phase 5: continue tightening service interfaces + data flow ownership (remove remaining “helper”/misc ownership and cycles).
  - [ ] Phase 6: complete build/config simplification (deterministic `:core`/`:app` split + one config source for deploy scripts). NOTE: removed the `core-sources.gradle` include/exclude hack (`c93fc02c`).
  - [ ] Phase 7: keep quality/docs aligned (`--warning-mode all`, lint noise, and bring `platform/android/ClassStructure.txt` back in sync).

Recent progress
- 2025-12-25: Phase 3 slice: slimmed `platform/android/src/org/opendroidpdf/MuPDFReaderView.java` toward “paging/child management” by deleting legacy mode helper methods (`switchTo*`/`is*Active`) and removing the unused `SearchResultsController` exposure; updated `platform/android/src/org/opendroidpdf/app/services/DrawingServiceImpl.java` to set/view `ReaderMode` via `setMode/getMode`, and updated `platform/android/src/org/opendroidpdf/app/hosts/DocumentToolbarHostAdapter.java` to enter search mode via `requestMode(SEARCHING)`. Commit: `6a6c2aa2`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Phase 3 slice: removed SharedPreferences reads from view classes by making `platform/android/src/org/opendroidpdf/app/preferences/EditorPreferences.java` snapshot/provider-backed (no Context/AppServices), caching pen/editor snapshots in `platform/android/src/org/opendroidpdf/app/preferences/PreferencesCoordinator.java`, and threading `EditorPreferences` through `platform/android/src/org/opendroidpdf/app/document/DocumentViewDelegate.java` → `platform/android/src/org/opendroidpdf/MuPDFPageAdapter.java` → `platform/android/src/org/opendroidpdf/app/reader/ReaderComposition.java` → `platform/android/src/org/opendroidpdf/PageView.java`/`platform/android/src/org/opendroidpdf/app/overlay/PageOverlayView.java`. Commit: `6b219275`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Phase 3 slice: made `PageState` the single owner of min-zoom page layout by removing redundant `mSize`/`mSourceScale` fields from `PageView` and extracting the min-zoom computation into `platform/android/src/org/opendroidpdf/app/reader/PageMinZoomCalculator.java`. Commit: `4a2376cd`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Phase 3 slice: routed PageView’s draw/erase gesture pipeline through `InkController` (MuPDFPageView now delegates `start/continue/finish/cancel` for draw + erase to ink), capturing ink/eraser thickness once per gesture and centralizing undo-cache updates. Commit: `1960acd2`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Phase 3 slice: extracted `MuPDFReaderView` interaction state (mode/links/search/gesture delegation) into `platform/android/src/org/opendroidpdf/app/reader/MuPDFReaderInteractionController.java` so `MuPDFReaderView` stays focused on paging/child management. Commit: `d6fdb6d5`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Phase 3 slice: moved `EditorPreferences` construction out of `PageView` and into `ReaderComposition`, so all `MuPDFPageView` instances for a document share one preferences access surface (reduces per-page view allocations and keeps view code thinner). Commit: `82e87916`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Phase 3 slice: removed `MuPDFPageView` → parent view casting for text-annotation prompts by routing “edit/add text annotation” requests through a per-document `TextAnnotationRequester` on `ReaderComposition` (wired by `MuPDFReaderView` when setting the `MuPDFPageAdapter`). Commit: `2c64e163`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Phase 3 slice: removed `MuPDFPageView` → `MuPDFReaderView` mode-coupling by routing “request drawing/erasing mode” through a per-document `ReaderModeRequester` on `ReaderComposition` (wired by `MuPDFReaderView` when setting the `MuPDFPageAdapter`). Commit: `9f5f9a01`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Phase 3 slice: made `GestureRouter` the only owner of gesture detectors by removing `ReaderView`’s redundant `GestureDetector`/`ScaleGestureDetector` fields and eliminating the “router nullable” fallback paths. Commit: `3bb9a87d`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Phase 3 slice: moved `ReaderView`’s `GestureRouter.Host` implementation into the gesture zone as `platform/android/src/org/opendroidpdf/app/reader/gesture/ReaderViewGestureHost.java`, keeping `ReaderView`’s internal host bridge helpers package-private via a narrow `ViewBridge` interface. Commit: `787399ae`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Phase 3 slice: moved reader gesture/selection/drawing plumbing into the dedicated gesture zone (`platform/android/src/org/opendroidpdf/app/reader/gesture/`) to make gesture ownership explicit and reduce `org.opendroidpdf` package-private coupling. Introduced a public `platform/android/src/org/opendroidpdf/Hit.java` enum (replacing the old package-private `Hit` inside `MuPDFView.java`) and a public `platform/android/src/org/opendroidpdf/app/reader/gesture/ReaderMode.java` wrapper so gesture code does not depend on `MuPDFReaderView.Mode`. Commit: `f68d3986`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Phase 3 slice: fixed link-tap navigation regression by ensuring `TapGestureRouter` receives the active `MuPDFReaderView` instance (via `ReaderGestureController`’s `Host.rootView()`), so link taps can route to `LinkTapHandler` without needing the reader view enum/exposed state. Commit: `055fd89c`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Phase 2 closure slice: removed remaining feature/controller references to `OpenDroidPDFActivity` by introducing narrow `Host` contracts and adapters (including `ActivityCompositionHostAdapter` and a centralized `DocumentViewerIntents` helper for in-app VIEW intents); kept `MuPDFReaderView.Mode` package-private by adding a public `switchToSearchingMode()` wrapper. Commit: `6b92c4a5`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**). Verified: `rg -n "\\bOpenDroidPDFActivity\\b" platform/android/src` only matches the activity and `app/hosts/`.
- 2025-12-24: Phase 2 slice: introduced narrow host adapters under `platform/android/src/org/opendroidpdf/app/hosts/` for document lifecycle/setup, back press, intent resume, and debug actions; controllers now depend on `Host` interfaces rather than `OpenDroidPDFActivity`, leaving the activity as a delegating host (including the new `DashboardHost` methods). Commit: `d255ca25`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Removed the concrete activity dependency from `platform/android/src/org/opendroidpdf/app/document/DocumentViewDelegate.java` by introducing `DocumentViewDelegate.Host` and implementing it in `platform/android/src/org/opendroidpdf/app/hosts/DocumentViewDelegateHostAdapter.java`; updated `platform/android/src/org/opendroidpdf/app/lifecycle/ActivityComposition.java` wiring accordingly. Commit: `8f5aa3b2`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Decoupled navigation from the concrete activity type by removing the `OpenDroidPDFActivity` dependency from `platform/android/src/org/opendroidpdf/app/document/DocumentNavigationController.java` (now host/context-driven) and `platform/android/src/org/opendroidpdf/app/navigation/NavigationDelegate.java` (no longer stores the activity). Commit: `ca7e4de4`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Routed toolbar “truth” to the real owners by removing `OpenDroidPDFActivity` toolbar-state helpers (draw/erase mode + selected-annotation editability) and wiring `ToolbarHostProvider` to read drawing state from `DrawingService` and selection editability from `DocumentViewHostAdapter` (also removed unused `ToolbarHostAdapter.Provider.currentPageView`). Commit: `99b325ed`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Centralized “current doc/view” helpers (document type + current page view + sidecar provider) in `platform/android/src/org/opendroidpdf/app/hosts/DocumentViewHostAdapter.java` and removed those helpers from `platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java`; rewired `DocumentViewDelegate` and host adapters (`ExportHostAdapter`, `ViewportHostAdapter`, `SaveUiHostAdapter`, `DocumentSetupHostAdapter`, `DocumentToolbarHostAdapter`, `ToolbarHostProvider`) to depend on the adapter so activity stays a thin host. Commit: `4b90e5af`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Removed duplicate link hit-testing logic from `platform/android/src/org/opendroidpdf/MuPDFPageView.java` by delegating `hitLink(...)` to `platform/android/src/org/opendroidpdf/PageHitRouter.java` (new `hitLink(viewX, viewY)`), so link-hit ownership lives with the page hit router. Commit: `e6dcf3ef`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Extracted embedded-vs-sidecar selection action routing (delete/edit/deselect/editability) out of `platform/android/src/org/opendroidpdf/MuPDFPageView.java` into `platform/android/src/org/opendroidpdf/PageSelectionCoordinator.java` so the view delegates rather than branching on selection ownership. Commit: `4081b3b5`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Extracted the embedded+sidecar page-tap hit-routing (`passClickEvent` / `clickWouldHit`) out of `platform/android/src/org/opendroidpdf/MuPDFPageView.java` into `platform/android/src/org/opendroidpdf/PageTapHitRouter.java` so the view delegates rather than coordinating embedded-vs-sidecar tap logic. Commit: `e1203482`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Moved sidecar selection actions (delete/edit + editability) into `platform/android/src/org/opendroidpdf/app/selection/SidecarSelectionController.java` so `platform/android/src/org/opendroidpdf/MuPDFPageView.java` delegates and sidecar notes can be edited from toolbar/gesture entry points (not only “tap twice”). Commit: `4f264ee6`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-24: Extracted sidecar note/highlight hit-testing + selection state out of `platform/android/src/org/opendroidpdf/MuPDFPageView.java` into `platform/android/src/org/opendroidpdf/app/selection/SidecarSelectionController.java` so sidecar selection has one owner and the view delegates. Commit: `2b656b7e`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-23: Moved “reopen for edit (write permission)” SAF launch behavior out of `OpenDroidPDFActivity` into `platform/android/src/org/opendroidpdf/app/hosts/DocumentAccessHostAdapter.java` and wired it through `ActivityComposition` so `SaveUiHostAdapter`/`DocumentSetupHostAdapter` can request write-permissions without activity-owned helper methods. Commit: `3a7c17df`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-23: Storage-permission rationale dialog state is now owned solely by `StoragePermissionController` (no activity wrapper methods), and the unused MANAGE_EXTERNAL_STORAGE “awaiting” flag/state was removed. Commit: `18102d6b`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-23: Centralized activity request/permission codes in `platform/android/src/org/opendroidpdf/app/helpers/RequestCodes.java` and fixed the signature/image file-pick flow by routing `FILE_PICK` results through `ActivityResultRouter` into `platform/android/src/org/opendroidpdf/FilePickerCoordinator.java` (removing request-code fields/getters and pending picker state from `OpenDroidPDFActivity`). Commit: `6fbd00ed`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-23: Moved document-scoped state (DocumentIdentity + transient “Save failed” override) out of `OpenDroidPDFActivity` into `DocumentLifecycleManager` so the doc lifecycle is the single owner and the activity is a thin host. Commit: `7823c302`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-23: Refreshed `platform/android/ClassStructure.txt` to match current ownership taxonomy and aligned docs to the real wiring entry points (`ActivityComposition` + `AppServices`). Commit: `1b786af7`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-23: Sidecar highlights now persist a stable reflow range anchor (`reflow_location` + `anchor_start_word`/`anchor_end_word_excl`) alongside quote context. Re-anchoring targets the reflow location first and disambiguates repeated quotes by word-start proximity. Commit: `ce2c34ea`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh`, `scripts/geny_epub_highlight_reanchor_smoke.sh` (**PASS**).
- 2025-12-23: EPUB viewport restore now prefers MuPDF reflow `fz_location` whenever present (including cold start before the active reflow layout profile id is available). Hardened `scripts/geny_epub_viewport_restore_smoke.sh` to mutate stored prefs and force location-only restore (clears `docprogress`/`page`, forces bogus `layoutProfileId`). Commit: `a898ad6d`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh`, `scripts/geny_epub_viewport_restore_smoke.sh` (**PASS**).
- 2025-12-23: EPUB viewport restore now persists MuPDF reflow `fz_location` (chapter/page) in `ViewportSnapshot` and restores by location when reflow layout mismatches (fallback: docProgress01). Genymotion smokes now auto-detect the adb serial when `DEVICE` is unset. Commit: `d5d6442e`. Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**). Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh`, `scripts/geny_epub_viewport_restore_smoke.sh`, `scripts/geny_epub_edge_relayout_smoke.sh` (**PASS**).
- 2025-12-22: Hardened Genymotion UI helpers to retry flaky `uiautomator dump` runs and wait for the document view container before proceeding (reduces smoke flakiness). Commit: `868f88a7`. Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**).
- 2025-12-22: Unblocked `./gradlew lintDebug` by fixing `LongLogTag` (shortened `TAG` constants) and adding missing `de`/`es` translations for the PDF read-only banner + “Enable saving” action. Commit: `faaaf232`. Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**). Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug lintDebug` (**PASS**).
- 2025-12-22: Added a Genymotion smoke that opens a PDF via the system SAF picker (DocumentsUI / `ACTION_OPEN_DOCUMENT`, i.e. real `content://` Uris), draws ink, saves in-place, pulls the underlying file, and asserts an external render changed (proves embedded marks). Commit: `a8799933`. Smokes: `scripts/geny_pdf_documents_uri_save_smoke.sh` (**PASS**). Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**).
- 2025-12-22: Cached `canSaveToCurrentUri` in `DocumentLifecycleManager` (preflight once + refresh after Save As) to avoid repeated ContentResolver/SAF probes during menu updates; moved `SaveUiController` save success callbacks (`onSaveCompleted`/`onSaveAsCompleted`) onto the main thread; added `OpenDroidPDFActivity.refreshSaveCapabilityCache()` so Save-As updates the Save-enabled UI deterministically. Commit: `9ac211f1`. Smokes: `scripts/geny_smoke.sh`, `scripts/geny_save_permission_downgrade_smoke.sh`, `scripts/geny_pdf_save_embeds_ink_smoke.sh`, `scripts/geny_pdf_readonly_export_smoke.sh` (**PASS**). Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**).
- 2025-12-22: Centralized “reopen for edit (write permission)” launching (later moved into `platform/android/src/org/opendroidpdf/app/hosts/DocumentAccessHostAdapter.java`) and removed duplicate launch logic from `DocumentSetupHostAdapter` and `SaveUiHostAdapter`. Commit: `d25a4332`. Smokes: `scripts/geny_smoke.sh`, `scripts/geny_save_permission_downgrade_smoke.sh`, `scripts/geny_pdf_readonly_export_smoke.sh` (**PASS**).
- 2025-12-22: Hardened PDF sidecar export smoke to assert exported PDFs preserve extractable text (via `pdftotext`), not just a file-size heuristic. Commit: `8f3aa35b`. Smokes: `scripts/geny_pdf_readonly_export_smoke.sh` (**PASS**).
- 2025-12-22: Centralized SAF open/create-document intent shapes (MIME filters + persistable grant flags) in `DocumentAccessIntents`; rewired `DocumentNavigationController` + `OpenDroidPDFFileChooser` to use it for consistency. Commits: `65d60f5b`, `24a75786`, `e74e9e8d`. Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh`, `scripts/geny_save_permission_downgrade_smoke.sh` (**PASS**).
- 2025-12-22: Moved `DrawingController` into `:core` and removed the `gradle/core-sources.gradle` include/exclude hack so `:core` builds only from `core/src/main/java`. Commit: `c93fc02c`. Smokes: `scripts/geny_smoke.sh`, `scripts/geny_epub_smoke.sh` (**PASS**). Build: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` (**PASS**).
- 2025-12-21: Content-based document identity (`sha256:*`) + migrations across sidecar/recents/viewport/reflow prefs; added rename-stability smoke (`scripts/geny_docid_rename_smoke.sh`). Commits: `e080a1c6`, `84b69335`.
- 2025-12-21: Updated `docs/architecture.md` + `docs/transition.md` to reflect sidecar/docId/EPUB behavior. Commit: `3932f81c`.
- 2025-12-21: Added edge reflow EPUB fixture (`test_assets/edge.epub`) + relayout smoke (`scripts/geny_epub_edge_relayout_smoke.sh`). Commit: `be24b826`. Smokes: `scripts/geny_epub_edge_relayout_smoke.sh` (**PASS**).
- 2025-12-21: Sidecar highlights now persist TextQuoteSelector context (`quote_prefix`/`quote_suffix`) and re-anchor via extracted page text (not native search). Commit: `10a31431`. Smokes: `scripts/geny_epub_highlight_reanchor_smoke.sh`, `scripts/geny_epub_smoke.sh`, `scripts/geny_smoke.sh` (**PASS**).

Ownership taxonomy (canonical zones)
- Activity host: lifecycle + top-level navigation only (`OpenDroidPDFActivity`).
- Navigation/intents: open/close/export entry points (`IntentRouter`, `DocumentNavigationController`).
- Toolbar/UI state: menu visibility/enabled rules and search/annot toggles (`ToolbarStateController`).
- Gesture & interaction: tap/selection/scroll/pinch routers plus gesture state (`GestureRouter`, `TapGestureRouter`, `SelectionGestureHandler`, `GestureStateHelper`).
- Reader views: layout/render containers only (`MuPDFReaderView`, `MuPDFPageView`, `PageView`) with geometry helpers (`ReaderGeometry`, `NormalizedScroll`).
- Annotation tools + flows: tool state, undo/redo, dialogs/widgets (`AnnotationController`, `DrawingController`, widget/signature controllers).
- Export/share: save/print/share prompts and actions (`ExportController`).
- Permissions: storage/runtime permissions and rationales (`StoragePermissionHelper`).
- Preferences: scoped settings access + migrations (`PreferencesRepository`).
- Services/wiring: `ActivityComposition` wires activity-scoped controllers/adapters and `AppServices` provides app-scoped stores/services; no generic “misc helper” buckets.

Dependency rules (enforced)
- Directional only: Activity → controllers/services → views/core. Views must not reach into Activity.
- No cycles between controllers; ownership is singular (each concept has one home in the taxonomy above).
- Shared prefs/files accessed only through `PreferencesRepository` with documented migrations.
- Scoped object lifetime:
  - App-scope: navigation, permissions, global prefs, recent files index.
  - Document-scope: document session, annotation session, search session, layout profile, page cache.
  - View-scope: gesture state, transient UI bridges.
- The service locator may provide factories, but must not become an ambient global:
  - No `AppServices.get()` calls from low-level views/core (wire dependencies at composition boundaries).
  - Document-scope objects are created by a document composition root (your `ReaderComposition`) and passed down.

Phase 1 — Map & De-tangle
- Produce a current dependency/ownership map: UI → controllers → services → core/native.
- Identify globals/statics and shared prefs namespaces; plan replacements with scoped providers.
- Outcome: `docs/architecture.md` + list of highest-coupling hotspots with targeted refactor slices.

Phase 2 — Activity/Navigation Simplification
- Collapse navigation/share/export/permission flows behind dedicated services; activity becomes a thin host.
- Keep menu logic out of activity; activity delegates to `ToolbarStateController`.
- Outcome: `OpenDroidPDFActivity` wires UI and delegates; no inline flow logic.

Phase 3 — Reader Stack Simplification (ReaderView/PageView)
- Keep `MuPDFReaderView` responsible only for paging/child management.
- Route all gestures through routers; selection/drawing are controllers.
- Keep rendering/layout/content in controllers/models (e.g., `PageLayoutController`, `PageContentController`, `PageState`).
- Outcome: view → controller → core is explicit; no prefs reads or static drawing constants in views.

Phase 4 — Annotation Pipeline Unification (pre-req for EPUB)
This is the correction that prevents “two annotation systems.”
- Introduce a single tool-facing annotation pipeline:
  - Tools produce operations (ink/highlight/note/erase/undo).
  - Operations are applied to a document-scoped annotation session state.
- Split persistence into backends (document-scoped):
  - Backend A: PDF in-file persistence (commit on Save/export).
  - Backend B: sidecar persistence (always).
- Render path:
  - In-progress tool preview is always overlay.
  - Persisted annotations are rendered through a single “annotation snapshot” interface so EPUB/PDF don’t fork tool code.
- Outcome: tool code does not branch on PDF vs EPUB; only persistence/export policy changes.

Phase 5 — Services & Data Flow
- Define small service interfaces (Drawing, Search, Export, PenPreferences, RecentFiles, DocumentSession).
- Move data holders shared by app/core into :core; keep UI-only models in :app.
- Remove duplicate-class exclusions once stable; re-enable R8 deterministically.
- Outcome: controllers depend on interfaces; mocks are easy; no duplicate classes.

Phase 6 — Build & Config Simplification
- Clean Gradle split: :core holds pure Java + MuPDF adapters; :app holds Android/UI.
- Standardize build constants/env vars and deployment scripts (F-Droid) under `scripts/` with one config source.
- Outcome: `assembleDebug`/`assembleRelease` clean; no duplicates; deploy script uses consistent naming.

Phase 7 — Quality & Docs
- Run `--warning-mode all`; fix deprecations and noisy lint where quick wins exist.
- Keep `docs/architecture.md`, `docs/transition.md`, and `ClassStructure.txt` aligned with code structure and scopes.
- Outcome: docs match code; newcomers can follow layers without spelunking monoliths.

Immediate Next Actions (rolling)
1) Enforce scope rules in code review: app-scope vs doc-scope vs view-scope; no leaking doc controllers into app locator.
2) Finish Phase 4: ensure all tool paths (ink/highlight/note/undo/erase) go through the unified annotation session surface.
3) Keep `scripts/geny_smoke.sh` baseline: open → draw → undo → search → export; log outcomes in `docs/housekeeping/baseline_smoke.md`.
4) Keep “Vision QA” screenshots, but treat them as regression artifacts; correctness should be asserted from internal state + deterministic renders (see testing section below).

==========================================================
EPUB Support Track (Revised Dec 20, 2025)
==========================================================

Goal
Add DRM-free `.epub` viewing support without forking the codebase into “PDF mode vs EPUB mode” hacks.
Preserve “advanced PDF reader” expectations (PDF annotations saved into the file when possible) while enabling EPUB annotations via a reliable sidecar overlay + explicit export to PDF.

Decisions (locked in)
- EPUB reading scope (v1): open/read + TOC + font size + theme (light/dark/sepia) + basic margins/line spacing. Defer user CSS and advanced typography knobs.
- DRM: detect and show a specific “DRM-protected EPUB is not supported” error (not a generic open failure).
- Annotation meaning:
  - EPUB: annotations are sidecar-only (overlay). No attempts to “write annotations into the EPUB file”.
  - Provide explicit “Export annotated PDF” for EPUB → flattened PDF with overlay marks.
  - Do not auto-convert EPUB → PDF on import (optional explicit “Import as PDF” later).
- Tools on EPUB:
  - Allow highlight + note always.
  - Allow ink only under a “layout lock” policy (see below).
- Anchoring (EPUB):
  - MVP uses layout-locked anchors (geometry + layout profile).
  - Later upgrade highlights to text-anchored.
- PDF persistence:
  - PDF writable: keep current behavior (write into PDF / standard Save).
  - PDF not writable: store sidecar and guide user; offer “Export annotated copy”.
- Sidecar doc identity: hybrid doc identity (prefer content-derived ID; fallback to URI+size+mtime); must survive rename/move.
- Sidecar storage: internal app storage by default; explicit export/import later.

High-level architecture (capabilities + data shapes)
- Document capabilities (determined at open):
  - `DocumentType`: PDF vs EPUB (later CBZ/XPS/IMG).
  - `WriteCapability`: writable-in-place vs not.
  - `ReflowCapability`: yes/no (EPUB yes).
  - `OutlineCapability`: yes/no.
- Reflow layout model (EPUB only):
  - `LayoutProfile` (layout-affecting fields only):
    - `pageWidthUnits`, `pageHeightUnits`
    - `fontSizeUnits`
    - `marginScale`, `lineSpacing`
    - (optional later) hyphenation toggle
  - `layoutProfileId`: stable hash/string derived ONLY from layout-affecting fields.
  - `Theme`: paint-only (light/dark/sepia) stored separately and must not influence `layoutProfileId`.
- Annotation model (format-agnostic):
  - `InkStroke(points[], brush{color,width,opacity}, createdAt, id)`
  - `Highlight(quads[]/rects[], color, opacity, optional textAnchor)`
  - `Note(anchor, text, id)`
  - Anchors:
    - PDF: `(pageIndex, page-space geometry)`
    - EPUB (MVP): `(pageIndex OR spineId+progression, layoutProfileId, geometry)`
- Annotation session (document-scoped):
  - In-memory state supports realtime ink, undo/redo, erase, selection, etc.
  - Persists to a backend chosen by policy (see next section).
- Persistence backends (document-scoped):
  - `SidecarBackend`: persists annotation state/ops to internal store (SQLite recommended below).
  - `PdfCommitBackend`: commits a delta of annotations into the PDF on Save/export (does not drive tool logic).
- Rendering:
  - Always render in-progress edits as overlay.
  - Persisted annotation rendering uses a single snapshot format (quads/strokes) so EPUB/PDF don’t fork the drawing pipeline.
  - Existing embedded PDF annotations can remain “native layer” for now (view-only) and are rendered separately until you decide to import them.

Canonical persistence policy (the practical “advanced reader” split)
- EPUB: sidecar is canonical (always).
- PDF writable:
  - Embedded PDF is canonical at Save boundary.
  - During editing, maintain a working delta layer in the annotation session (overlay).
  - On Save: commit delta to PDF, then clear delta (or mark it as synced).
  - This avoids double-rendering and avoids two different undo stacks.
- PDF not writable:
  - Sidecar is canonical.
  - Save is replaced with export options.

User-visible behavior matrix (must be consistent)
- PDF (writable):
  - Save: commits annotations into the PDF.
  - Annotations while editing: overlay (working delta) + native baseline (existing PDF annots).
  - Export annotated copy: produces a new PDF; prefer “preserve PDF content + embed annots”; flatten only as fallback.
- PDF (not writable):
  - Save: disabled/replaced with “Export annotated copy” + guidance banner.
  - Annotations: sidecar overlay.
- EPUB:
  - Save: hidden/disabled (“no save into EPUB”).
  - Share/Print: route through “Export annotated PDF” (flatten).
  - Annotations: sidecar overlay; layout lock rules apply.

Critical corrections applied (fixes the earlier errors)
1. No profile-scoped stores. Sidecar store is keyed by document identity only; layout profile is stored on the annotation anchor, not as a storage partition.
2. Theme is not part of layout identity. Theme is paint-only; it must not change the layout profile ID or hide annotations/recents.
3. One annotation tool pipeline. Tools are unified; only persistence backend differs.
4. PDF export strategy avoids flattening by default for PDFs. Flatten is the fallback, not the default, for PDFs.

Layout-lock policy (EPUB) – revised to avoid “notes disappeared”
- Before any annotations: layout changes allowed freely.
- After highlights/notes exist (MVP layout-locked anchors):
  - Layout changes allowed, BUT:
    - persistent banner: “Annotations were created with a different layout.”
    - one-tap action: “Switch to annotated layout.”
    - (optional) show highlights as “disabled/hidden” under mismatched layout to avoid misleading misplacement.
- After first ink stroke:
  - Default: lock layout controls for that document (predictable).
  - If override is allowed: ink is hidden under mismatched layout with the same persistent “Switch to annotated layout” affordance.

Decisions (choose defaults now; don’t leave them vague)

Layout units (MuPDF `fz_layout_document(w,h,em)`)
- Goal: stable, repeatable mapping across devices and consistent between:
  - on-screen render
  - anchor coordinates
  - export page size
- Default mapping (screen-stable, not “physical inches”):
  - Define a “layout unit” as dp-derived points:
    - `wUnits = viewportWidthDp * 72/160`
    - `hUnits = viewportHeightDp * 72/160`
    - `fontUnits = userFontDp * 72/160` (or a direct slider value mapped into units)
  - Theme CSS must not change layout-affecting properties (no font-size/margins).

Initial layout defaults (first open)
- `fontUnits` equivalent to ~14–16dp (not 12pt literal). Start readable on phones.
- `marginScale = 1.0`, `lineSpacing = 1.0`
- Theme default: light

Sidecar storage format (corrected)
- Default: SQLite (WAL enabled) under internal storage.
  - Reason: ink + undo will outgrow JSON quickly; SQLite avoids rewrite-on-stroke and is crash-safe.
- Store keyed by document identity only; annotations carry `layoutProfileId` in anchors.

Export defaults
- EPUB export flatten:
  - 150–200 DPI target for bitmap render (balance quality/perf).
  - PDF page size matches the layout profile’s `wUnits/hUnits` to preserve geometry alignment.
- PDF export annotated copy:
  - Prefer: embed annotations into a new PDF while preserving original content.
  - Fallback: flatten if embedding fails or unsupported (encrypted edge cases).

Recents/viewport (EPUB MVP)
- Store location as:
  - `spineId + progression` if available; else
  - `pageIndex + normalizedScroll` as temporary
- Always store the `layoutProfileId` with the location, but do not store theme as part of it.

Implementation phases (EPUB track)

E0 — Plumbing: open EPUB + no crashes + basic gating
- Intents:
  - accept `.epub` + `application/epub+zip` in pick/open flows
  - update manifest VIEW filters accordingly
- Doc type detection:
  - use MuPDF-reported format string as canonical; map to `DocumentType`
- UI gating:
  - hide/disable Save for EPUB
  - hide/disable PDF-only actions not applicable
- Definition of done:
  - open EPUB; scroll; render non-blank
  - TOC button opens and navigates without crash (even if minimal)

E1 — EPUB reading baseline (TOC + layout controls + relayout stability)
- Wire reflow layout:
  - expose and call `fz_layout_document` for reflow docs on layout changes
  - ensure relayout invalidates caches (page sizes/tiles) and resets view safely
- Theme:
  - implement light/dark/sepia via user CSS that is strictly paint-only
- Preferences:
  - persist layout profile per document (doc identity → last-used profile)
  - persist theme separately
- Definition of done:
  - font size/margins/line spacing cause relayout with no stale blank pages
  - reopen restores location under same layout profile

E2 — Unified annotation session + sidecar backend (EPUB + PDF fallback)
- Data model:
  - implement annotation objects + anchors as above
- Sidecar storage:
  - SQLite tables for:
    - documents (identity mapping + metadata)
    - annotations (id, type, payload blob/json, created/updated, deleted)
    - anchors (page/spine, layoutProfileId, geometry bounds index)
    - optional ops/journal table for undo/redo replay if you choose op-based storage
- Overlay rendering:
  - render annotation snapshots on top of base page render
- Policy wiring:
  - EPUB: tools write to sidecar backend always
  - PDF not writable: tools write to sidecar backend
  - PDF writable: tools write to working delta layer (overlay); Save commits delta to PDF (E4)
- Definition of done:
  - EPUB: highlight/note persist + render after reopen
  - EPUB: ink persists + erases + undo/redo works
  - PDF not writable: same behavior as EPUB

E3 — Export annotated PDF (EPUB flatten + PDF fallback flatten)
- EPUB export:
  - render each reflow “page” under the selected annotated layout profile
  - composite annotation overlay
  - write new PDF with one image per page (flatten)
- UX:
  - EPUB menu exposes “Export annotated PDF”
  - share/print routes through export
- Definition of done:
  - exported PDF non-blank and contains annotation marks in correct positions

E4 — PDF writable Save + export (preserve content; embed annots)
- Writability detection (revised)
  - preflight at open based on URI permissions/provider + doc encryption state
  - if Save fails, downgrade capability and switch UI to export mode (never lose work)
- Save semantics (revised to avoid tool forks)
  - maintain a working delta layer during editing
  - Save commits delta into the PDF (embedded annotations or PDF content additions), then clears delta
- Export annotated copy for PDFs
  - prefer: new PDF preserving original content + embedded annotations
  - fallback: flatten only if embedding path fails
- Definition of done:
  - writable PDF: Save makes annotations visible in other PDF viewers
  - downgrade path: if Save fails, app switches to sidecar + export guidance without losing annotations

E5 — Robust EPUB highlights (text anchors) + mismatch handling
- Add text anchoring for highlights:
  - store text range anchor (CFI-like / DOM offset range) + quote context
  - resolve anchor → quads per current layout
- Layout mismatch UX:
  - highlights remain visible across layout changes (once text anchors exist)
  - ink remains layout-locked; mismatch banner + “switch to annotated layout”
- Definition of done:
  - change font size → highlights remain attached correctly
  - ink behavior remains predictable and never silently “moves”

Testing plan (automation + fixtures) – revised to be deterministic

Fixtures
- DRM-free EPUB: TOC + multi-chapter + images
- Edge EPUB: complex HTML/CSS (tables/long paragraphs)
- DRM/encrypted sample (or synthetic encryption.xml) to validate error messaging

Automated checks (avoid OCR as a primary oracle)
- Render sanity:
  - screenshot + pixel variance threshold (non-blank)
- Text sanity:
  - prefer MuPDF text extraction APIs for a known string/keyword when available
  - only use OCR as a last-resort smoke signal (not pass/fail gate unless you accept flakiness)
- Overlay sanity:
  - after drawing/highlight creation, assert from sidecar DB:
    - annotation count increased
    - annotation bounds intersect visible page bounds
  - then confirm render by pixel-diff/red-pixel count if you keep that heuristic
- Export sanity:
  - export PDF → render first/last pages → non-blank + overlay present
- Crash detection:
  - fail on fatal logcat signals; archive artifacts for each run

Known risks / mitigations (updated)
- Reflow invalidation bugs: aggressively invalidate cached page sizes/tiles after relayout; treat relayout as a state transition with a clear “layout generation” counter to avoid mixing old/new page geometry.
- Sidecar growth/perf: use SQLite WAL; store stroke point arrays as compact binary blobs; index by doc + page/spine + layoutProfileId for fast page queries.
- Layout mismatch confusion: persistent banner + one-tap switch back to annotated layout; never rely on one-time warnings.
- Doc identity migration: keep existing IDs stable; introduce content-derived ID alongside existing identifiers; migrate deliberately with a mapping table and clear rollback behavior.
- PDF export quality: for PDFs, embedding must be preferred over flatten to preserve selectable text and vector content; flatten is fallback only.
