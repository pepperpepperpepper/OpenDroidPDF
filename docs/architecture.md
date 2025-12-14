OpenDroidPDF Architecture Snapshot (Dec 14, 2025)
=================================================

Goal
----
Document ownership and dependency rules so every concept has a single home and there are no cycles. Update this snapshot as refactors land.

Canonical ownership zones
-------------------------
- **Activity host**: `OpenDroidPDFActivity` — lifecycle + top-level navigation only.
- **Navigation**: intents/back-stack/open-close-save-export (`IntentRouter`, `DocumentNavigationController`).
- **Toolbar/UI state**: menu visibility/enabled rules, search/annot toggles (`ToolbarStateController`).
- **Gesture & interaction**: tap/selection/scroll/pinch routing and gesture state (`ReaderGestureController` over `TapGestureRouter`, `SelectionGestureHandler`, `GestureStateHelper`).
- **Reader views**: layout/render containers (`MuPDFReaderView`, `MuPDFPageView`, `PageView`) plus geometry helpers (`ReaderGeometry`, `NormalizedScroll`).
- **Annotations & drawing**: annotation dialogs/widgets/signatures and ink capture/undo (`AnnotationController`, `DrawingController`).
- **Export/share/save**: all save-as/print/share prompts and execution (`ExportController` via `ServiceLocator.ExportService`).
- **Permissions**: runtime/storage permission checks + rationales (`StoragePermissionHelper`).
- **Preferences**: scoped settings access + migrations (`PreferencesRepository`).
- **Services/wiring**: `ServiceLocator` provides typed factories for the above; no generic “helper” buckets.

Dependency direction
--------------------
- Flow is one-way: Activity → controllers/services → views/core. Views never reach back into the Activity.
- Controllers do not depend on each other cyclically; each concept has a single owner.
- Shared prefs/files are accessed only through `PreferencesRepository` and documented migrations.

Current state (loc/roles)
-------------------------
- `OpenDroidPDFActivity` ~660 LOC: already delegates gestures/search/nav partly; still owns some export/save prompts slated for controllers.
- `MuPDFReaderView` ~310 LOC: paging/child reuse; all gesture routing delegated to `ReaderGestureController` (tap/scroll/fling/scale/touch). Remaining view logic is child setup + search navigation.
- `MuPDFPageView` ~600 LOC: rendering + selection/annot hit-testing; annotation dialogs/widgets/signature flows now live in `AnnotationUiController` and ink lifecycle in `InkController`.
- `OpenDroidPDFCore` ~894 LOC, `MuPDFCore` ~548 LOC: JNI/native bridge unchanged.
- ServiceLocator in place; navigation/permission/export services wired for most flows.

Hotspots (next to simplify)
---------------------------
1) **Annotation/dialog plumbing** — move widget/signature/note dialogs into `AnnotationController`; PageView keeps rendering only.
2) **Drawing capture/undo** — finish isolating ink pipeline in `DrawingController`; remove event/state from views.
3) **Export/save prompts** — route remaining prompts through `ExportController`; remove from Activity.
4) **Reader geometry/state** — finish moving inline math/state into `ReaderGeometry`/`NormalizedScroll`; keep ReaderView lean.

Immediate follow-up (aligned to plan.md)
----------------------------------------
- Implement the above hotspots while keeping the dependency rules; update LOC counts after each slice.
- Run `./gradlew assembleDebug -x lint` + `scripts/geny_smoke.sh` after significant moves; log results in `docs/housekeeping/baseline_smoke.md`.
- Keep this doc synchronized with `plan.md` as ownership shifts.
