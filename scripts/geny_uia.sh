#!/usr/bin/env bash
set -euo pipefail

# Shared helpers for Genymotion/ADB UI automation.
#
# Uses UIAutomator dump + resource-id/content-desc/text to locate elements.
# This avoids fragile hard-coded coordinates when menus/layouts shift.
#
# Expected env:
#   DEVICE (adb device serial) e.g. localhost:42865

DEVICE=${DEVICE:-localhost:42865}

_uia_dump_to() {
  local out="$1"
  adb -s "$DEVICE" shell uiautomator dump /sdcard/__opendroidpdf_uia.xml >/dev/null
  adb -s "$DEVICE" exec-out cat /sdcard/__opendroidpdf_uia.xml > "$out"
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
  if uia_has_res_id "$required_rid"; then
    return 0
  fi
  echo "[uia] FAIL: not in document view (missing $required_rid)" >&2
  return 1
}
