# OpenDroidPDF JNI Bridge

This directory glues the Java/Kotlin layer to the MuPDF native engine.  The
legacy monolithic `mupdf.c` file has been split into feature-scoped
compilation units so ownership is obvious and incremental work stays isolated.

## Module Map

- `document_io.c` – document/session lifecycle, password handling, blank-page
  insertion, outline/link helpers, and general MuPDFCore bookkeeping.
- `render.c` – page and patch rendering, incremental repaint support, cookie
  helpers, and display list utilities.
- `ink.c` – ink annotation creation, stroke preview defaults, and color/
  thickness setters.
- `text_selection.c` – text search, structured text extraction, and HTML
  export utilities (wraps MuPDF’s `fz_stext_*` APIs).
- `text_annot.c` – highlight/underline/strikeout/free-text annotation glue.
- `widgets.c` – generic form widget plumbing (text/choice/listbox) plus focus
  management.
- `widgets_signature.c` – certificate verification, signing, and signature
  widget state helpers (split out so crypto dependencies stay isolated).
- `export_share.c` – save-as/incremental export helpers and unsaved-change
  checks.
- `alerts.c`, `cookies.c`, `proof.c`, `separations.c`, `utils.c` – supporting
  alert loop, cookie cancellation, gproof/separations, and shared helpers.
- `mupdf_native.h` – shared declarations, JNI macros, and the `globals`
  struct used across the source files.

On the managed side the JNI surface now feeds into `MuPdfRepository`
and the Kotlin `MuPdfController`, which expose feature-scoped helpers to
the rest of the app.  Whenever you extend the native API, add the C stub
here, declare it in `mupdf_native.h`, and then surface it through the
repository/controller pair so UI code never talks to `MuPDFCore`
directly.

Whenever you add a new domain, drop a dedicated `*.c` file here, include
`mupdf_native.h`, and list the file in `Android.mk` so ndk-build compiles it.

## Build Flow

Gradle drives the build via `ndkBuild` tasks.  `platform/android/jni/Android.mk`
links the feature files above with the upstream MuPDF static libraries produced
by `Core.mk` (MuPDF sources) and `ThirdParty.mk` (third-party deps).

1. `Core.mk` compiles `mupdfcore` from the upstream MuPDF checkout referenced
   by `MUPDF_ROOT`.  The comment at the top of `Core.mk` now mirrors this
   layout to keep future edits in sync.
2. `ThirdParty.mk` gathers MuPDF’s vendored dependencies (freetype, harfbuzz,
   etc.).  A short header comment explains how the include paths map to the
   split JNI files.
3. `Android.mk` links everything into the `libmupdf.so` shared library that the
   Java layer loads at runtime.  The new source list resides near the bottom of
   the file and should remain alphabetized to make diffs obvious.

To rebuild just the native layer you can run from `platform/android/`:

```
./gradlew :app:externalNativeBuildDebug
```

or rely on the normal `./gradlew assembleDebug` task, which first invokes the
ndk-build steps before packaging the APK.  If you change the MuPDF checkout
path, update `MUPDF_ROOT` in `Android.mk` and rerun Gradle so the new sources
are picked up.
