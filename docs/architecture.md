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

Current state (loc/roles, Dec 15)
---------------------------------
- `OpenDroidPDFActivity` ~610 LOC: thin host; lifecycle save flags now owned by `SaveFlagController` via `LifecycleHooks`. Most nav/export/menu logic sits in controllers/adapters; remaining surface is wiring + host getters.
- `MuPDFReaderView` ~270 LOC: paging/child reuse; gesture routing delegated to `GestureRouter`/helpers. Search navigation sits in `SearchResultsController`, with the view just delegating add/next/clear/apply.
- `MuPDFPageView` ~500 LOC: render + annotation/widget coordination; hit-testing and widget pass-click routing moved into `PageHitRouter` to keep the view focused on rendering.
- `MuPDFPageView` ~509 LOC: rendering + selection/annot hit-testing; annotation selection handled by `AnnotationSelectionManager`; hit-tests via `AnnotationHitHelper`.
- `OpenDroidPDFCore` ~894 LOC, `MuPDFCore` ~548 LOC: JNI/native bridge unchanged.
- ServiceLocator in place; navigation/permission/export services wired; export host now talks directly to `SaveFlagController`/`SaveUiDelegate` (no activity passthrough).

Hotspots (next to simplify)
---------------------------
1) **Activity wiring** — keep pushing residual menu/state helpers into controllers/adapters; activity should only wire services and forward events.
2) **Drawing/annotation flows** — continue isolating ink/selection/dialog flows into controllers; PageView stays render-only.
3) **Reader geometry/state** — finish moving inline math/state into `ReaderGeometry`/`NormalizedScroll`; keep ReaderView/PageView lean.
4) **Service boundaries** — ensure all save/export/nav paths consume `SaveFlagController`, `SaveUiDelegate`, and other services directly (no activity pass-through helpers).

Immediate follow-up (aligned to plan.md)
----------------------------------------
- Keep iterating on the hotspots above; update this doc as ownership shifts.
- After each slice: `./gradlew assembleDebug -x lint` + `scripts/geny_smoke.sh`; record outcomes in `docs/housekeeping/baseline_smoke.md`.
- Maintain the dependency rules: Activity → controllers/services → views/core; no cycles; single owner per concept.
