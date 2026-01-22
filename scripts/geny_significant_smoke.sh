#!/usr/bin/env bash
set -euo pipefail

# "Significant" Genymotion smoke suite.
# Runs a curated set of end-to-end smokes that exercise core PDF open/render,
# drawing/eraser, Fill & Sign, and Phase 3 text annotations/markup.
#
# Usage:
#   DEVICE=localhost:<port> APK=/path/to/OpenDroidPDF-debug.apk ./scripts/geny_significant_smoke.sh
#
# Genymotion SaaS (optional):
#   If DEVICE is unset and gmsaas is available, this script can start and ADB-connect
#   a temporary Android 14 instance automatically.
#   - Override recipe with GENY_RECIPE_UUID
#   - Keep the instance running with GENY_KEEP_INSTANCE=1
#
# Outputs:
#   OUTDIR (default: /tmp/opendroidpdf_significant_smoke_<timestamp>)

DEVICE="${DEVICE:-${GENYMOTION_DEV:-${ANDROID_SERIAL:-}}}"
APK="${APK:-/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk}"
OUTDIR="${OUTDIR:-/tmp/opendroidpdf_significant_smoke_$(date +%Y%m%d_%H%M%S)}"

GENY_RECIPE_UUID="${GENY_RECIPE_UUID:-9074ccc1-7aba-4c9b-b615-e69ef389738c}" # Android 14 Genymotion Phone
GENY_INSTANCE_NAME="${GENY_INSTANCE_NAME:-odp_significant_smoke_$(date +%Y%m%d_%H%M%S)}"
GENY_MAX_RUN_DURATION_MIN="${GENY_MAX_RUN_DURATION_MIN:-180}"
GENY_KEEP_INSTANCE="${GENY_KEEP_INSTANCE:-0}"

GMSAAS_TMPDIR="${GMSAAS_TMPDIR:-$HOME/.Genymobile/gmsaas/tmp_${USER}_gmadbtunneld}"

PDF_LOCAL_MARKUP="${PDF_LOCAL_MARKUP:-list.pdf}"

PKG="org.opendroidpdf"

log() { echo "[significant-smoke] $*"; }

_pick_adb_device() {
  local pick=""
  mapfile -t serials < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  for s in "${serials[@]}"; do [[ "$s" == localhost:* ]] && pick="$s" && break; done
  if [[ -z "$pick" ]]; then for s in "${serials[@]}"; do [[ "$s" == 127.0.0.1:* ]] && pick="$s" && break; done; fi
  if [[ -z "$pick" ]]; then for s in "${serials[@]}"; do [[ "$s" == emulator-* ]] && pick="$s" && break; done; fi
  if [[ -z "$pick" && "${#serials[@]}" -eq 1 ]]; then pick="${serials[0]}"; fi
  printf '%s' "$pick"
}

GENY_INSTANCE_UUID="${GENY_INSTANCE_UUID:-}"
GENY_STARTED="0"

cleanup() {
  local ec="$?"
  set +e
  if [[ -n "$GENY_INSTANCE_UUID" ]]; then
    log "Disconnecting ADB tunnel (instance=$GENY_INSTANCE_UUID)"
    TMPDIR="$GMSAAS_TMPDIR" gmsaas instances adbdisconnect "$GENY_INSTANCE_UUID" >/dev/null 2>&1 || true

    if [[ "$GENY_STARTED" == "1" && "$GENY_KEEP_INSTANCE" != "1" ]]; then
      log "Stopping Genymotion instance (instance=$GENY_INSTANCE_UUID)"
      TMPDIR="$GMSAAS_TMPDIR" gmsaas instances stop "$GENY_INSTANCE_UUID" --no-wait >/dev/null 2>&1 || true
    fi
  fi
  exit "$ec"
}
trap cleanup EXIT

mkdir -p "$OUTDIR"
mkdir -p "$GMSAAS_TMPDIR" 2>/dev/null || true

if [[ ! -f "$APK" ]]; then
  echo "FAIL: APK not found: $APK" >&2
  exit 2
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "FAIL: adb not found in PATH" >&2
  exit 2
fi

if [[ -z "$DEVICE" ]]; then
  DEVICE="$(_pick_adb_device)"
fi

if [[ -z "$DEVICE" ]]; then
  if [[ -n "$GENY_INSTANCE_UUID" ]]; then
    if ! command -v gmsaas >/dev/null 2>&1; then
      echo "FAIL: GENY_INSTANCE_UUID set but gmsaas not found in PATH" >&2
      exit 2
    fi
    log "ADB-connecting to existing Genymotion instance (instance=$GENY_INSTANCE_UUID)"
    DEVICE="$(TMPDIR="$GMSAAS_TMPDIR" gmsaas instances adbconnect "$GENY_INSTANCE_UUID")"
  elif command -v gmsaas >/dev/null 2>&1; then
    log "Starting Genymotion SaaS instance (recipe=$GENY_RECIPE_UUID name=$GENY_INSTANCE_NAME)"
    GENY_INSTANCE_UUID="$(gmsaas --format json instances start "$GENY_RECIPE_UUID" "$GENY_INSTANCE_NAME" --max-run-duration "$GENY_MAX_RUN_DURATION_MIN" | jq -r '.instance.uuid // .uuid // empty')"
    if [[ -z "$GENY_INSTANCE_UUID" || "$GENY_INSTANCE_UUID" == "null" ]]; then
      echo "FAIL: could not determine instance UUID from gmsaas output" >&2
      exit 2
    fi
    GENY_STARTED="1"

    log "ADB-connecting to instance (instance=$GENY_INSTANCE_UUID)"
    DEVICE="$(TMPDIR="$GMSAAS_TMPDIR" gmsaas instances adbconnect "$GENY_INSTANCE_UUID")"
  else
    echo "FAIL: no DEVICE specified and gmsaas not found. Set DEVICE=localhost:<port>." >&2
    exit 2
  fi
fi

log "Using DEVICE=$DEVICE"
adb -s "$DEVICE" get-state >/dev/null
export DEVICE APK

_slug() {
  echo "$1" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '_' | sed 's/^_\\+//;s/_\\+$//'
}

FAIL=0
SUMMARY="$OUTDIR/summary.txt"
: >"$SUMMARY"

ensure_device() {
  if adb -s "$DEVICE" get-state >/dev/null 2>&1; then
    return 0
  fi
  if [[ -n "$GENY_INSTANCE_UUID" ]] && command -v gmsaas >/dev/null 2>&1; then
    log "ADB device missing; re-connecting (instance=$GENY_INSTANCE_UUID)"
    DEVICE="$(TMPDIR="$GMSAAS_TMPDIR" gmsaas instances adbconnect "$GENY_INSTANCE_UUID")"
    export DEVICE
    adb -s "$DEVICE" get-state >/dev/null
    log "Reconnected DEVICE=$DEVICE"
    return 0
  fi
  return 1
}

run_test() {
  local name="$1"
  shift
  local slug log_file rc
  slug="$(_slug "$name")"
  log_file="$OUTDIR/${slug}.log"

  echo >>"$SUMMARY"
  echo "==> $name" | tee -a "$SUMMARY"

  if ! ensure_device; then
    FAIL=1
    echo "FAIL (device missing) log=$log_file" | tee -a "$SUMMARY"
    return 0
  fi

  set +e
  "$@" 2>&1 | tee "$log_file"
  rc="${PIPESTATUS[0]}"
  set -e

  if [[ "$rc" -ne 0 ]]; then
    FAIL=1
    echo "FAIL (exit=$rc) log=$log_file" | tee -a "$SUMMARY"
    adb -s "$DEVICE" exec-out screencap -p >"$OUTDIR/${slug}_fail.png" 2>/dev/null || true
    adb -s "$DEVICE" logcat -d >"$OUTDIR/${slug}_logcat.txt" 2>/dev/null || true
  else
    echo "OK log=$log_file" | tee -a "$SUMMARY"
  fi
}

run_test "1/4 Core open/draw/search/share smoke" \
  ./scripts/geny_smoke.sh

run_test "2/4 Fill & Sign save/reopen smoke" \
  env OUT_PNG="$OUTDIR/fill_sign.png" OUT_PDF="$OUTDIR/fill_sign_saved.pdf" \
  ./scripts/geny_fill_sign_smoke.sh

run_test "3/4 Eraser pending+committed ink smoke" \
  env OUTDIR="$OUTDIR/eraser" ./scripts/geny_eraser_smoke.sh

run_test "4/4 Phase 3 text annotation acceptance" \
  env PDF_LOCAL_MARKUP="$PDF_LOCAL_MARKUP" ./scripts/geny_pdf_text_phase3_acceptance.sh

echo
if [[ "$FAIL" -ne 0 ]]; then
  log "FAIL: one or more smokes failed. See $SUMMARY"
  exit 1
fi

log "OK: significant smoke suite complete. Summary: $SUMMARY"
