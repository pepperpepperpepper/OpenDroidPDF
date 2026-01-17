#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for Fill & Sign:
# - Push a blank PDF into app-private storage
# - Inject a deterministic signature template + name
# - Place multiple Fill & Sign items (Signature + Name + Date)
# - Save in-place and reopen
# - Pull the saved PDF back to the host and assert it contains:
#   - name/date as FreeText annotations, and
#   - signature as an Ink annotation.
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_fill_sign_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK=${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}
PDF_LOCAL=${PDF_LOCAL:-test_blank.pdf}

PKG=org.opendroidpdf
ACT=.OpenDroidPDFActivity

PDF_REMOTE_REL=files/odp_fill_sign_smoke.pdf
PDF_REMOTE="/data/data/${PKG}/${PDF_REMOTE_REL}"

SIG_REMOTE_REL=files/fill_sign_signature.json
NAME_REMOTE_REL=files/fill_sign_name.txt

OUT_PNG=${OUT_PNG:-/tmp/odp_fill_sign_smoke.png}
OUT_PDF=${OUT_PDF:-/tmp/odp_fill_sign_smoke_saved.pdf}

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/geny_uia.sh"

adb -s "$DEVICE" get-state >/dev/null
uia_disable_flaky_ime

_wm_size() {
  local line
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p > "$out_png"
}

_pull_private_file() {
  local remote_rel="$1"
  local local_path="$2"
  adb -s "$DEVICE" shell "run-as $PKG cat '$remote_rel'" >"$local_path"
}

echo "[1/8] Install debug APK"
if ! adb -s "$DEVICE" install -r "$APK" >/dev/null; then
  echo "  install failed; attempting uninstall/reinstall (signature mismatch?)" >&2
  adb -s "$DEVICE" uninstall "$PKG" >/dev/null || true
  adb -s "$DEVICE" install "$APK" >/dev/null
fi

echo "[2/8] Push blank PDF into app-private storage"
adb -s "$DEVICE" shell "run-as $PKG sh -lc 'mkdir -p files && cat > \"${PDF_REMOTE_REL}\"'" <"$PDF_LOCAL"

echo "[3/8] Inject Fill & Sign fixtures (signature + name)"
SIG_JSON='{"version":1,"aspectRatio":3.2,"strokes":[[[0.05,0.55],[0.25,0.25],[0.45,0.70],[0.65,0.30],[0.85,0.65]],[[0.10,0.80],[0.90,0.80]]]}'
adb -s "$DEVICE" shell "run-as $PKG sh -lc 'cat > \"${SIG_REMOTE_REL}\"'" <<<"$SIG_JSON"
adb -s "$DEVICE" shell "run-as $PKG sh -lc 'cat > \"${NAME_REMOTE_REL}\"'" <<<"ODP_SMOKE_NAME"

echo "[4/8] Launch viewer"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view

read -r W H < <(_wm_size)
X=$((W / 2))
Y_SIG=$((H / 2))
Y_NAME=$((H * 35 / 100))
Y_DATE=$((H * 65 / 100))

_place_fill_sign() {
  local label="$1"
  local tx="$2"
  local ty="$3"

  uia_open_annotate_sheet || { echo "FAIL: could not open Annotate sheet" >&2; exit 1; }
  uia_tap_any_res_id "org.opendroidpdf:id/annotate_action_fill_sign" || uia_tap_text_contains "Fill" || {
    echo "FAIL: Fill & Sign action not found in Annotate sheet" >&2
    exit 1
  }
  sleep 0.6
  uia_tap_text_contains "$label" || {
    echo "FAIL: Fill & Sign dialog did not offer '$label'" >&2
    exit 1
  }
  sleep 0.5
  adb -s "$DEVICE" shell input tap "$tx" "$ty"
  sleep 0.9
}

echo "[5/8] Place Fill & Sign items (Name, Date, Signature)"
_place_fill_sign "Name" "$X" "$Y_NAME"
_place_fill_sign "Date" "$X" "$Y_DATE"
_place_fill_sign "Signature" "$X" "$Y_SIG"

echo "[6/8] Save in-place"
uia_open_navigate_view_sheet || { echo "FAIL: could not open Navigate & View sheet" >&2; exit 1; }
uia_tap_any_res_id "org.opendroidpdf:id/navigate_view_action_save" || uia_tap_text_contains "Save changes" || {
  echo "FAIL: Save changes action not found in Navigate & View sheet" >&2
  exit 1
}
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 1.8

echo "[7/8] Reopen and assert saved PDF persists"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view
_screencap_png "$OUT_PNG"
_pull_private_file "$PDF_REMOTE_REL" "$OUT_PDF"

if ! rg -a -q "ODP_SMOKE_NAME" "$OUT_PDF"; then
  echo "FAIL: saved PDF did not contain name token 'ODP_SMOKE_NAME'" >&2
  echo "  wrote $OUT_PNG" >&2
  echo "  wrote $OUT_PDF" >&2
  exit 1
fi

FREETEXT_COUNT="$(rg -a "/Subtype\\s*/FreeText" "$OUT_PDF" | wc -l | tr -d ' ')"
if [[ -z "$FREETEXT_COUNT" ]] || (( FREETEXT_COUNT < 2 )); then
  echo "FAIL: expected >=2 FreeText annotations (name+date), found $FREETEXT_COUNT" >&2
  echo "  wrote $OUT_PNG" >&2
  echo "  wrote $OUT_PDF" >&2
  exit 1
fi

INK_COUNT="$(rg -a "/Subtype\\s*/Ink" "$OUT_PDF" | wc -l | tr -d ' ')"
if [[ -z "$INK_COUNT" ]] || (( INK_COUNT < 1 )); then
  echo "FAIL: expected >=1 Ink annotation (signature), found $INK_COUNT" >&2
  echo "  wrote $OUT_PNG" >&2
  echo "  wrote $OUT_PDF" >&2
  exit 1
fi

echo "[8/8] OK"
echo "OK: Fill & Sign items persist after reopen (Name/Date/Signature)"
echo "  screenshot: $OUT_PNG"
echo "  pdf: $OUT_PDF"
