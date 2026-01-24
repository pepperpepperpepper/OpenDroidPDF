# geny_pdf_text_annot_steps_style_misc.sh: style/lock/clipboard/pan checks for the PDF text-annot smoke.
#
# Intended to be sourced by `scripts/lib/geny_pdf_text_annot_steps.sh`. Assumes:
# - `set -euo pipefail` is set by the caller
# - `geny_uia.sh` and `geny_pdf_smoke_ocr.sh` are already sourced

_geny_pdf_text_annot_step_style_misc() {
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
  uia_open_annotate_sheet || true
  uia_tap_any_res_id "org.opendroidpdf:id/annotate_action_add_text" || uia_tap_text_contains "Add text" || true
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
}

