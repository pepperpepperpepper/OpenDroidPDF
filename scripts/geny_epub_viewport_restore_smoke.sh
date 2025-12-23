#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for EPUB viewport/recents restore:
# - Open an EPUB with a TOC (edge fixture)
# - Navigate to a later section via Contents
# - Background the app (triggers onPause -> saveViewport)
# - Force-stop to ensure persistence is exercised
# - Relaunch the same EPUB and assert the toolbar page indicator restores
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_viewport_restore_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/edge.epub}
EPUB_REMOTE=${EPUB_REMOTE:-/sdcard/Download/edge.epub}
TOC_ENTRY_TEXT=${TOC_ENTRY_TEXT:-Long Paragraphs}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_page_indicator_text() {
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python - "$tmp" <<'PY'
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

candidates.sort(key=lambda x: x[0])
print(candidates[0][1])
raise SystemExit(0)
PY
  local rc=$?
  rm -f "$tmp"
  return $rc
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

page_before=""
for _ in {1..60}; do
  page_before="$(_page_indicator_text 2>/dev/null || true)"
  if [[ -n "$page_before" ]]; then
    break
  fi
  sleep 0.5
done
if [[ -z "$page_before" ]]; then
  _uia_dump_to tmp_geny_epub_viewport_restore_before_ui.xml || true
  echo "FAIL: could not detect toolbar page indicator" >&2
  exit 1
fi
echo "  page indicator before: $page_before"

echo "[6/8] Navigate via Contents -> \"$TOC_ENTRY_TEXT\""
uia_tap_desc "More options"
sleep 0.4
uia_tap_text_contains "Contents" || { echo "FAIL: Contents menu missing" >&2; exit 1; }
sleep 0.8
uia_tap_text_contains "$TOC_ENTRY_TEXT" || {
  _uia_dump_to tmp_geny_epub_viewport_restore_toc_ui.xml || true
  adb -s "$DEVICE" exec-out screencap -p > tmp_geny_epub_viewport_restore_toc_fail.png || true
  echo "FAIL: could not find TOC entry '$TOC_ENTRY_TEXT'" >&2
  exit 1
}
sleep 0.6

page_target=""
for _ in {1..40}; do
  cur="$(_page_indicator_text 2>/dev/null || true)"
  if [[ -n "$cur" && "$cur" != "$page_before" ]]; then
    page_target="$cur"
    break
  fi
  sleep 0.5
done
if [[ -z "$page_target" ]]; then
  _uia_dump_to tmp_geny_epub_viewport_restore_after_nav_ui.xml || true
  adb -s "$DEVICE" exec-out screencap -p > tmp_geny_epub_viewport_restore_after_nav.png || true
  echo "FAIL: page indicator did not change after TOC navigation (before=$page_before)" >&2
  exit 1
fi
echo "  page indicator after nav: $page_target"

echo "[7/8] Background (HOME) -> force-stop -> relaunch and assert restore"
adb -s "$DEVICE" shell input keyevent 3
sleep 1.2
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
sleep 0.6

adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

page_restored=""
for _ in {1..40}; do
  cur="$(_page_indicator_text 2>/dev/null || true)"
  if [[ -n "$cur" ]]; then
    page_restored="$cur"
    break
  fi
  sleep 0.5
done
if [[ -z "$page_restored" ]]; then
  _uia_dump_to tmp_geny_epub_viewport_restore_relaunch_ui.xml || true
  echo "FAIL: could not detect page indicator after relaunch" >&2
  exit 1
fi
echo "  page indicator after relaunch: $page_restored"

if [[ "$page_restored" != "$page_target" ]]; then
  adb -s "$DEVICE" exec-out screencap -p > tmp_geny_epub_viewport_restore_relaunch_fail.png || true
  _uia_dump_to tmp_geny_epub_viewport_restore_relaunch_fail_ui.xml || true
  echo "FAIL: viewport not restored (expected=$page_target got=$page_restored)" >&2
  exit 1
fi

echo "[8/8] Logcat fatal check"
if adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" >/dev/null; then
  echo "FAIL: detected fatal logcat entries" >&2
  adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" | tail -n 40 >&2
  exit 1
fi

echo "EPUB viewport restore smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
