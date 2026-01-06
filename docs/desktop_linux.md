# OpenDroidPDF on Linux (Desktop)

This repo can already build MuPDF tools/viewers on Linux, and the “Desktop/Linux parity track” (see `plan.md`)
aims to evolve that into “OpenDroidPDF on desktop” with shared core behavior across Android and Linux.

## Baseline build (Arch / Linux)

### Dependencies (packages)

At minimum (typical Arch package names):
- Toolchain: `base-devel`, `pkgconf`
- Core libs: `freetype2`, `harfbuzz`, `jbig2dec`, `openjpeg2`, `libjpeg-turbo`, `zlib`, `openssl`
- X11/GL runtime deps (for `mupdf-x11`/`mupdf-gl`): `libx11`, `libxext`, `libxrandr`, `libxcursor`, `libxinerama`, `mesa`

Notes:
- The in-repo vendored GLFW is currently compiled in X11 mode, so `mupdf-gl` runs on Wayland via Xwayland.
- If you want native Wayland later, we can switch to system GLFW built with Wayland support or extend the vendored build.
- PDF digital signature support is **opt-in** on Linux (adds an OpenSSL/libcrypto dependency).
  Enable it with `make ENABLE_OPENSSL=yes ...`.

### Optional: Word import (`.doc`/`.docx`)

Word documents are supported on Linux via **import-as-PDF** using LibreOffice headless.

Cross-platform plan:
- See `docs/word_import.md` for how Android (Office Pack / `pdfbox-android`) and Linux (LibreOffice / `soffice`)
  fit together.

Prereq (Arch package name):
- `libreoffice-fresh` (provides `soffice`)

Behavior:
- Opening a `.doc/.docx` converts it to a cached PDF under `$XDG_CACHE_HOME/opendroidpdf/word/`.
- The viewer opens the derived PDF, but exports use the original document name (`*-annotated.pdf`).

Smoke:
- `./scripts/linux_docx_import_smoke.sh`

### Build

From the repo root:
- `make build=debug -j$(nproc)`

Outputs land in:
- `build/debug/`

Useful binaries:
- `build/debug/mutool`
- `build/debug/mupdf-gl` (GLFW)
- `build/debug/mupdf-x11`

## Desktop viewer shortcuts (mupdf-gl)

Search:
- `Ctrl+F` (search forward)
- `Ctrl+Shift+F` (search backward)
- `F3` (next match)
- `Shift+F3` (previous match)
- `/` (search forward, vi-style)
- `?` (search backward, vi-style)

Annotations (PDF-only):
- `p` toggles Pen (drag left mouse to draw)
- `h` toggles Highlight (drag to mark a rectangle)
- `k` toggles Note (click to place a sticky note)
- `e` toggles Eraser (click ink to delete)
- `Ctrl+Z` / `Ctrl+Shift+Z` undo/redo
- `Ctrl+S` exports an annotated copy as `*-annotated.pdf` (next to the input PDF)
  - For PDFs: prefers embedding annotations; falls back to a flattened export if embedding fails.
  - If the input directory is not writable, it falls back to writing under `$HOME` (or `$TMPDIR`).
- `Esc` exits the current tool

## Smoke (non-blank render oracle)

Run:
- `./scripts/linux_smoke.sh`

What it does:
- Builds MuPDF (`make build=debug -j…`)
- Runs a “flatten export” smoke via `pp_demo --flatten-smoke`
- Renders:
  - `test_assets/pdf_with_text.pdf` page 1
  - `test_assets/hello.epub` page 1 (with fixed reflow layout args)
- Fails if the resulting PPM has “low contrast” (very small byte span), which usually indicates blank rendering

Artifacts:
- `build/debug/linux_smoke_pdf.ppm`
- `build/debug/linux_smoke_epub.ppm`

## Next steps

The desktop parity plan introduces a shared portable C core (`platform/common/pp_core.*`) and progressively routes
both Android JNI and the Linux frontend through it so that document behavior is implemented once (ONE OWNER).

## Packaging (Flatpak / AppImage)

Packaging is tracked in `plan.md` under the Desktop / Linux parity track (L8).

### Flatpak (recommended for testers)

Prereqs:
- Install `flatpak` + `flatpak-builder` from your distro

Build + install (from repo root):
- `flatpak-builder --user --install --force-clean build/flatpak flatpak/org.opendroidpdf.OpenDroidPDF.yml`

Run:
- `flatpak run org.opendroidpdf.OpenDroidPDF`

Notes:
- The in-repo GLFW build is currently X11-only, so on Wayland the Flatpak runs via Xwayland.

### AppImage (portable)

Build (from repo root):
- `./scripts/build_appimage.sh`

Output:
- `build/appimage/` (filename determined by `linuxdeploy`)
