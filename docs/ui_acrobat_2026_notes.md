# Acrobat Reader (Android, “New experience”) vs OpenDroidPDF — UI Layout Comparison (2026-02-01)

This is a **layout-first** (placement/structure) comparison between:
- **Adobe Acrobat Reader (Android)** as documented in Adobe’s “New experience” help pages, plus the navigation/search help pages that include UI screenshots.
- **OpenDroidPDF (this repo)** as seen in the latest UI gallery report and the current UI architecture docs.

Goal: identify what makes Acrobat feel “modern” and where OpenDroidPDF differs, so we can deliberately align or intentionally diverge.

## Sources used (Acrobat, official)

Primary references:
- “Working with PDFs on Acrobat Reader Android: New Experience” (top menu, quick actions toolbar, tools menu, etc.)
  - https://helpx.adobe.com/acrobat/using/work-with-pdfs-in-adobe-reader-mobile-android.html
- “Viewing PDFs” (immersive mode; view settings: Continuous/Single page/Reading mode/Night mode)
  - https://helpx.adobe.com/acrobat/android.html
- “Navigate and Search” (bottom navigation tabs; scrubber tab; navigation menu items like Thumbnails/Bookmarks/Contents)
  - https://opensource.adobe.com/dc-acrobat-sdk-docs/acrobat_android/en/navigatesearch.html

Note: Acrobat UI can vary by account state (signed-in vs not), feature flags, subscription, and locale. This document focuses on the **stable, reader-core layout**.

## OpenDroidPDF references

Latest local UI gallery (Genymotion, 2026-02-01):
- `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_manifest.txt`
- `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_002_pdf_viewer_multipage.png` (reading chrome)
- `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_004_navigate_view_sheet.png` (Navigate & View sheet)
- `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_005_annotate_sheet.png` (Annotate sheet)
- `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_014_search.png` (Search UI)

Architecture inventories:
- `ui_architecture_current.md` (what exists today; stable IDs)
- `ui_architecture_target.md` (ideal UX contract; “reading-first”)

---

# 1) App-level structure (what “shell” you’re in)

## Acrobat (Android)

### Global navigation: **persistent bottom navigation bar**
Acrobat’s “reader app” experience is organized around a bottom tab bar:
- **Home**
- **Files**
- **Shared**
- **Search**

This means Acrobat is conceptually “multi-surface” even before you open a document: you can move between content sources and workflows without going “back” out of a document picker.

### Home vs Files separation
Acrobat’s bottom tabs imply a separation of intent:
- **Home**: “What was I doing recently?” (recents + workflow entry points).
- **Files**: “Where are my documents?” (local + cloud providers).
- **Shared**: collaboration/sharing surface.
- **Search**: cross-surface search entry point.

Even if you ignore cloud features, this structure keeps “finding a file” and “reading” as separate mental modes.

## OpenDroidPDF (today)

### Global navigation: **single-activity + library/dashboard overlay**
OpenDroidPDF is effectively:
- A **Dashboard/Library** (open/new/settings + recents)
- A **Document view** (reader/editor)

There is no persistent bottom navigation. The primary “global move” is:
- **Library** (aka dashboard) opened via `menu_open` in the top app bar.

Implication: compared to Acrobat, OpenDroidPDF is more “single-flow”: open something → read/edit → go back.

### Notable current difference
In the UI gallery “Library/Home screen” screenshot, the surface is visually sparse (no obvious “Open / New / Settings” CTAs are visible in that capture). In practice, the dashboard *does* contain those actions (see `ui_architecture_current.md`), but the first-screen visual affordances need to match that reality.

---

# 2) Document reader: chrome placement (what stays on screen while reading)

## Acrobat: reading chrome (structure)

### A) **Top menu bar** (always at the top when chrome is visible)
Adobe describes a top menu that includes:
- **Back** (returns to Home)
- **File name/title** (center/top)
- A row of **document actions** (icons on the right side), including at least:
  - Liquid mode (reflow)
  - Comments list
  - Share
  - Search
  - Overflow (⋮)

Placement principle: **everything “global-to-this-document” is in the top bar**.

### B) **Quick actions toolbar** (secondary actions near the bottom)
Acrobat describes a “quick actions toolbar” separate from the top menu.

This creates a clear hierarchy:
- Top = “document/global”
- Bottom = “do something common now”

### C) **Tool-mode bottom toolbar** (only appears in tool contexts)
When you choose a tool (comment/edit/etc.), Acrobat surfaces tool-specific actions in a bottom bar.

This means the reading top bar stays relatively stable, and “editing affordances” appear **only when you’re editing**.

### D) **Immersive mode on tap**
Acrobat supports a “tap to hide chrome” immersive mode; tapping again brings chrome back.

## OpenDroidPDF: reading chrome (structure)

### A) **Top app bar** (Toolbar)
In reading mode (main menu), OpenDroidPDF typically shows:
- **Back arrow** on the left (returns to library/dashboard)
- **Document title** in the center (truncated filename)
- Right-side icon cluster (varies by build/mode), commonly:
  - Search
  - Annotate (pencil)
  - Share
  - Overflow (⋮)

### B) **Page indicator pill** (bottom-center overlay)
OpenDroidPDF uses a bottom-center “`current / total`” pill. It is both:
- **status** (shows where you are)
- **entry point**: tap opens **Navigate & View** sheet (scrubber + toggles)

### C) **Sheets instead of toolbars**
OpenDroidPDF tends to use bottom sheets for:
- Navigate & View
- Annotate tool chooser
- Export

Tool modes (draw/erase/selection/edit) swap the **top bar menu**, rather than adding a dedicated bottom tool strip.

### D) Immersive/reading mode
OpenDroidPDF has a “Reading mode” toggle (in Navigate & View) that hides the toolbar; it is not primarily “tap-to-toggle” today.

---

# 3) Page navigation: “how do I move quickly through a long PDF?”

## Acrobat

### Page position indicator: **edge scrubber tab**
Acrobat includes a **scrubber tab** that shows the current page number and can be dragged to jump. Placement varies by view mode:
- On the **right edge** in some modes
- On the **bottom** in other modes (e.g., when continuous scrolling changes the layout)

### “Go to page” is a dedicated dialog
Acrobat provides an explicit “Go to page” flow with a page range and numeric input.

### Navigation menu (outline/thumbnails/bookmarks)
Acrobat has a navigation menu with multiple document-structure views:
- Comment list
- Bookmarks
- Contents/TOC
- Thumbnails
- Attachments

## OpenDroidPDF

### Page position indicator: **bottom-center pill**
OpenDroidPDF’s primary navigation affordance is the bottom-center page indicator pill.

### Scrubber + nav live inside “Navigate & View”
OpenDroidPDF provides:
- A page scrubber (slider) inside a bottom sheet
- “Contents” and “Go to page” entries (also in that sheet)

### What’s missing vs Acrobat’s navigation structure
OpenDroidPDF does not currently expose, as first-class places:
- Thumbnails (fast visual navigation)
- Bookmarks vs TOC separation
- Attachments panel

It does expose an annotations/comments list, but it is not integrated into a unified navigation drawer/panel concept.

---

# 4) View/layout controls (continuous vs single page, night mode, etc.)

## Acrobat

Acrobat surfaces “View settings” that include at least:
- **Continuous**
- **Single page**
- **Reading mode**
- **Night mode**

Placement-wise, these are reachable from the document’s chrome (overflow → view settings), i.e., **inside the document view**, not buried in a global settings screen.

## OpenDroidPDF

OpenDroidPDF has:
- **Scroll mode**: Continuous vs Paged (implemented; default = Continuous)
- **Page swipe direction**: Vertical vs Horizontal (applies to Paged mode)
- **Reading mode**: a toggle (currently surfaced in Navigate & View)
- **Fullscreen**: toggle in Navigate & View
- **Show annotations** and **Forms highlight** toggles in Navigate & View

Notable differences:
- OpenDroidPDF’s “layout system” has more degrees of freedom (paged axis + scroll mode).
- OpenDroidPDF does not currently have a first-class **Night mode** in the reader chrome.

---

# 5) Annotation & commenting: entry points and surfaces

## Acrobat

Acrobat’s reader describes:
- A **Comments list** entry in the top menu (show all comments)
- A tool system where choosing a tool creates a **tool-mode bottom toolbar**
- Quick actions toolbar (bottom) for common actions

The net effect is that “commenting” feels like:
- Tap a bottom tool / quick action
- Get a dedicated tool UI
- Exit back to a clean reading chrome

## OpenDroidPDF

OpenDroidPDF uses:
- Top bar **Annotate** icon (pencil) → opens **Annotate bottom sheet**
- Sheet actions: Draw, Erase, Mark up text, Add text, Fill & Sign, Annotations
- Tool modes swap the **top bar menu** (Draw mode shows Cancel/Done/Undo/Redo, etc.)
- Annotations list is a dialog/sheet separate from navigation/outline.

Notable differences in “feel”:
- Acrobat: tools are *bottom-first* (quick actions + tool strip).
- OpenDroidPDF: tools are *top-first* (top-bar icon → sheet → top-bar mode).
- Acrobat: long vertical flings keep momentum across many pages in continuous mode.
- OpenDroidPDF: continuous-mode flings were previously capped by the 3-page attached view stack; now uses a large vertical fling range so momentum can carry across page switches.
- OpenDroidPDF: Settings → Display → **Continuous fling momentum** controls how far a “flick” scrolls in continuous mode.

---

# 6) Search: placement and workflow

## Acrobat

Acrobat’s reader puts **Search** in the top menu and opens an in-document search UI (query field + next/prev hits).

## OpenDroidPDF

OpenDroidPDF’s Search is also in the top bar and opens a focused search mode with:
- A query field in the top bar
- Next/prev navigation controls

This is one of the closer layout matches between the two apps.

---

# 7) Visual design & spacing (why Acrobat “feels modern”)

## Acrobat (as observed in their docs/screenshots)

Key layout cues:
- **Page-on-canvas** look: white page on a light gray background (page edges are obvious).
- **Small page separation** in continuous mode (no giant dead zones).
- **Chrome hierarchy**: stable top bar + bottom quick actions; editing UI appears only when needed.
- **Edge scrubber**: page position is always “one gesture away”.

## OpenDroidPDF (current)

Strengths:
- The reader now uses a gray backdrop + subtle page border/shadow in continuous mode, and pages “flow” without dead-space.
- Bottom sheets provide progressive disclosure (good for a reading-first contract).

Where it still differs:
- Page indicator placement (center-bottom vs edge scrubber).
- Lack of a persistent “quick actions” strip; entry points are more mode-driven.
- Library/home surface doesn’t yet communicate “recents + open” as clearly as Acrobat.

---

# 8) Placement parity checklist (what to change if we want Acrobat-like layout)

This is the “if we want to look/feel like Acrobat” worklist distilled into *placement* decisions:

## Library/Home
- Add a clear primary CTA for **Open** (and optionally **New**) on first launch.
- Consider a bottom navigation model (even if only “Home / Files / Search” initially) **or** a visually equivalent top-level structure.

## Reader chrome
- Keep top bar minimal and stable: Back + Title + Search + Share/Export + Overflow.
- Create a bottom “quick actions” row (optional) for: Annotate, Comments, View settings.

## Navigation affordances
- Consider adding an **edge scrubber** (right-side tab) while keeping the current page-indicator sheet as an accessible alternative.
- Add a first-class **Thumbnails** surface for fast visual navigation.
- Consolidate “Comments/Annotations” into a single navigation panel concept (Acrobat’s nav menu model).

## View settings parity
- Expose “Night mode” (or equivalent) in the reader chrome.
- Ensure Continuous/Single page is a one-tap reach from overflow/sheet (not Settings-only).
