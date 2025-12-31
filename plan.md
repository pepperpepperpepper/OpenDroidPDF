OpenDroidPDF – Project Plan (Updated 2025-12-31)
================================================

Purpose
Make the project easier to understand, change, and ship by simplifying structure—not just shrinking files. Every step reduces coupling, clarifies ownership (“ONE OWNER”), and removes redundancy while keeping behavior stable and builds green.

What “simpler” means (project goals)
- Clear layering: UI orchestrates; controllers implement flows; services own cross-cutting capabilities; core/adapters talk to MuPDF/native.
- Explicit dependencies: no hidden singletons, no Activity lookups from deep layers. Dependencies are constructor-provided or scope-provided via a small, typed locator.
- Small, named contracts: APIs are capability-oriented (Drawing, Export, Search, Permissions, Recents, Import), not “where code lives.”
- Reader pipeline clarity: views lay out/render; gesture/selection/drawing logic lives in dedicated routers/controllers; avoid duplicated state across view/controller.
- Annotation pipeline clarity: one tool pipeline across document types; persistence backends are swappable (PDF-in-file vs sidecar) without tool logic forking.
- Build hygiene: clean `:app` / `:core` split; deterministic Gradle/R8; F-Droid scripts pull config from one place.

Hard rules (slice discipline)
- ONE OWNER: every concept/process has one obvious home; no “misc helper” buckets.
- One slice at a time: implement → verify → update docs/logs → commit/push.
- Always green: never leave master broken; add/extend a smoke before refactoring risky code.
- No fake APIs in docs: refer to real modules/classes or describe data shapes generically.

Verification defaults (run after each slice)
- Android build/unit tests:
  - `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint`
- Genymotion smokes (baseline):
  - `./scripts/geny_smoke.sh` (PDF)
  - `./scripts/geny_epub_smoke.sh` (EPUB + sidecar)
- Desktop/Linux smoke:
  - `./scripts/linux_smoke.sh`
- Ownership guardrail:
  - `./scripts/one_owner_check.sh`

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
- Completed (tracked in git history + `docs/housekeeping/baseline_smoke.md`):
  - [x] EPUB (sidecar + export + reanchor) (see `docs/transition.md`)
  - [x] Core refactor phases 1–7 (see `docs/architecture.md`)
  - [x] Desktop/Linux parity loop (see `docs/desktop_linux.md` + `C_migration.md`)
- Current focus:
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
- Preferences: scoped settings access + migrations (`PreferencesCoordinator` + `*PrefsStore` interfaces).
- Services/wiring: `ActivityComposition` wires activity-scoped controllers/adapters and `AppServices` provides app-scoped stores/services; no generic “misc helper” buckets.

Dependency rules (enforced)
- Directional only: Activity → controllers/services → views/core. Views must not reach into Activity.
- No cycles between controllers; ownership is singular (each concept has one home in the taxonomy above).
- Shared prefs/files accessed only through the preferences owner (`PreferencesCoordinator` + stores) with documented migrations.
- Scoped object lifetime:
  - App-scope: navigation, permissions, global prefs, recent files index.
  - Document-scope: document session, annotation session, search session, layout profile, page cache.
  - View-scope: gesture state, transient UI bridges.
- The service locator may provide factories, but must not become an ambient global:
  - No `AppServices.get()` calls from low-level views/core (wire dependencies at composition boundaries).
  - Document-scope objects are created by `ReaderComposition` and passed down.

==========================================================
Word Documents Track (.doc/.docx) — “Import as PDF”
==========================================================

Goal
Support opening `.docx` / `.doc` by converting them to PDF and then using the existing PDF pipeline (render/search/annotations/export). This is explicitly “view/annotate the imported PDF”, not “edit the Word document”.

Why this approach (best fit for ONE OWNER + F-Droid)
- MuPDF-facing behavior remains owned by `platform/common/pp_core.*`; Word conversion stays outside `pp_core`.
- Word conversion engines are large/complex. To keep the main app slim, we prefer an optional companion APK (“Office Pack”) that provides conversion.
- The user share artifact stays consistent: “Export annotated PDF” (we never try to write back into `.doc/.docx`).

Alternatives considered (and why they’re not the default)
- Docx → HTML → `WebView`:
  - Good: smaller than LibreOfficeKit; “just render something”.
  - Bad: layout fidelity is inconsistent, and it’s a second rendering stack that doesn’t share the existing page/annotation geometry pipeline.
- Remote conversion service:
  - Good: best fidelity + small APK.
  - Bad: breaks offline use, adds privacy/security risk, and is a non-starter for F-Droid-first expectations.
- Embedded engine in the main APK (e.g. LibreOfficeKit in-app):
  - Good: all-in-one install.
  - Bad: enormous size + complex build; makes the main APK slower to ship and harder to keep green.

Decisions (locked in)
- Word docs are not opened natively; they are imported/converted to PDF first.
- Imported Word docs behave like non-writable docs:
  - “Save into original” is not offered.
  - Annotations are stored in sidecar (keyed by the source Word doc’s identity) and shared via export.
- Conversion is a platform-level pre-processing step (UI/controller ownership), not a `pp_core` feature:
  - `pp_core` owns rendering/search/annotations of the converted PDF, and stays converter-agnostic.
- Android conversion uses a companion “Office Pack” when installed; otherwise we fall back to a guided external conversion flow.
- `.docx` is the v1 target; `.doc` support is best-effort and may be “unsupported on Android” until the Office Pack engine supports it (must be a clear, explicit error, not a crash).
- F-Droid constraint: the main app must not download/execute new code after install; “optional support” must be delivered as an installable package (the Office Pack APK), not a runtime code download.

Office Pack (Android) — packaging + security rules
- The Office Pack is a separate APK (built from the same repo or a sibling repo) with its own `applicationId` (e.g. `org.opendroidpdf.officepack`).
- The main app only binds to the Office Pack if it is signed by the same signing certificate as the main app (prevents a malicious “converter” app from hijacking the flow).
- The main app must not grant broad storage permissions; conversion I/O is via `ParcelFileDescriptor` or app-private temp files + explicit URI grants scoped to the Office Pack.
- Treat documents as untrusted input:
  - no macro execution; conversion must be offline (no network),
  - enforce timeouts and surface errors cleanly (no ANR).

Office Pack (Android) — converter contract (v1)
- A single exported bound service owned by the Office Pack performs conversions (ONE OWNER).
- Data shapes (don’t leak UI state/types like `View`, `Activity`, `SharedPreferences`):
  - `ConversionRequest { sourceUri, sourceMime, sourceDocId, targetPdfUri }`
  - `ConversionResult { ok, errorCode, errorMessage, derivedPdfUri }`
- Minimal requirements:
  - explicit cancel (user can back out),
  - deterministic output path (the main app controls `targetPdfUri`),
  - no “magic” side effects (Office Pack does not write into Recents or app prefs).

Critical implementation invariant (prevents UI/Save regressions)
- The reader session for an imported Word doc must carry:
  - the source Word URI/path (for reopen),
  - the source Word `docId` (sha256 of bytes; canonical identity for sidecar/recents/viewport),
  - the derived PDF URI/path (cache artifact for rendering),
  - an “origin = Word” flag (so UI gating doesn’t accidentally treat the cached PDF as a normal writable PDF).

UX rules
- When a user opens a Word doc:
  - show “Importing…” progress (conversion can be slow),
  - then open the resulting PDF in the reader.
- In the reader UI for imported Word docs:
  - hide/disable “Save” (there is nothing to save “into the .doc/.docx”),
  - keep Share/Print/Export enabled (export produces a PDF),
  - show an info banner “Imported document (Word)” (with a help link/explainer).
- If conversion is unavailable/fails:
  - show an actionable error (install/enable converter; or “Open in another app and export to PDF, then open the PDF”).

Data/identity + caching rules (to keep sidecar stable)
- Canonical doc identity:
  - `docId = sha256(sourceBytes)` of the Word file.
  - Compute off the UI thread (streaming); never read the file twice just to hash.
- Cache key:
  - derived PDF is cached per `docId` (e.g., `<cacheDir>/<docId>.pdf`).
  - Invalidate/reconvert when the source bytes change (docId changes).
- Cache size:
  - keep a bounded cache (LRU by last-opened) so conversions don’t grow disk unbounded.

Implementation slices (each is a small commit: implement → verify → docs → push)

- [x] W0 — Plumbing + gating (Android + Linux)
  - Accept `.docx`/`.doc` in pick/open flows:
    - Android MIME: `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `application/msword`
    - plus filename-extension fallback when providers lie.
    - Desktop open dialog / CLI path handling (where applicable).
  - Add a document-open branch: “Word → import pipeline → open resulting PDF”.
  - Ensure UI gating uses the “origin = Word” signal:
    - Save disabled; Export/Share available; info banner shown.
  - Definition of done:
    - Selecting a `.docx`/`.doc` never crashes; user sees either a converter flow or a clear “conversion unavailable” message.

- [x] W1 — Linux/desktop conversion (LibreOffice headless)
  - Add `test_assets/word_with_text.docx` fixture containing a stable token.
    - (Already added in `0aabfc77`.)
  - Document prerequisites:
    - `docs/desktop_linux.md`: LibreOffice requirement for Word import on Linux.
    - `docs/transition.md`: imported docs behavior (Save disabled; export is the sharing path).
  - Convert using LibreOffice (`soffice`) with a dedicated temporary profile:
    - `soffice --headless --nologo --nolockcheck --nodefault --norestore --invisible -env:UserInstallation=file://<tmpProfileDir> --convert-to pdf --outdir <cacheDir> <input>`
  - Capture logs + exit status; enforce a timeout and show a clear failure message.
  - Add `scripts/linux_docx_import_smoke.sh`:
    - convert `test_assets/word_with_text.docx` → cached PDF
    - render page 1 and assert non-blank
    - optionally assert extracted text contains a known token (prefer `pp_core` text extraction; OCR is last-resort).
  - Definition of done:
    - On Linux, opening `.docx`/`.doc` works end-to-end and can export an annotated PDF (and the fixture + docs prerequisites are in place).

- [x] W2a — Android conversion fallback (no Office Pack installed)
  - When a `.docx`/`.doc` is selected and Office Pack is not installed:
    - show a dialog explaining “Import as PDF”,
    - offer `ACTION_VIEW` (“Open in another app to convert to PDF”) and instructions to return by opening the produced PDF.
  - Add a deterministic Genymotion smoke asserting the dialog is shown (no crash).
  - Definition of done:
    - Android provides a clear user path when Office Pack is absent; no silent failure; Save gating remains correct (“origin=Word”).

- [x] W2b — Android Office Pack integration (skeleton)
  - Main app:
    - implement Office Pack detection + signature verification,
    - bind to the converter service and route conversions through it.
  - Office Pack (new APK / module):
    - ship a minimal converter service implementing the contract, but it may return “conversion unsupported” initially.
  - Add a deterministic Genymotion smoke asserting:
    - when Office Pack is installed + signature matches, the app routes through the Office Pack and surfaces a clear error if conversion is unsupported.
  - Definition of done:
    - In-app conversion path is wired end-to-end with secure binding (even if the engine is stubbed).

- [ ] W2c — Android Office Pack engine (high fidelity)
  - Implement real `.doc/.docx → PDF` conversion inside Office Pack (engine choice TBD; LibreOfficeKit/Collabora is the likely candidate).
  - Update the Genymotion smoke so that with Office Pack installed it:
    - converts a fixture Word file → opens the derived PDF → asserts non-blank render (and optionally a known token via text extraction).
  - Definition of done:
    - With Office Pack installed, `.docx` opens end-to-end and behaves like an imported (non-writable) document.

- [ ] W3 — Sidecar + export semantics for imported docs
  - Imported Word docs use sidecar persistence only (same behavior as EPUB / read-only PDF).
  - Export path:
    - “Export annotated PDF” produces a PDF with original content + overlay marks (using the derived PDF as base).
  - Recents/viewport:
    - key by the Word doc’s `docId` (not the cache PDF filename) so rename/move works.
    - store the source URI/path so reopen re-runs the import pipeline (and reuses cache when docId matches).
  - Definition of done:
    - Reopen restores viewport and annotations reliably (no “lost notes” after rename/move).

Open decisions (parked until W0–W3 are stable)
- Do we want “Import as PDF” to produce a user-visible saved PDF (explicit Save As), or keep it as a cache artifact unless exported?
- For very large Word files, do we keep full sha256 as canonical, or switch to a fast+safe partial-hash strategy?
- Office Pack: do we build it from this repo as a second Gradle application module (simpler dev/repro), or keep it as a separate repo (simpler F-Droid metadata separation)?
- Office Pack: which engine do we accept (fidelity vs size vs build complexity), and do we support `.doc` or only `.docx` initially?
- Do we want a “view-only Word” fallback (docx→HTML/WebView) if conversion is unavailable, or keep the behavior strict (“convert to PDF or show guidance”)?

Recent progress (keep short; older history lives in git + baseline_smoke)
- 2025-12-30: Docs cleanup: removed stale `todo.md` and cleanup plans. Commit: `0c903dd7`.
- 2025-12-31: Plan refresh: consolidated plan and added Word import track. Commit: `d4201d39`.
- 2025-12-31: Word import: pivot Android path to “Office Pack” + fallback; clarify security rules. Commits: `3c62a833`, `f2dda0d6`, `1840d196`.
- 2025-12-31: Word import W0 plumbing (Android + Desktop) + hardened Genymotion smokes (disable flaky IME). Commit: `caf94e44`.
- 2025-12-31: Word import W2a fallback dialog + deterministic docx smoke. Commit: `0aabfc77`.
- 2025-12-31: Word import W1 Linux/desktop conversion (LibreOffice headless) + `scripts/linux_docx_import_smoke.sh`. Commit: `c8f4ef5b`.
- 2025-12-31: Word import W2b Office Pack skeleton (AIDL contract + companion APK + secure binding + new Genymotion smoke). Commit: `c4d503e6`.
