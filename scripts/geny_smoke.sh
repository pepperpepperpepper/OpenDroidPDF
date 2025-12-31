#!/usr/bin/env bash
set -euo pipefail

# Quick Genymotion smoke: open a known PDF, draw, undo, search, and try opening Share.
# This script avoids fragile coordinates by using UIAutomator resource-ids where possible.
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_blank.pdf}
# Keep smokes independent of MANAGE_EXTERNAL_STORAGE by using app-private storage.
PDF_REMOTE=${PDF_REMOTE:-/data/data/org.opendroidpdf/files/test_blank.pdf}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null
uia_disable_flaky_ime

echo "[1/8] Install debug APK"
if ! adb -s "$DEVICE" install -r "$APK" >/dev/null; then
  echo "  install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
  adb -s "$DEVICE" install "$APK" >/dev/null
fi

echo "[2/8] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[3/8] Push sample PDF"
adb -s "$DEVICE" shell "run-as $PKG sh -lc 'mkdir -p files && cat > files/test_blank.pdf'" <"$PDF_LOCAL"

echo "[4/8] Launch viewer with sample PDF"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[5/8] Enter draw mode and draw a stroke"
uia_tap_any_res_id "org.opendroidpdf:id/draw_image_button" "org.opendroidpdf:id/menu_draw"
sleep 0.5
adb -s "$DEVICE" shell input swipe 420 1000 820 1040 300
sleep 0.5

echo "[6/8] Undo the stroke"
uia_assert_in_document_view
uia_tap_res_id "org.opendroidpdf:id/menu_undo"
sleep 0.5

echo "[7/8] Exit annotation mode (Cancel)"
if ! uia_tap_any_res_id "org.opendroidpdf:id/cancel_image_button" "org.opendroidpdf:id/menu_cancel"; then
  adb -s "$DEVICE" shell input keyevent 4
fi
sleep 0.8

echo "[8/8] Open overflow -> Search, then overflow -> Share (best-effort)"
if uia_tap_desc "More options"; then
  sleep 0.4
  uia_tap_any_res_id "org.opendroidpdf:id/menu_search" || uia_tap_text_contains "Search" || true
  sleep 0.6
  adb -s "$DEVICE" shell input text 'test'
  adb -s "$DEVICE" shell input keyevent 66
  sleep 0.8
fi

adb -s "$DEVICE" shell input keyevent 4 || true
sleep 0.4

if uia_tap_desc "More options"; then
  sleep 0.4
  uia_tap_any_res_id "org.opendroidpdf:id/menu_share" || uia_tap_text_contains "Share" || true
fi

echo "Smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 80
