#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for the signed release APK with a "zoom → idle → pan" interaction:
# - Installs the latest signed org.opendroidpdf_*.apk from the local F-Droid repo (unless APK= is provided)
# - Opens a fixture PDF via DocumentsUI (content:// URI)
# - Pinch-zooms in (UIAutomator multi-touch)
# - Idles, then one-finger pans
# - Waits and fails fast if the process dies / logcat shows a fatal
# - Captures screenshots + logcat as artifacts
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_release_zoom_pan_watch_smoke.sh
#   DEVICE=localhost:<port> APK=/path/to/org.opendroidpdf_XXX.apk ./scripts/geny_release_zoom_pan_watch_smoke.sh
#
# Notes:
#   - This is intended to reproduce user reports like:
#       "open PDF → wait → pinch zoom in → wait → one-finger drag (pan) → crash"

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/geny_uia.sh"

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

PDF_LOCAL=${PDF_LOCAL:-${ROOT_DIR}/test_assets/pdf_with_text.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_release_zoom_pan_watch.pdf}

WAIT_BEFORE_ZOOM_S=${WAIT_BEFORE_ZOOM_S:-2}
WAIT_AFTER_ZOOM_S=${WAIT_AFTER_ZOOM_S:-4}
WAIT_AFTER_PAN_S=${WAIT_AFTER_PAN_S:-30}

OUT_PREFIX="${OUT_PREFIX:-tmp_geny_release_zoom_pan_watch}"
BEFORE_ZOOM_PNG="${BEFORE_ZOOM_PNG:-${OUT_PREFIX}_before_zoom.png}"
AFTER_ZOOM_PNG="${AFTER_ZOOM_PNG:-${OUT_PREFIX}_after_zoom.png}"
AFTER_PAN_PNG="${AFTER_PAN_PNG:-${OUT_PREFIX}_after_pan.png}"
LOGCAT_TXT="${LOGCAT_TXT:-${OUT_PREFIX}_logcat.txt}"

UIA_ZOOM_TEST=${UIA_ZOOM_TEST:-org.opendroidpdf.uia.ZoomPinchTest#testProgressiveZoomInDoesNotCrash}

_resolve_apk() {
  if [[ -n "${APK}" ]]; then
    echo "${APK}"
    return 0
  fi
  local latest
  latest="$(ls -1 /home/arch/fdroid/repo/org.opendroidpdf_*.apk 2>/dev/null | sort -V | tail -n 1 || true)"
  if [[ -z "${latest}" ]]; then
    echo "FAIL: could not find /home/arch/fdroid/repo/org.opendroidpdf_*.apk (set APK=...)" >&2
    return 1
  fi
  echo "${latest}"
}

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p > "$out_png"
}

_fail_if_fatal_logcat() {
  local out_txt="$1"
  adb -s "$DEVICE" logcat -d > "$out_txt" || true
  if rg -q "FATAL EXCEPTION|Process ${PKG} \\(pid [0-9]+\\) has died|Fatal signal" "$out_txt"; then
    echo "FAIL: detected crash in logcat ($out_txt)" >&2
    rg -n "FATAL EXCEPTION|AndroidRuntime|${PKG}|Fatal signal" "$out_txt" | tail -n 260 >&2 || true
    return 1
  fi
  return 0
}

_fail_if_process_dead() {
  if ! adb -s "$DEVICE" shell pidof "$PKG" >/dev/null 2>&1; then
    echo "FAIL: ${PKG} process is not running" >&2
    return 1
  fi
  return 0
}

_wm_size() {
  local line
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

adb -s "$DEVICE" get-state >/dev/null

APK_REAL="$(_resolve_apk)"
echo "[1/9] Install signed release APK: $APK_REAL"
adb -s "$DEVICE" uninstall "$PKG" >/dev/null 2>&1 || true
adb -s "$DEVICE" install -r "$APK_REAL" >/dev/null

echo "[2/9] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/9] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null

echo "[4/9] Launch app and open the PDF via DocumentsUI (content:// URI)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT" >/dev/null
sleep 1.1

uia_tap_any_res_id "org.opendroidpdf:id/entry_screen_open_document_card_view" || {
  echo "FAIL: could not tap entry-screen open-document card" >&2
  exit 1
}
sleep 1.2

fname="$(basename "$PDF_REMOTE_PATH")"

uia_tap_docsui_roots_drawer || {
  echo "FAIL: could not open DocumentsUI roots drawer" >&2
  exit 1
}
sleep 0.6
uia_tap_text_contains "Downloads" || {
  echo "FAIL: could not switch DocumentsUI to Downloads root" >&2
  exit 1
}
sleep 0.8

uia_tap_any_res_id "com.android.documentsui:id/option_menu_search" || uia_tap_desc "Search" || {
  echo "FAIL: could not open DocumentsUI search" >&2
  exit 1
}
sleep 0.5
adb -s "$DEVICE" shell input text "$fname"
sleep 1.0
uia_tap_text_contains "$fname" || {
  echo "FAIL: could not select $fname in DocumentsUI search results" >&2
  _screencap_png "$BEFORE_ZOOM_PNG" || true
  _fail_if_fatal_logcat "$LOGCAT_TXT" || true
  exit 1
}

uia_assert_in_document_view
_fail_if_fatal_logcat "$LOGCAT_TXT"

echo "[5/9] Wait ${WAIT_BEFORE_ZOOM_S}s before zoom"
sleep "$WAIT_BEFORE_ZOOM_S"
_fail_if_process_dead

echo "[6/9] Pinch-zoom in via UIAutomator2 runner (multi-touch)"
uia_runner_ensure_installed
_screencap_png "$BEFORE_ZOOM_PNG" || true
uia_runner_run_test "$UIA_ZOOM_TEST"
sleep "$WAIT_AFTER_ZOOM_S"
_screencap_png "$AFTER_ZOOM_PNG" || true
_fail_if_fatal_logcat "$LOGCAT_TXT"
_fail_if_process_dead

echo "[7/9] One-finger pan (swipe) after zoom"
read -r w h < <(_wm_size)
sx=$((w / 2))
sy=$((h * 70 / 100))
ex=$sx
ey=$((h * 35 / 100))
adb -s "$DEVICE" shell input swipe "$sx" "$sy" "$ex" "$ey" 420
sleep 1.2
_screencap_png "$AFTER_PAN_PNG" || true
_fail_if_fatal_logcat "$LOGCAT_TXT"
_fail_if_process_dead

echo "[8/9] Wait ${WAIT_AFTER_PAN_S}s and ensure process stays alive"
for _ in $(seq 1 "$WAIT_AFTER_PAN_S"); do
  sleep 1
  _fail_if_process_dead || break
done

echo "[9/9] Capture final artifacts"
_fail_if_fatal_logcat "$LOGCAT_TXT"

echo "OK: release zoom-pan crash-watch smoke passed"
echo "  before zoom: $BEFORE_ZOOM_PNG"
echo "  after zoom:  $AFTER_ZOOM_PNG"
echo "  after pan:   $AFTER_PAN_PNG"
echo "  logcat:      $LOGCAT_TXT"
