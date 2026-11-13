cmake_minimum_required(VERSION 3.10)

project(simple_whisper)

set(CMAKE_CXX_STANDARD 17)
set(WHISPER_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../../third_party/whisper.cpp)

set(
    SOURCE_FILES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_SOURCE_DIR}/simple_whisper_jni.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml.c
    ${WHISPER_LIB_DIR}/ggml/src/ggml-alloc.c
    ${WHISPER_LIB_DIR}/ggml/src/ggml-backend.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-backend-reg.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-quants.c
    ${WHISPER_LIB_DIR}/ggml/src/ggml-threading.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/ggml-cpu.c
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/ggml-cpu.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/ggml-cpu-aarch64.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/ggml-cpu-hbm.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/ggml-cpu-quants.c
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/ggml-cpu-traits.cpp
)

find_library(LOG_LIB log)

add_library(simple_whisper SHARED ${SOURCE_FILES})

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(simple_whisper PRIVATE -march=armv8.2-a+fp16)
endif()

if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    target_compile_options(simple_whisper PRIVATE -Os)
    target_compile_options(simple_whisper PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
    target_compile_options(simple_whisper PRIVATE -ffunction-sections -fdata-sections)
    target_link_options(simple_whisper PRIVATE -Wl,--gc-sections)
    target_link_options(simple_whisper PRIVATE -Wl,--exclude-libs,ALL)
    target_link_options(simple_whisper PRIVATE -flto)
endif()

target_include_directories(
    simple_whisper
    PRIVATE
    ${WHISPER_LIB_DIR}
    ${WHISPER_LIB_DIR}/src
    ${WHISPER_LIB_DIR}/include
    ${WHISPER_LIB_DIR}/ggml/include
    ${WHISPER_LIB_DIR}/ggml/src
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu
)

target_link_libraries(simple_whisper ${LOG_LIB} android)
