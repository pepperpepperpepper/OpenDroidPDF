# UI Taxonomy (Android)

This document is a developer-facing map of OpenDroidPDF’s current Android UI: what screens exist, what actions are available, where settings live, and how the core user flows work.

Scope: `platform/android` (OpenDroidPDFActivity + MuPDF reader/editor UI). Labels may differ slightly by locale; menu item IDs are included where useful.

## High-level screens

### Dashboard (entry screen)
The dashboard is the first screen shown on launch and is also reachable from the document toolbar.

Primary affordances:
- **Open Document**: launches the Android document picker (SAF on API 19+, legacy file chooser on old devices).
- **New Document**: prompts for a filename and creates a new blank PDF “note document” in the app’s notes directory.
- **Settings**: opens the global settings screen.
- **Recent files list**: a scrollable list of recently opened documents (up to the configured limit), each with a title and thumbnail; tapping an entry opens it.

Implementation anchors:
- UI: `platform/android/src/org/opendroidpdf/app/DashboardFragment.java`
- Navigation host: `platform/android/src/org/opendroidpdf/app/hosts/DashboardHostAdapter.java`

### Document viewer/editor (single-activity)
The document view hosts the reader/editor for PDFs and EPUBs.

Primary components:
- **Top app bar** (`Toolbar`) whose menu changes by mode (viewing/search/select/edit/draw).
- **Document content** (`MuPDFReaderView` + per-page `MuPDFPageView`) handling scroll/zoom, link taps, annotation overlays, widgets/forms, and sidecar annotations for formats that can’t embed edits.
- **Page indicator** (`R.id.page_indicator`): bottom overlay showing “current / total”; tapping opens the **Navigate & View** sheet (navigation + view toggles + contextual document actions).

Implementation anchors:
- Activity: `platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java`
- View factory + toolbar mode wiring: `platform/android/src/org/opendroidpdf/DocViewFactory.java`
- Reader view gesture routing: `platform/android/src/org/opendroidpdf/app/reader/gesture/*`

### Settings screen (global preferences)
This is a legacy `PreferenceFragment` screen for app-wide defaults (autosave, viewer behavior, annotation defaults).

Implementation anchors:
- Activity: `platform/android/src/org/opendroidpdf/SettingsActivity.java`
- Fragment: `platform/android/src/org/opendroidpdf/SettingsFragment.java`
- XML: `platform/android/res/xml/preferences.xml`

## Top app bar modes (what menu is shown)

The top toolbar is mode-driven. There are two relevant “mode” concepts:
- **ReaderMode** (touch interaction mode): viewing/searching/selecting/drawing/erasing/adding text.
- **ActionBarMode** (toolbar menu): main/search/selection/edit/annot/adding-text/hidden/empty.

Mode mapping is driven by `DocViewFactory` and the `DrawingService`-backed `AnnotationModeStore`.

| ActionBar mode | Menu XML | Typical entry | Typical exit |
| --- | --- | --- | --- |
| Main | `platform/android/res/menu/main_menu.xml` | Open a document; tap away from selection; finish editing | Enter another mode (search/select/edit/draw/add-text) |
| Search | `platform/android/res/menu/search_menu.xml` | `menu_search` | Back button or closing the SearchView |
| Selection | `platform/android/res/menu/selection_menu.xml` | Long-press to select text, or **Annotate → Mark up text** (then tap text) | Perform an action; Back cancels selection |
| Edit | `platform/android/res/menu/edit_menu.xml` | Tap an annotation (Hit.Annotation/TextAnnotation/InkAnnotation) | Done/Cancel/Back to exit selection, or Delete |
| Annot | `platform/android/res/menu/annot_menu.xml` | Tap Draw; stylus-down (if enabled); edit an ink annotation | Done commits; Cancel/Back exits (discard prompts when there are in-progress strokes) |
| AddingTextAnnot | `platform/android/res/menu/add_text_annot_menu.xml` | Tap “Add text” | Place a text annotation, or Cancel |
| Hidden | `platform/android/res/menu/empty_menu.xml` | Fullscreen | Back button exits fullscreen |
| Empty | `platform/android/res/menu/empty_menu.xml` | Dashboard is shown | Open a document / hide dashboard |

## Actions taxonomy (where actions live)

### Dashboard actions

Where:
- Dashboard cards in `DashboardFragment` (Open / New / Settings)
- Recent file cards (Open recent)

Actions:
- **Open Document**: opens SAF (or legacy chooser); the selection is reopened in OpenDroidPDFActivity.
- **New Document**: shows a filename prompt and creates a `.pdf` in the notes dir.
- **Settings**: opens `SettingsActivity`.
- **Open recent**: opens the chosen recent URI and closes the previous activity instance.

### Document viewer actions (Main toolbar)

The main toolbar (`platform/android/res/menu/main_menu.xml`) has two groups:
- `menu_group_document_actions` (file/navigation/visibility/export)
- `menu_group_editor_tools` (annotation + editing shortcuts)

#### File/document actions (main menu)

Primary homes:
- **Top app bar**: Library + core editing shortcuts (mode-dependent).
- **Page indicator → Navigate & View sheet**: navigation, view toggles, and contextual document actions.

Common actions:
- **Save changes** (`menu_save`): saves back to the current URI (writable PDFs only); accessed via Navigate & View → Document.
- **Library** (`menu_open`): shows the dashboard/library (used to switch/open documents).
- **Go to page** (`menu_gotopage`): Navigate & View → Navigate.
- **Contents** (`menu_toc`): Navigate & View → Navigate (PDF outline, or EPUB toc fallback).
- **Search** (`menu_search`): enters Search mode with a SearchView.
- **Annotate** (`menu_annotate`): opens an **Annotate** sheet (bottom sheet) with:
  - Draw, Erase, Mark up text
  - Add text, Paste
  - Fill &amp; Sign (PDF only)
  - Annotations list
- **Fullscreen** (`menu_fullscreen`): Navigate & View → View.
- **Reading settings** (`menu_reading_settings`): Navigate & View → View (EPUB only).
- **Settings** (`menu_settings`): global Settings (reachable via Library/dashboard).

Comments/navigation actions:
- **Annotations list** (`menu_comments`): Navigate & View → View (list dialog for jumping between “comment-style” annotations).
- **Show annotations** (`menu_show_comments`, checkable): Navigate & View → View (toggles on-page rendering of comment-style annotations, embedded + sidecar).
- **Show note markers** (`menu_sticky_notes`, checkable): Navigate & View → View (sidecar docs only; marker-only mode for sidecar notes).
- **Previous / Next comment** (`menu_comment_previous`, `menu_comment_next`): icon buttons (shown only when an editable annotation is selected) that jump to the previous/next comment-style annotation.

Export/interop actions:
- **Export…** (`menu_share`): opens an **Export sheet** (bottom sheet) that provides:
  - **Share a copy**: exports a copy and opens the Android share sheet.
  - **Save a copy…**: exports a PDF copy to a user-selected location (SAF create-document).
  - **Print** (`menu_print`): exports a copy and invokes Android printing.
  - **Advanced options…** (contextual): reveals additional export variants:
    - **Export (linearized)** (`menu_share_linearized`): shares a qpdf-linearized copy (only when qpdf ops are enabled).
    - **Export (encrypted)** (`menu_share_encrypted`): prompts for passwords and shares an encrypted copy (qpdf ops).
    - **Export (flattened)** (`menu_share_flattened`): shares a flattened PDF copy (pdfbox when available; otherwise raster fallback).
    - **Save (linearized)** (`menu_save_linearized`): saves a linearized copy to a user-selected URI (qpdf ops).
    - **Save (encrypted)** (`menu_save_encrypted`): prompts for passwords, then saves an encrypted copy to a user-selected URI (qpdf ops).
    - **Export annotations** (`menu_export_annotations`): shares a sidecar annotation bundle (EPUB + read-only PDFs).
    - **Import annotations** (`menu_import_annotations`): imports a sidecar annotation bundle (EPUB + read-only PDFs).

PDF-only document mutation:
- **Add blank page** (`menu_addpage`): inserts a blank page at the end of a PDF (Navigate & View → Document).
- **Organize pages…**: Navigate & View → Document → Organize pages… opens a sheet for structural PDF edits (save-a-copy model): Reorder pages, Insert blank page, Insert pages from PDF, Create new PDF from pages, Merge another PDF, Remove pages, Rotate pages.

EPUB-only reading:
- **Reading settings** (`menu_reading_settings`): opens per-document reflow settings (font size/margins/line spacing/theme) via Navigate & View.

Special-cased notes:
- **Delete note** (`menu_delete_note`): deletes the current “note document” created by New Document and returns to dashboard (Navigate & View → Document).

Link navigation:
- **Link back** (`menu_linkback`): returns to the pre-link viewport after following an internal link (only visible when there is a remembered link-back target).

Key controllers:
- Action handling: `platform/android/src/org/opendroidpdf/app/document/DocumentToolbarController.java`
- Export flows: `platform/android/src/org/opendroidpdf/app/document/ExportController.java`
- Visibility/enablement: `platform/android/src/org/opendroidpdf/app/toolbar/ToolbarStateController.java`

#### Editor tools (main menu)

Location: main toolbar icon row + overflow (state-driven).

Core actions:
- **Undo** (`menu_undo`) / **Redo** (`menu_redo`): primarily affects ink strokes (and other operations routed through the page view’s undo stack).
- **Draw** (`menu_draw`): enters drawing mode.
  - Pen settings are reachable via visible controls while in Draw (ink color / pen size).
- **Add text annotation** (`menu_add_text_annot`): enters “tap to add” text annotation mode.
- **Paste text annotation** (`menu_paste_text_annot`): pastes a copied text annotation (enabled only when clipboard has a payload).

PDF-only editor tools:
- **Forms** (`menu_forms`, checkable): toggles highlighting of AcroForm widget bounds.
- **Previous / Next form field** (`menu_form_previous`, `menu_form_next`): navigates form fields in reading order (visible only when Forms highlight is enabled).
- **Fill & sign** (`menu_fill_sign`): starts a “place signature/initials/stamps” workflow.

### Debug-only actions (overflow menu, debug builds)

In debug builds (`BuildConfig.DEBUG=true`), the app overlays a debug menu (`platform/android/res/menu/debug_menu.xml`) onto every toolbar mode via `ToolbarStateController`. These items show up in the overflow menu.

Actions:
- **Snap-to-fit width** (`menu_debug_snap_fit`): adjusts zoom to “snap” to fit-width behavior.
- **Show text widget dialog** (`menu_debug_show_text_widget`): shows a test text-widget editor dialog.
- **Show choice widget dialog** (`menu_debug_show_choice_widget`): shows a test choice-widget dialog.
- **Export test** (`menu_debug_export_test`): exercises the export pipeline for debugging.
- **Alert test** (`menu_debug_alert_test`): shows a test alert.
- **Render self-test** (`menu_debug_render_self_test`): exercises render/self-test hooks.
- **qpdf smoke test** (`menu_debug_qpdf_smoke`): runs qpdf ops smoke (only visible when a PDF is open).
- **pdfbox flatten** (`menu_debug_pdfbox_flatten`): runs pdfbox flatten (only visible/enabled when pdfboxops is available and a PDF is open).

Implementation anchor: `platform/android/src/org/opendroidpdf/app/debug/DebugActionsController.java`

### Annotation mode actions (Draw/Eraser toolbar)

Where: `platform/android/res/menu/annot_menu.xml` (ActionBarMode.Annot), shown when drawing or erasing is active.

Actions:
- **Undo / Redo**: undo/redo ink strokes.
- **Save changes** (`menu_save`): saves back to the current URI (writable PDFs only; overflow item).
- **Cancel (X)** (`menu_cancel`): exits annotate mode.
  - If there are in-progress strokes, prompts to discard before exiting.
- **Erase** (`menu_erase`) and **Draw** (`menu_draw`): toggles between drawing and erasing modes.
  - Switching to eraser forces a commit of any pending ink first, so strokes become erasable.
- **Ink color** (`menu_ink_color`) / **Pen size** (`menu_pen_size`): opens the unified pen settings dialog (visible only while in Draw, not Erase).
- **Done** (`menu_accept`): commits pending ink and exits back to viewing.

### Text selection actions (Selection toolbar)

Where: `platform/android/res/menu/selection_menu.xml` (ActionBarMode.Selection), shown when text selection mode is active.

How to enter:
- Long-press on page content to start text selection.
- **Annotate → Mark up text** to enter selection mode without long-press (then tap text to select).
- Drag the selection handles (left/right markers) to expand/adjust selection.

Actions:
- **Copy** (`menu_copytext`): copies selected text to clipboard.
- **Highlight** (`menu_highlight`)
- **Underline** (`menu_underline`)
- **Strikeout** (`menu_strikeout`) and **Delete text** (`menu_delete_text`): both create a strikeout annotation (this is “commenting”, not true content deletion).
- **Squiggly** (`menu_squiggly`)
- **Caret** (`menu_caret`)
- **Replace** (`menu_replace`): a proofreading workflow that strikes out the selection and adds a caret annotation after it (an “insert here” marker).
- **Done** (`menu_accept`): exits selection mode and returns to viewing.

After applying a markup action, the selection is cleared but selection mode remains active so you can tap to select another span and apply another markup. Use **Done** (or Back) to exit selection mode.

### Annotation edit actions (Edit toolbar)

Where: `platform/android/res/menu/edit_menu.xml` (ActionBarMode.Edit), shown after tapping an existing annotation.

Actions:
- **Cancel (X)** (`menu_cancel`): exits selection without deleting.
- **Delete** (`menu_delete_annotation`): deletes the selected annotation (explicit + confirmable).
- **Undo / Redo**: undo/redo strokes (and edit operations supported by the underlying page stack).
- **Save changes** (`menu_save`): saves back to the current URI (writable PDFs only; available from edit state).
- **Edit** (`menu_edit`): edits the selected annotation.
  - For ink: enters drawing mode so strokes can be adjusted.
- **Move** (`menu_move`): shows a hint; actual movement is done by drag gestures on the selected box.
- **Resize** (`menu_resize`, checkable): toggles resize handles for text annotations.
- **Text style / Properties** (`menu_text_style`): opens the text style dialog for FreeText / sidecar notes.
- **Duplicate** (`menu_duplicate_text`): duplicates the selected text annotation (FreeText / sidecar note).
- **Copy / Paste text annotation** (`menu_copy_text_annot`, `menu_paste_text_annot`): copies/pastes a text annotation via an internal clipboard.
- **Done** (`menu_accept`): deselects the annotation and exits back to viewing.

Comment navigation entries:
- **Previous / Next comment** (`menu_comment_previous`, `menu_comment_next`): when enabled, jumps between comment-style annotations while in an edit context.

### Search actions (Search toolbar)

Where: `platform/android/res/menu/search_menu.xml` (ActionBarMode.Search).

Actions:
- **Search box** (`menu_search_box`): type a query; submitting triggers search.
- **Previous / Next** (`menu_previous`, `menu_next`): navigates between search hits.
- Closing the SearchView or pressing Back exits search, clears results, and returns to viewing.

### “Add text” placement mode (AddingTextAnnot toolbar)

Where: `platform/android/res/menu/add_text_annot_menu.xml` (ActionBarMode.AddingTextAnnot).

Actions:
- **Cancel** (`menu_cancel`): exits add-text placement mode (no annotation placed).

## Contextual / non-toolbar UI elements

### Snackbars (banners) that warn + offer quick actions

These are state-driven snackbars (Material `Snackbar`) anchored to the activity root.

Banners:
- **PDF read-only**: shown when a PDF is open but the app cannot save back to the current URI.
  - Action: **Enable saving** → reopens the document picker to grant/refresh permissions.
- **Imported Word**: shown when a `.doc/.docx` was successfully imported to a temporary PDF.
  - Action: **Learn more** → shows an explainer dialog about the import limitations.
- **EPUB reflow layout mismatch**: shown when the doc has sidecar annotations in a different reflow layout profile.
  - Action: **Switch to annotated layout** → restores the layout snapshot under which those annotations were created, so they become visible again.
  - Also used to block export/share/print when *all* annotations are currently hidden due to a layout mismatch.
- **PDF XFA unsupported**: shown when XFA forms are detected in a PDF.
  - Action: **Learn more** → shows an action list (convert via XFA Pack if installed, install XFA Pack, open in another app, share).

Implementation anchors:
- Snackbar host: `platform/android/src/org/opendroidpdf/app/ui/UiStateDelegate.java`
- Banner decision points: `platform/android/src/org/opendroidpdf/app/hosts/DocumentSetupHostAdapter.java`, `platform/android/src/org/opendroidpdf/app/hosts/DocumentToolbarHostAdapter.java`

### Text annotation quick actions popup (contextual mini-toolbar)

Where: a small popup shown near the currently selected text annotation (FreeText / sidecar note). It is not gated by the “Show annotations” rendering toggle.

Primary buttons (icons are small square buttons):
- **Properties**: opens the text style dialog for the selection.
- **Duplicate**: duplicates the selected text annotation.
- **Multi-select: add**: adds the current selection into the per-page multi-select set.
- **Multi-select: align/distribute**: opens a picker for align/distribute operations (requires 2+ selected; includes a “clear selection” entry).
- **Multi-select: group**: toggles “grouped move” so dragging one moves the whole set.
- **Lock**: toggles lock position/size on the selected text annotation.
- **Fit to text** (FreeText only): resizes the box to better fit its content.
- **Delete**: deletes the selected annotation immediately.

Key controllers:
- Popup UI: `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationQuickActionsController.java`
- Multi-select geometry ops: `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationMultiSelectController.java`

### Direct manipulation gestures for text annotations (move/resize)

When a text annotation is selected:
- Drag inside the selection box (or near it, with a small slop) to **move**.
- Drag the **move handle** (top-center) to move (when shown).
- If “Resize” handles are enabled, drag corner handles to **resize**.
- If “Lock position/size” is enabled, move/resize is blocked and the UI will hint that it’s locked.
- If multi-select group is enabled, moving one will translate the rest of the selection set on the same page.

Implementation anchor: `platform/android/src/org/opendroidpdf/app/reader/gesture/TextAnnotationManipulationGestureHandler.java`

### Text annotation input UI (inline editor + dialog fallback)

When adding/editing a text annotation, the app tries to use an in-place editor; if not supported it falls back to a dialog.

Editing entry points:
- Tap **Edit** in the edit toolbar, or
- Tap a **selected** text annotation again (short-tap or “double-tap” within the system tap timeout) to open the editor directly.

Dialog affordances:
- A multi-line text input.
- Optional “rich text warning” when editing an Acrobat-style rich FreeText (editing may drop formatting).
- **< / >** buttons to jump to previous/next comment while editing (best-effort comment navigation).

Implementation anchor: `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationController.java`

### Comments list dialog

Accessible via Navigate & View → **Annotations** (`menu_comments`).

Affordances:
- Search field (filters list entries).
- Type filter (All / Notes / Text boxes / Markups / Ink).
- List of entries; tapping jumps to page + centers view + attempts to select the comment.

Implementation anchor: `platform/android/src/org/opendroidpdf/app/comments/CommentsListController.java`

### Pen settings dialog (size + color)

Accessible from:
- Annotation toolbar: “Ink color” / “Pen size”

Affordances:
- Thickness slider with stroke preview.
- Color palette grid.

Implementation anchor: `platform/android/src/org/opendroidpdf/app/annotation/PenSettingsController.java`

### Text style dialog (text box properties)

Accessible from:
- Edit toolbar: “Text style”
- Quick actions popup: “Properties”

Affordances (selected text box / note):
- Font size, family, and style (bold/italic/underline/strike).
- Alignment.
- Line spacing and first-line indent.
- Text color palette.
- Background color palette + opacity.
- Fit-to-text.
- Rotation (0/90/180/270).
- Border: color, style (solid/dashed), width, radius.
- Locking: lock position/size; lock contents.

Implementation anchor: `platform/android/src/org/opendroidpdf/app/annotation/TextAnnotationStyleController.java`

### Reflow “Reading settings” dialog (EPUB only)

Accessible via `menu_reading_settings` when an EPUB is open.

Affordances (per-document):
- Font size
- Margins
- Line spacing
- Theme (light/dark/sepia)

Special behavior:
- If ink exists on the document, layout-affecting changes may be blocked to avoid drifting geometry-anchored strokes; theme changes remain allowed.

Implementation anchor: `platform/android/src/org/opendroidpdf/app/reflow/ReflowSettingsController.java`

### Forms/widgets UI (PDF only)

Entry points:
- Enable **Forms** highlight (`menu_forms`) to show field bounds.
- Tap a widget field to edit it.

Common widget UI:
- Inline text editor over the field bounds where possible; dialog fallback otherwise.
- Choice widgets show a picker dialog.
- Signature fields can trigger a signing/checking flow (key file picker + password prompt).

Implementation anchors:
- Widget UI integration: `platform/android/src/org/opendroidpdf/MuPDFPageViewWidgets.java`
- Signature flow dialogs: `platform/android/src/org/opendroidpdf/app/signature/SignatureFlowController.java`

### Fill & sign (PDF only)

Entry point: `menu_fill_sign`.

Flow:
- Choose an action: signature / initials / checkmark / cross / date / name.
- If signature/initials (or name) has not been captured yet, you’ll be prompted to capture/enter it once.
- Placement is one-shot:
  - For signature/initials/check/cross: touch down to preview, drag/rotate/scale with two fingers, lift to commit (creates an ink annotation).
  - For date/name: tap to place a text stamp (creates a FreeText annotation).

Implementation anchor: `platform/android/src/org/opendroidpdf/app/fillsign/FillSignController.java`

### Password and permission dialogs

Shown as needed:
- **Password prompt**: when opening encrypted PDFs (or other password-protected docs).
- **Permission prompt**: when the app lacks read access to a selected URI; offers a “Grant access” flow.
- **Word import unavailable**: when opening `.doc/.docx` and conversion is unavailable; offers “open in other app” and optionally “install Office Pack”.
- **Encrypted/DRM EPUB not supported**: when opening DRM/encrypted EPUBs, shows a “cannot open” dialog.

Implementation anchors:
- Password: `platform/android/src/org/opendroidpdf/app/dialog/PasswordDialogHelper.java`
- Permission/Word dialogs: `platform/android/src/org/opendroidpdf/app/document/DocumentSetupController.java`

## Settings taxonomy (where settings live)

### Global settings screen (SettingsActivity)

Navigation: **Dashboard (Library) → Settings**

Source of truth: `platform/android/res/xml/preferences.xml`

Autosave settings:
- **Save on destroy** (`pref_save_on_destroy`): auto-save when activity is destroyed.
- **Save on stop** (`pref_save_on_stop`): auto-save when activity stops.

UI settings:
- **Use stylus** (`pref_use_stylus`): enables stylus-first behavior (stylus-down enters drawing; drawing uses stylus pointer only; scale gestures are briefly suppressed after stylus input).
- **Smart text selection** (`pref_smart_text_selection`): adjusts selection behavior to reduce accidental cross-column selection.
- **Number of recent files** (`pref_number_recent_files`): max entries shown on the dashboard.

Editor settings:
- **Editor settings** (`pref_editor_settings`): opens a placeholder screen containing a single disabled placeholder item.

Display settings:
- **Keep screen on** (`keep_screen_on`): prevents sleep while viewing documents.
- **Fit width** (`pref_fit_width`): default zoom behavior to fit width.
- **Page swipe direction** (`pref_page_paging_axis`): horizontal vs vertical paging axis.

Annotation settings:
- **Ink thickness** (`pref_ink_thickness`): default pen thickness (also adjustable via pen dialog).
- **Eraser thickness** (`pref_eraser_thickness`): default eraser thickness.
- **Ink color** (`pref_ink_color`): default pen color (also adjustable via pen dialog).
- **Highlight color** (`pref_highlight_color`)
- **Underline color** (`pref_underline_color`)
- **Strikeout color** (`pref_strikeout_color`)
- **Text annotation icon color** (`pref_textannoticon_color`): affects marker/icon styling for text annotations/notes.

About (`pref_about_screen`):
- **Version** (`pref_about_version`, read-only)
- **License** (`pref_about_license`): shows a short license summary and offers “view full”.
- **Source** (`pref_about_source`): opens the source URL.
- **Issues** (`pref_about_issues`): opens the issue tracker URL.

Experimental:
- **Experimental mode** (`pref_experimental_mode`): currently present as a toggle; behavior is feature-dependent.

### In-document toggles and per-document settings (not in Settings screen)

These settings live in the document UI itself:
- **Show comments** (`menu_show_comments`): page-level rendering toggle for comment-style annotations.
- **Sticky notes** (`menu_sticky_notes`): sidecar note rendering mode (marker-only vs full boxes) for sidecar docs.
- **Forms highlight** (`menu_forms`): PDF widget highlight toggle; also gates form field navigation icons.
- **Reading settings** (`menu_reading_settings`): EPUB-only per-document reflow preferences.
- **Pen settings dialog**: quick access to pen thickness/color while drawing.
- **Text style dialog**: per-annotation properties + locks for text boxes/notes.
- **Fill & sign captured values**: signature/initials strokes and name are stored for reuse by the fill & sign feature.

## Detailed user flows

### 1) Launch → open a document
1. Launch OpenDroidPDF → dashboard appears.
2. Tap **Open Document** → pick a file.
3. If the document is password-protected → enter password.
4. Document viewer opens in **Main** mode.

### 2) Open a recent document
1. Launch → dashboard lists recent entries.
2. Tap a recent entry → it opens in a new activity instance and closes the old one.

### 3) Navigation basics (read mode)
- Swipe/pan to move around the page; pinch to zoom.
- Single-tap behavior depends on tap location and state:
  - In normal viewing/search: tapping near the **top/left margin** moves backward; tapping near the **bottom/right margin** moves forward.
  - Tapping the main document area: clears/deselects contextual state and refreshes UI (e.g., exits Edit back to Main).
  - When a text annotation is selected, margin taps behave like “main document area” (deselect) to avoid accidental page navigation.
  - In **Add text annotation** mode, tapping places the text box at that point and exits add-text mode.
- Tap a link:
  - Internal links navigate immediately and record a **Link back** target.
  - Tap **Link back** to return to the pre-link viewport.
- Use the page indicator for direct navigation and view toggles:
  - Tap the **page indicator** → **Navigate & View** sheet → **Go to page** / **Contents** / view toggles.

### 4) Search within the document
1. Document menu → **Search**.
2. Type a query in the search box and submit.
3. Use **Previous/Next** to jump hits.
4. Back (or close search) to exit search and clear highlights.

### 5) Add ink (pen) annotations
Entry points:
- Tap **Draw** in the main toolbar, or
- Enable **Use stylus**, then touch down with the stylus while viewing (auto-enters drawing).

Flow:
1. Enter drawing mode → annotation toolbar appears.
2. Draw strokes on the page.
3. Optional: adjust **Pen size** / **Ink color** (visible while in Draw).
4. Tap **Done** to commit strokes and exit, or tap **Cancel** / press Back to discard (with confirmation if there are in-progress strokes).

### 6) Erase ink
1. While in annotation mode, tap **Erase**.
2. Drag over strokes to erase.
3. Tap **Done** to exit.

### 7) Add a text box / note
1. Tap **Add text annotation**.
2. The UI enters “tap to add”.
3. Tap on the page to place a default-sized box.
4. Enter text via inline editor (if supported) or the dialog fallback.
5. Finish editing → returns to viewing; the annotation can be reselected for styling/move/resize.

### 8) Edit an existing annotation (text boxes, notes, ink)
1. Tap an annotation on the page → Edit toolbar appears.
2. Depending on the annotation:
   - Tap **Edit** to edit contents (ink edits transition into draw mode).
   - Use drag gestures to move; enable **Resize** handles to resize.
   - Use **Text style** / quick-actions **Properties** for text box appearance and locks.
3. Tap **Done** to exit edit state.
4. To delete: tap **Delete** (explicit) and confirm.

### 9) Multi-select text annotations (align/distribute + grouped move)
1. Select a text annotation (FreeText or sidecar note).
2. In the quick-actions popup:
   - Tap **Multi-add** to add this selection to the set.
   - Select another text annotation on the *same page* and tap **Multi-add** again.
3. Tap **Align/Distribute** to choose an operation (requires 2+ items).
4. Optionally toggle **Group** so dragging one moves the whole set.
5. Change page clears the selection set (multi-select is per-page).

### 10) Mark up document text (highlight/underline/etc.)
1. Enter selection mode:
   - Long-press to select text, or
   - Tap **Annotate** → **Mark up text**, then tap text to select.
2. Drag the selection handles as needed.
3. Tap a markup action (Highlight/Underline/Strikeout/Squiggly/Caret/Replace) or Copy.
4. Selection mode stays active so repeated markups are easy; press Back to exit selection and return to Reading.

### 11) Browse “Annotations” and jump between them
1. Page indicator → **Navigate & View** → **Annotations**.
2. Use the search field and type filter to find an entry.
3. Tap an entry → the view jumps and attempts to select it.
4. Use previous/next comment controls (toolbar and/or in the text editor dialog) to step between comments.

### 12) Fill PDF forms (AcroForm widgets)
1. (Recommended) Enable **Forms** highlight (page indicator → Navigate & View) so fields are visible.
2. Tap a field:
   - Text fields: inline editor or dialog.
   - Choice fields: picker dialog.
   - Signature fields: signing/check flows.
3. Use **Previous/Next field** buttons to move between fields when Forms highlight is enabled.

### 13) Fill & sign (signatures/stamps)
1. Menu → **Fill & sign**.
2. Pick an action (signature/initials/check/cross/date/name).
3. If needed, capture signature/initials or enter name once.
4. Place on page (drag/rotate/scale if applicable) → lift to commit.

### 14) Save vs export/share/print
- **Save changes** (writable PDFs only) writes back to the current URI.
- **Save a copy…** exports a PDF copy to a user-selected destination URI.
- **Share / Print** always exports a copy for external use and then launches the Android share/print flow.
- **Linearized/encrypted** export options are available only when qpdf ops are enabled.
- **Import/Export annotations** (sidecar bundle) is for EPUB and read-only PDFs.
- **EPUB export note**: if annotations exist only under a different reflow layout, export/share/print can be blocked until the user taps **Switch to annotated layout** in the snackbar.

### 15) EPUB reading settings (reflow)
1. Open an EPUB.
2. Page indicator → **Navigate & View** → **Reading settings**.
3. Adjust typography and theme; apply.
4. If ink exists, layout changes may be blocked and a notice is shown.

### 16) Create and manage “note documents”
1. Dashboard → **New Document**.
2. Enter a filename → a new blank PDF is created and opened.
3. To delete the note: page indicator → **Navigate & View** → **Delete note** (returns to dashboard).

### 17) Back button behavior (important)
- If in fullscreen: Back exits fullscreen.
- If dashboard is showing over a document: Back hides dashboard.
- If in Reading (no transient UI) and the document activity is task-root: Back shows Library (dashboard).
- If in Search: Back exits search and clears results.
- If text is selected: Back cancels selection.
- If in draw/erase mode: Back exits the mode; when there are in-progress strokes, it prompts to discard.
- If in Edit or Add-text placement: Back exits the mode (no “trapped” states).
- If there are unsaved changes and the activity is not task-root (e.g., opened from another app): Back shows a save prompt (**Save/Save as**, **No**, **Cancel**).
