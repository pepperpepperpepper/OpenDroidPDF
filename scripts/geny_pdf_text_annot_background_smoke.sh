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
PDF_LOCAL=${PDF_LOCAL:-test_pdf.pdf}
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

_selection_box_bbox_px() {
  # Returns a bbox around the cyan selection outline/handles (x0 y0 x1 y1), or empty on failure.
  local png="$1"
  python3 - "$png" <<'PY'
from PIL import Image
import sys

im = Image.open(sys.argv[1]).convert("RGBA")
w, h = im.size
px = im.load()

minx = miny = None
maxx = maxy = None

for y in range(h):
    for x in range(w):
        r, g, b, a = px[x, y]
        if a < 200:
            continue
        # Selection box/handles are drawn in a light blue/cyan tint.
        if b > 150 and g > 100 and r < 210 and b > r + 20:
            minx = x if minx is None else min(minx, x)
            miny = y if miny is None else min(miny, y)
            maxx = x if maxx is None else max(maxx, x)
            maxy = y if maxy is None else max(maxy, y)

if minx is None:
    print("")
else:
    print(f"{minx} {miny} {maxx} {maxy}")
PY
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

_scroll_dialog_down() {
  # Swipe up inside the visible dialog to reveal lower sections.
  local w h x y1 y2 dur
  dur="${1:-420}"
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y1=$((h * 70 / 100))
  y2=$((h * 30 / 100))
  adb -s "$DEVICE" shell input swipe "$x" "$y1" "$x" "$y2" "$dur"
}

_scroll_dialog_until_rid_visible() {
  local rid="$1"
  local max_swipes="${2:-10}"
  local w h l t r b cy
  read -r w h < <(_wm_size)
  for _ in $(seq 1 "$max_swipes"); do
    if read -r l t r b < <(_uia_bounds_for_rid "$rid" 2>/dev/null); then
      cy=$(((t + b) / 2))
      if (( cy > h / 8 && cy < h * 7 / 8 )); then
        return 0
      fi
    fi
    _scroll_dialog_down
    sleep 0.35
  done
  return 1
}

_scroll_dialog_until_desc_tap_lowest() {
  local desc="$1"
  local max_swipes="${2:-10}"
  for _ in $(seq 1 "$max_swipes"); do
    if _uia_tap_desc_lowest "$desc"; then
      return 0
    fi
    _scroll_dialog_down
    sleep 0.35
  done
  return 1
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
  read -r w h < <(_wm_size)
  coords="$(python3 - "$tmp" "$desc" "$w" "$h" <<'PY'
import re, sys, xml.etree.ElementTree as ET
xml_path, desc, w, h = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
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
    if x < 0 or y < 0 or x > w or y > h:
        continue
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

_find_dark_bar_bounds_xyxy() {
  # Heuristic detector for the text-annotation quick-actions bar.
  # UIAutomator does not reliably expose the PopupWindow accessibility tree on some devices,
  # so we locate the dark rounded-rect bar in a screenshot and tap by coordinates.
  local png="$1"
  python3 - "$png" <<'PY'
from PIL import Image
import sys

png = sys.argv[1]
im = Image.open(png).convert("RGB")
w, h = im.size
px = im.load()

# Exclude the app bar / nav bar regions.
y_min = int(h * 0.15)
y_max = int(h * 0.85)

def is_dark(r, g, b):
    return r < 90 and g < 90 and b < 90

thr = int(w * 0.35)

segments = []
seg_y0 = None
seg_y1 = None
seg_xs = []
for y in range(y_min, y_max):
    xs = []
    for x in range(w):
        r, g, b = px[x, y]
        if is_dark(r, g, b):
            xs.append(x)
    if len(xs) >= thr:
        if seg_y0 is None:
            seg_y0 = y
        seg_y1 = y
        seg_xs.extend(xs)
    else:
        if seg_y0 is not None:
            segments.append((seg_y0, seg_y1, seg_xs))
            seg_y0 = None
            seg_y1 = None
            seg_xs = []
if seg_y0 is not None:
    segments.append((seg_y0, seg_y1, seg_xs))

cands = []
for (top, bot, xs) in segments:
    height = bot - top + 1
    if not xs:
        continue
    xs.sort()
    # Use percentiles to avoid stray dark pixels from the underlying document content
    # widening our bbox (the quick-actions bar itself is a dense dark region).
    l_idx = int(len(xs) * 0.05)
    r_idx = int(len(xs) * 0.95)
    l_idx = max(0, min(len(xs) - 1, l_idx))
    r_idx = max(0, min(len(xs) - 1, r_idx))
    minx = xs[l_idx]
    maxx = xs[r_idx]
    width = maxx - minx + 1
    cy = (top + bot) / 2
    if height < 20 or height > 120:
        continue
    if width < w * 0.5:
        continue
    if cy < h * 0.2 or cy > h * 0.8:
        continue
    cands.append((width * height, minx, top, maxx, bot))

if not cands:
    raise SystemExit(1)

cands.sort(reverse=True)
_, l, t, r, b = cands[0]
print(l, t, r, b)
PY
}

_ensure_quick_actions_visible() {
  # Selecting a note via the annotations list does not always show the quick-actions popup.
  # A single tap within the selection box reliably brings it up.
  local tmp_png sel
  tmp_png="$(mktemp --suffix=.png)"
  _screencap_png "$tmp_png"
  sel="$(_selection_box_bbox_px "$tmp_png" || true)"
  rm -f "$tmp_png"

  if [[ -z "$sel" ]]; then
    return 1
  fi

  local x0 y0 x1 y1 tap_x tap_y
  read -r x0 y0 x1 y1 <<<"$sel"
  tap_x=$((x0 + (x1 - x0) / 2))
  tap_y=$((y0 + (y1 - y0) / 2))
  adb -s "$DEVICE" shell input tap "$tap_x" "$tap_y"
  sleep 0.35
  return 0
}

_tap_quick_actions_properties() {
  local tries="${1:-8}"
  local out_png l t r b x y w density off_px
  out_png="${OUT_PREFIX}_quick_actions.png"
  for _ in $(seq 1 "$tries"); do
    _screencap_png "$out_png"
    if read -r l t r b < <(_find_dark_bar_bounds_xyxy "$out_png" 2>/dev/null); then
      w=$((r - l))
      # Tap the left-most button (Properties). Compute a dp-based offset so we land
      # near the center of the first 36dp ImageButton inside the 6dp padded container.
      density="$(adb -s "$DEVICE" shell wm density 2>/dev/null | tr -d '\r' | rg -o '[0-9]+' | head -n 1 || true)"
      if [[ -z "${density:-}" ]]; then
        density="$(adb -s "$DEVICE" shell getprop ro.sf.lcd_density 2>/dev/null | tr -d '\r' | rg -o '[0-9]+' | head -n 1 || true)"
      fi
      if [[ -n "${density:-}" ]]; then
        # (6dp padding + 18dp half button) * density/160 = 24dp * density/160.
        off_px=$((density * 3 / 20))
      else
        off_px=33
      fi
      x=$((l + off_px))
      if (( x >= r )); then
        x=$(((l + r) / 2))
      fi
      y=$(((t + b) / 2))
      adb -s "$DEVICE" shell input tap "$x" "$y"
      return 0
    fi
    sleep 0.35
  done
  return 1
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
# Some builds use an inline editor instead of an OK/Cancel dialog. Support both.
if uia_has_res_id "android:id/button1" "com.android.internal:id/button1"; then
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
    echo "FAIL: could not confirm text dialog" >&2
    exit 1
  }
else
  read -r w h < <(_wm_size)
  blank_x=$((w * 9 / 10))
  blank_y=$((h / 5))
  adb -s "$DEVICE" shell input tap "$blank_x" "$blank_y"
  for _ in $(seq 1 20); do
    if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      break
    fi
    sleep 0.25
  done
fi
sleep 1.0

echo "[7/9] Apply background fill + opacity (Style dialog)"
opened_style=0
style_dialog_marker_rid="org.opendroidpdf:id/text_style_summary"
bg_opacity_seekbar_rid="org.opendroidpdf:id/text_style_background_opacity_seekbar"
bg_color_label_rid="org.opendroidpdf:id/text_style_background_color_label"

echo "  selecting annotation via Annotations list"
uia_open_annotations_list || { echo "FAIL: could not open annotations list" >&2; exit 1; }
sleep 0.8
uia_tap_text_contains "$TOKEN_SEARCH" || uia_tap_text_contains "$TOKEN_EXPECTED" || {
  echo "FAIL: could not select annotation in annotations list (token=$TOKEN_SEARCH)" >&2
  exit 1
}
sleep 1.2

# Prefer the top-bar edit action when available (reliable; avoids debug overflow).
uia_tap_any_res_id "org.opendroidpdf:id/menu_text_style" || true
sleep 0.8

for _ in $(seq 1 18); do
  if uia_has_res_id "$style_dialog_marker_rid"; then
    opened_style=1
    break
  fi
  # Fall back to the contextual quick-actions toolbar (PopupWindow); UIAutomator may not expose it.
  if uia_tap_any_res_id "org.opendroidpdf:id/text_quick_actions_properties"; then
    sleep 0.6
  else
    _ensure_quick_actions_visible || true
    if _tap_quick_actions_properties; then
      sleep 0.6
    else
      # Do not open the global overflow menu here: in debug builds it contains debug actions, not Style.
      sleep 0.35
    fi
  fi
done

if (( opened_style == 0 )); then
  fail_xml="${OUT_PREFIX}_style_fail.xml"
  fail_png="${OUT_PREFIX}_style_fail.png"
  _uia_dump_to "$fail_xml" || true
  adb -s "$DEVICE" exec-out screencap -p >"$fail_png" || true
  echo "FAIL: could not open text style dialog (wrote $fail_xml and $fail_png)" >&2
  exit 1
fi

if ! _scroll_dialog_until_rid_visible "$bg_opacity_seekbar_rid" 12; then
  fail_xml="${OUT_PREFIX}_bg_seekbar_fail.xml"
  fail_png="${OUT_PREFIX}_bg_seekbar_fail.png"
  _uia_dump_to "$fail_xml" || true
  adb -s "$DEVICE" exec-out screencap -p >"$fail_png" || true
  echo "FAIL: could not find background opacity seekbar (wrote $fail_xml and $fail_png)" >&2
  exit 1
fi

_drag_seekbar_pct "$bg_opacity_seekbar_rid" "$BG_OPACITY_PCT" || {
  echo "FAIL: could not adjust background opacity seekbar" >&2
  exit 1
}
sleep 0.4

desc="Set ink color to ${BG_COLOR_NAME}"
if ! _scroll_dialog_until_rid_visible "$bg_color_label_rid" 10; then
  echo "FAIL: could not scroll to background fill color label" >&2
  exit 1
fi
if ! _scroll_dialog_until_desc_tap_lowest "$desc" 8; then
  echo "FAIL: could not tap background fill swatch ($desc)" >&2
  exit 1
fi
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
