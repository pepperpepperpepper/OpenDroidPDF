#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for legacy Word (.doc) behavior when the Office Pack is installed.
#
# Ensures:
# - selecting a legacy .doc never crashes
# - Office Pack returns UNSUPPORTED for OLE2 Word docs
# - the app shows an actionable "Import as PDF" dialog with a clear ".doc not supported" message
#
# Usage:
#   DEVICE=localhost:<port> \
#   APK=/path/to/OpenDroidPDF-debug.apk \
#   APK_OFFICEPACK=/path/to/officepack-debug.apk \
#   ./scripts/geny_doc_unsupported_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
APK_OFFICEPACK=${APK_OFFICEPACK:-/mnt/subtitled/opendroidpdf-android-build/officepack/outputs/apk/debug/officepack-debug.apk}
DOC_LOCAL=${DOC_LOCAL:-test_assets/word_legacy_stub.doc}
# Keep smokes independent of MANAGE_EXTERNAL_STORAGE by using app-private storage.
DOC_REMOTE=${DOC_REMOTE:-/data/data/org.opendroidpdf/files/word_legacy_stub.doc}

PKG=org.opendroidpdf
PKG_OFFICEPACK=org.opendroidpdf.officepack
ACT=.OpenDroidPDFActivity
DOC_MIME="application/msword"

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null
uia_disable_flaky_ime

echo "[1/6] Install debug APKs (app + Office Pack)"
if ! adb -s "$DEVICE" install -r "$APK" >/dev/null; then
  echo "  install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
  adb -s "$DEVICE" install "$APK" >/dev/null
fi
if ! adb -s "$DEVICE" install -r "$APK_OFFICEPACK" >/dev/null; then
  echo "  Office Pack install failed; attempting uninstall/reinstall" >&2
  adb -s "$DEVICE" uninstall "$PKG_OFFICEPACK" >/dev/null || true
  adb -s "$DEVICE" install "$APK_OFFICEPACK" >/dev/null
fi

echo "[2/6] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/6] Push legacy .doc stub fixture"
adb -s "$DEVICE" shell "run-as $PKG sh -lc 'mkdir -p files && cat > files/word_legacy_stub.doc'" <"$DOC_LOCAL"

echo "[4/6] Launch viewer with legacy .doc (expect Import as PDF dialog)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$DOC_REMOTE" -t "$DOC_MIME" "$PKG/$ACT" >/dev/null
sleep 1.0

ok=0
for _ in $(seq 1 12); do
  if uia_has_text_contains "Import as PDF" >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 0.6
done

if [[ "$ok" != "1" ]]; then
  OUT_PREFIX="${OUT_PREFIX:-tmp_geny_doc_unsupported}"
  _uia_dump_to "${OUT_PREFIX}_ui.xml" || true
  adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_fail.png" || true
  echo "FAIL: expected Import as PDF dialog (missing title text)" >&2
  exit 1
fi

echo "[5/6] Assert dialog message mentions legacy .doc is unsupported"
if ! uia_has_text_contains "legacy .doc" >/dev/null 2>&1; then
  OUT_PREFIX="${OUT_PREFIX:-tmp_geny_doc_unsupported}"
  _uia_dump_to "${OUT_PREFIX}_ui.xml" || true
  adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_fail.png" || true
  echo "FAIL: expected dialog message to mention legacy .doc is unsupported" >&2
  exit 1
fi

uia_tap_any_res_id "android:id/button2" "com.android.internal:id/button2" || uia_tap_text_contains "Dismiss" || true
sleep 0.6

echo "[6/6] Assert no crash"
if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION" && adb -s "$DEVICE" logcat -d | rg -q "Process: ${PKG}"; then
  echo "FAIL: detected crash in logcat" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2
  exit 1
fi

echo "Legacy .doc unsupported smoke complete."

