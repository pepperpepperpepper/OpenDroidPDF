#!/usr/bin/env bash
set -euo pipefail

# Genymotion smoke for cross-page drag-move (Acrobat parity):
# - Push a small 2-page fixture PDF to /sdcard/Download
# - Open it via DocumentsUI (content:// grant so Save is available)
# - Create a FreeText annotation on page 1 and drag it onto page 2
# - Create an Ink annotation on page 1 (Fill & Sign signature if possible, else Draw) and drag it onto page 2
# - Save in-place
# - Pull the saved PDF back to host, render pages 1/2 with Poppler (pdftoppm), and assert:
#     - Page 1 render stays "clean" (no new marks)
#     - Page 2 render contains new marks (moved annotations)
# - Optionally publish a static HTML report via wtf-upload (scripts/qa_report_upload.sh)
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_cross_page_drag_move_smoke.sh
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF.apk UPLOAD=0 ./scripts/geny_cross_page_drag_move_smoke.sh

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/geny_uia.sh"
source "${ROOT_DIR}/scripts/lib/geny_pdf_smoke_ocr.sh"

PKG="org.opendroidpdf"
ACT=".OpenDroidPDFActivity"

PDF_LOCAL="${PDF_LOCAL:-${ROOT_DIR}/test_assets/pdf_form_nav.pdf}"
PDF_REMOTE_PATH="${PDF_REMOTE_PATH:-/sdcard/Download/odp_cross_page_drag_move_smoke.pdf}"
TOKEN="${TOKEN:-CROSSPAGEMOVETESTTOKEN}"

OUTDIR="${OUTDIR:-.}"
mkdir -p "$OUTDIR"
OUT_PREFIX="${OUT_PREFIX:-${OUTDIR}/tmp_geny_cross_page_drag_move_smoke_$(date -u +%Y%m%d_%H%M%S)}"
LOGCAT_TXT="${LOGCAT_TXT:-${OUT_PREFIX}_logcat.txt}"

TITLE="${TITLE:-OpenDroidPDF Cross-Page Drag-Move Smoke}"
UPLOAD="${UPLOAD:-1}"
UPLOAD_PREFIX="${UPLOAD_PREFIX:-}"

_wm_size() {
  local line
  line="$(adb -s "$DEVICE" shell wm size 2>/dev/null | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

_fail_if_fatal_logcat() {
  if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION|Process ${PKG} \\(pid [0-9]+\\) has died|ANR in"; then
    echo "FAIL: detected crash/ANR in logcat" >&2
    adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|AndroidRuntime|ANR in|${PKG}" | tail -n 260 >&2 || true
    return 1
  fi
  return 0
}

_resolve_apk() {
  if [[ -n "${APK}" ]]; then
    echo "${APK}"
    return 0
  fi
  local debug_apk="/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk"
  if [[ -f "$debug_apk" ]]; then
    echo "$debug_apk"
    return 0
  fi
  local latest
  latest="$(ls -1 /home/arch/fdroid/repo/org.opendroidpdf_*.apk 2>/dev/null | sort -V | tail -n 1 || true)"
  if [[ -z "${latest}" ]]; then
    echo "FAIL: could not find a default APK (set APK=...)" >&2
    return 1
  fi
  echo "${latest}"
}

_install_apk() {
  local apk="$1"
  local out
  out="$(adb -s "$DEVICE" install -r -t "$apk" 2>&1 || true)"
  if [[ "$out" == *"Success"* ]]; then
    return 0
  fi
  if [[ "$out" == *"INSTALL_FAILED_UPDATE_INCOMPATIBLE"* ]]; then
    adb -s "$DEVICE" uninstall "$PKG" >/dev/null 2>&1 || true
    adb -s "$DEVICE" install -r -t "$apk" >/dev/null
    return 0
  fi
  printf '%s\n' "$out" >&2
  return 1
}

_screencap_png() {
  local out_png="$1"
  adb -s "$DEVICE" exec-out screencap -p > "$out_png"
}

_tap_doc_center() {
  local w h x y
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y=$((h * 45 / 100))
  adb -s "$DEVICE" shell input tap "$x" "$y"
}

_ensure_on_page1_best_effort() {
  # Show chrome if needed so the Navigate & View sheet can be opened.
  for _ in $(seq 1 3); do
    if uia_has_res_id "org.opendroidpdf:id/page_indicator"; then
      break
    fi
    _tap_doc_center
    sleep 0.6
  done
  if ! uia_has_res_id "org.opendroidpdf:id/page_indicator"; then
    return 0
  fi
  if ! uia_open_navigate_view_sheet; then
    return 0
  fi
  for _ in $(seq 1 6); do
    uia_tap_any_res_id "org.opendroidpdf:id/navigate_view_page_prev" || true
    sleep 0.35
  done
  adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 0.6
}

_open_pdf_via_documentsui() {
  local fname
  fname="$(basename "$PDF_REMOTE_PATH")"

  adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
  adb -s "$DEVICE" logcat -c >/dev/null || true
  adb -s "$DEVICE" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT" >/dev/null
  sleep 1.3

  uia_tap_any_res_id "org.opendroidpdf:id/entry_screen_open_document_card_view" "org.opendroidpdf:id/menu_open_document" || {
    echo "FAIL: could not tap dashboard Open document entry point" >&2
    return 1
  }
  sleep 1.4

  if ! uia_has_text_contains "$fname"; then
    uia_tap_docsui_roots_drawer || {
      echo "FAIL: could not open DocumentsUI roots drawer" >&2
      return 1
    }
    sleep 0.6
    uia_tap_text_contains "Downloads" || {
      echo "FAIL: could not switch DocumentsUI to Downloads root" >&2
      return 1
    }
    sleep 0.9
  fi

  # If the file isn't visible, use DocumentsUI search to locate it.
  if ! uia_has_text_contains "$fname"; then
    uia_tap_any_res_id "com.android.documentsui:id/option_menu_search" || uia_tap_desc "Search" || true
    sleep 0.6
    adb -s "$DEVICE" shell input text "$fname" >/dev/null 2>&1 || true
    sleep 0.6
  fi

  for _ in $(seq 1 30); do
    if uia_has_text_contains "$fname"; then
      break
    fi
    sleep 0.25
  done
  uia_tap_text_contains "$fname" || {
    echo "FAIL: could not select $fname in DocumentsUI" >&2
    return 1
  }

  uia_assert_in_document_view
}

_render_pdf_page_to_png() {
  local pdf="$1"
  local page="$2"
  local out_png="$3"
  local tmpdir prefix
  tmpdir="$(mktemp -d -t odp_pdf_render_XXXXXX)"
  prefix="$tmpdir/out"
  pdftoppm -f "$page" -l "$page" -r 160 -singlefile -png "$pdf" "$prefix" >/dev/null
  mv -f -- "${prefix}.png" "$out_png"
  rm -rf -- "$tmpdir"
}

_diff_metrics() {
  local before_png="$1"
  local after_png="$2"
  python3 - "$before_png" "$after_png" <<'PY'
import sys
from PIL import Image

before_path, after_path = sys.argv[1], sys.argv[2]
a = Image.open(before_path).convert("RGB")
b = Image.open(after_path).convert("RGB")
if a.size != b.size:
    raise SystemExit(f"FAIL: render size mismatch: {a.size} vs {b.size}")

w, h = a.size
pa = a.load()
pb = b.load()

step = 3
threshold = 90  # sum(|dr|,|dg|,|db|)
changed = 0
new_dark = 0

for y in range(0, h, step):
    for x in range(0, w, step):
        r1, g1, b1 = pa[x, y]
        r2, g2, b2 = pb[x, y]
        if abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2) > threshold:
            changed += 1
        # Track pixels that go from "nearly white" to "dark" as a proxy for new marks.
        if (r1 >= 245 and g1 >= 245 and b1 >= 245) and (r2 < 80 and g2 < 80 and b2 < 80):
            new_dark += 1

print(f"{changed} {new_dark}")
PY
}

_require_selection_bbox() {
  local png="$1"
  local label="$2"
  local bbox
  bbox="$(_selection_box_bbox_px "$png" || true)"
  if [[ -z "${bbox}" ]]; then
    echo "FAIL: selection box not detected ($label)" >&2
    echo "  screenshot: $png" >&2
    return 1
  fi
  printf '%s\n' "$bbox"
  return 0
}

_drag_selection_down_across_pages() {
  local selected_png="$1"
  local label="$2"
  local dur_ms="${3:-1400}"
  local w h
  read -r w h < <(_wm_size)

  local bbox
  bbox="$(_require_selection_bbox "$selected_png" "$label")" || return 1
  local x0 y0 x1 y1
  read -r x0 y0 x1 y1 <<<"$bbox"

  local move_x move_y move_y2
  move_x=$(((x0 + x1) / 2))
  move_y=$(((y0 + y1) / 2))
  move_y2=$((move_y + (h * 55 / 100)))
  if (( move_y2 > h - 12 )); then move_y2=$((h - 12)); fi

  adb -s "$DEVICE" shell input swipe "$move_x" "$move_y" "$move_x" "$move_y2" "$dur_ms"
  sleep 0.9
  _fail_if_fatal_logcat || return 1
  return 0
}

_create_freetext_on_page1() {
  local w h x y
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y=$((h * 26 / 100))

  uia_enter_add_text_mode || { echo "FAIL: Add text entry point missing" >&2; return 1; }
  sleep 0.6
  adb -s "$DEVICE" shell input tap "$x" "$y"

  for _ in $(seq 1 20); do
    if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      break
    fi
    sleep 0.25
  done
  uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || true
  adb -s "$DEVICE" shell input text "$TOKEN" >/dev/null 2>&1 || true
  sleep 0.2
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
    echo "FAIL: could not confirm text dialog" >&2
    return 1
  }
  sleep 1.1
  _fail_if_fatal_logcat || return 1

  # Ensure selection is visible.
  adb -s "$DEVICE" shell input tap "$x" "$y"
  sleep 0.7
}

_create_signature_or_ink_on_page1() {
  local w h x y
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y=$((h * 40 / 100))

  if adb -s "$DEVICE" shell "run-as $PKG id" >/dev/null 2>&1; then
    local sig_json
    sig_json='{"version":1,"aspectRatio":3.2,"strokes":[[[0.05,0.55],[0.25,0.25],[0.45,0.70],[0.65,0.30],[0.85,0.65]],[[0.10,0.80],[0.90,0.80]]]}'
    adb -s "$DEVICE" shell "run-as $PKG sh -lc 'cat > \"files/fill_sign_signature.json\"'" <<<"$sig_json" || true

    uia_open_annotate_sheet || { echo "FAIL: could not open Annotate sheet" >&2; return 1; }
    uia_tap_any_res_id "org.opendroidpdf:id/annotate_action_fill_sign" || uia_tap_text_contains "Fill" || {
      echo "FAIL: Fill & Sign action not found in Annotate sheet" >&2
      return 1
    }
    sleep 0.8
    uia_tap_text_contains "Signature" || {
      echo "FAIL: Fill & Sign dialog did not offer 'Signature'" >&2
      return 1
    }
    sleep 0.6
    adb -s "$DEVICE" shell input tap "$x" "$y"
    sleep 1.0
    _fail_if_fatal_logcat || return 1
    # Signature should be selected; if not, tap to select.
    adb -s "$DEVICE" shell input tap "$x" "$y" >/dev/null 2>&1 || true
    sleep 0.7
    return 0
  fi

  echo "WARN: run-as unavailable; using Draw tool ink instead of Fill & Sign signature" >&2
  uia_enter_draw_mode || { echo "FAIL: Draw entry point missing" >&2; return 1; }
  sleep 0.6
  local x1 x2 y1
  x1=$((w * 25 / 100))
  x2=$((w * 75 / 100))
  y1=$((h * 40 / 100))
  adb -s "$DEVICE" shell input swipe "$x1" "$y1" "$x2" "$y1" 320
  sleep 0.5
  uia_tap_any_res_id "org.opendroidpdf:id/accept_image_button" "org.opendroidpdf:id/menu_accept" || {
    echo "FAIL: accept button not found after drawing" >&2
    return 1
  }
  sleep 1.3
  # Exit draw mode so taps select rather than draw.
  uia_tap_any_res_id "org.opendroidpdf:id/menu_cancel" "org.opendroidpdf:id/cancel_image_button" || true
  sleep 0.8
  adb -s "$DEVICE" shell input tap "$x" "$y"
  sleep 0.8
  return 0
}

adb -s "$DEVICE" get-state >/dev/null

if ! command -v pdftoppm >/dev/null 2>&1; then
  echo "FAIL: pdftoppm not found (install poppler)." >&2
  exit 2
fi

APK_REAL="$(_resolve_apk)"
echo "[1/10] Install APK: $APK_REAL"
_install_apk "$APK_REAL"

echo "[2/10] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true

echo "[3/10] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null

echo "[4/10] Open PDF via DocumentsUI"
_open_pdf_via_documentsui
sleep 1.1
_ensure_on_page1_best_effort || true

echo "[5/10] Create FreeText on page 1"
SHOT_TEXT_CREATED="${SHOT_TEXT_CREATED:-${OUT_PREFIX}_text_created.png}"
_create_freetext_on_page1
_screencap_png "$SHOT_TEXT_CREATED"

echo "[6/10] Drag FreeText down across pages (expect drop on page 2)"
_drag_selection_down_across_pages "$SHOT_TEXT_CREATED" "FreeText after create/select"
SHOT_TEXT_MOVED="${SHOT_TEXT_MOVED:-${OUT_PREFIX}_text_moved.png}"
_screencap_png "$SHOT_TEXT_MOVED"

echo "[7/10] Create signature/ink on page 1"
_create_signature_or_ink_on_page1
SHOT_INK_CREATED="${SHOT_INK_CREATED:-${OUT_PREFIX}_ink_created.png}"
_screencap_png "$SHOT_INK_CREATED"

echo "[8/10] Drag ink down across pages (expect drop on page 2)"
_drag_selection_down_across_pages "$SHOT_INK_CREATED" "Ink after create/select"
SHOT_INK_MOVED="${SHOT_INK_MOVED:-${OUT_PREFIX}_ink_moved.png}"
_screencap_png "$SHOT_INK_MOVED"

echo "[9/10] Save in-place and pull saved PDF"
uia_save_changes || { echo "FAIL: Save changes entry point missing" >&2; exit 1; }
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 3.2
_fail_if_fatal_logcat || exit 1

SAVED_PDF="${SAVED_PDF:-${OUT_PREFIX}_saved.pdf}"
adb -s "$DEVICE" pull "$PDF_REMOTE_PATH" "$SAVED_PDF" >/dev/null
echo "  wrote $SAVED_PDF"

echo "[10/10] Render baseline vs saved (page 1 should stay clean; page 2 should contain marks)"
BASE_P1="${BASE_P1:-${OUT_PREFIX}_base_p1.png}"
BASE_P2="${BASE_P2:-${OUT_PREFIX}_base_p2.png}"
AFTER_P1="${AFTER_P1:-${OUT_PREFIX}_after_p1.png}"
AFTER_P2="${AFTER_P2:-${OUT_PREFIX}_after_p2.png}"
_render_pdf_page_to_png "$PDF_LOCAL" 1 "$BASE_P1"
_render_pdf_page_to_png "$PDF_LOCAL" 2 "$BASE_P2"
_render_pdf_page_to_png "$SAVED_PDF" 1 "$AFTER_P1"
_render_pdf_page_to_png "$SAVED_PDF" 2 "$AFTER_P2"

read -r changed1 newdark1 < <(_diff_metrics "$BASE_P1" "$AFTER_P1")
read -r changed2 newdark2 < <(_diff_metrics "$BASE_P2" "$AFTER_P2")
echo "  page1: changed=$changed1 new_dark=$newdark1"
echo "  page2: changed=$changed2 new_dark=$newdark2"

# Page 2 must clearly have new marks.
if (( changed2 < 120 || newdark2 < 35 )); then
  echo "FAIL: expected page 2 to contain moved marks (changed2>=120 and newdark2>=35)" >&2
  echo "  renders: $BASE_P2 $AFTER_P2" >&2
  exit 1
fi

# Page 1 should be mostly unchanged (no marks left behind).
if (( changed1 > 60 || newdark1 > 15 )); then
  echo "FAIL: expected page 1 to remain clean after cross-page moves (changed1<=60 and newdark1<=15)" >&2
  echo "  renders: $BASE_P1 $AFTER_P1" >&2
  exit 1
fi

# Quick sanity: ensure saved PDF contains expected annotation subtypes.
if ! rg -a -q "/Subtype\\s*/FreeText" "$SAVED_PDF"; then
  echo "FAIL: saved PDF did not contain a FreeText annotation (expected after Add text)" >&2
  exit 1
fi
if ! rg -a -q "/Subtype\\s*/Ink" "$SAVED_PDF"; then
  echo "WARN: saved PDF did not contain an Ink annotation subtype; ink move may not have embedded as Ink" >&2
fi

adb -s "$DEVICE" logcat -d -v time >"$LOGCAT_TXT" || true

if [[ "$UPLOAD" == "1" ]]; then
  echo "[report] Publishing report via wtf-upload..." >&2
  shopt -s nullglob
  artifacts=("${OUT_PREFIX}"*.png "${OUT_PREFIX}"*.txt "${OUT_PREFIX}"*.xml "${OUT_PREFIX}"*.log "${OUT_PREFIX}"*.mp4)
  shopt -u nullglob
  if [[ "${#artifacts[@]}" -eq 0 ]]; then
    echo "FAIL: no artifacts found to upload for prefix $OUT_PREFIX" >&2
    exit 2
  fi
  report_out="${OUTDIR}/_qa_report"
  report_url="$("${ROOT_DIR}/scripts/qa_report_upload.sh" --title "$TITLE" --prefix "$UPLOAD_PREFIX" --outdir "$report_out" "${artifacts[@]}")"
  printf '%s\n' "$report_url"
else
  echo "UPLOAD=0 (skipping publish)" >&2
fi

echo "OK: cross-page drag-move smoke complete"
