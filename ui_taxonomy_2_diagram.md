# UI Taxonomy 2 (Android) - ASCII Diagram

This is a node-map style, ASCII-only diagram of `ui_taxonomy_2.md`. It is meant to be *readable at a glance* while still enumerating every menu action and setting.

Legend:
- `[Node]` = screen/surface/mode
- `(menu_*)` = menu item ID
- `(pref_*)` / other keys = preference keys / category keys
- `-->` = primary navigation / action result
- `<Back>` = back behavior / exit
- `*` = availability is gated (PDF vs EPUB, permissions, debug build, etc.)

```text
================================================================================
PROPOSED UX (TARGET ORGANIZATION)
================================================================================

Constraints / heuristics:
- No long-press commands (long-press does not trigger actions; never used for delete/discard/selection).
- Aim for <=2 taps from Reading/Library to reach common tasks whenever possible.
- Avoid multi-level/nested menus for common actions (use one layer + section headers/dividers).

[APP]
|-- [Library / Home]  (implemented today as a dashboard overlay via (menu_open))
|   |-- Open document... -----------------------> [Android SAF picker] -> [Document]
|   |-- New document... ------------------------> create blank note PDF -> [Document]
|   |-- Recents --------------------------------> [Document]
|   `-- Settings -------------------------------> [Settings]
|
|-- [Document: Reading]
|   |-- Top app bar (stable / minimal)
|   |   |-- Library ----------------------------> (menu_open) -> [Library overlay]
|   |   |-- Search -----------------------------> (menu_search) -> [Search]
|   |   |-- Annotate (tool palette) ------------> [Annotate palette]
|   |   |-- Export... --------------------------> (menu_share) -> [Export sheet]
|   |
|   |-- Bottom edge
|   |   `-- Page indicator ---------------------> [Navigate & View sheet] (one layer; no nested)
|   |        |-- Navigate ----------------------> (menu_toc, menu_gotopage)
|   |        |-- View --------------------------> (menu_fullscreen, menu_show_comments,
|   |        |                                    menu_sticky_notes*, menu_reading_settings*,
|   |        |                                    menu_forms*)
|   |        `-- Document (contextual) ----------> (menu_save*, Organize pages (new)*, menu_delete_note*)
|   |
|   |-- [Organize pages (PDF only)] (new)
|   |   |-- Entry: page indicator -> Organize pages
|   |   |-- Select pages ----------------------> tap to toggle selection; selection count visible
|   |   |-- Reorder ---------------------------> drag handle OR Move up/down
|   |   |-- Delete page(s) --------------------> confirm
|   |   |-- Rotate page(s) --------------------> 90-degree steps
|   |   |-- Insert blank page -----------------> choose position
|   |   |-- Insert pages from PDF -------------> pick PDF, optionally pick pages, insert/append (merge)
|   |   |-- Create new PDF from pages ---------> select pages -> create PDF -> save a copy
|   |   `-- Done -------------------------------> Save a copy (default) OR Save changes*
|   |
|   |-- Contextual chip
|   |   `-- Return* (after link jump) ----------> (menu_linkback)
|   |
|   |-- Contextual surfaces (appear only when relevant)
|   |   |-- [Text selection (ad-hoc)] ----------> (menu_copytext + markups)
|   |   |-- [Text markup mode] -----------------> markups + (menu_undo/menu_redo) + Done
|   |   |-- [Annotation selected] --------------> Properties / Delete / Duplicate / Arrange/Advanced actions
|   |   `-- [Forms active*] --------------------> (menu_form_previous/menu_form_next)
|   |
|   `-- Exit semantics (contract)
|       |-- Close/Cancel = X -------------------> exits transient UI/mode
|       |-- Done = checkmark -------------------> returns to Reading
|       |-- Delete = trash ---------------------> deletes selected object (confirm as needed)
|       `-- <Back> unwinds: sheet/dialog -> selection/search -> tool mode -> link return -> Library
|
`-- [Settings] (global defaults; future-doc intent)


================================================================================
CURRENT UI (IMPLEMENTED TODAY) - SURFACES + STATE MACHINE
================================================================================

[OpenDroidPDFActivity]
|-- Surfaces
|   |-- [Dashboard] (DashboardFragment)
|   |   |-- Open Document ----------------------> Android SAF picker -> reopen chosen URI
|   |   |-- New Document -----------------------> filename prompt -> create note PDF -> open
|   |   |-- Settings ---------------------------> SettingsActivity
|   |   `-- Open recent ------------------------> open recent (closes prior activity instance)
|   |
|   |-- [Document viewer/editor]
|   |-- [SettingsActivity] (SettingsFragment)
|   `-- [System UI] Android-owned (picker/share/print)
|
|-- Gesture router (TapGestureRouter)
|   |-- Pan/scroll; pinch-zoom
|   |-- Tap navigation (view + search):
|   |   |-- top/left margin --------------------> previous page/position
|   |   `-- bottom/right margin ----------------> next page/position
|   |-- Tap main document area -----------------> clears contextual state (e.g., exit Edit -> Main)
|   |-- Links ----------------------------------> follow; record link-back target
|   `-- Add-text placement ----------------------> tap page = place text annotation + exit placement
|
|-- Toolbar mode machine (ActionBarMode -> menu XML)
|   |-- [Main]            -> main_menu.xml
|   |-- [Search]          -> search_menu.xml
|   |-- [Selection]       -> selection_menu.xml
|   |-- [Edit]            -> edit_menu.xml
|   |-- [Annot]           -> annot_menu.xml
|   |-- [AddingTextAnnot] -> add_text_annot_menu.xml
|   |-- [Hidden]          -> empty_menu.xml (fullscreen)
|   `-- [Empty]           -> empty_menu.xml (dashboard visible)
|
`-- Back behavior (current)
    |-- Fullscreen (Hidden) ---------------------> <Back> exits fullscreen
    |-- Dashboard shown over a document ---------> <Back> hides dashboard overlay
    |-- Search ----------------------------------> <Back> exits search + clears highlights
    |-- Text selection --------------------------> <Back> cancels selection
    |-- Draw/erase (Annot) ----------------------> <Back> exits mode; discard prompts when needed
    |-- Edit + Add-text placement ---------------> <Back> exits mode first (no trapped states)
    `-- Unsaved changes -------------------------> prompt: Save/Save-as, No, Cancel


================================================================================
CURRENT UI (IMPLEMENTED TODAY) - MENUS (EVERY USER ACTION)
================================================================================

[Main toolbar] (main_menu.xml)
|-- Primary icons
|   |-- Library ---------------------------------> (menu_open) -> show dashboard/library
|   |-- Search ----------------------------------> (menu_search) -> Search mode
|   |-- Export... --------------------------------> (menu_share) -> [Export sheet]
|   `-- (other primary icons are state-driven)
|
|-- [Export sheet]
|   |-- Share a copy ----------------------------> export copy -> Android share sheet
|   |-- Save a copy... --------------------------> export copy -> SAF create-document -> saved file
|   |-- Print -----------------------------------> (menu_print) -> export copy -> Android print UI
|   `-- Advanced options... (expand) ------------> export/save variants
|       |-- Export linearized copy...* ----------> (menu_share_linearized) -> qpdf -> share
|       |-- Export encrypted copy...* -----------> (menu_share_encrypted) -> password -> qpdf -> share
|       |-- Export flattened PDF...* ------------> (menu_share_flattened) -> pdfbox/raster -> share
|       |-- Save linearized copy...* ------------> (menu_save_linearized) -> qpdf -> SAF "save to" URI
|       |-- Save encrypted copy...* -------------> (menu_save_encrypted) -> password -> qpdf -> SAF "save to" URI
|       |-- Export annotations...* --------------> (menu_export_annotations) (EPUB + read-only PDFs)
|       `-- Import annotations...* --------------> (menu_import_annotations) (EPUB + read-only PDFs)
|
|-- Link return (contextual)
|   `-- Link back* ------------------------------> (menu_linkback) -> return to pre-link viewport
|
|-- Editor tools (state-driven)
|   |-- Undo / Redo -----------------------------> (menu_undo / menu_redo)
|   |-- Draw ------------------------------------> (menu_draw) -> Annot mode
|   |-- Add text annotation ---------------------> (menu_add_text_annot) -> placement mode
|   |-- Paste text annotation -------------------> (menu_paste_text_annot) (enabled when internal clipboard has payload)
|   |-- Prev/Next field (contextual; PDF*) ------> (menu_form_previous / menu_form_next)
|   `-- Fill & Sign... (PDF*) -------------------> (menu_fill_sign) -> fill/sign flow
|
`-- Bottom edge (page indicator)
    `-- Page indicator --------------------------> [Navigate & View sheet]
         |-- Navigate ---------------------------> (menu_toc, menu_gotopage)
         |-- View -------------------------------> (menu_fullscreen, menu_show_comments, menu_comments,
         |                                          menu_sticky_notes*, menu_reading_settings*,
         |                                          menu_forms*)
         `-- Document ---------------------------> (menu_save*, menu_addpage*, menu_delete_note*)


[Search toolbar] (search_menu.xml)
|-- Search box -----------------------------------> (menu_search_box)
|-- Previous / Next hit ---------------------------> (menu_previous / menu_next)
`-- Exit ------------------------------------------> <Back> / close SearchView


[Selection toolbar] (selection_menu.xml)  (entry: long-press selects text)
|-- Copy text -------------------------------------> (menu_copytext)
|-- Highlight -------------------------------------> (menu_highlight)
|-- Underline -------------------------------------> (menu_underline)
|-- Strikeout -------------------------------------> (menu_strikeout)
|-- Delete (proof) --------------------------------> (menu_delete_text)  (creates a proofing/strikeout-style annotation)
|-- Squiggly --------------------------------------> (menu_squiggly)
|-- Insert mark -----------------------------------> (menu_caret)
`-- Replace (proof) --------------------------------> (menu_replace)
    `-- (behavior note) action typically exits selection -> Main


[Edit toolbar] (edit_menu.xml)  (entry: tap an annotation)
|-- Cancel (X) -------------------------------------> (menu_cancel) -> exit selection
|-- Delete -----------------------------------------> (menu_delete_annotation) -> confirm -> delete
|-- Undo / Redo ------------------------------------> (menu_undo / menu_redo)
|-- Save... ----------------------------------------> (menu_save)
|-- Prev/Next comment (contextual) -----------------> (menu_comment_previous / menu_comment_next)
|-- Edit (label currently "Draw") ------------------> (menu_edit)
|-- Move -------------------------------------------> (menu_move)
|-- Resize (toggle) --------------------------------> (menu_resize)
|-- Style ------------------------------------------> (menu_text_style) -> text style dialog
|-- Duplicate text ---------------------------------> (menu_duplicate_text)
|-- Copy / Paste annotation ------------------------> (menu_copy_text_annot / menu_paste_text_annot)
`-- Done -------------------------------------------> (menu_accept) -> exit Edit


[Annot toolbar] (annot_menu.xml)  (entry: (menu_draw) or stylus-down if enabled)
|-- Undo / Redo ------------------------------------> (menu_undo / menu_redo)
|-- Save... ----------------------------------------> (menu_save)
|-- Cancel (X) -------------------------------------> (menu_cancel) -> discard prompt when needed -> exit Annot
|-- Erase / Draw -----------------------------------> (menu_erase / menu_draw)
|-- Ink color / Pen size ---------------------------> (menu_ink_color / menu_pen_size) -> pen settings dialog
`-- Done -------------------------------------------> (menu_accept) -> commit + exit Annot


[Add-text placement toolbar] (add_text_annot_menu.xml)
|-- Cancel -----------------------------------------> (menu_cancel) -> exit placement
`-- Place text box ---------------------------------> tap page -> create text annotation + open editor


[Fullscreen/Hidden toolbar] (empty_menu.xml)
`-- (no menu items)


[Debug overlay] (debug_menu.xml)  (debug builds only)
|-- Snap fit ---------------------------------------> (menu_debug_snap_fit)
|-- Show text widget --------------------------------> (menu_debug_show_text_widget)
|-- Show choice widget ------------------------------> (menu_debug_show_choice_widget)
|-- Export test -------------------------------------> (menu_debug_export_test)
|-- Alert test --------------------------------------> (menu_debug_alert_test)
|-- Render self-test --------------------------------> (menu_debug_render_self_test)
|-- qpdf smoke test* --------------------------------> (menu_debug_qpdf_smoke)
`-- pdfbox flatten* ---------------------------------> (menu_debug_pdfbox_flatten)


================================================================================
CURRENT UI (IMPLEMENTED TODAY) - CONTEXTUAL / NON-TOOLBAR UI
================================================================================

[Snackbars / banners] (UiStateDelegate)
|-- PDF read-only ----------------------------------> action: Enable saving (reopen picker to refresh permissions)
|-- Imported Word -----------------------------------> action: Learn more
|-- EPUB reflow layout mismatch ---------------------> action: Switch to annotated layout
`-- PDF XFA unsupported -----------------------------> action: Learn more -> (convert via XFA Pack / install / open in other app / share)

[Text annotation quick actions popup] (TextAnnotationQuickActionsController)
|-- Visible only when:
|   `-- a text annotation is selected AND (menu_show_comments) is enabled
`-- Actions:
    |-- Properties ----------------------------------> text style dialog
    |-- Duplicate
    |-- Multi-select: add
    |-- Multi-select: align/distribute --------------> includes "clear selection"
    |-- Multi-select: group move toggle
    |-- Lock position/size toggle
    |-- Fit to text (FreeText only)
    `-- Delete (immediate)

[Direct manipulation - text annotations] (TextAnnotationManipulationGestureHandler)
|-- Drag inside selection bounds --------------------> move
|-- Drag move handle (top-center) -------------------> move (when shown)
|-- Resize handles (when (menu_resize) enabled) -----> resize
|-- Lock enabled -----------------------------------> blocks move/resize (UI hint)
`-- Multi-select group ------------------------------> move the entire set (same page only)

[Text annotation editor] (TextAnnotationController)
|-- Entry points:
|   |-- (menu_edit)
|   `-- tap selected text annotation again ----------> open editor directly
`-- Affordances:
    |-- inline editor (if supported) OR dialog fallback
    |-- rich-text warning (Acrobat rich FreeText)
    `-- best-effort previous/next comment navigation

[Comments list dialog] (CommentsListController)  (entry: (menu_comments))
|-- Search field (filters list)
|-- Type filter (All / Notes / Text boxes / Markups / Ink)
`-- Select item ------------------------------------> jump to page + attempt selection

[Pen settings dialog] (PenSettingsController)
|-- Entry: (menu_ink_color) / (menu_pen_size) OR draw-icon shortcut
`-- Controls:
    |-- thickness slider + preview
    `-- color palette grid

[Text style dialog] (TextAnnotationStyleController)
|-- Entry: (menu_text_style) OR quick-actions Properties
`-- Controls (selection-dependent):
    |-- font size/family/style, alignment, line spacing/indent
    |-- text + background colors, background opacity
    |-- fit-to-text, rotation
    |-- border: color/style/width/radius
    `-- locks: lock position/size; lock contents

[Reflow "Reading settings" dialog] (ReflowSettingsController)  (EPUB; entry: (menu_reading_settings))
|-- Font size, margins, line spacing, theme (per-document)
`-- Special case: if ink exists, layout-affecting changes may be blocked (theme changes allowed)

[Forms/widgets UI] (MuPDFPageViewWidgets) (PDF)
|-- Toggle highlight -------------------------------> (menu_forms)
|-- Tap widget -------------------------------------> edit widget
`-- Navigate fields (contextual) -------------------> (menu_form_previous / menu_form_next)

[Fill & Sign flow] (FillSignController) (PDF; entry: (menu_fill_sign))
|-- Choose: signature / initials / checkmark / cross / date / name
|-- Capture signature/initials (if needed; once)
`-- Place:
    |-- signature/initials/check/cross --------------> touch down preview; drag/rotate/scale; lift to commit (ink)
    `-- date/name ----------------------------------> tap-to-place (FreeText)

[Password + permission dialogs]
|-- Password prompt (encrypted PDFs) ---------------> PasswordDialogHelper
|-- Missing URI access -----------------------------> DocumentSetupController (Grant access)
|-- Word import unavailable ------------------------> open in other app / install Office Pack
`-- Encrypted/DRM EPUB unsupported -----------------> informational dialog (cannot open)


================================================================================
SETTINGS (CURRENT) - PREFERENCE TREE
================================================================================

[SettingsActivity -> SettingsFragment]  (preferences.xml)
|-- Autosave (pref_autosave_settings)
|   |-- Save on destroy ----------------------------> (pref_save_on_destroy)
|   `-- Save on stop -------------------------------> (pref_save_on_stop)
|
|-- UI (pref_ui_settings)
|   |-- Use stylus ---------------------------------> (pref_use_stylus)
|   |-- Smart text selection -----------------------> (pref_smart_text_selection)
|   `-- Number of recent files ---------------------> (pref_number_recent_files)
|
|-- Editor (screen) (pref_editor_settings)
|   `-- (placeholder, disabled) --------------------> (pref_editor_settings_placeholder)
|
|-- Display (display_settings)
|   |-- Keep screen on -----------------------------> (keep_screen_on)
|   |-- Fit width ----------------------------------> (pref_fit_width)
|   `-- Page swipe direction ------------------------> (pref_page_paging_axis)
|
|-- Annotation (pref_annotation_settings)
|   |-- Ink thickness -------------------------------> (pref_ink_thickness)
|   |-- Eraser thickness ----------------------------> (pref_eraser_thickness)
|   |-- Ink color -----------------------------------> (pref_ink_color)
|   |-- Highlight color -----------------------------> (pref_highlight_color)
|   |-- Underline color -----------------------------> (pref_underline_color)
|   |-- Strikeout color -----------------------------> (pref_strikeout_color)
|   `-- Text annotation icon color ------------------> (pref_textannoticon_color)
|
|-- About (pref_about_screen)
|   |-- Version (read-only) -------------------------> (pref_about_version)
|   |-- License -------------------------------------> (pref_about_license)
|   |-- Source --------------------------------------> (pref_about_source)
|   `-- Issues --------------------------------------> (pref_about_issues)
|
`-- Experimental (pref_experimental_mode_settings)
    `-- Experimental mode ---------------------------> (pref_experimental_mode)

Keys present but not exposed to normal users:
|-- pref_scroll_vertical (commented out)
|-- pref_scroll_continuous (commented out)
`-- pref_test_list_preference (test scaffolding)


================================================================================
END-TO-END FLOWS (CURRENT) - QUICK MAP
================================================================================

Launch -> [Dashboard]
[Dashboard] Open Document -> SAF picker -> [Document: Main]
[Document] (menu_open) -> [Library] -> choose recent/open -> [Document]
[Document] (menu_search) -> [Search] -> query -> (menu_previous/menu_next) -> <Back> -> [Reading]
[Document] page indicator -> [Navigate & View] -> (menu_toc/menu_gotopage) -> jump -> [Reading]
[Document] link tap -> (menu_linkback*) -> return
[Document] (menu_draw) -> [Annot] -> draw -> (menu_accept) OR (menu_cancel/<Back>) (discard prompt if needed)
[Document] (menu_add_text_annot) -> [Add-text placement] -> tap to place -> editor -> accept/done paths
[Document] long-press text -> [Selection] -> markup/copy -> exit -> [Reading]
[Document] export: (menu_share/menu_print/menu_share_*/menu_save_*) -> system share/print/picker
[Document] page indicator -> [Navigate & View] -> (menu_fullscreen) -> [Fullscreen] -> <Back> -> [Reading]
[Library] Settings -> [SettingsActivity]
```
