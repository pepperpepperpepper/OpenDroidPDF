# Text Annotations vs Acrobat — Parity Gap Notes

Scope: **comment-style** text annotations (PDF `FreeText`, `Text`/sticky notes, and text-markup comments). This is intentionally **not** “Edit PDF” (true content editing) and **not** AcroForm filling.

## Current OpenDroidPDF baseline (what exists today)
- **FreeText** text boxes: create/edit text, move, deliberate resize (handles-only mode), auto-fit/grow unless user-resized, and basic style controls (font size, color, alignment).
- **Text markup**: highlight/underline/strikeout selection (with color/opacity managed by existing ink/markup settings).
- **Sidecar notes** (EPUB + read-only PDFs): FreeText-like text boxes rendered by OpenDroidPDF (plus a small marker), stored in `sidecar_annotations.db`, selected by tapping the marker/text, edited via the same text dialog.

## Acrobat “sidecar” model (Comments pane/list) — navigation + UX expectations
Acrobat’s comment system is opinionated about navigation: there is a **page markup** (icon/highlight/etc) and a **side list** (Comments pane/list) that lets you jump between comments without hunting visually.

### Desktop Acrobat (Comments pane)
- **Comments list in the right pane** shows all comments and provides:
  - sort (page/author/date/type/unread/color),
  - filter (type/read/unread, etc),
  - show/hide all comments toggle,
  - expand/collapse threads,
  - “open all pop-ups” / “minimize pop-ups” actions,
  - export/summary actions (comment summary, export to data file/Word).
- **Keyboard navigation** is first-class:
  - Tab / Shift+Tab moves focus to next/previous comment (or form field),
  - Enter opens the pop-up note for the focused comment,
  - R replies to the focused comment.
- **Preferences** exist specifically to reduce clutter and keep navigation sane with many comments (e.g., hide comment pop-ups when the Comments list is open; show a checkbox column for marking comments).

### Acrobat Reader Android (Comments list / text comment navigation)
- Shared-review files expose a **Comments List** view that lets you browse/select comments without hunting for them on the page.
- Text comments use a **comment icon** on the page; tapping it opens a bottom “comment edit panel” (reply/edit/etc).
- The “Text edit” dialog for added text/comments provides **< / > navigation arrows** to step through all text/comments in the document.

## Sidecar notes: OpenDroidPDF vs Acrobat (gap analysis)
OpenDroidPDF sidecar notes are conceptually similar to Acrobat comments, but the navigation experience is not yet comparable:

- **No global Comments list for sidecar docs**: there’s no single place to browse/search/sort notes and jump-to a note.
- **No next/previous comment navigation**: users must pan/zoom and visually locate markers/text, instead of stepping through comments.
- **Different “sticky note” behavior**:
  - Acrobat’s `Text` (sticky note) is primarily an **icon + pop-up + list entry**.
  - OpenDroidPDF sidecar notes are primarily **visible on-page text boxes** (FreeText-like) plus a small marker.
- **No threads/metadata**: sidecar notes currently don’t model replies, author, status/unread, or review history like Acrobat can.

## Recommended parity backlog for sidecar navigation (docs-only; not yet scheduled)
- Add a **Comments list UI** that is available for sidecar sessions (and ideally unified with embedded PDF annots):
  - list entries: page number, type (note/highlight/ink), snippet, created/modified time,
  - tap → jump-to and select,
  - filter by type + search by text.
- Add **next/previous comment** navigation affordances when a note/comment is selected (toolbar buttons and/or IME “next” style navigation).
- Add **show/hide sidecar annotations** toggle (match Acrobat “show/hide all comments” behavior).
- Consider a “sticky note mode” for sidecar notes (icon-only + pop-up) to reduce page clutter on dense documents.

## Where Acrobat is ahead (what’s missing / not at parity)

### 1) Text comment tool coverage
- **Callouts** (text box with leader line + endpoints + line ending styles).
- **Typewriter vs Text Box** distinction (different default behavior + properties in Acrobat; “typewriter” is effectively a free-text comment variant).
- **Text correction / proofreading markups** (insert/replace/delete style workflows that tie markup to replacement text).
- **Squiggly underline** and other text-review variants (beyond highlight/underline/strikeout).

### 2) Typography + rich text
- **Font family** selection (and robust embedding/substitution behavior across viewers).
- **Font styles**: bold/italic/underline/strikethrough; mixed styles within a single annotation.
- **Paragraph controls**: line spacing, margins/padding, indentation, bullets/numbering, and (often) vertical alignment.
- **Spellcheck** (and “edit like a text editor” affordances).
- **Per-annotation defaults/presets** (“make properties default”, style presets applied to new comments).

### 3) Box appearance + drawing properties
- **Fill / background color** (opaque/semi-opaque text box backgrounds).
- **Border** controls: color, width, dash style, corner rounding, and opacity.
- **Opacity** controls (commonly exposed per comment in Acrobat).
- **Rotation** of the text box.
- **Locking**: lock position/size and/or lock contents to prevent accidental edits.

### 4) Interaction / UX ergonomics
- **True in-place editing** (tap-to-place caret and edit on-canvas) rather than a modal dialog flow.
- **Contextual quick-action toolbar** near the selection (properties, delete, duplicate, align, etc.).
- **Copy/duplicate** annotations (and optionally “paste in place”, “paste to multiple pages”).
- **Multi-select + align/distribute** (and **group/ungroup** markups).
- **Undo/redo** for text annotation edits (content + style + geometry), not just ink strokes.

### 5) Comment management workflow
- **Comments list pane**: browse/sort/filter/search comments, jump-to-annotation, and show/hide comments globally.
- **Replies/threads**, review status, and review history per comment.
- **Author/metadata editing** (subject, icon, status) and consistent metadata display.

### 6) Interoperability / PDF “comment ecosystem”
- **FDF/XFDF import/export** for comment exchange with Acrobat-heavy workflows.
- **Rich-content preservation**: handling Acrobat-written rich text (`RC`/`DS`) and regenerating appearance streams without losing formatting.
- **Viewer compatibility**: ensuring FreeText renders consistently in non-MuPDF viewers (appearance streams, fonts, transparency, and flags).

## PDF-level mapping (useful when implementing parity items)
This is a non-exhaustive map of “Acrobat-like” features to common PDF fields/constructs:
- Text formatting: `/DA` (default appearance), and rich text via `/RC` + `/DS` (where used).
- Alignment: `/Q` (quadding).
- Opacity: `/CA`.
- Border: `/BS` (border style) and related appearance generation.
- Fill/interior: `/IC` (interior color).
- Callouts: `/CL` (callout line) + `/LE` (line endings) + intent (`/IT`) variants.
- Rendering consistency: `/AP` (appearance streams) and font resources.

## References (starting points)
- Adobe Acrobat desktop help:
  - View/filter comments (Comments pane): https://helpx.adobe.com/acrobat/desktop/share-and-review-documents/manage-reviews/view-comments.html
  - Add sticky notes/chat bubbles: https://helpx.adobe.com/acrobat/desktop/share-and-review-documents/review-documents/add-stickynotes-bubbles.html
  - Keyboard shortcuts (comments navigation): https://helpx.adobe.com/acrobat/using/keyboard-shortcuts.html
  - Print pop-up comments (mentions “Hide Comment Pop-ups When Comments List is Open”): https://helpx.adobe.com/acrobat/kb/print-comments-acrobat-reader.html
  - Show checkbox for comments (marking comments): https://helpx.adobe.com/acrobat/kb/add-checkmark-comment.html
- Acrobat for Android help:
  - Navigate/search + comments list: https://www.adobe.com/devnet-docs/acrobat/android/en/navigatesearch.html
  - Work with text comments (shared review): https://www.adobe.com/devnet-docs/acrobat/android/en/mv-review.html
  - Modify PDFs (“Text edit” dialog < / > navigation): https://www.adobe.com/devnet-docs/acrobat/android/en/mv-modifypdf.html
- PDFBox `PDAnnotationFreeText` / `PDAnnotationMarkup` API surface (good proxy for spec-supported FreeText capabilities).
- iText `PdfFreeTextAnnotation` API surface (another proxy for spec-supported FreeText capabilities).
