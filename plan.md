OpenDroidPDF – Project Plan (Updated 2025-12-31)
================================================

Purpose
Make the project easier to understand, change, and ship by simplifying structure—not just shrinking files. Every step reduces coupling, clarifies ownership (“ONE OWNER”), and removes redundancy while keeping behavior stable and builds green.

What “simpler” means here (goals)
- Clear layering: screens/fragments orchestrate only; controllers implement flows; services own cross-cutting capabilities; core/adapters talk to MuPDF/native.
- Explicit dependencies: no hidden singletons, no Activity lookups from deep layers. Dependencies are constructor-provided or scope-provided via a small, typed locator.
- Small, named contracts: APIs are capability-oriented (Drawing, Export, Search, Permissions, Recents, Import), not “where code lives.”
- Reader pipeline clarity: ReaderView lays out children and hosts; PageView renders; gesture/selection/drawing logic lives in dedicated routers/controllers. Avoid duplicated state across view/controller.
- Annotation pipeline clarity: one tool pipeline across document types; persistence backends are swappable (PDF-in-file vs sidecar) without tool logic forking.
- Build hygiene: clean `:app` / `:core` split; deterministic Gradle/R8; F-Droid scripts pull config from one place.
- Safety net: after each slice, run a fast smoke (open → draw → undo → search → export) and keep a dated log.

Non-goals / constraints
- Do not delete user data or untracked assets without explicit approval.
- No broad style rewrites or Kotlin-first conversions unless they serve the above goals.
- Keep F-Droid deployment intact after each structural change; version bumps only when shipping behavior changes.

Guiding principles
- One layer, one job: UI orchestrates; controllers implement flows; core/repo talks to MuPDF/native; utilities stay pure.
- Explicit scope: app-scope vs document-scope vs view-scope objects are separated and never leaked across scopes.
- Stable boundaries: capability interfaces are small and named; no “misc helper” buckets.

Progress tracking (living)
- After each slice, update:
  - date (YYYY-MM-DD),
  - short description of the slice,
  - the commit(s),
  - the verification commands + whether they passed.
- Canonical references this plan expects to stay aligned with code:
  - `docs/architecture.md` (ownership map + dependency direction)
  - `docs/transition.md` (migration/compatibility notes)
  - `docs/desktop_linux.md` (Linux build/run notes)
  - `docs/housekeeping/baseline_smoke.md` (dated smoke log)

Status dashboard
- Shipped (tracked in git history + `docs/housekeeping/baseline_smoke.md`):
  - [x] EPUB track (sidecar + export + reanchor) (see `docs/transition.md`)
  - [x] Core refactor phases 1–7 (see `docs/architecture.md`)
  - [x] Desktop/Linux parity (pp_core ONE OWNER + desktop UI loop) (see `docs/desktop_linux.md` + `C_migration.md`)
- In-progress / next:
  - [ ] Word documents (`.doc` / `.docx`) import-as-PDF (section below)

Ownership taxonomy (canonical zones)
- Activity host: lifecycle + top-level navigation only (`OpenDroidPDFActivity`).
- Navigation/intents/import: open/close/import/export entry points (`IntentRouter`, `DocumentNavigationController`).
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

==========================================================
Word Documents Track (.doc/.docx) — “Import as PDF”
==========================================================

Goal
Support opening `.docx` / `.doc` by converting them to PDF and then using the *existing* PDF pipeline (render/search/annotations/export). This is explicitly “view/annotate the imported PDF”, not “edit the Word document”.

Why this approach
- It preserves the ONE OWNER rule: MuPDF-facing behavior remains owned by `platform/common/pp_core.*`.
- It avoids shipping a giant conversion engine inside the app (especially on Android/F-Droid).
- It gives users a predictable artifact to share: “Export annotated PDF”.

Decisions (locked in)
- Word docs are not opened natively; they are imported/converted to PDF first.
- Word docs behave like non-writable docs:
  - “Save into original” is not offered.
  - Annotations are stored in sidecar (keyed by the source doc’s identity) and shared via export.
- Conversion is a platform-level pre-processing step (UI/controller ownership), not a `pp_core` feature:
  - `pp_core` owns rendering/search/annotations *of the converted PDF*, and stays converter-agnostic.

UX rules
- When a user opens a Word doc:
  - show “Importing…” progress (conversion can be slow),
  - then open the resulting PDF in the reader.
- In the reader UI for imported Word docs:
  - hide/disable “Save” (there is nothing to save “into the .doc/.docx”),
  - keep Share/Print/Export enabled (export produces a PDF).
- If conversion is unavailable/fails:
  - show an actionable error (install/enable converter; or “Open in another app and export to PDF, then open the PDF”).

Data/identity rules (to keep sidecar stable)
- Doc identity key:
  - use the source Word file’s content-derived `docId` (sha256 of bytes) as the canonical identity.
- Cache the converted PDF per `docId`:
  - reuse the cached PDF on reopen to keep anchors stable,
  - invalidate/reconvert when the source file bytes change (docId changes).

Implementation slices (each is a small commit: implement → smoke → docs → push)

W0 — Plumbing + gating (Android + Linux)
- Allow selecting `.docx`/`.doc`:
  - Android file browser filters + MIME filters
  - Desktop open dialog / CLI path handling (where applicable)
- Add a new document-open branch: “Word → import pipeline → open resulting PDF”.
- Ensure UI gating is consistent:
  - Save disabled; Export/Share available; show an info banner “Imported document (Word)”.
- Definition of done:
  - Selecting a `.docx`/`.doc` never crashes; user sees either a converter flow or a clear “conversion unavailable” message.

W1 — Linux/desktop conversion (LibreOffice headless)
- Implement “Word → PDF” conversion using `soffice` (LibreOffice):
  - `soffice --headless --convert-to pdf --outdir <cacheDir> <input>`
- Cache location:
  - per-user cache directory (e.g., under the app’s existing cache/tmp folder), keyed by `docId`.
- Smokes:
  - Add `scripts/linux_docx_import_smoke.sh`:
    - convert a tiny fixture `.docx` → PDF
    - render page 1 and assert non-blank
    - optionally assert extracted text contains a known token (via `pp_demo`/`pp_core` extraction, not OCR)
- Definition of done:
  - On Linux, opening `.docx`/`.doc` works end-to-end and can export an annotated PDF.

W2 — Android conversion (v1: guided external conversion; v2 optional)
- v1 (ship this first; realistic for F-Droid):
  - When a `.docx`/`.doc` is selected, show a dialog:
    - explains that OpenDroidPDF imports Word docs as PDF,
    - offers “Open in another app to convert to PDF” (ACTION_VIEW),
    - explains how to return: “Share/Save as PDF, then open that PDF in OpenDroidPDF”.
  - Add a deterministic Genymotion smoke that asserts the dialog is shown (no crash).
- v2 (optional later; only if we find a small, dependable approach):
  - integrate an in-app converter *only if* size/licensing/perf are acceptable for F-Droid.
- Definition of done:
  - Android provides a clear user path; no silent failure; no broken Save semantics.

W3 — Sidecar + export semantics for imported docs
- Ensure imported Word docs use sidecar persistence only (same behavior as EPUB / read-only PDF).
- Export path:
  - “Export annotated PDF” produces a PDF with original content + overlay marks.
- Ensure recents/viewport are keyed by the Word doc’s `docId` (not the cache PDF filename) so rename/move works.
- Definition of done:
  - Reopen/imported doc restores viewport and annotations reliably.

W4 — Fixtures + docs
- Add a tiny `test_assets/word_with_text.docx` fixture containing a stable token.
- Document prerequisites:
  - `docs/desktop_linux.md`: LibreOffice requirement for Word import on Linux.
  - `docs/transition.md`: how imported docs behave (Save disabled; export is sharing path).
- Definition of done:
  - CI/smokes cover the “import path exists and is non-blank” on Linux, and “Android shows guidance” on Android.

Recent progress (keep short; older history lives in git + baseline_smoke)
- 2025-12-30: Docs cleanup: removed stale `todo.md` and `cleanup_plan.md`. Commit: `0c903dd7`.
