#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for EPUB table-of-contents navigation:
# - Open an EPUB with a TOC (edge fixture)
# - Capture a baseline screenshot
# - Open Contents, jump to the second TOC entry
# - Assert the rendered page changes (screenshot diff)
# - Fail on fatal crashes / ANRs
#
# Usage:
#   DEVICE=localhost:42865 APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_toc_smoke.sh

DEVICE=${DEVICE:-localhost:42865}
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/edge.epub}
EPUB_REMOTE=${EPUB_REMOTE:-/sdcard/Download/edge.epub}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_assert_screens_differ() {
  local before="$1"
  local after="$2"
  python - "$before" "$after" <<'PY'
import sys
from PIL import Image

a_path, b_path = sys.argv[1], sys.argv[2]
a = Image.open(a_path).convert("RGB")
b = Image.open(b_path).convert("RGB")

aw, ah = a.size
bw, bh = b.size
w = min(aw, bw)
h = min(ah, bh)

# Skip toolbar/status region.
ystart = int(h * 0.12)
step = 3

changed = 0
samples = 0

apx = a.load()
bpx = b.load()

for y in range(ystart, h, step):
    for x in range(0, w, step):
        ar, ag, ab = apx[x, y]
        br, bg, bb = bpx[x, y]
        d = abs(ar - br) + abs(ag - bg) + abs(ab - bb)
        samples += 1
        if d >= 40:
            changed += 1
        if changed > 600:
            break
    if changed > 600:
        break

if changed <= 600:
    raise SystemExit(f"FAIL: expected page to change after TOC nav (changed={changed}, samples={samples}, size={w}x{h})")

print(f"  OK: TOC nav changed screen (changed={changed}, samples={samples}, size={w}x{h})")
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

echo "[4/8] Push EPUB"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null

echo "[5/8] Launch viewer with EPUB"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

OUT_PREFIX="${OUT_PREFIX:-tmp_geny_epub_toc}"

echo "[6/8] Screenshot baseline"
OUT_BEFORE="${OUT_BEFORE:-${OUT_PREFIX}_before.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_BEFORE"
echo "  wrote $OUT_BEFORE"

echo "[7/8] Open Contents -> jump to second TOC entry"
uia_tap_desc "More options"
sleep 0.4
uia_tap_text_contains "Contents" || { echo "FAIL: Contents menu missing" >&2; exit 1; }
sleep 0.8

if uia_has_text_contains "No table of contents"; then
  echo "FAIL: app reported empty TOC for $EPUB_LOCAL" >&2
  adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_toc_empty.png" || true
  exit 1
fi

# Our edge fixture defines "Tables + CSS" and "Long Paragraphs" in toc.ncx; jump to the second entry.
uia_tap_text_contains "Long Paragraphs" || {
  _uia_dump_to "${OUT_PREFIX}_toc_ui.xml" || true
  adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_toc_fail.png" || true
  echo "FAIL: could not find TOC entry 'Long Paragraphs'" >&2
  exit 1
}
sleep 2

OUT_AFTER="${OUT_AFTER:-${OUT_PREFIX}_after.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_AFTER"
echo "  wrote $OUT_AFTER"
_assert_screens_differ "$OUT_BEFORE" "$OUT_AFTER"

echo "[8/8] Logcat fatal check"
if adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" >/dev/null; then
  echo "FAIL: detected fatal logcat entries" >&2
  adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" | tail -n 40 >&2
  exit 1
fi

echo "EPUB TOC smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
