#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${ROOT_DIR}/scripts/fdroid_lib.sh"

CONFIG_FILE="${1:-${ROOT_DIR}/scripts/fdroid.env}"
odp_fdroid_load_env "${CONFIG_FILE}"

# Resource tuning (defaults prioritize keeping the machine responsive).
# Override via scripts/fdroid.env or by exporting these vars before running.
: "${ODP_FDROID_CLEAN:=0}"
: "${ODP_FDROID_SKIP_INDEX:=0}"
: "${ODP_FDROID_NDK_JOBS:=2}"
: "${ODP_FDROID_GRADLE_MAX_WORKERS:=2}"
: "${ODP_FDROID_GRADLE_ARGS:=--no-daemon --max-workers=${ODP_FDROID_GRADLE_MAX_WORKERS} --no-parallel}"
: "${ODP_FDROID_GRADLE_XMX:=4g}"
: "${ODP_FDROID_GRADLE_METASPACE:=512m}"
: "${ODP_FDROID_GRADLE_JAVA_OPTS:=-Xmx${ODP_FDROID_GRADLE_XMX} -XX:MaxMetaspaceSize=${ODP_FDROID_GRADLE_METASPACE} -Dfile.encoding=UTF-8}"
: "${ODP_FDROID_BUILD_NICE:=10}"
: "${ODP_FDROID_BUILD_IONICE_CLASS:=2}"
: "${ODP_FDROID_BUILD_IONICE_LEVEL:=7}"
: "${ODP_FDROID_STOP_DAEMONS:=1}"

run_lowprio() {
  if command -v nice >/dev/null 2>&1; then
    if command -v ionice >/dev/null 2>&1; then
      ionice -c "${ODP_FDROID_BUILD_IONICE_CLASS}" -n "${ODP_FDROID_BUILD_IONICE_LEVEL}" \
        nice -n "${ODP_FDROID_BUILD_NICE}" "$@"
      return $?
    fi
    nice -n "${ODP_FDROID_BUILD_NICE}" "$@"
    return $?
  fi
  "$@"
}

# Ensure gradle invocations (including printAppConfig) inherit these defaults.
export ODP_FDROID_GRADLE_ARGS
export ODP_FDROID_GRADLE_JAVA_OPTS

if [[ "${ODP_FDROID_STOP_DAEMONS}" == "1" ]]; then
  echo "[fdroid_build] stopping existing Gradle daemons"
  (cd "${ROOT_DIR}/platform/android" && ./gradlew --stop >/dev/null 2>&1 || true)
fi

odp_fdroid_refresh_app_config
odp_fdroid_refresh_officepack_config
odp_fdroid_refresh_xfapack_config

BUILD_DIR="${ODP_APP_BUILD_DIR}"
ABI_FILTERS="${ODP_APP_ABI_FILTERS}"
REPO_DIR="${ODP_REPO_DIR}"

if [[ -z "${ODP_KEYSTORE:-}" || -z "${ODP_KEY_ALIAS:-}" || -z "${ODP_KEY_PASS:-}" ]]; then
  echo "[fdroid_build] ODP_KEYSTORE / ODP_KEY_ALIAS / ODP_KEY_PASS must be set (see scripts/fdroid.env.sample)" >&2
  exit 1
fi

echo "[fdroid_build] Using appId=${ODP_APP_ID} officePackId=${ODP_OFFICEPACK_ID} xfaPackId=${ODP_XFAPACK_ID} buildDir=${BUILD_DIR} abi=${ABI_FILTERS} repo=${REPO_DIR}"

pushd "${ROOT_DIR}/platform/android" >/dev/null

declare -a gradle_props=()
odp_fdroid_gradle_prop_args gradle_props

declare -a gradle_args=()
read -r -a gradle_args <<<"${ODP_FDROID_GRADLE_ARGS}"

declare -a gradle_tasks=()
if [[ "${ODP_FDROID_CLEAN}" == "1" ]]; then
  gradle_tasks+=("clean")
fi
gradle_tasks+=("assembleRelease" ":officepack:assembleRelease" ":xfapack:assembleRelease")

echo "[fdroid_build] gradle opts: ${ODP_FDROID_GRADLE_ARGS} (JAVA_OPTS: ${ODP_FDROID_GRADLE_JAVA_OPTS})"
run_lowprio env JAVA_OPTS="${ODP_FDROID_GRADLE_JAVA_OPTS}" ./gradlew "${gradle_args[@]}" "${gradle_tasks[@]}" "${gradle_props[@]}"

APP_VERSION_CODE="${ODP_APP_VERSION_CODE}"
APP_VERSION_NAME="${ODP_APP_VERSION_NAME}"
OFFICEPACK_VERSION_CODE="${ODP_OFFICEPACK_VERSION_CODE}"
OFFICEPACK_VERSION_NAME="${ODP_OFFICEPACK_VERSION_NAME}"
XFAPACK_VERSION_CODE="${ODP_XFAPACK_VERSION_CODE}"
XFAPACK_VERSION_NAME="${ODP_XFAPACK_VERSION_NAME}"

find_unsigned_apk() {
  local build_dir="${1}"
  local -n out_apk="${2}"
  local -n out_dir="${3}"

  out_apk=""
  out_dir=""

  local found=""
  local candidate=""

  local -a candidates=(
    "${build_dir}/app/outputs/apk/release"   # multi-module legacy
    "${build_dir}/outputs/apk/release"       # modern/default
  )

  for candidate in "${candidates[@]}"; do
    if [[ -d "${candidate}" ]]; then
      found=$(find "${candidate}" -maxdepth 1 -type f -name '*release-unsigned.apk' 2>/dev/null | sort | tail -n1 || true)
      if [[ -n "${found}" ]]; then
        out_dir="${candidate}"
        out_apk="${found}"
        return 0
      fi
    fi
  done

  found=$(find "${build_dir}" -maxdepth 6 -type f -name '*release-unsigned.apk' 2>/dev/null | sort | tail -n1 || true)
  if [[ -n "${found}" ]]; then
    out_dir="$(dirname "${found}")"
    out_apk="${found}"
    return 0
  fi
  return 1
}

APP_APK_UNALIGNED=""
APP_APK_DIR=""

if ! find_unsigned_apk "${BUILD_DIR}" APP_APK_UNALIGNED APP_APK_DIR; then
  echo "[fdroid_build] Could not find app release-unsigned APK under ${BUILD_DIR}" >&2
  exit 1
fi

OFFICEPACK_APK_UNALIGNED=""
OFFICEPACK_APK_DIR=""

if ! find_unsigned_apk "${ODP_OFFICEPACK_BUILD_DIR}" OFFICEPACK_APK_UNALIGNED OFFICEPACK_APK_DIR; then
  echo "[fdroid_build] Could not find officepack release-unsigned APK under ${ODP_OFFICEPACK_BUILD_DIR}" >&2
  exit 1
fi

XFAPACK_APK_UNALIGNED=""
XFAPACK_APK_DIR=""

if ! find_unsigned_apk "${ODP_XFAPACK_BUILD_DIR}" XFAPACK_APK_UNALIGNED XFAPACK_APK_DIR; then
  echo "[fdroid_build] Could not find xfapack release-unsigned APK under ${ODP_XFAPACK_BUILD_DIR}" >&2
  exit 1
fi

APP_ZIPALIGNED="${APP_APK_DIR}/OpenDroidPDF-release-aligned.apk"
APP_SIGNED="${REPO_DIR}/${ODP_APP_ID}_${APP_VERSION_CODE}.apk"

OFFICEPACK_ZIPALIGNED="${OFFICEPACK_APK_DIR}/OpenDroidPDF-OfficePack-release-aligned.apk"
OFFICEPACK_SIGNED="${REPO_DIR}/${ODP_OFFICEPACK_ID}_${OFFICEPACK_VERSION_CODE}.apk"

XFAPACK_ZIPALIGNED="${XFAPACK_APK_DIR}/OpenDroidPDF-XfaPack-release-aligned.apk"
XFAPACK_SIGNED="${REPO_DIR}/${ODP_XFAPACK_ID}_${XFAPACK_VERSION_CODE}.apk"

mkdir -p "${REPO_DIR}"

echo "[fdroid_build] zipalign app -> ${APP_ZIPALIGNED}"
zipalign -f -p 4 "${APP_APK_UNALIGNED}" "${APP_ZIPALIGNED}"

echo "[fdroid_build] apksigner app -> ${APP_SIGNED} (versionCode=${APP_VERSION_CODE} versionName=${APP_VERSION_NAME})"
apksigner sign \
  --ks "${ODP_KEYSTORE}" \
  --ks-key-alias "${ODP_KEY_ALIAS}" \
  --ks-pass pass:"${ODP_KEY_PASS}" \
  --key-pass pass:"${ODP_KEY_KEY_PASS:-${ODP_KEY_PASS}}" \
  --out "${APP_SIGNED}" \
  "${APP_ZIPALIGNED}"

echo "[fdroid_build] zipalign officepack -> ${OFFICEPACK_ZIPALIGNED}"
zipalign -f -p 4 "${OFFICEPACK_APK_UNALIGNED}" "${OFFICEPACK_ZIPALIGNED}"

echo "[fdroid_build] apksigner officepack -> ${OFFICEPACK_SIGNED} (versionCode=${OFFICEPACK_VERSION_CODE} versionName=${OFFICEPACK_VERSION_NAME})"
apksigner sign \
  --ks "${ODP_KEYSTORE}" \
  --ks-key-alias "${ODP_KEY_ALIAS}" \
  --ks-pass pass:"${ODP_KEY_PASS}" \
  --key-pass pass:"${ODP_KEY_KEY_PASS:-${ODP_KEY_PASS}}" \
  --out "${OFFICEPACK_SIGNED}" \
  "${OFFICEPACK_ZIPALIGNED}"

echo "[fdroid_build] zipalign xfapack -> ${XFAPACK_ZIPALIGNED}"
zipalign -f -p 4 "${XFAPACK_APK_UNALIGNED}" "${XFAPACK_ZIPALIGNED}"

echo "[fdroid_build] apksigner xfapack -> ${XFAPACK_SIGNED} (versionCode=${XFAPACK_VERSION_CODE} versionName=${XFAPACK_VERSION_NAME})"
apksigner sign \
  --ks "${ODP_KEYSTORE}" \
  --ks-key-alias "${ODP_KEY_ALIAS}" \
  --ks-pass pass:"${ODP_KEY_PASS}" \
  --key-pass pass:"${ODP_KEY_KEY_PASS:-${ODP_KEY_PASS}}" \
  --out "${XFAPACK_SIGNED}" \
  "${XFAPACK_ZIPALIGNED}"

echo "[fdroid_build] verifying signatures"
apksigner verify --print-certs "${APP_SIGNED}"
apksigner verify --print-certs "${OFFICEPACK_SIGNED}"
apksigner verify --print-certs "${XFAPACK_SIGNED}"

if command -v fdroid >/dev/null; then
  echo "[fdroid_build] updating local repo metadata"
  FDROID_ROOT="${FDROIDCONFDIR:-${HOME}/fdroid}"
  if [[ -f "${FDROID_ROOT}/config.yml" ]]; then
    odp_fdroid_sync_metadata "${FDROID_ROOT}"
    # config.yml typically pulls these from env; derive them from the signing settings if needed.
    export FDROID_KEYSTORE_PASS="${FDROID_KEYSTORE_PASS:-${ODP_KEY_PASS}}"
    export FDROID_KEY_PASS="${FDROID_KEY_PASS:-${ODP_KEY_KEY_PASS:-${ODP_KEY_PASS}}}"
    export FDROID_KEY_STORE_PASS="${FDROID_KEY_STORE_PASS:-${FDROID_KEYSTORE_PASS}}"
    if [[ "${ODP_FDROID_SKIP_INDEX}" == "1" ]]; then
      echo "[fdroid_build] ODP_FDROID_SKIP_INDEX=1; skipping fdroid update (index regeneration)"
    else
      (cd "${FDROID_ROOT}" && run_lowprio fdroid update)
    fi
  else
    echo "[fdroid_build] ${FDROID_ROOT}/config.yml not found; skipped index regeneration"
  fi
else
  echo "[fdroid_build] fdroid command not found; skipped index regeneration"
fi

popd >/dev/null
echo "[fdroid_build] done"
