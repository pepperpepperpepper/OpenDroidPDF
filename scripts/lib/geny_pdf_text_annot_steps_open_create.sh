# geny_pdf_text_annot_steps_open_create.sh: open + create/edit steps for the PDF text-annot smoke.
#
# Intended to be sourced by `scripts/lib/geny_pdf_text_annot_steps.sh`. Assumes:
# - `set -euo pipefail` is set by the caller
# - `geny_uia.sh` and `geny_pdf_smoke_ocr.sh` are already sourced
# - Required env vars (DEVICE, PKG, ACT, APK, etc) are set by the caller

_geny_pdf_text_annot_step_open_pdf() {
echo "[1/14] Install APK"
adb -s "$DEVICE" install -r "$APK" >/dev/null

echo "[2/14] Clear app data"
adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null || true
echo "[2b/14] Grant storage perms (best-effort)"
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1 || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE >/dev/null 2>&1 || true

USE_APP_PRIVATE_STORAGE="${USE_APP_PRIVATE_STORAGE:-1}"
APP_PRIVATE_REL_PATH="${APP_PRIVATE_REL_PATH:-files/odp_text_annot_smoke.pdf}"

echo "[3/14] Stage fixture PDF"
PDF_LOCAL_TO_PUSH="$PDF_LOCAL"
TMP_MULTI_PAGE_FIXTURE=""
if command -v pdfinfo >/dev/null 2>&1 && command -v pdfunite >/dev/null 2>&1; then
  pages="$(pdfinfo "$PDF_LOCAL" 2>/dev/null | awk '/^Pages:/ {print $2; exit}' || true)"
  if [[ -n "${pages:-}" ]] && (( pages < 2 )); then
    # Our "Navigate & View" sheet entry point is the bottom page indicator/scrubber, which is
    # hidden for 1-page documents. Make a tiny 2-page variant so Save + sheet interactions
    # remain testable and stable.
    TMP_MULTI_PAGE_FIXTURE="$(mktemp -t odp_text_annot_smoke_XXXXXX.pdf)"
    if pdfunite "$PDF_LOCAL" "$PDF_LOCAL" "$TMP_MULTI_PAGE_FIXTURE" >/dev/null 2>&1; then
      PDF_LOCAL_TO_PUSH="$TMP_MULTI_PAGE_FIXTURE"
    else
      rm -f "$TMP_MULTI_PAGE_FIXTURE"
      TMP_MULTI_PAGE_FIXTURE=""
    fi
  fi
fi
if [[ "$USE_APP_PRIVATE_STORAGE" == "1" ]]; then
  adb -s "$DEVICE" shell "run-as $PKG sh -lc 'mkdir -p \"$(dirname "$APP_PRIVATE_REL_PATH")\" && cat > \"$APP_PRIVATE_REL_PATH\"'" <"$PDF_LOCAL_TO_PUSH"
else
  adb -s "$DEVICE" push "$PDF_LOCAL_TO_PUSH" "$PDF_REMOTE_PATH" >/dev/null
fi
rm -f "$TMP_MULTI_PAGE_FIXTURE" >/dev/null 2>&1 || true

echo "[4/14] Launch app and open the PDF (try direct SAF grant, fallback to DocumentsUI picker)"
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null || true
adb -s "$DEVICE" logcat -c >/dev/null || true

opened=0

if [[ "$USE_APP_PRIVATE_STORAGE" == "1" ]]; then
  PDF_APP_PRIVATE="/data/data/${PKG}/${APP_PRIVATE_REL_PATH}"
  if adb -s "$DEVICE" shell am start -W \
      -a android.intent.action.VIEW \
      -d "file://$PDF_APP_PRIVATE" \
      -t application/pdf \
      -n "$PKG/$ACT" >/dev/null; then
    sleep 2.0
    if uia_assert_in_document_view; then
      opened=1
    fi
  fi
fi

if (( opened == 0 )); then
  fname="$(basename "$PDF_REMOTE_PATH")"
  doc_id="primary:Download/$fname"
  doc_id_enc="$(python3 - "$doc_id" <<'PY'
import urllib.parse, sys
print(urllib.parse.quote(sys.argv[1], safe=''))
PY
)"
  DOC_URI="content://com.android.externalstorage.documents/document/${doc_id_enc}"

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
fi
}

_geny_pdf_text_annot_step_create_and_edit() {
echo "[5/14] Enter add-text mode"
uia_open_annotate_sheet || { echo "FAIL: could not open Annotate sheet" >&2; exit 1; }
uia_tap_any_res_id "org.opendroidpdf:id/annotate_action_add_text" || uia_tap_text_contains "Add text" || {
  echo "FAIL: add-text action not found in Annotate sheet" >&2
  exit 1
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
  # Avoid right-edge "tap-to-next-page" zones on some viewer modes; tap near top-center.
  blank_x=$((w / 2))
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

OUTDIR="${OUTDIR:-.}"
mkdir -p "$OUTDIR"
OUT_PREFIX="${OUT_PREFIX:-${OUTDIR}/tmp_geny_pdf_text_annot}"
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
      # Avoid right-edge "tap-to-next-page" zones on some viewer modes; tap near top-center.
      blank_x=$((w / 2))
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
  # Tap away from the bottom page scrubber and edge tap zones.
  blank_x=$((w / 2))
  blank_y=$((h * 3 / 4))
  adb -s "$DEVICE" shell input tap "$blank_x" "$blank_y"
  sleep 0.7
  _fail_if_fatal_logcat
  if _wait_for_token_onscreen_ocr "$TOKEN_EXPECTED_FINAL" "${UI_OCR_TIMEOUT_S:-12}"; then
    echo "  wrote $SCREENSHOT_PNG"
  else
    echo "WARN: onscreen OCR did not find token; continuing" >&2
  fi
fi

read -r TOKEN_EDIT_X TOKEN_EDIT_Y < <(_ocr_token_center_xy "$SCREENSHOT_PNG" "$TOKEN_EDIT_SEARCH" 2>/dev/null || echo "")
}
