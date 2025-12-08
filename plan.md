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
