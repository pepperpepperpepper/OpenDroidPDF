#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "FreeText background fill + opacity persists":
# - Open a writable PDF via file://
# - Create a FreeText comment with a known token
# - Set background fill color + opacity in the Style dialog
# - Save in-place and render the PDF
# - OCR the token and assert the pixels under the text are tinted (not white)
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_text_annot_background_smoke.sh
#
# Requirements (host):
#   - pdftoppm (poppler)
#   - tesseract

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_with_text.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_text_bg_smoke.pdf}
TOKEN=${TOKEN:-ODPTEXTBG%sFILL%sTEST}
TOKEN_INPUT=${TOKEN_INPUT:-$TOKEN}
TOKEN_EXPECTED=${TOKEN_EXPECTED:-${TOKEN//%s/ }}
TOKEN_SEARCH=${TOKEN_SEARCH:-${TOKEN_EXPECTED%% *}}
BG_COLOR_NAME=${BG_COLOR_NAME:-Yellow}
BG_OPACITY_PCT=${BG_OPACITY_PCT:-60}
OUT_PREFIX="${OUT_PREFIX:-tmp_geny_pdf_text_annot_bg}"

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

if ! command -v pdftoppm >/dev/null 2>&1; then
  echo "FAIL: pdftoppm not found (install poppler)." >&2
  exit 2
fi
if ! command -v tesseract >/dev/null 2>&1; then
  echo "FAIL: tesseract not found (install tesseract-ocr)." >&2
  exit 2
fi

_wm_size() {
  local line
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

_doc_center_xy() {
  local w h x y
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y=$((h * 45 / 100))
  echo "$x $y"
}

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p > "$out_png"
}

_uia_bounds_for_rid() {
  local rid="$1"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python3 - "$tmp" "$rid" <<'PY'
import re, sys, xml.etree.ElementTree as ET
xml_path, rid = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    b = node.attrib.get("bounds", "")
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    if not m:
        continue
    l, t, r, bt = map(int, m.groups())
    print(l, t, r, bt)
    raise SystemExit(0)
raise SystemExit(1)
PY
  rm -f "$tmp"
}

_drag_seekbar_pct() {
  local rid="$1"
  local pct="$2"
  local l t r b w x0 x1 y
  read -r l t r b < <(_uia_bounds_for_rid "$rid")
  w=$((r - l))
  if (( w <= 0 )); then
    echo "FAIL: seekbar bounds invalid for $rid" >&2
    return 1
  fi
  pct=$((pct < 0 ? 0 : (pct > 100 ? 100 : pct)))
  x0=$((l + 8))
  x1=$((l + (w * pct / 100)))
  y=$(((t + b) / 2))
  adb -s "$DEVICE" shell input swipe "$x0" "$y" "$x1" "$y" 320
}

_uia_tap_desc_lowest() {
  local desc="$1"
  local tmp coords
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  coords="$(python3 - "$tmp" "$desc" <<'PY'
import re, sys, xml.etree.ElementTree as ET
xml_path, desc = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)

def center(bounds: str):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    l, t, r, b = map(int, m.groups())
    return (l + r) // 2, (t + b) // 2

best = None
best_y = -1
for node in tree.iter("node"):
    if node.attrib.get("content-desc", "") != desc:
        continue
    c = center(node.attrib.get("bounds", ""))
    if not c:
        continue
    x, y = c
    if y > best_y:
        best_y = y
        best = (x, y)

if best is None:
    raise SystemExit(1)
print(f"{best[0]} {best[1]}")
PY
)"
  rm -f "$tmp"
  if [[ -z "$coords" ]]; then
    return 1
  fi
  set -- $coords
  adb -s "$DEVICE" shell input tap "$1" "$2"
}

_ocr_token_center_xy() {
  local png="$1"
  local token="$2"
  tesseract "$png" stdout -l eng --psm 6 tsv 2>/dev/null \
    | awk -F'\t' -v tok="$token" 'NR>1 && $1==5 && index($12,tok)>0 { printf "%d %d\n", ($7 + int($9/2)), ($8 + int($10/2)); found=1; exit } END { exit found?0:1 }'
}

_wait_for_token_center_xy() {
  local token="$1"
  local timeout_s="${2:-12}"
  local start now
  start="$(date +%s)"
  while true; do
    _screencap_png "${OUT_PREFIX}_onscreen.png"
    if coords="$(_ocr_token_center_xy "${OUT_PREFIX}_onscreen.png" "$token" 2>/dev/null)"; then
      printf '%s\n' "$coords"
      return 0
    fi
    now="$(date +%s)"
    if (( now - start >= timeout_s )); then
      break
    fi
    sleep 0.4
  done
  return 1
}

_render_pdf_to_png() {
  local pdf="$1"
  local out_png="$2"
  local tmpdir prefix
  tmpdir="$(mktemp -d -t odp_pdf_render_XXXXXX)"
  prefix="$tmpdir/out"
  pdftoppm -f 1 -l 1 -r 300 -singlefile -png "$pdf" "$prefix" >/dev/null
  mv -f -- "${prefix}.png" "$out_png"
  rm -rf -- "$tmpdir"
}

_bbox_from_pdf_rect() {
  local pdf="$1"
  local token="$2"
  local png="$3"
  python3 - "$pdf" "$token" "$png" <<'PY'
import re
import sys
from PIL import Image

pdf_path, token, png_path = sys.argv[1], sys.argv[2], sys.argv[3]

with open(pdf_path, "rb") as f:
    data = f.read().decode("latin1", "ignore")

mediabox = None
for m in re.finditer(r"/MediaBox\s*\[([^\]]+)\]", data):
    try:
        vals = [float(x) for x in m.group(1).strip().split()[:4]]
    except Exception:
        continue
    if len(vals) == 4:
        mediabox = vals
        break

if not mediabox:
    raise SystemExit(1)

mb_x0, mb_y0, mb_x1, mb_y1 = mediabox
page_w = mb_x1 - mb_x0
page_h = mb_y1 - mb_y0
if page_w <= 0 or page_h <= 0:
    raise SystemExit(1)

rect = None
pat = re.compile(r"/Subtype\s*/FreeText\b.*?/Rect\s*\[([^\]]+)\].*?/Contents\s*\(([^)]*)\)", re.S)
for m in pat.finditer(data):
    contents = m.group(2)
    if token not in contents:
        continue
    try:
        vals = [float(x) for x in m.group(1).strip().split()[:4]]
    except Exception:
        continue
    if len(vals) == 4:
        rect = vals
        break

if not rect:
    raise SystemExit(1)

x0, y0, x1, y1 = rect

im = Image.open(png_path)
w, h = im.size
sx = w / page_w
sy = h / page_h

px0 = int((x0 - mb_x0) * sx)
px1 = int((x1 - mb_x0) * sx)
py0 = int((mb_y1 - y1) * sy)
py1 = int((mb_y1 - y0) * sy)

if px1 <= px0 or py1 <= py0:
    raise SystemExit(1)

print(px0, py0, px1, py1)
PY
}

_ocr_token_bbox_xyxy() {
  local png="$1"
  local token="$2"
  tesseract "$png" stdout -l eng --psm 6 tsv 2>/dev/null \
    | awk -F'\t' -v tok="$token" 'NR>1 && $1==5 && index($12,tok)>0 { printf "%d %d %d %d\n", $7, $8, ($7 + $9), ($8 + $10); found=1; exit } END { exit found?0:1 }'
}

_assert_tinted_background_in_bbox() {
  local png="$1"
  local x0="$2"
  local y0="$3"
  local x1="$4"
  local y1="$5"
  python3 - "$png" "$x0" "$y0" "$x1" "$y1" "$BG_OPACITY_PCT" <<'PY'
from PIL import Image
import sys

png, x0, y0, x1, y1, pct = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])
im = Image.open(png).convert("RGBA")
w, h = im.size

pad = 6
x0 = max(0, min(w - 1, x0 - pad))
y0 = max(0, min(h - 1, y0 - pad))
x1 = max(1, min(w, x1 + pad))
y1 = max(1, min(h, y1 + pad))

crop = im.crop((x0, y0, x1, y1))
px = crop.load()
cw, ch = crop.size

sum_r = sum_g = sum_b = 0
count = 0

for y in range(ch):
    for x in range(cw):
        r, g, b, a = px[x, y]
        if a < 200:
            continue
        # Ignore near-black text pixels.
        if r < 80 and g < 80 and b < 80:
            continue
        sum_r += r
        sum_g += g
        sum_b += b
        count += 1

if count < 60:
    print(f"FAIL: insufficient bright pixels in token bbox (count={count})", file=sys.stderr)
    raise SystemExit(1)

avg_r = sum_r / count
avg_g = sum_g / count
avg_b = sum_b / count
print(f"avg_rgb=({avg_r:.1f},{avg_g:.1f},{avg_b:.1f}) bright_px={count}")

# For a yellow-ish fill blended onto white paper:
# - R/G should stay high
# - B should drop from 255 (white) but not be near 0 (full opaque yellow) when opacity < 100%
if not (avg_r >= 220 and avg_g >= 210 and avg_r >= avg_b + 20 and avg_b <= 230):
    print("FAIL: expected tinted (non-white) background under token text", file=sys.stderr)
    raise SystemExit(1)
if pct < 90 and avg_b < 30:
    print("FAIL: opacity appears ignored (background looks fully opaque)", file=sys.stderr)
    raise SystemExit(1)
PY
}

echo "[1/9] Install debug APK"
if ! adb -s "$DEVICE" install -r "$APK" >/dev/null; then
  echo "  install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
  adb -s "$DEVICE" install "$APK" >/dev/null
fi

echo "[2/9] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/9] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[4/9] Push fixture PDF"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null

echo "[5/9] Launch viewer with file:// PDF"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE_PATH" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[6/9] Add FreeText token"
uia_enter_add_text_mode || {
  echo "FAIL: could not enter Add text mode" >&2
  exit 1
}
sleep 0.6
read -r cx cy < <(_doc_center_xy)
adb -s "$DEVICE" shell input tap "$cx" "$cy"

for _ in $(seq 1 25); do
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    break
  fi
  sleep 0.25
done
uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || { echo "FAIL: text input not shown" >&2; exit 1; }
adb -s "$DEVICE" shell input text "$TOKEN_INPUT"
sleep 0.2
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || { echo "FAIL: could not confirm text dialog" >&2; exit 1; }
sleep 1.0

echo "[7/9] Apply background fill + opacity (Style dialog)"
# Find the token on-screen and use that for reliable selection taps.
if ! read -r tx ty < <(_wait_for_token_center_xy "$TOKEN_SEARCH" 14); then
  echo "FAIL: could not locate token '$TOKEN_SEARCH' on-screen to select it (wrote ${OUT_PREFIX}_onscreen.png)" >&2
  exit 1
fi

# Select the annotation and enter edit-selected-annotation mode so the Text style action is available.
adb -s "$DEVICE" shell input tap "$tx" "$ty"
sleep 0.35
adb -s "$DEVICE" shell input tap "$tx" "$ty"
sleep 0.9
# If we opened the edit dialog, accept without changing text so we stay in "edit selected annotation" mode.
if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
  sleep 0.8
fi

uia_tap_any_res_id "org.opendroidpdf:id/menu_text_style" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  uia_tap_text_contains "Style" || {
    fail_xml="${OUT_PREFIX}_style_fail.xml"
    fail_png="${OUT_PREFIX}_style_fail.png"
    _uia_dump_to "$fail_xml" || true
    adb -s "$DEVICE" exec-out screencap -p >"$fail_png" || true
    echo "FAIL: could not open text style dialog (wrote $fail_xml and $fail_png)" >&2
    exit 1
  }
}
sleep 0.8

_drag_seekbar_pct "org.opendroidpdf:id/text_style_background_opacity_seekbar" "$BG_OPACITY_PCT" || {
  echo "FAIL: could not adjust background opacity seekbar" >&2
  exit 1
}
sleep 0.4

desc="Set ink color to ${BG_COLOR_NAME}"
_uia_tap_desc_lowest "$desc" || {
  echo "FAIL: could not tap background fill swatch ($desc)" >&2
  exit 1
}
sleep 0.6

adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
sleep 0.9

# Return to main so Save is accessible.
uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || true
sleep 0.8

echo "[8/9] Save in-place and pull PDF"
uia_save_changes || { echo "FAIL: Save changes entry point missing" >&2; exit 1; }
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 3.5

OUT_PREFIX="${OUT_PREFIX:-tmp_geny_pdf_text_annot_bg}"
SAVED_PDF="${SAVED_PDF:-${OUT_PREFIX}.pdf}"
adb -s "$DEVICE" pull "$PDF_REMOTE_PATH" "$SAVED_PDF" >/dev/null
echo "  wrote $SAVED_PDF"

echo "[9/9] Render + OCR token bbox + assert tinted fill"
RENDER_PNG="${RENDER_PNG:-${OUT_PREFIX}_render.png}"
_render_pdf_to_png "$SAVED_PDF" "$RENDER_PNG"
echo "  wrote $RENDER_PNG"

if read -r x0 y0 x1 y1 < <(_bbox_from_pdf_rect "$SAVED_PDF" "$TOKEN_SEARCH" "$RENDER_PNG"); then
  :
elif read -r x0 y0 x1 y1 < <(_ocr_token_bbox_xyxy "$RENDER_PNG" "$TOKEN_SEARCH"); then
  :
else
  echo "FAIL: could not locate token '$TOKEN_SEARCH' in rendered output (PDF /Rect and OCR both failed)" >&2
  echo "OCR raw:" >&2
  tesseract "$RENDER_PNG" stdout -l eng --psm 6 2>/dev/null | tr -d '\f' | tr -d '\r' | head -n 30 >&2 || true
  exit 1
fi

_assert_tinted_background_in_bbox "$RENDER_PNG" "$x0" "$y0" "$x1" "$y1"
echo "OK: background fill is visible under token text (color=$BG_COLOR_NAME opacity=${BG_OPACITY_PCT}%)"
