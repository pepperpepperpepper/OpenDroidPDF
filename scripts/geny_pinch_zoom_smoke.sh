#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke: open a PDF and repeatedly pinch-zoom in.
#
# Uses a small UIAutomator v1 test jar (compiled on the fly) to perform multi-touch.
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_pinch_zoom_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_assets/pdf_with_text.pdf}
PDF_REMOTE=${PDF_REMOTE:-/sdcard/Download/odp_zoom_smoke.pdf}
PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

ANDROID_PLATFORM=${ANDROID_PLATFORM:-/home/arch/android-sdk/platforms/android-34}
ANDROID_JAR="$ANDROID_PLATFORM/android.jar"
UIAUTOMATOR_JAR="$ANDROID_PLATFORM/uiautomator.jar"
JUNIT_JAR=${JUNIT_JAR:-/home/arch/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar}
D8=${D8:-/home/arch/android-sdk/build-tools/35.0.1/d8}

SRC_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
JAVA_SRC="$SRC_DIR/uia/ZoomPinchTest.java"
JAVA_SOURCES=()
while IFS= read -r -d '' f; do JAVA_SOURCES+=("$f"); done < <(find "$SRC_DIR/uia" -name '*.java' -print0)

TMPDIR=${TMPDIR:-/tmp}
BUILD_DIR="$TMPDIR/odp_uia_build"
CLASSES_JAR="$BUILD_DIR/odp-uia-zoom-classes.jar"
JAR_LOCAL="$BUILD_DIR/odp-uia-zoom-dex.jar"
JAR_REMOTE=/sdcard/odp-uia-zoom.jar

export UIA_DOC_VIEW_TIMEOUT_S="${UIA_DOC_VIEW_TIMEOUT_S:-25}"

source "$SRC_DIR/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

echo "[1/6] Build UIAutomator test jar"
mkdir -p "$BUILD_DIR/classes"
javac -source 8 -target 8 -Xlint:none \
  -cp "$ANDROID_JAR:$UIAUTOMATOR_JAR:$JUNIT_JAR" \
  -d "$BUILD_DIR/classes" \
  "${JAVA_SOURCES[@]}"
jar cf "$CLASSES_JAR" -C "$BUILD_DIR/classes" .
"$D8" --release --min-api 21 \
  --lib "$ANDROID_JAR" \
  --classpath "$UIAUTOMATOR_JAR" \
  --classpath "$JUNIT_JAR" \
  --output "$JAR_LOCAL" \
  "$CLASSES_JAR"

echo "[2/6] Install debug APK (clear data + perms)"
_install_out="$(adb -s "$DEVICE" install -r "$APK" 2>&1 || true)"
if [[ "$_install_out" != *"Success"* ]]; then
  if [[ "$_install_out" == *"INSTALL_FAILED_UPDATE_INCOMPATIBLE"* ]]; then
    echo "[2/6] Signature mismatch; uninstalling $PKG and retrying install"
    adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
    adb -s "$DEVICE" install -r "$APK" >/dev/null
  else
    echo "$_install_out" >&2
    exit 1
  fi
fi

adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[3/6] Push sample PDF"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE" >/dev/null

echo "[4/6] Launch viewer with sample PDF"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[5/6] Push + run pinch-zoom test (progressive zoom-in)"
adb -s "$DEVICE" push "$JAR_LOCAL" "$JAR_REMOTE" >/dev/null
_uia_out="$(adb -s "$DEVICE" shell uiautomator runtest "$JAR_REMOTE" -c org.opendroidpdf.uia.ZoomPinchTest#testProgressiveZoomInDoesNotCrash 2>&1 || true)"
printf '%s\n' "$_uia_out"
if printf '%s\n' "$_uia_out" | grep -q "FAILURES!!!"; then
  echo "[5/6] FAIL: UIAutomator pinch+drag test failed" >&2
  exit 1
fi

echo "[6/6] Logcat tail"
adb -s "$DEVICE" logcat -d | tail -n 120

echo "Pinch zoom smoke complete."
