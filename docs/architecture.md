OpenDroidPDF Architecture Snapshot (Dec 14, 2025)
================================================

Goal
----
Document the current layering/ownership to guide simplification. This is a snapshot; update as services/controllers move per `plan.md`.

Layers & Roles (current state)
------------------------------
- **UI hosts**: `OpenDroidPDFActivity` (665 LOC) owns lifecycle, menus, permission prompts, document open/share/export, and embeds fragments; `DashboardFragment` and `DocumentHostFragment` display dashboard vs. document view containers.
- **Controllers/Helpers** (Java/Kotlin in `app/`): alert/UI state (`AlertUiManager`, `UiStateManager`), document lifecycle (`DocumentLifecycleManager`), navigation (`DocumentNavigationController`), host wiring (`DocumentHostController`), dashboard plumbing (`DashboardController`), annotation/pen (`InkUndoController`, `AnnotationToolbarController`, `PenStrokePreviewView`), gesture helpers (`DrawingGestureHandler`, `StylusGestureHelper`, `LongPressHandler`, `SearchResultNavigator`, `TapGestureRouter`, `SelectionGestureHandler`). A per-activity `ServiceLocator` exposes `NavigationService`, `PermissionService`, and `ExportService`; export logic remains in `ExportController`.
- **Reader stack**: `MuPDFReaderView` (325 LOC) manages paging/child reuse; gesture routing now lives in `TapGestureRouter`, `SelectionGestureHandler`, `DrawingGestureHandler`, `SearchResultNavigator`, `StylusGestureHelper`. `MuPDFPageView` (612 LOC) handles page rendering, links, selection, and annotation hit-testing; `PageView` (562 LOC) is another page-level container; `ReaderView` (800 LOC) wraps touch/layout for older flows. Gesture routing is partially extracted but still coupled.
- **Core/native bridge**: `OpenDroidPDFCore` (894 LOC) and `MuPDFCore` (548 LOC) wrap native MuPDF; JNI in `platform/android/jni/mupdf.c` (not counted above). Shared prefs/files still accessed from activity and helpers directly.
- **Assets/branding/deployment**: F-Droid metadata under `fdroid/`; deployment scripts under `/home/arch/fdroid/scripts/`; branding assets in `resources/branding/`.

Hotspots (to simplify first)
---------------------------
1) `OpenDroidPDFActivity` – still orchestrates many flows (navigation, export, permissions, dialogs). Target: delegate to Navigation/Export/Permission services via a locator.
2) Reader stack (`ReaderView`, `MuPDFPageView`, `PageView`) – dense gesture/layout/selection logic; goal is clear separation: view vs. controllers (drawing, search, selection, geometry).
3) Core wrappers (`OpenDroidPDFCore`, `MuPDFCore`) – large surfaces; consider narrower interfaces exposed to UI/controllers.
4) Shared prefs/data access – scattered; migrate to scoped providers when services are added.

Current dependencies of note
----------------------------
- Activity reaches into multiple helpers directly instead of service interfaces (navigation/export/permissions).
- Reader views call helpers via instance fields; some state duplicated between view and controller (e.g., selection, search highlighting).
- No central service locator yet; dependencies often pulled from activity context.

Immediate follow-up (per plan)
------------------------------
- Create NavigationService, ExportService, PermissionService and wire activity → services via a small locator.
- Keep a running hotspot/ownership list here as pieces move; update LOC counts when major trims land.
