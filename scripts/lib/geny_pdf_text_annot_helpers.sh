# geny_pdf_text_annot_helpers.sh: helpers for the PDF text-annotation smoke steps.
#
# Intended to be sourced by `scripts/lib/geny_pdf_text_annot_steps.sh`. Assumes:
# - `set -euo pipefail` is set by the caller
# - `geny_uia.sh` is already sourced (uia_* helpers are available)
# - Required env vars (DEVICE, PKG, etc) are set by the caller

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

_geny_pdf_text_annot_ensure_on_first_page_best_effort() {
  # The PDF text-annot smoke asserts on page-1 OCR at the end. Some emulator images (or app
  # state) can start us on a later page even after clearing app data, so force page 1.
  #
  # Only works when the bottom page indicator/scrubber exists (multi-page docs). For 1-page
  # fixtures, this is a no-op.
  if ! uia_has_res_id "org.opendroidpdf:id/page_indicator"; then
    # The page indicator can be hidden behind UI chrome; tap once to show toolbars and retry.
    for _ in $(seq 1 3); do
      _tap_doc_center
      sleep 0.6
      if uia_has_res_id "org.opendroidpdf:id/page_indicator"; then
        break
      fi
    done
  fi

  if ! uia_has_res_id "org.opendroidpdf:id/page_indicator"; then
    return 0
  fi

  uia_open_navigate_view_sheet || return 1
  # Tap "previous" a few times to be sure we land on page 1.
  for _ in $(seq 1 6); do
    uia_tap_any_res_id "org.opendroidpdf:id/navigate_view_page_prev" || true
    sleep 0.35
  done
  adb -s "$DEVICE" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 0.6
  return 0
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
