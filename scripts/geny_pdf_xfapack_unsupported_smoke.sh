#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for the XFA Pack "unsupported" flow:
# - Install main app + XFA Pack (debug, same signing key)
# - Open a "pure/dynamic" XFA PDF (default: PDFium `simple_xfa.pdf`) via DocumentsUI (content:// URI)
# - Trigger "Convert to form (XFA Pack)"
# - Assert the conversion returns RESULT_UNSUPPORTED (via logcat) and no converted copy opens
#
# Usage:
#   DEVICE=localhost:<port> \
#   APK=/path/to/OpenDroidPDF-debug.apk \
#   APK_XFAPACK=/path/to/xfapack-debug.apk \
#   PDF_LOCAL=test_assets/thirdparty/pdfium_xfa/simple_xfa.pdf \
#   ./scripts/geny_pdf_xfapack_unsupported_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
APK_XFAPACK=${APK_XFAPACK:-/mnt/subtitled/opendroidpdf-android-build/xfapack/outputs/apk/debug/xfapack-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-}
PDF_REMOTE_PATH=${PDF_REMOTE_PATH:-/sdcard/Download/odp_xfapack_unsupported_smoke.pdf}

PKG=org.opendroidpdf
PKG_XFAPACK=org.opendroidpdf.xfapack
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
  sleep 1.2
  uia_tap_text_contains "$fname" || {
    echo "FAIL: could not select $fname in DocumentsUI search results" >&2
    adb -s "$DEVICE" logcat -d | tail -n 120 >&2
    exit 1
  }

  uia_assert_in_document_view
}

root_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -z "$PDF_LOCAL" ]]; then
  PDF_LOCAL="${root_dir}/test_assets/thirdparty/pdfium_xfa/simple_xfa.pdf"
fi

echo "[1/4] Install debug APKs (main + XFA Pack)"
adb -s "$DEVICE" uninstall "$PKG_XFAPACK" >/dev/null 2>&1 || true
adb -s "$DEVICE" uninstall "$PKG" >/dev/null 2>&1 || true
adb -s "$DEVICE" install -r "$APK" >/dev/null
adb -s "$DEVICE" install -r "$APK_XFAPACK" >/dev/null

echo "[2/4] Push XFA PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null
fname="$(basename "$PDF_REMOTE_PATH")"

echo "[3/4] Open PDF via DocumentsUI (expect XFA banner)"
_open_pdf_via_documentsui "$fname"

want_banner="This PDF uses XFA forms"
for _ in $(seq 1 24); do
  if uia_has_text_contains "$want_banner"; then
    break
  fi
  sleep 0.35
done
uia_has_text_contains "$want_banner" || {
  echo "FAIL: did not find XFA unsupported banner text after open" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2 || true
  exit 1
}

uia_tap_text_contains "Learn more" || {
  echo "FAIL: did not find 'Learn more' action for XFA banner" >&2
  exit 1
}
sleep 0.8
uia_has_text_contains "Convert to form" || {
  echo "FAIL: did not find 'Convert to form (XFA Pack)' action in XFA explainer dialog" >&2
  exit 1
}

uia_tap_text_contains "Convert to form" || {
  echo "FAIL: could not tap 'Convert to form' action" >&2
  exit 1
}

echo "[4/4] Assert conversion reports UNSUPPORTED (and no converted PDF opens)"
# Wait a bit for the IPC conversion to complete.
sleep 4.0

if uia_has_text_contains "xfa_convert_"; then
  echo "FAIL: unexpectedly opened a converted file title (xfa_convert_*) for a pure XFA fixture" >&2
  exit 1
fi

if ! adb -s "$DEVICE" logcat -d | rg -q "XFA Pack conversion finished code=1"; then
  echo "FAIL: did not observe RESULT_UNSUPPORTED (code=1) in logcat after conversion attempt" >&2
  adb -s "$DEVICE" logcat -d | tail -n 240 >&2 || true
  exit 1
fi

echo "OK: XFA Pack correctly reported UNSUPPORTED for a pure/dynamic XFA PDF"

