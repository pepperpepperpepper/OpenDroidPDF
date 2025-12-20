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
- Services/wiring: `ServiceLocator` exposes typed factories with explicit scopes; no generic global helpers.

Dependency rules (enforced)
- Directional only: Activity → controllers/services → views/core. Views must not reach into Activity.
- No cycles between controllers; ownership is singular (each concept has one home in the taxonomy above).
- Shared prefs/files accessed only through `PreferencesRepository` with documented migrations.
- Scoped object lifetime:
  - App-scope: navigation, permissions, global prefs, recent files index.
  - Document-scope: document session, annotation session, search session, layout profile, page cache.
  - View-scope: gesture state, transient UI bridges.
- The service locator may provide factories, but must not become an ambient global:
  - No `ServiceLocator.get()` calls from low-level views/core.
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
