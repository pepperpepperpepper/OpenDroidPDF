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
