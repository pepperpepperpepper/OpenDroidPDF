#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/fdroid_lib.sh"

CONFIG_FILE="${1:-${ROOT_DIR}/scripts/fdroid.env}"
odp_fdroid_load_env "${CONFIG_FILE}"
odp_fdroid_refresh_app_config

BUILD_DIR="${ODP_APP_BUILD_DIR}"
ABI_FILTERS="${ODP_APP_ABI_FILTERS}"
REPO_DIR="${ODP_REPO_DIR}"

if [[ -z "${ODP_KEYSTORE:-}" || -z "${ODP_KEY_ALIAS:-}" || -z "${ODP_KEY_PASS:-}" ]]; then
  echo "[fdroid_build] ODP_KEYSTORE / ODP_KEY_ALIAS / ODP_KEY_PASS must be set (see scripts/fdroid.env.sample)" >&2
  exit 1
fi

echo "[fdroid_build] Using appId=${ODP_APP_ID} buildDir=${BUILD_DIR} abi=${ABI_FILTERS} repo=${REPO_DIR}"

pushd "${ROOT_DIR}/platform/android" >/dev/null

declare -a gradle_props=()
odp_fdroid_gradle_prop_args gradle_props

./gradlew clean assembleRelease "${gradle_props[@]}"

VERSION_CODE="${ODP_APP_VERSION_CODE}"
VERSION_NAME="${ODP_APP_VERSION_NAME}"

APK_UNALIGNED=""
APK_DIR=""

APK_DIR_CANDIDATES=(
  "${BUILD_DIR}/app/outputs/apk/release"   # multi-module (app module)
  "${BUILD_DIR}/outputs/apk/release"       # single-module (legacy layout)
)

for candidate in "${APK_DIR_CANDIDATES[@]}"; do
  if [[ -d "${candidate}" ]]; then
    found=$(find "${candidate}" -maxdepth 1 -type f -name '*release-unsigned.apk' 2>/dev/null | sort | tail -n1 || true)
    if [[ -n "${found}" ]]; then
      APK_DIR="${candidate}"
      APK_UNALIGNED="${found}"
      break
    fi
  fi
done

if [[ -z "${APK_UNALIGNED}" ]]; then
  # Last resort: search the build dir.
  APK_UNALIGNED=$(find "${BUILD_DIR}" -maxdepth 6 -type f -name '*release-unsigned.apk' 2>/dev/null | sort | tail -n1 || true)
  APK_DIR=$(dirname "${APK_UNALIGNED}")
fi

if [[ -z "${APK_UNALIGNED}" ]]; then
  echo "[fdroid_build] Could not find release-unsigned APK under ${BUILD_DIR}" >&2
  exit 1
fi

ZIPALIGNED="${APK_DIR}/OpenDroidPDF-release-aligned.apk"
SIGNED="${REPO_DIR}/${ODP_APP_ID}_${VERSION_CODE}.apk"

mkdir -p "${REPO_DIR}"

echo "[fdroid_build] zipalign -> ${ZIPALIGNED}"
zipalign -f -p 4 "${APK_UNALIGNED}" "${ZIPALIGNED}"

echo "[fdroid_build] apksigner -> ${SIGNED} (versionCode=${VERSION_CODE} versionName=${VERSION_NAME})"
apksigner sign \
  --ks "${ODP_KEYSTORE}" \
  --ks-key-alias "${ODP_KEY_ALIAS}" \
  --ks-pass pass:"${ODP_KEY_PASS}" \
  --key-pass pass:"${ODP_KEY_KEY_PASS:-${ODP_KEY_PASS}}" \
  --out "${SIGNED}" \
  "${ZIPALIGNED}"

echo "[fdroid_build] verifying signature"
apksigner verify --print-certs "${SIGNED}"

if command -v fdroid >/dev/null; then
  echo "[fdroid_build] updating local repo metadata"
  FDROID_ROOT="${FDROIDCONFDIR:-${HOME}/fdroid}"
  if [[ -f "${FDROID_ROOT}/config.yml" ]]; then
    # config.yml typically pulls these from env; derive them from the signing settings if needed.
    export FDROID_KEYSTORE_PASS="${FDROID_KEYSTORE_PASS:-${ODP_KEY_PASS}}"
    export FDROID_KEY_PASS="${FDROID_KEY_PASS:-${ODP_KEY_KEY_PASS:-${ODP_KEY_PASS}}}"
    export FDROID_KEY_STORE_PASS="${FDROID_KEY_STORE_PASS:-${FDROID_KEYSTORE_PASS}}"
    (cd "${FDROID_ROOT}" && fdroid update --create-metadata)
  else
    echo "[fdroid_build] ${FDROID_ROOT}/config.yml not found; skipped index regeneration"
  fi
else
  echo "[fdroid_build] fdroid command not found; skipped index regeneration"
fi

popd >/dev/null
echo "[fdroid_build] done"
