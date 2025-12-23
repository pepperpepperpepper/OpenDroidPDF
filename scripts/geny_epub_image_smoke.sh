#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for an EPUB fixture that embeds an image:
# - Open an EPUB with a large magenta PNG
# - Assert render is non-blank (content region)
# - Assert screenshot contains a meaningful amount of magenta pixels
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_image_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/image.epub}
EPUB_REMOTE=${EPUB_REMOTE:-/sdcard/Download/image.epub}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_assert_nonblank_and_magenta_png() {
  local png="$1"
  python - "$png" <<'PY'
import sys
from PIL import Image

path = sys.argv[1]
img = Image.open(path).convert("RGB")
w, h = img.size

# Skip status/toolbar area; focus on document content.
ystart = int(h * 0.12)

step = 3
nonwhite = 0
dark = 0
magenta = 0
samples = 0

px = img.load()
for y in range(ystart, h, step):
    for x in range(0, w, step):
        r, g, b = px[x, y]
        samples += 1

        if r < 245 or g < 245 or b < 245:
            nonwhite += 1
        if r < 80 and g < 80 and b < 80:
            dark += 1

        # Magenta-ish pixels (our fixture image is dominated by #FF00FF).
        if r >= 230 and b >= 230 and g <= 90:
            magenta += 1

    # Early exit once we have enough signal.
    if nonwhite > 1800 and dark > 250 and magenta > 450:
        break

if nonwhite <= 1800 or dark <= 250:
    raise SystemExit(
        f"FAIL: render looks blank-ish: nonwhite={nonwhite}, dark={dark}, samples={samples}, size={w}x{h}"
    )
if magenta <= 450:
    raise SystemExit(
        f"FAIL: fixture image not detected (magenta too low): magenta={magenta}, samples={samples}, size={w}x{h}"
    )

print(
    f"OK: nonblank render + image detected (nonwhite={nonwhite}, dark={dark}, magenta={magenta}, samples={samples}, size={w}x{h})"
)
PY
}

OUT_PREFIX="${OUT_PREFIX:-tmp_geny_epub_image}"

echo "[1/6] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/6] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/6] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[4/6] Push image EPUB"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null

echo "[5/6] Launch viewer with image EPUB"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

OUT_BASE="${OUT_BASE:-${OUT_PREFIX}.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_BASE"
echo "  wrote $OUT_BASE"
_assert_nonblank_and_magenta_png "$OUT_BASE"

echo "[6/6] Logcat fatal check"
if adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" >/dev/null; then
  echo "FAIL: detected fatal logcat entries" >&2
  adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" | tail -n 40 >&2
  exit 1
fi

echo "EPUB image smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
