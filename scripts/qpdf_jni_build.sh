#!/usr/bin/env bash
# Build a tiny JNI shared lib that wraps qpdf static archives for Android ABIs.
# Outputs libqpdfjni.so to platform/android/jni/qpdf/prebuilt/<abi>/lib/.

set -euo pipefail

ABI_LIST="${ABI_LIST:-arm64-v8a armeabi-v7a}"
API_LEVEL="${API_LEVEL:-24}"
NDK_ROOT="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-${NDK_HOME:-}}}"

if [[ -z "${NDK_ROOT}" ]]; then
  echo "ANDROID_NDK_ROOT / ANDROID_NDK_HOME is required" >&2
  exit 2
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
SRC_ROOT="${REPO_ROOT}/platform/android/jni/qpdf"
OUT_ROOT="${REPO_ROOT}/platform/android/jni/qpdf/prebuilt"
BUILD_ROOT="${REPO_ROOT}/thirdparty_build/qpdf-jni-android"

rm -rf "${BUILD_ROOT}"
mkdir -p "${BUILD_ROOT}"

for ABI in ${ABI_LIST}; do
  BUILD_DIR="${BUILD_ROOT}/${ABI}"
  mkdir -p "${BUILD_DIR}"
  pushd "${BUILD_DIR}" >/dev/null

  LIB_ROOT="${OUT_ROOT}/${ABI}/lib"
  INC_ROOT="${OUT_ROOT}/${ABI}/include"
  JPEG_ROOT="${REPO_ROOT}/platform/android/jni/qpdf/deps/libjpeg/${ABI}"

  cmake \
    -G Ninja \
    "-DCMAKE_TOOLCHAIN_FILE=${NDK_ROOT}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="${ABI}" \
    -DANDROID_PLATFORM="android-${API_LEVEL}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_FLAGS_RELEASE="-Oz -ffunction-sections -fdata-sections -fvisibility=hidden" \
    -DCMAKE_CXX_FLAGS_RELEASE="-Oz -ffunction-sections -fdata-sections -fvisibility=hidden -fexceptions -frtti" \
    -DCMAKE_EXE_LINKER_FLAGS_RELEASE="-Wl,--gc-sections" \
    -DQPDF_SHARED_LIB="${LIB_ROOT}/libqpdf.so" \
    -DJPEG_STATIC_LIB="${JPEG_ROOT}/lib/libjpeg.a" \
    -DQPDF_INC_ROOT="${INC_ROOT}" \
    -DJPEG_INC_ROOT="${JPEG_ROOT}/include" \
    "${SRC_ROOT}"

  ninja

  mkdir -p "${LIB_ROOT}"
  cp libqpdfjni.so "${LIB_ROOT}/"
  popd >/dev/null
done

echo "Done. JNI libs at ${OUT_ROOT}/{abi}/lib/libqpdfjni.so"
