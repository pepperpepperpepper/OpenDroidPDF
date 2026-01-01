#!/usr/bin/env bash
set -euo pipefail

# Shared helpers for Genymotion/ADB UI automation.
#
# Uses UIAutomator dump + resource-id/content-desc/text to locate elements.
# This avoids fragile hard-coded coordinates when menus/layouts shift.
#
# Expected env:
#   DEVICE (adb device serial) e.g. localhost:<port> (Genymotion SaaS) or emulator-5554
#   - If DEVICE is unset, falls back to GENYMOTION_DEV, then ANDROID_SERIAL, then auto-detects
#     from `adb devices` (prefers localhost:*).
#   - Genymotion SaaS example:
#       DEVICE="$(gmsaas instances adbconnect <INSTANCE_UUID>)" ./scripts/geny_smoke.sh

if [[ -z "${DEVICE:-}" ]]; then
  DEVICE="${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}"
fi

if [[ -z "${DEVICE:-}" ]]; then
  if ! command -v adb >/dev/null 2>&1; then
    echo "[uia] FAIL: adb not found in PATH" >&2
    if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then return 2; else exit 2; fi
  fi

  mapfile -t _adb_serials < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')

  _pick=""
  for s in "${_adb_serials[@]}"; do [[ "$s" == localhost:* ]] && _pick="$s" && break; done
  if [[ -z "$_pick" ]]; then for s in "${_adb_serials[@]}"; do [[ "$s" == 127.0.0.1:* ]] && _pick="$s" && break; done; fi
  if [[ -z "$_pick" ]]; then for s in "${_adb_serials[@]}"; do [[ "$s" == emulator-* ]] && _pick="$s" && break; done; fi
  if [[ -z "$_pick" && "${#_adb_serials[@]}" -eq 1 ]]; then _pick="${_adb_serials[0]}"; fi

  if [[ -n "$_pick" ]]; then
    DEVICE="$_pick"
  elif [[ "${#_adb_serials[@]}" -gt 1 ]]; then
    echo "[uia] FAIL: multiple adb devices detected; set DEVICE (or GENYMOTION_DEV/ANDROID_SERIAL)." >&2
    echo "[uia] connected devices:" >&2
    printf '%s\n' "${_adb_serials[@]}" >&2
    if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then return 2; else exit 2; fi
  fi
fi

if [[ -z "${DEVICE:-}" ]]; then
  echo "[uia] FAIL: no adb device detected. Set DEVICE (adb serial) or run 'adb devices -l'." >&2
  echo '[uia] Genymotion SaaS: DEVICE="$(gmsaas instances adbconnect <INSTANCE_UUID>)" ./scripts/geny_smoke.sh' >&2
  if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then return 2; else exit 2; fi
fi

uia_disable_flaky_ime() {
  # Some Genymotion images ship with "New Soft Keyboard Dev" as the default IME and it can ANR,
  # which breaks UIAutomator-based smokes. Best-effort disable it and switch to a stable IME.
  local flaky_pkg="wtf.uhoh.newsoftkeyboard"
  local stable_ime="com.android.inputmethod.latin/.LatinIME"

  adb -s "$DEVICE" shell pm list packages 2>/dev/null | tr -d '\r' | rg -q "^package:${flaky_pkg}$" \
    && adb -s "$DEVICE" shell pm disable-user --user 0 "$flaky_pkg" >/dev/null 2>&1 || true

  adb -s "$DEVICE" shell ime list -s 2>/dev/null | tr -d '\r' | rg -q "^${stable_ime}$" \
    && adb -s "$DEVICE" shell ime set "$stable_ime" >/dev/null 2>&1 || true
}

# Most Genymotion/CI images are safe to mutate, and a flaky default IME can derail smokes.
# Default to disabling it unless callers explicitly opt out (UIA_DISABLE_FLAKY_IME=0).
if [[ "${UIA_DISABLE_FLAKY_IME:-1}" != "0" ]]; then
  uia_disable_flaky_ime || true
fi

_uia_dump_to() {
  local out="$1"
  local device_path="/sdcard/__opendroidpdf_uia.xml"
  local max_attempts="${UIA_DUMP_RETRIES:-6}"
  local sleep_s="${UIA_DUMP_RETRY_SLEEP_S:-0.25}"
  local attempt dump_out

  # UIAutomator can be flaky on some emulator images (especially if a prior dump crashed),
  # so we retry and validate that we actually got a <hierarchy> document.
  for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    dump_out="$(adb -s "$DEVICE" shell uiautomator dump "$device_path" 2>&1 || true)"
    if [[ "$dump_out" == *"UI hierchary dumped to:"* ]]; then
      adb -s "$DEVICE" exec-out cat "$device_path" > "$out" 2>/dev/null || true
      if rg -q "<hierarchy" "$out" 2>/dev/null; then
        # If a system ANR dialog steals focus, UIAutomator dumps that instead of our app.
        # Dismiss it and retry so callers see the underlying app UI.
        if rg -q 'resource-id="android:id/aerr_wait"' "$out" 2>/dev/null; then
          if coords="$(_uia_center_for "$out" rid "android:id/aerr_wait" 2>/dev/null)"; then
            set -- $coords
            adb -s "$DEVICE" shell input tap "$1" "$2" || true
            sleep 0.6
            continue
          fi
        fi
        return 0
      fi
    fi
    sleep "$sleep_s"
  done

  echo "[uia] FAIL: UIAutomator dump failed after ${max_attempts} attempts" >&2
  echo "[uia] last dump output: ${dump_out}" >&2
  return 1
}

_uia_center_for() {
  local xml="$1"
  local mode="$2"
  local needle="$3"
  python - "$xml" "$mode" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET

xml_path, mode, needle = sys.argv[1], sys.argv[2], sys.argv[3]
tree = ET.parse(xml_path)

def center(bounds: str):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    l, t, r, b = map(int, m.groups())
    return (l + r) // 2, (t + b) // 2

def match(node):
    if mode == "rid":
        return node.attrib.get("resource-id", "") == needle
    if mode == "desc":
        return node.attrib.get("content-desc", "") == needle
    if mode == "text-eq":
        return node.attrib.get("text", "") == needle
    if mode == "text-contains":
        return needle.lower() in (node.attrib.get("text", "") or "").lower()
    raise SystemExit(2)

for node in tree.iter("node"):
    if not match(node):
        continue
    b = node.attrib.get("bounds")
    c = center(b)
    if not c:
        continue
    print(f"{c[0]} {c[1]}")
    raise SystemExit(0)

raise SystemExit(1)
PY
}

uia_has_res_id() {
  local rid="$1"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  if _uia_center_for "$tmp" rid "$rid" >/dev/null 2>&1; then
    rm -f "$tmp"
    return 0
  fi
  rm -f "$tmp"
  return 1
}

uia_has_text_contains() {
  local text="$1"
  local tmp
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  if _uia_center_for "$tmp" text-contains "$text" >/dev/null 2>&1; then
    rm -f "$tmp"
    return 0
  fi
  rm -f "$tmp"
  return 1
}

uia_tap_res_id() {
  local rid="$1"
  local tmp coords
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  if coords="$(_uia_center_for "$tmp" rid "$rid" 2>/dev/null)"; then
    rm -f "$tmp"
    set -- $coords
    adb -s "$DEVICE" shell input tap "$1" "$2"
    return 0
  fi
  rm -f "$tmp"
  return 1
}

uia_tap_any_res_id() {
  local tmp coords rid
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  for rid in "$@"; do
    if coords="$(_uia_center_for "$tmp" rid "$rid" 2>/dev/null)"; then
      rm -f "$tmp"
      set -- $coords
      adb -s "$DEVICE" shell input tap "$1" "$2"
      return 0
    fi
  done
  rm -f "$tmp"
  return 1
}

uia_long_press_any_res_id() {
  # Long-press on the first matching resource-id.
  # Duration can be overridden via UIA_LONG_PRESS_MS env var.
  local duration="${UIA_LONG_PRESS_MS:-700}"
  local tmp coords rid
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  for rid in "$@"; do
    if coords="$(_uia_center_for "$tmp" rid "$rid" 2>/dev/null)"; then
      rm -f "$tmp"
      set -- $coords
      adb -s "$DEVICE" shell input swipe "$1" "$2" "$1" "$2" "$duration"
      return 0
    fi
  done
  rm -f "$tmp"
  return 1
}

uia_tap_desc() {
  local desc="$1"
  local tmp coords
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  if coords="$(_uia_center_for "$tmp" desc "$desc" 2>/dev/null)"; then
    rm -f "$tmp"
    set -- $coords
    adb -s "$DEVICE" shell input tap "$1" "$2"
    return 0
  fi
  rm -f "$tmp"
  return 1
}

uia_tap_text_contains() {
  local text="$1"
  local tmp coords
  tmp="$(mktemp)"
  _uia_dump_to "$tmp"
  if coords="$(_uia_center_for "$tmp" text-contains "$text" 2>/dev/null)"; then
    rm -f "$tmp"
    set -- $coords
    adb -s "$DEVICE" shell input tap "$1" "$2"
    return 0
  fi
  rm -f "$tmp"
  return 1
}

uia_assert_in_document_view() {
  # In our Phase-2 fragment swap, the document view exists only when DocumentHostFragment is active.
  local required_rid="org.opendroidpdf:id/document_host_container"
  local timeout_s="${UIA_DOC_VIEW_TIMEOUT_S:-12}"
  local start now
  start="$(date +%s)"
  while true; do
    # Dismiss system ANR overlays that can block UIAutomator from seeing the app window.
    # Common on some Genymotion images ("New Soft Keyboard Dev isn't responding").
    local tmp coords
    tmp="$(mktemp)"
    if _uia_dump_to "$tmp"; then
      if coords="$(_uia_center_for "$tmp" rid "android:id/aerr_wait" 2>/dev/null)"; then
        set -- $coords
        adb -s "$DEVICE" shell input tap "$1" "$2"
        rm -f "$tmp"
        sleep 0.6
        continue
      fi
      if _uia_center_for "$tmp" rid "$required_rid" >/dev/null 2>&1; then
        rm -f "$tmp"
        return 0
      fi
    fi
    rm -f "$tmp"
    now="$(date +%s)"
    if (( now - start >= timeout_s )); then
      break
    fi
    sleep 0.4
  done

  echo "[uia] FAIL: not in document view after ${timeout_s}s (missing $required_rid)" >&2
  return 1
}
