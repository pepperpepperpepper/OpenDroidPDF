#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for "Next field / Previous field" form navigation:
# - Push a 2-page AcroForm PDF to /sdcard/Download
# - Open via DocumentsUI so Save remains available (content:// URI)
# - Enable Forms highlight (reveals field nav icons)
# - Navigate Next twice (page should advance to 2/2), then Previous (back to 1/2)
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pdf_form_nav_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_form_nav.pdf}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_form_nav_smoke.pdf}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_open_pdf_via_documentsui() {
  local fname="$1"
  adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
  adb -s "$DEVICE" logcat -c >/dev/null || true
  adb -s "$DEVICE" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT" >/dev/null
  sleep 1.2

  uia_tap_any_res_id "org.opendroidpdf:id/entry_screen_open_document_card_view" || {
    echo "FAIL: could not tap entry-screen open-document card" >&2
    exit 1
  }
  sleep 1.5

  uia_tap_docsui_roots_drawer || {
    echo "FAIL: could not open DocumentsUI roots drawer" >&2
    exit 1
  }
  sleep 0.7
  uia_tap_text_contains "Downloads" || {
    echo "FAIL: could not switch DocumentsUI to Downloads root" >&2
    exit 1
  }
  sleep 0.9

  uia_tap_any_res_id "com.android.documentsui:id/option_menu_search" || uia_tap_desc "Search" || {
    echo "FAIL: could not open DocumentsUI search" >&2
    exit 1
  }
  sleep 0.6
  adb -s "$DEVICE" shell input text "$fname"

  for _ in $(seq 1 20); do
    if uia_has_text_contains "$fname"; then
      break
    fi
    sleep 0.35
  done
  uia_tap_text_contains "$fname" || {
    echo "FAIL: could not select $fname in DocumentsUI search results" >&2
    adb -s "$DEVICE" logcat -d | tail -n 180 >&2 || true
    exit 1
  }

  for _ in $(seq 1 20); do
    if uia_has_res_id "org.opendroidpdf:id/document_host_container"; then
      return 0
    fi
    sleep 0.6
  done

  echo "FAIL: document view did not appear after open (fname=$fname)" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2 || true
  return 1
}

_wait_for_page_indicator() {
  local want="$1"
  for _ in $(seq 1 24); do
    if uia_has_text_contains "$want"; then
      return 0
    fi
    sleep 0.35
  done
  return 1
}

_tap_overflow_then_text() {
  local text="$1"
  uia_tap_desc "More options" || return 1
  sleep 0.4
  uia_tap_text_contains "$text"
}

_tap_form_nav_next() {
  uia_tap_any_res_id "org.opendroidpdf:id/menu_form_next" && return 0
  _tap_overflow_then_text "Next field"
}

_tap_form_nav_prev() {
  uia_tap_any_res_id "org.opendroidpdf:id/menu_form_previous" && return 0
  _tap_overflow_then_text "Previous field"
}

echo "[1/5] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/5] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/5] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null
fname="$(basename "$PDF_REMOTE_PATH")"

echo "[4/5] Open PDF via DocumentsUI (persistable grant)"
_open_pdf_via_documentsui "$fname"
sleep 1.0

echo "[5/5] Navigate fields across pages"
uia_tap_any_res_id "org.opendroidpdf:id/menu_forms" || true
sleep 0.8

# First navigation selects the first field on page 1; second should advance to page 2.
_tap_form_nav_next || { echo "FAIL: could not tap Next field" >&2; exit 1; }
sleep 0.8
_tap_form_nav_next || { echo "FAIL: could not tap Next field (2nd)" >&2; exit 1; }
if ! _wait_for_page_indicator "2/2"; then
  echo "FAIL: expected to navigate to page 2/2 after Next field" >&2
  exit 1
fi

_tap_form_nav_prev || { echo "FAIL: could not tap Previous field" >&2; exit 1; }
if ! _wait_for_page_indicator "1/2"; then
  echo "FAIL: expected to navigate back to page 1/2 after Previous field" >&2
  exit 1
fi

echo "OK: Form field navigation moved across pages (1/2 <-> 2/2)"
