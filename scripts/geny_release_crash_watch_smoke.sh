#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for the signed release APK:
# - Installs the latest signed org.opendroidpdf_*.apk from the local F-Droid repo (unless APK= is provided)
# - Opens a fixture PDF via DocumentsUI (content:// URI)
# - Waits and fails fast if the process dies / logcat shows a fatal
# - Captures a screenshot + logcat tail as artifacts
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_release_crash_watch_smoke.sh
#   DEVICE=localhost:<port> APK=/path/to/org.opendroidpdf_XXX.apk ./scripts/geny_release_crash_watch_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/geny_uia.sh"

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

PDF_LOCAL=${PDF_LOCAL:-${ROOT_DIR}/test_assets/pdf_with_text.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_release_crash_watch.pdf}

WAIT_S=${WAIT_S:-45}

OUT_PREFIX="${OUT_PREFIX:-tmp_geny_release_crash_watch}"
SCREENSHOT_PNG="${SCREENSHOT_PNG:-${OUT_PREFIX}_ui.png}"
LOGCAT_TXT="${LOGCAT_TXT:-${OUT_PREFIX}_logcat.txt}"

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

adb -s "$DEVICE" get-state >/dev/null

APK_REAL="$(_resolve_apk)"
echo "[1/7] Install signed release APK: $APK_REAL"
adb -s "$DEVICE" uninstall "$PKG" >/dev/null 2>&1 || true
adb -s "$DEVICE" install -r "$APK_REAL" >/dev/null

echo "[2/7] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/7] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null

echo "[4/7] Launch app and open the PDF via DocumentsUI (content:// URI)"
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

uia_tap_desc "Show roots" || {
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
  _screencap_png "$SCREENSHOT_PNG" || true
  _fail_if_fatal_logcat "$LOGCAT_TXT" || true
  exit 1
}

uia_assert_in_document_view
_fail_if_fatal_logcat "$LOGCAT_TXT"

echo "[5/7] Wait ${WAIT_S}s and ensure process stays alive"
for _ in $(seq 1 "$WAIT_S"); do
  sleep 1
  _fail_if_process_dead || break
done

echo "[6/7] Capture artifacts"
_screencap_png "$SCREENSHOT_PNG" || true
_fail_if_fatal_logcat "$LOGCAT_TXT"

echo "[7/7] OK: release crash-watch smoke passed"
echo "  screenshot: $SCREENSHOT_PNG"
echo "  logcat: $LOGCAT_TXT"

