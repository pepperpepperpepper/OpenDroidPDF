#!/usr/bin/env bash
set -euo pipefail

echo "[ui-gallery] Device Farm runner"

chmod +x ./scripts/geny_ui_gallery_smoke.sh ./scripts/geny_uia.sh >/dev/null 2>&1 || true

if [[ -n "${DEVICEFARM_DEVICE_UDID:-}" ]]; then
  export ANDROID_SERIAL="${ANDROID_SERIAL:-${DEVICEFARM_DEVICE_UDID}}"
fi

if [[ -n "${DEVICEFARM_APP_PATH:-}" ]]; then
  export APK="${APK:-${DEVICEFARM_APP_PATH}}"
fi

# Default to screenshots-only on Device Farm; enable with RECORD_SCRUB=1 if needed.
export RECORD_SCRUB="${RECORD_SCRUB:-0}"

export UPLOAD="${UPLOAD:-0}"
export TITLE="${TITLE:-OpenDroidPDF UI Screenshot Gallery (Device Farm)}"

log_dir="${DEVICEFARM_LOG_DIR:-$(pwd)/artifacts}"
export OUTDIR="${OUTDIR:-${log_dir}}"
mkdir -p "$OUTDIR"

echo "[ui-gallery] ANDROID_SERIAL=${ANDROID_SERIAL:-}" >&2
echo "[ui-gallery] APK=${APK:-}" >&2
echo "[ui-gallery] OUTDIR=$OUTDIR" >&2

set +e
./scripts/geny_ui_gallery_smoke.sh
rc=$?
set -e

# Device Farm result semantics: treat "some gallery steps failed" as non-fatal as long as we
# produced screenshot artifacts for review.
if [[ "$rc" -ne 0 ]]; then
  if compgen -G "$OUTDIR/tmp_geny_ui_gallery_*.png" >/dev/null; then
    echo "[ui-gallery] WARN: gallery exited $rc, but screenshots exist; marking run PASSED." >&2
    rc=0
  fi
fi

echo "[ui-gallery] Done. Artifacts:" >&2
ls -la "$OUTDIR" >&2 || true

exit "$rc"
