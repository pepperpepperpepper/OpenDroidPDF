# UI Taxonomy 2 (Android)

This document is an audited evolution of `ui_taxonomy.md` and doubles as a usability-first UI spec.

It is intentionally written in two layers:
- **Usability-first spec (proposed):** how the UI should be organized so common tasks are fast and discoverable.
- **Implementation inventory (current):** what exists today in the Android app, keyed by stable identifiers (menu item IDs + preference keys).

Scope: `platform/android` (OpenDroidPDFActivity + MuPDF reader/editor UI). Labels may differ by locale. Menu item IDs and preference keys are treated as stable identifiers for verification and maintenance.

## Usability-first UI spec (proposed)

This section is about *user experience*, not implementation. It proposes where actions/settings should live and what the “happy path” feels like, while the rest of this document pins down the current UI precisely.

### Design principles (non-negotiables)

1. **Reading-first**: opening and reading should be one-tap simple; editing tools should not crowd the reading experience.
2. **Low cognitive load**: the user should not have to choose between many export variants up front.
3. **Discoverable editing**: entering/exiting modes should be obvious and reversible (no “trapped” states).
4. **Safe by default**: destructive actions should be explicit and confirmable; “Cancel” should never secretly delete.
5. **Predictable Back**: Back should step “up” one level of intent (mode → document → dashboard), and only prompt for saving when actually leaving the document.
6. **One obvious place**: each action should have one “primary home”; duplicates are OK only as shortcuts.
7. **Progressive disclosure**: show the common 20% of actions 80% of the time; advanced tools live behind an “Advanced options…” step in a sheet.
8. **Explain state**: if something is unavailable (read-only, missing permission, feature gated), the UI should say why and what to do next.
9. **Explicit paths**: gestures can be shortcuts, but they should never be the only way to do something important.
10. **Consistent meaning**: the same word/icon should not mean different things in different modes (e.g., trash ≠ cancel).
11. **Thumb-friendly defaults**: common actions should not require “secret” long-presses or precision taps; make the primary path tappable and visible.
12. **Accessible by default**: tap targets should be comfortably large, actions should not rely on color alone, and critical paths should work well with one hand and common assistive tech.
13. **Two-taps to common tasks**: core actions should be reachable in ≤2 taps from Reading/Library whenever possible; avoid multi-level menus and long-press-only “hidden” actions.
14. **No long-press commands**: long-press should not trigger user-facing commands (especially destructive ones). If the platform still delivers long-press events, treat them as a no-op (or a non-command hint), not as a required interaction.

### Current usability pain points (what makes the UI feel unintuitive)

These are the biggest “why is this so confusing?” moments in the current UI, and they drive the spec priorities below.

- **Reading mode is cluttered**: too many always-visible icons and state-dependent actions make it hard to know what to tap first.
- **Cancel vs delete was ambiguous**: fixed by separating Cancel (X) from explicit Delete (confirmable).
- **“Open” didn’t match intent**: fixed by labeling `menu_open` as **Library** (dashboard/library surface).
- **Editing is fragmented**: draw/text/sign/comment tools are spread across multiple modes/menus, and some behavior depends on unrelated toggles (e.g., comment visibility).
- **Export is overwhelming**: many similar share/save variants show up as a flat list instead of one “Export…” entry point.
- **Back is inconsistent**: partially addressed (Back now exits draw/edit/add-text modes); remaining inconsistencies should be audited.
- **Important actions are hidden behind gestures**: partially addressed (delete/discard/pen settings no longer require long-press); text selection no longer requires long-press when using **Annotate → Mark up text** (tap-to-select in selection mode).

Spec priorities (fix-first):
- Make the default experience a clean “read” mode with minimal, stable affordances.
- Provide one obvious entry point for editing: **Annotate**.
- Separate **Cancel/Done** (mode exit) from **Delete** (destructive).
- Put secondary actions in one predictable place: a **Navigate & View sheet** (opened from the page indicator), plus a dedicated **Export…** sheet for export variants.
- Make Back consistently unwind state before leaving the document.

### Primary jobs-to-be-done (what users come here to do)

- Open a document (or a recent), then read (scroll/zoom) without thinking.
- Find something (search / table of contents / go to page).
- Mark up text quickly (highlight/underline/strikeout/etc.).
- Add handwritten ink or a text box; adjust style; move/resize; delete.
- Share/print/export a copy.
- Organize PDFs: reorder pages, remove pages, merge PDFs, and create a new PDF from selected pages.
- Fill & sign a PDF; interact with forms/widgets.
- Adjust defaults (autosave, display, annotation defaults).

### Proposed information architecture (where things should live)

Top-level places:
- **Dashboard (Home/Library)**: Open Document, New Document, Recents, Settings.
- **Document view**: reading + in-document actions (search, annotate, export).
- **Settings**: global defaults and “About”.

Rules:
- Anything that changes *this document only* should live in the document UI (menu or contextual UI), not Settings.
- Anything that sets a *default for future documents* should live in Settings.
- Advanced/export-variant actions should be reachable, but not shown as a long flat list in the primary reading surface; keep them behind the Export sheet’s “Advanced options…”.

### Navigation model (library-first)

The current “dashboard overlay” model is easy to implement but confusing to users because it makes “Open” feel like an in-document action. The spec should treat the dashboard as a first-class place: **Library**.

Proposed behavior:
- **Library is the home**: launch → Library; leaving a document returns to Library.
- **In a document, the left-most action is Library** (not “Open”):
  - Tap **Library** → show the dashboard overlay (current implementation) or navigate to the library surface (future).
  - From Library, **Open Document…** triggers SAF; **Recents** are one-tap.
- **Switching docs never looks like “opening inside the document”**: the user returns to Library, then chooses the next document.

Back should follow user intent:
- Back exits transient UI (selection/search/editing) first.
- Back from document (no transient UI) returns to Library (not exit-app), with a save prompt only if needed.

### Library (Home) surface (proposed)

Library is where users answer: “what do I want to open next?”

Entry points:
- App launch → Library.
- From any document → tap **Library** (`menu_open`).

Primary actions (top of screen; always visible):
- **Open document…** (system picker / SAF)
- **New document…** (creates a blank note document)
- **Settings** (global)

Recents (rest of screen):
- A scrollable list of recent documents with:
  - file name (primary), last opened (secondary), optional thumbnail
  - clear error state if the URI is no longer readable (with an action to re-grant access)
- Tapping a recent opens it immediately.

Usability rules:
- Library should never feel like a “modal inside the document”. If the Library is shown, the user is choosing what to open, not editing the current document.
- If memory is low, gracefully degrade thumbnails rather than hiding recents entirely.

### Document screen layout (proposed)

The goal is to make the most common actions always visible and keep the rest grouped in predictable places.

**Top app bar (reading mode):**
- Left: **Library** → `menu_open`
- Title: current document name (tap for document info; long name truncates with marquee/scroll-on-tap).
- Right icons (minimal set; stable positions):
  - **Search** → `menu_search`
  - **Annotate** (opens the tool palette) → (maps to existing tools: `menu_draw`, `menu_add_text_annot`, `menu_fill_sign`)
  - **Export…** (single entry point) → `menu_share` (opens an export sheet; see below)

Notes:
- Avoid “icon soup”: keep reading mode to 2–3 primary icons.
- Don’t let icons shift around as state changes; prefer disabling (with a brief “why”) over reflowing the toolbar.
- Prefer making link “Return” (`menu_linkback`) primarily a Back behavior and/or a temporary chip, not a permanent always-visible icon.

**Bottom edge (reading mode):**
- Show a simple page indicator (e.g., “12 / 245”) at all times; tapping it opens a **Navigate & View sheet** (one layer, no nested submenus):
  - Navigate: **Contents…** → `menu_toc`, **Go to page…** → `menu_gotopage`
  - View: **Fullscreen** → `menu_fullscreen`, **Show annotations** (toggle) → `menu_show_comments`, **Forms** (toggle; PDF) → `menu_forms`, **Reading settings…** (EPUB) → `menu_reading_settings`, **Show note markers** (sidecar) → `menu_sticky_notes`
  - Document (contextual): **Save changes** → `menu_save` (when allowed), **Organize pages…** (PDF) → (reorder/merge/extract), **Delete note…** (note docs) → `menu_delete_note`
- If tap-navigation margins remain, add a one-time hint so it’s discoverable and doesn’t feel “random”.

**Contextual UI (appears when relevant, disappears when done):**
- Text selection → selection toolbar (markups + copy) rather than forcing the user to hunt in menus.
- Annotation selection → a consistent “annotation actions” affordance (Properties / Delete / Duplicate / Align / Lock), without requiring any unrelated toggle like “show comments”.
- Forms/widgets → a lightweight “field navigation” affordance (Previous / Next) that appears only when a form field is active or when forms highlight is enabled.

**Mode indicator:**
- Whenever the menu/gestures change (Search/Selection/Edit/Draw/Add-text/Fullscreen), show a clear indicator of the current mode plus an obvious exit (e.g., a persistent “Done/Close” affordance, and “Cancel” only when discarding in-progress work is meaningful).

### Document states and controls (proposed)

The key to “feels intuitive” is that the user can always answer two questions:
1) **What mode am I in?**
2) **How do I get back to reading?**

Reading mode should stay stable; other states can be contextual, but they must always have an obvious exit.

| State | User intent | Primary controls | Exit / Back behavior |
| --- | --- | --- | --- |
| **Reading** | Read + navigate | Top app bar: **Library** (`menu_open`), **Search** (`menu_search`), **Annotate** (palette), **Export…** (`menu_share`). Bottom: page indicator → **Navigate & View sheet** (`menu_toc`, `menu_gotopage`, `menu_fullscreen`, view toggles…). | Back follows Back rules (exit transient UI → `menu_linkback` if available → Library). |
| **Search** | Find text | Search field + next/previous (`menu_previous`, `menu_next`). | Back/close exits search, clears highlights, returns to Reading. |
| **Text selection (ad-hoc)** | Copy/mark up text once | Selection actions (`menu_copytext`, markups). | Tap-away or Back clears selection and returns to Reading (after applying a markup, selection typically exits). |
| **Text markup mode** | Mark up text repeatedly | Selection actions (markups + copy), **Undo/Redo** (`menu_undo`, `menu_redo`), plus an explicit **Done** (`menu_accept`). | Back exits markup mode (it does *not* undo prior markups); after applying a markup, remain in this mode so multiple markups are easy. |
| **Annotate: draw/erase** | Handwritten ink | Tool mode bar: **Done** (`menu_accept`), **Cancel** (`menu_cancel`), Undo/Redo, tool options (`menu_pen_size`, `menu_ink_color`). | Back behaves like Cancel; Cancel confirms discard when there are in-progress strokes. Done commits and returns to Reading. |
| **Annotate: add text placement** | Add a text box | Placement bar: **Cancel** (`menu_cancel`); tap places annotation. | Back behaves like Cancel until placed; after placement, Back exits text editor before leaving document. |
| **Annotation selected** | Inspect/edit an existing annotation | A consistent actions surface (Properties, Delete, Duplicate, Move/Resize…). | Back exits selection first (no activity exit surprises). |
| **Export sheet** | Export/share/print | Export sheet actions (Share/Print/Advanced options). | Back closes the sheet and returns to Reading. |
| **Fullscreen** | Read without chrome | No app bar. Optional page indicator remains. | Back exits fullscreen (no save prompts). |
| **Library shown** | Switch/open docs | Library actions + recents list. | Back returns to document if it’s an overlay; otherwise back navigation returns to prior surface. |

### Toolbar and sheet layout contract (proposed)

To reduce “where did the UI go?” moments, all modes should share a predictable chrome layout so users can build muscle memory.

Global rules:
- **Close/Cancel** is always an **X**-style affordance that exits the current transient UI/mode. It is never a trash icon.
- **Done** (checkmark) is the universal “return to Reading” action for tool modes. It commits any completed actions and exits the mode.
- **Undo/Redo** appear only when meaningful, and stay in a stable position next to Done.
- **Delete** (trash) is reserved for destructive deletion of a selected object; it is never labeled “Cancel”.
- If a mode can discard in-progress work, Cancel/Close triggers a discard confirmation when needed.

Recommended placements (phone):
- **Reading top app bar**: left **Library** (`menu_open`), center title/status, right **Search**, **Annotate**, **Export…**.
- **Reading bottom edge**: page indicator opens the **Navigate & View sheet** (Contents/Go to page + view toggles + contextual document actions).
- **Search**: left **Close**, center query field, right **Previous/Next** hit navigation.
- **Annotate: draw/erase**: left **Cancel** (discard), right **Undo/Redo + Done**, with pen options discoverable via visible controls (palette entry or explicit buttons).
- **Text markup mode**: a persistent selection/markup surface with **Undo/Redo + Done** visible; applying a markup should not cause the controls to disappear.
- **Annotation selected**: keep reading chrome stable and surface annotation actions in one consistent place (prefer a bottom sheet) including **Properties** and **Delete**.

### Proposed action hierarchy (document view)

This is the intended grouping for ease-of-use (not the current XML structure).

Primary actions (icons; reading mode):
- **Library** (go back to Library/dashboard) → `menu_open`
- **Search** → `menu_search`
- **Annotate** (tool palette entry point):
  - Draw / erase → `menu_draw` / `menu_erase`
  - Pen settings → `menu_pen_size`, `menu_ink_color`
  - Mark up text (highlight/underline/strikeout; advanced proof marks listed in the same palette section, not hidden behind gestures) → `menu_highlight`, `menu_underline`, `menu_strikeout`, `menu_squiggly`, `menu_caret`, `menu_replace`, `menu_delete_text`
  - Add text box / Paste text box → `menu_add_text_annot`, `menu_paste_text_annot`
  - Fill & sign… → `menu_fill_sign`
- **Export…** → `menu_share` (and `menu_print` inside the export sheet)

Primary actions (icons; contextual-only):
- **Undo / Redo** (only in modes where it’s meaningful; stable position) → `menu_undo`, `menu_redo`
- **Return** (only when available; prefer a chip, not a menu hunt) → `menu_linkback`

Secondary actions (visible entry point; reading mode):
- **Navigate & View sheet** (tap page indicator) → Navigate (`menu_toc`, `menu_gotopage`), View (`menu_fullscreen`, `menu_show_comments`, `menu_sticky_notes`, `menu_forms`, `menu_reading_settings`), and Document actions (`menu_save` + Organize pages…).

Search mode hit navigation:
- `menu_previous`, `menu_next`

Annotate-internal (tool palette + contextual):
- Text selection markups should be accessible via the selection toolbar (and optionally as shortcuts in the Annotate palette) → `menu_highlight`, `menu_underline`, `menu_strikeout`, `menu_squiggly`, `menu_caret`, `menu_replace`, `menu_delete_text`, `menu_copytext`
- Annotations list should live in Annotate → `menu_comments` (comment previous/next remain contextual: `menu_comment_previous`, `menu_comment_next`)

Document management (contextual; inside Navigate & View sheet and/or a document-info sheet):
- `menu_save` (save changes back, when allowed)
- Organize pages… (PDF only; new) (reorder/rotate/remove pages; merge PDFs by inserting pages; extract selected pages to a new PDF)
- `menu_delete_note` (note documents only)

Export (Export sheet):
- `menu_share`, `menu_print`
- Advanced: `menu_share_flattened`, `menu_share_linearized`, `menu_share_encrypted`, `menu_save_linearized`, `menu_save_encrypted`
- Sidecar bundles (EPUB + read-only PDFs): `menu_export_annotations`, `menu_import_annotations`

### Tool palette (Annotate) (proposed)

Problem this solves: today, “editing” is split across multiple modes and hidden affordances. A single, consistent entry point reduces the number of places users have to look and makes the mental model “I’m annotating now”.

Entry point:
- Tap **Annotate** (top app bar) → opens the **Tool palette** (bottom sheet or popover).

Tool palette layout (suggested):
- **Tools (primary)**
  - **Draw** → `menu_draw`
  - **Erase** → `menu_erase` (only visible/enabled once ink exists, or always visible but disabled with “No ink to erase yet”)
  - **Mark up text** (common markups; enters a text-markup mode) → `menu_highlight`, `menu_underline`, `menu_strikeout` (advanced: `menu_squiggly`, `menu_caret`, `menu_replace`, `menu_delete_text`)
  - **Text box** → `menu_add_text_annot` (and **Paste** → `menu_paste_text_annot` when available)
  - **Fill & sign…** → `menu_fill_sign` (PDF only)
- **Tool options (contextual)**
  - For Draw: color/size → `menu_ink_color`, `menu_pen_size`
  - For Erase: eraser thickness (currently a global default: `pref_eraser_thickness`)
  - For Text box: properties shortcut → `menu_text_style` (opens the same style UI used when editing)

Behavior rules:
- Picking a tool immediately enters that mode and closes/collapses the palette.
- Mark up text should not require long-press:
  - While in text-markup mode, a **single tap selects a word** and shows selection handles.
  - The selection toolbar should surface markups + copy; after applying a markup, remain in text-markup mode so the user can mark up multiple areas without re-entering the tool.
  - Provide **Undo/Redo** while in text-markup mode so users can quickly correct mistakes without leaving the mode.
- Avoid “gesture-only” affordances in general: if an action is important, it must have a visible, tappable UI entry point (gestures are shortcuts, not requirements).
- While a tool mode is active, show a persistent exit affordance:
  - Always: **Done/Close** → `menu_accept`
  - Only when the mode has meaningful “in-progress” state that can be discarded: **Cancel** → `menu_cancel` (with a discard confirmation if needed)
- Tool selection should be sticky (remember last tool) *per document*, not globally.
- Don’t overload “Trash” to mean both cancel and delete; **Cancel** exits the tool, **Delete** deletes a selection.

### Annotation actions surface (selected annotation) (proposed)

When an annotation is selected, the UI should expose the *same* actions every time, in one obvious place (prefer bottom sheet / contextual bar).

Always-visible actions:
- **Properties** → `menu_text_style` (for text) or the equivalent properties UI for other annotation types
- **Delete** → (should be first-class; not long-press-only on `menu_cancel`)
- **Duplicate** → `menu_duplicate_text` (for FreeText / sidecar note)
- **Done** → `menu_accept` (or tap-away / Back exits selection)

Secondary actions (within the annotation sheet):
- Move → `menu_move`
- Copy / paste → `menu_copy_text_annot`, `menu_paste_text_annot`
- Resize handles toggle (text) → `menu_resize`
- Lock / group move / align / distribute (today these exist in quick-actions popup)

Rules:
- Do not gate this surface behind `menu_show_comments`. Comment visibility is a view preference, not an edit permission.
- If delete is triggered from a prominent UI element, confirm with an object-specific message.

### Saving model (proposed)

The UI should reduce “save anxiety” by being clear about what happens automatically vs explicitly.

Rules:
- If autosave is enabled (`pref_save_on_stop` / `pref_save_on_destroy`), avoid prompting unless the app is truly leaving the document with unsaved changes that cannot be saved automatically.
- Separate “Save changes” (save back to the same file/URI) from “Save a copy…” (export to a new file/URI).
  - `menu_save` should be treated/labeled as **Save changes** when saving back is possible.
  - “Save a copy…” should live under **Export…** (and may offer advanced variants like encrypt/linearize).
- For read-only URIs, show a single clear path to resolve it (existing banner action: **Enable saving**).
- Provide a lightweight, non-modal status cue so users don’t have to guess:
  - **Read-only** indicator when saving back is impossible.
  - Optional “Saved”/“Edited” cue near the title or in the Navigate & View sheet header (avoid toasts that spam).

### Navigate surface (proposed)

Navigation should be “one obvious place”, not scattered across dialogs and hidden gestures.

Entry points:
- Tap the page indicator to open the **Navigate & View sheet** (recommended).

Suggested contents:
- **Contents / outline…** → `menu_toc`
- **Go to page…** → `menu_gotopage`
  - Note: link return should primarily be a contextual **Return** chip (`menu_linkback`) and/or a Back behavior, not a “hunt for it in a menu” action.

Behavior rules:
- If the document has no outline, show an empty state with a short explanation instead of a blank list.
- After jumping, keep the user’s place discoverable (e.g., show current page number and allow “Back” via `menu_linkback` when the jump came from a link).

### View surface (proposed)

View controls are “how it looks”, not “what it does”.

Entry points:
- From the **Navigate & View sheet** (opened via the page indicator).

Suggested contents:
- **Fullscreen** → `menu_fullscreen`
- **Show annotations** (toggle) → `menu_show_comments`
- **Show note markers** (toggle; sidecar docs only) → `menu_sticky_notes`
- **Forms highlight** (toggle; PDF only) → `menu_forms`
- **Reading settings…** (EPUB only) → `menu_reading_settings`

Behavior rules:
- Toggles should explain scope: “this document only” vs “default for new documents”.
- If a feature is unavailable, keep it visible but disabled with a short “why”.

### Secondary actions surface (proposed)

The current UI uses an overflow menu, but in the proposed UX we avoid a “mystery meat” overflow menu by moving secondary actions into a single, predictable surface.

#### Navigate & View sheet

Entry point:
- Tap the **page indicator** in Reading mode.

Design rules:
- **One layer**: section headers/dividers only (no nested submenus for common actions).
- **No long-press commands**: every entry is a visible tap target.
- **No duplicates**: actions should not be listed here *and* elsewhere as separate menu trees (shortcuts are OK only if they open this same sheet).

Suggested sections (single list with headers):

1. **Navigate**
   - Contents… → `menu_toc`
   - Go to page… → `menu_gotopage`
2. **View**
   - Fullscreen → `menu_fullscreen`
   - Show annotations (toggle) → `menu_show_comments`
   - Show note markers (toggle; sidecar docs only) → `menu_sticky_notes`
   - Forms highlight (toggle; PDF only) → `menu_forms` (keep field navigation contextual: `menu_form_previous`, `menu_form_next`)
   - Reading settings… (EPUB only) → `menu_reading_settings`
3. **Document** (contextual)
   - Save changes (only when saving back is allowed) → `menu_save`
   - Organize pages… (PDF only; new action) → (reorder / rotate / delete / merge / extract; see Page organizer below)
   - Delete note… (note documents only) → `menu_delete_note`

What does *not* live here (to avoid confusion):
- **Annotate tools** and **Annotations list** live under **Annotate** (tool palette).
- **Export** lives under **Export…** (export sheet).
- **Settings** lives in **Library** (global).

Rule of thumb:
- Items can be conditionally shown/hidden (format, permissions, feature-gates), but the group ordering should stay stable.

#### Page organizer (PDF only) (proposed)

Entry point:
- Tap page indicator → Document → **Organize pages…**

What the user sees:
- A thumbnail list/grid of pages with page numbers.
- A visible reorder affordance on each page (e.g., a drag handle) plus an accessible alternative (Move up / Move down).
- Tap-to-select pages (checkbox or highlighted state) with a visible selection count; no long-press required for multi-select.

Supported actions (single-tap, no long-press commands):
- Reorder pages (drag handle or Move up/down).
- Remove pages (select pages → Delete, with confirmation).
- Rotate selected page(s) (90 degrees increments).
- Insert a blank page at a specific position (or “Insert after page N”).
- Insert pages from another PDF (merge):
  - Pick a source PDF, optionally choose a page range / selection, then insert at a chosen position.
  - Default action is “insert all pages at the end” to make a basic merge one-tap after picking the file.
- Create a new PDF from selected pages (extract/cherry-pick):
  - Select pages → **Create PDF…** → choose output filename/location (Save a copy).
  - Supports “drop pages” by selecting the pages to keep and exporting that selection, leaving the original unchanged.

Save model:
- Changes are staged until **Done**.
- On Done, always offer **Save a copy…** (default) and **Save changes** (only if saving back is allowed) so structural edits don’t accidentally overwrite the original.

### Export model (proposed)

The current UI is “feature complete” but overloads users with multiple export variants. The spec should funnel users through one entry point and reveal advanced options only when requested.

Entry point:
- A single **Export…** toolbar action that opens an **Export sheet**.

Export sheet (suggested layout):
- **Share copy** (default) → `menu_share`
- **Print** → `menu_print`
- **Save a copy…** (standard PDF) → Export sheet action (implemented)
- **Advanced options…**
  - **Flatten annotations** → `menu_share_flattened`
  - **Encrypt…** → `menu_share_encrypted` / `menu_save_encrypted`
  - **Optimize for fast web view** → `menu_share_linearized` / `menu_save_linearized`
  - **Export annotations bundle** (sidecar docs) → `menu_export_annotations`
  - **Import annotations bundle** (sidecar docs) → `menu_import_annotations`

Rules:
- Use plain language in user-visible labels; keep “qpdf/pdfbox” out of the UI.
- If an option is unavailable, show it disabled with a short “why” (e.g., “Requires Office Pack”, “Requires qpdf”, “Not available for EPUB”).

### Mode design and Back behavior (usability rules)

Mode entry/exit should be obvious:
- Entering a mode should visually confirm “you are now in X mode” (toolbar changes, highlighted tool icon, etc.).
- Exiting a mode should be one action away; Back should generally exit the current mode before trying to exit the document.

Back priority order (most local → most global):
1. Close transient surfaces (tool palette, export sheet, dialogs, comments list).
2. Exit selection / search.
3. Exit the active tool mode (draw / add-text / edit) via Cancel/Done semantics.
4. If a “return from link” target exists, Back returns there (same behavior as `menu_linkback`).
5. Return to Library from the document.

Cancel/delete should be unambiguous:
- “Cancel” should mean “leave this mode without applying changes”.
- “Delete” should mean “remove the selected thing”, with a confirmation when it’s easy to hit accidentally.
- Avoid binding destructive actions to long-press at all; expose a visible tap target for delete/discard (with confirmation as needed).

Saving prompts should be intent-based:
- Prompt to save when leaving the document (activity), not when merely leaving an editing sub-mode.

Mode rules (concrete):
- **Text markup mode**: provide explicit **Done** (`menu_accept`) and show **Undo/Redo** (`menu_undo`, `menu_redo`); applying a markup should not automatically exit the mode so multiple markups are fast.
- **Draw/erase**: provide explicit **Done** (`menu_accept`) and **Cancel** (`menu_cancel`) actions; Back should behave like Cancel (with a discard-confirmation if there are in-progress strokes).
- **Edit (annotations)**: Back exits Edit first; deletion is always a first-class action (not a hidden long-press).
- **Add text box**: Back cancels placement; after placement, Back exits the text editor (without losing text) before exiting the document.
- **Fullscreen**: Back exits fullscreen (no data-loss prompts).

### Annotation and editing UX (proposed)

The current UI has powerful capabilities but hides key actions behind state (modes) and long-press-only affordances. The spec should make annotation actions visible and predictable.

Selection model:
- **Tap selects**; tap-away deselects.
- **Second tap edits** (for editable text annotations) and should always work regardless of comment visibility.
- Selection should always present a consistent set of actions in one place (toolbar or bottom sheet): **Properties**, **Delete**, **Duplicate**, plus an **Arrange/Advanced** section for multi-select operations.

Quick actions:
- Do not gate quick actions behind `menu_show_comments`; comment visibility is a view preference, not an edit permission.
- Keep “Properties” discoverable: it should never require opening secondary panels/sheets.

Multi-select and arrange:
- Multi-select should have one consistent entry (e.g., a visible “Select multiple” toggle/button in the annotation actions surface, then tap other annotations).
- Show a clear selection count and only expose align/distribute when it applies (2+ selections).
- Keep “arrange” actions in the same annotation actions surface under an **Arrange/Advanced** section, not in a separate hidden popup.

Destructive actions:
- “Cancel” exits a mode; “Delete” deletes the selected object. Do not overload a single trash icon for both.
- If a destructive action is triggered from an easy-to-hit UI element, confirm with a clear, object-specific message (“Delete this text box?”).

### Settings organization (usability rules)

Proposed Settings sections (user-facing):
- **Saving**: autosave behaviors (`pref_save_on_destroy`, `pref_save_on_stop`)
- **Interaction**: stylus, smart selection, recents size (`pref_use_stylus`, `pref_smart_text_selection`, `pref_number_recent_files`)
- **Display**: keep screen on, fit width, paging axis (`keep_screen_on`, `pref_fit_width`, `pref_page_paging_axis`)
- **Annotation defaults**: thickness + default colors (`pref_ink_thickness`, `pref_eraser_thickness`, `pref_ink_color`, `pref_highlight_color`, `pref_underline_color`, `pref_strikeout_color`, `pref_textannoticon_color`)
- **About**: version/license/source/issues (`pref_about_version`, `pref_about_license`, `pref_about_source`, `pref_about_issues`)
- **Advanced**: experimental mode (`pref_experimental_mode`)

Rules:
- Per-document controls (e.g., comment visibility, forms highlight, EPUB reading settings) should live in the document UI under a predictable “View” or “Document” grouping, not inside global Settings.
- Avoid placeholder screens; if a Settings section exists, it should contain at least one user-relevant control.

### Terminology and labels (recommended)

The goal is to keep labels aligned with user intent (and avoid implementation jargon).

These current labels are from the English resources in `platform/android/res/values/strings_menu.xml` (other locales may vary).

| Internal ID | Current label | Recommended label | Why it’s clearer |
| --- | --- | --- | --- |
| `menu_open` | Open | Library | It’s the “go to library/switch docs” entry, not a file-open inside the document. |
| `menu_share` | Share… | Export… | “Export” describes the intent and can contain Share/Print/Save a copy. |
| `menu_linkback` | Back before link clicked | Return | Shorter + explains the action without “how it works”. |
| `menu_comments` | Comments | Annotations | The list includes markups/ink/text, not just comments. |
| `menu_show_comments` | Show comments | Show annotations | Matches what the toggle actually controls; avoid using it as an edit gate. |
| `menu_sticky_notes` | Sticky notes (sidecar) | Show note markers | Removes implementation jargon. |
| `menu_add_text_annot` | Add text | Text box | Matches user intent (“add a text box”). |
| `menu_accept` | Accept | Done | Standard “exit tool” language. |
| `menu_cancel` | Cancel | Cancel / Close (X icon) | If it remains a trash icon, it should be labeled as a destructive discard/delete action, not “Cancel”. |
| `menu_delete_text` | Delete (proof) | Strikeout (proof) | This is a markup/annotation, not true content deletion. |
| `menu_edit` (Edit toolbar) | Draw (uses `@string/menu_draw`) | Edit | Avoids a misleading label when the action is “edit this annotation”. |

### Proposed “happy path” flows (optimized)

These are the core flows the UI should make fast and obvious (even if implementation work remains).

- **Open and read**: Launch → Library → tap recent → read (no extra modes visible until requested).
- **Find**: Search icon → type → next/previous hits → Back exits search.
- **Highlight text**: Annotate → Mark up text → tap a word (drag handles to adjust) → Highlight; repeat as needed → Done.
- **Draw**: tap Annotate → Draw → draw → Done; Back cancels draw mode first.
- **Change pen color/size**: Annotate → Draw → Pen options (visible) → adjust → continue drawing → Done.
- **Add a text box**: tap Annotate → Text box → tap to place → type → Done; Back exits editor before leaving the document.
- **Delete an annotation**: tap annotation → actions surface (Properties/Delete/…) → Delete → confirm → returns to Reading.
- **Open annotation list**: tap Annotate → Annotations list… → tap item to jump → Back returns to document.
- **Jump via contents**: tap page indicator → Contents… → tap entry → return to Reading; `menu_linkback` (or Back behavior) returns if needed.
- **Reorder pages** (PDF): tap page indicator → Organize pages… → reorder (drag handle or Move up/down) → Done → Save a copy (default) / Save changes.
- **Merge PDFs**: open a PDF → tap page indicator → Organize pages… → Insert pages from PDF… → pick a PDF (defaults to “append all pages”) → Done → Save a copy.
- **Create a new PDF from pages**: open a PDF → tap page indicator → Organize pages… → select pages → Create PDF… → Save a copy.
- **Export**: Export… → Share copy (default) / Print / Advanced options.
- **Fill a form**: tap page indicator → Forms highlight → tap field → Next/Previous (contextual).
- **Sign**: Annotate → Fill & sign… → choose signature/initials → place → Done.

### Usability acceptance criteria (proposed)

These are concrete checks for “this feels intuitive” in a smoke-test / demo build.

- From Reading, a user can reliably find: **Library**, **Search**, **Annotate**, **Export…**, and the **page indicator** (Navigate & View sheet) without guessing.
- No user-facing command is bound to long-press (especially destructive actions like delete/discard); every action has a visible, tappable entry point.
- “Done/Close” exits the current mode; “Delete” removes content; the UI never labels a trash icon as “Cancel”.
- Back unwinds state in a predictable order (sheet/dialog → selection/search → mode → link return → Library) and never exits the activity from a deep mode.
- Marking up text does not require long-press selection at all (tap-to-select in markup mode), and multiple markups can be applied without re-entering the tool.
- Pen size/color are reachable through an explicit, tappable UI (no “secret” double-tap/long-press-only path).
- Export is a single entry point, with advanced variants behind “Advanced options…”.
- From Reading, the following are reachable in **≤2 taps** (when applicable): Contents, Go to page, Annotations list, Forms highlight, Fill & sign, Fullscreen, Settings.
- A first-time user can (a) highlight text, (b) draw, and (c) add a text box in **≤ 2 decisions** each (i.e., without hunting across multiple menus).
- For PDFs, a user can start **Organize pages…** in **≤2 taps** (page indicator → Organize pages…) and reorder/merge/extract pages without any long-press commands.
- Deleting a selected annotation is possible via a visible **Delete** action (with confirmation), not only via a long-press on a “Cancel” icon.
- Tap targets for primary actions (Library/Search/Annotate/Export/page indicator/Done/Delete) meet comfortable minimum sizes and work without relying on color alone.

### Proposed rollout (make the UI feel intuitive quickly)

This is a practical ordering of improvements to get most of the usability wins without trying to redesign everything at once.

Phase 1 (reduce confusion without adding new features):
- Rename `menu_open` user-facing label to **Library** and ensure “Open document…” lives inside Library, not as a document action.
- Remove “icon soup” in reading mode by moving editor tools behind **Annotate** and putting navigation/view/document toggles into the **Navigate & View sheet** (opened from the page indicator).
- Make **Cancel/Done** mean “exit mode”, and make **Delete** explicit (no long-press-only trash affordances).
- Make Back unwind state consistently (exit modes, then `menu_linkback`, then return to Library).

Phase 2 (reduce cognitive load in export/save):
- Introduce a single **Export…** sheet and hide advanced variants behind “Advanced options…”.
- Clarify “Save changes” vs “Save a copy…” and make read-only flows point to one obvious path.

Phase 3 (polish + discoverability):
- Add one-time, dismissible hints for hidden-but-important gestures (text selection, tap-navigation margins, link return).
- Make annotation selection actions and multi-select arrange actions consistent and discoverable (one surface, one mental model).

## Audit notes (what this version changes)

- Adds a **usability-first spec layer** (the section above) so this doc can guide UX decisions, not just list IDs.
- Separates **UI model / state machine** (modes + transitions + Back behavior) from the **inventory** (every action, submenu, and setting).
- Adds a **menu-ID index** so completeness can be checked quickly.
- Uses a consistent template for each UI surface: entry points → actions → dialogs/overlays → gestures → exit paths.
- Treats “non-toolbar” actions (snackbars, contextual popups, direct manipulation) as first-class UI, not footnotes.

## Sources of truth (code + resources)

Menus (action IDs live here):
- `platform/android/res/menu/main_menu.xml`
- `platform/android/res/menu/search_menu.xml`
- `platform/android/res/menu/selection_menu.xml`
- `platform/android/res/menu/edit_menu.xml`
- `platform/android/res/menu/annot_menu.xml`
- `platform/android/res/menu/add_text_annot_menu.xml`
- `platform/android/res/menu/empty_menu.xml`
- `platform/android/res/menu/debug_menu.xml` (debug builds only)

Settings (preference keys live here):
- `platform/android/res/xml/preferences.xml`

Key controllers (mode + behavior wiring):
- `platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java`
- `platform/android/src/org/opendroidpdf/DocViewFactory.java`
- `platform/android/src/org/opendroidpdf/app/toolbar/ToolbarStateController.java`
- `platform/android/src/org/opendroidpdf/app/toolbar/ToolbarMenuDelegate.java`
- `platform/android/src/org/opendroidpdf/app/navigation/BackPressController.java`
- `platform/android/src/org/opendroidpdf/app/reader/gesture/*`
- Structural PDF operations (qpdf-backed; not fully surfaced in UI today): `platform/android/src/org/opendroidpdf/core/PdfOpsService.kt`, `platform/android/src/org/opendroidpdf/core/PdfOps.kt`

## Menu ID index (quick completeness check)

Main toolbar (`platform/android/res/menu/main_menu.xml`):
- Document actions group: `menu_group_document_actions`
- `menu_save`, `menu_open`, `menu_gotopage`, `menu_toc`, `menu_search`, `menu_fullscreen`, `menu_settings`
- `menu_comments`, `menu_show_comments`, `menu_sticky_notes`, `menu_comment_previous`, `menu_comment_next`
- `menu_share`, `menu_share_linearized`, `menu_share_encrypted`, `menu_share_flattened`
- `menu_save_linearized`, `menu_save_encrypted`
- `menu_print`, `menu_export_annotations`, `menu_import_annotations`
- `menu_addpage`, `menu_reading_settings`, `menu_delete_note`, `menu_linkback`
- Editor tools group: `menu_group_editor_tools`
- `menu_undo`, `menu_redo`, `menu_draw`, `menu_add_text_annot`, `menu_paste_text_annot`
- `menu_forms`, `menu_form_previous`, `menu_form_next`, `menu_fill_sign`

Search toolbar (`platform/android/res/menu/search_menu.xml`):
- `menu_search_box`, `menu_previous`, `menu_next`

Selection toolbar (`platform/android/res/menu/selection_menu.xml`):
- `menu_copytext`, `menu_highlight`, `menu_underline`, `menu_strikeout`, `menu_delete_text`, `menu_squiggly`, `menu_caret`, `menu_replace`

Edit toolbar (`platform/android/res/menu/edit_menu.xml`):
- `menu_cancel`, `menu_undo`, `menu_redo`, `menu_save`, `menu_edit`, `menu_move`, `menu_resize`, `menu_text_style`, `menu_duplicate_text`, `menu_copy_text_annot`, `menu_paste_text_annot`, `menu_accept`

Annot (draw/erase) toolbar (`platform/android/res/menu/annot_menu.xml`):
- `menu_undo`, `menu_redo`, `menu_save`, `menu_cancel`, `menu_erase`, `menu_draw`, `menu_ink_color`, `menu_pen_size`, `menu_accept`

Add-text placement toolbar (`platform/android/res/menu/add_text_annot_menu.xml`):
- `menu_cancel`

Fullscreen/hidden toolbar (`platform/android/res/menu/empty_menu.xml`):
- (no action IDs)

Debug overlay (debug builds; `platform/android/res/menu/debug_menu.xml`):
- `menu_debug_snap_fit`, `menu_debug_show_text_widget`, `menu_debug_show_choice_widget`, `menu_debug_export_test`, `menu_debug_alert_test`, `menu_debug_render_self_test`, `menu_debug_qpdf_smoke`, `menu_debug_pdfbox_flatten`

## UI model (surfaces, modes, and transitions)

### Surfaces (top-level places a user can be)

1. **Dashboard** (entry screen)
   - Implemented as `DashboardFragment` hosted by the main activity.
2. **Document viewer/editor** (single activity)
   - Reader/editor surface hosting `MuPDFReaderView` + per-page `MuPDFPageView`.
3. **Settings** (global preferences)
   - Separate `SettingsActivity`/`SettingsFragment`.
4. **System UI** (external surfaces owned by Android)
   - SAF document picker, share sheet, print UI.

### Toolbar state machine (ActionBarMode)

The toolbar menu is “mode-driven”:
- **ReaderMode** describes touch behavior (viewing/searching/selecting/drawing/erasing/adding text).
- **ActionBarMode** describes *which menu XML is inflated*.

| ActionBarMode | Menu XML | Typical entry | Typical exit |
| --- | --- | --- | --- |
| Main | `main_menu.xml` | Open a document; clear selection; accept edit | Enter Search/Selection/Edit/Annot/Add-text/Hidden |
| Search | `search_menu.xml` | `menu_search` | Back / close SearchView |
| Selection | `selection_menu.xml` | Long-press selects text | Action applied or Back cancels |
| Edit | `edit_menu.xml` | Tap an annotation | Done/Cancel/Back exits selection; Delete removes selection |
| Annot | `annot_menu.xml` | `menu_draw` or stylus-down (if enabled) | Done commits; Cancel/Back exits (discard prompts when there are in-progress strokes) |
| AddingTextAnnot | `add_text_annot_menu.xml` | `menu_add_text_annot` | Place text annotation or Cancel |
| Hidden | `empty_menu.xml` | `menu_fullscreen` | Back exits fullscreen |
| Empty | `empty_menu.xml` | Dashboard visible | Open a document / hide dashboard |

### Back button behavior (important invariants)

- Fullscreen (Hidden): Back exits fullscreen.
- Dashboard shown over a document: Back hides the dashboard overlay.
- Search: Back exits search and clears results.
- Text selection: Back cancels selection.
- Draw/erase (Annot): Back exits the mode; when there are in-progress strokes, it prompts to discard.
- Edit and add-text placement: Back exits the mode first (no “trapped” states).
- Unsaved changes: Back prompts with **Save/Save as**, **No**, **Cancel**.

## Inventory (every user action + submenu)

### 1) Dashboard

Where:
- `platform/android/src/org/opendroidpdf/app/DashboardFragment.java`

Actions:
- **Open Document**: launches the Android document picker; chosen URI is reopened in the activity.
- **New Document**: prompts for a filename; creates and opens a new blank PDF “note document” in the notes directory.
- **Settings**: opens `SettingsActivity`.
- **Open recent**: opens a recent document entry and closes the previous activity instance.

### 2) Document viewer/editor (common interactions)

Where:
- Activity: `platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java`
- View factory + mode wiring: `platform/android/src/org/opendroidpdf/DocViewFactory.java`

Gestures / direct interactions:
- Pan/scroll, pinch-zoom in the document view.
- **Tap navigation** (view mode and search mode):
  - Tap near the **top/left margin** → navigate backward.
  - Tap near the **bottom/right margin** → navigate forward.
  - Tap the main document area → clears contextual state (e.g., exits Edit back to Main).
  - When a text annotation is selected, margin taps behave like a main-area tap (deselect) to avoid accidental page navigation.
  - In Add-text placement mode, tapping places the text annotation and exits add-text mode.
- **Links**: tapping a link follows it; internal links record a “link-back” target.

Key anchor: `platform/android/src/org/opendroidpdf/app/reader/gesture/TapGestureRouter.java`

### 3) Main toolbar (document actions + editor tools)

Where:
- Menu: `platform/android/res/menu/main_menu.xml`
- Action handling: `platform/android/src/org/opendroidpdf/app/document/DocumentToolbarController.java`
- Export flows: `platform/android/src/org/opendroidpdf/app/document/ExportController.java`
- Visibility/enablement: `platform/android/src/org/opendroidpdf/app/toolbar/ToolbarStateController.java`

Notes:
- Items may appear as toolbar icons or overflow depending on `showAsAction`, screen size, and state.
- Some items are format- or capability-gated (PDF vs EPUB; writable vs read-only; qpdf/pdfbox availability).

Usability notes (opportunities):
- `menu_open` is labeled **Library** and shows the dashboard/library (the “open/switch docs” surface).
- Export is consolidated behind **Export…** (`menu_share`), which opens an Export sheet with “Share a copy”, “Save a copy…”, “Print”, and expandable “Advanced options…” variants.
- Cancel vs delete is now explicit: Cancel is an X, and Delete is a first-class action (no long-press-only destructive affordances).

#### A) File/document actions (`menu_group_document_actions`)

Open/navigation:
- **Save changes** (`menu_save`): saves back to the current URI (writable PDFs only); accessible via page indicator → Navigate & View → Document.
- **Library** (`menu_open`): shows the dashboard/library (the “open/switch docs” entry point).
- **Go to page** (`menu_gotopage`): page indicator → Navigate & View → Navigate.
- **Contents** (`menu_toc`): page indicator → Navigate & View → Navigate (PDF outline; EPUB toc fallback).
- **Search** (`menu_search`): enters Search mode.
- **Fullscreen** (`menu_fullscreen`): page indicator → Navigate & View → View (hides system + app bars; disables link taps while active).
- **Settings** (`menu_settings`): opens global settings (reachable via dashboard/library).

Comments:
- **Annotations list** (`menu_comments`): page indicator → Navigate & View → View (dialog listing comment-style annotations; selecting an entry jumps to and centers it).
- **Show annotations** (`menu_show_comments`, checkable): page indicator → Navigate & View → View (toggles rendering of comment-style annotations).
- **Show note markers** (`menu_sticky_notes`, checkable): page indicator → Navigate & View → View (sidecar docs; marker-only note rendering mode).
- **Previous/Next comment** (`menu_comment_previous`, `menu_comment_next`): only shown when a comment is selected; jumps between comment-style annotations.

Export / interop:
- **Export…** (`menu_share`): opens an **Export sheet** (bottom sheet) that provides:
  - **Share a copy**: exports a copy and opens the Android share sheet.
  - **Save a copy…**: exports a PDF copy to a user-selected location (SAF create-document).
  - **Print** (`menu_print`): exports a copy and invokes Android printing.
  - **Advanced options…** (contextual): reveals additional export variants:
    - **Export (linearized)** (`menu_share_linearized`): shares a qpdf-linearized PDF copy (qpdf ops only).
    - **Export (encrypted)** (`menu_share_encrypted`): prompts for passwords, then shares an encrypted copy (qpdf ops only).
    - **Export (flattened)** (`menu_share_flattened`): shares a flattened copy (pdfbox when available; otherwise raster fallback).
    - **Save (linearized)** (`menu_save_linearized`): saves a qpdf-linearized copy to a user-selected URI.
    - **Save (encrypted)** (`menu_save_encrypted`): prompts for passwords, then saves an encrypted copy to a user-selected URI.
    - **Export annotations** (`menu_export_annotations`): exports/shares a sidecar annotation bundle (EPUB and read-only PDFs).
    - **Import annotations** (`menu_import_annotations`): imports a sidecar annotation bundle (EPUB and read-only PDFs).

PDF-only mutation:
- **Add blank page** (`menu_addpage`): inserts a blank page at the end of a PDF (page indicator → Navigate & View → Document).

EPUB-only:
- **Reading settings** (`menu_reading_settings`): per-document reflow settings (font/margins/line spacing/theme) via page indicator → Navigate & View.

Special note documents:
- **Delete note** (`menu_delete_note`): deletes the “note document” created by **New Document** and returns to dashboard (page indicator → Navigate & View → Document).

Link navigation:
- **Link back** (`menu_linkback`): returns to the pre-link viewport; visible only when a link-back target exists.

#### B) Editor tools (`menu_group_editor_tools`)

Core tools:
- **Undo / Redo** (`menu_undo`, `menu_redo`): primarily affects ink strokes (and other operations routed through the page view’s undo stack).
- **Draw** (`menu_draw`): enters drawing mode.
  - Pen settings are reachable via visible controls while drawing (ink color / pen size).
- **Add text annotation** (`menu_add_text_annot`): enters “tap to add” text annotation placement mode.
- **Paste text annotation** (`menu_paste_text_annot`): pastes a copied text annotation (enabled only when the internal clipboard has a payload).

PDF-only editor tools:
- **Forms** (`menu_forms`, checkable): toggles highlighting of widget bounds (primary entry point: Navigate & View sheet; the toolbar icon is hidden).
- **Previous / Next form field** (`menu_form_previous`, `menu_form_next`): navigates widgets in reading order (only shown when Forms highlight is enabled).
- **Fill & sign** (`menu_fill_sign`): starts the signature/initials/stamps placement workflow.

### 4) Search toolbar (Search mode)

Where:
- Menu: `platform/android/res/menu/search_menu.xml`

Actions:
- **Search box** (`menu_search_box`): type + submit a query.
- **Previous / Next hit** (`menu_previous`, `menu_next`).
- Closing the SearchView or pressing Back exits search and clears highlights.

### 5) Selection toolbar (text selection)

Where:
- Menu: `platform/android/res/menu/selection_menu.xml`

Entry:
- Long-press on page content starts text selection; drag handles to adjust.

Actions:
- **Copy** (`menu_copytext`)
- **Highlight** (`menu_highlight`)
- **Underline** (`menu_underline`)
- **Strikeout** (`menu_strikeout`)
- **Delete text** (`menu_delete_text`): creates a strikeout annotation (this is “commenting”, not true content deletion).
- **Squiggly** (`menu_squiggly`)
- **Caret** (`menu_caret`)
- **Replace** (`menu_replace`): strikes out selection and adds a caret-style “insert here” marker.

After applying a markup action, selection mode exits back to viewing.

### 6) Edit toolbar (annotation selected)

Where:
- Menu: `platform/android/res/menu/edit_menu.xml`

Entry:
- Tap an annotation (ink/text/other supported types).

Actions:
- **Cancel (X)** (`menu_cancel`): exits selection without deleting.
- **Delete** (`menu_delete_annotation`): deletes the selected annotation (explicit + confirmable).
- **Undo / Redo** (`menu_undo`, `menu_redo`)
- **Save changes** (`menu_save`): saves back to the current URI (writable PDFs only).
- **Edit** (`menu_edit`): edits the selected annotation (ink edits transition into drawing).
- **Move** (`menu_move`): shows a hint; actual movement is done by dragging the selection box.
- **Resize** (`menu_resize`, checkable): toggles resize handles (text annotations).
- **Text style / Properties** (`menu_text_style`): text style dialog for FreeText / sidecar notes.
- **Duplicate** (`menu_duplicate_text`): duplicates the selected text annotation (FreeText / sidecar note).
- **Copy / Paste text annotation** (`menu_copy_text_annot`, `menu_paste_text_annot`)
- **Done** (`menu_accept`): deselects and returns to viewing.

Comment navigation:
- **Previous / Next comment** (`menu_comment_previous`, `menu_comment_next`): when shown, jumps between comment-style annotations while in an edit context.

### 7) Annot toolbar (draw/erase mode)

Where:
- Menu: `platform/android/res/menu/annot_menu.xml`

Actions:
- **Undo / Redo** (`menu_undo`, `menu_redo`)
- **Save changes** (`menu_save`): saves back to the current URI (writable PDFs only).
- **Cancel (X)** (`menu_cancel`): exits annotate mode; prompts to discard when there are in-progress strokes.
- **Erase / Draw** (`menu_erase`, `menu_draw`): toggles eraser vs pen.
  - Switching to eraser forces a commit of pending ink first so strokes become erasable.
- **Ink color / Pen size** (`menu_ink_color`, `menu_pen_size`): pen settings dialog (Draw only; hidden in Erase).
- **Done** (`menu_accept`): commits ink and exits.

### 8) Add-text placement toolbar (tap to place)

Where:
- Menu: `platform/android/res/menu/add_text_annot_menu.xml`

Actions:
- **Cancel** (`menu_cancel`): exits add-text placement without placing anything.

### 9) Debug-only overflow actions (debug builds)

Where:
- Menu overlay: `platform/android/res/menu/debug_menu.xml`
- Controller: `platform/android/src/org/opendroidpdf/app/debug/DebugActionsController.java`

Actions:
- **Snap-to-fit width** (`menu_debug_snap_fit`)
- **Show text widget dialog** (`menu_debug_show_text_widget`)
- **Show choice widget dialog** (`menu_debug_show_choice_widget`)
- **Export test** (`menu_debug_export_test`)
- **Alert test** (`menu_debug_alert_test`)
- **Render self-test** (`menu_debug_render_self_test`)
- **qpdf smoke test** (`menu_debug_qpdf_smoke`) (PDF open only)
- **pdfbox flatten** (`menu_debug_pdfbox_flatten`) (PDF open + pdfbox available)

## Contextual / non-toolbar UI (still user actions)

### Snackbars (banners) with actions

Where:
- Host: `platform/android/src/org/opendroidpdf/app/ui/UiStateDelegate.java`
- Decision points: `platform/android/src/org/opendroidpdf/app/hosts/DocumentSetupHostAdapter.java`, `platform/android/src/org/opendroidpdf/app/hosts/DocumentToolbarHostAdapter.java`

Banners:
- **PDF read-only** → action **Enable saving** (reopens document picker to refresh permissions).
- **Imported Word** → action **Learn more** (explainer dialog).
- **EPUB reflow layout mismatch** → action **Switch to annotated layout** (restores the layout snapshot under which sidecar annotations are visible).
- **PDF XFA unsupported** → action **Learn more** (action list: convert via XFA Pack if installed, install XFA Pack, open in another app, share).

### Text annotation quick actions popup

Where:
- `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationQuickActionsController.java`

Visibility:
- Shown only when a text annotation (FreeText / Text / sidecar note) is selected *and* comments are currently visible (`menu_show_comments` checked / `pageView.areCommentsVisible()`).

Buttons/actions:
- **Properties**: opens text style dialog.
- **Duplicate**: duplicates selection.
- **Multi-select: add**: adds current selection into the per-page multi-select set.
- **Multi-select: align/distribute**: picker of align/distribute operations (requires 2+; includes a “clear selection” entry).
- **Multi-select: group**: grouped move toggle.
- **Lock**: lock position/size toggle.
- **Fit to text** (FreeText only): resize box to better fit content.
- **Delete**: immediate delete.

### Direct manipulation for text annotations (move/resize)

Where:
- `platform/android/src/org/opendroidpdf/app/reader/gesture/TextAnnotationManipulationGestureHandler.java`

Actions/gestures:
- Drag inside selection bounds to move.
- Drag the move handle (top-center) to move (when shown).
- When `menu_resize` is enabled, drag resize handles to resize.
- When “Lock position/size” is enabled, move/resize is blocked and the UI hints it’s locked.
- Multi-select group applies movement to the entire selection set (same page only).

### Text annotation editing UI (inline editor + dialog fallback)

Where:
- `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationController.java`

Entry points:
- `menu_edit`, or
- Tap a *selected* text annotation again (short-tap within the system tap timeout) to open the editor directly.

Affordances:
- Multi-line text input (inline if supported; dialog otherwise).
- Optional rich-text warning when editing an Acrobat-style rich FreeText.
- Optional previous/next comment navigation controls (best-effort).

### Comments list dialog

Entry point: `menu_comments`

Where:
- `platform/android/src/org/opendroidpdf/app/comments/CommentsListController.java`

Affordances:
- Search field (filters list).
- Type filter (All / Notes / Text boxes / Markups / Ink).
- Selecting an entry jumps to its page and attempts to select it.

### Pen settings dialog (size + color)

Entry points:
- From Annot toolbar (`menu_ink_color`, `menu_pen_size`)

Where:
- `platform/android/src/org/opendroidpdf/app/annotation/PenSettingsController.java`

Affordances:
- Thickness slider with preview.
- Color palette grid.

### Text style dialog (text box properties)

Entry points:
- Edit toolbar (`menu_text_style`)
- Text quick actions popup (**Properties**)

Where:
- `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationStyleController.java`

Affordances:
- Font size/family/style, alignment, line spacing/indent.
- Text + background colors, background opacity.
- Fit-to-text, rotation.
- Border: color/style/width/radius.
- Locks: lock position/size; lock contents.

### Reflow “Reading settings” dialog (EPUB only)

Entry point: `menu_reading_settings`

Where:
- `platform/android/src/org/opendroidpdf/app/reflow/ReflowSettingsController.java`

Affordances (per-document):
- Font size, margins, line spacing, theme.

Special behavior:
- If ink exists, layout-affecting changes may be blocked to avoid drifting geometry-anchored strokes; theme changes remain allowed.

### Forms/widgets UI (PDF only)

Entry points:
- Toggle `menu_forms` to highlight widgets.
- Tap a widget to edit.
- Use `menu_form_previous` / `menu_form_next` when Forms highlight is enabled.

Where:
- `platform/android/src/org/opendroidpdf/MuPDFPageViewWidgets.java`
- `platform/android/src/org/opendroidpdf/app/signature/SignatureFlowController.java` (signature flows)

### Fill & sign (PDF only)

Entry point: `menu_fill_sign`

Where:
- `platform/android/src/org/opendroidpdf/app/fillsign/FillSignController.java`

Flow:
- Choose: signature / initials / checkmark / cross / date / name.
- If needed, capture signature/initials once (or enter name once).
- Place on page:
  - Signature/initials/check/cross: touch down to preview; drag/rotate/scale; lift to commit (ink annotation).
  - Date/name: tap to place (FreeText annotation).

### Password and permission dialogs

Where:
- Password: `platform/android/src/org/opendroidpdf/app/dialog/PasswordDialogHelper.java`
- Permission + Word import: `platform/android/src/org/opendroidpdf/app/document/DocumentSetupController.java`

Dialogs:
- Password prompt for encrypted PDFs / protected docs.
- Permission prompt when URI access is missing; offers “Grant access”.
- Word import unavailable: offers “open in other app” and optionally “install Office Pack”.
- Encrypted/DRM EPUB not supported: informs user the file cannot be opened.

## Settings taxonomy (where settings live)

This section describes the *current* Settings UI (as implemented in `preferences.xml`). For the proposed, user-facing grouping and naming, see **Settings organization (usability rules)** above.

### Global settings (SettingsActivity → SettingsFragment)

Navigation:
- Dashboard → **Settings**, or
- Document menu → `menu_settings`

Source: `platform/android/res/xml/preferences.xml`

Autosave:
Category key: `pref_autosave_settings`
- Save on destroy (`pref_save_on_destroy`)
- Save on stop (`pref_save_on_stop`)

UI:
Category key: `pref_ui_settings`
- Use stylus (`pref_use_stylus`)
- Smart text selection (`pref_smart_text_selection`)
- Number of recent files (`pref_number_recent_files`)

Editor:
Screen key: `pref_editor_settings` (currently a placeholder screen)
- Placeholder item: `pref_editor_settings_placeholder` (disabled, not selectable)

Display:
Category key: `display_settings`
- Keep screen on (`keep_screen_on`)
- Fit width (`pref_fit_width`)
- Page swipe direction (`pref_page_paging_axis`)

Annotation:
Category key: `pref_annotation_settings`
- Ink thickness (`pref_ink_thickness`)
- Eraser thickness (`pref_eraser_thickness`)
- Ink color (`pref_ink_color`)
- Highlight color (`pref_highlight_color`)
- Underline color (`pref_underline_color`)
- Strikeout color (`pref_strikeout_color`)
- Text annotation icon color (`pref_textannoticon_color`)

About (`pref_about_screen`):
- Version (`pref_about_version`, read-only)
- License (`pref_about_license`)
- Source (`pref_about_source`)
- Issues (`pref_about_issues`)

Experimental:
Category key: `pref_experimental_mode_settings`
- Experimental mode (`pref_experimental_mode`)

### In-document toggles and per-document settings (not in SettingsActivity)

- Show comments (`menu_show_comments`)
- Sticky notes (`menu_sticky_notes`)
- Forms highlight (`menu_forms`)
- Reading settings (`menu_reading_settings`)
- Pen settings dialog (ink thickness/color while drawing)
- Text style dialog (per-annotation properties + locks)
- Fill & sign captured values (stored for reuse by Fill & sign)

### Keys present but not currently exposed in UI

These keys exist in `preferences.xml` but are commented out or only used as test scaffolding, so they are not reachable by normal users:
- `pref_scroll_vertical`
- `pref_scroll_continuous`
- `pref_test_list_preference`

## User flows (end-to-end)

### Launch → open a document
1. Launch → dashboard.
2. Tap **Open Document** → pick a file.
3. If prompted, enter password or grant permissions.
4. Document opens in Main mode.

### Switch to another document (dashboard overlay)
1. While a document is open, tap `menu_open` to show the dashboard overlay.
2. Choose a recent entry, or tap **Open Document** to pick a different file.
3. The newly selected document opens; Back hides the dashboard overlay.

### Navigate within a document (contents / go to page / tap navigation)
- `menu_toc` opens the outline/contents list; selecting an entry jumps to that location.
- `menu_gotopage` opens the “Go to page” dialog, then jumps to the entered page.
- Tap navigation (view/search mode):
  - tap near the **top/left margin** → back
  - tap near the **bottom/right margin** → forward

### Search within a document
1. `menu_search` → Search mode.
2. Type query (`menu_search_box`) and submit.
3. Navigate hits with `menu_previous` / `menu_next`.
4. Back/close search to exit and clear highlights.

### Follow links → return (“link back”)
1. Tap a link in the document.
2. If it’s an internal jump, the app records a “link-back” target.
3. Tap `menu_linkback` (only visible when available) to return to the previous viewport.

### Comments (visibility + list + navigation)
- Toggle comment rendering with `menu_show_comments` (page indicator → Navigate & View).
- For sidecar docs (EPUB + read-only PDFs), toggle marker-only note rendering with `menu_sticky_notes` (page indicator → Navigate & View).
- Open the comments/annotations list with `menu_comments` (page indicator → Navigate & View), then filter and select an entry to jump to it.
- When available, use `menu_comment_previous` / `menu_comment_next` to move between comment-style annotations.

### Draw / erase ink
1. Enter draw via `menu_draw` (or stylus-down if enabled).
2. Draw strokes.
3. Optional: pen settings via `menu_ink_color` / `menu_pen_size`.
4. Exit:
   - `menu_accept` commits + exits, or
   - `menu_cancel` / Back exits (discard prompts when there are in-progress strokes).
5. To erase: in Annot mode tap `menu_erase`, erase strokes, then `menu_accept`.

### Add and edit a text annotation
1. `menu_add_text_annot` enters placement mode.
2. Tap to place the text box; editor opens (inline or dialog).
3. To edit later: select annotation → `menu_edit`, or tap the selected annotation again.
4. Style/lock via `menu_text_style` or quick-actions **Properties**.
5. Move/resize via drag gestures (resize handles via `menu_resize`).
6. Delete via `menu_delete_annotation` (Edit toolbar) or quick-actions **Delete**.
7. Note: the quick-actions popup is only shown when comments are visible (`menu_show_comments` enabled).

### Mark up selected text (highlight/underline/etc.)
1. Long-press to select text.
2. Adjust handles.
3. Apply `menu_highlight` / `menu_underline` / `menu_strikeout` / `menu_delete_text` / `menu_squiggly` / `menu_caret` / `menu_replace`.
4. Mode returns to Main.

### Forms/widgets (PDF only)
1. Toggle form-field highlight with `menu_forms` (page indicator → Navigate & View).
2. Tap a widget to edit it.
3. Use `menu_form_previous` / `menu_form_next` to navigate fields (visible only when highlight is enabled).

### Fill & sign (PDF only)
1. Start via `menu_fill_sign`.
2. Choose what to place (signature/initials/check/cross/date/name), capture if needed.
3. Place on the page (drag/scale/rotate for ink-style stamps; tap-to-place for text-style items).

### Fullscreen
1. Tap `menu_fullscreen` to hide system + app bars.
2. Press Back to exit fullscreen.

### Export/share/print
- Save back to the current file (PDF only, when allowed): `menu_save`
- Share a copy: `menu_share`
- Print a copy: `menu_print`
- Share a flattened copy (PDF): `menu_share_flattened`
- Advanced qpdf variants (only when enabled): `menu_share_linearized`, `menu_share_encrypted`, `menu_save_linearized`, `menu_save_encrypted`
- Sidecar docs (EPUB + read-only PDFs): export/import annotation bundles via `menu_export_annotations`, `menu_import_annotations`

### Add a blank page (PDF only)
1. Tap `menu_addpage` to append a blank page.

### EPUB reading settings (reflow)
1. Open EPUB.
2. `menu_reading_settings` opens reflow settings.
3. Apply typography/theme changes.
4. If ink exists, layout changes may be blocked; theme changes remain allowed.

### Note documents
1. Dashboard → **New Document** creates a blank PDF note doc and opens it.
2. Delete via `menu_delete_note`.

## Maintenance checks (keeping this doc complete)

Menu IDs (all menus):
- `rg -o -I 'android:id=\"@\\+id/[^\\\"]+\"' platform/android/res/menu/*.xml | sed -E 's/.*@\\+id\\///; s/\"$//' | sort -u`

Preference keys:
- `rg -o -I 'android:key=\"[^\"]+\"' platform/android/res/xml/preferences.xml | sed -E 's/.*\"(.*)\"/\\1/' | sort -u` (includes keys inside XML comments)
