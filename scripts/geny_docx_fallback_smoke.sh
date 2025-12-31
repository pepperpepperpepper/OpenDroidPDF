#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for Word (.docx/.doc) fallback behavior when conversion is unavailable.
#
# Ensures:
# - selecting a Word document never crashes
# - the app shows an actionable "Import as PDF" dialog (no silent failure)
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_docx_fallback_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
DOCX_LOCAL=${DOCX_LOCAL:-test_assets/word_with_text.docx}
# Keep smokes independent of MANAGE_EXTERNAL_STORAGE by using app-private storage.
DOCX_REMOTE=${DOCX_REMOTE:-/data/data/org.opendroidpdf/files/word_with_text.docx}

PKG=org.opendroidpdf
PKG_OFFICEPACK=org.opendroidpdf.officepack
ACT=.OpenDroidPDFActivity
DOCX_MIME="application/vnd.openxmlformats-officedocument.wordprocessingml.document"

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null
uia_disable_flaky_ime

echo "[1/5] Install debug APK"
if ! adb -s "$DEVICE" install -r "$APK" >/dev/null; then
  echo "  install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
  adb -s "$DEVICE" install "$APK" >/dev/null
fi

echo "[1b/5] Ensure Office Pack is not installed (fallback path)"
adb -s "$DEVICE" uninstall "$PKG_OFFICEPACK" >/dev/null 2>&1 || true

echo "[2/5] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/5] Push docx fixture"
adb -s "$DEVICE" shell "run-as $PKG sh -lc 'mkdir -p files && cat > files/word_with_text.docx'" <"$DOCX_LOCAL"

echo "[4/5] Launch viewer with docx (expect Import as PDF dialog)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$DOCX_REMOTE" -t "$DOCX_MIME" "$PKG/$ACT" >/dev/null
sleep 1.0

# Wait for the dialog to appear (Word import is async; keep the polling low-frequency
# to avoid triggering flaky UiAutomation registration races on some images).
ok=0
for _ in $(seq 1 12); do
  if uia_has_text_contains "Import as PDF" >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 0.6
done

if [[ "$ok" != "1" ]]; then
  OUT_PREFIX="${OUT_PREFIX:-tmp_geny_docx_fallback}"
  _uia_dump_to "${OUT_PREFIX}_ui.xml" || true
  adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_fail.png" || true
  echo "FAIL: expected Import as PDF dialog (missing title text)" >&2
  exit 1
fi

# Dismiss (don't leave the app by pressing "Open in another app" in automation).
uia_tap_any_res_id "android:id/button2" "com.android.internal:id/button2" || uia_tap_text_contains "Dismiss" || true
sleep 0.6

echo "[5/5] Assert no crash"
if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION" && adb -s "$DEVICE" logcat -d | rg -q "Process: ${PKG}"; then
  echo "FAIL: detected crash in logcat" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2
  exit 1
fi

echo "DOCX fallback smoke complete."
