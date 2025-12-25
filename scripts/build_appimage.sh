#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OUTDIR="$ROOT/build/appimage"
TOOLS_DIR="$OUTDIR/tools"
APPDIR="$OUTDIR/OpenDroidPDF.AppDir"

mkdir -p "$TOOLS_DIR"

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "error: missing required command: $1" >&2
    exit 1
  }
}

download_if_missing() {
  local url="$1"
  local dst="$2"
  if [ -x "$dst" ]; then
    return 0
  fi
  need_cmd curl
  echo "Downloading: $url"
  curl -L --fail --output "$dst" "$url"
  chmod +x "$dst"
}

LINUXDEPLOY="$TOOLS_DIR/linuxdeploy-x86_64.AppImage"
APPIMAGETOOL="$TOOLS_DIR/appimagetool-x86_64.AppImage"

download_if_missing "https://github.com/linuxdeploy/linuxdeploy/releases/download/continuous/linuxdeploy-x86_64.AppImage" "$LINUXDEPLOY"
download_if_missing "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage" "$APPIMAGETOOL"

need_cmd make

echo "Building (release)…"
make build=release -j"$(nproc)"

rm -rf "$APPDIR"
mkdir -p "$APPDIR"

echo "Installing into AppDir…"
make build=release install prefix=/usr DESTDIR="$APPDIR"

install -Dm755 "$ROOT/flatpak/opendroidpdf" "$APPDIR/usr/bin/opendroidpdf"
install -Dm644 "$ROOT/flatpak/org.opendroidpdf.OpenDroidPDF.desktop" "$APPDIR/usr/share/applications/org.opendroidpdf.OpenDroidPDF.desktop"
install -Dm644 "$ROOT/flatpak/icons/hicolor/512x512/apps/org.opendroidpdf.OpenDroidPDF.png" "$APPDIR/usr/share/icons/hicolor/512x512/apps/org.opendroidpdf.OpenDroidPDF.png"

echo "Bundling + producing AppImage…"
(
  cd "$OUTDIR"
  APPIMAGE_EXTRACT_AND_RUN=1 APPIMAGETOOL="$APPIMAGETOOL" "$LINUXDEPLOY" \
    --appdir "$APPDIR" \
    --desktop-file "$APPDIR/usr/share/applications/org.opendroidpdf.OpenDroidPDF.desktop" \
    --icon-file "$APPDIR/usr/share/icons/hicolor/512x512/apps/org.opendroidpdf.OpenDroidPDF.png" \
    --output appimage
)

echo "Done. Output should be in: $OUTDIR/"
