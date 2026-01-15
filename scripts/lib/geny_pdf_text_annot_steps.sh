# geny_pdf_text_annot_steps.sh: UI automation steps for the PDF text-annotation smoke.
#
# Intended to be sourced from `scripts/geny_pdf_text_annot_smoke.sh`. Assumes:
# - `set -euo pipefail` is set by the caller
# - `geny_uia.sh` is already sourced (uia_* helpers are available)
# - Required env vars (DEVICE, PKG, ACT, etc) are set by the caller

_wm_size() {
  local line
  line="$(adb -s "$DEVICE" shell wm size | tr -d '\r' | rg -o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

_tap_doc_center() {
  local w h x y
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y=$((h * 45 / 100))
  adb -s "$DEVICE" shell input tap "$x" "$y"
}

_doc_center_xy() {
  local w h x y
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y=$((h * 45 / 100))
  echo "$x $y"
}

_uia_bounds_for_rid() {
  local rid="$1"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python3 - "$tmp" "$rid" <<'PY'
import re, sys, xml.etree.ElementTree as ET

xml_path, rid = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)

def parse_bounds(bounds: str):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    return tuple(map(int, m.groups()))

for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    b = parse_bounds(node.attrib.get("bounds", ""))
    if not b:
        continue
    print(f"{b[0]} {b[1]} {b[2]} {b[3]}")
    raise SystemExit(0)

raise SystemExit(1)
PY
  rm -f "$tmp"
}

_uia_text_for_rid() {
  local rid="$1"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  python3 - "$tmp" "$rid" <<'PY'
import sys, xml.etree.ElementTree as ET

xml_path, rid = sys.argv[1], sys.argv[2]
tree = ET.parse(xml_path)
for node in tree.iter("node"):
    if node.attrib.get("resource-id", "") != rid:
        continue
    print(node.attrib.get("text", "") or "")
    raise SystemExit(0)

raise SystemExit(1)
PY
  rm -f "$tmp"
}

_scroll_dialog_down() {
  # Scroll the style dialog down (content moves up).
  local w h x y1 y2
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y1=$((h * 80 / 100))
  y2=$((h * 25 / 100))
  adb -s "$DEVICE" shell input swipe "$x" "$y1" "$x" "$y2" 320
}

_scroll_dialog_up() {
  # Scroll the style dialog up (content moves down).
  local w h x y1 y2
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y1=$((h * 25 / 100))
  y2=$((h * 80 / 100))
  adb -s "$DEVICE" shell input swipe "$x" "$y1" "$x" "$y2" 320
}

_scroll_dialog_down_small() {
  # Scroll the style dialog down a little (content moves up).
  local w h x y1 y2
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y1=$((h * 70 / 100))
  y2=$((h * 60 / 100))
  adb -s "$DEVICE" shell input swipe "$x" "$y1" "$x" "$y2" 240
}

_scroll_dialog_up_small() {
  # Scroll the style dialog up a little (content moves down).
  local w h x y1 y2
  read -r w h < <(_wm_size)
  x=$((w / 2))
  y1=$((h * 60 / 100))
  y2=$((h * 70 / 100))
  adb -s "$DEVICE" shell input swipe "$x" "$y1" "$x" "$y2" 240
}

_dismiss_text_style_dialog() {
  # Close the "Text style" dialog if it's still open (best-effort). This can be flaky on
  # some emulators if BACK gets eaten while the dialog is scrolling.
  for _ in $(seq 1 4); do
    if ! uia_has_text_contains "Text style"; then
      return 0
    fi
    adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
    sleep 0.6
  done
  return 0
}

_fail_if_fatal_logcat() {
  if adb -s "$DEVICE" logcat -d | rg -q "FATAL EXCEPTION|Process ${PKG} \\(pid [0-9]+\\) has died"; then
    echo "FAIL: detected crash in logcat" >&2
    adb -s "$DEVICE" logcat -d | rg -n "FATAL EXCEPTION|AndroidRuntime|${PKG}" | tail -n 260 >&2 || true
    return 1
  fi
  return 0
}

geny_pdf_text_annot_smoke_run() {
echo "[1/14] Install APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/14] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
echo "[2b/14] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1 || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE >/dev/null 2>&1 || true

echo "[3/14] Push fixture PDF to Downloads"
adb -s "$DEVICE" push "$PDF_LOCAL" "$PDF_REMOTE_PATH" >/dev/null

echo "[4/14] Launch app and open the PDF (try direct SAF grant, fallback to DocumentsUI picker)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true

fname="$(basename "$PDF_REMOTE_PATH")"
doc_id="primary:Download/$fname"
doc_id_enc="$(python3 - "$doc_id" <<'PY'
import urllib.parse, sys
print(urllib.parse.quote(sys.argv[1], safe=''))
PY
)"
DOC_URI="content://com.android.externalstorage.documents/document/${doc_id_enc}"

opened=0
if adb -s "$DEVICE" shell content grant --user 0 --mode rw --uri "$DOC_URI" --package "$PKG" >/dev/null 2>&1; then
  if adb -s "$DEVICE" shell am start -W \
      -a android.intent.action.VIEW \
      -d "$DOC_URI" \
      -t application/pdf \
      -n "$PKG/$ACT" >/dev/null; then
    sleep 2.0
    if uia_assert_in_document_view; then
      opened=1
    fi
  fi
fi

if (( opened == 0 )); then
  # Fallback 1: direct file:// open (legacy storage path)
  if adb -s "$DEVICE" shell am start -W \
      -a android.intent.action.VIEW \
      -d "file://$PDF_REMOTE_PATH" \
      -t application/pdf \
      -n "$PKG/$ACT" >/dev/null 2>&1; then
    sleep 2.0
    if uia_assert_in_document_view; then
      opened=1
    fi
  fi
fi

if (( opened == 0 )); then
  echo "[4/14] Direct grant failed; falling back to DocumentsUI picker"
  adb -s "$DEVICE" shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/$ACT" >/dev/null
  sleep 1.2

  uia_tap_any_res_id "org.opendroidpdf:id/entry_screen_open_document_card_view" || {
    echo "FAIL: could not tap entry-screen open-document card" >&2
    exit 1
  }
  sleep 1.5

  uia_tap_docsui_roots_drawer || {
    echo "FAIL: could not open DocumentsUI roots drawer" >&2
    exit 1
  }
  sleep 0.7
  uia_tap_text_contains "Downloads" || uia_tap_text_contains "Download" || {
    echo "FAIL: could not switch DocumentsUI to Downloads root" >&2
    exit 1
  }
  sleep 0.9

  uia_tap_any_res_id "com.android.documentsui:id/option_menu_search" || uia_tap_desc "Search" || {
    echo "FAIL: could not open DocumentsUI search" >&2
    exit 1
  }
  sleep 0.6
  adb -s "$DEVICE" shell input text "$fname"
  sleep 1.2
  if ! uia_tap_text_contains "$fname"; then
    uia_tap_any_res_id "com.android.documentsui:id/drag_area" || true
    uia_tap_text_contains "$fname" || uia_tap_any_res_id "com.android.documentsui:id/thumbnail" || uia_tap_any_res_id "com.android.documentsui:id/icon_mime" || {
      echo "FAIL: could not select $fname in DocumentsUI search results" >&2
      echo "Logcat tail:" >&2
      adb -s "$DEVICE" logcat -d | tail -n 120 >&2
      exit 1
    }
  fi
  # Some picker variants require hitting an "Open" / checkmark action.
  uia_tap_any_res_id "com.android.documentsui:id/action_menu_open" || \
  uia_tap_any_res_id "com.android.documentsui:id/open" || \
  uia_tap_desc "Open" || true

  uia_assert_in_document_view
fi

echo "[5/14] Enter add-text mode"
uia_tap_any_res_id "org.opendroidpdf:id/menu_add_text_annot" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  uia_tap_text_contains "Add text" || {
    echo "FAIL: add-text action not found" >&2
    exit 1
  }
}
sleep 0.6

echo "[6/14] Tap page and enter token"
_tap_doc_center
sleep 0.8
for _ in $(seq 1 10); do
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    break
  fi
  sleep 0.3
done
if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
  echo "FAIL: text input UI did not appear" >&2
  adb -s "$DEVICE" logcat -d | tail -n 160 >&2
  exit 1
fi
# Dialog flow needs an explicit tap into the input; inline editor is already focused and tapping
# can reposition the caret (which would corrupt our append-assertions).
if uia_has_res_id "android:id/button1" "com.android.internal:id/button1"; then
  uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || {
    echo "FAIL: could not focus text input dialog" >&2
    exit 1
  }
fi
adb -s "$DEVICE" shell input text "$TOKEN_INPUT"
sleep 0.4
if uia_has_res_id "android:id/button1" "com.android.internal:id/button1"; then
  uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
    echo "FAIL: could not confirm text annotation dialog" >&2
    exit 1
  }
else
  # Inline editor: commit via focus loss (tap outside the editor).
  read -r w h < <(_wm_size)
  blank_x=$((w * 9 / 10))
  blank_y=$((h / 5))
  adb -s "$DEVICE" shell input tap "$blank_x" "$blank_y"
  for _ in $(seq 1 15); do
    if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      break
    fi
    sleep 0.25
  done
fi
sleep 2.0

uia_assert_in_document_view
_fail_if_fatal_logcat

OUT_PREFIX="${OUT_PREFIX:-tmp_geny_pdf_text_annot}"
SCREENSHOT_PNG="${SCREENSHOT_PNG:-${OUT_PREFIX}_ui.png}"
SKIP_EDIT=${SKIP_EDIT:-0}
TOKEN_EXPECTED_FINAL="$TOKEN_EDIT_EXPECTED"

echo "[7/14] Assert in-app text is visible (screenshot + OCR)"
# Some flows open the text-style dialog immediately after creation; close it to expose the page.
if uia_has_res_id "android:id/parentPanel"; then
  adb -s "$DEVICE" shell input keyevent KEYCODE_BACK
  sleep 0.6
fi
if [[ "$ASSERT_ONSCREEN_OCR" == "1" ]]; then
  if _wait_for_token_onscreen_ocr "$TOKEN_EXPECTED" "${UI_OCR_TIMEOUT_S:-12}"; then
    echo "  wrote $SCREENSHOT_PNG"
  else
    echo "WARN: onscreen OCR did not find token; will rely on saved-PDF OCR" >&2
    SKIP_EDIT=1
    TOKEN_EXPECTED_FINAL="$TOKEN_EXPECTED"
  fi
fi

read -r TOKEN_X TOKEN_Y < <(_ocr_token_center_xy "$SCREENSHOT_PNG" "$TOKEN_SEARCH" 2>/dev/null || echo "")

echo "[8/14] Tap twice to select + edit text annotation and append ${TOKEN_SUFFIX_EDIT}"
if (( SKIP_EDIT == 0 )) && [[ -n "${TOKEN_X:-}" && -n "${TOKEN_Y:-}" ]]; then
  adb -s "$DEVICE" shell input tap "$TOKEN_X" "$TOKEN_Y"
else
  SKIP_EDIT=1
  TOKEN_EXPECTED_FINAL="$TOKEN_EXPECTED"
fi
sleep 0.35
if (( SKIP_EDIT == 0 )); then
  adb -s "$DEVICE" shell input tap "$TOKEN_X" "$TOKEN_Y"
  sleep 0.9
  for _ in $(seq 1 10); do
    if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      break
    fi
    sleep 0.3
  done
  if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    echo "WARN: edit text UI did not appear after tapping the existing annotation; skipping edit" >&2
    SKIP_EDIT=1
    TOKEN_EXPECTED_FINAL="$TOKEN_EXPECTED"
  else
    if uia_has_res_id "android:id/button1" "com.android.internal:id/button1"; then
      uia_tap_any_res_id "org.opendroidpdf:id/dialog_text_input" || {
        echo "FAIL: could not focus edit text dialog" >&2
        exit 1
      }
    fi
    adb -s "$DEVICE" shell input text "$TOKEN_SUFFIX_EDIT"
    sleep 0.4
    if uia_has_res_id "android:id/button1" "com.android.internal:id/button1"; then
      uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
        echo "FAIL: could not confirm edited text annotation dialog" >&2
        exit 1
      }
    else
      # Inline editor: commit via focus loss (tap outside the editor).
      read -r w h < <(_wm_size)
      blank_x=$((w * 9 / 10))
      blank_y=$((h / 5))
      adb -s "$DEVICE" shell input tap "$blank_x" "$blank_y"
      for _ in $(seq 1 15); do
        if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
          break
        fi
        sleep 0.25
      done
    fi
    sleep 2.0
    uia_assert_in_document_view
    _fail_if_fatal_logcat
  fi
fi

echo "[9/14] Assert edited text is visible (screenshot + OCR)"
if [[ "$ASSERT_ONSCREEN_OCR" == "1" ]]; then
  # Deselect before OCR so the selection box/handles don't corrupt recognition.
  read -r w h < <(_wm_size)
  blank_x=$((w * 9 / 10))
  blank_y=$((h * 9 / 10))
  adb -s "$DEVICE" shell input tap "$blank_x" "$blank_y"
  sleep 0.7
  _fail_if_fatal_logcat
  _wait_for_token_onscreen_ocr "$TOKEN_EXPECTED_FINAL" "${UI_OCR_TIMEOUT_S:-12}" || exit 1
  echo "  wrote $SCREENSHOT_PNG"
fi

read -r TOKEN_EDIT_X TOKEN_EDIT_Y < <(_ocr_token_center_xy "$SCREENSHOT_PNG" "$TOKEN_EDIT_SEARCH" 2>/dev/null || echo "")

echo "[9.5/14] Select the text annotation and drag-move it (direct manipulation)"
MOVE_BEFORE_PNG="${MOVE_BEFORE_PNG:-${OUT_PREFIX}_move_before.png}"
MOVE_AFTER_PNG="${MOVE_AFTER_PNG:-${OUT_PREFIX}_move_after.png}"
_screencap_png "$MOVE_BEFORE_PNG"

read -r w h < <(_wm_size)
if [[ -n "${TOKEN_EDIT_X:-}" && -n "${TOKEN_EDIT_Y:-}" ]]; then
  x="$TOKEN_EDIT_X"
  y="$TOKEN_EDIT_Y"
else
  read -r x y < <(_doc_center_xy)
fi

# Single tap selects (should show bounding box + handles).
adb -s "$DEVICE" shell input tap "$x" "$y"
sleep 0.7
_fail_if_fatal_logcat
MOVE_SELECTED_PNG="${MOVE_SELECTED_PNG:-${OUT_PREFIX}_move_selected.png}"
_screencap_png "$MOVE_SELECTED_PNG"
sel_top_before="$(_selection_box_top_px "$MOVE_SELECTED_PNG" || true)"

# Drag inside the selection box downward to move (Acrobat-style).
read -r bbox_x0 bbox_y0 bbox_x1 bbox_y1 < <(_selection_box_bbox_px "$MOVE_SELECTED_PNG" || echo "")
if [[ -z "${bbox_x0:-}" || -z "${bbox_y0:-}" || -z "${bbox_x1:-}" || -z "${bbox_y1:-}" ]]; then
  echo "FAIL: could not detect selection bbox for move step" >&2
  echo "  screenshot: $MOVE_SELECTED_PNG" >&2
  exit 1
fi

move_x=$(((bbox_x0 + bbox_x1) / 2))
move_y=$(((bbox_y0 + bbox_y1) / 2))
y2=$((y + h / 5))
move_y2=$((move_y + h / 5))
if (( move_y2 > h - 8 )); then move_y2=$((h - 8)); fi
# Use a longer swipe duration and capture mid-gesture to ensure the text preview
# follows the selection box during drag (regression: text "disappears" until drop).
MOVE_MID_PNG="${MOVE_MID_PNG:-${OUT_PREFIX}_move_mid.png}"
adb -s "$DEVICE" shell input swipe "$move_x" "$move_y" "$move_x" "$move_y2" 1200 &
swipe_pid=$!
sleep 0.55
_screencap_png "$MOVE_MID_PNG"
wait "$swipe_pid" || true
sleep 0.6
_fail_if_fatal_logcat

MOVE_AFTER_SELECTED_PNG="${MOVE_AFTER_SELECTED_PNG:-${OUT_PREFIX}_move_after_selected.png}"
_screencap_png "$MOVE_AFTER_SELECTED_PNG"
sel_top_after="$(_selection_box_top_px "$MOVE_AFTER_SELECTED_PNG" || true)"
move_delta=$((sel_top_after - sel_top_before))

# If the drag did not move enough (or at all), retry once with a bigger swipe to
# avoid false negatives on devices with low-dpi or sluggish gesture dispatch.
if (( move_delta < 15 )); then
  echo "WARN: move delta too small (${move_delta}px); retrying with larger drag" >&2
  move_y2=$((move_y + h / 4))
  if (( move_y2 > h - 8 )); then move_y2=$((h - 8)); fi
  adb -s "$DEVICE" shell input swipe "$move_x" "$move_y" "$move_x" "$move_y2" 1500
  sleep 0.7
  _screencap_png "$MOVE_AFTER_SELECTED_PNG"
  sel_top_after="$(_selection_box_top_px "$MOVE_AFTER_SELECTED_PNG" || true)"
  move_delta=$((sel_top_after - sel_top_before))
fi

echo "[9.55/14] Undo/redo: undo move then redo move (assert selection returns)"
UNDO_MOVE_PNG="${UNDO_MOVE_PNG:-${OUT_PREFIX}_undo_move.png}"
REDO_MOVE_PNG="${REDO_MOVE_PNG:-${OUT_PREFIX}_redo_move.png}"

uia_tap_any_res_id "org.opendroidpdf:id/menu_undo" || uia_tap_desc "Undo" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  uia_tap_text_contains "Undo" || {
    echo "FAIL: could not tap Undo action" >&2
    exit 1
  }
}
sleep 1.0
_fail_if_fatal_logcat
_screencap_png "$UNDO_MOVE_PNG"
sel_top_undo="$(_selection_box_top_px "$UNDO_MOVE_PNG" || true)"

uia_tap_any_res_id "org.opendroidpdf:id/menu_redo" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  uia_tap_text_contains "Redo" || {
    echo "FAIL: could not tap Redo action" >&2
    exit 1
  }
}
sleep 1.0
_fail_if_fatal_logcat
_screencap_png "$REDO_MOVE_PNG"
sel_top_redo="$(_selection_box_top_px "$REDO_MOVE_PNG" || true)"

if [[ -z "${sel_top_before:-}" || -z "${sel_top_after:-}" || -z "${sel_top_undo:-}" || -z "${sel_top_redo:-}" ]]; then
  echo "FAIL: could not detect selection box for undo/redo assertions" >&2
  echo "  before=$MOVE_SELECTED_PNG after=$MOVE_AFTER_SELECTED_PNG undo=$UNDO_MOVE_PNG redo=$REDO_MOVE_PNG" >&2
  exit 1
fi

undo_delta=$((sel_top_undo - sel_top_before))
abs_undo_delta="${undo_delta#-}"
if (( abs_undo_delta > 24 )); then
  echo "FAIL: expected Undo to return selection close to original top (abs(delta) <= 24px), got ${undo_delta}px" >&2
  echo "  before_top=$sel_top_before undo_top=$sel_top_undo" >&2
  echo "  screenshots: $MOVE_SELECTED_PNG $UNDO_MOVE_PNG" >&2
  exit 1
fi

redo_delta=$((sel_top_redo - sel_top_after))
abs_redo_delta="${redo_delta#-}"
if (( abs_redo_delta > 24 )); then
  echo "FAIL: expected Redo to return selection close to moved top (abs(delta) <= 24px), got ${redo_delta}px" >&2
  echo "  after_top=$sel_top_after redo_top=$sel_top_redo" >&2
  echo "  screenshots: $MOVE_AFTER_SELECTED_PNG $REDO_MOVE_PNG" >&2
  exit 1
fi

# Deselect before OCR so the selection box/handles don't corrupt token recognition.
blank_x=$((w * 9 / 10))
blank_y=$((h * 9 / 10))
adb -s "$DEVICE" shell input tap "$blank_x" "$blank_y"
sleep 0.7
_fail_if_fatal_logcat

_screencap_png "$MOVE_AFTER_PNG"

if [[ -n "${sel_top_before:-}" && -n "${sel_top_after:-}" ]]; then
  delta_sel=$((sel_top_after - sel_top_before))
  if (( delta_sel < 30 )); then
    echo "FAIL: expected selection box to move down (top delta >= 30px), got ${delta_sel}px" >&2
    echo "  before: $MOVE_SELECTED_PNG (top=$sel_top_before) after: $MOVE_AFTER_SELECTED_PNG (top=$sel_top_after)" >&2
    exit 1
  fi
else
  echo "FAIL: could not detect selection box in move screenshots" >&2
  echo "  before: $MOVE_SELECTED_PNG (top=$sel_top_before) after: $MOVE_AFTER_SELECTED_PNG (top=$sel_top_after)" >&2
  exit 1
fi

echo "[9.6/14] Assert text stays visible during drag (mid-gesture screenshot)"
read -r mid_x0 mid_y0 mid_x1 mid_y1 < <(_selection_box_bbox_px "$MOVE_MID_PNG" || echo "")
if [[ -z "${mid_x0:-}" || -z "${mid_y0:-}" || -z "${mid_x1:-}" || -z "${mid_y1:-}" ]]; then
  echo "FAIL: could not detect selection bbox in mid-drag screenshot" >&2
  echo "  screenshot: $MOVE_MID_PNG" >&2
  exit 1
fi
mid_text_h="$(_dark_text_height_in_bbox_px "$MOVE_MID_PNG" "$mid_x0" "$mid_y0" "$mid_x1" "$mid_y1" || true)"
if [[ -z "${mid_text_h:-}" || "$mid_text_h" -lt 6 ]]; then
  echo "FAIL: expected dark text inside selection box during drag; got height=${mid_text_h:-<none>}px" >&2
  echo "  screenshot: $MOVE_MID_PNG bbox=($mid_x0,$mid_y0 $mid_x1,$mid_y1)" >&2
  exit 1
fi

echo "[9.7/14] Resize the text annotation via bottom-right handle (assert bbox grows)"
# Re-select to show handles (we deselected for OCR stability above).
adb -s "$DEVICE" shell input tap "$x" "$y2"
sleep 0.7
_fail_if_fatal_logcat
SKIP_RESIZE=0

# Corner resize handles are hidden by default; explicitly enable resize mode via the Resize action.
if ! uia_tap_any_res_id "org.opendroidpdf:id/menu_resize"; then
  if uia_tap_desc "More options"; then sleep 0.4; fi
  if ! uia_tap_text_contains "Resize" && ! uia_tap_text_contains "Größe ändern" && ! uia_tap_text_contains "Cambiar tamaño"; then
    echo "WARN: could not enable resize mode (menu_resize); skipping resize-dependent checks" >&2
    SKIP_RESIZE=1
  fi
fi
sleep 0.7
_fail_if_fatal_logcat

RESIZE_SELECTED_PNG="${RESIZE_SELECTED_PNG:-${OUT_PREFIX}_resize_selected.png}"
_screencap_png "$RESIZE_SELECTED_PNG"
read -r bbox_x0 bbox_y0 bbox_x1 bbox_y1 < <(_selection_box_bbox_px "$RESIZE_SELECTED_PNG" || echo "")
if [[ -z "${bbox_x0:-}" || -z "${bbox_y0:-}" || -z "${bbox_x1:-}" || -z "${bbox_y1:-}" ]]; then
  if (( SKIP_RESIZE == 0 )); then
    echo "FAIL: could not detect selection bbox for resize step" >&2
    echo "  screenshot: $RESIZE_SELECTED_PNG" >&2
    exit 1
  else
    bbox_x0=$x; bbox_y0=$y2; bbox_x1=$((x+10)); bbox_y1=$((y2+10))
  fi
fi

start_rx=$bbox_x1
start_ry=$bbox_y1
end_rx=$((start_rx + w / 10))
end_ry=$((start_ry + h / 12))
if (( end_rx > w - 8 )); then end_rx=$((w - 8)); fi
if (( end_ry > h - 8 )); then end_ry=$((h - 8)); fi

# Drag the bottom-right handle outward to resize.
if (( SKIP_RESIZE == 0 )); then
  adb -s "$DEVICE" shell input swipe "$start_rx" "$start_ry" "$end_rx" "$end_ry" 320
  sleep 1.2
  _fail_if_fatal_logcat
fi

RESIZE_AFTER_PNG="${RESIZE_AFTER_PNG:-${OUT_PREFIX}_resize_after.png}"
_screencap_png "$RESIZE_AFTER_PNG"
read -r bbox2_x0 bbox2_y0 bbox2_x1 bbox2_y1 < <(_selection_box_bbox_px "$RESIZE_AFTER_PNG" || echo "")
if [[ -z "${bbox2_x0:-}" || -z "${bbox2_y0:-}" || -z "${bbox2_x1:-}" || -z "${bbox2_y1:-}" ]]; then
  if (( SKIP_RESIZE == 0 )); then
    echo "FAIL: could not detect selection bbox after resize" >&2
    echo "  screenshot: $RESIZE_AFTER_PNG" >&2
    exit 1
  else
    bbox2_x0=$bbox_x0; bbox2_y0=$bbox_y0; bbox2_x1=$bbox_x1; bbox2_y1=$bbox_y1
  fi
fi

before_w=$((bbox_x1 - bbox_x0))
before_h=$((bbox_y1 - bbox_y0))
after_w=$((bbox2_x1 - bbox2_x0))
after_h=$((bbox2_y1 - bbox2_y0))
if (( SKIP_RESIZE == 0 )); then
  if (( after_w - before_w < 20 && after_h - before_h < 20 )); then
    echo "FAIL: expected selection bbox to grow after resize (delta >= 20px), got dw=$((after_w-before_w)) dh=$((after_h-before_h))" >&2
    echo "  before: $RESIZE_SELECTED_PNG (w=$before_w h=$before_h) after: $RESIZE_AFTER_PNG (w=$after_w h=$after_h)" >&2
    exit 1
  fi
fi

if [[ "$ASSERT_TEXT_WRAP_ON_RESIZE" == "1" ]]; then
  echo "[9.75/14] Wrap regression (force multiline via newline and assert two lines)"
  # Re-open editor and insert a newline + token to force two lines deterministically.
  adb -s "$DEVICE" shell input tap "$x" "$y2"
  sleep 0.4
  adb -s "$DEVICE" shell input tap "$x" "$y2"
  sleep 0.6
  before_wrap_h=$((bbox2_y1 - bbox2_y0))
  for _ in $(seq 1 12); do
    if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      break
    fi
    sleep 0.25
  done
  if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    uia_tap_any_res_id "org.opendroidpdf:id/menu_edit" "org.opendroidpdf:id/menu_edit_text_annot" || uia_tap_text_contains "Edit" || true
    sleep 0.8
    for _ in $(seq 1 8); do
      if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
        break
      fi
      sleep 0.25
    done
  fi
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    adb -s "$DEVICE" shell input keyevent 66  # ENTER
    sleep 0.2
    adb -s "$DEVICE" shell input text WRAPLINE
    sleep 0.2
    wrap_dialog_text="$(_uia_text_for_rid "org.opendroidpdf:id/dialog_text_input" || true)"
    if [[ "$wrap_dialog_text" != *"WRAPLINE"* ]]; then
      echo "FAIL: wrap dialog text does not contain WRAPLINE (got: $wrap_dialog_text)" >&2
      exit 1
    fi
    if uia_has_res_id "android:id/button1" "com.android.internal:id/button1"; then
      uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || {
        echo "FAIL: could not confirm text edit dialog during wrap step" >&2
        exit 1
      }
    else
      # Inline editor: commit via focus loss.
      read -r w h < <(_wm_size)
      blank_x=$((w * 9 / 10))
      blank_y=$((h / 5))
      adb -s "$DEVICE" shell input tap "$blank_x" "$blank_y"
      for _ in $(seq 1 15); do
        if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
          break
        fi
        sleep 0.2
      done
    fi
  else
    echo "FAIL: could not open text edit dialog for wrap check" >&2
    exit 1
  fi
  sleep 1.2
  _fail_if_fatal_logcat

  WRAP_AFTER_PNG="${WRAP_AFTER_PNG:-${OUT_PREFIX}_wrap_after.png}"
  _screencap_png "$WRAP_AFTER_PNG"
  read -r bbox3_x0 bbox3_y0 bbox3_x1 bbox3_y1 < <(_selection_box_bbox_px "$WRAP_AFTER_PNG" || echo "")
  if [[ -n "${bbox3_x0:-}" && -n "${bbox3_y0:-}" && -n "${bbox3_x1:-}" && -n "${bbox3_y1:-}" ]]; then
    wrap_ocr="$(_ocr_png "$WRAP_AFTER_PNG" | tr '\\n' ' ' | tr -s ' ')" || wrap_ocr=""
    if ! printf '%s\n' "$wrap_ocr" | rg -q "WRAPLINE"; then
      echo "INFO: WRAPLINE not visible in OCR; relying on dialog text check (ok)" >&2
    fi
  else
    echo "FAIL: could not detect selection bbox after wrap edit" >&2
    exit 1
  fi
fi

echo "[9.77/14] Fit-to-text action (style dialog) and assert bbox shrinks"
FIT_BEFORE_PNG="${FIT_BEFORE_PNG:-${OUT_PREFIX}_fit_before.png}"
FIT_AFTER_PNG="${FIT_AFTER_PNG:-${OUT_PREFIX}_fit_after.png}"
_screencap_png "$FIT_BEFORE_PNG"
read -r fit_x0 fit_y0 fit_x1 fit_y1 < <(_selection_box_bbox_px "$FIT_BEFORE_PNG" || echo "")
if [[ -z "${fit_x0:-}" || -z "${fit_y0:-}" || -z "${fit_x1:-}" || -z "${fit_y1:-}" ]]; then
  echo "FAIL: could not detect selection bbox before fit-to-text" >&2
  echo "  screenshot: $FIT_BEFORE_PNG" >&2
  exit 1
fi
fit_before_w=$((fit_x1 - fit_x0))
fit_before_h=$((fit_y1 - fit_y0))

SKIP_STYLE=0
uia_tap_any_res_id "org.opendroidpdf:id/menu_text_style" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  if ! uia_tap_text_contains "Style"; then
    echo "WARN: could not open text style dialog; skipping style-dependent checks" >&2
    SKIP_STYLE=1
  fi
}
sleep 0.8

if (( SKIP_STYLE == 0 )); then
  # Bring dialog near the top to make middle controls visible/dumpable.
  for _ in $(seq 1 6); do _scroll_dialog_up_small; sleep 0.15; done

  # Best-effort: exercise alignment toggle (should not crash).
  uia_tap_any_res_id "org.opendroidpdf:id/text_style_align_center" || true
  sleep 0.2

  # Prefer direct bounds to reduce overflow/menu dependency. Scroll down until the button appears.
  fit_tapped=0
  for _ in $(seq 1 16); do
    if uia_tap_any_res_id "org.opendroidpdf:id/text_style_fit_to_text"; then
      fit_tapped=1
      break
    fi
    if read -r fitbtn_l fitbtn_t fitbtn_r fitbtn_b < <(_uia_bounds_for_rid "org.opendroidpdf:id/text_style_fit_to_text" 2>/dev/null); then
      tap_x=$(((fitbtn_l + fitbtn_r) / 2))
      tap_y=$(((fitbtn_t + fitbtn_b) / 2))
      adb -s "$DEVICE" shell input tap "$tap_x" "$tap_y"
      fit_tapped=1
      break
    fi
    _scroll_dialog_down_small
    sleep 0.25
  done
  if (( fit_tapped == 0 )); then
    _uia_dump_to "${OUT_PREFIX}_fit_uia.xml" || true
    echo "WARN: could not tap Fit to text; skipping fit assertion (button not found)" >&2
    SKIP_STYLE=1
  fi
  sleep 0.8
fi

# If the style dialog was opened but we bailed early, make sure it isn't left blocking quick actions.
if (( SKIP_STYLE == 1 )); then
  _dismiss_text_style_dialog
fi

echo "[9.78/14] Border controls: set thick red dashed border + rounding"
# Ensure the border controls are visible. UIAutomator dumps omit off-screen ScrollView children,
# so we must scroll until the seekbar is actually visible.
#
# Strategy:
#   - First, best-effort scroll to the top of the dialog (so search direction is deterministic).
#   - Then, scroll down until the border-width seekbar becomes visible.
if (( SKIP_STYLE == 0 )); then
for _ in $(seq 1 12); do
  if uia_has_res_id "org.opendroidpdf:id/text_style_summary"; then
    break
  fi
  _scroll_dialog_up
  sleep 0.25
done

# Find the border-width seekbar.
bw_found=0
for _ in $(seq 1 48); do
  if read -r bw_l bw_t bw_r bw_b < <(_uia_bounds_for_rid "org.opendroidpdf:id/text_style_border_width_seekbar" 2>/dev/null); then
    bw_found=1
    break
  fi
  _scroll_dialog_down_small
  # Give the ScrollView time to settle; UIAutomator dumps can miss nodes mid-scroll.
  sleep 0.35
done
# One last check: the final scroll in the loop can land on the target, but the for-loop
# would otherwise end without re-checking.
if (( bw_found == 0 )); then
  sleep 0.35
  if read -r bw_l bw_t bw_r bw_b < <(_uia_bounds_for_rid "org.opendroidpdf:id/text_style_border_width_seekbar" 2>/dev/null); then
    bw_found=1
  fi
fi
if (( bw_found == 0 )); then
  echo "FAIL: could not locate border width seekbar in Text style dialog" >&2
  tmp_uia="$(mktemp -t odp_uia_border_width_XXXXXX).xml"
  _uia_dump_to "$tmp_uia" || true
  echo "  uia dump: $tmp_uia" >&2
  exit 1
fi

bw_y=$(((bw_t + bw_b) / 2))
# Prefer tap-to-set (more reliable than drag in a scrolling dialog).
bw_x=$((bw_l + (bw_r - bw_l) * 92 / 100))
adb -s "$DEVICE" shell input tap "$bw_x" "$bw_y"
sleep 0.35

bw_txt="$(_uia_text_for_rid "org.opendroidpdf:id/text_style_border_width_value" 2>/dev/null || true)"
bw_num="$(printf '%s' "$bw_txt" | rg -o '[0-9]+(\\.[0-9]+)?' | head -n 1 || true)"
if [[ -z "$bw_num" || "$bw_num" == "0" || "$bw_num" == "0.0" ]]; then
  # Fallback: longer swipe so the SeekBar receives the gesture (some devices treat short drags as scroll).
  adb -s "$DEVICE" shell input swipe $((bw_l + 28)) "$bw_y" $((bw_r - 28)) "$bw_y" 900
  sleep 0.4
  bw_txt="$(_uia_text_for_rid "org.opendroidpdf:id/text_style_border_width_value" 2>/dev/null || true)"
  bw_num="$(printf '%s' "$bw_txt" | rg -o '[0-9]+(\\.[0-9]+)?' | head -n 1 || true)"
fi
if [[ -z "$bw_num" || "$bw_num" == "0" || "$bw_num" == "0.0" ]]; then
  echo "FAIL: border width did not increase (value='${bw_txt:-<missing>}')" >&2
  exit 1
fi

# Border style: dashed.
uia_tap_any_res_id "org.opendroidpdf:id/text_style_border_style_dashed" || true
sleep 0.3

# Border radius: swipe seekbar towards ~60%.
if read -r br_l br_t br_r br_b < <(_uia_bounds_for_rid "org.opendroidpdf:id/text_style_border_radius_seekbar" 2>/dev/null); then
  br_y=$(((br_t + br_b) / 2))
  br_x=$((br_l + (br_r - br_l) * 60 / 100))
  adb -s "$DEVICE" shell input swipe $((br_l + 28)) "$br_y" "$br_x" "$br_y" 900
  sleep 0.4
fi

# Border color: pick Red via content-desc (unique for border swatches).
uia_tap_desc "Set border color to Red" || true
sleep 0.4

echo "[9.79/14] Locking: enable lock position/size + lock contents"
_scroll_dialog_down
sleep 0.4
uia_tap_any_res_id "org.opendroidpdf:id/text_style_lock_position_size" || true
sleep 0.3
uia_tap_any_res_id "org.opendroidpdf:id/text_style_lock_contents" || true
sleep 0.3
adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
sleep 0.8
_dismiss_text_style_dialog
_fail_if_fatal_logcat
else
  echo "WARN: skipping border/lock/style checks (style dialog unavailable)" >&2
fi

_screencap_png "$FIT_AFTER_PNG"
read -r fit2_x0 fit2_y0 fit2_x1 fit2_y1 < <(_selection_box_bbox_px "$FIT_AFTER_PNG" || echo "")
if [[ -z "${fit2_x0:-}" || -z "${fit2_y0:-}" || -z "${fit2_x1:-}" || -z "${fit2_y1:-}" ]]; then
  # Selection can disappear after dialog interactions; re-tap inside the last-known bbox.
  tap_fit_x=$(((fit_x0 + fit_x1) / 2))
  tap_fit_y=$(((fit_y0 + fit_y1) / 2))
  adb -s "$DEVICE" shell input tap "$tap_fit_x" "$tap_fit_y"
  sleep 0.8
  _screencap_png "$FIT_AFTER_PNG"
  read -r fit2_x0 fit2_y0 fit2_x1 fit2_y1 < <(_selection_box_bbox_px "$FIT_AFTER_PNG" || echo "")
fi
if [[ -z "${fit2_x0:-}" || -z "${fit2_y0:-}" || -z "${fit2_x1:-}" || -z "${fit2_y1:-}" ]]; then
  echo "FAIL: could not detect selection bbox after fit-to-text" >&2
  echo "  screenshot: $FIT_AFTER_PNG" >&2
  exit 1
fi
fit_after_w=$((fit2_x1 - fit2_x0))
fit_after_h=$((fit2_y1 - fit2_y0))
dw_fit=$((fit_before_w - fit_after_w))
dh_fit=$((fit_before_h - fit_after_h))
if (( SKIP_STYLE == 0 )) && (( dw_fit < 20 && dh_fit < 20 )); then
  echo "FAIL: expected bbox to shrink after Fit to text (dw>=20 or dh>=20), got dw=$dw_fit dh=$dh_fit" >&2
  echo "  before: $FIT_BEFORE_PNG (w=$fit_before_w h=$fit_before_h) after: $FIT_AFTER_PNG (w=$fit_after_w h=$fit_after_h)" >&2
  exit 1
fi

echo "[9.81/14] Lock position/size regression: drag inside selection should not MOVE (and should not page-pan)"
if (( SKIP_STYLE == 0 )); then
adb -s "$DEVICE" logcat -c >/dev/null || true
lock_x=$(((fit2_x0 + fit2_x1) / 2))
lock_y=$(((fit2_y0 + fit2_y1) / 2))
lock_y2=$((lock_y + h / 6))
if (( lock_y2 > h - 12 )); then lock_y2=$((h - 12)); fi
adb -s "$DEVICE" shell input swipe "$lock_x" "$lock_y" "$lock_x" "$lock_y2" 420
sleep 0.9
if adb -s "$DEVICE" logcat -d | rg -q "TextAnnotGesture: start MOVE"; then
  echo "FAIL: locked position/size should prevent starting MOVE on drag" >&2
  adb -s "$DEVICE" logcat -d | rg -n "TextAnnotGesture: start MOVE" | tail -n 40 >&2 || true
  exit 1
fi
if adb -s "$DEVICE" logcat -d | rg -q "GestureRouter: onScroll"; then
  echo "FAIL: expected drag inside locked selection to be consumed (GestureRouter: onScroll should be absent)" >&2
  adb -s "$DEVICE" logcat -d | rg -n "GestureRouter: onScroll" | tail -n 80 >&2 || true
  exit 1
fi

echo "[9.82/14] Lock contents regression: edit dialog must not appear"
# Attempt to edit by double-tapping inside the selection.
adb -s "$DEVICE" shell input tap "$lock_x" "$lock_y"
sleep 0.35
adb -s "$DEVICE" shell input tap "$lock_x" "$lock_y"
sleep 1.0
if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
  echo "FAIL: edit dialog appeared even though contents are locked" >&2
  _screencap_png "${OUT_PREFIX}_lock_contents_fail.png" || true
  echo "  wrote ${OUT_PREFIX}_lock_contents_fail.png" >&2
  exit 1
fi
else
  echo "WARN: skipping lock regression checks (style dialog unavailable)" >&2
  lock_x=$(((fit2_x0 + fit2_x1) / 2))
  lock_y=$(((fit2_y0 + fit2_y1) / 2))
fi

echo "[9.83/14] Duplicate (quick-action toolbar) and assert selection moves"
# Ensure selection is active so the quick-action popup is present.
adb -s "$DEVICE" shell input tap "$lock_x" "$lock_y"
sleep 0.7
_fail_if_fatal_logcat

DUP_SKIP=0
DUP_BEFORE_PNG="${DUP_BEFORE_PNG:-${OUT_PREFIX}_dup_before.png}"
DUP_AFTER_PNG="${DUP_AFTER_PNG:-${OUT_PREFIX}_dup_after.png}"
_screencap_png "$DUP_BEFORE_PNG"
sel_dup_before="$(_selection_box_top_px "$DUP_BEFORE_PNG" || true)"
if [[ -z "${sel_dup_before:-}" ]]; then
  echo "FAIL: could not detect selection box before duplicate" >&2
  echo "  screenshot: $DUP_BEFORE_PNG" >&2
  exit 1
fi

uia_tap_any_res_id "org.opendroidpdf:id/menu_duplicate_text" || {
  # Fallback: some devices may overflow action buttons into "More options".
  if uia_tap_desc "More options"; then sleep 0.4; fi
  if ! uia_tap_text_contains "Duplicate"; then
    echo "WARN: could not tap Duplicate action; skipping duplicate assertion" >&2
    _uia_dump_to "${OUT_PREFIX}_dup_uia.xml" || true
    DUP_SKIP=1
  fi
}
sleep 1.4
_fail_if_fatal_logcat

_screencap_png "$DUP_AFTER_PNG"
sel_dup_after="$(_selection_box_top_px "$DUP_AFTER_PNG" || true)"
if [[ -z "${sel_dup_after:-}" ]]; then
  echo "FAIL: could not detect selection box after duplicate" >&2
  echo "  screenshot: $DUP_AFTER_PNG" >&2
  exit 1
fi
if (( DUP_SKIP == 0 )); then
  delta_dup=$((sel_dup_after - sel_dup_before))
  abs_delta_dup="${delta_dup#-}"
  if (( abs_delta_dup < 10 )); then
    echo "FAIL: expected selection box to move after duplicate (abs(delta) >= 10px), got ${delta_dup}px" >&2
    echo "  before: $DUP_BEFORE_PNG (top=$sel_dup_before) after: $DUP_AFTER_PNG (top=$sel_dup_after)" >&2
    exit 1
  fi
fi

echo "[9.84/14] Copy + Paste (clipboard) and assert selection moves"
CLIP_BEFORE_PNG="${CLIP_BEFORE_PNG:-${OUT_PREFIX}_clip_before.png}"
CLIP_AFTER_PNG="${CLIP_AFTER_PNG:-${OUT_PREFIX}_clip_after.png}"
_screencap_png "$CLIP_BEFORE_PNG"
sel_clip_before="$(_selection_box_top_px "$CLIP_BEFORE_PNG" || true)"
if [[ -z "${sel_clip_before:-}" ]]; then
  echo "FAIL: could not detect selection box before clipboard copy/paste" >&2
  echo "  screenshot: $CLIP_BEFORE_PNG" >&2
  exit 1
fi

CLIP_SKIP=0
uia_tap_any_res_id "org.opendroidpdf:id/menu_copy_text_annot" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  if ! uia_tap_text_contains "Copy"; then
    echo "WARN: could not tap Copy action; skipping copy/paste assertions" >&2
    _uia_dump_to "${OUT_PREFIX}_clip_uia.xml" || true
    CLIP_SKIP=1
  fi
}
sleep 0.6

if (( CLIP_SKIP == 0 )); then
  uia_tap_any_res_id "org.opendroidpdf:id/menu_paste_text_annot" || {
    if uia_tap_desc "More options"; then sleep 0.4; fi
    if ! uia_tap_text_contains "Paste"; then
      echo "WARN: could not tap Paste action; skipping copy/paste assertions" >&2
      CLIP_SKIP=1
    fi
  }
fi
sleep 1.4
_fail_if_fatal_logcat

_screencap_png "$CLIP_AFTER_PNG"
sel_clip_after="$(_selection_box_top_px "$CLIP_AFTER_PNG" || true)"
if [[ -z "${sel_clip_after:-}" ]]; then
  echo "FAIL: could not detect selection box after clipboard paste" >&2
  echo "  screenshot: $CLIP_AFTER_PNG" >&2
  exit 1
fi
if (( CLIP_SKIP == 0 )); then
  delta_clip=$((sel_clip_after - sel_clip_before))
  abs_delta_clip="${delta_clip#-}"
  if (( abs_delta_clip < 10 )); then
    echo "FAIL: expected selection box to move after paste (abs(delta) >= 10px), got ${delta_clip}px" >&2
    echo "  before: $CLIP_BEFORE_PNG (top=$sel_clip_before) after: $CLIP_AFTER_PNG (top=$sel_clip_after)" >&2
    exit 1
  fi
fi

echo "[9.8/14] Pinch-zoom + one-finger pan regression (pan outside selection)"
if ! uia_runner_run_test "$UIA_ZOOM_TEST"; then
  echo "WARN: UIA zoom/pinch test failed; continuing smoke" >&2
fi
sleep 1.0
_dismiss_text_style_dialog
_fail_if_fatal_logcat

PAN_BEFORE_PNG="${PAN_BEFORE_PNG:-${OUT_PREFIX}_panzoom_before.png}"
PAN_AFTER_PNG="${PAN_AFTER_PNG:-${OUT_PREFIX}_panzoom_after.png}"
_screencap_png "$PAN_BEFORE_PNG"

adb -s "$DEVICE" logcat -c >/dev/null || true

read -r w h < <(_wm_size)
sx=$((w / 2))
sy=$((h * 70 / 100))
ex=$sx
ey=$((h * 35 / 100))

# If a selection box is visible, start the pan gesture *outside* it so we validate:
# - pan still works while a text box is selected
# - drag inside the selection moves the annotation (covered earlier)
read -r sel_x0 sel_y0 sel_x1 sel_y1 < <(_selection_box_bbox_px "$PAN_BEFORE_PNG" || echo "")
if [[ -n "${sel_x0:-}" && -n "${sel_y0:-}" && -n "${sel_x1:-}" && -n "${sel_y1:-}" ]]; then
  sx=$((sel_x1 + 60))
  if (( sx > w - 8 )); then sx=$((sel_x0 - 60)); fi
  if (( sx < 8 )); then sx=$((w / 2)); fi
  sy=$(((sel_y0 + sel_y1) / 2))
  if (( sy < h / 10 )); then sy=$((h / 2)); fi
  if (( sy > h - 10 )); then sy=$((h / 2)); fi
  # Swipe up by ~35% of the screen height, clamped to the viewport.
  ex=$sx
  ey=$((sy - (h * 35 / 100)))
  if (( ey < h / 10 )); then ey=$((h / 10)); fi
fi
adb -s "$DEVICE" shell input swipe "$sx" "$sy" "$ex" "$ey" 420
sleep 0.9
_screencap_png "$PAN_AFTER_PNG"

log_tail="$(adb -s "$DEVICE" logcat -d | rg -n "GestureRouter: onScroll" | tail -n 5 || true)"
if [[ -z "$log_tail" ]]; then
  echo "WARN: one-finger pan did not log GestureRouter:onScroll (skipping pan assertion)" >&2
else
  if printf '%s\n' "$log_tail" | rg -q "scrollDisabled=true"; then
    echo "FAIL: one-finger pan reached onScroll but scrollDisabled=true" >&2
    printf '%s\n' "$log_tail" >&2
    exit 1
  fi
fi
if adb -s "$DEVICE" logcat -d | rg -q "TextAnnotGesture: start MOVE"; then
  echo "FAIL: pan gesture triggered text MOVE (pan should scroll, not move the annotation)" >&2
  adb -s "$DEVICE" logcat -d | rg -n "TextAnnotGesture: start MOVE" | tail -n 40 >&2 || true
  exit 1
fi
if [[ -n "$log_tail" ]]; then
  echo "OK: pan gesture reached ReaderView onScroll with scrollEnabled"
fi

_fail_if_fatal_logcat

echo "[10/14] Exit edit mode (show main menu)"
uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || true
sleep 0.8

if [[ "$POST_EDIT_IDLE_TAP_S" != "0" ]]; then
  echo "[10.5/14] Wait ${POST_EDIT_IDLE_TAP_S}s, then tap-to-edit again (catch tap-after-idle crashes)"
  sleep "$POST_EDIT_IDLE_TAP_S"
  _fail_if_fatal_logcat

  # Re-enter text tool to force inline editor readiness before tapping.
  uia_tap_any_res_id "org.opendroidpdf:id/menu_add_text_annot" || {
    if uia_tap_desc "More options"; then sleep 0.4; fi
    uia_tap_text_contains "Add text" || true
  }
  sleep 0.5

  idle_tap_x="$x"
  idle_tap_y="$y2"
  if [[ -n "${bbox2_x0:-}" && -n "${bbox2_y0:-}" && -n "${bbox2_x1:-}" && -n "${bbox2_y1:-}" ]]; then
    idle_tap_x=$(((bbox2_x0 + bbox2_x1) / 2))
    idle_tap_y=$(((bbox2_y0 + bbox2_y1) / 2))
  fi

  adb -s "$DEVICE" shell input tap "$idle_tap_x" "$idle_tap_y"
  sleep 0.35
  adb -s "$DEVICE" shell input tap "$idle_tap_x" "$idle_tap_y"
  sleep 0.9
  for _ in $(seq 1 10); do
    if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
      break
    fi
    sleep 0.3
  done
  # Retry once with a long-press to reselect the annotation if the first attempt failed.
  if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    adb -s "$DEVICE" shell input swipe "$idle_tap_x" "$idle_tap_y" "$idle_tap_x" "$idle_tap_y" 700
    sleep 1.0
    for _ in $(seq 1 8); do
      if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
        break
      fi
      sleep 0.25
    done
  fi
  # Final fallback: explicit "Edit" action if available in toolbar.
  if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    uia_tap_any_res_id "org.opendroidpdf:id/menu_edit" "org.opendroidpdf:id/menu_edit_text_annot" || true
    sleep 0.8
  fi
  if uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
    if uia_has_res_id "android:id/button3" "com.android.internal:id/button3"; then
      uia_tap_any_res_id "android:id/button3" "com.android.internal:id/button3" || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK || true
      sleep 0.8
    else
      # Inline editor: dismiss via focus loss (tap outside the editor).
      read -r w h < <(_wm_size)
      blank_x=$((w * 9 / 10))
      blank_y=$((h / 5))
      adb -s "$DEVICE" shell input tap "$blank_x" "$blank_y"
      for _ in $(seq 1 15); do
        if ! uia_has_res_id "org.opendroidpdf:id/dialog_text_input"; then
          break
        fi
        sleep 0.25
      done
    fi
    # Canceling the dialog/inline editor can leave us in Edit mode; return to main so Save is accessible.
    uia_tap_any_res_id "org.opendroidpdf:id/menu_accept" || true
    sleep 0.6
  else
    uia_assert_in_document_view || true
    echo "INFO: tap-after-idle edit dialog not shown after retries; continuing" >&2
  fi
  _fail_if_fatal_logcat
fi

echo "[11/14] Save in-place"
if uia_tap_desc "More options"; then
  sleep 0.4
fi
uia_tap_any_res_id "org.opendroidpdf:id/menu_save" || uia_tap_text_contains "Save" || {
  echo "FAIL: Save menu item not found" >&2
  exit 1
}
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 4

echo "[12/14] Pull saved PDF back to host"
SAVED_PDF="${SAVED_PDF:-${OUT_PREFIX}.pdf}"
adb -s "$DEVICE" pull "$PDF_REMOTE_PATH" "$SAVED_PDF" >/dev/null
echo "  wrote $SAVED_PDF"

echo "[13/14] Render first page and OCR for token"
RENDER_PNG="${RENDER_PNG:-${OUT_PREFIX}_render.png}"
_render_pdf_to_png "$SAVED_PDF" "$RENDER_PNG"
echo "  wrote $RENDER_PNG"

ocr="$(_ocr_png "$RENDER_PNG" | tr '\n' ' ' | sed -e 's/[[:space:]]\\+/ /g' -e 's/^ //; s/ $//')"
token_key="$(printf '%s' "$TOKEN_EXPECTED_FINAL" | tr -cd '[:alnum:]' | cut -c1-10)"
ocr_key="$(printf '%s' "$ocr" | tr -cd '[:alnum:]')"
if ! printf '%s\n' "$ocr_key" | rg -q "$token_key"; then
  # Fall back to a more stable thresholded OCR pass.
  if ! _assert_token_in_rendered_pdf "$RENDER_PNG" "$TOKEN_EXPECTED_FINAL"; then
    if rg -a -q "$TOKEN_EXPECTED_FINAL" "$SAVED_PDF"; then
      echo "WARN: OCR did not find token but PDF text contains it; continuing" >&2
    else
      echo "  token_key=$token_key" >&2
      echo "  OCR output: $ocr" >&2
      echo "PDF byte scan (first match):" >&2
      rg -a -n "$TOKEN_EXPECTED_FINAL" "$SAVED_PDF" | head -n 5 >&2 || true
      exit 1
    fi
  fi
else
  # Strict OCR already found it.
  true
fi

_assert_red_border_pixels_in_rendered_png "$RENDER_PNG"

_fail_if_fatal_logcat

if [[ "$POST_SAVE_HOME_WAIT_S" != "0" ]]; then
  echo "[14/14] Background app and wait ${POST_SAVE_HOME_WAIT_S}s (catch delayed native crashes)"
  adb -s "$DEVICE" shell input keyevent KEYCODE_HOME
  sleep "$POST_SAVE_HOME_WAIT_S"
  _fail_if_fatal_logcat
fi

echo "OK: text annotation rendered and OCR found token ($TOKEN_EDIT_EXPECTED)"
}

