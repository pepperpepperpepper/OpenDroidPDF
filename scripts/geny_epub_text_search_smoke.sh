#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "EPUB text search" + reflow relayout interaction:
# - Open a small EPUB that contains known text
# - Assert render is non-blank
# - Run Search for a known token and assert highlight overlay appears
# - Force a reflow relayout (font size change) and assert prior search highlights are cleared
# - Re-run Search and assert highlight overlay appears again
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_text_search_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/hello.epub}
EPUB_REMOTE=${EPUB_REMOTE:-/sdcard/Download/hello.epub}
QUERY=${QUERY:-OpenDroidPDF}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

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

  x="$(python - "$l" "$w" "$ratio" <<'PY'
import sys
l = int(sys.argv[1])
w = int(sys.argv[2])
ratio = float(sys.argv[3])
ratio = max(0.05, min(0.95, ratio))
x = l + int(w * ratio)
print(x)
PY
)"

  y=$(((t + b) / 2))
  adb -s "$DEVICE" shell input tap "$x" "$y"
}

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
        # Reflow text pages can be mostly white with relatively little "dark" ink.
        if nonwhite > 1600 and dark > 120:
            break
    if nonwhite > 1600 and dark > 120:
        break

if nonwhite <= 1600 or dark <= 120:
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

# Skip toolbar; for small EPUB pages the content starts closer to the top than
# many PDFs, so don't skip too aggressively or we'll miss the first match.
ystart = int(h * 0.12)
step = 2

count = 0
px = img.load()
for y in range(ystart, h, step):
    for x in range(0, w, step):
        r, g, b = px[x, y]
        # Search highlight is a blue tint (#33B5E5-ish), sometimes blended.
        if b > 150 and (b - max(r, g)) > 25:
            count += 1

print(count)
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

echo "[4/10] Push EPUB fixture"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null

echo "[5/10] Launch viewer with EPUB"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

OUT_PREFIX="${OUT_PREFIX:-tmp_geny_epub_text_search}"

echo "[6/10] Screenshot baseline + assert non-blank"
OUT_BEFORE="${OUT_BEFORE:-${OUT_PREFIX}_before.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_BEFORE"
echo "  wrote $OUT_BEFORE"
_assert_nonblank_png "$OUT_BEFORE"

before_blue="$(_count_search_highlight_pixels "$OUT_BEFORE")"
echo "  baseline blue-ish samples: $before_blue"

echo "[7/10] Search for \"$QUERY\""
uia_tap_desc "More options"
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/menu_search" || uia_tap_text_contains "Search" || { echo "FAIL: Search menu missing" >&2; exit 1; }
sleep 0.8
adb -s "$DEVICE" shell input text "$QUERY"
adb -s "$DEVICE" shell input keyevent 66
sleep 3

echo "[8/10] Screenshot after search + assert highlight appears"
OUT_AFTER="${OUT_AFTER:-${OUT_PREFIX}_after.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_AFTER"
echo "  wrote $OUT_AFTER"

after_blue="$(_count_search_highlight_pixels "$OUT_AFTER")"
echo "  after-search blue-ish samples: $after_blue"

if [[ "$after_blue" -le $((before_blue + 40)) ]]; then
  echo "FAIL: expected search highlight overlay to increase (before=$before_blue after=$after_blue)" >&2
  exit 1
fi

echo "[9/10] Force reflow relayout; assert stale highlights cleared; re-search"
# Collapse the SearchView so the overflow menu contains Reading settings.
adb -s "$DEVICE" shell input keyevent 4
sleep 0.6

uia_tap_desc "More options"
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/menu_reading_settings" || uia_tap_text_contains "Reading settings" || { echo "FAIL: Reading settings missing" >&2; exit 1; }
sleep 0.8
_tap_seekbar_ratio "org.opendroidpdf:id/reflow_seek_font_size" 0.80 || { echo "FAIL: could not adjust font size seekbar" >&2; exit 1; }
sleep 0.3
uia_tap_text_contains "Apply" || uia_tap_any_res_id "android:id/button1" || { echo "FAIL: Apply not found" >&2; exit 1; }
sleep 3
uia_assert_in_document_view

OUT_RELAYOUT="${OUT_RELAYOUT:-${OUT_PREFIX}_relayout.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_RELAYOUT"
echo "  wrote $OUT_RELAYOUT"
_assert_nonblank_png "$OUT_RELAYOUT"

relayout_blue="$(_count_search_highlight_pixels "$OUT_RELAYOUT")"
echo "  after-relayout blue-ish samples: $relayout_blue"
if [[ "$relayout_blue" -gt $((before_blue + 40)) ]]; then
  echo "FAIL: expected stale search highlights cleared after relayout (baseline=$before_blue relayout=$relayout_blue)" >&2
  exit 1
fi

uia_tap_desc "More options"
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/menu_search" || uia_tap_text_contains "Search" || { echo "FAIL: Search menu missing (post-relayout)" >&2; exit 1; }
sleep 0.8
adb -s "$DEVICE" shell input text "$QUERY"
adb -s "$DEVICE" shell input keyevent 66
sleep 3

OUT_RESEARCH="${OUT_RESEARCH:-${OUT_PREFIX}_research.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_RESEARCH"
echo "  wrote $OUT_RESEARCH"

research_blue="$(_count_search_highlight_pixels "$OUT_RESEARCH")"
echo "  re-search blue-ish samples: $research_blue"
if [[ "$research_blue" -le $((relayout_blue + 40)) ]]; then
  echo "FAIL: expected search highlight overlay to increase after relayout (before=$relayout_blue after=$research_blue)" >&2
  exit 1
fi

echo "[10/10] Logcat fatal check"
if adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" >/dev/null; then
  echo "FAIL: detected fatal logcat entries" >&2
  adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" | tail -n 40 >&2
  exit 1
fi

echo "EPUB text+search smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
