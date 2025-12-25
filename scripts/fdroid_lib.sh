#!/usr/bin/env bash

ODP_ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ODP_ANDROID_DIR="${ODP_ROOT_DIR}/platform/android"

ODP_APP_ID=""
ODP_APP_VERSION_CODE=""
ODP_APP_VERSION_NAME=""
ODP_APP_BUILD_DIR=""
ODP_APP_ABI_FILTERS=""

odp_fdroid_load_env() {
  local config_file="${1:-${ODP_ROOT_DIR}/scripts/fdroid.env}"

  if [[ -f "${config_file}" ]]; then
    # shellcheck disable=SC1090
    source "${config_file}"
  fi

  : "${ODP_REPO_DIR:=${HOME}/fdroid/repo}"
}

odp_fdroid_gradle_prop_args() {
  local -n out_args="$1"
  out_args=()

  if [[ -n "${ODP_BUILD_DIR:-}" ]]; then
    out_args+=("-Popendroidpdf.buildDir=${ODP_BUILD_DIR}")
  fi

  if [[ -n "${ODP_ABI:-}" ]]; then
    out_args+=("-Popendroidpdf.abi=${ODP_ABI}")
  fi

  if [[ -n "${ODP_VERSION_CODE:-}" ]]; then
    out_args+=("-Popendroidpdf.versionCode=${ODP_VERSION_CODE}")
  fi

  if [[ -n "${ODP_VERSION_NAME:-}" ]]; then
    out_args+=("-Popendroidpdf.versionName=${ODP_VERSION_NAME}")
  fi
}

odp_fdroid_refresh_app_config() {
  local -a gradle_props=()
  odp_fdroid_gradle_prop_args gradle_props

  local output
  output="$(cd "${ODP_ANDROID_DIR}" && ./gradlew -q printAppConfig "${gradle_props[@]}")"

  ODP_APP_ID=""
  ODP_APP_VERSION_CODE=""
  ODP_APP_VERSION_NAME=""
  ODP_APP_BUILD_DIR=""
  ODP_APP_ABI_FILTERS=""

  while IFS='=' read -r key value; do
    case "${key}" in
      applicationId) ODP_APP_ID="${value}" ;;
      versionCode) ODP_APP_VERSION_CODE="${value}" ;;
      versionName) ODP_APP_VERSION_NAME="${value}" ;;
      buildDir) ODP_APP_BUILD_DIR="${value}" ;;
      abiFilters) ODP_APP_ABI_FILTERS="${value}" ;;
    esac
  done <<<"${output}"

  if [[ -z "${ODP_APP_ID}" || -z "${ODP_APP_VERSION_CODE}" || -z "${ODP_APP_VERSION_NAME}" || -z "${ODP_APP_BUILD_DIR}" ]]; then
    echo "[fdroid_lib] Failed to parse printAppConfig output:" >&2
    echo "${output}" >&2
    return 1
  fi
}
