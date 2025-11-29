#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"

echo "Cleaning workspace artifacts…"

remove_if_exists() {
  local target="$1"
  if [[ -e "$target" || -L "$target" ]]; then
    rm -rf -- "$target"
    echo " removed $target"
  fi
}

FILE_PATTERNS=(
  "after_undo.png"
  "geny_*.png"
  "opendroidpdf_*.png"
  "screen_runtime.png"
  "logcat_*.txt"
  "namedump"
  "cquote"
  "ui*.xml"
  "ui_dump*.xml"
  "uidump*.xml"
  "window_dump*.xml"
  "dashboard*.xml"
  "ui_menu.xml"
  "undo_*.pdf"
)

DIR_PATTERNS=(
  "test-output"
  "test_outputs"
  "thirdparty_build"
)

shopt -s nullglob
for pattern in "${FILE_PATTERNS[@]}"; do
  for match in $pattern; do
    remove_if_exists "$match"
  done
done
shopt -u nullglob

for dir in "${DIR_PATTERNS[@]}"; do
  remove_if_exists "$dir"
done

echo "Workspace cleanup complete."
