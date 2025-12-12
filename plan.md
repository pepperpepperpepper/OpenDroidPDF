OpenDroidPDF – Monolith Decomposition Follow-up
==============================================

Goals
- Shrink the remaining oversized classes while keeping public behavior stable.
- Finish moving UI logic into controllers and JNI calls through the repository layer.
- Maintain green builds/tests and F-Droid deployability at every step.

Milestones / Phases

Phase A — Activity / UI shell
- Split `OpenDroidPDFActivity.java` into:
  - `NavigationController` (dashboard/doc host swaps, back handling)
  - `IntentRouter` / `ShareHandler` (share/open/export routes)
  - `LifecycleHooks` helper (onResume/onPause/onDestroy glue)
- Ensure menu/toolbar logic is fully owned by `ToolbarStateController`; remove inline menu handling from the activity.
- Target size: activity ≤ 800 LOC.
- Tests: `connectedDebugAndroidTest` smoke + manual dashboard↔doc switch.

Phase B — ReaderView & PageView
- `PageView.java`: keep rendering only; move gesture/overlay/ink routing into `DrawingController` + a small `GestureRouter`.
- `ReaderView.java`: split into `PageAdapterHost` (adapter wiring/prefetch), `ScrollState` helper, `InteractionBridge` for passClick/events.
- Target sizes: PageView ≤ 600 LOC; ReaderView ≤ 600 LOC.
- Tests: gesture/undo instrumentation; adapter prefetch unit test; baseline export/undo smoke.

Phase C — Core wrappers
- Finish trimming `OpenDroidPDFCore.java`, `MuPDFCore.java`, `MuPDFPageView.java` to thin adapters around `MuPdfController`/`MuPdfRepository`.
- Move any lingering JNI calls or state into controllers/repository.
- Target sizes: each ≤ 400 LOC.
- Tests: existing export/ink/search instrumentation; run lint.

Phase D — JNI cleanup
- Split `document_io.c` into:
  - `document_session.c` (open/close, permissions)
  - `document_save.c` (save/export)
  - `document_meta.c` (info/bookmarks)
- Keep `mupdf_native.h` declarations organized; adjust Android.mk accordingly.
- Tests: `assembleDebug` + `connectedDebugAndroidTest`; quick save/export manual check.

Phase E — Polish
- Re-run `--warning-mode all`; ensure zero new Gradle deprecations.
- Update docs: `docs/architecture.md` and `docs/transition.md` with new class/module layout.
- Optional: F-Droid deploy if functionality changes.

Working agreements
- Keep commits small and buildable; no API changes visible to users.
- Don’t revert existing user changes; keep the worktree dirty artifacts untouched unless explicitly removing temp files.
- Default build dir: `/mnt/subtitled/opendroidpdf-android-build`; emulator: `localhost:42865`.

Next action
- Start Phase A (activity split) first after this plan lands.

Status Update — Shared Types Migration (Dec 9, 2025)
- Moved remaining Java-only shared types from :app to :core to avoid duplication and to unblock further refactors:
  • org.opendroidpdf.OutlineItem
  • org.opendroidpdf.MuPDFAlert
  • org.opendroidpdf.MuPDFAlertInternal
- Gradle wiring updated:
  • platform/android/core/build.gradle now includes these files in `coreSources` (compiled from core/src/main/java).
  • platform/android/build.gradle excludes the same patterns via `coreSourcePatterns` to prevent duplicate classes.
- Follow-up check: audit for any other plain Java data holders still in :app that are referenced by both core controllers and UI (e.g., consider `RecentFile/RecentFilesList` only if we later want them reusable from non-UI code; safe to keep in :app for now).


PageView Lift Plan (Dec 10, 2025)
---------------------------------

Objective
- Reduce `PageView.java` to a thin container that owns only three children (entire bitmap view, HQ patch view, overlay) and delegates everything else.
- Centralize rendering, layout, and state decisions in dedicated collaborators so PageView becomes easier to reason about and test.

Current Anatomy (after recent trims)
- Already externalized: drawing (DrawingController), selection (SelectionController + SelectionRenderer), search/links renderers, busy‑indicator helper, patch render orchestration (PageRenderOrchestrator.ensureAndRender), HQ discard/layout helper (layoutOrDiscardHq).
- Still local inside PageView and good candidates to lift:
  • Entire-bitmap matrix/visibility logic (mEntireMat scaling + covered‑by‑HQ test)
  • Preference plumbing and static ink/eraser/highlight colors
  • Text/links/annotations async loads and callbacks (DocumentContentController hooks)
  • OnMeasure/OnLayout responsibilities for overlay and indicator coordination
  • Search/selection result holders and doc‑rel X bounds collection

Target End-State
- PageView is a 300–400 LOC class that:
  • Holds references to children and forwards lifecycle calls.
  • Delegates rendering to PageRenderOrchestrator and layout to a PageLayoutController.
  • Delegates content loads to a PageContentController.
  • Reads pen/eraser settings via PenPreferences; no static thickness/color fields inside PageView.

Deliverables (slices, safe to land one by one)
1) Entire‑view layout helper
   - Add PageRenderOrchestrator.layoutEntire(entireView, area, size, matrix, hqCovers) to encapsulate:
     • Covered‑by‑HQ check
     • Matrix scale application and visibility toggling
   - Replace inline logic in PageView.onLayout with the helper.

2) PageLayoutController
   - New class under `app/overlay/` (or `app/reader/`): owns all layout math for HQ/entire/overlay and busy‑indicator placement.
   - PageView.onLayout delegates measurements and child layout to the controller.
   - Acceptance: visual parity on zoom/pan; no flicker regressions.

3) Preference plumbing removal
   - Remove static `inkThickness/eraserThickness/*Color` from PageView; read from `PenPreferences` (already in app services).
   - Migrate `PageView.onSharedPreferenceChanged` into a small adapter that updates controllers/renderers; ensure defaults match existing values.
   - Acceptance: pen/eraser size and colors behave identically across restarts.

4) Content loading split
   - Introduce `PageContentController` (wraps `DocumentContentController`) to load text, links, and annotations with callbacks.
   - PageView only forwards `pageNumber` and invalidation hooks.
   - Acceptance: search/selection/links still function; no leaks (cancel outstanding jobs on release).

5) State model + bounds
   - Add `PageState` (pageNumber, size, sourceScale, docRelXmin/max, flags) that controllers consume instead of touching PageView internals.
   - Replace direct field access in renderers with `PageState` getters.
   - Acceptance: selection bounds and smart‑selection continue to work.

6) Public surface audit
   - Minimize PageView public API to methods used by MuPDFPageView: `setPage(...)`, `redraw(...)`, selection APIs, drawing APIs, and getters required by controllers.
   - Mark anything else package‑private and move helpers next to consumers.

7) Tests & QA
   - Manual: zoom/pan/fit‑width, draw/erase/save/undo, search highlight navigation, link taps, add text annotation.
   - Instrumentation: smoke test for draw→accept→undo and selection markers rendering.
   - Performance: confirm no regressions in HQ patch creation and no extra GC from matrix churn.

8) Cleanup & Docs
   - Remove dead fields/methods from PageView once slices land.
   - Update `docs/architecture.md` and `ClassStructure.txt` with the new roles (Layout/Content/Orchestrator).
   - Add a short migration note in `docs/transition.md` for contributors touching PageView.

Guardrails / Risks
- Land in small PRs; keep visual snapshots from Genymotion for parity.
- If a slice causes flicker, temporarily gate the new path behind a debug flag until fixed.
- Ensure `MuPDFPageView` overrides remain compatible (no behavior change in `saveDraw`, `undoDraw`, `setPage`).

KPIs
- PageView LOC ≤ 600 after slice 2, ≤ 450 after slice 5.
- No change in export/print correctness; no new ANRs; memory steady on repeated zooms.
