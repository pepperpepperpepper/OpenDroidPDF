#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "Sidecar note background fill + opacity":
# - Open a fixture EPUB via file:// (sidecar-backed session)
# - Create a text note with a known token
# - Set background fill color + opacity in the Style dialog
# - Assert the on-screen selection box area is tinted
# - Export sidecar DB and assert bg_color/bg_opacity persisted
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_note_background_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/hello.epub}
EPUB_REMOTE_PATH=${EPUB_REMOTE_PATH:-/sdcard/Download/odp_note_bg_smoke.epub}
TOKEN=${TOKEN:-ODP_NOTE_BG}
BG_COLOR_NAME=${BG_COLOR_NAME:-Yellow}
BG_OPACITY_PCT=${BG_OPACITY_PCT:-60}
BORDER_COLOR_NAME=${BORDER_COLOR_NAME:-Red}
BORDER_WIDTH_PCT=${BORDER_WIDTH_PCT:-65}
BORDER_RADIUS_PCT=${BORDER_RADIUS_PCT:-55}

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

_scroll_dialog_down() {
  local w h x y1 y2
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y1=$((h * 80 / 100))
  y2=$((h * 25 / 100))
  adb -s "$DEVICE" shell input swipe "$x" "$y1" "$x" "$y2" 320
}

_selection_box_bbox_px() {
  local png="$1"
  python3 - "$png" <<'PY'
from PIL import Image
import sys

im = Image.open(sys.argv[1]).convert("RGBA")
w, h = im.size
px = im.load()

minx = None
miny = None
maxx = None
maxy = None

for y in range(h):
  for x in range(w):
    r, g, b, a = px[x, y]
    if a < 200:
      continue
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

_assert_tinted_background_in_selection_bbox() {
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

pad = 12
x0 = max(0, min(w - 1, x0 + pad))
y0 = max(0, min(h - 1, y0 + pad))
x1 = max(1, min(w, x1 - pad))
y1 = max(1, min(h, y1 - pad))

crop = im.crop((x0, y0, x1, y1))
px = crop.load()
cw, ch = crop.size

sum_r = sum_g = sum_b = 0
count = 0

def is_blue_handle(r, g, b, a):
    return a >= 200 and b > 150 and g > 100 and r < 210 and b > r + 20

for y in range(ch):
    for x in range(cw):
        r, g, b, a = px[x, y]
        if a < 200:
            continue
        if is_blue_handle(r, g, b, a):
            continue
        if r < 80 and g < 80 and b < 80:
            continue
        sum_r += r
        sum_g += g
        sum_b += b
        count += 1

if count < 200:
    print(f"FAIL: insufficient bright pixels in selection bbox (count={count})", file=sys.stderr)
    raise SystemExit(1)

avg_r = sum_r / count
avg_g = sum_g / count
avg_b = sum_b / count
print(f"avg_rgb=({avg_r:.1f},{avg_g:.1f},{avg_b:.1f}) bright_px={count}")

if not (avg_r >= 220 and avg_g >= 210 and avg_r >= avg_b + 20 and avg_b <= 230):
    print("FAIL: expected tinted (non-white) background inside note selection bbox", file=sys.stderr)
    raise SystemExit(1)
if pct < 90 and avg_b < 30:
    print("FAIL: opacity appears ignored (background looks fully opaque)", file=sys.stderr)
    raise SystemExit(1)
PY
}

_assert_redish_border_in_selection_bbox() {
  local png="$1"
  local x0="$2"
  local y0="$3"
  local x1="$4"
  local y1="$5"
  python3 - "$png" "$x0" "$y0" "$x1" "$y1" <<'PY'
from PIL import Image
import sys

png, x0, y0, x1, y1 = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
im = Image.open(png).convert("RGBA")
w, h = im.size

# Focus on the interior band so the blue selection stroke doesn't dominate, but still keep enough
# area that a thick border will be visible.
pad = 8
x0 = max(0, min(w - 1, x0 + pad))
y0 = max(0, min(h - 1, y0 + pad))
x1 = max(1, min(w, x1 - pad))
y1 = max(1, min(h, y1 - pad))

crop = im.crop((x0, y0, x1, y1))
px = crop.load()
cw, ch = crop.size

def is_redish(r, g, b, a):
    return a >= 200 and r > 200 and g < 90 and b < 90

def is_blue_handle(r, g, b, a):
    return a >= 200 and b > 150 and g > 100 and r < 210 and b > r + 20

count = 0
for y in range(ch):
    for x in range(cw):
        r, g, b, a = px[x, y]
        if a < 200:
            continue
        if is_blue_handle(r, g, b, a):
            continue
        if is_redish(r, g, b, a):
            count += 1

print(f"redish_px={count}")
if count < 250:
    print("FAIL: expected red-ish border pixels inside note selection bbox", file=sys.stderr)
    raise SystemExit(1)
PY
}

_export_sidecar_db() {
  local out="$1"
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db >"$out"
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db-wal >"${out}-wal" 2>/dev/null || true
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db-shm >"${out}-shm" 2>/dev/null || true
}

_assert_db_has_style_fields() {
  local db="$1"
  local token="$2"
  local want_color_hex="$3"
  local want_opacity01="$4"
  local want_border_hex="$5"
  local want_lock_position_size="${6:-0}"
  local want_lock_contents="${7:-0}"
  python3 - "$db" "$token" "$want_color_hex" "$want_opacity01" "$want_border_hex" "$want_lock_position_size" "$want_lock_contents" <<'PY'
import sqlite3, sys

db, token = sys.argv[1], sys.argv[2]
want_hex = int(sys.argv[3], 16)
want_opacity = float(sys.argv[4])
want_border_hex = int(sys.argv[5], 16)
want_lock_pos = int(sys.argv[6])
want_lock_contents = int(sys.argv[7])
conn = sqlite3.connect(db)
try:
    cur = conn.execute(
        "select bg_color, bg_opacity, border_color, border_width_pt, border_style, border_radius_pt, lock_position_size, lock_contents from notes where text like ? order by created_at_ms desc limit 1",
        (f"%{token}%",),
    )
    row = cur.fetchone()
    if not row:
        print(f"FAIL: no note row found for token {token}", file=sys.stderr)
        raise SystemExit(1)
    bg_color, bg_opacity, border_color, border_width_pt, border_style, border_radius_pt, lock_position_size, lock_contents = row
    print(f"db.bg_color=0x{bg_color & 0xFFFFFFFF:08X} bg_opacity={bg_opacity} border_color=0x{border_color & 0xFFFFFFFF:08X} border_width_pt={border_width_pt} border_style={border_style} border_radius_pt={border_radius_pt} lock_position_size={lock_position_size} lock_contents={lock_contents}")
    if (bg_color & 0xFFFFFFFF) != (want_hex & 0xFFFFFFFF):
        print("FAIL: bg_color mismatch", file=sys.stderr)
        raise SystemExit(1)
    if abs(float(bg_opacity) - want_opacity) > 0.05:
        print("FAIL: bg_opacity mismatch", file=sys.stderr)
        raise SystemExit(1)
    if want_border_hex and (border_color & 0xFFFFFFFF) != (want_border_hex & 0xFFFFFFFF):
        print("FAIL: border_color mismatch", file=sys.stderr)
        raise SystemExit(1)
    if float(border_width_pt) < 4.0:
        print("FAIL: border_width_pt too small (expected >= 4)", file=sys.stderr)
        raise SystemExit(1)
    if int(border_style) != 1:
        print("FAIL: border_style mismatch (expected dashed=1)", file=sys.stderr)
        raise SystemExit(1)
    if float(border_radius_pt) < 2.0:
        print("FAIL: border_radius_pt too small (expected >= 2)", file=sys.stderr)
        raise SystemExit(1)
    if int(lock_position_size) != int(want_lock_pos):
        print("FAIL: lock_position_size mismatch", file=sys.stderr)
        raise SystemExit(1)
    if int(lock_contents) != int(want_lock_contents):
        print("FAIL: lock_contents mismatch", file=sys.stderr)
        raise SystemExit(1)
finally:
    conn.close()
PY
}

_note_bounds_from_db() {
  local db="$1"
  local token="$2"
  python3 - "$db" "$token" <<'PY'
import sqlite3, sys

db, token = sys.argv[1], sys.argv[2]
conn = sqlite3.connect(db)
try:
    cur = conn.execute(
        "select left, top, right, bottom from notes where text like ? order by created_at_ms desc limit 1",
        (f"%{token}%",),
    )
    row = cur.fetchone()
    if not row:
        raise SystemExit(1)
    l, t, r, b = row
    print(f"{float(l)} {float(t)} {float(r)} {float(b)}")
finally:
    conn.close()
PY
}

echo "[1/10] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/10] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/10] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[4/10] Push fixture EPUB"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE_PATH" >/dev/null

echo "[5/10] Launch viewer with file:// EPUB"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE_PATH" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[6/10] Create a text note"
uia_tap_any_res_id "org.opendroidpdf:id/menu_add_text_annot" || uia_tap_desc "Add text" || {
  echo "FAIL: could not enter Add text mode" >&2
  exit 1
}
sleep 0.5
read -r cx cy < <(_doc_center_xy)
adb -s "$DEVICE" shell input tap "$cx" "$cy"

for _ in $(seq 1 25); do
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    break
  fi
  sleep 0.25
done
uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || { echo "FAIL: text input not shown" >&2; exit 1; }
adb -s "$DEVICE" shell input text "$TOKEN"
sleep 0.2
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || { echo "FAIL: could not confirm text dialog" >&2; exit 1; }
sleep 1.0

echo "[7/10] Apply background fill + opacity (Style dialog) and assert on-screen tint"
adb -s "$DEVICE" shell input tap "$cx" "$cy"
sleep 0.8

uia_tap_any_res_id "org.opendroidpdf:id/menu_text_style" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  uia_tap_text_contains "Style" || {
    echo "FAIL: could not open text style dialog" >&2
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

OUT_PREFIX="${OUT_PREFIX:-tmp_geny_epub_note_bg}"
OUT_PNG="${OUT_PNG:-${OUT_PREFIX}_screen.png}"
_screencap_png "$OUT_PNG"
echo "  wrote $OUT_PNG"

if ! read -r x0 y0 x1 y1 < <(_selection_box_bbox_px "$OUT_PNG"); then
  echo "FAIL: could not detect selection bbox for note (expected blue selection box)" >&2
  exit 1
fi
_assert_tinted_background_in_selection_bbox "$OUT_PNG" "$x0" "$y0" "$x1" "$y1"

echo "[8/10] Apply border settings + locks (Style dialog) and assert red-ish border pixels"
adb -s "$DEVICE" shell input tap "$cx" "$cy"
sleep 0.8
uia_tap_any_res_id "org.opendroidpdf:id/menu_text_style" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  uia_tap_text_contains "Style" || {
    echo "FAIL: could not open text style dialog (border step)" >&2
    exit 1
  }
}
sleep 0.8

# Border controls live below background controls; scroll down to ensure they are on-screen.
_scroll_dialog_down
sleep 0.4
_scroll_dialog_down
sleep 0.4

_drag_seekbar_pct "org.opendroidpdf:id/text_style_border_width_seekbar" "$BORDER_WIDTH_PCT" || {
  echo "FAIL: could not adjust border width seekbar" >&2
  exit 1
}
sleep 0.4

uia_tap_any_res_id "org.opendroidpdf:id/text_style_border_style_dashed" || true
sleep 0.4

_drag_seekbar_pct "org.opendroidpdf:id/text_style_border_radius_seekbar" "$BORDER_RADIUS_PCT" || {
  echo "FAIL: could not adjust border radius seekbar" >&2
  exit 1
}
sleep 0.4

uia_tap_desc "Set border color to ${BORDER_COLOR_NAME}" || {
  echo "FAIL: could not tap border color swatch (${BORDER_COLOR_NAME})" >&2
  exit 1
}
sleep 0.6

# Locking controls live below the border section; enable both.
_scroll_dialog_down
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/text_style_lock_position_size" || true
sleep 0.3
uia_tap_any_res_id "org.opendroidpdf:id/text_style_lock_contents" || true
sleep 0.3

adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
sleep 0.9

OUT_PNG2="${OUT_PNG2:-${OUT_PREFIX}_border_screen.png}"
_screencap_png "$OUT_PNG2"
echo "  wrote $OUT_PNG2"
if ! read -r bx0 by0 bx1 by1 < <(_selection_box_bbox_px "$OUT_PNG2"); then
  echo "FAIL: could not detect selection bbox for border assertion" >&2
  exit 1
fi
_assert_redish_border_in_selection_bbox "$OUT_PNG2" "$bx0" "$by0" "$bx1" "$by1"

echo "[9/10] Lock regression: drag must not move note; edit dialog must not appear"
DB_BEFORE="${OUT_PREFIX}_sidecar_before_move.db"
_export_sidecar_db "$DB_BEFORE"
read -r l0 t0 r0 b0 < <(_note_bounds_from_db "$DB_BEFORE" "$TOKEN" || echo "")
if [[ -z "${l0:-}" ]]; then
  echo "FAIL: could not read initial note bounds from sidecar DB" >&2
  exit 1
fi

adb -s "$DEVICE" logcat -c >/dev/null || true
read -r w h < <(_wm_size)
lock_x=$(((bx0 + bx1) / 2))
lock_y=$(((by0 + by1) / 2))
lock_y2=$((lock_y + h / 6))
if (( lock_y2 > h - 12 )); then lock_y2=$((h - 12)); fi
adb -s "$DEVICE" shell input swipe "$lock_x" "$lock_y" "$lock_x" "$lock_y2" 420
sleep 0.9

DB_AFTER="${OUT_PREFIX}_sidecar_after_move.db"
_export_sidecar_db "$DB_AFTER"
read -r l1 t1 r1 b1 < <(_note_bounds_from_db "$DB_AFTER" "$TOKEN" || echo "")
if [[ -z "${l1:-}" ]]; then
  echo "FAIL: could not read post-drag note bounds from sidecar DB" >&2
  exit 1
fi
python3 - "$l0" "$t0" "$r0" "$b0" "$l1" "$t1" "$r1" "$b1" <<'PY'
import sys

vals = list(map(float, sys.argv[1:]))
l0, t0, r0, b0, l1, t1, r1, b1 = vals
dl = abs(l1 - l0)
dt = abs(t1 - t0)
dr = abs(r1 - r0)
db = abs(b1 - b0)
print(f"db.bounds_delta=(dl={dl:.6f}, dt={dt:.6f}, dr={dr:.6f}, db={db:.6f})")
thr = 0.01
if max(dl, dt, dr, db) > thr:
    print("FAIL: note bounds changed after drag; expected lock_position_size to prevent moves/resizes", file=sys.stderr)
    raise SystemExit(1)
PY

if adb -s "$DEVICE" logcat -d | rg -q "TextAnnotGesture: start MOVE"; then
  echo "FAIL: locked position/size should prevent starting MOVE on drag" >&2
  adb -s "$DEVICE" logcat -d | rg -n "TextAnnotGesture: start MOVE" | tail -n 40 >&2 || true
  exit 1
fi

# Attempt to edit by double-tapping inside the selection.
adb -s "$DEVICE" shell input tap "$lock_x" "$lock_y"
sleep 0.35
adb -s "$DEVICE" shell input tap "$lock_x" "$lock_y"
sleep 1.0
if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
  echo "FAIL: edit dialog appeared even though contents are locked" >&2
  _screencap_png "${OUT_PREFIX}_lock_contents_fail.png" || true
  echo "  wrote ${OUT_PREFIX}_lock_contents_fail.png" >&2
  exit 1
fi

echo "[10/10] Export sidecar DB and assert bg+border+lock fields persisted"
DB_LOCAL="${DB_LOCAL:-${OUT_PREFIX}_sidecar.db}"
_export_sidecar_db "$DB_LOCAL"
want_hex="0xFFFFF601"
want_border_hex="0xFFCC0000"
want_opacity="$(python3 - "$BG_OPACITY_PCT" <<'PY'
import sys
print(float(sys.argv[1]) / 100.0)
PY
)"
_assert_db_has_style_fields "$DB_LOCAL" "$TOKEN" "$want_hex" "$want_opacity" "$want_border_hex" 1 1
echo "OK: sidecar note background+border+locking persisted (bg=$BG_COLOR_NAME ${BG_OPACITY_PCT}% border=$BORDER_COLOR_NAME; locks enabled)"
