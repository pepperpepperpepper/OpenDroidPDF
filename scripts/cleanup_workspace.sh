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

for pattern in \
  "after_undo.png" \
  "geny_*.png" \
  "penandpdf_*.png" \
  "screen_runtime.png" \
  "logcat_*.txt" \
  "namedump" \
  "cquote"; do
  for match in $pattern; do
    remove_if_exists "$match"
  done
done

for dir in test-output test_outputs thirdparty_build; do
  remove_if_exists "$dir"
done

echo "Workspace cleanup complete."
