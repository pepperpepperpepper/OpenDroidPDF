# Plan: Vertical vs Horizontal Page Paging (Android)

## Goal
Add a user-facing setting that switches the document viewer’s page navigation between:
- **Horizontal paging** (current behavior: swipe left/right to change pages)
- **Vertical paging** (new: swipe up/down to change pages)

Default stays **horizontal** to preserve existing UX.

## Status (as of 2026-01-14)
- [x] Add preference + settings UI (`pref_page_paging_axis`)
- [x] Wire preference through `ViewerPrefsSnapshot` → `SharedPreferencesViewerPrefsStore` → `ReaderView.applyViewerPrefs()`
- [x] Add `PagingAxis` enum and thread through layout + fling routing
- [x] Add instrumentation coverage (toggle pref + verify page movement)
- [x] QA pass on a tall-page PDF (ensure in-page scroll still works; page switch occurs at edge/overscroll)
  - Smoke: `DEVICE=localhost:<port> ./scripts/geny_paging_axis_tall_pdf_smoke.sh`

## UX/Behavior Decisions (define before coding)
- **Naming:** “Page swipe direction” or “Page navigation direction”.
- **What changes:** only **page-to-page** navigation axis. In-page pan remains 2D (as today).
- **Gesture precedence:**
  - If the page can still scroll within bounds on the chosen axis, keep scrolling within the page.
  - Only when at the axis edge (within the fling margin) should a fling switch pages.
- **Tap margins:** keep existing behavior (top/left = back, bottom/right = forward) unless it feels inconsistent.

## Implementation Steps

### 1) Add preference + settings UI
- Add a new preference key (recommend list preference):
  - `pref_page_paging_axis = horizontal|vertical`
- Add it to `platform/android/res/xml/preferences.xml` under Display settings. ✅
- Add strings/arrays in `platform/android/res/values/strings_settings.xml` and `platform/android/res/values/arrays_settings.xml`. ✅

### 2) Wire preference through the prefs layer
- Extend `platform/android/src/org/opendroidpdf/app/preferences/ViewerPrefsSnapshot.java` with:
  - `pagingAxis` (enum-like String) or `boolean verticalPaging`
- Update `platform/android/src/org/opendroidpdf/app/preferences/SharedPreferencesViewerPrefsStore.java` to load it. ✅
- Update `platform/android/src/org/opendroidpdf/ReaderView.java#applyViewerPrefs` to apply it. ✅

### 3) Introduce a small “paging orientation” abstraction
- Add a tiny type to avoid boolean soup (e.g., `PagingAxis { HORIZONTAL, VERTICAL }` in `app.reader`).
- ReaderView exposes `isVerticalPaging()` (or `pagingAxis()`).
✅ Implemented as `platform/android/src/org/opendroidpdf/app/reader/PagingAxis.java` and stored on `ReaderView`.

### 4) Make ReaderView + layout switching support vertical neighbors
The current layout assumes left/right neighbors and switches pages when the current view slides past the center.

Update the extracted helper(s) to handle both axes:
- `platform/android/src/org/opendroidpdf/app/reader/LayoutSwitchHelper.java`
  - Generalize `shouldMoveNext/Prev` to use either:
    - **horizontal:** current logic (left/right)
    - **vertical:** analogous logic using `top/height` and container half-height
- `LayoutSwitchHelper.layoutCurrentAndNeighbors(...)`
  - For vertical paging: layout the previous page **above** and the next page **below** (instead of left/right).

Acceptance: with paging set to vertical, a page switch changes the `mCurrent` index and lays pages stacked vertically.
✅ Implemented (axis-aware switch thresholds + above/below neighbor layout).

### 5) Update fling-to-page-switch logic to respect paging axis
`platform/android/src/org/opendroidpdf/app/reader/GestureRouter.java#onFling` currently only page-switches on left/right travel.

Add vertical switching behavior:
- If paging axis is vertical:
  - On **MOVING_UP** / **MOVING_DOWN**, consult `bounds.top/bottom` similarly to `bounds.left/right`.
  - Map direction so “swipe up” advances (next page) and “swipe down” goes back.
- Keep existing horizontal behavior unchanged when axis is horizontal.
✅ Implemented (axis-gated: horizontal uses left/right, vertical uses up/down).

### 6) Validation + regression testing
- Add a small instrumentation test that:
  - sets the preference
  - opens a multi-page PDF
  - performs a swipe on the configured axis
  - asserts page index changes
- Update/extend an existing Genymotion smoke script to:
  - toggle the setting
  - verify page movement happens via the expected swipe direction.

## Acceptance Criteria
- Setting exists and persists.
- Horizontal mode: behavior unchanged.
- Vertical mode: swipe up/down changes pages; left/right no longer required for page-to-page.
- No regressions for: zoom/pan, ink, text annotations, form widgets, search result navigation.

## Out of Scope (future)
- “Continuous vertical scroll” (true stacked scrolling through many pages without discrete page switching).
- Two-page spreads / facing-pages modes.
- Per-document override (global-only for first pass).

---

# Next: Reading Navigation (Fast Page Switching)

## Goal
Make page-to-page navigation **fast** and **discoverable** while reading.

Today, swipe-to-change-page works, but feels **sluggish** and there’s no obvious “quick nav” affordance.

## UX Proposal
- **Tap the page indicator** (e.g., `1 / 200`) to open a lightweight **page switcher**.
- Page switcher includes:
  - a **slider/scrubber** to move quickly across pages
  - an optional **page number input** (jump-to-page)
  - optional **step buttons** (prev/next) for one-page moves without swiping
- Optional (future): thumbnail preview while scrubbing (minimap-like), but not required for v1.

## Status (as of 2026-01-21)
- [x] Add a page scrubber + prev/next buttons to the page-indicator “Navigate & View” sheet.
- [x] Add instrumentation coverage for the page scrubber buttons.
- [x] Capture and share a screenshot of the new page switcher UI (so you can verify you’re seeing the intended sheet). (See `tmp_geny_page_switcher.png`)
- [x] If users still can’t find it: add a small in-app hint (“Tap 1 / N to navigate”) or make the affordance more obvious.
- [x] Add “Reading mode” toggle (hide toolbar) and verify page-indicator tap still opens the switcher.
- [x] Avoid “white flash” on page switches: double-buffer full-page renders (don’t clear the currently-displayed bitmap).
- [x] UX: Make the page scrubber always available on-page (no “tap twice to reach scrubber”; ensure it sits above system nav bars/insets).
- [x] QA on Genymotion: large PDF + repeated page switching should feel immediate and avoid “white box” flashes.
  - Script: `scripts/geny_release_page_switcher_watch_smoke.sh` (repeated next/prev, screenshots + blank-ish detection).
  - **Genymotion etiquette:** instances are shared; **do not stop/kill/disconnect** a running instance you didn’t start (it may be executing another smoke). If capacity is exhausted (`TOO_MANY_RUNNING_VDS`), **wait your turn**. Only spin up a separate instance if we absolutely need parallel QA and quota allows.
  - 2026-01-21: smoke passed (LOOPS=4) and captured screenshots/logcat.

## Issue: Scrubber feels laggy (page update latency)

### Symptom
- Dragging the scrubber updates the thumb/label immediately, but the rendered document page can lag behind the thumb on large PDFs, which feels sluggish.

### Plan (as of 2026-01-22)
- [x] Confirm baseline behavior: both the on-page scrubber and the Navigate & View sheet scrubber only navigate on `onStopTrackingTouch`.
- [x] Implement **live scrubbing**: while dragging, navigate to the current scrubber page with a small **throttle** (avoid firing a full page switch for every pixel).
  - Apply to on-page scrubber (`platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java`).
  - Apply to Navigate & View sheet scrubber (`platform/android/src/org/opendroidpdf/app/document/DocumentToolbarController.java`).
- [x] Improve responsiveness: defer `setNormalizedScroll(0,0)` until scrub release (avoid extra layout work during active dragging).
- [x] Fix crash during rapid scrubbing: `PageView.redraw()` must tolerate a missing overlay view while a page is being reused/reset.
- [x] Add guardrails:
  - Don’t navigate when the change is programmatic (`fromUser=false`).
  - Skip redundant switches (target page already current).
  - Cancel any pending throttled jump when scrubbing stops (and apply the final page immediately).
- [x] QA:
  - [x] Build sanity: `platform/android` → `./gradlew assembleDebug`.
  - [x] Large PDF: rapid scrub across far pages; confirm visible page updates quickly and does not crash.
    - Script: `scripts/geny_page_scrubber_smoke.sh` (builds a many-page PDF via `pdfunite`, scrubs to end/back, screenshots + logcat crash check).
  - [x] Ensure the final page always matches the scrubber position on release.
  - [x] Ensure no regressions for zoom/pan + annotations.
    - Script: `scripts/geny_pinch_zoom_smoke.sh` (pinch-zoom + pan diff).
    - Script: `scripts/geny_eraser_smoke.sh` (draw/erase pixel diff).

### Follow-up Plan: Make page render keep up while dragging (as of 2026-01-23)
- [x] Confirm the lag source (render time vs. navigation): add log markers around scrub navigation and first-patch render.
- [x] Add a **Scrub Render Mode**:
  - While the user is actively dragging a page scrubber, render the target page at a **lower raster resolution** (fast preview) so the visible page changes quickly.
  - On scrub release, switch back to full resolution and trigger a redraw so the final page is crisp.
  - Apply to both on-page scrubber and Navigate & View sheet scrubber.
- [x] Tighten Scrub Render Mode for latency:
  - Cap the preview bitmap to a small pixel budget (currently ~80k pixels) so “entire” renders are fast even on large/tall pages.
  - Suppress/cancel HQ patch rendering while scrubbing (only show the lightweight “entire” preview).
  - Reduce scrub navigation throttle slightly (80ms → 60ms) to make the page track the thumb more tightly without flooding switches.
- [x] QA:
  - [x] Large PDF: drag-scrub across far pages and verify the visible page updates continuously (no “stuck on old page”), then settles to sharp render after release.
    - 2026-01-23: `DEVICE=localhost:<port> SWIPE_MS=1400 ./scripts/geny_page_scrubber_smoke.sh` shows `first patch rendered ... scrub=true` at the target pages (fast preview), then sharpens on release.
  - [x] Ensure no new crashes/blank pages during aggressive scrubbing (logcat clean).

### Follow-up Plan: Reduce work per scrub step (as of 2026-01-24)
- [x] While scrubbing, **don’t create/render neighbor pages** (only lay out the current page) to avoid doing ~3x render work for each thumb move.
  - Implemented by skipping neighbor layout in `LayoutSwitchHelper.layoutCurrentAndNeighbors(...)` when `ReaderView.isScrubbing()==true`.
- [x] Ensure neighbors come back after scrub release by requesting a layout pass whenever scrubbing toggles.
- [x] Re-run Genymotion scrub smoke (`scripts/geny_page_scrubber_smoke.sh`) and validate page updates “track the thumb” with minimal lag. (2026-01-24: passed)

### Follow-up Plan: Defer non-essential work while scrubbing (as of 2026-01-24)
- [x] While scrubbing, **skip background loads** that compete with rendering (links/text/annotations). Only render the page preview.
- [x] Avoid UI churn on page switches while scrubbing: keep `PageOverlayView` across page reuse (don’t remove/recreate it in `PageView.reset()`).
- [x] On scrub release (final page), load deferred links/text/annotations and request an HQ redraw.
- [x] QA: re-run `scripts/geny_page_scrubber_smoke.sh` on a large PDF and confirm page updates track the thumb with less perceived lag. (2026-01-25: passed; REPEAT=180)

### Follow-up Plan: Fix scrub “stale/blank page” latency (as of 2026-01-25)
- Symptom: while scrubbing, the thumb/label moves immediately but the visible page can stay on the *previous* page (or go blank) until much later.
- Hypothesis: the low-res “entire” render is being skipped on some page switches because `PagePatchView` gates rendering on `PageView.isPageReady()`, but `mPageReady` can still be `false` when `addEntire()` is called (especially when the adapter reuses a view that was `resetForReuse()`'d).

- [x] Move the `mPageReady=true` transition **before** `addEntire()` in `PageView.setPage(...)`, and ensure `reset()` sets `mPageReady=false`.
- [x] QA: Re-run `scripts/geny_page_scrubber_smoke.sh` on a large PDF and confirm the visible page updates immediately (no “stuck on old page”, no blank white page while scrubbing).
  - 2026-01-26: `DEVICE=localhost:35329 REPEAT=180 SWIPE_MS=1400 ./scripts/geny_page_scrubber_smoke.sh` (Android 14 / SDK 34) passed.
  - 2026-01-26: `DEVICE=localhost:41073 REPEAT=180 SWIPE_MS=1400 ./scripts/geny_page_scrubber_smoke.sh` (Android 16 / SDK 36) passed.
- [x] Lower scrub throttle (60ms → 30ms) so the visible page can track the thumb more tightly without waiting for long debounce windows.
- [ ] If any lag remains: reduce scrub preview work further (optimize perceived page-update latency while dragging).
  - [x] Lower the on-page preview render budget while scrubbing (tune `SCRUB_ENTIRE_MAX_PIXELS` in `platform/android/src/org/opendroidpdf/PageView.java`).
  - [x] Remove/guard hot-path `Log.d(...)` calls in the render loop (e.g., `MuPdfPatchRenderer`, `PageRenderOrchestrator`) to avoid spending time/string allocs while scrubbing.
  - [x] Coalesce scrub thumbnail renders (one render in-flight; queue latest target) to avoid cancel/restart thrash while dragging.
  - [x] Increase scrub thumbnail LRU cache (12 → 32) so back-and-forth scrubs reuse already-rendered previews.
  - 2026-01-26: `DEVICE=localhost:43947 UIA_DUMP_RETRIES=20 UIA_DUMP_RETRY_SLEEP_S=0.5 REPEAT=180 SWIPE_MS=1400 ./scripts/geny_page_scrubber_smoke.sh` (Android 14 / nsk-android14) passed.
  - [x] Optional: add a thumbnail-only preview while dragging (minimap-style), then render full page on release.
    - [x] Add preview `ImageView` to the on-page scrubber container and Navigate & View sheet.
    - [x] While dragging, render thumbnails only (do not page-switch until release).
    - [x] On release, switch pages once; keep the preview visible until the target page settles; then hide + trigger an HQ redraw.
    - [x] QA: Genymotion scrub smoke passes (2026-01-26: `DEVICE=localhost:43947 UIA_DUMP_RETRIES=20 UIA_DUMP_RETRY_SLEEP_S=0.5 REPEAT=180 SWIPE_MS=1400 ./scripts/geny_page_scrubber_smoke.sh`).
    - [x] While dragging (thumbnail preview mode), keep `ReaderView.setScrubbing(true)` so background renders don't compete with thumbnail rendering.
      - 2026-01-26: `DEVICE=localhost:42373 UIA_DUMP_RETRIES=20 UIA_DUMP_RETRY_SLEEP_S=0.5 REPEAT=180 SWIPE_MS=1400 ./scripts/geny_page_scrubber_smoke.sh` passed.
    - [ ] Manual “feel” check: confirm the drag thumb stays 1:1 with the preview (no perceived lag while dragging).

## Engineering Tasks
- Add a `PageSwitcher` UI (dialog/bottom-sheet) wired to `ReaderView` page index changes.
- Ensure page switching avoids re-render flicker (no “white box” flashes) and feels immediate:
  - prefetch adjacent pages
  - keep animations short (or disable for step buttons)
- Add a setting for “Reading mode” (optional): hide toolbar; show page indicator + page switcher on tap.

## Acceptance Criteria
- User can move **rapidly** to nearby pages (prev/next) and far pages (scrub/jump) without fighting swipe gestures.
- Switching pages does not cause pages to flash white or disappear.

---

# Bug: Dashboard Settings Crash + Crash Report Sharing (Android)

## Goal
- Tapping the **Settings** icon should **never crash**.
- If a crash happens, the in-app crash report prompt must be **exportable** (share/copy/save) so we can debug real-device issues.

## Status (as of 2026-01-21)
- [x] Reproduce the Settings crash on an F-Droid/release build and capture the full stack trace. (2026-01-21: ClassCastException Float → String during Settings preference inflate)
- [x] Fix root cause and add regression coverage.
- [x] Improve crash report export UX:
  - [x] Share should work with common targets (Gmail, Notes, Drive, etc).
  - [x] Add “Copy to clipboard” fallback so you can paste the report anywhere.
  - [x] Keep the crash report file around after launching the share sheet (fixes file-manager targets like Total Commander failing with “Could not open the file for reading!”).
  - [x] Optional: add a “Save to file” export via SAF (`ACTION_CREATE_DOCUMENT`) for maximum compatibility. (2026-01-22: added “Save report” to crash prompt)

## Notes
- Debug build instrumentation tests exist for “tap Settings opens Settings”, but release-only/proguard-only crashes can slip through.

---

# Feature: Receive Shared Documents (Android “Share to OpenDroidPDF”)

## Goal
Support receiving documents via Android share/open-with flows (especially from other apps) reliably.

## Tasks
- [x] Handle `ACTION_SEND` / `ACTION_SEND_MULTIPLE` (PDF/DOC/DOCX/EPUB) by normalizing to an internal `ACTION_VIEW` with `data=uri`.
- [x] Ensure URI permission handling works (`FLAG_GRANT_READ_URI_PERMISSION`, persistable permissions when possible).
- [x] Add instrumentation coverage that launching with `ACTION_SEND` opens the shared document.

---

# Next: Annotation Toolbar UX (Eraser Size)

## Goal
Make **eraser size** adjustable directly from the **top bar** (action icon), matching the pen controls.
Avoid burying the control in the overflow menu.

## Status (as of 2026-01-19)
- [x] Eraser size dialog exists, but is only reachable from the overflow menu (not ideal).
- [x] Add a dedicated **top-bar icon** for eraser size while in **erasing** mode.
- [x] Add a dedicated **top-bar icon** for pen settings (size + color) while in **drawing** mode.
- [x] QA: use `test_pdf.pdf` to verify eraser size changes are obvious and do not obscure the document.
  - Smoke: `DEVICE=localhost:<port> ./scripts/geny_eraser_size_smoke.sh`
- [x] UX: Tool size controls must have single ownership + single pathway (no buried submenu duplicates).
  - [x] Eraser thickness: only adjustable from the toolbar (one place).
  - [x] Pen thickness: only adjustable from the toolbar (one place).
  - [x] Audit other similar tool settings (highlighter size, ink opacity/color, etc) and apply the same rule.
  - Rule of thumb: when a tool is active, its adjustable parameters must be reachable **directly from the toolbar** without drilling into secondary menus, and must not be duplicated elsewhere.

## Implementation Notes
- Add or pick an icon for “eraser size” (do not reuse the erase-mode toggle icon).
- Add or pick an icon for “pen settings” (do not reuse the draw-mode toggle icon).
- In `annot_menu.xml`, set `menu_eraser_size` and `menu_pen_settings` to `showAsAction="always"` and give them icons.
- Gate visibility in `ToolbarStateController` to: `hasDocView && erasing`.
- Gate `menu_pen_settings` visibility in `ToolbarStateController` to: `hasDocView && drawing`.
- Hide Settings UI duplicates for ink/eraser size + ink color (keep prefs for persistence).

---

# Next: HQ Render Flicker (White Boxes)

## Goal
Prevent the hi-res patch from briefly flashing **white boxes** (blank tiles) when the view area changes
and a full redraw is required.

## Status (as of 2026-01-19)
- [x] Avoid in-place full redraws: when `viewArea` changes, render into the offscreen HQ bitmap (double-buffer).
- [x] QA on Genymotion using `test_pdf.pdf`: open, tap around, pinch-zoom, and ensure images never “disappear” into white boxes.
  - Smoke: `DEVICE=localhost:<port> ./scripts/geny_hq_flicker_smoke.sh`

---

# Bug: Markup Text Placement (Underline/Strike/Highlight)

## Goal
Ensure text markup annotations (highlight/underline/strikeout/caret) are created at the exact selected text location.

## Status (as of 2026-01-19)
- [x] Fix quad-point ordering for markup annotations (UL/UR/LL/LR) and remove legacy highlight-only swap.
- [x] Fix embedded markup placement on MuPDF 1.27+: pass quads/rects in fitz page-space (don’t pre-convert to PDF space).
- [x] QA on `test_pdf.pdf`: select text → underline/strike/highlight; verify the markup lands exactly on the selection across zoom levels. (2026-01-23)
  - Smoke: `DEVICE=localhost:<port> PDF_LOCAL=test_pdf.pdf RUN_ZOOM_MARKUP_CHECK=1 ./scripts/geny_pdf_text_markup_smoke.sh`

---

# Next: Monolith Audit (Non-third-party)

## Goal
Identify and refactor our biggest in-tree files (excluding `thirdparty/`, `srclibs/`, `thirdparty_build/`) to improve maintainability and reduce regression risk.

## Current Biggest “Local” Files (by LOC)

### Android app code (`platform/android/src/org/opendroidpdf/`)
- `platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java` (~910)
- `platform/android/src/org/opendroidpdf/PageView.java` (~860)
- `platform/android/src/org/opendroidpdf/MuPDFCore.java` (~791)
- `platform/android/src/org/opendroidpdf/app/document/DocumentToolbarController.java` (~788)
- `platform/android/src/org/opendroidpdf/app/document/ExportController.java` (~771)
- `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationEmbeddedFreeTextOps.java` (~763)
- `platform/android/src/org/opendroidpdf/ReaderView.java` (~724)
- `platform/android/src/org/opendroidpdf/MuPDFPageView.java` (~724)
- `platform/android/src/org/opendroidpdf/MuPDFPageViewTextAnnotations.java` (~658)
- `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationStyleDialogBinder.java` (~647)

### Android smoke scripts (`scripts/`)
- `scripts/geny_epub_note_background_smoke.sh` (~750)
- `scripts/geny_pdf_text_markup_smoke.sh` (~745)
- `scripts/geny_pdf_form_widgets_smoke.sh` (~703)
- `scripts/geny_uia.sh` (~646)
- `scripts/geny_pdf_text_annot_background_smoke.sh` (~612)
- `scripts/geny_epub_smoke.sh` (~571)

### Android JNI (`platform/android/jni/`, excluding `platform/android/jni/qpdf/`)
- `platform/android/jni/text_annot.c` (~1393)
- `platform/android/jni/document_io.c` (~978)

## How to Refresh This List (repeatable)
1. **Android app:** `git ls-files platform/android/src/org/opendroidpdf | rg '\\.(java|kt)$' | xargs wc -l | sort -nr | rg -v ' total$' | head`
2. **Scripts:** `git ls-files scripts | rg '\\.(sh|py)$' | xargs wc -l | sort -nr | rg -v ' total$' | head`
3. **JNI:** `git ls-files platform/android/jni | rg '\\.(c|h)$' | rg -v '^platform/android/jni/qpdf/' | xargs wc -l | sort -nr | rg -v ' total$' | head`

## Refactor Strategy
- Monolith thresholds: **> 750 LOC** for Java/Kotlin, **> 700 LOC** for shell scripts, **> 900 LOC** for JNI C (excluding `platform/android/jni/qpdf/`).
- Prefer extracting cohesive sub-systems into `.../app/<feature>/` controllers + small data types.
- Backfill instrumentation/regression tests before aggressive surgery (especially for annotations + export).

## Target Order (starting point)
- [x] Decide threshold + pick 3 targets.
- [x] `MuPDFPageView.java`: split into (render/layout) vs (input) vs (annotation overlay) responsibilities.
  - Extracted input host adapters: `MuPDFPageViewInkHost.java` + `MuPDFPageViewHitHost.java` (overlay already lived in `MuPDFPageViewTextAnnotations.java` + `MuPDFPageViewWidgets.java`).
  - Build: `cd platform/android && ./gradlew assembleDebug :uia_runner:assembleDebug`
  - Smoke: `DEVICE=localhost:<port> PDF_LOCAL=test_pdf.pdf ./scripts/geny_pdf_text_markup_smoke.sh`
- [x] `SidecarAnnotationSession.java`: split into (persistence) vs (render invalidation) vs (session lifecycle).
  - Extracted persistence/cache ops: `SidecarInkOps.java`, `SidecarHighlightOps.java`, `SidecarNoteOps.java`.
  - Build: `cd platform/android && ./gradlew assembleDebug :uia_runner:assembleDebug`
  - Smoke: `DEVICE=localhost:<port> ./scripts/geny_epub_note_background_smoke.sh`
- [x] `scripts/lib/geny_pdf_text_annot_steps.sh`: break into smaller `lib_*.sh` helpers + small scenario scripts per feature.
  - Extracted: `geny_pdf_text_annot_helpers.sh`, `geny_pdf_text_annot_steps_open_create.sh`, `geny_pdf_text_annot_steps_move_resize.sh`, `geny_pdf_text_annot_steps_style_misc.sh`, `geny_pdf_text_annot_steps_save_assert.sh`.
  - Smoke: `DEVICE=localhost:<port> ./scripts/geny_pdf_text_annot_smoke.sh`

---

# Bug: Settings Action Crash (Android / F-Droid)

## Goal
Tapping the Settings action must never crash, and should be covered by automated tests.

## Tasks
- [x] Verify on a release / F-Droid-equivalent build + device (capture `adb logcat` and the full stack trace if it still reproduces).
  - (2026-01-24) Release build installed on Genymotion and `org.opendroidpdf.uia.OpenSettingsTest#testOpenSettingsFromDashboardDoesNotCrash` passed; logcat saved locally under `/tmp/odp_settings_release_smoke_*`.
- [x] Identify which entry point crashes (dashboard Settings vs document-view Settings).
  - (2026-01-24) Startup crash during `StartupBootstrap.bootstrap()` → `PreferencesCoordinator.refreshAndApply()` when `SharedPreferencesViewerPrefsStore.load()` reads `pref_page_paging_axis` as a String but it’s stored as an Integer.
- [x] Fix the crash and add an instrumentation regression test for the failing entry point.
  - [x] Add UIAutomator regression: `org.opendroidpdf.uia.OpenSettingsTest#testOpenSettingsFromDashboardDoesNotCrash`
  - [x] Harden preference-type migration for `pref_page_paging_axis` (drop invalid types/values so Settings falls back to default).
  - [x] Run preference-type migration during app startup (before initial prefs apply).
  - [x] Add instrumentation regression: `PreferencesTypeMigratorInstrumentedTest#invalidPagingAxisPrefType_doesNotCrashOnStartup`
- [x] Make crash output shareable: add a “Share” action that sends the crash text via Android’s share sheet (and/or copies it to clipboard).
  - Already implemented via `CrashReportPrompter` (Share / Copy / Save report).

---

# Feature/Bug: Share-To / Open-With (Android Intents)

## Goal
OpenDroidPDF must reliably accept documents shared to it from other apps (Files, browser, email, chat) and open them.

## Tasks
- [x] Verify intent handling for: `ACTION_VIEW`, `ACTION_SEND`, and `ACTION_SEND_MULTIPLE` (including `content://` URIs and `ClipData`).
  - Instrumentation: `ShareIntentOpenInstrumentedTest` covers `ACTION_VIEW` (content URI), `ACTION_SEND` (EXTRA_STREAM + ClipData), and `ACTION_SEND_MULTIPLE` (EXTRA_STREAM + ClipData).
- [x] Ensure we persist URI permissions when needed and handle missing permissions gracefully (clear user-facing error).
  - Persist: `DocumentNavigationController.openDocumentFromIntent()` calls `UriPermissionHelper.tryTakePersistablePermissions(...)`.
  - Missing grants: `DocumentSetupController.setupCore(...)` catches `SecurityException` and shows a permission hint instead of crashing.
- [x] Add instrumentation coverage for share-to open (at least `ACTION_SEND` with a PDF asset).

---

# Backlog: User-Reported Issues (2026-01-23)

## Intake
- [x] Bug: Exporting does not immediately save annotations.
  - [x] Repro (Android): `DEVICE=localhost:<port> ./scripts/geny_export_latest_text_annot_smoke.sh` (creates text annotation then immediately exports; asserts OCR token is present).
  - [x] Repro (Linux): `./scripts/linux_export_latest_text_annot_smoke.sh` (pp_demo annotates + save-as, reopens, renders; asserts visible delta).
  - [x] Decide expected behavior: export/share/save-copy should include the *latest visible edits* by automatically committing any in-progress UI edits (no extra prompt; export is a copy).
  - [x] Fix (Android): ensure export/share/save-copy/print waits for pending edits:
    - Clear focused inline editors (force focus-loss commit).
    - Wait (best-effort) for any in-flight async annotation jobs (add/update/delete) before writing the PDF copy.
    - Implemented via `InkCommitHostAdapter.commitPendingInkToCoreBlocking()` (2026-01-25).
  - [x] Fix (Linux): on desktop export (`Ctrl+S`), commit any in-progress tool edits (pen/highlight drag) before writing the annotated copy.
  - [x] Add Android regression smoke: `DEVICE=localhost:<port> ./scripts/geny_export_latest_text_annot_smoke.sh`
  - [x] Add Linux regression coverage (script/unit test): `./scripts/linux_export_latest_text_annot_smoke.sh`

- [x] Bug: Importing `.docx` often strips formatting.
  - [x] Collect 2–3 sample `.docx` fixtures with expected formatting (headers, lists, bold/italic, tables).
    - `test_assets/word_formatting.docx`: heading + bold/italic runs + simple bullet list.
    - `test_assets/word_edge.docx`: table + image.
    - `test_assets/word_with_text.docx`: basic paragraphs + stable token.
  - [x] Identify conversion path (Android vs Linux) and where formatting is lost:
    - Android: `OfficePackWordImportPipeline` → Office Pack service → `WordToPdfConverter` (PDFBox). Current implementation writes mostly **plain text + images + basic tables** and ignores run/paragraph styles → formatting loss.
    - Linux/Desktop: `platform/gl/odp_word_import.c` uses LibreOffice (`soffice`) to convert `.docx` → PDF (should preserve formatting; if not, check LO invocation/options + fonts).
  - [x] Improve importer to preserve basic formatting (or document limitations clearly).
    - [x] Office Pack: preserve Heading 1–3 as larger, bold text.
    - [x] Office Pack: preserve bold/italic runs inside paragraphs.
    - [x] Office Pack: render basic lists with a hanging indent (prefix `-` + indent).
    - [x] QA: `DEVICE=localhost:<port> DOCX_LOCAL=test_assets/word_formatting.docx EXPECTED_TOKEN=opendroidpdf-docx-formatting ./scripts/geny_docx_officepack_smoke.sh`
      - 2026-01-26: `DEVICE=localhost:43947 DOCX_LOCAL=test_assets/word_formatting.docx EXPECTED_TOKEN=opendroidpdf-docx-formatting ./scripts/geny_docx_officepack_smoke.sh` passed.
      - If `gmsaas` reports `TOO_MANY_RUNNING_VDS`, wait your turn or use a dedicated `GENY_INSTANCE_UUID`/`DEVICE` (don’t stop someone else’s instance).

- [x] Bug: Two-finger pinch/zoom draws marks while in drawing mode.
  - [x] Repro: enable drawing → pinch-zoom → ensure no ink/marks are created.
  - [x] Fix gesture routing so multi-touch cancels/ignores drawing strokes and only zooms/pans.
    - (2026-01-24) `DrawingGestureHandler` now ignores multi-touch during drawing and drops the accidental single-point stroke when a pinch begins.
  - [x] Add UIAutomator regression test (pinch while drawing) or scripted smoke.
    - (2026-01-24) Smoke: `DEVICE=localhost:<port> ./scripts/geny_pinch_while_drawing_smoke.sh`

- [x] Bug: In text annotation mode, markup overlay is full-screen and cannot be closed.
  - [x] Repro: enter text annotation/selection → choose markup action → verify overlay has a close/back/accept action and is dismissible.
    - Instrumentation: `InlineTextAnnotationEditorDismissInstrumentedTest`
  - [x] Fix: ensure back/close works and overlay doesn’t block navigation indefinitely.
    - (2026-01-24) Back press + toolbar Cancel/Done now dismiss the inline text-annotation editor (`MuPDFPageView.dismissInlineTextAnnotationEditor()`).
  - [x] Add regression coverage.
    - Instrumentation: `InlineTextAnnotationEditorDismissInstrumentedTest`

- [x] UX: Add a “Home/Library” icon while viewing a document to return to the dashboard/home screen.
  - [x] Define behavior: preserve doc state, prompt to save if dirty, return to dashboard.
  - [x] Implement and test on Android (and Linux if applicable).
    - (2026-01-24) Update the toolbar "Library" action icon to a home glyph and prompt-to-save on tap when the document is dirty; after Save-As, continue to the dashboard automatically.

- [x] UX: Color picker uses too much screen real estate; redesign to be more space-efficient.
  - [x] Pick an approach: keep the fixed palette, but render it as a 1-row horizontally-scrollable swatch strip in dense dialogs (e.g., Text style) to cut vertical space; keep the full palette dialog for dedicated pickers.
  - [x] Android: Text style dialog uses scrollable swatch strips (Text/Background/Border) instead of 3 full grids.
  - [x] QA: run `scripts/geny_pdf_text_annot_background_smoke.sh` and spot-check the Text style dialog on a small phone screen.
    - 2026-01-26: `DEVICE=localhost:38913 ./scripts/geny_pdf_text_annot_background_smoke.sh` passed (after updating the smoke to scroll horizontal swatch strips).
  - [x] Android: make the existing palette dialog more compact (fewer text rows + tighter swatches + more columns on wide screens).
    - [x] Tighten swatch sizing and auto-fit columns in pen/text style dialogs to reduce palette rows.
    - [x] QA: `DEVICE=localhost:<port> ./scripts/geny_pdf_text_annot_background_smoke.sh` (exercises the Style dialog background color/opacity controls).
      - 2026-01-26: `DEVICE=localhost:42373 UIA_DUMP_RETRIES=20 UIA_DUMP_RETRY_SLEEP_S=0.5 ./scripts/geny_pdf_text_annot_background_smoke.sh` passed.
  - [x] Ensure the picker works well on small Android screens and larger Linux windows.
    - 2026-01-26: Android verified via Genymotion phone smoke; desktop GL/x11 currently has no swatch/picker UI, so no sizing work to do there yet.
