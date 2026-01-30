# Acrobat Reader (Android) UI notes (2026) + OpenDroidPDF parity map

This is a product-facing snapshot of Acrobat Reader’s current Android UI patterns and how OpenDroidPDF differs, with a prioritized roadmap for convergence.

## Acrobat Reader — high-level layout

### Library (no document open)
- Bottom navigation with 4 primary destinations: **Home**, **Files**, **Shared**, **Search**.

### Document viewer (PDF open)
- **Top app bar** with a **back arrow** on the left.
- Right-side actions are compact (commonly: **Search**, **Share**, **Overflow**; sometimes a **Comments**/navigation entry and **Undo/Redo** when editing).
- **Immersive behavior**: a single tap toggles UI chrome visibility (top bar + page navigation affordance).
- **Page navigation** uses a small **page scrubber tab** that shows the current page (e.g., “2 of 3”); it’s discoverable but visually minimal compared to a full-width slider.
- Tool modes (highlight / add text / etc.) surface **tool-specific controls in a bottom quick-actions toolbar** (color, size, etc.) rather than crowding the top bar.
- A “navigation” entry (often via overflow or a dedicated icon) exposes: **Thumbnails/Pages**, **Bookmarks**, **Contents**, **Attachments**, and sometimes **Comments**.
- Viewer “view settings” typically live in overflow (e.g., single/continuous page, night mode).

## OpenDroidPDF — current layout differences
- **Top bar content**: historically showed “page / total” as the title and the document name as subtitle (dynamic title churn).
- **Library access**: exposed as an action icon rather than a left back arrow.
- **Page navigation**: persistent bottom overlay (indicator + scrubber) rather than a minimal tab that appears with chrome.
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

