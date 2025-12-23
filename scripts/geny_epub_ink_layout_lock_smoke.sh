#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for EPUB "ink locks layout changes":
# - Open an EPUB
# - Create a sidecar ink stroke
# - Open Reading settings and assert layout-affecting controls are disabled
# - Apply a theme change and assert no crash
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_ink_layout_lock_smoke.sh

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

_draw_swipe() {
  local w h x1 y1 x2 y2 dur
  read -r w h < <(_wm_size)
  x1=$((w * 2 / 10))
  x2=$((w * 8 / 10))
  y1=$((h * 35 / 100))
  y2=$((h * 55 / 100))
  dur=${1:-240}
  adb -s "$DEVICE" shell input swipe "$x1" "$y1" "$x2" "$y2" "$dur"
}

_uia_assert_rid_enabled() {
  local rid="$1"
  local expected="$2" # "true" or "false"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python - "$tmp" "$rid" "$expected" <<'PY'
import sys, xml.etree.ElementTree as ET
xml_path, rid, expected = sys.argv[1], sys.argv[2], sys.argv[3]
tree = ET.parse(xml_path)
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    enabled = node.attrib.get("enabled", "")
    if enabled != expected:
        raise SystemExit(f"FAIL: {rid} enabled={enabled!r} expected={expected!r}")
    print(f"OK: {rid} enabled={enabled}")
    raise SystemExit(0)
raise SystemExit(f"FAIL: could not find rid {rid}")
PY
  rm -f "$tmp"
}

echo "[1/8] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/8] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/8] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[4/8] Push EPUB fixture"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null

echo "[5/8] Launch viewer with EPUB + draw/commit ink"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

uia_tap_any_res_id "org.opendroidpdf:id/draw_image_button" "org.opendroidpdf:id/menu_draw" || { echo "FAIL: draw button missing" >&2; exit 1; }
sleep 0.4
_draw_swipe 260
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/accept_image_button" "org.opendroidpdf:id/menu_accept" || { echo "FAIL: accept missing" >&2; exit 1; }
sleep 1.0

echo "[6/8] Open Reading settings and assert layout controls are disabled"
uia_tap_desc "More options"
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/menu_reading_settings" || uia_tap_text_contains "Reading settings" || { echo "FAIL: Reading settings missing" >&2; exit 1; }
sleep 0.8

uia_has_text_contains "Layout changes are locked" || {
  echo "FAIL: expected layout-locked notice text in Reading settings" >&2
  exit 1
}

_uia_assert_rid_enabled "org.opendroidpdf:id/reflow_seek_font_size" "false"
_uia_assert_rid_enabled "org.opendroidpdf:id/reflow_seek_margins" "false"
_uia_assert_rid_enabled "org.opendroidpdf:id/reflow_seek_line_spacing" "false"

echo "[7/8] Change theme (allowed) and apply"
uia_tap_text_contains "Dark" || true
sleep 0.2
uia_tap_text_contains "Apply" || uia_tap_any_res_id "android:id/button1" || { echo "FAIL: Apply not found" >&2; exit 1; }
sleep 2
uia_assert_in_document_view

echo "[8/8] Logcat fatal check"
if adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" >/dev/null; then
  echo "FAIL: detected fatal logcat entries" >&2
  adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|ANR in" | tail -n 40 >&2
  exit 1
fi

echo "EPUB ink layout-lock smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
