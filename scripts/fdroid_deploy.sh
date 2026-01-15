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
  echo "[fdroid_deploy] verifying https://fdroid.uh-oh.wtf/repo/index-v1.json"
  index_json="$(curl -fsSL https://fdroid.uh-oh.wtf/repo/index-v1.json)"

  declare -a packages=()
  if odp_fdroid_refresh_app_config >/dev/null 2>&1; then
    packages+=("${ODP_APP_ID}")
  else
    packages+=("org.opendroidpdf")
  fi
  if odp_fdroid_refresh_officepack_config >/dev/null 2>&1; then
    packages+=("${ODP_OFFICEPACK_ID}")
  else
    packages+=("org.opendroidpdf.officepack")
  fi
  if odp_fdroid_refresh_xfapack_config >/dev/null 2>&1; then
    packages+=("${ODP_XFAPACK_ID}")
  else
    packages+=("org.opendroidpdf.xfapack")
  fi

  for pkg in "${packages[@]}"; do
    echo "${index_json}" \
      | jq -r --arg pkg "${pkg}" '.packages[$pkg] | max_by(.versionCode) | "pkg=\($pkg) versionName=\(.versionName) versionCode=\(.versionCode) apk=\(.apkName)"'

    # Droidify/F-Droid uses suggestedVersionCode for "recommended" updates. If this drifts behind the latest
    # APK in the repo, clients will not surface upgrades correctly.
    suggested="$(echo "${index_json}" | jq -r --arg pkg "${pkg}" '.apps[] | select(.packageName==$pkg) | .suggestedVersionCode // empty')"
    latest="$(echo "${index_json}" | jq -r --arg pkg "${pkg}" '.packages[$pkg] | max_by(.versionCode) | .versionCode')"
    if [[ -n "${suggested}" && "${suggested}" != "${latest}" ]]; then
      echo "FAIL: repo suggestedVersionCode mismatch for ${pkg}: suggested=${suggested} latest=${latest}" >&2
      echo "      Run ./scripts/fdroid_index_refresh.sh (or ./scripts/fdroid_build.sh) to sync metadata and regenerate indexes." >&2
      exit 1
    fi
  done
else
  echo "[fdroid_deploy] curl/jq not available; skipped server-side verification"
fi

echo "[fdroid_deploy] done"
