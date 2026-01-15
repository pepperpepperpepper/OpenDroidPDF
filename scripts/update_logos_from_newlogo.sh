#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/newlogo.png"

if ! command -v magick >/dev/null 2>&1; then
  echo "error: ImageMagick 'magick' not found in PATH" >&2
  exit 1
fi

if [[ ! -f "$SRC" ]]; then
  echo "error: missing source logo: $SRC" >&2
  exit 1
fi

calc_inner() {
  local size="$1"
  echo $(((size * 83 + 50) / 100))
}

render_png_square() {
  local size="$1"
  local out="$2"
  local inner
  inner="$(calc_inner "$size")"
  mkdir -p "$(dirname "$out")"
  magick "$SRC" \
    -colorspace sRGB -alpha off -background white \
    -fuzz 3% -trim +repage \
    -resize "${inner}x${inner}" \
    -gravity center -extent "${size}x${size}" \
    -strip \
    "$out"
}

render_jpg_square() {
  local size="$1"
  local out="$2"
  local inner
  inner="$(calc_inner "$size")"
  mkdir -p "$(dirname "$out")"
  magick "$SRC" \
    -colorspace sRGB -alpha off -background white \
    -fuzz 3% -trim +repage \
    -resize "${inner}x${inner}" \
    -gravity center -extent "${size}x${size}" \
    -strip -quality 92 \
    "$out"
}

render_ico() {
  local out="$1"
  local inner
  inner="$(calc_inner 256)"
  mkdir -p "$(dirname "$out")"
  magick "$SRC" \
    -colorspace sRGB -alpha off -background white \
    -fuzz 3% -trim +repage \
    -resize "${inner}x${inner}" \
    -gravity center -extent 256x256 \
    -strip -define icon:auto-resize=256,128,64,48,32,16 \
    "$out"
}

render_android_mipmaps() {
  local base="$1" # e.g. platform/android/res
  render_png_square 48 "$ROOT/$base/mipmap-mdpi/ic_launcher.png"
  render_png_square 72 "$ROOT/$base/mipmap-hdpi/ic_launcher.png"
  render_png_square 96 "$ROOT/$base/mipmap-xhdpi/ic_launcher.png"
  render_png_square 144 "$ROOT/$base/mipmap-xxhdpi/ic_launcher.png"
  render_png_square 192 "$ROOT/$base/mipmap-xxxhdpi/ic_launcher.png"
}

zip_googleplay_pack() {
  local out_zip="$1"
  local res_dir="$2" # platform/android/googleplayresources/res
  local tmp
  tmp="$(mktemp -d)"

  mkdir -p "$tmp/res"
  cp -R "$ROOT/$res_dir/mipmap-"* "$tmp/res/"
  render_png_square 512 "$tmp/web_hi_res_512.png"

  (
    cd "$tmp"
    # -X strips extra timestamps/attrs where supported.
    zip -q -r -9 -X "$ROOT/$out_zip" res web_hi_res_512.png
  )

  rm -rf "$tmp"
}

main() {
  # Branding / desktop packaging
  render_png_square 1024 "$ROOT/resources/branding/opendroidpdf_logo_1024.png"
  render_jpg_square 1024 "$ROOT/resources/branding/opendroidpdf_logo_source.jpg"
  render_png_square 512 "$ROOT/flatpak/icons/hicolor/512x512/apps/org.opendroidpdf.OpenDroidPDF.png"
  render_png_square 256 "$ROOT/flatpak/icons/hicolor/256x256/apps/org.opendroidpdf.OpenDroidPDF.png"
  render_ico "$ROOT/platform/gl/mupdf.ico"

  # Android app icons
  render_android_mipmaps "platform/android/res"
  render_android_mipmaps "platform/android/officepack/src/main/res"
  render_android_mipmaps "platform/android/googleplayresources/res"
  render_android_mipmaps "platform/android/googleplayresources/res_with_ear"

  # Google Play marketing assets
  render_png_square 854 "$ROOT/platform/android/googleplayresources/icon_square.png"
  render_png_square 854 "$ROOT/platform/android/googleplayresources/icon_square_open.png"
  render_png_square 854 "$ROOT/platform/android/googleplayresources/icon_square_settings.png"
  render_png_square 512 "$ROOT/platform/android/googleplayresources/icon_square_web_hi_res_512.png"
  render_png_square 512 "$ROOT/platform/android/googleplayresources/icon_with_ear.png"

  # Regenerate the zip packs (contents mirror the launcher mipmaps + web_hi_res_512.png)
  zip_googleplay_pack "platform/android/googleplayresources/ic_launcher_square.zip" "platform/android/googleplayresources/res"
  zip_googleplay_pack "platform/android/googleplayresources/ic_launcher_no_ear.zip" "platform/android/googleplayresources/res"
  zip_googleplay_pack "platform/android/googleplayresources/ic_launcher_with_ear.zip" "platform/android/googleplayresources/res"
  zip_googleplay_pack "platform/android/googleplayresources/icon_with_ear.zip" "platform/android/googleplayresources/res"

  # iOS app icon set + iTunes artwork
  render_png_square 57 "$ROOT/platform/ios/MuPDF/Images.xcassets/AppIcon.appiconset/Icon.png"
  render_png_square 114 "$ROOT/platform/ios/MuPDF/Images.xcassets/AppIcon.appiconset/Icon@2x.png"
  render_png_square 72 "$ROOT/platform/ios/MuPDF/Images.xcassets/AppIcon.appiconset/Icon-72.png"
  render_png_square 144 "$ROOT/platform/ios/MuPDF/Images.xcassets/AppIcon.appiconset/Icon-72@2x.png"
  render_png_square 76 "$ROOT/platform/ios/MuPDF/Images.xcassets/AppIcon.appiconset/Icon-76.png"
  render_png_square 152 "$ROOT/platform/ios/MuPDF/Images.xcassets/AppIcon.appiconset/Icon-76@2x.png"
  render_png_square 120 "$ROOT/platform/ios/MuPDF/Images.xcassets/AppIcon.appiconset/Icon-120.png"
  render_png_square 1024 "$ROOT/platform/ios/iTunesArtwork2.png"

  echo "updated logos/icons from $SRC"
}

main "$@"
