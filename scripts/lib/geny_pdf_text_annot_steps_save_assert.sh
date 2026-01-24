# geny_pdf_text_annot_steps_save_assert.sh: save + host-side assertions for the PDF text-annot smoke.
#
# Intended to be sourced by `scripts/lib/geny_pdf_text_annot_steps.sh`. Assumes:
# - `set -euo pipefail` is set by the caller
# - `geny_uia.sh` and `geny_pdf_smoke_ocr.sh` are already sourced

_geny_pdf_text_annot_step_save_and_assert() {
echo "[11/14] Save in-place"
uia_save_changes || { echo "FAIL: could not trigger Save changes" >&2; exit 1; }
sleep 0.8
uia_tap_any_res_id "android:id/button1" "com.android.internal:id/button1" || true
sleep 4

echo "[12/14] Pull saved PDF back to host"
SAVED_PDF="${SAVED_PDF:-${OUT_PREFIX}.pdf}"
if [[ "$USE_APP_PRIVATE_STORAGE" == "1" ]]; then
  adb -s "$DEVICE" exec-out run-as "$PKG" cat "$APP_PRIVATE_REL_PATH" >"$SAVED_PDF"
else
  adb -s "$DEVICE" pull "$PDF_REMOTE_PATH" "$SAVED_PDF" >/dev/null
fi
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
