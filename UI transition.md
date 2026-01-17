# UI Transition (Android)

This document is the “bridge” between:
- **Current UI (implemented today):** `ui_taxonomy.md`
- **Ideal UI (usability-first spec):** `ui_taxonomy_2.md`

Goal: clearly spell out **what needs to change** (behavior, placement, labels, and entry points) to move from the current UI to the ideal UI, while keeping stable identifiers (menu IDs + preference keys) intact wherever possible.

## One-sentence summary

Move from a mode-heavy, overflow-menu-driven UI with hidden long-press actions to a **reading-first** UI with **four stable primary entry points** (Library, Search, Annotate, Export…) plus a **page-indicator sheet** for navigation/view/document actions.

## Top-level deltas (what changes in the user’s mental model)

1. **“Open” becomes “Library”**: `menu_open` is the way to switch documents (Library overlay/screen), not a file picker inside the document.
2. **Secondary actions move out of overflow menus**: navigation, view toggles, and document actions move behind the **page indicator → Navigate & View sheet**.
3. **Editing becomes discoverable**: a single **Annotate** entry point opens a tool palette instead of scattering tools across multiple menus/modes.
4. **Export becomes one decision**: a single **Export…** entry point replaces a long flat list of share/save variants; advanced variants move behind “Advanced options…”.
5. **No long-press actions**: long-press is not used for user-facing commands; every core task has a visible, tappable entry point.
6. **Structural PDF edits get a home**: “Organize pages…” becomes the place for reorder/merge/extract/insert/delete/rotate pages.

## Terminology and label changes (user-facing)

These are label changes only; IDs should remain the same unless there’s a strong reason to add a new one.

- `menu_open`: **Open** → **Library**
- `menu_share`: **Share…** → **Export…**
- `menu_comments`: **Comments** → **Annotations**
- `menu_show_comments`: **Show comments** → **Show annotations**
- `menu_sticky_notes`: **Sticky notes (sidecar)** → **Show note markers**
- `menu_accept`: **Accept** → **Done**
- `menu_cancel`: should not be a trash icon labeled “Cancel”; split into **Cancel/Close (X)** vs **Delete (trash)** depending on context

## Status (Android)

Already implemented in the working tree:
- Page indicator bottom affordance (`R.id.page_indicator`) that opens the **Navigate & View** sheet.
- Navigate & View sheet for: Contents / Go to page / Fullscreen / Show annotations / Show note markers (sidecar) / Forms highlight (PDF) / Reading settings (EPUB) + contextual document actions (Save, Organize pages…, Add blank page, Delete note).
- Organize pages… now opens a dedicated sheet for structural PDF edits (staged changes → Done → Save a copy): Reorder pages (thumbnails) / Insert blank page (thumbnail position picker) / Insert pages from PDF (thumbnail position picker) / Create new PDF from pages / Merge another PDF / Remove pages / Rotate pages.
- Annotate palette (bottom sheet) behind **Annotate** (`menu_annotate`): Draw / Erase / Mark up text / Add text / Paste / Fill & Sign (PDF) / Annotations list.
- Annotations list (`menu_comments`, labeled **Annotations**) is currently reachable from Navigate & View as a shortcut; in the ideal UI its primary home is the Annotate palette.
- Export sheet behind **Export…** (`menu_share`) with “Share a copy”, “Save a copy…”, “Print”, and expandable “Advanced options…” variants (linearize/encrypt/flatten/save + sidecar bundle import/export).
- Long-press-only cancel/discard/delete removed; Cancel is an X, and Delete is explicit + confirmable.
- Selecting an annotation shows a contextual quick-actions popup (Properties/Delete/Duplicate/Arrange for text) while keeping the Reading top bar stable; Back clears selection first.
- Back unwinds Annot/AddingTextAnnot modes (with discard confirmation for in-progress ink).
- Core label alignment: Library / Export… / Annotations / Done.

Implementation anchors:
- Page indicator → Navigate & View: `platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java` + `platform/android/src/org/opendroidpdf/app/document/DocumentToolbarController.java` + `platform/android/res/layout/dialog_navigate_view_sheet.xml`
- Export… sheet: `platform/android/src/org/opendroidpdf/app/document/DocumentToolbarController.java` + `platform/android/res/layout/dialog_export_sheet.xml`
- Annotate sheet: `platform/android/src/org/opendroidpdf/app/annotation/AnnotationToolbarController.java` + `platform/android/res/layout/dialog_annotate_sheet.xml`
- Menu de-cluttering (hiding duplicates in main menu): `platform/android/src/org/opendroidpdf/app/toolbar/ToolbarStateController.java`

## Action relocation map (menu IDs)

This map is intentionally “high signal” (not exhaustive). It answers: **what’s the primary home** of each action in the ideal UI.

### Reading top bar (primary)
- `menu_open` (Library)
- `menu_search` (Search)
- `menu_share` (Export…)
- `menu_annotate` (Annotate → tool palette)

### Page indicator → Navigate & View sheet (primary)
- Navigate: `menu_toc`, `menu_gotopage`
- View: `menu_fullscreen`, `menu_show_comments`, `menu_sticky_notes`, `menu_forms`, `menu_reading_settings`
- Document: `menu_save` (when allowed), `menu_addpage` (shortcut → Organize pages… → Insert blank page), `menu_delete_note` (notes only)
- Shortcut (optional): `menu_comments` (Annotations list)
- **Organize pages…** (implemented)

### Export sheet (primary)
- Main entries: `menu_share` (opens sheet), `menu_print`
- Advanced variants: `menu_share_linearized`, `menu_share_encrypted`, `menu_share_flattened`, `menu_save_linearized`, `menu_save_encrypted`
- Sidecar bundle: `menu_export_annotations`, `menu_import_annotations`

### Annotate palette (proposed primary)
- Tools: `menu_draw`, `menu_erase`, `menu_add_text_annot`, `menu_paste_text_annot`, `menu_fill_sign`
- Markups: `menu_highlight`, `menu_underline`, `menu_strikeout`, `menu_squiggly`, `menu_caret`, `menu_replace`, `menu_delete_text`
- Annotations list: `menu_comments`

## Surface-by-surface transition map

Each section below answers:
- What we do today (from `ui_taxonomy.md`)
- What the ideal UI expects (from `ui_taxonomy_2.md`)
- What must change (concrete work items)

### 1) Library / Dashboard

**Current**
- Launch lands on the **Dashboard** with Open/New/Settings + recents.
- In-document `menu_open` opens a **dashboard overlay** on top of the document.

**Ideal**
- Treat this surface as **Library** (home): open/switch docs happens here, not “inside the document”.
- Back from a document (when not in a mode/dialog) returns to Library.

**Changes required**
- Relabel `menu_open` to **Library** and treat it as “switch/open docs”. (Implemented)
- Ensure “Open document…” lives in Library (not as a document action). (Implemented)
- Make Back semantics consistent with “Library-first” (document → Library rather than exit-app). (Implemented for task-root docs; external callers still fall back to system Back)

### 2) Reading mode chrome (top bar + bottom affordance)

**Current**
- Main toolbar can expose many icons and a large overflow menu (navigation, comments, forms, settings, export variants, etc.).
- Page indicator shows “current / total” and acts as the hub for navigation/view/document actions.

**Ideal**
- Reading mode top bar is stable and minimal:
  - Left: **Library** (`menu_open`)
  - Right: **Search** (`menu_search`), **Annotate** (tool palette), **Export…** (`menu_share`)
- Bottom: a **page indicator** (e.g., “12 / 245”) that opens the **Navigate & View sheet**.

**Changes required**
- Reduce main toolbar “icon soup”: keep only Library/Search/Annotate/Export… visible as primary actions. (Implemented)
- Add a tappable page indicator and wire it to open the Navigate & View sheet. (Implemented)
- Prefer disabling actions (with “why”) over removing/reordering icons as state changes.

### 3) Navigate & View (page indicator → sheet)

**Current**
- Navigation actions and view toggles are accessed via the page indicator → Navigate & View sheet (rather than the main toolbar overflow).

**Ideal**
- A single **Navigate & View sheet** (one layer; no nested submenus) with:
  - Navigate: Contents (`menu_toc`), Go to page (`menu_gotopage`)
  - View: Fullscreen (`menu_fullscreen`), Show annotations (`menu_show_comments`), Show note markers (`menu_sticky_notes`), Forms highlight (`menu_forms`), Reading settings (`menu_reading_settings`)
  - Document: Save changes (`menu_save`, when allowed), **Organize pages…** (new), Delete note (`menu_delete_note`, note docs only)

**Changes required**
- Implement the sheet UI and re-home these actions there. (Implemented)
- Remove duplicated “separate trees” for these actions; allow only shortcuts that open the same sheet. (Implemented)
- Keep “Forms highlight” as a view toggle, but make field navigation appear contextually (not only via hidden overflow).

### 4) Search

**Current**
- `menu_search` enters Search mode; SearchView close or Back exits and clears highlights.

**Ideal**
- Mostly the same, but Search is always one tap from Reading and visually consistent (Close, query, prev/next).

**Changes required**
- Keep behavior, but ensure Search is a stable top-bar icon in Reading. (Implemented)

### 5) Annotate (tool palette instead of scattered modes)

**Current**
- Editing tools are split across:
  - Main toolbar shortcuts (Draw, Add text, Fill & sign, Forms toggle)
  - Annot toolbar (draw/erase) with pen settings
  - Edit toolbar (annotation selected) with properties and delete hidden behind long-press
  - A text-annotation-only quick-actions popup (previously gated by `menu_show_comments`)
- Pen settings are reachable via visible controls while in Draw (ink color / pen size).

**Ideal**
- **Annotate** is one obvious entry point that opens a **Tool palette**:
  - Draw / Erase + visible pen options
  - Mark up text (highlight/underline/strikeout + proof marks)
  - Text box + paste
  - Fill & sign (PDF)
  - Annotations list (current `menu_comments`, renamed and re-scoped)

**Changes required**
- Add an Annotate tool palette surface and route existing tool actions through it.
- Keep gesture shortcuts only as optional accelerators; ensure pen options are discoverable via visible controls.
- Move “Annotations list” (currently “Comments”) under Annotate, not the document overflow.

### 6) Text markups (highlight/underline/etc.)

**Current**
- Enter selection by **long-press** or via **Annotate → Mark up text**; in selection mode, a tap selects text.
- Applying markups keeps you in selection mode (repeatable markups without re-entering selection).

**Ideal**
- A dedicated **Mark up text mode** reachable from Annotate that supports repeated markups and does not require long-press selection as the primary path (tap-to-select in markup mode).

**Changes required**
- Add an explicit **Mark up text** entry point and keep selection mode active for repeatable markups. (Implemented)
- Don’t rely on long-press selection: tap-to-select in markup mode is the primary path. (Implemented)
- Add an explicit **Done** action to exit markup/selection mode. (Implemented)
- Introduce undo/redo for embedded PDF markups so markup mode can be “Undo/Redo + Done”. (Implemented)

### 7) Annotation selection actions (Properties/Delete/Duplicate/Arrange)

**Current**
- Selection actions are surfaced via a small contextual **quick-actions popup** near the selected annotation.
- Annotation actions are not gated by `menu_show_comments` (visibility toggle ≠ edit permission).
- Delete is explicit + confirmable (not hidden behind long-press).

**Ideal**
- A single consistent **annotation actions surface** (prefer a bottom sheet) that always offers:
  - Properties, Delete (explicit), Duplicate
  - Multi-select entry + Arrange/Advanced section (align/distribute where applicable)
- Visibility of annotation actions must not depend on “show annotations” rendering preferences.

**Changes required**
- Decouple annotation actions from `menu_show_comments` (visibility toggle ≠ edit permission). (Implemented)
- Replace long-press-to-delete patterns with explicit, confirmable Delete actions. (Implemented)
- Ensure Back exits selection first (no activity exit surprises). (Implemented)

### 8) Cancel / Done / Delete semantics (remove long-press commands)

**Current**
- **Cancel (X)** exits a mode; it does not delete.
- **Delete** is an explicit action in edit/selection surfaces and prompts before deleting.
- Back unwinds draw/edit/add-text modes (with discard confirmation for in-progress ink).

**Ideal**
- **Cancel/Close (X)** exits a mode; **Done** commits and exits; **Delete** deletes content (never labeled Cancel).
- No user-facing action is triggered by long-press.
- Back should unwind state predictably; in draw/edit modes it should behave like Cancel (with confirmation if needed).

**Changes required**
- Remove long-press command handlers for cancel/discard/delete. (Implemented)
- Add explicit visible actions for discard and delete in the appropriate surfaces. (Implemented)
- Align Back behavior with the “unwind state” model. (Implemented)

### 9) Export / Share / Print

**Current**
- Export is accessed via **Export…** (`menu_share`) which opens an **Export sheet** (bottom sheet):
  - “Share a copy” (default) and “Print”
  - “Save a copy…” (standard PDF)
  - Expandable “Advanced options…” for export/save variants (linearize/encrypt/flatten) and sidecar bundle import/export (EPUB + read-only PDFs).

**Ideal**
- One **Export…** entry point (`menu_share`) opens an **Export sheet**:
  - Share copy (default), Save a copy…, Print
  - “Advanced options…” reveals flatten/encrypt/linearize, etc.
  - Sidecar bundle export/import is clearly grouped for EPUB/read-only PDFs

**Changes required**
- Consolidate export entry points into a sheet with progressive disclosure. (Implemented)
- Keep capability-gated items visible but disabled with a clear reason when possible.

### 10) PDF structure editing (reorder/merge/extract/insert/delete pages)

**Current**
- **Organize pages…** is available under Navigate & View → Document and provides (save-a-copy model):
  - Reorder pages (drag list + thumbnails)
  - Insert blank page (thumbnail position picker)
  - Insert pages from PDF (thumbnail position picker)
  - Create new PDF from pages (extract/cherry-pick)
  - Merge another PDF (append)
  - Remove pages
  - Rotate pages
- `menu_addpage` acts as a shortcut into **Organize pages… → Insert blank page** (staged-save model) rather than doing an immediate in-place edit.

**Ideal**
- Add **Organize pages…** under Navigate & View → Document:
  - Reorder (drag handle + Move up/down)
  - Delete pages (with confirmation)
  - Rotate pages
  - Insert blank page at a position
  - Insert pages from another PDF (merge)
  - Create new PDF from selected pages (extract/cherry-pick)
  - Save model: staged changes → Done → Save a copy (default) / Save changes (only if allowed)

**Changes required**
- Add an **Organize pages…** entry point and scaffold the flow from Navigate & View → Document. (Implemented)
- Implement a first-pass Organize pages sheet wired to qpdf ops (extract/merge/remove/rotate/reorder). (Implemented)
- Implement insert-at-position (blank pages + insert from PDF). (Implemented; thumbnail position picker)
- Add thumbnail previews + staged-save model. (Implemented: reorder + insert position show thumbnails; changes save on Done)
- Re-home or de-emphasize `menu_addpage` as a standalone toolbar action in favor of Organize pages. (Implemented: `menu_addpage` routes into Organize pages)

### 11) Settings

**Current**
- Settings is reachable from Dashboard and from the document menu (`menu_settings`).
- Settings contains a placeholder “Editor settings” screen.

**Ideal**
- Settings is a **Library** concern (global defaults); in-document toggles remain in the document UI (sheet/palette).

**Changes required**
- Keep Settings reachable from Reading in ≤2 taps via Library → Settings.
- Remove or replace placeholder settings screens so Settings doesn’t feel unfinished.

## Work breakdown (what to implement, in a sensible order)

This is an implementation-oriented checklist derived from the ideal spec.

### Phase 1 (reduce confusion without adding new features)
- Relabel `menu_open` to **Library** and ensure “Open document…” lives in Library. (Implemented)
- Create the **Navigate & View sheet** and move navigation/view toggles into it. (Implemented)
- Remove long-press-only cancel/discard/delete; split Cancel vs Delete semantics. (Implemented)
- Make Back unwind state predictably (sheet/dialog → selection/search → mode → document → Library). (Implemented for task-root docs)

### Phase 2 (make export/saving feel simple)
- Implement the **Export sheet** behind `menu_share` and move export variants under “Advanced options…”. (Implemented)
- Clarify “Save changes” vs “Save a copy…” and make read-only flows point to one obvious path. (Implemented)

### Phase 3 (make annotation workflows discoverable)
- Implement the **Annotate tool palette** (tools + Annotations list). (Implemented)
- Add a persistent **text markup mode** (repeatable markups, no long-press requirement). (Implemented)
- Implement a consistent **annotation actions surface** (Properties/Delete/Duplicate/Arrange) not gated by `menu_show_comments`. (Implemented)

### Phase 4 (structural PDF editing)
- Add **Organize pages…** and implement merge/extract/remove/rotate (save-a-copy first pass). (Implemented)
- Implement insert-at-position (blank pages + insert from PDF). (Implemented; thumbnail position picker)
- Implement thumbnail-based insert-at-position + staged-save model. (Implemented)
- Re-home `menu_addpage` into Organize pages (or keep it as a shortcut that opens Organize pages at “Insert blank page”).

## “Done” criteria (what success looks like)

- From Reading, a first-time user can find **Library, Search, Annotate, Export…**, and the **page indicator** without guessing.
- No core action is triggered by long-press (and long-press is never required).
- Export is a single entry point with advanced variants behind “Advanced options…”.
- Users can reorder/merge/extract pages from **Organize pages…** in ≤2 taps from Reading.
- “Show annotations” controls visibility only; it never blocks editing or access to annotation actions.
