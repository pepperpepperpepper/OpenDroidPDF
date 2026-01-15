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
7. **Progressive disclosure**: show the common 20% of actions 80% of the time; advanced tools live behind a “More…” step.
8. **Explain state**: if something is unavailable (read-only, missing permission, feature gated), the UI should say why and what to do next.

### Primary jobs-to-be-done (what users come here to do)

- Open a document (or a recent), then read (scroll/zoom) without thinking.
- Find something (search / table of contents / go to page).
- Mark up text quickly (highlight/underline/strikeout/etc.).
- Add handwritten ink or a text box; adjust style; move/resize; delete.
- Share/print/export a copy.
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
- Advanced/export-variant actions should be reachable, but not shown as a long flat list in the first overflow layer.

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
  - Overflow (“More”): everything else, but grouped (Navigate / View / Document / Advanced).

Notes:
- Avoid “icon soup”: keep reading mode to 2–3 primary icons plus overflow.
- Don’t let icons shift around as state changes; prefer disabling (with a brief “why”) over reflowing the toolbar.

**Contextual UI (appears when relevant, disappears when done):**
- Text selection → selection toolbar (markups + copy) rather than forcing the user to hunt in overflow.
- Annotation selection → a consistent “annotation actions” affordance (Properties / Delete / Duplicate / Align / Lock), without requiring any unrelated toggle like “show comments”.
- Forms/widgets → a lightweight “field navigation” affordance (Previous / Next) that appears only when a form field is active or when forms highlight is enabled.

**Mode indicator:**
- Whenever the menu/gestures change (Search/Selection/Edit/Draw/Add-text/Fullscreen), show a clear indicator of the current mode plus an obvious exit (e.g., a persistent “Done”/“Cancel” affordance).

### Proposed action hierarchy (document view)

This is the intended grouping for ease-of-use (not the current XML structure).

Primary actions (icons; reading mode):
- **Library** (go back to Library/dashboard) → `menu_open`
- **Search** → `menu_search`
- **Annotate** (tool palette entry point):
  - Draw / erase → `menu_draw` / `menu_erase`
  - Pen settings → `menu_pen_size`, `menu_ink_color`
  - Add text box / Paste text box → `menu_add_text_annot`, `menu_paste_text_annot`
  - Fill & sign… → `menu_fill_sign`
- **Export…** → `menu_share` (and `menu_print` inside the export sheet)

Primary actions (icons; contextual-only):
- **Undo / Redo** (only in modes where it’s meaningful; stable position) → `menu_undo`, `menu_redo`
- **Return** (only when available) → `menu_linkback`

Navigation (overflow):
- `menu_toc`, `menu_gotopage`
- (Search mode hit navigation uses `menu_previous`, `menu_next`)

View (overflow):
- `menu_fullscreen`
- `menu_reading_settings` (EPUB only)
- Comment visibility toggles: `menu_show_comments`, `menu_sticky_notes`
- Forms highlight (toggle): `menu_forms` (and keep field navigation contextual: `menu_form_previous`, `menu_form_next`)

Annotate (overflow + contextual):
- Text selection markups: `menu_highlight`, `menu_underline`, `menu_strikeout`, `menu_squiggly`, `menu_caret`, `menu_replace`, `menu_delete_text`, `menu_copytext`
- Annotations list + navigation: `menu_comments`, `menu_comment_previous`, `menu_comment_next`
- Fill & sign: `menu_fill_sign`

Document management (overflow):
- `menu_save` (save changes back, when allowed)
- `menu_addpage` (PDF only)
- `menu_delete_note` (note documents only)
- `menu_settings` (global Settings entry)

Export (overflow, but ideally consolidated under a single “Export…” entry):
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
  - **Text box** → `menu_add_text_annot` (and **Paste** → `menu_paste_text_annot` when available)
  - **Fill & sign…** → `menu_fill_sign` (PDF only)
- **Tool options (contextual)**
  - For Draw: color/size → `menu_ink_color`, `menu_pen_size`
  - For Erase: eraser thickness (currently a global default: `pref_eraser_thickness`)
  - For Text box: properties shortcut → `menu_text_style` (opens the same style UI used when editing)

Behavior rules:
- Picking a tool immediately enters that mode and closes/collapses the palette.
- While a tool mode is active, show a persistent exit affordance:
  - **Done** → `menu_accept`
  - **Cancel** → `menu_cancel` (with a discard confirmation if there is in-progress ink/text)
- Tool selection should be sticky (remember last tool) *per document*, not globally.
- Don’t overload “Trash” to mean both cancel and delete; **Cancel** exits the tool, **Delete** deletes a selection.

### Annotation actions surface (selected annotation) (proposed)

When an annotation is selected, the UI should expose the *same* actions every time, in one obvious place (prefer bottom sheet / contextual bar).

Always-visible actions:
- **Properties** → `menu_text_style` (for text) or the equivalent properties UI for other annotation types
- **Delete** → (should be first-class; not long-press-only on `menu_cancel`)
- **Duplicate** → `menu_duplicate_text` (for FreeText / sidecar note)

Secondary actions (“More…” within the annotation sheet):
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

### Navigate surface (proposed)

Navigation should be “one obvious place”, not scattered across dialogs and hidden gestures.

Entry points:
- From overflow group **Navigate** (see below), or
- From a dedicated Navigate affordance (future), if we add one.

Suggested contents:
- **Contents / outline…** → `menu_toc`
- **Go to page…** → `menu_gotopage`
- **Return** (only when available) → `menu_linkback`

Behavior rules:
- If the document has no outline, show an empty state with a short explanation instead of a blank list.
- After jumping, keep the user’s place discoverable (e.g., show current page number and allow “Back” via `menu_linkback` when the jump came from a link).

### View surface (proposed)

View controls are “how it looks”, not “what it does”.

Entry points:
- From overflow group **View** (see below).

Suggested contents:
- **Fullscreen** → `menu_fullscreen`
- **Show comments** (toggle) → `menu_show_comments`
- **Show note markers** (toggle; sidecar docs only) → `menu_sticky_notes`
- **Forms highlight** (toggle; PDF only) → `menu_forms`
- **Reading settings…** (EPUB only) → `menu_reading_settings`

Behavior rules:
- Toggles should explain scope: “this document only” vs “default for new documents”.
- If a feature is unavailable, keep it visible but disabled with a short “why”.

### Proposed “More” menu structure (grouped)

The overflow menu should be grouped and ordered so users can predict where something lives.

Suggested group order:

1. **Navigate**
   - Contents… → `menu_toc`
   - Go to page… → `menu_gotopage`
   - Return (only when available) → `menu_linkback`
2. **View**
   - Fullscreen → `menu_fullscreen`
   - Show comments (toggle) → `menu_show_comments`
   - Show note markers (toggle; sidecar docs only) → `menu_sticky_notes`
   - Reading settings… (EPUB only) → `menu_reading_settings`
   - Forms highlight (toggle; PDF only) → `menu_forms` (and keep field navigation contextual: `menu_form_previous`, `menu_form_next`)
3. **Annotate**
   - Annotations list… → `menu_comments`
   - Fill & sign… → `menu_fill_sign`
4. **Document**
   - Save changes (only when saving back is allowed) → `menu_save`
   - Add blank page (PDF only) → `menu_addpage`
   - Delete note… (note documents only) → `menu_delete_note`
5. **Settings**
   - Settings → `menu_settings`
6. **Advanced**
   - Advanced export variants (linearize/encrypt/flatten; bundles) and debug-only actions (debug builds only)

Rule of thumb:
- Items can be conditionally shown/hidden (format, permissions, feature-gates), but the group ordering should stay stable.

### Export model (proposed)

The current UI is “feature complete” but overloads users with multiple export variants. The spec should funnel users through one entry point and reveal advanced options only when requested.

Entry point:
- A single **Export…** action (toolbar icon or first overflow item) that opens an **Export sheet**.

Export sheet (suggested layout):
- **Share copy** (default) → `menu_share`
- **Print** → `menu_print`
- **Save a copy…** (standard PDF) → (future UI entry; today this is spread across `menu_save` (save changes + save-as flow) and the variant `menu_save_*` actions)
- **More options…**
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
4. Return to Library from the document.

Cancel/delete should be unambiguous:
- “Cancel” should mean “leave this mode without applying changes”.
- “Delete” should mean “remove the selected thing”, with a confirmation when it’s easy to hit accidentally.
- Avoid hiding destructive actions behind long-press-only affordances; long-press can be a shortcut, not the only path.

Saving prompts should be intent-based:
- Prompt to save when leaving the document (activity), not when merely leaving an editing sub-mode.

Mode rules (concrete):
- **Draw/erase**: provide explicit **Done** (`menu_accept`) and **Cancel** (`menu_cancel`) actions; Back should behave like Cancel (with a discard-confirmation if there are in-progress strokes).
- **Edit (annotations)**: Back exits Edit first; deletion is always a first-class action (not a hidden long-press).
- **Add text box**: Back cancels placement; after placement, Back exits the text editor (without losing text) before exiting the document.
- **Fullscreen**: Back exits fullscreen (no data-loss prompts).

### Annotation and editing UX (proposed)

The current UI has powerful capabilities but hides key actions behind state (modes) and long-press-only affordances. The spec should make annotation actions visible and predictable.

Selection model:
- **Tap selects**; tap-away deselects.
- **Second tap edits** (for editable text annotations) and should always work regardless of comment visibility.
- Selection should always present a consistent set of actions in one place (toolbar or bottom sheet): **Properties**, **Delete**, **Duplicate**, plus “More…” for advanced multi-select operations.

Quick actions:
- Do not gate quick actions behind `menu_show_comments`; comment visibility is a view preference, not an edit permission.
- Keep “Properties” discoverable: it should never require opening overflow.

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

- `menu_open`: currently labeled “Open” but actually shows the dashboard overlay; consider “Library”/“Dashboard”.
- `menu_share`: consider labeling the entry point “Export…” and using “Share” only inside the export sheet.
- `menu_linkback`: currently “Back before link clicked”; consider a shorter “Back”/“Return”, with a one-time hint describing what it does.
- `menu_edit`: in `edit_menu.xml` the title uses `@string/menu_draw`; consider using a dedicated “Edit” label (and only show “Draw” when the action truly enters drawing).
- `menu_sticky_notes`: “Sticky notes (sidecar)” is implementation-ish; consider a user-facing explanation like “Show note markers”.

### Proposed “happy path” flows (optimized)

These are the core flows the UI should make fast and obvious (even if implementation work remains).

- **Open and read**: Launch → Library → tap recent → read (no extra modes visible until requested).
- **Find**: Search icon → type → next/previous hits → Back exits search.
- **Highlight text**: long-press → highlight → tap anywhere to dismiss selection.
- **Draw**: tap Annotate → Draw → draw → Done; Back cancels draw mode first.
- **Add a text box**: tap Annotate → Text box → tap to place → type → Done; Back exits editor before leaving the document.
- **Export**: Export… → Share copy (default) / Print / More options.

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
| Edit | `edit_menu.xml` | Tap an annotation | Accept / tap-away / delete / mode change |
| Annot | `annot_menu.xml` | `menu_draw` or stylus-down (if enabled) | Accept commits or long-press Trash discards |
| AddingTextAnnot | `add_text_annot_menu.xml` | `menu_add_text_annot` | Place text annotation or Cancel |
| Hidden | `empty_menu.xml` | `menu_fullscreen` | Back exits fullscreen |
| Empty | `empty_menu.xml` | Dashboard visible | Open a document / hide dashboard |

### Back button behavior (important invariants)

- Fullscreen (Hidden): Back exits fullscreen.
- Dashboard shown over a document: Back hides the dashboard overlay.
- Search: Back exits search and clears results.
- Text selection: Back cancels selection.
- Draw/erase (Annot): Back is intentionally consumed; exit via `menu_accept` or long-press `menu_cancel`.
  - Usability note: this can feel like Back is “broken” unless the user has learned the accept/cancel affordances.
- Edit and add-text placement: Back does *not* explicitly cancel these modes; if there are no unsaved changes, Back may exit the document activity.
  - Usability note: this makes Back behave differently across modes; consider exiting the mode first, then allowing activity exit.
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
- `menu_open` is labeled “Open” but it shows the dashboard overlay (not a file picker); consider a user-facing label like “Library”/“Dashboard”, with “Open Document…” inside the dashboard.
- Export is currently presented as multiple flat overflow entries when enabled (`menu_share_*`, `menu_save_*`, `menu_print`); consider consolidating behind a single “Export…” entry.
- The Edit/Annot “cancel” button (`menu_cancel`) uses a trash icon and hides the destructive action behind long-press; consider separating “Cancel” (X) from “Delete/Discard” (trash) for clarity.

#### A) File/document actions (`menu_group_document_actions`)

Open/navigation:
- **Save** (`menu_save`): opens “Save / Save as”; visible only when saving back to the current URI is allowed (PDF + persistable write access).
- **Open** (`menu_open`): shows the dashboard (this is the “open another doc” entry point).
- **Go to page** (`menu_gotopage`): page number dialog, then jump.
- **Table of contents** (`menu_toc`): outline list (PDF outline; EPUB toc fallback).
- **Search** (`menu_search`): enters Search mode.
- **Fullscreen** (`menu_fullscreen`): hides system + app bars; disables link taps while active.
- **Settings** (`menu_settings`): opens global settings.

Comments:
- **Comments list** (`menu_comments`): opens a dialog listing comment-style annotations; selecting an entry jumps to and centers it.
- **Show comments** (`menu_show_comments`, checkable): toggles rendering of comment-style annotations.
- **Sticky notes** (`menu_sticky_notes`, checkable): for sidecar-backed docs, toggles marker-only note rendering.
- **Previous/Next comment** (`menu_comment_previous`, `menu_comment_next`): only shown when a comment is selected; jumps between comment-style annotations.

Export / interop:
- **Share** (`menu_share`): exports a copy, then opens the Android share sheet.
- **Share (linearized)** (`menu_share_linearized`): shares a qpdf-linearized PDF copy (qpdf ops only).
- **Share (encrypted)** (`menu_share_encrypted`): prompts for passwords, then shares an encrypted copy (qpdf ops only).
- **Share (flattened)** (`menu_share_flattened`): shares a flattened copy (pdfbox when available; otherwise raster fallback).
- **Save (linearized)** (`menu_save_linearized`): saves a qpdf-linearized copy to a user-selected URI.
- **Save (encrypted)** (`menu_save_encrypted`): prompts for passwords, then saves an encrypted copy to a user-selected URI.
- **Print** (`menu_print`): exports a copy then invokes Android printing.
- **Export annotations** (`menu_export_annotations`): exports/shares a sidecar annotation bundle (EPUB and read-only PDFs).
- **Import annotations** (`menu_import_annotations`): imports a sidecar annotation bundle (EPUB and read-only PDFs).

PDF-only mutation:
- **Add blank page** (`menu_addpage`): inserts a blank page at the end of a PDF.

EPUB-only:
- **Reading settings** (`menu_reading_settings`): per-document reflow settings (font/margins/line spacing/theme).

Special note documents:
- **Delete note** (`menu_delete_note`): deletes the “note document” created by **New Document** and returns to dashboard.

Link navigation:
- **Link back** (`menu_linkback`): returns to the pre-link viewport; visible only when a link-back target exists.

#### B) Editor tools (`menu_group_editor_tools`)

Core tools:
- **Undo / Redo** (`menu_undo`, `menu_redo`): primarily affects ink strokes (and other operations routed through the page view’s undo stack).
- **Draw** (`menu_draw`): enters drawing mode.
  - Shortcut: double-tap/long-press on the draw icon opens the pen settings dialog.
- **Add text annotation** (`menu_add_text_annot`): enters “tap to add” text annotation placement mode.
- **Paste text annotation** (`menu_paste_text_annot`): pastes a copied text annotation (enabled only when the internal clipboard has a payload).

PDF-only editor tools:
- **Forms** (`menu_forms`, checkable): toggles highlighting of widget bounds.
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
- **Trash icon (Cancel)** (`menu_cancel`):
  - Tap shows a hint (“long press to delete”).
  - Long-press deletes the selected annotation and exits edit mode.
- **Undo / Redo** (`menu_undo`, `menu_redo`)
- **Save** (`menu_save`)
- **Edit** (`menu_edit`): edits the selected annotation (ink edits transition into drawing).
- **Move** (`menu_move`): shows a hint; actual movement is done by dragging the selection box.
- **Resize** (`menu_resize`, checkable): toggles resize handles (text annotations).
- **Text style / Properties** (`menu_text_style`): text style dialog for FreeText / sidecar notes.
- **Duplicate** (`menu_duplicate_text`): duplicates the selected text annotation (FreeText / sidecar note).
- **Copy / Paste text annotation** (`menu_copy_text_annot`, `menu_paste_text_annot`)
- **Checkmark (Accept)** (`menu_accept`): deselects and returns to viewing.

Comment navigation:
- **Previous / Next comment** (`menu_comment_previous`, `menu_comment_next`): when shown, jumps between comment-style annotations while in an edit context.

### 7) Annot toolbar (draw/erase mode)

Where:
- Menu: `platform/android/res/menu/annot_menu.xml`

Actions:
- **Undo / Redo** (`menu_undo`, `menu_redo`)
- **Save** (`menu_save`): same Save / Save as flow as main toolbar (writable PDFs only).
- **Trash icon (Cancel)** (`menu_cancel`):
  - Tap shows a hint (“long press to delete”).
  - Long-press discards in-progress ink and exits back to viewing.
- **Erase / Draw** (`menu_erase`, `menu_draw`): toggles eraser vs pen.
  - Switching to eraser forces a commit of pending ink first so strokes become erasable.
- **Ink color / Pen size** (`menu_ink_color`, `menu_pen_size`): pen settings dialog (Draw only; hidden in Erase).
- **Checkmark (Accept)** (`menu_accept`): commits ink and exits.

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
- Draw icon shortcut (double-tap/long-press on `menu_draw`)

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
- Toggle comment rendering with `menu_show_comments`.
- For sidecar docs (EPUB + read-only PDFs), toggle marker-only note rendering with `menu_sticky_notes`.
- Open the comments list with `menu_comments`, then filter and select an entry to jump to it.
- When available, use `menu_comment_previous` / `menu_comment_next` to move between comment-style annotations.

### Draw / erase ink
1. Enter draw via `menu_draw` (or stylus-down if enabled).
2. Draw strokes.
3. Optional: pen settings via `menu_ink_color` / `menu_pen_size` (or draw-icon shortcut).
4. Exit:
   - `menu_accept` commits + exits, or
   - long-press `menu_cancel` discards in-progress ink + exits.
5. To erase: in Annot mode tap `menu_erase`, erase strokes, then `menu_accept`.

### Add and edit a text annotation
1. `menu_add_text_annot` enters placement mode.
2. Tap to place the text box; editor opens (inline or dialog).
3. To edit later: select annotation → `menu_edit`, or tap the selected annotation again.
4. Style/lock via `menu_text_style` or quick-actions **Properties**.
5. Move/resize via drag gestures (resize handles via `menu_resize`).
6. Delete via long-press `menu_cancel` (Edit toolbar) or quick-actions **Delete**.
7. Note: the quick-actions popup is only shown when comments are visible (`menu_show_comments` enabled).

### Mark up selected text (highlight/underline/etc.)
1. Long-press to select text.
2. Adjust handles.
3. Apply `menu_highlight` / `menu_underline` / `menu_strikeout` / `menu_delete_text` / `menu_squiggly` / `menu_caret` / `menu_replace`.
4. Mode returns to Main.

### Forms/widgets (PDF only)
1. Toggle form-field highlight with `menu_forms`.
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
