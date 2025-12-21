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
TOC_ENTRY_TEXT=${TOC_ENTRY_TEXT:-Long Paragraphs}

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

_page_indicator_text() {
  # Extracts the toolbar page indicator like "1/2" from a UIAutomator dump.
  #
  # NOTE: Some builds/devices don't expose this as a stable resource-id, so we
  # scan both text and content-desc for a fraction pattern and pick the top-most
  # match (toolbar region).
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  _page_indicator_text_in_xml "$tmp"
  local rc=$?
  rm -f "$tmp"
  return $rc
}

_page_indicator_text_in_xml() {
  local xml_path="$1"
  python - "$xml_path" <<'PY'
import re, sys, xml.etree.ElementTree as ET

xml_path = sys.argv[1]
try:
    tree = ET.parse(xml_path)
except Exception:
    raise SystemExit(1)

rx = re.compile(r"^\d+/\d+$")

def top_of(bounds: str):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    return int(m.group(2))

candidates = []
for node in tree.iter("node"):
    for key in ("text", "content-desc"):
        raw = (node.attrib.get(key) or "").strip()
        if not raw:
            continue
        if not rx.match(raw):
            continue
        t = top_of(node.attrib.get("bounds") or "")
        if t is None:
            continue
        candidates.append((t, raw))

if not candidates:
    raise SystemExit(1)

# Prefer the top-most occurrence (toolbar).
candidates.sort(key=lambda x: x[0])
print(candidates[0][1])
raise SystemExit(0)
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

page_before=""
# Some Genymotion boots render reflow docs slowly; wait up to ~30s.
for _ in {1..60}; do
  page_before="$(_page_indicator_text 2>/dev/null || true)"
  if [[ -n "$page_before" ]]; then
    break
  fi
  sleep 0.5
done
if [[ -z "$page_before" ]]; then
  # One last snapshot — avoids a race where the indicator appears right after the final poll.
  _uia_dump_to "${OUT_PREFIX}_before_ui.xml" || true
  page_before="$(_page_indicator_text_in_xml "${OUT_PREFIX}_before_ui.xml" 2>/dev/null || true)"
fi
if [[ -n "$page_before" ]]; then
  echo "  page indicator before: $page_before" >&2
else
  echo "  WARN: page indicator not detectable; will fall back to screenshot diff" >&2
fi

echo "[6/8] Screenshot baseline"
OUT_BEFORE="${OUT_BEFORE:-${OUT_PREFIX}_before.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_BEFORE"
echo "  wrote $OUT_BEFORE"

echo "[7/8] Open Contents -> jump via TOC entry"
uia_tap_desc "More options"
sleep 0.4
uia_tap_text_contains "Contents" || { echo "FAIL: Contents menu missing" >&2; exit 1; }
sleep 0.8

if uia_has_text_contains "No table of contents"; then
  echo "FAIL: app reported empty TOC for $EPUB_LOCAL" >&2
  adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_toc_empty.png" || true
  exit 1
fi

# Tap the expected TOC entry.
uia_tap_text_contains "$TOC_ENTRY_TEXT" || {
  _uia_dump_to "${OUT_PREFIX}_toc_ui.xml" || true
  adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_toc_fail.png" || true
  echo "FAIL: could not find TOC entry '$TOC_ENTRY_TEXT'" >&2
  exit 1
}
sleep 0.5

page_after=""
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  cur="$(_page_indicator_text 2>/dev/null || true)"
  if [[ -n "$page_before" && -n "$cur" && "$cur" != "$page_before" ]]; then
    page_after="$cur"
    break
  fi
  sleep 0.5
done

OUT_AFTER="${OUT_AFTER:-${OUT_PREFIX}_after.png}"
adb -s "$DEVICE" exec-out screencap -p >"$OUT_AFTER"
echo "  wrote $OUT_AFTER"

if [[ -n "$page_before" && -n "$page_after" ]]; then
  echo "  OK: page indicator changed ($page_before -> $page_after)" >&2
else
  if [[ -n "$page_before" ]]; then
    _uia_dump_to "${OUT_PREFIX}_after_ui.xml" || true
    maybe="$(_page_indicator_text_in_xml "${OUT_PREFIX}_after_ui.xml" 2>/dev/null || true)"
    if [[ -n "$maybe" && "$maybe" != "$page_before" ]]; then
      echo "  OK: page indicator changed ($page_before -> $maybe)" >&2
      page_after="$maybe"
    fi
  fi
fi

if [[ -z "$page_after" ]]; then
  # Fall back to a screen diff when the toolbar indicator isn't detectable.
  _assert_screens_differ "$OUT_BEFORE" "$OUT_AFTER"
fi

echo "[8/8] Logcat fatal check"
if adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" >/dev/null; then
  echo "FAIL: detected fatal logcat entries" >&2
  adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" | tail -n 40 >&2
  exit 1
fi

echo "EPUB TOC smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
