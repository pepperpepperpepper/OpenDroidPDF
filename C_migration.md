# Plan

Cross‑platform migration plan to extract platform‑neutral C from the Android JNI layer and reuse it on Linux and Android, while preserving existing MuPDF functionality.

## Requirements
- Share one C API that wraps MuPDF for document I/O, rendering, text, annotations, widgets, links/outline, save/export, and JS alerts.
- Work on Linux (CLI/X11/GL) and Android (JNI/Bitmap) with zero feature regressions for current app flows.
- Keep diffs minimal: thin Android wrappers, no churn in Java/Kotlin call sites beyond wiring.
- No Android‑only assumptions in shared code (e.g., `/system/fonts`, `AndroidBitmap_*`).

## Scope
- In: Extract portable C from `platform/android/jni/*.c`, add a small shared library, adapt Android JNI to call it, and integrate with existing Linux viewers/CLI.
- Out: Rewriting Android UI, replacing X11/GL front‑ends, or introducing a brand‑new Linux GUI (can be follow‑ups).

## Files and Entry Points
- New shared module: `platform/common/pp_core.h`, `platform/common/pp_core.c` (and small companions if needed).
- Android JNI (kept, thinned): `platform/android/jni/{document_io.c,render.c,text_selection.c,text_annot.c,ink.c,widgets.c,export_share.c,separations.c,alerts.c,utils.c}`.
- Linux front‑ends (reuse): `platform/x11/*`, `platform/gl/*`, CLI tools in `tools/` (e.g., `mutool`).
- Build scripts: `Makefile`, `Makerules`, `platform/android/jni/Core.mk` (or Gradle ndkBuild configs).

## Data Model / API (C) – proposed surface
- Context and document
  - `pp_ctx* pp_new(int store_mb)` / `void pp_drop(pp_ctx*)`
  - `pp_doc* pp_open(pp_ctx*, const char* path)` / `pp_doc* pp_open_stream(pp_ctx*, const char* magic, const void* data, size_t len)` / `void pp_close(pp_doc*)`
  - `int pp_count_pages(pp_doc*)`, `bool pp_is_pdf(pp_doc*)`, `const char* pp_format(pp_doc*)`
- Page and rendering
  - `bool pp_goto_page(pp_doc*, int page_index, pp_page_cache* out)`
  - `bool pp_page_size(pp_doc*, int page_index, float* w, float* h)`
  - `bool pp_render_patch_rgba(pp_doc*, int page_index, int view_w, int view_h, int x, int y, int w, int h, uint8_t* rgba, int stride, const pp_cookie* cookie)`
- Text/search/export
  - `int pp_search_page(pp_doc*, int page_index, pp_rect* out, int max)`
  - `bool pp_text_as_html(pp_doc*, int page_index, pp_buf* out_html)`
- Annotations
  - `bool pp_add_ink(pp_doc*, int page_index, const pp_point* points, const int* counts, int arcs, float r,g,b, float thickness)`
  - `bool pp_add_markup(pp_doc*, int page_index, const pp_point* quad_points, int n, pp_markup_type type)`
  - `bool pp_add_text_annot(pp_doc*, int page_index, const pp_point* where, int n, const char* text)`
  - `bool pp_delete_annot(pp_doc*, int page_index, int annot_index)` / `bool pp_delete_annot_objnum(pp_doc*, int page_index, long objnum)`
- Widgets (PDF forms)
  - `int pp_widget_rects(pp_doc*, int page_index, pp_rect* out, int max)`
  - `bool pp_widget_set_text(pp_doc*, const char* value)` / `int pp_widget_choices(pp_doc*, const char** out, int max)` / `bool pp_widget_set_choices(pp_doc*, const char** vals, int n)`
- Links/outline
  - `int pp_links(pp_doc*, int page_index, pp_link* out, int max)`
  - `int pp_outline(pp_doc*, pp_outline_item* out, int max)`
- Save/export/state
  - `bool pp_has_changes(pp_doc*)`, `bool pp_save_as(pp_doc*, const char* path, bool incremental_hint)`
- JS alerts
  - Callback registration: `void pp_set_alert_cb(pp_doc*, pp_alert_cb, void* user)`

Note: Types like `pp_rect`, `pp_point`, `pp_link`, `pp_outline_item`, `pp_cookie`, and a small `pp_page_cache` mirror the existing JNI structs without JNI fields.

## Action Items
- [ ] Create `platform/common/pp_core.h/.c` with minimal structs and the API above (wrap MuPDF only; no JNI/Android includes).
- [ ] Extract portable code from JNI files into `pp_core`:
  - Text/search/HTML: from `text_selection.c`.
  - Markup/text/ink annots: from `text_annot.c`, `ink.c` (lift `penandpdf_set_ink_annot_list`).
  - Widgets: from `widgets.c` (query/edit, focus handling minus Java coupling).
  - Links/outline: from `document_io.c` and related helpers.
  - Save/export/dirty: from `export_share.c` (rename temp‑path helper, remove JNI bits).
  - Separations: from `separations.c`.
  - Alerts: from `alerts.c` (keep pthreads; expose callback hook).
  - Page cache helpers: from `utils.c` (drop JNI field access; keep cache/drop logic).
- [ ] Rendering split:
  - Add `pp_render_patch_rgba(...)` that renders into caller‑provided RGBA buffer using MuPDF draw device.
  - Android: keep `render.c` JNI, replace `AndroidBitmap_*` usage with: lock Bitmap → call `pp_render_patch_rgba` → unlock.
  - Linux: plumb RGBA into `platform/x11/*` or `platform/gl/*` (XImage upload or GL texture path).
- [ ] Fonts hook abstraction:
  - Replace `install_android_system_fonts` with `pp_install_system_fonts(ctx, provider)` where provider is optional; on Linux, either no‑op or use fontconfig discovery; on Android, keep current `/system/fonts` probing.
- [ ] Build system integration:
  - Add `libppcore.a` to `Makefile` (Linux) and to `platform/android/jni/Core.mk`/Gradle ndkBuild (Android).
  - Link `mupdf-x11`/`mupdf-gl`/`mutool` with `libppcore.a` incrementally where useful (start with a small demo cmd: render patch, save, add ink).
- [ ] Android wiring (non‑breaking):
  - Keep `MuPDFCore`/`OpenDroidPDFCore` JNI signatures; inside JNI, translate Java types to `pp_core` calls.
  - Preserve existing behavior and logging; no Java/Kotlin API change.
- [ ] Minimal Linux demo:
  - Add a tiny CLI (e.g., `tools/pp_demo.c`) that opens a doc, renders page 0 to `out.png`, adds ink, and saves — exercises the shared API.
- [ ] Smoke tests:
  - PDF: open → render → search → add ink → save → reload verify.
  - EPUB: open → layout → render → export HTML.
  - Forms: list widget rects → set text/choices → save.
  - JS alert: register callback; ensure callback fires on documents that trigger alerts.
- [ ] Document the API and migration notes in `docs/pp_core.md` and update `README` with Linux/Android reuse guidance.

## Testing and Validation
- Linux
  - `make build=release -j && build/release/mutool info test_assets/pdf_with_text.pdf`
  - Run `pp_demo` to render `out.png`, add ink, and save; visually inspect and verify `pdf_has_unsaved_changes` transitions.
- Android
  - `./gradlew assembleDebug` then existing instrumentation smokes (open, draw, search, save/export, widgets).
- Golden images
  - Add a page‑0 render of `test_assets/pdf_with_text.pdf` as a baseline; allow small per‑backend deltas.
- Threading
  - Verify cookies/abort, cache dropping, and alert callback synchronization under normal and aborted renders.

## Risks and Edge Cases
- Rendering parity: RGBA patch path must match Android’s visuals; watch color management and stride alignment.
- Fonts: Android `/system/fonts` probing must not leak into Linux; decide on fontconfig use vs MuPDF defaults.
- JNI stability: Keep method signatures and semantics to avoid breaking the Java/Kotlin layers.
- Incremental saves: Preserve behavior when saving to same path (temp rename, incremental hints).
- PDF‑only features: Widgets/separations must be no‑ops for non‑PDF docs.
- Alerts: Callback lifetimes and mutex/condvar use must avoid deadlocks on shutdown.

## Open Questions
- Should Linux integrate via existing `mupdf-x11/gl`, or ship a small new GTK/Qt front‑end later?
- Do we adopt fontconfig explicitly, or rely on MuPDF’s built‑ins for now?
- Where to host conformance tests for visual parity (repo vs CI artifact)?

