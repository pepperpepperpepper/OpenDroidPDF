#!/usr/bin/env bash
# Helper to cross-compile qpdf for Android (static lib) using the NDK toolchain.
# Outputs libqpdf.a and headers under platform/android/jni/qpdf/prebuilt/<abi>/.

set -euo pipefail

QPDF_VERSION="${QPDF_VERSION:-12.2.0}"
QPDF_URL="${QPDF_URL:-https://github.com/qpdf/qpdf/releases/download/v${QPDF_VERSION}/qpdf-${QPDF_VERSION}.tar.gz}"
ABI_LIST="${ABI_LIST:-arm64-v8a armeabi-v7a}"
API_LEVEL="${API_LEVEL:-24}"
NDK_ROOT="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-${NDK_HOME:-}}}"
ENABLE_LTO="${ENABLE_LTO:-0}" # set to 1 to enable thin LTO; off by default to keep strip-friendly objects

if [[ -z "${NDK_ROOT}" ]]; then
  echo "ANDROID_NDK_ROOT / ANDROID_NDK_HOME is required" >&2
  exit 2
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
WORK_DIR="${WORK_DIR:-${REPO_ROOT}/thirdparty_build/qpdf-${QPDF_VERSION}-android}"
SRC_DIR="${WORK_DIR}/src"
BUILD_DIR="${WORK_DIR}/build"
OUT_DIR="${REPO_ROOT}/platform/android/jni/qpdf/prebuilt"
JPEG_ROOT_BASE="${JPEG_ROOT_BASE:-${REPO_ROOT}/platform/android/jni/qpdf/deps/libjpeg}"

mkdir -p "${SRC_DIR}" "${BUILD_DIR}" "${OUT_DIR}"

if [[ ! -d "${SRC_DIR}/qpdf-${QPDF_VERSION}" ]]; then
  echo "Fetching qpdf ${QPDF_VERSION} …"
  curl -L "${QPDF_URL}" | tar -xz -C "${SRC_DIR}"
fi

for ABI in ${ABI_LIST}; do
  ABI_BUILD="${BUILD_DIR}/${ABI}"
  mkdir -p "${ABI_BUILD}"
  pushd "${ABI_BUILD}" >/dev/null

  JPEG_ROOT="${JPEG_ROOT_BASE}/${ABI}"
  if [[ ! -d "${JPEG_ROOT}" ]]; then
    echo "Missing libjpeg for ${ABI} at ${JPEG_ROOT}; run scripts/libjpeg_android_build.sh" >&2
    exit 3
  fi

  export PKG_CONFIG_LIBDIR="${JPEG_ROOT}/lib/pkgconfig"

  cmake \
    -G Ninja \
    "-DCMAKE_TOOLCHAIN_FILE=${NDK_ROOT}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="${ABI}" \
    -DANDROID_PLATFORM="android-${API_LEVEL}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DCMAKE_INTERPROCEDURAL_OPTIMIZATION:BOOL=$([[ "${ENABLE_LTO}" == "1" ]] && echo ON || echo OFF) \
    -DCMAKE_C_FLAGS_RELEASE="-Oz -ffunction-sections -fdata-sections -fvisibility=hidden -fPIC" \
    -DCMAKE_CXX_FLAGS_RELEASE="-Oz -ffunction-sections -fdata-sections -fvisibility=hidden -fPIC" \
    -DCMAKE_EXE_LINKER_FLAGS_RELEASE="-Wl,--gc-sections" \
    -DUSE_IMPLICIT_CRYPTO=OFF \
    -DREQUIRE_CRYPTO_NATIVE=ON \
    -DREQUIRE_CRYPTO_GNUTLS=OFF \
    -DREQUIRE_CRYPTO_OPENSSL=OFF \
    -DBUILD_STATIC_LIBS=ON \
    -DBUILD_SHARED_LIBS=ON \
    -DQPDF_BUILD_EXAMPLES=OFF \
    -DQPDF_BUILD_DOC=OFF \
    -DQPDF_BUILD_TESTS=OFF \
    -DQPDF_BUILD_PROGRAMS=OFF \
    -DQPDF_BUILD_MANUAL=OFF \
    -DQPDF_USE_SYSTEM_ZLIB=ON \
    -DQPDF_QTC_DISABLED=ON \
    -DQPDF_QTC_USES_JPEG=OFF \
    ${QPDF_CMAKE_ARGS:-} \
    "${SRC_DIR}/qpdf-${QPDF_VERSION}"

  ninja libqpdf.a libqpdf.so

  ABI_OUT="${OUT_DIR}/${ABI}"
  mkdir -p "${ABI_OUT}/lib" "${ABI_OUT}/include"
  cp libqpdf/libqpdf.a "${ABI_OUT}/lib/" 2>/dev/null || cp libqpdf.a "${ABI_OUT}/lib/"
  if [[ -f libqpdf/libqpdf.so ]]; then
    cp libqpdf/libqpdf.so "${ABI_OUT}/lib/"
  fi

  STRIP_BIN="${NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
  if [[ -x "${STRIP_BIN}" ]]; then
    "${STRIP_BIN}" -g "${ABI_OUT}/lib/libqpdf.a" || true
    if [[ -f "${ABI_OUT}/lib/libqpdf.so" ]]; then
      "${STRIP_BIN}" --strip-unneeded "${ABI_OUT}/lib/libqpdf.so" || true
    fi
  fi

  rsync -a "${SRC_DIR}/qpdf-${QPDF_VERSION}/include/qpdf/" "${ABI_OUT}/include/qpdf/"
  if [[ -f "${ABI_BUILD}/libqpdf/qpdf/qpdf-config.h" ]]; then
    cp "${ABI_BUILD}/libqpdf/qpdf/qpdf-config.h" "${ABI_OUT}/include/qpdf/"
  fi
  popd >/dev/null
done

echo "Done. Artifacts in ${OUT_DIR}/{abi}/{lib,include}"
