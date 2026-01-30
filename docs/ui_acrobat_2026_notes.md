# Acrobat Reader (Android) UI notes (2026) + OpenDroidPDF parity map

This is a product-facing snapshot of Acrobat Reader’s current Android UI patterns and how OpenDroidPDF differs, with a prioritized roadmap for convergence.

## Sources (reviewed 2026-01-30)
- Acrobat for Android Help: Navigate and search (scrubber tab, immersive mode, nav menu): https://www.adobe.com/devnet-docs/acrobat/android/en/navigatesearch.html
- Acrobat for Android Help: View PDFs (view settings: continuous vs single page, full screen): https://www.adobe.com/devnet-docs/acrobat/android/en/mv-viewpdf.html
- Adobe Help Center: “Working with PDFs on Acrobat Reader Android: New Experience” (top/back/share/search/overflow/more tools/quick actions toolbar): https://helpx.adobe.com/lv/acrobat/android-new-experience.html

## Acrobat Reader — high-level layout

### Library (no document open)
- Bottom navigation with 4 primary destinations: **Home**, **Files**, **Shared**, **Search**.

### Document viewer (PDF open)
- **Top app bar** with a **back arrow** on the left.
- Right-side actions are kept compact and “contextual” (commonly **Search**, **Share**, **Overflow**, sometimes a dedicated **More tools** entry).
- **Immersive behavior**: a single tap toggles UI chrome visibility (menus hidden/shown for reading).
- **View mode** is an explicit concept under **View settings**:
  - **Continuous**: scroll through pages vertically.
  - **Single page**: page-by-page; swipe left/right (or tap screen edges) to change pages.
  - **Reading mode** and **Night mode** are also available.
- **Page navigation** uses a small **page scrubber tab** that appears when chrome is visible; long-press + slide to jump pages; tap for “go to page”.
- Tool modes (edit / add text / draw / etc.) surface **tool-specific controls in a bottom quick-actions toolbar**, keeping the top bar stable.
- **Navigation menu** (via overflow) provides entry points to document structure: **Thumbnails**, **Bookmarks**, **Contents**, **Attachments**, and sometimes **Comments**.

## OpenDroidPDF — current layout differences
- **Top bar content**: historically showed “page / total” as the title and the document name as subtitle (dynamic title churn).
- **Library access**: historically exposed as an action icon rather than a left back arrow.
- **View mode**: no explicit “continuous vs single-page” concept; the viewer always behaves like a page-by-page reader (even when paging axis is vertical).
- **Page navigation**: persistent bottom overlay (indicator + scrubber) rather than a minimal scrubber tab that appears with chrome.
- **Tools**: exposed via **Annotate** sheet + mode-driven menus rather than a dedicated bottom quick-actions toolbar per tool.
- **Library screen**: dashboard-only (no bottom navigation destinations).

## Parity roadmap (prioritized)

### P0 — Reader chrome alignment (Acrobat-like)
- Back navigation affordance on the left (toolbar navigation icon).
- Title shows document name (stable) rather than page count.
- Reduce top-bar action clutter; keep “primary” actions as icons and push the rest to overflow/sheets.

### P1 — Page navigation alignment
- Make the page navigation affordance more Acrobat-like:
  - visually minimal by default (tab-style)
  - shown/hidden with chrome (tap to toggle)
  - tap = go-to-page / navigation sheet, drag = scrub

### P2 — Tool control alignment
- Tool modes (highlight/text/ink) expose their **controls near the bottom** (quick actions toolbar or bottom sheet), keeping the top bar stable.
- Match Acrobat’s “tool mode” mental model: enter a tool, adjust properties quickly, exit tool.

### P3 — Library navigation alignment
- Consider introducing a bottom navigation model (Home/Files/Shared/Search equivalents) if it improves discoverability without bloating the UX.
