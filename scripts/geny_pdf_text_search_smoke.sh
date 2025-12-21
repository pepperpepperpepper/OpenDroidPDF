#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "PDF with text" fixture:
# - Open a PDF that contains known text
# - Assert render is non-blank
# - Run Search for a known token and assert highlight overlay appears
#
# Usage:
#   DEVICE=localhost:42865 APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_text_search_smoke.sh

DEVICE=${DEVICE:-localhost:42865}
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_with_text.pdf}
PDF_REMOTE=${PDF_REMOTE:-/sdcard/Download/pdf_with_text.pdf}
QUERY=${QUERY:-quick}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_assert_nonblank_png() {
  local png="$1"
  python - "$png" <<'PY'
import sys
from PIL import Image

path = sys.argv[1]
img = Image.open(path).convert("RGB")
w, h = img.size

# Skip toolbar/status region; focus on the document content.
ystart = int(h * 0.12)

step = 3
nonwhite = 0
dark = 0
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
        if nonwhite > 1600 and dark > 220:
            break
    if nonwhite > 1600 and dark > 220:
        break

if nonwhite <= 1600 or dark <= 220:
    raise SystemExit(
        f"FAIL: render looks blank-ish: nonwhite={nonwhite}, dark={dark}, samples={samples}, size={w}x{h}"
    )

print(f"  OK: nonblank render (nonwhite>{nonwhite}, dark>{dark}, samples={samples}, size={w}x{h})")
PY
}

_count_search_highlight_pixels() {
  local png="$1"
  python - "$png" <<'PY'
import sys
from PIL import Image

path = sys.argv[1]
img = Image.open(path).convert("RGB")
w, h = img.size

# Skip toolbar; avoid action icons.
ystart = int(h * 0.14)
step = 2

count = 0
samples = 0

px = img.load()
for y in range(ystart, h, step):
    for x in range(0, w, step):
        r, g, b = px[x, y]
        samples += 1
        # Search highlight is a blue tint (#33B5E5-ish), sometimes blended.
        if b > 150 and (b - max(r, g)) > 25:
            count += 1

print(count)
PY
}

echo "[1/9] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/9] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/9] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[4/9] Push PDF fixture"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE" >/dev/null

echo "[5/9] Launch viewer with PDF"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

OUT_PREFIX="${OUT_PREFIX:-tmp_geny_pdf_text_search}"

echo "[6/9] Screenshot baseline + assert non-blank"
OUT_BEFORE="${OUT_BEFORE:-${OUT_PREFIX}_before.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_BEFORE"
echo "  wrote $OUT_BEFORE"
_assert_nonblank_png "$OUT_BEFORE"

before_blue="$(_count_search_highlight_pixels "$OUT_BEFORE")"
echo "  baseline blue-ish samples: $before_blue"

echo "[7/9] Search for \"$QUERY\""
uia_tap_desc "More options"
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/menu_search" || uia_tap_text_contains "Search" || { echo "FAIL: Search menu missing" >&2; exit 1; }
sleep 0.8
adb -s "$DEVICE" shell input text "$QUERY"
adb -s "$DEVICE" shell input keyevent 66

# Allow search to run and navigate to first result.
sleep 3

echo "[8/9] Screenshot after search + assert highlight appears"
OUT_AFTER="${OUT_AFTER:-${OUT_PREFIX}_after.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_AFTER"
echo "  wrote $OUT_AFTER"

after_blue="$(_count_search_highlight_pixels "$OUT_AFTER")"
echo "  after-search blue-ish samples: $after_blue"

if [[ "$after_blue" -le $((before_blue + 40)) ]]; then
  echo "FAIL: expected search highlight overlay to increase (before=$before_blue after=$after_blue)" >&2
  exit 1
fi

echo "[9/9] Logcat fatal check"
if adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" >/dev/null; then
  echo "FAIL: detected fatal logcat entries" >&2
  adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" | tail -n 40 >&2
  exit 1
fi

echo "PDF text+search smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
