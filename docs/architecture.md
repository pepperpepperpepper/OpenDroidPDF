OpenDroidPDF Architecture Snapshot (Dec 21, 2025)
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
- **Export/share/save**: all save-as/print/share prompts and execution (`ExportController` wired from `ActivityComposition`).
- **Permissions**: runtime/storage permission checks + rationales (`StoragePermissionHelper`).
- **Preferences**: scoped settings access + migrations (`PreferencesRepository`).
- **Services/wiring**: `ActivityComposition` wires controllers/adapters and `AppServices` provides app-scoped stores/services; no generic “helper” buckets.
- **Document identity**: stable doc identity is resolved once per open (`DocumentIdentityResolver`) and propagated as the canonical `docId` across recents/viewport/sidecar.
- **Sidecar annotations (EPUB + read-only PDFs)**: document-scoped `SidecarAnnotationSession` + `SQLiteSidecarAnnotationStore`; overlay rendering through `SidecarAnnotationRenderer`.
- **Reflow (EPUB)**: `ReflowSettingsController` owns reflow layout application; `layoutProfileId` is derived from layout-affecting fields only (theme is paint-only).

Dependency direction
--------------------
- Flow is one-way: Activity → controllers/services → views/core. Views never reach back into the Activity.
- Controllers do not depend on each other cyclically; each concept has a single owner.
- Shared prefs/files are accessed only through `PreferencesRepository` and documented migrations.
- Canonical document identity (`docId`) flows downward once computed; legacy URI-based ids are used only for migration shims.

Current state (roles, Dec 21)
-----------------------------
- **Open flow**: `DocumentSetupController` resolves and stores `DocumentIdentity` early and migrates legacy ids forward for:
  - Sidecar DB rows (`SQLiteSidecarAnnotationStore.migrateDocId(...)`),
  - Viewport snapshots (read legacy once then write-forward), and
  - Reflow prefs/layout snapshots (legacy keys → canonical `docId` keys).
- **EPUB behavior**:
  - Annotations are sidecar-only and rendered as an overlay (`SidecarAnnotationSession`).
  - Layout mismatch is a UI state owned by `ReflowSettingsController` and rendered via `UiStateDelegate` (snackbar banner + “Switch” action).
  - Highlight-only relayout can re-anchor highlights into the new `layoutProfileId` (best-effort, quote-based).
- **Export behavior**:
  - Sidecar docs export via `FlattenedPdfExporter` (bitmap render + overlay composite → new PDF).
  - PDF writable docs prefer native save/export paths; sidecar export is used only when the doc is effectively read-only.
- **Automation**: Genymotion smokes cover PDF + EPUB flows (open, draw, undo, search, export, relayout, mismatch, docId rename) and are recorded in `docs/housekeeping/baseline_smoke.md`.

Hotspots (next to simplify)
---------------------------
1) **Text anchoring depth** — highlights currently re-anchor via stored quote text; consider upgrading to a stronger anchor (range/context) if false-positive matches show up in real books.
2) **Reader/overlay boundaries** — keep pushing gesture/selection plumbing out of `MuPDFReaderView`/`MuPDFPageView` into routers/controllers to reduce view responsibilities.
3) **Save/export consistency** — continue making “Save failed → downgrade to sidecar/export mode” deterministic across SAF/providers, and keep the UI guidance consistent.

Immediate follow-up
-------------------
- Keep iterating on the hotspots above; update this doc as ownership shifts.
- After each slice: `./gradlew assembleDebug -x lint` + `scripts/geny_smoke.sh`; record outcomes in `docs/housekeeping/baseline_smoke.md`.
- Maintain the dependency rules: Activity → controllers/services → views/core; no cycles; single owner per concept.
