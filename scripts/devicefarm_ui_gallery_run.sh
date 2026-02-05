#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

REGION="${REGION:-us-west-2}"

PROJECT_NAME="${PROJECT_NAME:-codex-android-smoke}"
PROJECT_ARN="${PROJECT_ARN:-}"

DEVICE_POOL_PHONES_NAME="${DEVICE_POOL_PHONES_NAME:-codex-android-smoke-phones}"
DEVICE_POOL_TABLETS_NAME="${DEVICE_POOL_TABLETS_NAME:-codex-android-smoke-tablets}"

RUN_PHONES="${RUN_PHONES:-1}"
RUN_TABLETS="${RUN_TABLETS:-1}"

RUN_NAME_PREFIX="${RUN_NAME_PREFIX:-opendroidpdf-ui-gallery}"
LOCAL_OUTDIR="${LOCAL_OUTDIR:-${ROOT_DIR}/tmp_devicefarm_ui_gallery_$(date -u +%Y%m%d_%H%M%S)}"

APP_APK="${APP_APK:-}"
TEST_PACKAGE_ZIP="${TEST_PACKAGE_ZIP:-${ROOT_DIR}/build/devicefarm/ui_gallery_test_package.zip}"
TEST_SPEC_PATH="${TEST_SPEC_PATH:-${ROOT_DIR}/devicefarm/ui_gallery/test_spec.yml}"

JOB_TIMEOUT_MINUTES="${JOB_TIMEOUT_MINUTES:-25}"

PUBLISH="${PUBLISH:-0}"
PUBLISH_PREFIX="${PUBLISH_PREFIX:-}"
PUBLISH_TITLE="${PUBLISH_TITLE:-OpenDroidPDF UI Screenshot Gallery (Device Farm)}"

_aws() {
  AWS_DEFAULT_REGION="$REGION" aws "$@"
}

_fail() {
  echo "FAIL: $*" >&2
  exit 1
}

_ensure_project() {
  if [[ -n "$PROJECT_ARN" ]]; then
    return 0
  fi
  PROJECT_ARN="$(_aws devicefarm list-projects --query "projects[?name=='${PROJECT_NAME}'].arn | [0]" --output text)"
  if [[ -z "$PROJECT_ARN" || "$PROJECT_ARN" == "None" ]]; then
    echo "[df] creating project: ${PROJECT_NAME}" >&2
    PROJECT_ARN="$(_aws devicefarm create-project --name "$PROJECT_NAME" --query project.arn --output text)"
  fi
}

_device_pool_arn_by_name() {
  local want="$1"
  local arn
  arn="$(_aws devicefarm list-device-pools --arn "$PROJECT_ARN" --query "devicePools[?name=='${want}'].arn | [0]" --output text)"
  [[ -n "$arn" && "$arn" != "None" ]] || _fail "device pool not found in project: ${want}"
  printf '%s' "$arn"
}

_wait_upload() {
  local upload_arn="$1"
  local status message
  for _ in $(seq 1 120); do
    status="$(_aws devicefarm get-upload --arn "$upload_arn" --query upload.status --output text)"
    case "$status" in
      SUCCEEDED) return 0 ;;
      FAILED)
        message="$(_aws devicefarm get-upload --arn "$upload_arn" --query 'upload.metadata' --output text 2>/dev/null || true)"
        _fail "upload failed: $upload_arn (${message:-no-metadata})"
        ;;
      *)
        sleep 5
        ;;
    esac
  done
  _fail "timed out waiting for upload: $upload_arn"
}

_create_upload() {
  local file_path="$1"
  local upload_type="$2"
  local name="$3"

  [[ -f "$file_path" ]] || _fail "missing upload file: $file_path"

  local arn url
  read -r arn url < <(_aws devicefarm create-upload --project-arn "$PROJECT_ARN" --name "$name" --type "$upload_type" --query 'upload.[arn,url]' --output text)
  [[ -n "$arn" && -n "$url" ]] || _fail "create-upload returned empty arn/url (type=$upload_type name=$name)"

  echo "[df] uploading $name ($upload_type)" >&2
  curl -sS -T "$file_path" -H "content-type: application/octet-stream" "$url" >/dev/null
  _wait_upload "$arn"
  printf '%s' "$arn"
}

_wait_run() {
  local run_arn="$1"
  local status result
  while true; do
    status="$(_aws devicefarm get-run --arn "$run_arn" --query run.status --output text)"
    result="$(_aws devicefarm get-run --arn "$run_arn" --query run.result --output text)"
    echo "[df] run status: $status ($result) - $run_arn" >&2
    case "$status" in
      COMPLETED) return 0 ;;
      STOPPING|STOPPED) return 0 ;;
      *)
        sleep 20
        ;;
    esac
  done
}

_sanitize_name() {
  local s="$1"
  s="${s//\\/__}"
  s="${s//\//__}"
  s="${s// /_}"
  s="${s//[^A-Za-z0-9_.-]/_}"
  printf '%s' "$s"
}

_download_artifacts() {
  local job_arn="$1"
  local out_dir="$2"
  mkdir -p "$out_dir"

  local type
  for type in FILE SCREENSHOT LOG; do
    local rows
    rows="$(_aws devicefarm list-artifacts --arn "$job_arn" --type "$type" --query 'artifacts[].[name,extension,url]' --output text || true)"
    [[ -n "$rows" ]] || continue

    while IFS=$'\t' read -r name ext url; do
      [[ -n "${url:-}" ]] || continue
      case "${ext:-}" in
        png|mp4|txt|log|xml|json|html|jpg|jpeg|zip|sh|yml|yaml)
          ;;
        *)
          continue
          ;;
      esac
      local safe
      safe="$(_sanitize_name "$name")"
      if [[ -n "${ext:-}" && "$ext" != "None" && "$safe" != *".${ext}" ]]; then
        safe="${safe}.${ext}"
      fi
      curl -sS -L "$url" -o "${out_dir}/${safe}" || true
    done <<<"$rows"
  done
}

_extract_customer_artifacts_best_effort() {
  local out_dir="$1"
  local zip_path="${out_dir}/Customer_Artifacts.zip"
  [[ -f "$zip_path" ]] || return 0

  local unpack_dir="${out_dir}/Customer_Artifacts"
  mkdir -p "$unpack_dir"
  unzip -q -o "$zip_path" -d "$unpack_dir" || return 0

  local host_dir="${unpack_dir}/Host_Machine_Files/\$DEVICEFARM_LOG_DIR"
  if compgen -G "${host_dir}/tmp_geny_ui_gallery_*.png" >/dev/null; then
    local shots_dir="${out_dir}/screenshots"
    mkdir -p "$shots_dir"
    cp -f "${host_dir}"/tmp_geny_ui_gallery_* "$shots_dir"/ || true
  fi
  return 0
}

_download_run() {
  local run_arn="$1"
  local run_dir="$2"
  mkdir -p "$run_dir"

  local jobs
  jobs="$(_aws devicefarm list-jobs --arn "$run_arn" --query 'jobs[].[arn,device.name,device.os]' --output text)"
  if [[ -z "$jobs" ]]; then
    _fail "no jobs found for run: $run_arn"
  fi

  while IFS=$'\t' read -r job_arn device_name device_os; do
    local slug job_dir
    slug="$(_sanitize_name "${device_name}_android${device_os}")"
    job_dir="${run_dir}/${slug}"
    echo "[df] downloading artifacts for: ${device_name} (Android ${device_os})" >&2
    _download_artifacts "$job_arn" "$job_dir"
    _extract_customer_artifacts_best_effort "$job_dir"
  done <<<"$jobs"
}

mkdir -p "$LOCAL_OUTDIR"

echo "[df] region: $REGION" >&2
echo "[df] local out: $LOCAL_OUTDIR" >&2

if [[ -z "$APP_APK" ]]; then
  echo "[df] building debug APK" >&2
  (cd "$ROOT_DIR/platform/android" && ./gradlew --no-daemon -q assembleDebug)
  APP_APK="/mnt/subtitled/opendroidpdf-android-build/outputs/apk/debug/OpenDroidPDF-debug.apk"
fi
[[ -f "$APP_APK" ]] || _fail "APK not found: $APP_APK"

echo "[df] packaging test package" >&2
"$ROOT_DIR/scripts/devicefarm_ui_gallery_package.sh" "$TEST_PACKAGE_ZIP"
[[ -f "$TEST_PACKAGE_ZIP" ]] || _fail "test package zip missing: $TEST_PACKAGE_ZIP"
[[ -f "$TEST_SPEC_PATH" ]] || _fail "test spec missing: $TEST_SPEC_PATH"

_ensure_project
echo "[df] project: $PROJECT_ARN" >&2

phones_pool_arn=""
tablets_pool_arn=""
if [[ "$RUN_PHONES" == "1" ]]; then
  phones_pool_arn="$(_device_pool_arn_by_name "$DEVICE_POOL_PHONES_NAME")"
  echo "[df] phones pool: $phones_pool_arn" >&2
fi
if [[ "$RUN_TABLETS" == "1" ]]; then
  tablets_pool_arn="$(_device_pool_arn_by_name "$DEVICE_POOL_TABLETS_NAME")"
  echo "[df] tablets pool: $tablets_pool_arn" >&2
fi

ts="$(date -u +%Y%m%d_%H%M%S)"

app_arn="$(_create_upload "$APP_APK" ANDROID_APP "OpenDroidPDF-${ts}.apk")"
test_pkg_arn="$(_create_upload "$TEST_PACKAGE_ZIP" APPIUM_NODE_TEST_PACKAGE "odp-ui-gallery-${ts}.zip")"
test_spec_arn="$(_create_upload "$TEST_SPEC_PATH" APPIUM_NODE_TEST_SPEC "odp-ui-gallery-${ts}.yml")"

echo "[df] app arn: $app_arn" >&2
echo "[df] test pkg arn: $test_pkg_arn" >&2
echo "[df] test spec arn: $test_spec_arn" >&2

runs=()

if [[ -n "$phones_pool_arn" ]]; then
  run_name="${RUN_NAME_PREFIX}-${ts}-phone"
  echo "[df] scheduling run: $run_name" >&2
  run_arn="$(_aws devicefarm schedule-run \
    --project-arn "$PROJECT_ARN" \
    --app-arn "$app_arn" \
    --device-pool-arn "$phones_pool_arn" \
    --name "$run_name" \
    --test "type=APPIUM_NODE,testPackageArn=$test_pkg_arn,testSpecArn=$test_spec_arn" \
    --execution-configuration "jobTimeoutMinutes=${JOB_TIMEOUT_MINUTES},videoCapture=true,skipAppResign=true" \
    --configuration "billingMethod=METERED" \
    --query run.arn --output text)"
  runs+=("${run_name}	${run_arn}")
fi

if [[ -n "$tablets_pool_arn" ]]; then
  run_name="${RUN_NAME_PREFIX}-${ts}-tablet"
  echo "[df] scheduling run: $run_name" >&2
  run_arn="$(_aws devicefarm schedule-run \
    --project-arn "$PROJECT_ARN" \
    --app-arn "$app_arn" \
    --device-pool-arn "$tablets_pool_arn" \
    --name "$run_name" \
    --test "type=APPIUM_NODE,testPackageArn=$test_pkg_arn,testSpecArn=$test_spec_arn" \
    --execution-configuration "jobTimeoutMinutes=${JOB_TIMEOUT_MINUTES},videoCapture=true,skipAppResign=true" \
    --configuration "billingMethod=METERED" \
    --query run.arn --output text)"
  runs+=("${run_name}	${run_arn}")
fi

if [[ "${#runs[@]}" -eq 0 ]]; then
  _fail "no runs scheduled (RUN_PHONES=$RUN_PHONES RUN_TABLETS=$RUN_TABLETS)"
fi

echo "[df] waiting for runs to complete..." >&2
for line in "${runs[@]}"; do
  IFS=$'\t' read -r run_name run_arn <<<"$line"
  _wait_run "$run_arn"
  run_dir="${LOCAL_OUTDIR}/${run_name}"
  _download_run "$run_arn" "$run_dir"
done

if [[ "$PUBLISH" == "1" ]]; then
  echo "[df] publishing combined report via wtf-upload..." >&2
  shopt -s nullglob
  gallery_files=("$LOCAL_OUTDIR"/*/*/screenshots/tmp_geny_ui_gallery_*)
  shopt -u nullglob
  if [[ "${#gallery_files[@]}" -eq 0 ]]; then
    echo "[df] WARN: no gallery artifacts found under $LOCAL_OUTDIR" >&2
  else
    report_url="$("$ROOT_DIR/scripts/qa_report_upload.sh" --title "$PUBLISH_TITLE" --prefix "$PUBLISH_PREFIX" "${gallery_files[@]}")"
    echo "$report_url"
  fi
fi

echo "OK: downloaded artifacts to $LOCAL_OUTDIR" >&2
printf '%s\n' "$LOCAL_OUTDIR"
