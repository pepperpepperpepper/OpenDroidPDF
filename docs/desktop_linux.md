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
- PDF digital signature support is currently **disabled by default** on Linux because the in-repo signature code is
  not compatible with modern OpenSSL APIs. You can try enabling it with `make ENABLE_OPENSSL=yes ...`, but it is
  expected to fail until we modernize `source/pdf/pdf-pkcs7.c`.

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

Annotations (PDF-only; ink):
- `p` toggles Pen (drag left mouse to draw)
- `e` toggles Eraser (click ink to delete)
- `Ctrl+Z` / `Ctrl+Shift+Z` undo/redo
- `Esc` exits the current tool

## Smoke (non-blank render oracle)

Run:
- `./scripts/linux_smoke.sh`

What it does:
- Builds MuPDF (`make build=debug -j…`)
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
