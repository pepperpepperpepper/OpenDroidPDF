OpenDroidPDF – Project Plan (Updated 2026-01-06)
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
  - CI parity (lint + release): `cd platform/android && ./gradlew lint assembleRelease -PopendroidpdfAbi=x86_64 -Popendroidpdf.buildDir=/mnt/subtitled/opendroidpdf-android-build`
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
  - [x] Word documents (`.doc` / `.docx`) import-as-PDF (section below + `docs/word_import.md`)
  - [x] PDF forms (AcroForm widgets) + “Fill & Sign” UX (section below)
- Current focus:
  - [x] FreeText (comment) UX polish (Acrobat-style) (auto-fit/grow + font controls) (section below)

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
  - `docId = sha256:*` content-derived id of the Word file (full hash for small files; sampled for large seekable docs; see `DocumentIdentityResolver`).
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
    - offer an optional “Install Office Pack” action when conversion is unavailable due to missing/mismatched Office Pack.
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

- [x] W2c — Android Office Pack engine (v1: `.docx` text-only)
  - Implement a minimal `.docx → PDF` converter inside Office Pack:
    - parse `word/document.xml` and render paragraphs to a PDF via `pdfbox-android` (so the output PDF has a real text layer).
    - `.doc` (legacy OLE2) is still **unsupported** (explicit error, no crash).
  - Behavior notes (v1 limitations):
    - Layout fidelity is basic (no images/tables/styles beyond plain paragraph text).
    - Searchable-text fidelity is now validated via MuPDF text extraction (not OCR).
  - Update smokes:
    - `scripts/geny_docx_officepack_smoke.sh` now asserts: conversion routed → document view → non-blank render → MuPDF text extraction finds fixture token (OCR optional).
    - `scripts/geny_docx_fallback_smoke.sh` now uninstalls Office Pack first so the fallback path is deterministic.
  - Definition of done:
    - With Office Pack installed, `.docx` opens end-to-end and behaves like an imported (non-writable) document.

- [x] W2d — Android Office Pack engine (high fidelity)
  - Improve fidelity beyond plain paragraphs while preserving a searchable text layer.
  - Current approach: extend the existing `pdfbox-android` renderer (no heavy LibreOfficeKit dependency) with:
    - basic tables (`<w:tbl>` → grid + wrapped cell text),
    - embedded images (`document.xml.rels` image relationships → bitmap → PDF image).
  - Definition of done:
    - add a DOCX “edge” fixture (tables + at least one image): `test_assets/word_edge.docx`,
    - conversion renders the edge fixture recognizably (image presence is asserted via a red-pixel heuristic),
    - MuPDF text extraction still finds the expected token (no OCR gating).

- [x] W3 — Sidecar + export semantics for imported docs
  - Imported Word docs use sidecar persistence only (same behavior as EPUB / read-only PDF).
  - Export path:
    - “Export annotated PDF” produces a PDF with original content + overlay marks (using the derived PDF as base).
  - Recents/viewport:
    - key by the Word doc’s `docId` (not the cache PDF filename) so rename/move works.
    - store the source URI/path so reopen re-runs the import pipeline (and reuses cache when docId matches).
  - Definition of done:
    - Reopen restores viewport and annotations reliably (no “lost notes” after rename/move).

Decisions (current implementation)
- “Import as PDF” keeps the derived PDF as a cache artifact; sharing happens via OpenDroidPDF’s export/share pipeline.
- Doc identity uses a fast+safe `sha256:*` strategy (full hash for small files; sampled head/middle/tail for large seekable docs).
- Office Pack is built from this repo as a separate Gradle application module (`:officepack`) and distributed as a separate APK (`org.opendroidpdf.officepack`).
- Android support is `.docx` (v1); `.doc` remains unsupported on Android (explicit error, no crash).
- Conversion is strict: convert-to-PDF (Office Pack or external app) or show guidance; no WebView “view-only Word” fallback.

==========================================================
PDF Forms + Fill & Sign Track — “Fill out documents fully”
==========================================================

Goal
Make OpenDroidPDF feel like a complete “fill out this PDF” app by supporting both:
- **AcroForm widgets** (real PDF form fields), and
- **Fill & Sign** workflows (for PDFs with no form fields, often scanned/flattened).

Reality check (current state)
- AcroForm widgets: supported for common field types (text, checkbox, radio, choice) with highlight + field nav + persistence + flatten export; XFA is detected and warned.
- Fill & Sign: reusable signature/initials + common stamps with placement safety + flatten export.
- FreeText (comment) UX: aligned with Acrobat-ish expectations (auto-fit/grow unless user-resized + explicit font controls) with Genymotion regression coverage.

P0 — Immediate tasks (do next)
- [x] Verify AcroForm **text field** filling end-to-end on Android using `test_assets/pdf_form_text.pdf` (token entry → Save → reopen → value persists).
- [x] Add a deterministic Genymotion smoke for form filling:
  - `scripts/geny_pdf_form_fill_smoke.sh` fills a text field + saves + reopens to verify persistence.
- [x] Add a visible Forms entrypoint:
  - toolbar `Forms` toggle that highlights form fields (widget bounds) on-page.
- [x] Add an expanded AcroForm fixture (+ smoke coverage) for:
  - checkbox toggle + radio group selection,
  - dropdown selection (combo/list choice),
  - signature field interaction (at least “detect + prompt”; signing may be optional).
  - Fixture: `test_assets/pdf_form_widgets.pdf`
  - Smoke: `scripts/geny_pdf_form_widgets_smoke.sh`

P1 — Make widget filling feel complete
- [x] Field navigation: “Next field / Previous field” and IME “Next” on text input.
  - Toolbar actions: `Next field` / `Previous field` (visible when `Forms` highlight is enabled).
  - Smoke: `scripts/geny_pdf_form_nav_smoke.sh` using `test_assets/pdf_form_nav.pdf` (2 pages).
- [x] Improve text field UX: preserve selection/value and keyboard ergonomics.
  - Select-all on open for quick replace.
  - Heuristic single-line input for wide fields (avoids multiline/enter friction for typical name/address fields).
- [x] Reduce modal friction: inline form field editing (non-modal) for text fields.
  - Tap a text field to edit in-place (inline editor overlay).
  - Commit on focus loss; IME Next/Done supported (still supports Next-field navigation).
- [x] Widget coverage: confirm/check/fix checkboxes + radio groups, multi-select listboxes, editable comboboxes.
  - [x] Multi-select listboxes + editable comboboxes:
    - Plumbing added (native -> `PassClickResultChoice`) so the UI can choose multi-select vs editable flows.
    - UI added:
      - multi-select list dialog (checkbox list + OK),
      - editable combo dialog (single-line text entry + OK).
    - Fixture: `test_assets/pdf_form_choice_advanced.pdf`
    - Smoke: `scripts/geny_pdf_form_choice_advanced_smoke.sh` (green; saves + reopens and verifies persistence).
- [x] Save/dirty tracking: ensure widget edits reliably trigger “document changed” behavior.
- [x] Export options: optional “Flatten form fields + annotations” export for maximum viewer compatibility.
- [x] Compatibility messaging: detect and warn about unsupported forms (notably XFA) with a clear user-facing explanation.

P2 — Fill & Sign features (non-form PDFs)
- [x] Reusable signature + initials: capture once, store locally, and place many times with move/scale/rotate.
- [x] One-tap stamps: checkmark / X / date / name.
- [x] Placement-mode safety: suppress accidental page turns while placing/moving signatures/stamps; keep resizes deliberate.
- [x] Flatten export: produce a final shareable PDF with placed marks baked in.

FreeText (Comment) vs “Add Text” (Fill & Sign) — Acrobat-like behavior
- Acrobat has two text workflows that look similar but behave differently:
  - Commenting → **Free Text** (annotation): a text *container*. Resizing changes wrapping/layout and does **not** change font size.
    - Default box is small/content-fitting (no huge empty rectangle).
    - While editing, the box auto-grows to fit the text (at least height). Once the user resizes, respect that width and wrap.
  - Fill & Sign → **Add Text** (stamp-like): a text *stamp*. Bounds stay **tight** to glyphs; size is controlled via explicit font controls (A+/A-) or pinch, not by dragging a large container.
- OpenDroidPDF decision: treat the sidebar “Text” tool as **annotation**, not “true PDF content editing”.
- Previous OpenDroidPDF gap (fixed): FreeText creation used a page-percent default rect and did not auto-fit/grow while editing, resulting in large empty selection boxes and unintuitive resize behavior.
- Interaction expectations (Acrobat-ish):
  - Selecting/moving/resizing a text annotation must not pan/turn pages (allow 2-finger pan/zoom or explicit nav while selected).
  - Dragging keeps the text visible (live preview while moving/resizing).
  - Move is easy; resize is deliberate (handles only; avoid confusing affordances like a “mystery plus”).
- Work items (close the gap)
  - [x] Suppress page turns while dragging/moving FreeText.
  - [x] Make the move affordance unambiguous (no “mystery plus”).
  - [x] Keep text visible while dragging/resizing + Genymotion smoke coverage.
  - [x] Rework default FreeText placement size (dp-based, not page-percent) and align with Acrobat defaults.
  - [x] Auto-fit/grow FreeText bounds to content while editing until the user explicitly resizes (persist a `userResized` bit).
  - [x] Add a “Fit to text” action in the text properties UI to tighten bounds after edits.
  - [x] Add explicit font controls (size, color, alignment) for FreeText; resizing must never scale font.
  - [x] Add/extend Genymotion smokes to cover: auto-fit on edit + resize-wrap behavior.

Next (proposed)
- [x] AcroForm signature fields: implement real signing (PKCS#7) so users can sign *form signature widgets* (not just place a drawn signature overlay).
  - Android: key import (PKCS#12) + signing UI; Linux: OpenSSL-backed signing when `ENABLE_OPENSSL=yes`.
  - Add a fixture PDF with a signature field and a deterministic smoke that asserts the signed PDF contains PKCS#7 (`/ByteRange`, `/SubFilter /adbe.pkcs7.detached`, `/Contents <...>`) and renders a “Digitally signed by …” appearance.
- [x] Improve forms fidelity: regenerate widget appearance streams more consistently (fonts/line breaks) and validate in third-party viewers (Adobe/Chrome).
  - Fix: escape text safely when generating multiline text widget appearance streams (backslashes/parentheses/etc) so poppler/other viewers render consistently (`source/pdf/pdf-appearance.c`).
  - Fixture: `test_assets/pdf_form_multiline.pdf`
  - Smoke: `scripts/geny_pdf_form_multiline_smoke.sh` (asserts poppler `pdftotext` sees a literal backslash sequence; OCR optional)
- [x] Testing hygiene: migrate the legacy UI Automator runner used by smokes to UIAutomator 2 (remove deprecation warnings; keep scripts stable).
  - Add standalone instrumentation APK runner (`platform/android/uia_runner`) with UIAutomator2 `ZoomPinchTest`.
  - Replace `uiautomator runtest` jar usage in smokes with `adb shell am instrument` via `uia_runner_run_test` helper (`scripts/geny_uia.sh`).

Backlog (completed UX polish)
- [x] Text annotation move/resize: ensure the annotation text stays visible continuously while dragging/resizing (no disappear/reappear), including when MuPDF does not populate `Annotation.text` (fallback to a cached value or a lightweight query).
- [x] Text annotation move handle: replace/verify any ambiguous “+” affordance with a clear drag-grip icon and ensure interacting with it never changes the bounds.
- [x] Text annotation resize: keep resize deliberate (explicit mode/long-press + handles only) and tune hit slop so accidental resizes are rare.
- [x] Sidecar note (TEXT) bounds: apply the same Acrobat-like auto-fit/grow + “user resized” persistence used for embedded FreeText so the selection box tracks content instead of staying overly large.
- [x] Gesture conflicts: while a text box is being manipulated, suppress page navigation/panning, but keep 2-finger pan/zoom working; add/extend a Genymotion regression if needed.

Backlog (source control / housekeeping)
- [x] Ignore transient smoke artifacts like `tmp_*_ui.xml` so failure dumps don’t pollute `git status` (keep artifacts on disk; just don’t track them).
- [x] Split the current working tree into commits and push upstream (commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).

Platform/release notes
- Linux: PDF digital signatures are opt-in via `ENABLE_OPENSSL=yes` (builds on OpenSSL 1.1+/3.x); baseline: `ENABLE_OPENSSL=yes ./scripts/linux_smoke.sh` (see `docs/desktop_linux.md`).
- F-Droid: deployed `1.3.66 (127)` on 2026-01-05 via `./scripts/fdroid_build.sh` + `./scripts/fdroid_deploy.sh` (repo: `https://fdroid.uh-oh.wtf/repo`).

Recent progress (keep short; older history lives in git + baseline_smoke)
- 2025-12-30: Docs cleanup: removed stale `todo.md` and cleanup plans. Commit: `0c903dd7`.
- 2025-12-31: Plan refresh: consolidated plan and added Word import track. Commit: `d4201d39`.
- 2025-12-31: Word import: pivot Android path to “Office Pack” + fallback; clarify security rules. Commits: `3c62a833`, `f2dda0d6`, `1840d196`.
- 2025-12-31: Word import W0 plumbing (Android + Desktop) + hardened Genymotion smokes (disable flaky IME). Commit: `caf94e44`.
- 2025-12-31: Word import W2a fallback dialog + deterministic docx smoke. Commit: `0aabfc77`.
- 2025-12-31: Word import W1 Linux/desktop conversion (LibreOffice headless) + `scripts/linux_docx_import_smoke.sh`. Commit: `c8f4ef5b`.
- 2025-12-31: Word import W2b Office Pack skeleton (AIDL contract + companion APK + secure binding + new Genymotion smoke). Commit: `c4d503e6`.
- 2025-12-31: Word import W2c v1 Office Pack converter (DOCX text-only) + fix async Word open to attach doc view. Commit: `42659d4a`.
- 2025-12-31: Word import W3: key sidecar/recents by Word `docId` (sha256), reuse cached derived PDF, and validate via `scripts/geny_docx_officepack_smoke.sh`. Commit: `0ebf762a`.
- 2025-12-31: Word import W2c follow-up: Office Pack `.docx → PDF` output now has a real text layer (pdfbox-android), and the Genymotion smoke asserts MuPDF text extraction (OCR optional). Commit: `8b8b27cb`.
- 2026-01-01: Word import W2d: Office Pack now renders basic tables + images (edge fixture + updated Genymotion smoke). Commit: `d0f4d2b4`.
- 2026-01-01: F-Droid: build/sign both OpenDroidPDF + Office Pack and track metadata for the companion APK. Commit: `a4398875`.
- 2026-01-01: Release 1.3.63 (124) + publish Office Pack to the self-hosted F-Droid repo. Commits: `9bfb1566`, `f2d280a1`.
- 2026-01-01: Android CI: fix lint errors (missing translations + log tag) so `./gradlew lint assembleRelease` passes. Commit: `11a5e8b3`.
- 2026-01-01: Word import: clarify legacy `.doc` unsupported messaging and add a deterministic Genymotion smoke. Commit: `4c00eb9b`.
- 2026-01-01: Word import: offer an “Install Office Pack” action in the Import-as-PDF dialog when Office Pack is missing/mismatched/unsupported. Commit: `e494c3b0`.
- 2026-01-01: Release `1.3.64 (125)` published to `https://fdroid.uh-oh.wtf/repo` (OpenDroidPDF + Office Pack). Commit: `8d73fe84`.
- 2026-01-01: Genymotion smokes: disable flaky “New Soft Keyboard Dev” IME by default in `scripts/geny_uia.sh` (opt-out via `UIA_DISABLE_FLAKY_IME=0`). Commit: `2f26e935`.
- 2026-01-01: Genymotion smokes: add signed-release “zoom → idle → pan” crash-watch (`scripts/geny_release_zoom_pan_watch_smoke.sh`). Commit: `034274d9`.
- 2026-01-01: F-Droid: fix “updates not recommended” drift by syncing repo metadata into `$FDROIDCONFDIR` before `fdroid update`; add `scripts/fdroid_index_refresh.sh` and enforce suggestedVersionCode==latest in deploy verification. Commit: `9d5d6940`.
- 2026-01-04: Forms P0: add `Forms` highlight toggle + add `scripts/geny_pdf_form_fill_smoke.sh` (AcroForm text field fill persists). Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=<adb> ./scripts/geny_pdf_form_fill_smoke.sh` (commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-04: Forms P0 follow-up: add `test_assets/pdf_form_widgets.pdf` + `scripts/geny_pdf_form_widgets_smoke.sh` (text/checkbox/radio/choice persist; signature field prompts). Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=<adb> ./scripts/geny_pdf_form_widgets_smoke.sh` (commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-04: Forms P1: add field navigation (Next/Previous field + IME Next on text entry) + `scripts/geny_pdf_form_nav_smoke.sh`. Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=<adb> ./scripts/geny_pdf_form_nav_smoke.sh` (commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-04: Forms P1: improve text widget dialog ergonomics (select-all + single-line heuristic). Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=<adb> ./scripts/geny_pdf_form_fill_smoke.sh` (commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-04: Forms P1: multi-select listbox + editable combobox support (fixture + smoke stabilized). Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=<adb> ./scripts/geny_pdf_form_choice_advanced_smoke.sh` (commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-04: Forms P1: widget edits mark dirty, warn on XFA forms, and add “Share flattened PDF…” for compatibility exports. Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=<adb> ./scripts/geny_smoke.sh` + `DEVICE=<adb> ./scripts/geny_epub_smoke.sh` (commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-04: Forms P1: inline AcroForm text-field editing (non-modal) + updated smokes. Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=<adb> ./scripts/geny_pdf_form_fill_smoke.sh` + `DEVICE=<adb> ./scripts/geny_pdf_form_widgets_smoke.sh` + `DEVICE=<adb> ./scripts/geny_pdf_form_nav_smoke.sh` + `DEVICE=<adb> ./scripts/geny_pdf_form_choice_advanced_smoke.sh` + `DEVICE=<adb> ./scripts/geny_smoke.sh` + `DEVICE=<adb> ./scripts/geny_epub_smoke.sh` (commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-04: Fill & Sign P2: add reusable signature/initials + stamps + placement safety + new smoke `scripts/geny_fill_sign_smoke.sh`. Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=<adb> ./scripts/geny_fill_sign_smoke.sh` + `DEVICE=<adb> ./scripts/geny_smoke.sh` + `DEVICE=<adb> ./scripts/geny_pdf_form_fill_smoke.sh` (commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-04: F-Droid: deployed `1.3.65 (126)` to `https://fdroid.uh-oh.wtf/repo` (OpenDroidPDF + Office Pack). Verified: `cd platform/android && ./gradlew lint assembleRelease -PopendroidpdfAbi=x86_64 -Popendroidpdf.buildDir=/mnt/subtitled/opendroidpdf-android-build` + `./scripts/fdroid_build.sh` + `./scripts/fdroid_deploy.sh` + `./scripts/one_owner_check.sh` + `DEVICE=<adb> ./scripts/geny_smoke.sh` + `DEVICE=<adb> ./scripts/geny_fill_sign_smoke.sh` + `DEVICE=<adb> ./scripts/geny_pdf_form_fill_smoke.sh` (commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-04: Linux: modernize OpenSSL signature backend to build on OpenSSL 3 when enabled; `scripts/linux_smoke.sh` now accepts `ENABLE_OPENSSL=yes`. Verified: `ENABLE_OPENSSL=yes ./scripts/linux_smoke.sh` (commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-04: Re-verify end-to-end builds + smokes (current working tree). Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=localhost:35329 ./scripts/geny_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_epub_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_fill_sign_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_fill_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_widgets_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_nav_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_choice_advanced_smoke.sh` + `./scripts/linux_smoke.sh` + `ENABLE_OPENSSL=yes ./scripts/linux_smoke.sh` (PASS; commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-04: CI parity + F-Droid publish verification. Verified: `cd platform/android && ./gradlew lint assembleRelease -PopendroidpdfAbi=x86_64 -Popendroidpdf.buildDir=/mnt/subtitled/opendroidpdf-android-build` + `./scripts/one_owner_check.sh` + `./scripts/fdroid_build.sh` + `./scripts/fdroid_deploy.sh` (PASS; published 1.3.65 (126)).
- 2026-01-05: Stabilize `scripts/geny_pdf_form_widgets_smoke.sh` (retry taps when opening inline text editor after reopen to reduce flakes). Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_fill_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_widgets_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_nav_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_choice_advanced_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_fill_sign_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_epub_smoke.sh` + `./scripts/linux_smoke.sh` + `ENABLE_OPENSSL=yes ./scripts/linux_smoke.sh` + `./scripts/one_owner_check.sh` (PASS; commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-05: Re-verify current working tree end-to-end. Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `cd platform/android && ./gradlew lint assembleRelease -PopendroidpdfAbi=x86_64 -Popendroidpdf.buildDir=/mnt/subtitled/opendroidpdf-android-build` + `DEVICE=localhost:35329 ./scripts/geny_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_epub_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_fill_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_widgets_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_nav_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_choice_advanced_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_fill_sign_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_smoke.sh` + `./scripts/linux_smoke.sh` + `ENABLE_OPENSSL=yes ./scripts/linux_smoke.sh` + `./scripts/one_owner_check.sh` (PASS; commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-05: Add Genymotion coverage for recent PDF UX changes (no-page-turn while dragging FreeText, XFA warning banner), and harden Fill & Sign smoke assertions to avoid coordinate flake. Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=localhost:35329 ./scripts/geny_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_epub_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_no_page_turn_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_xfa_banner_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_fill_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_widgets_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_nav_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_choice_advanced_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_fill_sign_smoke.sh` (PASS; commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-05: F-Droid: deployed `1.3.66 (127)` to `https://fdroid.uh-oh.wtf/repo` (OpenDroidPDF + Office Pack). Verified: `./scripts/fdroid_build.sh` + `./scripts/fdroid_deploy.sh` (PASS; repo shows `versionName=1.3.66 versionCode=127`).
- 2026-01-05: Text annotation UX: make the move handle less ambiguous (replace “+” glyph with a drag grip), add an in-overlay text preview so text stays visible while dragging/resizing, and harden the Genymotion smoke to assert text-in-box during an in-progress drag. Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_no_page_turn_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_smoke.sh` (PASS; commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-05: FreeText (comment) UX: dp-based default box, auto-fit/grow after edits (unless user-resized), alignment + style dialog, and new auto-fit smoke. Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `cd platform/android && ./gradlew connectedDebugAndroidTest -x lint` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_no_page_turn_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_autofit_smoke.sh` (PASS; commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-05: Fix desktop build + harness parity: correct `fz_resize_array` sizing in `pdf-appearance.c`, write `OPDUserResized` metadata in a MuPDF-version-compatible way (Android vs desktop), and make `scripts/linux_smoke.sh` use separate build output dirs per OpenSSL toggle to avoid stale-object link failures. Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `cd platform/android && ./gradlew lint assembleRelease -PopendroidpdfAbi=x86_64 -Popendroidpdf.buildDir=/mnt/subtitled/opendroidpdf-android-build` + `cd platform/android && ./gradlew connectedDebugAndroidTest -x lint` + `DEVICE=localhost:35329 ./scripts/geny_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_epub_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_fill_sign_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_fill_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_widgets_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_nav_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_choice_advanced_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_xfa_banner_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_no_page_turn_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_autofit_smoke.sh` + `./scripts/linux_smoke.sh` + `ENABLE_OPENSSL=yes ./scripts/linux_smoke.sh` + `./scripts/one_owner_check.sh` (PASS; commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-06: Execute `plan.md` verification loop end-to-end (Android unit/lint/release + connected tests, Genymotion smokes, Linux smokes, ownership guardrail). Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `cd platform/android && ./gradlew lint assembleRelease -PopendroidpdfAbi=x86_64 -Popendroidpdf.buildDir=/mnt/subtitled/opendroidpdf-android-build` + `cd platform/android && ./gradlew connectedDebugAndroidTest -x lint` + `DEVICE=localhost:35329 ./scripts/geny_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_epub_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_fill_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_widgets_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_nav_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_choice_advanced_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_fill_sign_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_xfa_banner_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_no_page_turn_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_autofit_smoke.sh` + `./scripts/linux_smoke.sh` + `ENABLE_OPENSSL=yes ./scripts/linux_smoke.sh` + `./scripts/one_owner_check.sh` (PASS; commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`). Test coverage is adequate for current changes (Genymotion smokes already target the newest FreeText/forms/fill-sign UX).
- 2026-01-06: Forms P2: implement PKCS#7 signing for AcroForm signature widgets (enable OpenSSL-backed signing in the Android NDK build, use SAF for PKCS#12 selection + temp-copy content Uris, add `scripts/geny_pdf_form_sign_smoke.sh`). Verified: `DEVICE=localhost:35329 ./scripts/geny_pdf_form_sign_smoke.sh` + `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `cd platform/android && ./gradlew connectedDebugAndroidTest -x lint` + `cd platform/android && ./gradlew lint assembleRelease -PopendroidpdfAbi=x86_64 -Popendroidpdf.buildDir=/mnt/subtitled/opendroidpdf-android-build` + `./scripts/linux_smoke.sh` + `ENABLE_OPENSSL=yes ./scripts/linux_smoke.sh` + `./scripts/one_owner_check.sh` (PASS; commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-06: Genymotion smokes: fix `geny_pinch_zoom_smoke.sh` false-negative pan assertion by diffing only on the non-white content region (content may be edge-aligned after heavy zoom). Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug connectedDebugAndroidTest -x lint` + `DEVICE=localhost:35329 ./scripts/geny_pinch_zoom_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_epub_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_fill_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_widgets_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_nav_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_choice_advanced_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_fill_sign_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_xfa_banner_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_no_page_turn_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_autofit_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_form_sign_smoke.sh` (PASS; commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-06: Text annotation UX polish backlog: keep text visible during move/resize (including sidecar), make resize deliberate (no accidental “mystery plus”), apply sidecar note auto-fit + user-resized persistence, and make sidecar bundle import smoke robust to note-text prompts. Verified: `cd platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_pdf_text_annot_no_page_turn_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_epub_smoke.sh` + `DEVICE=localhost:35329 ./scripts/geny_sidecar_bundle_import_smoke.sh` (PASS; commits: `bdf9d24a`, `f689dcc8`, `ea86fc1c`).
- 2026-01-06: F-Droid: rebuilt + deployed `1.3.66 (127)` to `https://fdroid.uh-oh.wtf/repo` (OpenDroidPDF + Office Pack). Verified: `./scripts/fdroid_build.sh` + `./scripts/fdroid_deploy.sh` (PASS; repo shows `versionName=1.3.66 versionCode=127`).
