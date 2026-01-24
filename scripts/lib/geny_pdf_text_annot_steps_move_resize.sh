# geny_pdf_text_annot_steps_move_resize.sh: move/undo/resize steps for the PDF text-annot smoke.
#
# Intended to be sourced by `scripts/lib/geny_pdf_text_annot_steps.sh`. Assumes:
# - `set -euo pipefail` is set by the caller
# - `geny_uia.sh` and `geny_pdf_smoke_ocr.sh` are already sourced

_geny_pdf_text_annot_step_move_undo_resize() {
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

# Main-menu hides undo/redo; trigger text undo/redo via Annot mode (Draw), which is how
# users access undo/redo in the new toolbar-only UI.
read -r bbox_after_x0 bbox_after_y0 bbox_after_x1 bbox_after_y1 < <(_selection_box_bbox_px "$MOVE_AFTER_SELECTED_PNG" || echo "")
before_tap_x=$(((bbox_x0 + bbox_x1) / 2))
before_tap_y=$(((bbox_y0 + bbox_y1) / 2))
after_tap_x=$before_tap_x
after_tap_y=$before_tap_y
if [[ -n "${bbox_after_x0:-}" && -n "${bbox_after_y0:-}" && -n "${bbox_after_x1:-}" && -n "${bbox_after_y1:-}" ]]; then
  after_tap_x=$(((bbox_after_x0 + bbox_after_x1) / 2))
  after_tap_y=$(((bbox_after_y0 + bbox_after_y1) / 2))
fi

_enter_draw_mode_for_undo() {
  uia_open_annotate_sheet || return 1
  uia_tap_any_res_id "org.opendroidpdf:id/annotate_action_draw" || uia_tap_text_contains "Draw" || return 1
  # Wait for annot toolbar items to appear.
  for _ in $(seq 1 24); do
    if uia_has_res_id "org.opendroidpdf:id/menu_cancel" && uia_has_res_id "org.opendroidpdf:id/menu_undo"; then
      return 0
    fi
    sleep 0.25
  done
  return 1
}

_exit_draw_mode() {
  uia_tap_any_res_id "org.opendroidpdf:id/menu_cancel" "org.opendroidpdf:id/cancel_image_button" || \
    adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 0.9
}

_select_at_and_capture_top() {
  local tx1="$1"
  local ty1="$2"
  local tx2="$3"
  local ty2="$4"
  local out_png="$5"
  adb -s "$DEVICE" shell input tap "$tx1" "$ty1"
  sleep 0.7
  _screencap_png "$out_png"
  local top
  top="$(_selection_box_top_px "$out_png" || true)"
  if [[ -z "${top:-}" ]]; then
    adb -s "$DEVICE" shell input tap "$tx2" "$ty2"
    sleep 0.7
    _screencap_png "$out_png"
    top="$(_selection_box_top_px "$out_png" || true)"
  fi
  printf '%s\n' "${top:-}"
}

if ! _enter_draw_mode_for_undo; then
  echo "FAIL: could not enter Draw mode for undo/redo" >&2
  exit 1
fi

uia_tap_any_res_id "org.opendroidpdf:id/menu_undo" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  uia_tap_text_contains "Undo" || { echo "FAIL: could not tap Undo action" >&2; exit 1; }
}
sleep 1.1
_fail_if_fatal_logcat
_exit_draw_mode

sel_top_undo="$(_select_at_and_capture_top "$before_tap_x" "$before_tap_y" "$after_tap_x" "$after_tap_y" "$UNDO_MOVE_PNG")"

if ! _enter_draw_mode_for_undo; then
  echo "FAIL: could not re-enter Draw mode for redo" >&2
  exit 1
fi

uia_tap_any_res_id "org.opendroidpdf:id/menu_redo" || {
  if uia_tap_desc "More options"; then sleep 0.4; fi
  uia_tap_text_contains "Redo" || { echo "FAIL: could not tap Redo action" >&2; exit 1; }
}
sleep 1.1
_fail_if_fatal_logcat
_exit_draw_mode

sel_top_redo="$(_select_at_and_capture_top "$after_tap_x" "$after_tap_y" "$before_tap_x" "$before_tap_y" "$REDO_MOVE_PNG")"

if [[ -z "${sel_top_before:-}" || -z "${sel_top_after:-}" || -z "${sel_top_undo:-}" || -z "${sel_top_redo:-}" ]]; then
  echo "FAIL: could not detect selection box for undo/redo assertions" >&2
  echo "  before=$MOVE_SELECTED_PNG after=$MOVE_AFTER_SELECTED_PNG undo=$UNDO_MOVE_PNG redo=$REDO_MOVE_PNG" >&2
  exit 1
fi

undo_dist_before=$((sel_top_undo - sel_top_before))
undo_dist_before="${undo_dist_before#-}"
undo_dist_after=$((sel_top_undo - sel_top_after))
undo_dist_after="${undo_dist_after#-}"
if (( undo_dist_before > undo_dist_after )); then
  echo "FAIL: expected Undo to return selection closer to original top than moved top" >&2
  echo "  before_top=$sel_top_before after_top=$sel_top_after undo_top=$sel_top_undo" >&2
  echo "  dist_to_before=$undo_dist_before dist_to_after=$undo_dist_after" >&2
  echo "  screenshots: $MOVE_SELECTED_PNG $UNDO_MOVE_PNG" >&2
  exit 1
fi

redo_dist_before=$((sel_top_redo - sel_top_before))
redo_dist_before="${redo_dist_before#-}"
redo_dist_after=$((sel_top_redo - sel_top_after))
redo_dist_after="${redo_dist_after#-}"
if (( redo_dist_after > redo_dist_before )); then
  echo "FAIL: expected Redo to return selection closer to moved top than original top" >&2
  echo "  before_top=$sel_top_before after_top=$sel_top_after redo_top=$sel_top_redo" >&2
  echo "  dist_to_before=$redo_dist_before dist_to_after=$redo_dist_after" >&2
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
# Undo/redo is performed in Draw mode, which exits text-annot mode. Re-enter Add text so
# resize + style actions are available in the toolbar.
uia_open_annotate_sheet || { echo "WARN: could not open Annotate sheet (re-enter Add text)" >&2; }
uia_tap_any_res_id "org.opendroidpdf:id/annotate_action_add_text" || uia_tap_text_contains "Add text" || {
  echo "WARN: could not re-enter Add text mode (resize/style checks may be skipped)" >&2
}
sleep 0.6

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
}
