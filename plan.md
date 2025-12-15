OpenDroidPDF – Codebase Simplification Plan (Dec 14, 2025)
==========================================================

Purpose
Make the project easier to understand, change, and ship by simplifying structure—not just shrinking files. Every step should reduce coupling, clarify ownership, and remove redundancy while keeping behavior stable and builds green.

What “simpler” means here (detailed goals)
- Clear layering: screens/fragments only orchestrate, controllers implement flows, services own cross-cutting capabilities (navigation, export, permissions, drawing, search), and core adapters talk to MuPDF/native. Each piece has one place to live.
- Explicit dependencies: no hidden singletons or Activity lookups; dependencies are passed via constructors or a small service locator so testing/mocking is easy.
- Small, named contracts: public APIs are capability-oriented (e.g., DrawingService, ExportService), not location-oriented. Shared prefs and files have well-scoped namespaces with migrations documented.
- Reader pipeline clarity: ReaderView handles layout/child mgmt, PageView renders, gesture/selection/drawing logic lives in dedicated routers/controllers. Avoid state duplication between views and controllers.
- Build hygiene: a clean :app / :core split with no duplicate classes; deterministic Gradle/R8; F-Droid scripts pull config from one place.
- Safety net: after each refactor slice, run a fast Genymotion smoke (open → draw → undo → search → export) and keep notes in docs so behavior remains stable.

Non-goals / constraints
- Do not delete user data or untracked assets without explicit approval.
- No broad style rewrites or Kotlin-first conversions unless they serve the above goals.
- Keep F-Droid deployment intact after each structural change; version bumps only when shipping behavior changes.

Guiding Principles
- One layer, one job: UI (screens/fragments) orchestrates; controllers implement flows; core/repo talks to MuPDF/native; utilities stay pure.
- Explicit dependencies: no hidden activity lookups or statics; pass what’s needed through constructors or a small service locator.
- Stable boundaries: public APIs are small and named for capabilities (e.g., DrawingService, ExportService), not for where the code lives.
- Safety net: quick emulator smoke + targeted unit/instrumentation tests after each slice; keep F-Droid pipeline working.

Ownership taxonomy (canonical zones)
- Activity host: lifecycle + top-level navigation only (`OpenDroidPDFActivity`).
- Navigation: intents, back-stack, open/close/save/export entry points (`IntentRouter`, `DocumentNavigationController`).
- Toolbar/UI state: menu visibility/enabled rules and search/annot toggles (`ToolbarStateController`).
- Gesture & interaction: tap/selection/scroll/pinch routers plus gesture state bookkeeping (`TapGestureRouter`, `SelectionGestureHandler`, `GestureRouter`, `GestureStateHelper`).
- Reader views: layout/render containers only (`MuPDFReaderView`, `MuPDFPageView`, `PageView`) with geometry helpers (`ReaderGeometry`, `NormalizedScroll`).
- Annotations/drawing: add/delete/edit flows, dialogs, widgets, signatures, ink capture/undo (`AnnotationController`, `DrawingController`).
- Export/share: all save/print/share prompts and actions (`ExportController`).
- Permissions: storage/runtime permissions and rationales (`StoragePermissionHelper`).
- Preferences: scoped settings access + migrations (`PreferencesRepository`).
- Services/wiring: `ServiceLocator` exposes the above as typed factories; no generic “helper” buckets.

Dependency rules
- Directional only: Activity → controllers/services → views/core; views must not reach into the Activity.
- No cycles between controllers; ownership is singular (each concept has one home in the taxonomy above).
- Shared prefs/files are accessed only through `PreferencesRepository` and documented migrations.

Phase 1 — Map & De-tangle
- Produce a current dependency/ownership map: activities/fragments → controllers → services → core/native.
- Identify global/static singletons and shared prefs namespaces; plan replacements with scoped providers.
- Outcome: short architecture note in `docs/architecture.md` and a list of highest-coupling hotspots to fix first.

Phase 2 — Activity/Navigation Simplification
- Collapse navigation/share/export/permission flows behind dedicated services (NavigationService, ExportService, PermissionService); activities become thin hosts.
- Introduce a tiny service locator (debug-friendly) to provide shared services without static access.
- Outcome: `OpenDroidPDFActivity` only wires UI + delegates; no inline menu logic. Target ≤500 LOC but focus on clearer roles.

Phase 3 — Reader Stack Simplification (ReaderView/PageView)
- Keep `MuPDFReaderView` responsible only for paging/child mgmt; route gestures via `GestureRouter` helpers.
- `PageView` holds children; rendering/layout/content handled by `PageLayoutController`, `PageContentController`, `PageState` model.
- Outcome: reader path expresses “what happens” in three layers: view → controller → core; no static color/thickness or inline prefs.

Phase 4 — Services & Data Flow
- Define small service interfaces (DrawingService, SearchService, ExportService, PenPreferences, RecentFiles) with clear contracts.
- Move data holders shared by app/core into :core; keep UI-only models in :app. Remove duplicate-class exclusions once stable.
- Outcome: controllers depend on interfaces; swapping implementations (e.g., mock for tests) requires minimal wiring.

Phase 5 — Build & Config Simplification
- Clean the Gradle split: :core holds pure Java/MuPDF adapters; :app holds Android/UI. Re-enable R8 once duplicates are gone.
- Standardize build constants/env vars and deployment scripts (F-Droid) under `scripts/` with a single config source.
- Outcome: `assembleDebug`/`assembleRelease` clean; no duplicate classes; deploy script uses consistent names.

Phase 6 — Quality & Tooling
- Run `--warning-mode all`; fix deprecations and noisy lint where quick wins exist.
- Add small debug-only hooks to exercise hard-to-trigger paths (multi-touch zoom snap, alert flows) without UI clutter.
- Outcome: quieter builds, easier manual verification.

Phase 7 — Cleanup & Docs
- Remove/archivize obsolete screenshots/dumps once refactors stabilize (keep per agreement not to delete untracked without approval).
- Update `docs/architecture.md`, `docs/transition.md`, and `ClassStructure.txt` to reflect the simplified structure and service boundaries.
- Outcome: docs match code; newcomers can follow the layers without digging into monoliths.

Immediate Next Actions (rolling, Dec 15, 2025)
1) **Phase 3 done** – reader stack is now layered: gestures in routers, selection in SelectionUiBridge + shared manager, hit routing in PageHitRouter, widget/signature via controllers, per-document controllers built in ReaderComposition. MuPDFPageView ~333 LOC, MuPDFReaderView ~264 LOC.
2) **Phase 4 start** – define service interfaces (DrawingService, SearchService, ExportService, PenPreferences, RecentFiles) and have controllers depend on them rather than concrete classes. Move shared data holders to :core where appropriate, keeping UI-only models in :app.
3) **Safety net** – after each slice, run `scripts/geny_smoke.sh` (draw → undo → search → share) on Genymotion Pixel 6 @ `localhost:42865`; log outcomes in `docs/housekeeping/baseline_smoke.md`.
4) **Docs** – keep `docs/architecture.md` updated as services are introduced; add a short note on service interfaces once they’re in place.

Per-document reader composition
- `ReaderComposition` now constructs annotation/widget/signature/selection controllers once per MuPdfController and injects them into page views. Next steps: keep page-level annotation/selection UI hooks thin (push dialog/select-box plumbing into controller/bridge classes), then document the lifetime rules in architecture notes.
