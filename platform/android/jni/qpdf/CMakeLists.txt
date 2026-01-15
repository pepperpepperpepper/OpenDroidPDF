cmake_minimum_required(VERSION 3.16)
project(qpdfjni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT QPDF_SHARED_LIB)
  message(FATAL_ERROR "QPDF_SHARED_LIB is required")
endif()
if(NOT JPEG_STATIC_LIB)
  message(FATAL_ERROR "JPEG_STATIC_LIB is required")
endif()
if(NOT QPDF_INC_ROOT)
  message(FATAL_ERROR "QPDF_INC_ROOT is required")
endif()
if(NOT JPEG_INC_ROOT)
  message(FATAL_ERROR "JPEG_INC_ROOT is required")
endif()

add_library(qpdfjni SHARED qpdf_jni.cpp)
target_include_directories(qpdfjni PRIVATE ${QPDF_INC_ROOT} ${JPEG_INC_ROOT})
target_link_libraries(qpdfjni PRIVATE ${QPDF_SHARED_LIB} ${JPEG_STATIC_LIB} z log)

# Strip unused symbols to keep .so small.
set_target_properties(qpdfjni PROPERTIES LINK_FLAGS_RELEASE "-Wl,--gc-sections")
