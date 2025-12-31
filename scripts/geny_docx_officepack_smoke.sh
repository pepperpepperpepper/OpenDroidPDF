#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for Word (.docx/.doc) behavior when the Office Pack is installed.
#
# Ensures:
# - the main app detects the Office Pack
# - signature verification passes (same signing key)
# - the app binds to the Office Pack service and routes the conversion call
# - when the Office Pack is stubbed, the user sees a clear "unsupported" message (no crash)
#
# Usage:
#   DEVICE=localhost:<port> \
#   APK=/path/to/OpenDroidPDF-debug.apk \
#   APK_OFFICEPACK=/path/to/officepack-debug.apk \
#   ./scripts/geny_docx_officepack_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
APK_OFFICEPACK=${APK_OFFICEPACK:-/mnt/subtitled/opendroidpdf-android-build/officepack/outputs/apk/debug/officepack-debug.apk}
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

echo "[1/6] Install debug APKs (main + Office Pack)"
if ! adb -s "$DEVICE" install -r "$APK" >/dev/null; then
  echo "  main install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
  adb -s "$DEVICE" install "$APK" >/dev/null
fi

if ! adb -s "$DEVICE" install -r "$APK_OFFICEPACK" >/dev/null; then
  echo "  Office Pack install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG_OFFICEPACK" >/dev/null || true
  adb -s "$DEVICE" install "$APK_OFFICEPACK" >/dev/null
fi

echo "[2/6] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/6] Push docx fixture"
adb -s "$DEVICE" shell "run-as $PKG sh -lc 'mkdir -p files && cat > files/word_with_text.docx'" <"$DOCX_LOCAL"

echo "[4/6] Launch viewer with docx (expect Office Pack unsupported message)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$DOCX_REMOTE" -t "$DOCX_MIME" "$PKG/$ACT" >/dev/null

# Wait up to ~6s for the dialog to appear (Word import runs off-thread).
ok=0
for _ in $(seq 1 24); do
  if uia_has_text_contains "Office Pack is installed" >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 0.25
done

if [[ "$ok" != "1" ]]; then
  OUT_PREFIX="${OUT_PREFIX:-tmp_geny_docx_officepack}"
  _uia_dump_to "${OUT_PREFIX}_ui.xml" || true
  adb -s "$DEVICE" exec-out screencap -p >"${OUT_PREFIX}_fail.png" || true
  echo "FAIL: expected Office Pack 'unsupported' message (missing text)" >&2
  exit 1
fi

echo "[5/6] Assert conversion call routed through Office Pack (logcat)"
if ! adb -s "$DEVICE" logcat -d | rg -q "OfficePackConverter.*convertWordToPdf called"; then
  OUT_PREFIX="${OUT_PREFIX:-tmp_geny_docx_officepack}"
  adb -s "$DEVICE" logcat -d | tail -n 200 >"${OUT_PREFIX}_logcat_tail.txt" || true
  echo "FAIL: expected Office Pack service log entry (conversion not routed?)" >&2
  exit 1
fi

echo "[6/6] Assert no crash"
if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION"; then
  echo "FAIL: detected crash in logcat" >&2
  adb -s "$DEVICE" logcat -d | tail -n 200 >&2
  exit 1
fi

echo "DOCX Office Pack smoke complete."

