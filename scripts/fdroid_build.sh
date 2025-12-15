#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG_FILE="${1:-${ROOT_DIR}/scripts/fdroid.env}"

if [[ -f "${CONFIG_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${CONFIG_FILE}"
fi

BUILD_DIR="${ODP_BUILD_DIR:-/mnt/subtitled/opendroidpdf-android-build}"
ABI_FILTERS="${ODP_ABI:-arm64-v8a,armeabi-v7a}"
REPO_DIR="${ODP_REPO_DIR:-${HOME}/fdroid/repo}"

if [[ -z "${ODP_KEYSTORE:-}" || -z "${ODP_KEY_ALIAS:-}" || -z "${ODP_KEY_PASS:-}" ]]; then
  echo "[fdroid_build] ODP_KEYSTORE / ODP_KEY_ALIAS / ODP_KEY_PASS must be set (see scripts/fdroid.env.sample)" >&2
  exit 1
fi

echo "[fdroid_build] Using buildDir=${BUILD_DIR} abi=${ABI_FILTERS} repo=${REPO_DIR}"

pushd "${ROOT_DIR}/platform/android" >/dev/null

./gradlew clean assembleRelease \
  -Popendroidpdf.buildDir="${BUILD_DIR}" \
  -PopendroidpdfAbi="${ABI_FILTERS}" \
  ${ODP_VERSION_CODE:+-Popendroidpdf.versionCode=${ODP_VERSION_CODE}} \
  ${ODP_VERSION_NAME:+-Popendroidpdf.versionName=${ODP_VERSION_NAME}}

read -r VERSION_CODE VERSION_NAME < <(./gradlew -q printAppVersion | awk -F'=' '/versionCode/ {vc=$2} /versionName/ {vn=$2} END {print vc, vn}')

APK_DIR="${BUILD_DIR}/app/outputs/apk/release"
APK_UNALIGNED=$(find "${APK_DIR}" -maxdepth 1 -type f -name '*release-unsigned.apk' | sort | tail -n1)
if [[ -z "${APK_UNALIGNED}" ]]; then
  echo "[fdroid_build] Could not find release-unsigned APK under ${APK_DIR}" >&2
  exit 1
fi

ZIPALIGNED="${APK_DIR}/app-release-aligned.apk"
SIGNED="${REPO_DIR}/org.opendroidpdf_${VERSION_CODE}.apk"

mkdir -p "${REPO_DIR}"

echo "[fdroid_build] zipalign -> ${ZIPALIGNED}"
zipalign -f -p 4 "${APK_UNALIGNED}" "${ZIPALIGNED}"

echo "[fdroid_build] apksigner -> ${SIGNED} (versionCode=${VERSION_CODE} versionName=${VERSION_NAME})"
apksigner sign \
  --ks "${ODP_KEYSTORE}" \
  --ks-key-alias "${ODP_KEY_ALIAS}" \
  --ks-pass pass:"${ODP_KEY_PASS}" \
  --out "${SIGNED}" \
  "${ZIPALIGNED}"

echo "[fdroid_build] verifying signature"
apksigner verify --print-certs "${SIGNED}"

if command -v fdroid >/dev/null; then
  echo "[fdroid_build] updating local repo metadata"
  (cd "${REPO_DIR}" && fdroid update --create-metadata)
else
  echo "[fdroid_build] fdroid command not found; skipped index regeneration"
fi

popd >/dev/null
echo "[fdroid_build] done"
