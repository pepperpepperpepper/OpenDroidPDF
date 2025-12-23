#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for an "edge" EPUB fixture:
# - Open an EPUB with tables/long paragraphs/CSS
# - Assert render is non-blank (content region)
# - Change reflow settings (font size) to force relayout twice
# - Assert render remains non-blank after each relayout
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_edge_relayout_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/edge.epub}
EPUB_REMOTE=${EPUB_REMOTE:-/sdcard/Download/edge.epub}

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
  # Tap on a seekbar at a given horizontal ratio (0..1) to change its value.
  local rid="$1"
  local ratio="${2:-0.85}"
  local l t r b w x y
  read -r l t r b < <(_uia_bounds_for_rid "$rid")
  w=$((r - l))
  if (( w <= 0 )); then
    echo "FAIL: seekbar bounds invalid for $rid" >&2
    return 1
  fi

  # Clamp ratio to [0.05, 0.95] to avoid tapping exactly on the thumb edge.
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
        if nonwhite > 1800 and dark > 250:
            break
    if nonwhite > 1800 and dark > 250:
        break

if nonwhite <= 1800 or dark <= 250:
    raise SystemExit(
        f"FAIL: render looks blank-ish: nonwhite={nonwhite}, dark={dark}, samples={samples}, size={w}x{h}"
    )

print(f"  OK: nonblank render (nonwhite>{nonwhite}, dark>{dark}, samples={samples}, size={w}x{h})")
PY
}

echo "[1/8] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/8] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/8] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[4/8] Push edge EPUB"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null

echo "[5/8] Launch viewer with edge EPUB"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

OUT_PREFIX="${OUT_PREFIX:-tmp_geny_epub_edge}"

echo "[6/8] Screenshot baseline + assert non-blank"
OUT_BASE="${OUT_BASE:-${OUT_PREFIX}_base.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_BASE"
echo "  wrote $OUT_BASE"
_assert_nonblank_png "$OUT_BASE"

echo "[7/8] Relayout twice (font size) + assert non-blank after each"
for i in 1 2; do
  uia_tap_desc "More options"
  sleep 0.4
  uia_tap_text_contains "Reading settings" || { echo "FAIL: Reading settings missing" >&2; exit 1; }
  sleep 0.8

  # Force relayout by changing font size.
  if [[ "$i" -eq 1 ]]; then
    _tap_seekbar_ratio "org.opendroidpdf:id/reflow_seek_font_size" 0.85 || { echo "FAIL: could not adjust font size seekbar" >&2; exit 1; }
  else
    _tap_seekbar_ratio "org.opendroidpdf:id/reflow_seek_font_size" 0.30 || { echo "FAIL: could not adjust font size seekbar (2nd pass)" >&2; exit 1; }
  fi
  sleep 0.3
  uia_tap_text_contains "Apply" || { echo "FAIL: Apply not found" >&2; exit 1; }
  sleep 3
  uia_assert_in_document_view

  out="${OUT_PREFIX}_relayout${i}.png"
  adb -s "$DEVICE" exec-out screencap -p >"$out"
  echo "  wrote $out"
  _assert_nonblank_png "$out"
done

echo "[8/8] Logcat fatal check"
if adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" >/dev/null; then
  echo "FAIL: detected fatal logcat entries" >&2
  adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" | tail -n 40 >&2
  exit 1
fi

echo "Edge EPUB relayout smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
