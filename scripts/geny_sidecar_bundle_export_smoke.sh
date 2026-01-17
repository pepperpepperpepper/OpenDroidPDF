#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke: export the per-document sidecar annotations bundle (JSON) and assert
# it contains the annotations we just created.
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_sidecar_bundle_export_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
EPUB_LOCAL=${EPUB_LOCAL:-test_assets/hello.epub}
EPUB_REMOTE=${EPUB_REMOTE:-/sdcard/Download/hello.epub}
NOTE_TEXT=${NOTE_TEXT:-ODP_BUNDLE_NOTE}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null

_wm_size() {
  # Prints: "<w> <h>"
  local line
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | grep -Eo '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

echo "[1/8] Install debug APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/8] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/8] Push sample EPUB"
adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null

echo "[4/8] Launch viewer with sample EPUB"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$EPUB_REMOTE" -t application/epub+zip "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

echo "[5/8] Create a sidecar note (tap-to-add)"
uia_enter_add_text_mode || { echo "FAIL: add text entry point missing" >&2; exit 1; }
sleep 0.4
read -r w h < <(_wm_size)
adb -s "$DEVICE" shell input tap "$((w * 5 / 10))" "$((h * 45 / 100))"
sleep 0.6

# Some builds prompt for note text immediately (shared "text annotation" dialog).
for _ in $(seq 1 10); do
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    break
  fi
  sleep 0.2
done
if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
  uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || true
  adb -s "$DEVICE" shell input text "$NOTE_TEXT"
  sleep 0.2
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || { echo "FAIL: could not confirm note text dialog" >&2; exit 1; }
  sleep 0.8
fi

echo "[6/8] Export sidecar annotations bundle (Export… → Advanced options…)"
# Remove older artifacts so we can deterministically find the new one.
adb -s "$DEVICE" exec-out run-as "$PKG" sh -c 'rm -f cache/tmpfiles/*_annotations_*.json 2>/dev/null || true'

uia_open_export_sheet_advanced || { echo "FAIL: could not open Export sheet advanced options" >&2; exit 1; }
uia_tap_any_res_id "org.opendroidpdf:id/export_action_export_annotations" || uia_tap_text_contains "Export annotations" || {
  echo "FAIL: Export annotations action not found in Export sheet" >&2
  exit 1
}

# Share chooser is outside our app; back out to keep the test deterministic.
sleep 1.2
adb -s "$DEVICE" shell input keyevent 4
sleep 0.4

echo "[7/8] Pull the exported bundle from app cache"
OUT_JSON="${OUT_JSON:-tmp_geny_sidecar_bundle_export.json}"
latest="$(adb -s "$DEVICE" exec-out run-as "$PKG" sh -c 'ls -t cache/tmpfiles 2>/dev/null | grep -E "_annotations_.*\\.json$" | head -n 1' | tr -d '\r')"
if [[ -z "$latest" ]]; then
  echo "FAIL: no exported *_annotations_*.json found under cache/tmpfiles" >&2
  exit 1
fi
adb -s "$DEVICE" exec-out run-as "$PKG" cat "cache/tmpfiles/$latest" >"$OUT_JSON"
echo "  wrote $OUT_JSON"

echo "[8/8] Assert bundle contains the created note"
python - "$OUT_JSON" <<'PY'
import json, sys

path = sys.argv[1]
with open(path, "rb") as f:
    data = json.loads(f.read().decode("utf-8"))

assert data.get("format") == "opendroidpdf-sidecar", "unexpected format"
assert int(data.get("version", 0)) >= 1, "missing/invalid version"
assert data.get("docId"), "missing docId"

notes = data.get("notes") or []
assert len(notes) >= 1, f"expected notes >= 1, got {len(notes)}"
PY

echo "PASS"
