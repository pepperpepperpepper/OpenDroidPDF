#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "Save fails -> downgrade to read-only/export mode" (Phase E4):
# - Open a writable-ish PDF from external storage
# - Make a change (draw + accept)
# - Force the next Save to fail (debug hook)
# - Tap Save and assert the app shows the read-only banner ("Enable saving")
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_save_permission_downgrade_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_with_text.pdf}
PDF_REMOTE=${PDF_REMOTE:-/sdcard/Download/odp_save_downgrade.pdf}

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

echo "[1/7] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/7] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/7] Push sample PDF"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE" >/dev/null

echo "[4/7] Allow broad storage access (best-effort) and open PDF"
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

# If the read-only banner is already shown, the doc opened in read-only mode and this smoke can't
# validate the downgrade path (it never had Save capability to lose).
if uia_has_text_contains "Enable saving" || uia_has_text_contains "can’t be modified"; then
  echo "FAIL: PDF opened in read-only mode; cannot validate downgrade-on-save-failure" >&2
  exit 1
fi

echo "[5/7] Make a change (draw + accept)"
uia_enter_draw_mode || { echo "FAIL: draw entry point missing" >&2; exit 1; }
sleep 0.4
_draw_swipe 260
sleep 0.5
uia_tap_any_res_id "org.opendroidpdf:id/accept_image_button" "org.opendroidpdf:id/menu_accept" || { echo "FAIL: accept button not found" >&2; exit 1; }
sleep 1.0

echo "[6/7] Force save failure and attempt Save (expect failure + downgrade banner)"
adb -s "$DEVICE" shell run-as "$PKG" mkdir -p files >/dev/null 2>&1 || {
  echo "FAIL: could not set debug save-failure hook (run-as mkdir failed)" >&2
  exit 1
}
adb -s "$DEVICE" shell run-as "$PKG" dd if=/dev/zero of=files/odp_debug_fail_next_save bs=1 count=1 2>/dev/null || {
  echo "FAIL: could not set debug save-failure hook (run-as failed)" >&2
  exit 1
}

uia_assert_in_document_view

uia_save_changes || { echo "FAIL: Save changes entry point missing" >&2; exit 1; }
sleep 0.6

# The Save menu opens a "Save vs Save As" prompt; choose the primary "Save" button.
uia_tap_any_res_id "android:id/button1" || { echo "FAIL: Save confirmation dialog button not found" >&2; exit 1; }
sleep 4

uia_has_text_contains "Enable saving" || uia_has_text_contains "can’t be modified" || {
  echo "FAIL: expected read-only banner after save failure (downgrade did not trigger)" >&2
  echo "Logcat tail:" >&2
  adb -s "$DEVICE" logcat -d | tail -n 120 >&2
  exit 1
}

echo "[7/7] Done"
echo "Smoke complete. Logcat tail:"
adb -s "$DEVICE" logcat -d | tail -n 120
