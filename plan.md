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
- [ ] QA pass on a tall-page PDF (ensure in-page scroll still works; page switch occurs at edge/overscroll)

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
- [x] UX: Make the page scrubber always available on-page (no “tap twice to reach scrubber”).
- [x] QA on Genymotion: large PDF + repeated page switching should feel immediate and avoid “white box” flashes.
  - Script: `scripts/geny_release_page_switcher_watch_smoke.sh` (repeated next/prev, screenshots + blank-ish detection).
  - **Genymotion etiquette:** instances are shared; **do not stop/kill/disconnect** a running instance you didn’t start (it may be executing another smoke). If capacity is exhausted (`TOO_MANY_RUNNING_VDS`), **wait your turn**. Only spin up a separate instance if we absolutely need parallel QA and quota allows.
  - 2026-01-21: smoke passed (LOOPS=4) and captured screenshots/logcat.

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
- [ ] Improve crash report export UX:
  - [x] Share should work with common targets (Gmail, Notes, Drive, etc).
  - [x] Add “Copy to clipboard” fallback so you can paste the report anywhere.
  - [x] Keep the crash report file around after launching the share sheet (fixes file-manager targets like Total Commander failing with “Could not open the file for reading!”).
  - [ ] Optional: add a “Save to file” export via SAF (`ACTION_CREATE_DOCUMENT`) for maximum compatibility.

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
- [ ] QA: use `test_pdf.pdf` to verify eraser size changes are obvious and do not obscure the document.
- [ ] UX: Tool size controls must have single ownership + single pathway (no buried submenu duplicates).
  - [x] Eraser thickness: only adjustable from the toolbar (one place).
  - [x] Pen thickness: only adjustable from the toolbar (one place).
  - [ ] Audit other similar tool settings (highlighter size, ink opacity/color, etc) and apply the same rule.
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
- [ ] QA on Genymotion using `test_pdf.pdf`: open, tap around, pinch-zoom, and ensure images never “disappear” into white boxes.

---

# Bug: Markup Text Placement (Underline/Strike/Highlight)

## Goal
Ensure text markup annotations (highlight/underline/strikeout/caret) are created at the exact selected text location.

## Status (as of 2026-01-19)
- [x] Fix quad-point ordering for markup annotations (UL/UR/LL/LR) and remove legacy highlight-only swap.
- [x] Fix embedded markup placement on MuPDF 1.27+: pass quads/rects in fitz page-space (don’t pre-convert to PDF space).
- [ ] QA on `test_pdf.pdf`: select text → underline/strike/highlight; verify the markup lands exactly on the selection across zoom levels.

---

# Next: Monolith Audit (Non-third-party)

## Goal
Identify and refactor our biggest in-tree files (excluding `thirdparty/`, `srclibs/`, `thirdparty_build/`) to improve maintainability and reduce regression risk.

## Current Biggest “Local” Files (by LOC)

### Android app code (`platform/android/src/org/opendroidpdf/`)
- `platform/android/src/org/opendroidpdf/MuPDFPageView.java` (~1593)
- `platform/android/src/org/opendroidpdf/app/sidecar/SidecarAnnotationSession.java` (~1366)
- `platform/android/src/org/opendroidpdf/app/document/ExportController.java` (~895)
- `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationStyleController.java` (~862)
- `platform/android/src/org/opendroidpdf/OpenDroidPDFCore.java` (~849)
- `platform/android/src/org/opendroidpdf/ReaderView.java` (~847)
- `platform/android/src/org/opendroidpdf/MuPDFCore.java` (~791)
- `platform/android/src/org/opendroidpdf/PageView.java` (~777)
- `platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java` (~700)
- `platform/android/src/org/opendroidpdf/app/drawing/InkController.java` (~697)

### Android smoke scripts (`scripts/`)
- `scripts/geny_pdf_text_annot_smoke.sh` (~1614)
- `scripts/geny_pdf_form_widgets_smoke.sh` (~709)
- `scripts/geny_epub_smoke.sh` (~602)
- `scripts/geny_pdf_form_choice_advanced_smoke.sh` (~521)
- `scripts/geny_pdf_form_sign_smoke.sh` (~518)

### Android JNI (`platform/android/jni/`)
- `platform/android/jni/text_annot.c` (~1393)
- `platform/android/jni/document_io.c` (~978)

## How to Refresh This List (repeatable)
1. **Android app:** `git ls-files -z platform/android/src/org/opendroidpdf | tr '\\0' '\\n' | rg '\\.(java|kt)$' | xargs -0 wc -l | sort -nr | head`
2. **Scripts:** `git ls-files -z scripts | tr '\\0' '\\n' | rg '\\.(sh|py)$' | xargs -0 wc -l | sort -nr | head`
3. **JNI:** `git ls-files -z platform/android/jni | tr '\\0' '\\n' | rg '\\.(c|h)$' | xargs -0 wc -l | sort -nr | head`

## Refactor Strategy (proposal)
- Set a “monolith threshold” (e.g. **> 800 LOC** for Java/Kotlin, **> 500 LOC** for shell scripts, **> 700 LOC** for JNI C).
- Prefer extracting cohesive sub-systems into `.../app/<feature>/` controllers + small data types.
- Backfill instrumentation/regression tests before aggressive surgery (especially for annotations + export).

## Target Order (starting point)
- [ ] Decide threshold + pick 3 targets.
- [ ] `MuPDFPageView.java`: split into (render/layout) vs (input) vs (annotation overlay) responsibilities.
- [ ] `SidecarAnnotationSession.java`: split into (persistence) vs (render invalidation) vs (session lifecycle).
- [ ] `geny_pdf_text_annot_smoke.sh`: break into shared `lib_*.sh` helpers + small scenario scripts per feature.

---

# Bug: Settings Action Crash (Android / F-Droid)

## Goal
Tapping the Settings action must never crash, and should be covered by automated tests.

## Tasks
- [ ] Reproduce on the exact F-Droid build + device (capture `adb logcat` and the full stack trace).
- [ ] Identify which entry point crashes (dashboard Settings vs document-view Settings).
- [ ] Fix the crash and add an instrumentation regression test for the failing entry point.
- [ ] Make crash output shareable: add a “Share” action that sends the crash text via Android’s share sheet (and/or copies it to clipboard).

---

# Feature/Bug: Share-To / Open-With (Android Intents)

## Goal
OpenDroidPDF must reliably accept documents shared to it from other apps (Files, browser, email, chat) and open them.

## Tasks
- [ ] Verify intent handling for: `ACTION_VIEW`, `ACTION_SEND`, and `ACTION_SEND_MULTIPLE` (including `content://` URIs and `ClipData`).
- [ ] Ensure we persist URI permissions when needed and handle missing permissions gracefully (clear user-facing error).
- [ ] Add instrumentation coverage for share-to open (at least `ACTION_SEND` with a PDF asset).
