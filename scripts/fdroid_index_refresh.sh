#!/usr/bin/env bash
set -euo pipefail

# Refresh the hosted F-Droid repo indexes without rebuilding APKs.
# This is useful when metadata drift causes Droidify/F-Droid to stop recommending upgrades.
#
# Usage:
#   ./scripts/fdroid_index_refresh.sh
#   ./scripts/fdroid_index_refresh.sh /path/to/scripts/fdroid.env

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/fdroid_lib.sh"

CONFIG_FILE="${1:-${ROOT_DIR}/scripts/fdroid.env}"
odp_fdroid_load_env "${CONFIG_FILE}"

FDROID_ROOT="${FDROIDCONFDIR:-${HOME}/fdroid}"

if ! command -v fdroid >/dev/null; then
  echo "[fdroid_index_refresh] fdroid command not found; install fdroidserver first" >&2
  exit 1
fi

if [[ ! -f "${FDROID_ROOT}/config.yml" ]]; then
  echo "[fdroid_index_refresh] ${FDROID_ROOT}/config.yml not found; set FDROIDCONFDIR or create config.yml" >&2
  exit 1
fi

odp_fdroid_sync_metadata "${FDROID_ROOT}"

echo "[fdroid_index_refresh] regenerating indexes under ${ODP_REPO_DIR}"
(cd "${FDROID_ROOT}" && fdroid update)

echo "[fdroid_index_refresh] done"

