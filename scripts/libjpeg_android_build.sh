#!/usr/bin/env bash
# Build libjpeg-turbo for Android ABIs using the NDK toolchain.
# Outputs headers + static lib under platform/android/jni/qpdf/deps/libjpeg/<abi>/{include,lib}.

set -euo pipefail

JPEG_VERSION="${JPEG_VERSION:-3.0.0}"
JPEG_URL="${JPEG_URL:-https://github.com/libjpeg-turbo/libjpeg-turbo/archive/${JPEG_VERSION}.tar.gz}"
ABI_LIST="${ABI_LIST:-arm64-v8a armeabi-v7a}"
API_LEVEL="${API_LEVEL:-24}"
NDK_ROOT="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-${NDK_HOME:-}}}"

if [[ -z "${NDK_ROOT}" ]]; then
  echo "ANDROID_NDK_ROOT / ANDROID_NDK_HOME is required" >&2
  exit 2
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
WORK_DIR="${WORK_DIR:-${REPO_ROOT}/thirdparty_build/libjpeg-turbo-${JPEG_VERSION}-android}"
SRC_DIR="${WORK_DIR}/src"
BUILD_ROOT="${WORK_DIR}/build"
OUT_ROOT="${REPO_ROOT}/platform/android/jni/qpdf/deps/libjpeg"

mkdir -p "${SRC_DIR}" "${BUILD_ROOT}" "${OUT_ROOT}"

if [[ ! -d "${SRC_DIR}/libjpeg-turbo-${JPEG_VERSION}" ]]; then
  echo "Fetching libjpeg-turbo ${JPEG_VERSION} …"
  curl -L "${JPEG_URL}" | tar -xz -C "${SRC_DIR}"
fi

for ABI in ${ABI_LIST}; do
  BUILD_DIR="${BUILD_ROOT}/${ABI}"
  INSTALL_DIR="${OUT_ROOT}/${ABI}"
  mkdir -p "${BUILD_DIR}" "${INSTALL_DIR}"

  pushd "${BUILD_DIR}" >/dev/null
  cmake \
    -G Ninja \
    "-DCMAKE_TOOLCHAIN_FILE=${NDK_ROOT}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="${ABI}" \
    -DANDROID_PLATFORM="android-${API_LEVEL}" \
    -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DENABLE_SHARED=OFF \
    -DENABLE_STATIC=ON \
    -DWITH_JPEG8=ON \
    -DWITH_TURBOJPEG=OFF \
    -DWITH_SIMD=ON \
    -DCMAKE_INSTALL_PREFIX="${INSTALL_DIR}" \
    "${SRC_DIR}/libjpeg-turbo-${JPEG_VERSION}"

  ninja install

  # Provide a minimal pkg-config file for qpdf's CMake find logic.
  mkdir -p "${INSTALL_DIR}/lib/pkgconfig"
  cat > "${INSTALL_DIR}/lib/pkgconfig/libjpeg.pc" <<EOF
prefix=${INSTALL_DIR}
exec_prefix=\${prefix}
includedir=\${prefix}/include
libdir=\${exec_prefix}/lib

Name: libjpeg
Description: libjpeg-turbo ${JPEG_VERSION} (Android ${ABI})
Version: ${JPEG_VERSION}
Libs: -L\${libdir} -ljpeg
Cflags: -I\${includedir}
EOF

  popd >/dev/null
  echo "Built libjpeg-turbo ${JPEG_VERSION} for ${ABI} -> ${INSTALL_DIR}"
done

echo "Done. Artifacts in ${OUT_ROOT}/{abi}/"
