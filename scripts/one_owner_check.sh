#!/usr/bin/env bash
set -euo pipefail

# ONE OWNER guard: prevent new MuPDF PDF-API behavior from creeping into frontends.
#
# We only scan *newly added lines* in diffs for pdf_* function calls in:
#   - platform/android/
#   - platform/gl/
#
# The intent is: if you need new PDF behavior, add it to platform/common/pp_core.*
# and call it from adapters, rather than duplicating behavior in Android JNI/Java
# and the Linux frontend.
#
# Usage:
#   ./scripts/one_owner_check.sh [<base-ref>]
#
# Examples:
#   ./scripts/one_owner_check.sh origin/master
#   ./scripts/one_owner_check.sh HEAD~1

BASE_REF="${1:-origin/master}"

if ! git rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
  echo "one_owner_check: base ref not found: $BASE_REF (skipping)"
  exit 0
fi

BASE_COMMIT="$(git merge-base HEAD "$BASE_REF")"

bad=0
file=""

while IFS= read -r line; do
  case "$line" in
    "+++ b/"*)
      file="${line#+++ b/}"
      ;;
    "+++ "*)
      # /dev/null etc.
      ;;
    "+"*)
      # Ignore diff headers.
      if [[ "$line" == "+++"* ]]; then
        continue
      fi
      # Flag new pdf_* function calls (types/macros don't match this).
      if [[ "$line" =~ \bpdf_[A-Za-z0-9_]+\s*\( ]]; then
        printf 'ONE OWNER violation: %s: %s\n' "${file:-"(unknown file)"}" "$line"
        bad=1
      fi
      ;;
  esac
done < <(git diff --unified=0 "$BASE_COMMIT..HEAD" -- platform/android platform/gl)

if [[ "$bad" -ne 0 ]]; then
  cat <<'MSG'

Fix:
  - Implement the behavior in `platform/common/pp_core.{h,c}` (ONE OWNER)
  - Call it from Android JNI/Java and/or the desktop frontend.
MSG
  exit 1
fi

echo "one_owner_check: OK (no new pdf_* calls outside pp_core)"

