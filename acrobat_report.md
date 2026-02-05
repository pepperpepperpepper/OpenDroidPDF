# Acrobat Reader (Android, “New experience”) — Detailed UI Layout Report (2026-02-01)

This report documents **how modern Adobe Acrobat Reader on Android lays out its UI** (structure + placement), then compares it to **OpenDroidPDF (this repo)** so we can deliberately align our UX.

This is **layout-first**: where things live on screen, what appears/disappears, and what surfaces/actions are grouped together.

## Scope & methodology

**Acrobat scope**
- Target: **Acrobat Reader on Android** using Adobe’s “New experience” UI (as described in Adobe’s Android help pages and their accompanying screenshots).
- Acrobat’s UI varies by account state (signed in/out), file type (private/shared/review), and feature flags; this report focuses on the **stable core reader/editor layout**.

**OpenDroidPDF scope**
- Target: OpenDroidPDF Android UI as captured in the latest local UI gallery:
  - `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_manifest.txt`
  - Key screenshots:
    - `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_001_home_library.png`
    - `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_002_pdf_viewer_multipage.png`
    - `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_004_navigate_view_sheet.png`
    - `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_005_annotate_sheet.png`
    - `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_014_search.png`
- “Inventory truth” references:
  - `ui_architecture_current.md`
  - `ui_architecture_target.md`

**Primary sources (Acrobat, official)**
- Android “new experience” overview + labeled UI elements (top menu, quick actions toolbar, etc.):
  - https://helpx.adobe.com/acrobat/android-new-experience.html
- AI Assistant on mobile (voice + multi-doc + citations UI details):
  - https://helpx.adobe.com/acrobat/mobile-apps-acrobat-ai-assistant.html
- Acrobat for Android Help (navigation, view, edit, forms), including many UI screenshots:
  - https://www.adobe.com/devnet-docs/acrobat/android/en/
  - Notable pages used heavily:
    - View PDFs: https://www.adobe.com/devnet-docs/acrobat/android/en/mv-viewpdf.html
    - Navigate & search: https://www.adobe.com/devnet-docs/acrobat/android/en/navigatesearch.html
    - Modify PDFs: https://www.adobe.com/devnet-docs/acrobat/android/en/mv-modifypdf.html
    - Forms / Fill & Sign: https://www.adobe.com/devnet-docs/acrobat/android/en/forms.html
    - Open/Manage files: https://www.adobe.com/devnet-docs/acrobat/android/en/openfiles.html
    - Release notes (UI/feature evolution; includes AI Assistant additions through 2025): https://www.adobe.com/devnet-docs/acrobat/android/en/releasenotes.html

## UI variants (important context)

Acrobat’s Android docs cover multiple UI generations:
- **New experience**: includes the bottom **quick actions toolbar**, “More tools” grid, and the current reader chrome described in this report.
- **Classic interface**: older chrome/menus still referenced in some help pages; labels/actions may be in different places.

This report uses **New experience** as the baseline and calls out where the official docs explicitly indicate “classic interface” differences (e.g., on the View PDFs page).

---

# 1) Acrobat’s information architecture (the “app shell”)

## 1.1 Persistent bottom navigation (global)

Acrobat is organized around a **persistent bottom tab bar**. The docs describe the bottom menu as:
- **Home**
- **Files**
- **Shared**
- **Search**

This is a major structural difference vs OpenDroidPDF (which is basically “Library → Document”).

### Why this matters
- It creates “always-available” places for:
  - Recents/workflows (Home)
  - File sources (Files)
  - Collaboration (Shared)
  - Global search (Search)
- It reduces the need for “back out of the reader to find another file”, because the user can jump tabs.

Reference screenshot: bottom menu bar (`_images/bottommenu.png`) on the navigation/search page.

## 1.2 A global “+” create/open control (FAB menu)

Acrobat’s “Files” surface includes a **plus action** that opens a **floating action menu** (FAB menu) with entry points like:
- Scan / capture
- Create PDF
- Open file / browse locations

This gives Acrobat a consistent “do something” affordance at the app-shell level, independent of which file is open.

Reference screenshots on the open-files page:
- `_images/fab-menu.png`
- `_images/open-file.png`
- `_images/files.png`

Layout notes from the Open/Manage files documentation:
- The “Open file” flow is presented as a **location picker** (sources like “On this device” plus connected providers), not as a single monolithic file browser (`_images/open-file.png`).
- The “Files” surface itself is a **list-style** document surface (with recents/sources), with the “+” as the primary create/import affordance (`_images/files.png`).

## 1.3 “PDF spaces” (2025+) and other new top-level surfaces

Recent release notes introduce new app-shell surfaces (beyond the classic Home/Files/Shared/Search tabs), notably **PDF spaces**. This matters for layout parity because Acrobat is evolving toward:
- A “home for workflows” (spaces and assistant-driven experiences)
- Less emphasis on “just a file viewer”

Reference: the 2025 release notes section “PDF spaces” and related entries.

---

# 2) Acrobat’s document reader layout (PDF open)

Acrobat’s reader is built around a few consistent regions:

1) **Document canvas** (center): page content
2) **Top menu bar** (top): navigation + document-global actions
3) **Bottom quick actions toolbar** (bottom): common tools
4) **Edge/bottom scrubber tab** (edge): fast page jumping

## 2.1 Chrome show/hide + tap behavior

Acrobat supports an “immersive” behavior:
- A single tap on the page toggles chrome visibility.
- When chrome is hidden, the canvas becomes primary.

References:
- Tap zones (single page navigation): `_images/tapzones.png`
- Contextual blank-space menu (quick tool entry): `_images/draw-context-menu-blank.png`

### 2.1.1 Single page navigation: tap zones + horizontal swipe

In **Single page** mode, Acrobat’s navigation UI includes explicit “tap zones”:
- **Swipe left/right** to go forward/back a page.
- **Tap the left or right side** of the page to move one page (prev/next).

This is important because it encodes a different “page turning” mental model than continuous scrolling.

### 2.1.2 Blank-space contextual menu (quick actions near the tap)

In addition to the persistent toolbars, Acrobat can show a **small contextual menu** near the user’s tap on a blank area of the page (for quick access to tools like comment/draw/text/sign).

Layout takeaway:
- Acrobat uses both **persistent toolbars** (global discoverability) and **near-touch contextual menus** (low travel distance once you know them).

## 2.2 Top menu bar: what’s in it and why

Adobe explicitly labels the “top menu” elements in the new experience overview:
- **Back** button (left)
- **Document name** (center)
- A set of document-global action icons (right), including:
  - **Liquid mode** (reflow)
  - **Comments list**
  - **Share**
  - **Search**
  - **Overflow menu** (⋮)
  - **More tools** entry point (a separate tools hub)

Key nuance: Adobe notes the **top menu changes** based on file type/state (private/shared/review).

Reference: “New experience” UI labels (A–I) in the helpx page.

### 2.2.1 Editing-state additions: undo/redo in the top bar

In editing/tool contexts, Acrobat commonly adds **Undo / Redo** affordances into the top bar (near the back button), while keeping the document-global icons on the right.

References:
- Highlight mode: `_images/highlight-text.png`
- Add text mode: `_images/add-text-mode.png`

### Placement principle
Acrobat keeps “document-level” actions consistently in the **top bar**. Tools still exist, but the top bar reads as:
- Navigate out
- Know what file you’re in
- Search/share/comment
- Open menus for view/tools

## 2.3 Overflow menu (⋮): reader-adjacent actions grouped together

From the “View PDFs” docs, the overflow menu list includes entries like:
- **View settings**
- **Pages**
- **Read aloud**
- **Bookmarks & Table of Contents**
- **Edit, Organize & more**
- **Export as…**
- **Save to Adobe cloud storage**
- **Save a copy**
- **Print**
- (plus other items that vary by state)

References:
- Overflow menu with “View settings / Bookmarks & Table of Contents / Pages / Read aloud …”: `_images/bookmark-add-quick.png`
- Read aloud entry in overflow: `_images/read-aloud-menu.png`

Dynamic items:
- The overflow menu can include context actions like **“Add bookmark to this page”** depending on the current state (bookmark/no-bookmark).

### Why this matters
This menu is a *hybrid* of:
- Reader presentation controls (view settings, read aloud)
- Navigation to structural views (bookmarks/TOC, pages)
- File actions (save/copy/print)
- “Escalation” into tool workflows (“Edit, organize & more”)

The key layout takeaway is: Acrobat’s “⋮” is not “misc stuff”; it’s a curated list of **reader-adjacent** actions with clear nouns.

## 2.4 View settings dialog (presentation controls)

Acrobat’s view settings dialog includes reader presentation toggles such as:
- **Continuous** vs **Single page**
- **Reading mode**
- **Night mode**

Reference screenshot: `_images/view-settings.png` (shows all four controls in one dialog).

### Layout takeaway
Presentation is treated as an in-document, first-class concept:
- It’s reachable from the reader chrome (not buried in a global settings screen).
- It’s grouped as a single “view settings” dialog rather than scattered toggles.

## 2.5 Bottom quick actions toolbar (the “always-there tool strip”)

Acrobat’s new experience includes a **bottom quick actions toolbar**.

From Adobe’s own screenshots (bookmark confirmation view), the bottom strip shows tools like:
- **Comment**
- **Highlight**
- **Draw**
- **Text**
- **Fill & Sign**
- **More tools**

Reference screenshot: `_images/bookmark-added-message.png` (shows bookmark toast + bottom quick actions row).

### 2.5.1 Tool modes replace the quick actions row with a tool-specific bottom bar

When a tool is active, Acrobat commonly replaces the “quick actions” row with a **tool-specific bottom toolbar**, e.g.:
- Highlight: underline/strike/highlight toggles + color controls (`_images/highlight-text.png`)
- Add text: text size/style/color controls (`_images/add-text-mode.png`)

### Layout takeaway
This is one of the biggest “modern feel” differences:
- Acrobat surfaces “common do-something tools” as a persistent bottom row.
- It reduces discovery cost for annotate/highlight/draw/text/sign because the entry points are visually present.

## 2.6 “More tools” hub (grid of power tools)

Acrobat exposes a grid “more tools” menu with items like:
- Edit PDF
- Export PDF
- Combine files
- Compress PDF
- Set password
- Organize pages
- Crop pages
- Create PDF
- Request e-signatures
- New Scan

Reference screenshot: `_images/more-tools-menu.png` (grid of large icon tiles).

### Layout takeaway
Acrobat separates:
- **Quick tools** (bottom strip; 1-tap entry)
- **Power tools** (grid; “choose a workflow”)

This is progressive disclosure done as a **spatial separation**:
- bottom = “fast”
- grid = “big jobs”

## 2.7 Scrubber tab (fast page jumping affordance)

Acrobat provides a page scrubber “tab” (typically on the **right edge**) that:
- Displays current page / total pages (e.g., “3/85”)
- Can be **dragged** to jump quickly
- Can appear in different placements depending on view mode, but is designed to be accessible from the reader surface.

References:
- `_images/scrub.png`
- Navigate & search docs: scrubber tab section.

Interaction details (layout/gesture contract):
- The scrubber can be grabbed via a **long-press** and then dragged to change pages.
- Tapping the scrubber opens an explicit **Go to page** dialog.

### Layout takeaway
Acrobat makes “fast navigation” available from the document surface without requiring opening a sheet/menu.

OpenDroidPDF currently has a scrubber, but it’s one surface deeper (tap page indicator → sheet).

## 2.8 Go-to-page dialog (explicit numeric jump)

Tapping the scrubber tab opens a go-to-page dialog that supports:
- A page number field
- Quick previous/next actions (and potentially jump logic)

Reference screenshots:
- `_images/go-to-page.png`
- `_images/go-to-page-2.png`

## 2.9 Navigation menu (structure views: thumbnails, bookmarks, TOC, attachments)

Acrobat exposes a navigation menu that includes:
- Comments list
- Bookmarks
- Contents (TOC)
- **Thumbnails**
- **Attachments**

Reference screenshot: `_images/navmenu.png`.

Placement detail:
- The navigation menu is accessed via a **bottom-left navigation icon** in the reader chrome (per the Navigate & Search documentation).

### Layout takeaway
Acrobat treats navigation/structure as a dedicated “panel menu”, not just one “Contents” item:
- Thumbnails is explicitly first-class (visual navigation).
- Attachments is first-class (PDF package behavior).

## 2.10 Bookmarks + TOC surface (tabbed sheet)

Acrobat’s “Bookmarks and Table of Contents” surface is a tabbed UI with:
- A **Bookmarks** tab
- A **Table of Contents** tab
- An action like **“Add bookmark to this page”**

Reference screenshot: `_images/bookmark-add.png`.

Related: bookmark management actions (delete/rename) are exposed via a secondary sheet/menu:
- `_images/bookmark-delete-rename.png`

### Layout takeaway
Acrobat combines related structural navigation features (bookmarks + TOC) into one predictable place.

OpenDroidPDF’s TOC/Contents exists, but “Bookmarks” and “Thumbnails” are not (yet) first-class.

## 2.11 Context menus for text (selection, read aloud, comments)

When selecting text, Acrobat uses an in-canvas context menu with actions like:
- Add comment
- Read aloud
- Edit text (premium)
- Copy / select more (etc.)

Reference screenshot: `_images/read-aloud-context-menu.png`.

Layout takeaway:
- Acrobat keeps text actions **near the selection** (contextual overlay), not only in a top bar.

## 2.12 Read aloud surfaces (overflow + in-mode playback bar)

Acrobat exposes read aloud in two notable UI placements:
- **Overflow menu** entry (“Read aloud”) (`_images/read-aloud-menu.png`)
- **Context menu** on selected text (includes “Read aloud”) (`_images/read-aloud-context-menu.png`)

When read aloud is active, Acrobat shows a dedicated playback surface (bottom bar) while keeping the document visible.

Reference screenshot: `_images/read-aloud-mode.png`.

## 2.13 Search UI (top-bar icon + app-shell Search tab)

Acrobat exposes search at two levels:
- **In-document search** from the reader top menu bar (search icon).
- **App-shell search** as a bottom navigation tab (“Search”), for searching across surfaces.

In-document search layout contract (per Navigate & Search docs):
- Triggering search shows a dedicated search field UI.
- Next/previous match navigation is surfaced as explicit arrow controls.
- Closing search is an explicit “X” affordance.

---

# 3) Acrobat’s editing / annotation layouts (how tool modes change UI)

Acrobat differentiates tool modes mostly by:
- Keeping the document canvas visible
- Showing tool-specific controls in a **bottom toolbar / bottom strip**
- Keeping the top menu as “global + undo” and menus

## 3.1 Highlight mode

Highlight mode uses a bottom tool row for:
- Highlight / Underline / Strikeout toggles
- Color selection

Reference screenshot: `_images/highlight-text.png`.

## 3.2 Draw mode

Draw mode exposes:
- Drawing controls (color/width) and other draw affordances near the bottom
- Undo/menus in the top bar

Entry points (layout):
- Bottom quick actions toolbar (Draw)
- Blank-space contextual menu (Draw) (`_images/draw-context-menu-blank.png`)

(The modify-PDF page contains multiple draw-related screenshots; highlight is the clearest bottom-toolbar example.)

## 3.3 Text edit / add text mode (premium)

Text editing (“Edit PDF”) introduces a mode where:
- The canvas shows text boxes/handles
- The bottom bar switches to text-specific actions

Reference screenshot: `_images/add-text-mode.png`.

## 3.4 Add image mode (premium)

“Add image” mode shows:
- A bottom toolbar that anchors the workflow (organize/add text/add image)

Reference screenshot: `_images/add-image.png`.

## 3.5 Organize pages / crop pages (premium workflows)

Acrobat exposes dedicated “workflow screens” for some heavy tasks:
- Organize pages
- Crop pages

Reference screenshots:
- `_images/organize.png`
- `_images/crop-pdf.png`

Layout takeaway:
- Acrobat treats these as full workflows with their own surfaces, not just menu actions.

## 3.6 Voice comments (audio annotation UI)

Acrobat includes an audio/voice comment workflow that introduces a dedicated bottom-bar control surface for recording.

Reference screenshot: `_images/voice-comment-6.png`.

---

# 4) Acrobat’s forms / Fill & Sign layout

## 4.1 Fill & Sign entry point

Fill & Sign is a first-class quick action (bottom strip), which means it is **as discoverable as Highlight/Draw**.

Reference screenshot: `_images/form-fill-n-sign.png`.

## 4.2 Field-level toolbars

When editing form fields (text boxes, etc.), Acrobat shows a small local toolbar near the field with:
- Text size / formatting controls
- Checkmark/accept
- Delete/remove
- Possibly signature/initial controls

Reference screenshot: `_images/form-fill-fields.png`.

Reference for checkbox style editing: `_images/form-fill-checkbox.png`.

Layout takeaway:
- Field editing is “inline + local”, not a global “mode switch”.

---

# 5) Acrobat AI Assistant + voice UX (2024–2025 additions)

Acrobat’s recent releases add an **LLM-style assistant** and voice-first interaction patterns that materially affect layout.

This section focuses on the *UI mechanics* (where it appears, how it cites sources, how voice input/output is surfaced), not the underlying model/provider.

## 5.1 Entry points (where the assistant is launched)

From official documentation and release notes, the assistant is accessible via:
- A dedicated **AI Assistant icon** in the reader (document chrome).
- The **contextual menu** (e.g., select text → contextual actions → AI Assistant).

Layout takeaway:
- Acrobat treats “ask about this document” as a first-class *in-document* action, not a settings-only feature.

## 5.2 Assistant pane layout (resizable, citation-first)

Official docs describe the assistant as a **pane** over the reader with:
- A prompt bar for questions
- An **Options** menu (upper right) for additional actions
- A draggable top edge to resize the pane height (snap sizes like ~20% / 60% / 80%)

Related layout mechanics called out in official docs/release notes:
- A visible control to **stop generation** while the response is in progress.
- A “related questions” affordance after an answer (to continue the session without retyping).
- An explicit control to **start a new chat** (session reset) from within the assistant UI.

## 5.2.1 Multi-document context controls (chat with more than one PDF)

Acrobat’s assistant UI supports adding more documents into the current chat context:
- The prompt bar includes an **Add (+)** affordance to attach additional documents.
- Docs/release notes describe a cap (up to ~10 documents in one chat).

## 5.3 Source citations: numbered links that jump into the PDF

Assistant responses include **numbered source links**. Tapping a number navigates to the corresponding cited location in the PDF.

In addition, the assistant UI includes an affordance to **hide/show sources** so the chat can be read without citation clutter.

Layout takeaway:
- Acrobat makes “answer ↔ source” a direct interaction loop: the assistant UI is not separate from navigation; it *drives navigation*.

## 5.4 Voice input + hands-free mode (layout implications)

Recent notes describe voice interaction features including:
- A voice prompt button (mic) to speak a query
- A hands-free mode where the assistant can be driven via voice commands

## 5.5 AI summaries: adjustable panel + voice playback with highlight

Release notes also describe summary UX that includes:
- A summary panel that can be resized (same “20/60/80%” pattern)
- Optional voice playback of the summary and highlighting the text being read

References:
- Mobile AI assistant overview: https://helpx.adobe.com/acrobat/mobile-apps-acrobat-ai-assistant.html
- Release notes AI assistant entries (2024–2025): https://www.adobe.com/devnet-docs/acrobat/android/en/releasenotes.html

---

# 6) OpenDroidPDF’s current UI layout (as of 2026-02-01)

This is an intentionally condensed “what’s on screen” summary. For full inventory, see `ui_architecture_current.md`.

## 6.1 App shell

OpenDroidPDF has two primary surfaces:
- **Dashboard/Library** (open/new/settings + recents)
- **Document view** (reader/editor)

There is no persistent bottom navigation. “Library” is an action from the top bar (`menu_open`).

Reference screenshot: `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_001_home_library.png`.

## 6.2 Reader chrome

In the PDF reader:
- **Top app bar**: back arrow + truncated document name + action icons (search, annotate, share, overflow)
- **Bottom-center page indicator pill** (“1 / N”) that can be tapped to open a sheet

Reference screenshot: `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_002_pdf_viewer_multipage.png`.

## 6.3 Navigation & view controls

OpenDroidPDF uses a “Navigate & View” bottom sheet that contains:
- A page scrubber slider and prev/next step buttons
- Contents / Go to page
- View toggles (Fullscreen, Reading mode, Show annotations, Forms)

Reference screenshot: `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_004_navigate_view_sheet.png`.

## 6.4 Tools & annotation entry

OpenDroidPDF uses an “Annotate” bottom sheet as the main tool entry:
- Draw / Erase
- Mark up text
- Add text
- Fill & Sign
- Annotations list

Reference screenshot: `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_005_annotate_sheet.png`.

Tool modes mostly reconfigure the **top bar menu** (Cancel/Done/Undo/Redo).

## 6.5 Search

Search is a top-bar mode with query field + hit navigation.

Reference screenshot: `tmp_geny_ui_gallery_20260201_101119/tmp_geny_ui_gallery_014_search.png`.

---

# 7) Side-by-side layout comparison (Acrobat vs OpenDroidPDF)

This section is intentionally “mechanical”: it maps each major user-visible surface to where it lives in each app.

## 7.1 Global navigation & file discovery

**Acrobat**
- Bottom nav tabs: Home / Files / Shared / Search
- A global “+” / FAB menu for create/open/scan

**OpenDroidPDF**
- Dashboard/Library (no tabs)
- “Library” accessed from the document top bar
- Open/new/settings are dashboard actions (not persistent when reading)

Impact:
- Acrobat feels like an “app ecosystem”; OpenDroidPDF feels like a “viewer/editor”.
- Users switching files frequently have more “always-visible” cues in Acrobat.

## 7.2 Reader navigation

**Acrobat**
- Scrubber tab on edge/bottom (1 gesture away at any time)
- Go-to-page dialog reachable from scrubber tab
- Navigation menu includes Thumbnails/Attachments and separates Bookmarks/Contents

**OpenDroidPDF**
- Bottom-center page indicator pill
- Scrubber + “go to page” live inside Navigate & View sheet (one extra step)
- No thumbnails/attachments panel today

Impact:
- Acrobat optimizes “scan a long doc quickly”.
- OpenDroidPDF optimizes “keep the reading surface clean; put navigation in a sheet”.

## 7.3 Tool entry & editing chrome

**Acrobat**
- Persistent bottom quick actions toolbar: Comment / Highlight / Draw / Text / Fill&Sign / More tools
- “More tools” grid for power workflows
- Tool modes primarily change bottom toolbars; top bar stays as doc-global

**OpenDroidPDF**
- No persistent tool strip in reading mode (tools live behind Annotate icon/sheet)
- Tools are surfaced via bottom sheets (Annotate, Export, Navigate & View)
- Tool modes mostly swap the top bar menu (Cancel/Done/Undo/Redo)

Impact:
- Acrobat is more immediately discoverable (visible tool icons).
- OpenDroidPDF is lower-clutter but requires learning “open the sheet” patterns.

## 7.4 View/presentation controls

**Acrobat**
- View settings dialog includes continuous/single page, reading mode, night mode
- Reachable via overflow menu in the reader

**OpenDroidPDF**
- Continuous vs paged is a setting (implemented)
- Reading mode / fullscreen / show annotations are in Navigate & View
- No night mode in reader chrome today

Impact:
- Acrobat is more “presentation-configurable” from inside the reader.
- OpenDroidPDF currently leans on fewer presentation concepts (by design), but misses night mode parity.

---

# 8) What specifically makes Acrobat feel “modern” (layout/UX mechanics)

These are layout mechanics, not “features”:

1) **Persistent global structure** (bottom tabs) so users always know “where they are”.
2) **Persistent quick tools** (bottom strip) for the most common annotation jobs.
3) **Always-available fast navigation** (edge scrubber) without opening a sheet.
4) **Clear separation of quick vs power workflows** (quick strip vs tools grid).
5) **Presentation controls are in-document** (view settings dialog).
6) **Structure navigation is first-class** (thumbnails, attachments, bookmarks/TOC).

OpenDroidPDF already matches some of the “modern reader” feel via continuous vertical scrolling + page-on-gray, but it still differs on (1), (2), (3), and “structure nav”.

---

# 9) Acrobat-parity roadmap for OpenDroidPDF (layout-first)

This section is deliberately opinionated: “if we want to look/feel like Acrobat”.

## 9.1 Add a Thumbnails surface (biggest navigation parity gap)

Acrobat: navigation menu explicitly includes **Thumbnails**.

OpenDroidPDF: add a first-class “Thumbnails” entry to:
- Navigate & View sheet, or
- A new “Navigate” surface that mirrors Acrobat’s nav menu concept.

Key layout goal:
- A grid/list of page thumbnails with a clear “tap to jump”.

## 9.2 Add an edge scrubber tab (fast-jump parity)

Acrobat: scrubber tab is on the right edge/bottom, always accessible.

OpenDroidPDF: keep the bottom page indicator pill, but optionally add:
- A right-edge draggable “page tab” that mirrors Acrobat’s gesture.

Key layout goal:
- Jump without opening a sheet.

## 9.3 Add “Night mode” (presentation parity)

Acrobat: view settings includes night mode.

OpenDroidPDF: add a reader chrome toggle (probably in Navigate & View) that:
- Inverts colors or uses a night rendering mode.

Key layout goal:
- It should be discoverable from the same place as fullscreen/reading mode.

## 9.4 Consider a persistent “quick tools” strip (discoverability parity)

Acrobat: bottom strip exposes common tools.

OpenDroidPDF options:
- Add an optional bottom “quick tools” row visible in reading mode, or
- Add a compact floating tool button that expands into quick actions.

Key layout goal:
- One-tap entry to Highlight/Draw/Text/Fill&Sign without requiring opening a sheet.

## 9.5 Consolidate structure navigation as a named menu

Acrobat has a conceptual “navigation menu” that groups:
- Thumbnails / Bookmarks / Contents / Attachments / Comments

OpenDroidPDF currently spreads these:
- Contents + Go to page: Navigate & View
- Annotations list: Annotate sheet
- (No thumbnails/attachments)

Key layout goal:
- A predictable place for “document structure”.

---

# Appendix A: “Where is X?” mapping table (quick reference)

| Task | Acrobat (placement) | OpenDroidPDF (placement) |
| --- | --- | --- |
| Switch files | Bottom tabs (Home/Files) | Library action (top bar) |
| Go to page | Scrubber tab → dialog | Page indicator pill → Navigate & View → Go to page |
| Fast jump | Edge/bottom scrubber tab | Navigate & View scrubber (sheet) |
| Ask about document (AI) | AI Assistant icon / contextual menu → assistant pane | Not present (yet) |
| Thumbnails | Navigation menu | Not first-class (yet) |
| TOC / Bookmarks | Overflow → Bookmarks & TOC (tabbed) | Navigate & View → Contents |
| View settings | Overflow → View settings | Navigate & View toggles + Settings |
| Night mode | View settings dialog | Not present (yet) |
| Highlight | Bottom quick tools | Annotate sheet → Mark up text |
| Draw | Bottom quick tools | Annotate sheet → Draw |
| Text tool | Bottom quick tools | Annotate sheet → Add text |
| Fill & Sign | Bottom quick tools | Annotate sheet → Fill & Sign |
| Power tools | “More tools” grid | Mostly via sheets/overflow; some features differ |
