#!/usr/bin/env bash
set -euo pipefail

# Genymotion "LLM UI gallery" smoke:
# - Captures a screenshot walkthrough of the current Assistant / LLM UI surfaces
#   (Assistant sheet, privacy preview, providers screen).
# - Publishes a browsable index.html via wtf-upload (through scripts/qa_report_upload.sh).
#
# Usage:
#   DEVICE=localhost:<port> ./scripts/geny_llm_ui_gallery_smoke.sh
#   DEVICE=localhost:<port> UPLOAD_PREFIX=qa/llm-ui-gallery/ ./scripts/geny_llm_ui_gallery_smoke.sh
#
# Notes:
# - Uses a local OpenAI-compatible stub server reachable from the device via `adb reverse`,
#   so the gallery can include real assistant answers without external API keys.

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-${DEVICEFARM_APP_PATH:-}}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/geny_uia.sh"

PKG="org.opendroidpdf"
ACT=".OpenDroidPDFActivity"

OUTDIR="${OUTDIR:-${ROOT_DIR}/tmp_geny_llm_ui_gallery_$(date -u +%Y%m%d_%H%M%S)}"
mkdir -p "$OUTDIR"
OUT_PREFIX="${OUT_PREFIX:-${OUTDIR}/tmp_geny_llm_ui_gallery}"

TITLE="${TITLE:-OpenDroidPDF Assistant (LLM) UI Screenshot Gallery}"
UPLOAD="${UPLOAD:-1}"
UPLOAD_PREFIX="${UPLOAD_PREFIX:-}"

PDF_LOCAL="${PDF_LOCAL:-${ROOT_DIR}/test_assets/pdf_with_text.pdf}"
PDF_REMOTE="${PDF_REMOTE:-/data/data/org.opendroidpdf/files/llm_gallery_pdf_with_text.pdf}"

RESET_APP_DATA="${RESET_APP_DATA:-1}"

SHOT_N=0

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
  local out slug num
  slug="$(_slugify "$label")"
  SHOT_N=$((SHOT_N + 1))
  printf -v num "%03d" "$SHOT_N"
  out="${OUT_PREFIX}_${num}_${slug}.png"
  adb -s "$DEVICE" exec-out screencap -p >"$out" 2>/dev/null || {
    echo "WARN: screencap failed: $out" >&2
    return 1
  }
  echo "  wrote $out" >&2
  return 0
}

_wm_size() {
  local line
  line="$(adb -s "$DEVICE" shell wm size 2>/dev/null | tr -d '\r' | _uia_grep_o '[0-9]+x[0-9]+' | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "FAIL: unable to read device size via 'wm size'" >&2
    return 1
  fi
  echo "${line%x*} ${line#*x}"
}

_ensure_reader_toolbar_visible() {
  # Reader chrome can be toggled by tapping the page. Ensure the top toolbar is visible so we can
  # access overflow -> Assistant/Settings reliably.
  if uia_has_res_id "org.opendroidpdf:id/menu_annotate" || uia_has_res_id "org.opendroidpdf:id/menu_search"; then
    return 0
  fi
  local w h x y
  if ! read -r w h < <(_wm_size); then
    return 1
  fi
  x=$((w / 2))
  y=$((h / 2))
  adb -s "$DEVICE" shell input tap "$x" "$y" >/dev/null 2>&1 || true
  sleep 0.5
  if uia_has_res_id "org.opendroidpdf:id/menu_annotate" || uia_has_res_id "org.opendroidpdf:id/menu_search"; then
    return 0
  fi
  return 1
}

_open_overflow_menu() {
  if ! _ensure_reader_toolbar_visible; then
    echo "[llm-gallery] WARN: reader toolbar not visible; attempting to proceed" >&2
  fi

  if uia_tap_desc "More options" >/dev/null 2>&1; then
    :
  else
    # Fallback: tap top-right corner where the overflow button usually lives.
    local w h
    read -r w h < <(_wm_size)
    adb -s "$DEVICE" shell input tap $((w - 20)) 70 >/dev/null 2>&1 || true
  fi

  for _ in $(seq 1 12); do
    if uia_has_text_contains "View settings"; then
      return 0
    fi
    sleep 0.2
  done
  return 1
}

_open_assistant_sheet_from_overflow() {
  # Note: assistant sheet root containers may be elided in --compressed dumps; key off stable children.
  if uia_has_res_id "org.opendroidpdf:id/assistant_sheet_title" || uia_has_res_id "org.opendroidpdf:id/assistant_sheet_setup_provider"; then
    return 0
  fi

  _open_overflow_menu || return 1
  uia_tap_text_contains "Assistant" || return 1

  for _ in $(seq 1 20); do
    if uia_has_res_id "org.opendroidpdf:id/assistant_sheet_title" || uia_has_res_id "org.opendroidpdf:id/assistant_sheet_setup_provider"; then
      return 0
    fi
    sleep 0.2
  done
  return 1
}

_ensure_assistant_prompt_row_visible() {
  for _ in 1 2 3; do
    if uia_has_res_id "org.opendroidpdf:id/assistant_sheet_prompt"; then
      return 0
    fi
    uia_expand_bottom_sheet_best_effort >/dev/null 2>&1 || true
    uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_expand_toggle" >/dev/null 2>&1 || true
    sleep 0.5
  done
  return 1
}

_pick_free_port() {
  python3 - <<'PY'
import socket
for p in range(8788, 8838):
    s = socket.socket()
    try:
        s.bind(("127.0.0.1", p))
    except OSError:
        continue
    s.close()
    print(p)
    raise SystemExit(0)
raise SystemExit(1)
PY
}

STUB_PID=""
STUB_PORT=""
cleanup() {
  set +e
  if [[ -n "${STUB_PID}" ]]; then
    kill "${STUB_PID}" >/dev/null 2>&1 || true
    wait "${STUB_PID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${STUB_PORT}" ]]; then
    adb -s "$DEVICE" reverse --remove "tcp:${STUB_PORT}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "[llm-gallery] Using DEVICE=$DEVICE" >&2
adb -s "$DEVICE" get-state >/dev/null

echo "[llm-gallery] Installing APK..." >&2
apk="$(_resolve_apk)"
_install_apk "$apk"

if [[ "$RESET_APP_DATA" == "1" ]]; then
  echo "[llm-gallery] Resetting app data (pm clear)..." >&2
  adb -s "$DEVICE" shell pm clear "$PKG" >/dev/null 2>&1 || true
fi

echo "[llm-gallery] Granting storage perms (best-effort)..." >&2
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell pm grant "$PKG" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb -s "$DEVICE" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "[llm-gallery] Starting local OpenAI-compatible stub server..." >&2
STUB_PORT="$(_pick_free_port)"
python3 -u - "$STUB_PORT" >"${OUTDIR}/tmp_geny_llm_ui_gallery_stub_server.log" 2>&1 <<'PY' &
import json, re, sys, time
from http.server import BaseHTTPRequestHandler, HTTPServer
from socketserver import ThreadingMixIn

port = int(sys.argv[1])

def _json(obj):
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")

def _extract_question_and_context(messages):
    user = ""
    for m in reversed(messages or []):
        if isinstance(m, dict) and m.get("role") == "user":
            user = m.get("content") or ""
            break
    if "QUESTION:" in user and "CONTEXT:" in user:
        q = user.split("QUESTION:", 1)[1].split("CONTEXT:", 1)[0].strip()
        ctx = user.split("CONTEXT:", 1)[1].strip()
        return q, ctx
    return user.strip(), ""

def _pages_from_context(ctx):
    try:
        return [int(x) for x in re.findall(r"Page\\s+(\\d+)\\s*:", ctx or "")]
    except Exception:
        return []

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        # Quiet, but keep essential access logs.
        sys.stderr.write("%s - - [%s] %s\n" % (self.client_address[0], self.log_date_time_string(), fmt % args))

    def do_POST(self):
        if not self.path.endswith("/v1/chat/completions"):
            self.send_response(404)
            self.end_headers()
            return

        n = int(self.headers.get("content-length", "0") or "0")
        raw = self.rfile.read(n) if n > 0 else b""
        try:
            req = json.loads(raw.decode("utf-8", "replace"))
        except Exception:
            req = {}

        messages = req.get("messages") or []
        system = ""
        for m in messages:
            if isinstance(m, dict) and m.get("role") == "system":
                system = (m.get("content") or "")
                break
        is_summary = "Return only the requested summary text" in system

        question, ctx = _extract_question_and_context(messages)
        pages = _pages_from_context(ctx)
        citations = pages[:2] if pages else []

        # Simulate latency for "slow" screenshots.
        if "slow" in (question or "").lower():
            time.sleep(3.5)

        if is_summary:
            content = (
                "Summary (stub)\n"
                "- Main topic: document overview\n"
                "- Key idea: show current Assistant UI\n"
                "- Notes: this response is generated by a local stub server via adb reverse\n"
            )
        else:
            if not citations and pages:
                citations = [pages[0]]
            related = [
                "What are the main takeaways?",
                "List any named entities mentioned.",
                "What should I do next based on this?",
            ]
            answer = "Stub answer for UI review.\n\nQuestion:\n" + (question or "(empty)")[:600]
            obj = {"answerText": answer, "citations": citations, "relatedQuestions": related}
            content = json.dumps(obj, ensure_ascii=False)

        resp = {
            "id": "stub_chatcmpl",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": req.get("model") or "stub-model",
            "choices": [
                {
                    "index": 0,
                    "message": {"role": "assistant", "content": content},
                    "finish_reason": "stop",
                }
            ],
        }
        payload = _json(resp)
        self.send_response(200)
        self.send_header("content-type", "application/json; charset=utf-8")
        self.send_header("content-length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

class ThreadingHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True

srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
print(f"stub server listening on 127.0.0.1:{port}", file=sys.stderr)
srv.serve_forever()
PY
STUB_PID="$!"
sleep 0.2

echo "[llm-gallery] Enabling adb reverse to stub server (port=$STUB_PORT)..." >&2
adb -s "$DEVICE" reverse "tcp:${STUB_PORT}" "tcp:${STUB_PORT}" >/dev/null

echo "[llm-gallery] Pushing sample PDF..." >&2
adb -s "$DEVICE" shell "run-as $PKG sh -lc 'mkdir -p files && cat > files/llm_gallery_pdf_with_text.pdf'" <"$PDF_LOCAL"

echo "[llm-gallery] Launching viewer..." >&2
adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
sleep 2
uia_assert_in_document_view
_ensure_reader_toolbar_visible || true

echo "[llm-gallery] Capturing Assistant entry point..." >&2
_open_overflow_menu || true
_shot "overflow_menu_assistant_entry"

uia_tap_text_contains "Assistant" || {
  echo "[llm-gallery] FAIL: Assistant menu item not found in overflow menu" >&2
  exit 1
}
sleep 1.0
_shot "assistant_sheet_disabled_state"

echo "[llm-gallery] Opening Settings via Set up..." >&2
uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_setup_provider" || {
  echo "[llm-gallery] FAIL: Set up button not found in Assistant sheet" >&2
  exit 1
}
sleep 1.2
_shot "settings_top"

echo "[llm-gallery] Enabling Assistant (LLM)..." >&2
# Scroll until the Assistant category is visible.
for _ in $(seq 1 8); do
  if uia_has_text_contains "Enable Assistant (LLM)"; then break; fi
  adb -s "$DEVICE" shell input swipe 285 980 285 320 350 >/dev/null 2>&1 || true
  sleep 0.4
done
if ! uia_has_text_contains "Enable Assistant (LLM)"; then
  echo "[llm-gallery] FAIL: could not find Assistant settings section" >&2
  exit 1
fi
_shot "settings_assistant_section"

uia_tap_text_contains "Enable Assistant (LLM)" || true
sleep 0.6
_shot "settings_assistant_enabled"

echo "[llm-gallery] Returning to viewer..." >&2
for _ in $(seq 1 6); do
  if uia_assert_in_document_view >/dev/null 2>&1; then
    break
  fi
  adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 0.7
done
if ! uia_assert_in_document_view >/dev/null 2>&1; then
  echo "[llm-gallery] WARN: unable to return to viewer via BACK; restarting viewer" >&2
  adb -s "$DEVICE" shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adb -s "$DEVICE" shell am start -W -a android.intent.action.VIEW -d "file://$PDF_REMOTE" -t application/pdf "$PKG/$ACT" >/dev/null
  sleep 2
  uia_assert_in_document_view
fi
_ensure_reader_toolbar_visible || true

echo "[llm-gallery] Opening Providers via Set up..." >&2
if ! _open_assistant_sheet_from_overflow; then
  echo "[llm-gallery] FAIL: Assistant menu item not found in overflow menu" >&2
  exit 1
fi
sleep 1.0
_shot "assistant_sheet_provider_unconfigured"

uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_setup_provider" || {
  echo "[llm-gallery] FAIL: Set up provider button missing" >&2
  exit 1
}
sleep 1.0
_shot "assistant_providers_list_before_add"

echo "[llm-gallery] Adding stub provider..." >&2
uia_tap_res_id "org.opendroidpdf:id/assistant_providers_add" || uia_tap_text_contains "Add provider"
sleep 0.8
_shot "assistant_provider_add_dialog"

uia_tap_res_id "org.opendroidpdf:id/assistant_provider_edit_name" || true
adb -s "$DEVICE" shell input text "LocalStub" >/dev/null 2>&1 || true
sleep 0.2
uia_tap_res_id "org.opendroidpdf:id/assistant_provider_edit_base_url" || true
adb -s "$DEVICE" shell input text "http://127.0.0.1:${STUB_PORT}" >/dev/null 2>&1 || true
sleep 0.2
uia_tap_res_id "org.opendroidpdf:id/assistant_provider_edit_model" || true
adb -s "$DEVICE" shell input text "stub-model" >/dev/null 2>&1 || true
sleep 0.2
uia_tap_res_id "org.opendroidpdf:id/assistant_provider_edit_api_key" || true
adb -s "$DEVICE" shell input text "stub-key-1234" >/dev/null 2>&1 || true
sleep 0.4
_shot "assistant_provider_add_dialog_filled"

uia_tap_res_id "android:id/button1" || uia_tap_text_contains "Save" || true
sleep 1.0
_shot "assistant_providers_list_with_stub"

echo "[llm-gallery] Returning to Assistant sheet..." >&2
adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 1.0
if ! uia_has_res_id "org.opendroidpdf:id/assistant_sheet_root"; then
  echo "[llm-gallery] WARN: assistant sheet not visible after returning from Providers; reopening" >&2
  uia_assert_in_document_view || true
  _open_assistant_sheet_from_overflow || true
  sleep 1.0
fi

echo "[llm-gallery] Reopening Assistant sheet to refresh provider row..." >&2
uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_close" >/dev/null 2>&1 || adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 1.0
uia_assert_in_document_view || true

if ! _open_assistant_sheet_from_overflow; then
  echo "[llm-gallery] FAIL: could not reopen Assistant sheet" >&2
  exit 1
fi
sleep 0.8
_ensure_assistant_prompt_row_visible || true
_shot "assistant_sheet_configured_ask"

echo "[llm-gallery] Preview text dialog..." >&2
uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_preview" || true
sleep 0.9
_shot "assistant_preview_text_dialog"
uia_tap_res_id "android:id/button1" || uia_tap_text_contains "OK" || true
sleep 0.6

echo "[llm-gallery] Capturing Assistant options menu..." >&2
uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_options" || true
sleep 0.3
_shot "assistant_options_menu"
adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 0.4

echo "[llm-gallery] Read aloud mode..." >&2
uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_mode_read_aloud" || uia_tap_text_contains "Read aloud" || true
sleep 0.8
_shot "assistant_read_aloud_mode"
uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_mode_ask" || uia_tap_text_contains "Ask" || true
sleep 0.5

echo "[llm-gallery] Sending an Ask (with preview)..." >&2
if uia_has_res_id "org.opendroidpdf:id/assistant_sheet_prompt" || uia_has_res_id "org.opendroidpdf:id/assistant_sheet_send"; then
  if ! _ensure_assistant_prompt_row_visible; then
    echo "[llm-gallery] WARN: Assistant prompt row not visible; skipping Ask flow" >&2
  else
    uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_prompt" || true
    adb -s "$DEVICE" shell input text "What%sis%sthis%sabout%3F" >/dev/null 2>&1 || true
    sleep 0.3
    _shot "assistant_ask_ready"

    uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_send" || uia_tap_text_contains "Send" || {
      echo "[llm-gallery] WARN: Send button not found; skipping Ask flow" >&2
    }

    if uia_has_text_contains "Preview text" || uia_has_res_id "org.opendroidpdf:id/assistant_preview_allow_session"; then
      sleep 0.9
      _shot "assistant_privacy_preview_ask"

      uia_tap_res_id "org.opendroidpdf:id/assistant_preview_allow_session" || uia_tap_text_contains "Allow for this session" || true
      sleep 0.2
      _shot "assistant_privacy_preview_ask_allowed"

      uia_tap_res_id "android:id/button1" || uia_tap_text_contains "Send" || true
    fi

    # Wait for a response bubble to appear.
    for _ in $(seq 1 40); do
      if uia_has_text_contains "Stub answer for UI review" || uia_has_text_contains "Sources:" || uia_has_text_contains "Try asking:"; then
        break
      fi
      sleep 0.25
    done
    sleep 0.4
    _shot "assistant_ask_answer_with_sources_actions"
  fi
else
  echo "[llm-gallery] WARN: Ask prompt row not present in UI; skipping Ask flow screenshots" >&2
fi

echo "[llm-gallery] Summary mode..." >&2
uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_mode_summary" || uia_tap_text_contains "Summary" || true
sleep 0.8
_shot "assistant_summary_mode"

uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_summary_generate" || uia_tap_text_contains "Generate" || true
sleep 0.9
_shot "assistant_privacy_preview_summary"

uia_tap_res_id "org.opendroidpdf:id/assistant_preview_allow_session" || true
sleep 0.2
uia_tap_res_id "android:id/button1" || uia_tap_text_contains "Generate" || true

for _ in $(seq 1 40); do
  if uia_has_text_contains "Summary (stub)"; then
    break
  fi
  sleep 0.25
done
sleep 0.4
_shot "assistant_summary_output_actions"

uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_close" >/dev/null 2>&1 || true
sleep 0.8

echo "[llm-gallery] Attempting best-effort text selection + Explain entry point..." >&2
set +e
read -r W H < <(_wm_size)
set -e
if [[ -n "${W:-}" && -n "${H:-}" ]]; then
  # Try a few long-press coordinates to trigger text selection.
  sel_ok=0
  for frac in 35 45 55; do
    x=$((W * 55 / 100))
    y=$((H * frac / 100))
    adb -s "$DEVICE" shell input swipe "$x" "$y" "$x" "$y" 700 >/dev/null 2>&1 || true
    sleep 0.7
    if uia_has_res_id "org.opendroidpdf:id/selection_action_explain" || uia_has_res_id "org.opendroidpdf:id/reader_selection_actions_bar"; then
      sel_ok=1
      break
    fi
  done
  if [[ "$sel_ok" == "1" ]]; then
    _shot "text_selection_action_bar_with_explain"
    if uia_tap_res_id "org.opendroidpdf:id/selection_action_explain" >/dev/null 2>&1; then
      sleep 1.0
      _shot "assistant_opened_from_selection_explain"
      # Close sheet to avoid covering final uploads.
      uia_tap_res_id "org.opendroidpdf:id/assistant_sheet_close" >/dev/null 2>&1 || true
      sleep 0.5
    fi
  fi
fi

if [[ "$UPLOAD" != "1" ]]; then
  echo "[llm-gallery] UPLOAD=0; skipping publish. Artifacts in: $OUTDIR" >&2
  exit 0
fi

echo "[llm-gallery] Publishing report via wtf-upload..." >&2
prefix="$UPLOAD_PREFIX"
if [[ -z "$prefix" ]]; then
  prefix="qa/llm-ui-gallery/geny/$(date -u +%Y/%m/%d/%H%M%S)/"
fi

report_url="$(cd "$ROOT_DIR" && ./scripts/qa_report_upload.sh --title "$TITLE" --prefix "$prefix" --outdir "$OUTDIR" "$OUTDIR"/tmp_geny_llm_ui_gallery_*.png "$OUTDIR"/tmp_geny_llm_ui_gallery_*.log | tail -n 1)"
echo "$report_url"
