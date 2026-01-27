#!/usr/bin/env bash
set -euo pipefail

# Genymotion "UI gallery" smoke:
# - Drive OpenDroidPDF through major UI surfaces (home/library, viewer, navigate, annotate, export, search)
# - Capture a numbered screenshot sequence (and optional short screen recordings)
# - Publish a browsable index.html via wtf-upload (through scripts/qa_report_upload.sh)
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_ui_gallery_smoke.sh
#   DEVICE=localhost:<port> OUTDIR=tmp_geny_gallery UPLOAD_PREFIX=qa/ui-gallery/ ./scripts/geny_ui_gallery_smoke.sh
#
# Notes:
# - Outputs only tmp_geny_* artifacts so qa_report_upload.sh uploads safely by default.
# - Set UPLOAD=0 to skip publishing (still captures screenshots).

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/geny_uia.sh"

PKG="org.opendroidpdf"
ACT=".OpenDroidPDFActivity"

OUTDIR="${OUTDIR:-${ROOT_DIR}/tmp_geny_ui_gallery_$(date -u +%Y%m%d_%H%M%S)}"
mkdir -p "$OUTDIR"
OUT_PREFIX="${OUT_PREFIX:-${OUTDIR}/tmp_geny_ui_gallery}"

TITLE="${TITLE:-OpenDroidPDF UI Screenshot Gallery}"
UPLOAD="${UPLOAD:-1}"
UPLOAD_PREFIX="${UPLOAD_PREFIX:-}"

INCLUDE_FORMS="${INCLUDE_FORMS:-1}"
INCLUDE_EPUB="${INCLUDE_EPUB:-1}"
INCLUDE_DOCX="${INCLUDE_DOCX:-1}"

PDF_TEXT_LOCAL="${PDF_TEXT_LOCAL:-${ROOT_DIR}/test_assets/pdf_with_text.pdf}"
PDF_TEXT_REMOTE="${PDF_TEXT_REMOTE:-/sdcard/Download/odp_gallery_pdf_with_text.pdf}"

PDF_NAV_LOCAL="${PDF_NAV_LOCAL:-${ROOT_DIR}/test_pdf.pdf}"
PDF_NAV_REMOTE="${PDF_NAV_REMOTE:-/sdcard/Download/odp_gallery_test_pdf.pdf}"

PDF_FORM_LOCAL="${PDF_FORM_LOCAL:-${ROOT_DIR}/test_assets/pdf_form_nav.pdf}"
PDF_FORM_REMOTE="${PDF_FORM_REMOTE:-/sdcard/Download/odp_gallery_pdf_form_nav.pdf}"

EPUB_LOCAL="${EPUB_LOCAL:-${ROOT_DIR}/test_assets/hello.epub}"
EPUB_REMOTE="${EPUB_REMOTE:-/sdcard/Download/odp_gallery_hello.epub}"

DOCX_LOCAL="${DOCX_LOCAL:-${ROOT_DIR}/test_assets/word_with_text.docx}"
DOCX_REMOTE="${DOCX_REMOTE:-/sdcard/Download/odp_gallery_word_with_text.docx}"

MANIFEST_TXT="${MANIFEST_TXT:-${OUT_PREFIX}_manifest.txt}"
: >"$MANIFEST_TXT"

FAILURES=0
SHOT_N=0

_wait_for_dashboard_ready() {
  # The dashboard ScrollView starts as invisible and only becomes visible after
  # DashboardFragment.renderDashboard() runs. On some devices this can lag after
  # a cold start, so wait for a stable dashboard element before capturing the
  # "home/library" screenshot.
  local rid_dashboard_card="org.opendroidpdf:id/entry_screen_open_document_card_view"
  local timeout_s="${1:-14}"
  local start now
  start="$(date +%s)"
  while true; do
    if uia_has_res_id "$rid_dashboard_card"; then
      return 0
    fi
    # If we landed in the (blank) document host, tap the toolbar Home/Library button
    # to return to the dashboard and retry.
    uia_tap_any_res_id "org.opendroidpdf:id/menu_open" >/dev/null 2>&1 || true
    sleep 0.5
    now="$(date +%s)"
    if (( now - start >= timeout_s )); then
      return 1
    fi
  done
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

_slugify() {
  local s="$1"
  s="${s,,}"
  s="${s// /_}"
  s="${s//[^a-z0-9_]/_}"
  while [[ "$s" == *__* ]]; do s="${s//__/_}"; done
  s="${s##_}"
  s="${s%%_}"
  printf '%s' "${s:-shot}"
}

_shot() {
  local label="$1"
  local note="${2:-}"
  local out slug num
  slug="$(_slugify "$label")"
  SHOT_N=$((SHOT_N + 1))
  printf -v num "%03d" "$SHOT_N"
  out="${OUT_PREFIX}_${num}_${slug}.png"

  if [[ -n "$note" ]]; then
    printf '%s\t%s\n' "$(basename "$out")" "$note" >>"$MANIFEST_TXT"
  else
    printf '%s\n' "$(basename "$out")" >>"$MANIFEST_TXT"
  fi

  adb -s "$DEVICE" exec-out screencap -p >"$out" 2>/dev/null || {
    echo "WARN: screencap failed: $out" >&2
    FAILURES=$((FAILURES + 1))
    return 1
  }
  echo "  wrote $out" >&2
  return 0
}

_wm_size() {
  local line
  line="$(adb -s "$DEVICE" shell wm size 2>/dev/null | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

_bounds_for_rid() {
  local rid="$1"
  local attempts="${UIA_BOUNDS_RETRIES:-10}"
  local sleep_s="${UIA_BOUNDS_RETRY_SLEEP_S:-0.25}"
  local tmp out

  for ((i = 1; i <= attempts; i++)); do
    tmp="$(mktemp)"
    if _uia_dump_to "$tmp" >/dev/null 2>&1; then
      if out="$(python3 - "$tmp" "$rid" <<'PY'
import re, sys, xml.etree.ElementTree as ET

xml_path, rid = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    b = node.attrib.get("bounds", "")
    m = re.match(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]", b)
    if not m:
        continue
    l, t, r, b = map(int, m.groups())
    print(f"{l} {t} {r} {b}")
    raise SystemExit(0)
raise SystemExit(1)
PY
      )"; then
        rm -f "$tmp"
        printf '%s\n' "$out"
        return 0
      fi
    fi
    rm -f "$tmp"
    sleep "$sleep_s"
  done
  return 1
}

_do_scrub_swipes_best_effort() {
  local bounds l t r b y x1 x2
  bounds="$(_bounds_for_rid "org.opendroidpdf:id/page_scrubber" 2>/dev/null || true)"
  if [[ -z "$bounds" ]]; then
    return 0
  fi
  read -r l t r b <<<"$bounds"
  y=$(( (t + b) / 2 ))
  x1=$(( l + 10 ))
  x2=$(( r - 10 ))
  adb -s "$DEVICE" shell input swipe "$x1" "$y" "$x2" "$y" 1200 >/dev/null 2>&1 || true
  sleep 0.5
  adb -s "$DEVICE" shell input swipe "$x2" "$y" "$x1" "$y" 1200 >/dev/null 2>&1 || true
  sleep 0.4
  return 0
}

_try() {
  local label="$1"
  shift
  echo "[gallery] $label" >&2
  if "$@"; then
    return 0
  fi
  echo "WARN: step failed: $label" >&2
  FAILURES=$((FAILURES + 1))
  # Never fail-fast: this is a gallery collector, so keep going and publish whatever we got.
  return 0
}

_launch_home() {
  adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adb -s "$DEVICE" shell am start -W -n "$PKG/$ACT" >/dev/null 2>&1 || true
  sleep 2.0
  return 0
}

_open_file_viewer() {
  local remote_path="$1"
  local mime="$2"
  adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$remote_path" -t "$mime" "$PKG/$ACT" >/dev/null
  sleep 2.2
  uia_assert_in_document_view
  return 0
}

_record_screen_best_effort() {
  local label="$1"
  local seconds="$2"
  shift 2
  local remote
  local local_mp4
  remote="/sdcard/Download/$(basename "${OUT_PREFIX}_$(_slugify "$label").mp4")"
  local_mp4="${OUT_PREFIX}_$(_slugify "$label").mp4"

  adb -s "$DEVICE" shell rm -f "$remote" >/dev/null 2>&1 || true
  adb -s "$DEVICE" shell screenrecord --time-limit "$seconds" "$remote" >/dev/null 2>&1 &
  local rec_pid="$!"
  sleep 0.8

  # Execute provided actions while recording.
  "$@" || true

  wait "$rec_pid" >/dev/null 2>&1 || true
  adb -s "$DEVICE" pull "$remote" "$local_mp4" >/dev/null 2>&1 || true
  if [[ -f "$local_mp4" ]]; then
    printf '%s\t%s\n' "$(basename "$local_mp4")" "screenrecord: ${label}" >>"$MANIFEST_TXT"
    echo "  wrote $local_mp4" >&2
  else
    echo "WARN: screenrecord missing: $local_mp4" >&2
    return 1
  fi
  return 0
}

adb -s "$DEVICE" get-state >/dev/null

APK_REAL="$(_resolve_apk)"
echo "[1/8] Install APK: $APK_REAL" >&2
_install_apk "$APK_REAL"

echo "[2/8] Reset app state + grant storage perms (best-effort)" >&2
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null 2>&1 || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1 || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE >/dev/null 2>&1 || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1 || true

echo "[3/8] Capture Library/Home screen" >&2
_try "launch home" _launch_home
_try "wait for dashboard to render" _wait_for_dashboard_ready
_try "screenshot: home/library" _shot "home_library" "Library/Home screen"

echo "[4/8] Stage fixtures to /sdcard/Download" >&2
adb -s "$DEVICE" push "$PDF_NAV_LOCAL" "$PDF_NAV_REMOTE" >/dev/null
adb -s "$DEVICE" push "$PDF_TEXT_LOCAL" "$PDF_TEXT_REMOTE" >/dev/null
if [[ "$INCLUDE_FORMS" == "1" ]]; then adb -s "$DEVICE" push "$PDF_FORM_LOCAL" "$PDF_FORM_REMOTE" >/dev/null || true; fi
if [[ "$INCLUDE_EPUB" == "1" ]]; then adb -s "$DEVICE" push "$EPUB_LOCAL" "$EPUB_REMOTE" >/dev/null || true; fi
if [[ "$INCLUDE_DOCX" == "1" ]]; then adb -s "$DEVICE" push "$DOCX_LOCAL" "$DOCX_REMOTE" >/dev/null || true; fi

echo "[5/8] PDF viewer: navigation/search/annotate/export" >&2
_try "open PDF (multi-page fixture)" _open_file_viewer "$PDF_NAV_REMOTE" "application/pdf"
_try "screenshot: PDF viewer (multi-page)" _shot "pdf_viewer_multipage" "PDF viewer (multi-page fixture; shows page indicator/scrubber)"

_try "open Navigate & View sheet" uia_open_navigate_view_sheet
_try "screenshot: Navigate & View" _shot "navigate_view_sheet" "Navigate & View bottom sheet (page scrubber, view toggles)"
adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 0.4

_try "open Annotate sheet" uia_open_annotate_sheet
_try "screenshot: Annotate sheet" _shot "annotate_sheet" "Annotate bottom sheet (tool chooser)"
adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 0.35

_try "enter draw mode" uia_enter_draw_mode
sleep 0.6
_try "screenshot: draw mode toolbar" _shot "draw_mode" "Draw mode (toolbar)"

read -r W H < <(_wm_size)
X1=$((W * 25 / 100))
X2=$((W * 75 / 100))
Y1=$((H * 55 / 100))
Y2=$((H * 58 / 100))
adb -s "$DEVICE" shell input swipe "$X1" "$Y1" "$X2" "$Y2" 260 >/dev/null 2>&1 || true
sleep 0.6
_try "screenshot: with ink stroke" _shot "draw_mode_with_ink" "Draw mode (after drawing a stroke)"

_try "open pen settings dialog" uia_tap_any_res_id "org.opendroidpdf:id/menu_pen_settings" "org.opendroidpdf:id/menu_ink_color" "org.opendroidpdf:id/menu_pen_size"
sleep 0.8
_try "screenshot: pen settings" _shot "pen_settings_dialog" "Pen settings dialog (size/color)"
adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 0.4

_try "switch to eraser mode" uia_tap_any_res_id "org.opendroidpdf:id/menu_erase" || true
sleep 0.35
_try "open eraser size dialog" uia_tap_any_res_id "org.opendroidpdf:id/menu_eraser_size" || true
sleep 0.8
_try "screenshot: eraser size" _shot "eraser_size_dialog" "Eraser size dialog"
adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 0.4

_try "accept annotation mode" uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || true
sleep 0.8
_try "screenshot: back in viewer" _shot "viewer_after_annotate" "Viewer after accepting annotations"

_try "open annotations list" uia_open_annotations_list
sleep 0.8
_try "screenshot: annotations list" _shot "annotations_list" "Annotations list (comments)"
adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 0.4

_try "open export sheet" uia_open_export_sheet
sleep 0.8
_try "screenshot: export sheet" _shot "export_sheet" "Export sheet"
adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 0.4

echo "[gallery] Optional: short screenrecord (scrub preview)" >&2
if [[ "${RECORD_SCRUB:-1}" == "1" ]]; then
  # Best-effort: record a short scrub interaction if the on-page scrubber exists.
  _try "screenrecord: scrub preview" _record_screen_best_effort "scrub_preview" 8 _do_scrub_swipes_best_effort
fi

echo "[6/8] PDF text/search UI" >&2
_try "open PDF (text fixture)" _open_file_viewer "$PDF_TEXT_REMOTE" "application/pdf"
_try "screenshot: PDF viewer (text fixture)" _shot "pdf_viewer_text" "PDF viewer (text fixture; used for search/text UI)"

_try "open search" uia_tap_any_res_id "org.opendroidpdf:id/menu_search" || true
sleep 0.8
adb -s "$DEVICE" shell input text "OpenDroid" >/dev/null 2>&1 || true
adb -s "$DEVICE" shell input keyevent 66 >/dev/null 2>&1 || true
sleep 1.0
_try "screenshot: search results" _shot "search" "Search UI (query + results)"
adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 0.35

echo "[7/8] Optional: forms PDF" >&2
if [[ "$INCLUDE_FORMS" == "1" ]]; then
  _try "open PDF (form fixture)" _open_file_viewer "$PDF_FORM_REMOTE" "application/pdf" || true
  _try "enable forms highlight" uia_enable_forms_highlight || true
  _try "screenshot: forms highlight" _shot "forms_highlight" "Forms highlight enabled"
fi

echo "[8/8] Optional: EPUB + DOCX" >&2
if [[ "$INCLUDE_DOCX" == "1" ]]; then
  _try "open DOCX (word_with_text)" _open_file_viewer "$DOCX_REMOTE" "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || true
  _try "screenshot: DOCX viewer" _shot "docx_viewer" "DOCX viewer"
fi

if [[ "$INCLUDE_EPUB" == "1" ]]; then
  _try "open EPUB (hello)" _open_file_viewer "$EPUB_REMOTE" "application/epub+zip" || true
  _try "screenshot: EPUB viewer" _shot "epub_viewer" "EPUB viewer"
  _try "open TOC" uia_tap_any_res_id "org.opendroidpdf:id/menu_toc" || uia_tap_text_contains "Contents" || true
  sleep 0.8
  _try "screenshot: EPUB TOC" _shot "epub_toc" "Contents/TOC"
  adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 0.35
fi

echo "[gallery] Artifacts: $OUTDIR" >&2
echo "[gallery] Manifest: $MANIFEST_TXT" >&2

if [[ "$UPLOAD" == "1" ]]; then
  echo "[gallery] Publishing report via wtf-upload..." >&2
  shopt -s nullglob
  artifacts=("$OUTDIR"/tmp_geny_ui_gallery_* "$OUTDIR"/tmp_geny_ui_gallery_*.png "$OUTDIR"/tmp_geny_ui_gallery_*.mp4 "$OUTDIR"/tmp_geny_ui_gallery_*.txt)
  shopt -u nullglob
  if [[ "${#artifacts[@]}" -eq 0 ]]; then
    echo "FAIL: no artifacts found to upload in $OUTDIR" >&2
    exit 2
  fi

  report_out="${OUTDIR}/_qa_report"
  report_url="$("${ROOT_DIR}/scripts/qa_report_upload.sh" --title "$TITLE" --prefix "$UPLOAD_PREFIX" --outdir "$report_out" "${artifacts[@]}")"
  printf '%s\n' "$report_url"
  printf '\nReport: %s\n' "$report_url" >>"$MANIFEST_TXT"
else
  echo "UPLOAD=0 (skipping publish)" >&2
fi

if [[ "$FAILURES" -gt 0 ]]; then
  echo "WARN: gallery completed with $FAILURES failure(s)" >&2
  exit 1
fi

echo "OK: gallery smoke complete" >&2
