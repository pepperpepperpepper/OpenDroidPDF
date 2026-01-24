#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke: pinch/zoom while in drawing mode must NOT create accidental ink strokes.
#
# Strategy:
# - Enter drawing mode on a PDF
# - Assert Undo is NOT available (no strokes yet)
# - Run a multi-touch pinch gesture via the UIAutomator2 runner
# - Assert Undo is still NOT available (pinch did not create a stroke)
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_pinch_while_drawing_smoke.sh
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk PDF_LOCAL=test_pdf.pdf ./scripts/geny_pinch_while_drawing_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}"
PDF_LOCAL="${PDF_LOCAL:-test_pdf.pdf}"
PDF_REMOTE="${PDF_REMOTE:-/sdcard/Download/odp_pinch_while_drawing_smoke.pdf}"
PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

SRC_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SRC_DIR/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

echo "[1/6] Ensure UIAutomator2 runner installed"
uia_runner_ensure_installed

echo "[2/6] Install debug APK (clear data + perms)"
_install_out="$(adb -s "$DEVICE" install -r "$APK" 2>&1 || true)"
if [[ "$_install_out" != *"Success"* ]]; then
  if [[ "$_install_out" == *"INSTALL_FAILED_UPDATE_INCOMPATIBLE"* ]]; then
    echo "[2/6] Signature mismatch; uninstalling $PKG and retrying install"
    adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
    adb -s "$DEVICE" install -r "$APK" >/dev/null
  else
    echo "$_install_out" >&2
    exit 1
  fi
fi

adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[3/6] Push sample PDF"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE" >/dev/null

echo "[4/6] Launch viewer with sample PDF"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[5/6] Enter drawing mode and assert Undo is unavailable"
uia_enter_draw_mode
sleep 0.6

# In the annot toolbar, Undo is hidden unless there is at least one stroke.
if uia_has_res_id "org.opendroidpdf:id/menu_undo"; then
  echo "FAIL: Undo is visible before pinch (expected no strokes yet)" >&2
  exit 1
fi

echo "[5.5/6] Pinch-zoom while drawing (UIAutomator runner) and re-check Undo"
uia_runner_run_test "org.opendroidpdf.uia.ZoomPinchTest#testPinchOutOnlyDoesNotCrash"
sleep 0.8

if uia_has_res_id "org.opendroidpdf:id/menu_undo"; then
  echo "FAIL: Undo became visible after pinch while drawing (pinch likely created ink)" >&2
  exit 1
fi

echo "[6/6] Logcat tail"
adb -s "$DEVICE" logcat -d | tail -n 160

echo "OK: pinch while drawing did not create ink."

