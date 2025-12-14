#!/usr/bin/env bash
set -euo pipefail

# Quick Genymotion smoke: open a known PDF, draw, undo, search, and export/share tap.
# Device default: localhost:42865 (Pixel 6 A13 in this workspace).

DEV="${1:-localhost:42865}"
APK="/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk"
PDF_LOCAL="test_blank.pdf"
PDF_REMOTE="/sdcard/Download/test_blank.pdf"

adb -s "$DEV" get-state >/dev/null

echo "[1/7] Install debug APK"
adb -s "$DEV" install -r "$APK" >/dev/null

echo "[2/7] Grant storage perms (best-effort)"
adb -s "$DEV" shell pm grant org.opendroidpdf android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEV" shell pm grant org.opendroidpdf android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEV" shell appops set org.opendroidpdf MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[3/7] Push sample PDF"
adb -s "$DEV" push "$PDF_LOCAL" "$PDF_REMOTE" >/dev/null

echo "[4/7] Launch viewer with sample PDF"
adb -s "$DEV" shell am start -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf -n org.opendroidpdf/.OpenDroidPDFActivity >/dev/null
sleep 2

echo "[5/7] Enter pen mode and draw a stroke (coords tuned for 1080x2400)"
# Tap pen icon (top-right area), then draw a short line on page.
adb -s "$DEV" shell input tap 980 170
sleep 0.5
adb -s "$DEV" shell input touchscreen swipe 540 800 780 820 300
sleep 0.5

echo "[6/7] Undo the stroke (toolbar undo near top-left)"
adb -s "$DEV" shell input tap 200 170
sleep 0.5

echo "[7/7] Simple search invocation and share tap"
# Open search icon (magnifier), type 'test', submit.
adb -s "$DEV" shell input tap 860 170
sleep 0.5
adb -s "$DEV" shell input text 'test'
adb -s "$DEV" shell input keyevent 66
sleep 0.5
# Open share/export (three-dots then share position); coordinates are approximate.
adb -s "$DEV" shell input tap 1040 170
sleep 0.5
adb -s "$DEV" shell input tap 900 260

echo "Smoke complete. Capture logcat snippet:"
adb -s "$DEV" logcat -d | tail -n 60
