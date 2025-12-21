#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "writable PDF Save embeds ink into the PDF file":
# - Open a writable PDF from app-owned external storage
# - Draw a stroke and accept (commit)
# - Tap Save (in-place)
# - Pull the PDF back to host and render it with Poppler (pdftoppm)
# - Assert the rendered output differs from the baseline (ink is visible outside the app)
#
# Usage:
#   DEVICE=localhost:42865 APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_save_embeds_ink_smoke.sh

DEVICE=${DEVICE:-localhost:42865}
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_blank.pdf}
PDF_REMOTE_DIR=${PDF_REMOTE_DIR:-/sdcard/Android/data/org.opendroidpdf/files}
PDF_REMOTE=${PDF_REMOTE:-$PDF_REMOTE_DIR/odp_writable_save.pdf}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_wm_size() {
  local line
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

_draw_swipe() {
  local w h x1 y1 x2 y2 dur
  read -r w h < <(_wm_size)
  x1=$((w * 2 / 10))
  x2=$((w * 8 / 10))
  y1=$((h * 55 / 100))
  y2=$((h * 58 / 100))
  dur=${1:-280}
  adb -s "$DEVICE" shell input swipe "$x1" "$y1" "$x2" "$y2" "$dur"
}

_render_pdf_to_png() {
  local pdf="$1"
  local out_png="$2"
  local tmpdir prefix
  tmpdir="$(mktemp -d -t odp_pdf_render_XXXXXX)"
  prefix="$tmpdir/out"
  pdftoppm -f 1 -l 1 -r 140 -singlefile -png "$pdf" "$prefix" >/dev/null
  mv -f -- "${prefix}.png" "$out_png"
  rm -rf -- "$tmpdir"
}

_assert_renders_differ() {
  local before="$1"
  local after="$2"
  python - "$before" "$after" <<'PY'
import sys
from PIL import Image

before_path, after_path = sys.argv[1], sys.argv[2]
a = Image.open(before_path).convert("RGB")
b = Image.open(after_path).convert("RGB")
if a.size != b.size:
    raise SystemExit(f"FAIL: render size mismatch: {a.size} vs {b.size}")

w, h = a.size
ystart = int(h * 0.12)

pa = a.load()
pb = b.load()

step = 3
changed = 0
new_dark = 0
threshold = 90  # sum(|dr|,|dg|,|db|)
need = 120
need_dark = 35

for y in range(ystart, h, step):
    for x in range(0, w, step):
        r1, g1, b1 = pa[x, y]
        r2, g2, b2 = pb[x, y]
        if abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2) > threshold:
            changed += 1
        if (r1 >= 245 and g1 >= 245 and b1 >= 245) and (r2 < 80 and g2 < 80 and b2 < 80):
            new_dark += 1
        if changed > need and new_dark > need_dark:
                break
    if changed > need and new_dark > need_dark:
        break

if changed <= need or new_dark <= need_dark:
    raise SystemExit(
        f"FAIL: saved-PDF render didn't show embedded marks (changed={changed}, need>{need}, new_dark={new_dark}, need_dark>{need_dark}, size={w}x{h})"
    )

print(f"OK: saved-PDF render shows embedded marks (changed={changed}, new_dark={new_dark}, size={w}x{h})")
PY
}

echo "[1/9] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/9] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/9] Prepare writable PDF path + push fixture"
adb -s "$DEVICE" shell mkdir -p "$PDF_REMOTE_DIR" >/dev/null
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE" >/dev/null

echo "[4/9] Launch viewer with writable PDF"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[5/9] Draw + accept"
uia_tap_any_res_id "org.opendroidpdf:id/draw_image_button" "org.opendroidpdf:id/menu_draw"
sleep 0.4
_draw_swipe 300
sleep 0.5
uia_tap_any_res_id "org.opendroidpdf:id/accept_image_button" "org.opendroidpdf:id/menu_accept" || {
  echo "FAIL: accept button not found after drawing" >&2
  exit 1
}
sleep 1.2

echo "[6/9] Save in-place"
if uia_tap_desc "More options"; then
  sleep 0.4
fi
uia_tap_any_res_id "org.opendroidpdf:id/menu_save" || uia_tap_text_contains "Save" || {
  echo "FAIL: Save menu item not found" >&2
  exit 1
}
# Some builds show a confirmation dialog titled "Save". If present, tap the positive button.
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 4

echo "[7/9] Pull saved PDF back to host"
OUT_PREFIX="${OUT_PREFIX:-tmp_geny_pdf_save_embed}"
SAVED_PDF="${SAVED_PDF:-${OUT_PREFIX}.pdf}"
adb -s "$DEVICE" pull "$PDF_REMOTE" "$SAVED_PDF" >/dev/null
echo "  wrote $SAVED_PDF"

echo "[8/9] Render baseline + saved and assert ink is visible (external render)"
BASE_PNG="${BASE_PNG:-${OUT_PREFIX}_before.png}"
AFTER_PNG="${AFTER_PNG:-${OUT_PREFIX}_after.png}"
_render_pdf_to_png "$PDF_LOCAL" "$BASE_PNG"
_render_pdf_to_png "$SAVED_PDF" "$AFTER_PNG"
echo "  wrote $BASE_PNG"
echo "  wrote $AFTER_PNG"
_assert_renders_differ "$BASE_PNG" "$AFTER_PNG"

echo "[9/9] Logcat fatal check"
if adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" >/dev/null; then
  echo "FAIL: detected fatal logcat entries" >&2
  adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" | tail -n 40 >&2
  exit 1
fi

echo "PDF Save embed smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 120
