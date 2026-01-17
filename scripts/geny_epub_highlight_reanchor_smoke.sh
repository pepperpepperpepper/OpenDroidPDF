#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for EPUB highlight re-anchoring across relayout:
# - Open a sample EPUB
# - Create a sidecar highlight (stores selected-text anchor)
# - Change reading settings (layout-affecting) and apply
# - Assert no "annotations hidden/different layout" banner remains (highlights reanchored)
# - Assert the sidecar highlight row now matches the current layoutProfileId (from debug log)
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_highlight_reanchor_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/hello.epub}
EPUB_REMOTE=${EPUB_REMOTE:-/sdcard/Download/hello.epub}

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

_uia_bounds_for_rid() {
  local rid="$1"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python - "$tmp" "$rid" <<'PY'
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

_tap_seekbar_ratio() {
  local rid="$1"
  local ratio="${2:-0.85}"
  local l t r b w x y
  read -r l t r b < <(_uia_bounds_for_rid "$rid")
  w=$((r - l))
  if (( w <= 0 )); then
    echo "FAIL: seekbar bounds invalid for $rid" >&2
    return 1
  fi
  x=$((l + (w * 85 / 100)))
  y=$(((t + b) / 2))
  adb -s "$DEVICE" shell input tap "$x" "$y"
}

_export_sidecar_db() {
  local out="$1"
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db >"$out"
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db-wal >"${out}-wal" 2>/dev/null || true
  adb -s "$DEVICE" exec-out run-as "$PKG" cat databases/sidecar_annotations.db-shm >"${out}-shm" 2>/dev/null || true
}

echo "[1/7] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/7] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/7] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[4/7] Push sample EPUB"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null

echo "[5/7] Launch viewer + create highlight"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

OUT_PREFIX="${OUT_PREFIX:-tmp_geny_epub_highlight_reanchor}"
HIGHLIGHT_SHOT_BEFORE="${HIGHLIGHT_SHOT_BEFORE:-${OUT_PREFIX}_highlight_before.png}"
adb -s "$DEVICE" exec-out screencap -p >"$HIGHLIGHT_SHOT_BEFORE"
echo "  wrote $HIGHLIGHT_SHOT_BEFORE" >&2

# Choose a likely text pixel coordinate from a screenshot so we long-press on actual glyphs.
hl_xy="$(python - "$HIGHLIGHT_SHOT_BEFORE" <<'PY'
import sys
from PIL import Image

path = sys.argv[1]
img = Image.open(path).convert("RGB")
w, h = img.size
px = img.load()

ystart = min(h, 160)
yend = min(h, 650)
xend = min(w, int(w * 0.75))

best = None
best_score = 10**9
count = 0

step = 2
for y in range(ystart, yend, step):
    for x in range(0, xend, step):
        r, g, b = px[x, y]
        if r < 80 and g < 80 and b < 80:
            count += 1
            score = r + g + b
            if score < best_score:
                best_score = score
                best = (x, y)

if best is None or count < 200:
    raise SystemExit("FAIL: could not locate enough dark text pixels for long-press")

print(f"{best[0]} {best[1]}")
PY
)"
set -- $hl_xy
hl_x="$1"
hl_y="$2"
adb -s "$DEVICE" shell input swipe "$hl_x" "$hl_y" "$hl_x" "$hl_y" 1500
sleep 1.0

if ! uia_tap_any_res_id "org.opendroidpdf:id/menu_highlight"; then
  uia_tap_text_contains "Highlight" || {
    _uia_dump_to "${OUT_PREFIX}_highlight_ui.xml" || true
    adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_highlight_fail.png" || true
    echo "FAIL: could not find Highlight action after long-press selection" >&2
    exit 1
  }
fi
sleep 0.8

DB_LOCAL="$(mktemp -t geny_epub_hl_reanchor_XXXXXX.db)"
trap 'rm -f -- "$DB_LOCAL" "${DB_LOCAL}-wal" "${DB_LOCAL}-shm" 2>/dev/null || true' EXIT
_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after highlight" >&2; exit 1; }

echo "[6/7] Change reading settings (layout-affecting) and apply"
adb -s "$DEVICE" logcat -c >/dev/null || true
uia_open_navigate_view_sheet || { echo "FAIL: could not open Navigate & View sheet" >&2; exit 1; }
uia_tap_any_res_id "org.opendroidpdf:id/navigate_view_action_reading_settings" || uia_tap_text_contains "Reading settings" || { echo "FAIL: Reading settings missing" >&2; exit 1; }
sleep 0.8
_tap_seekbar_ratio "org.opendroidpdf:id/reflow_seek_font_size" 0.85 || { echo "FAIL: could not adjust font size seekbar" >&2; exit 1; }
sleep 0.3
uia_tap_text_contains "Apply" || { echo "FAIL: Apply not found" >&2; exit 1; }

# Allow relayout + highlight reanchor.
sleep 5

if uia_has_text_contains "Annotations are hidden" || uia_has_text_contains "different layout"; then
  echo "FAIL: mismatch banner still present after highlight-only relayout" >&2
  adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_mismatch_unexpected.png" || true
  echo "  wrote ${OUT_PREFIX}_mismatch_unexpected.png" >&2
  echo "Logcat tail:" >&2
  adb -s "$DEVICE" logcat -d | tail -n 120 >&2
  exit 1
fi

layout_id="$(adb -s "$DEVICE" logcat -d | tr -d '\r' | rg 'layoutDocument ok=.*layoutId=' | tail -n 1 | sed -n 's/.*layoutId=\([^ ]*\).*/\1/p' || true)"
if [[ -z "$layout_id" ]]; then
  echo "FAIL: could not extract layoutId from logcat (debug log missing)" >&2
  adb -s "$DEVICE" logcat -d | tail -n 120 >&2
  exit 1
fi
echo "  layoutId: $layout_id" >&2

_export_sidecar_db "$DB_LOCAL" || { echo "FAIL: could not export sidecar DB after relayout" >&2; exit 1; }
python - "$DB_LOCAL" "$layout_id" <<'PY'
import sqlite3, sys
db, layout_id = sys.argv[1], sys.argv[2]
conn = sqlite3.connect(db)
try:
    rows = list(conn.execute("select distinct layout_profile_id from highlights").fetchall())
    vals = sorted([r[0] for r in rows if r and r[0] is not None])
    if len(vals) != 1:
        raise SystemExit(f"FAIL: expected exactly 1 highlight layout_profile_id, got {vals!r}")
    if vals[0] != layout_id:
        raise SystemExit(f"FAIL: highlight layout_profile_id mismatch: db={vals[0]!r} logcat={layout_id!r}")
finally:
    conn.close()
print("OK: highlight re-anchored to current layout")
PY

echo "[7/7] Artifacts + logcat tail"
OUT_AFTER="${OUT_AFTER:-${OUT_PREFIX}_after.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_AFTER"
echo "  wrote $OUT_AFTER" >&2
adb -s "$DEVICE" logcat -d | tail -n 120
