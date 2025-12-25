#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/fdroid_lib.sh"

CONFIG_FILE="${1:-${ROOT_DIR}/scripts/fdroid.env}"
odp_fdroid_load_env "${CONFIG_FILE}"

REPO_DIR="${ODP_REPO_DIR}"
S3_REPO="${ODP_S3_BUCKET%/}/repo"

if [[ -z "${ODP_S3_BUCKET:-}" ]]; then
  echo "[fdroid_deploy] ODP_S3_BUCKET must be set (see scripts/fdroid.env.sample)" >&2
  exit 1
fi

if [[ -z "${ODP_CF_DIST_MAIN:-}" ]]; then
  echo "[fdroid_deploy] ODP_CF_DIST_MAIN must be set (see scripts/fdroid.env.sample)" >&2
  exit 1
fi

if [[ ! -d "${REPO_DIR}" ]]; then
  echo "[fdroid_deploy] Repo dir not found: ${REPO_DIR}" >&2
  exit 1
fi

if [[ ! -f "${REPO_DIR}/index-v1.json" ]]; then
  echo "[fdroid_deploy] Missing ${REPO_DIR}/index-v1.json; run scripts/fdroid_build.sh first (or fdroid update)." >&2
  exit 1
fi

echo "[fdroid_deploy] aws s3 sync ${REPO_DIR} -> ${S3_REPO}"
aws s3 sync "${REPO_DIR}" "${S3_REPO}" --delete --only-show-errors

invalidate() {
  local dist_id="${1}"
  if [[ -n "${dist_id}" ]]; then
    echo "[fdroid_deploy] cloudfront invalidation dist=${dist_id} paths=/repo/*"
    aws cloudfront create-invalidation \
      --distribution-id "${dist_id}" \
      --paths "/repo/*" >/dev/null
  fi
}

invalidate "${ODP_CF_DIST_MAIN}"
invalidate "${ODP_CF_DIST_ALIAS:-}"

if command -v curl >/dev/null && command -v jq >/dev/null; then
  PACKAGE_ID="org.opendroidpdf"
  if odp_fdroid_refresh_app_config >/dev/null 2>&1; then
    PACKAGE_ID="${ODP_APP_ID}"
  fi

  echo "[fdroid_deploy] verifying https://fdroid.uh-oh.wtf/repo/index-v1.json"
  curl -fsSL https://fdroid.uh-oh.wtf/repo/index-v1.json \
    | jq -r --arg pkg "${PACKAGE_ID}" '.packages[$pkg] | max_by(.versionCode) | "versionName=\(.versionName) versionCode=\(.versionCode) apk=\(.apkName)"'
else
  echo "[fdroid_deploy] curl/jq not available; skipped server-side verification"
fi

echo "[fdroid_deploy] done"
