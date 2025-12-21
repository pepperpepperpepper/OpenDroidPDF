#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke: verify DRM/encrypted EPUBs show a specific error instead of failing generically.
#
# Usage:
#   DEVICE=localhost:42865 APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_epub_drm_smoke.sh

DEVICE=${DEVICE:-localhost:42865}
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/drm.epub}
EPUB_REMOTE=${EPUB_REMOTE:-/sdcard/Download/drm.epub}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

echo "[1/6] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/6] Clear app data (avoid stale state)"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/6] Push DRM EPUB fixture"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null

echo "[4/6] Launch viewer with DRM EPUB"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 1.5

echo "[5/6] Assert DRM message is shown"
if ! uia_has_text_contains "DRM"; then
  echo "FAIL: expected DRM error dialog/message" >&2
  adb -s "$DEVICE" exec-out screencap -p > "tmp_geny_epub_drm_smoke.png" || true
  echo "  wrote tmp_geny_epub_drm_smoke.png" >&2
  exit 1
fi

echo "[6/6] Dismiss dialog (best-effort) and print logcat tail"
uia_tap_text_contains "Dismiss" || uia_tap_text_contains "OK" || true
sleep 0.3
adb -s "$DEVICE" logcat -d | tail -n 80

echo "DRM EPUB smoke complete."

