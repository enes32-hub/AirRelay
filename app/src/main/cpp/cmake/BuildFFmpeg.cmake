# build minimal static ffmpeg (libavcodec + libavutil + ALAC decoder) and expose under ffmpeg::avcodec and ffmpeg::avutil

include(ExternalProject)
include(ProcessorCount)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(_ffmpeg_arch aarch64)
    set(_ffmpeg_triple aarch64-linux-android)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(_ffmpeg_arch arm)
    set(_ffmpeg_triple armv7a-linux-androideabi)
    set(_ffmpeg_cpu --cpu=armv7-a)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(_ffmpeg_arch x86_64)
    set(_ffmpeg_triple x86_64-linux-android)
elseif(ANDROID_ABI STREQUAL "x86")
    set(_ffmpeg_arch x86)
    set(_ffmpeg_triple i686-linux-android)
else()
    message(FATAL_ERROR "BuildFFmpeg: unhandled ABI ${ANDROID_ABI}")
endif()

# NDK's per-API clang wrappers live next to the generic clang driver
get_filename_component(_ffmpeg_tc "${CMAKE_C_COMPILER}" DIRECTORY)

ProcessorCount(_ffmpeg_jobs)
if(_ffmpeg_jobs EQUAL 0)
    set(_ffmpeg_jobs 4)
endif()

set(FFMPEG_INSTALL ${CMAKE_BINARY_DIR}/ffmpeg)

# FFmpeg's configure script and generated Makefiles require a POSIX toolchain
# when the Android project is built from Windows.
if (CMAKE_HOST_WIN32)
    # Shared with openssl-cmake so one -DAIRRELAY_MSYS_ROOT=... configures both tools.
    if(NOT DEFINED AIRRELAY_MSYS_ROOT)
        set(AIRRELAY_MSYS_ROOT "C:/msys64" CACHE PATH "MSYS2 installation used for Android cross-compilation on Windows")
    endif()
    set(_ffmpeg_bash "${AIRRELAY_MSYS_ROOT}/usr/bin/bash.exe")
    set(_ffmpeg_make "${AIRRELAY_MSYS_ROOT}/usr/bin/make.exe")

    function(_ffmpeg_to_msys_path INPUT OUTPUT)
        string(SUBSTRING "${INPUT}" 0 1 _drive)
        string(SUBSTRING "${INPUT}" 2 -1 _rest)
        string(TOLOWER "${_drive}" _drive)
        set(${OUTPUT} "/${_drive}${_rest}" PARENT_SCOPE)
    endfunction()

    _ffmpeg_to_msys_path("${CMAKE_CURRENT_SOURCE_DIR}/third_party/ffmpeg" _ffmpeg_configure_source)
    _ffmpeg_to_msys_path("${CMAKE_CURRENT_SOURCE_DIR}/../../../../tools/ffmpeg-configure.sh" _ffmpeg_configure_runner)
    _ffmpeg_to_msys_path("${CMAKE_CURRENT_SOURCE_DIR}/../../../../tools/ffmpeg-make.sh" _ffmpeg_make_runner)
    _ffmpeg_to_msys_path("${FFMPEG_INSTALL}" _ffmpeg_configure_install)
    _ffmpeg_to_msys_path("${_ffmpeg_tc}" _ffmpeg_configure_toolchain)
    _ffmpeg_to_msys_path("${CMAKE_SYSROOT}" _ffmpeg_configure_sysroot)
else()
    set(_ffmpeg_bash "bash")
    set(_ffmpeg_make "make")
    set(_ffmpeg_configure_source "${CMAKE_CURRENT_SOURCE_DIR}/third_party/ffmpeg")
    set(_ffmpeg_configure_runner "${CMAKE_CURRENT_SOURCE_DIR}/../../../../tools/ffmpeg-configure.sh")
    set(_ffmpeg_make_runner "${CMAKE_CURRENT_SOURCE_DIR}/../../../../tools/ffmpeg-make.sh")
    set(_ffmpeg_configure_install "${FFMPEG_INSTALL}")
    set(_ffmpeg_configure_toolchain "${_ffmpeg_tc}")
    set(_ffmpeg_configure_sysroot "${CMAKE_SYSROOT}")
endif()

# instrument ffmpeg with the same sanitizers; built with --disable-asm
set(_ffmpeg_sanitize "")
if(SANITIZE)
    string(REPLACE ";" " " _ff_scflags "${SANITIZE_CFLAGS}")
    string(REPLACE ";" " " _ff_sldflags "${SANITIZE_LDFLAGS}")
    set(_ffmpeg_sanitize "--extra-cflags=${_ff_scflags}" "--extra-ldflags=${_ff_sldflags}")
endif()

ExternalProject_Add(ffmpeg_ep
    SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/ffmpeg
    DOWNLOAD_COMMAND ""
    PREFIX ${CMAKE_BINARY_DIR}/ffmpeg-ep
    INSTALL_DIR ${FFMPEG_INSTALL}
    CONFIGURE_COMMAND ${_ffmpeg_bash} ${_ffmpeg_configure_runner} ${_ffmpeg_configure_source}/configure
        --prefix=${_ffmpeg_configure_install}
        --target-os=android --arch=${_ffmpeg_arch} ${_ffmpeg_cpu} --enable-cross-compile
        --cc=${_ffmpeg_configure_toolchain}/${_ffmpeg_triple}${AIRPLAY_ANDROID_API}-clang
        --cxx=${_ffmpeg_configure_toolchain}/${_ffmpeg_triple}${AIRPLAY_ANDROID_API}-clang++
        --ar=${_ffmpeg_configure_toolchain}/llvm-ar --nm=${_ffmpeg_configure_toolchain}/llvm-nm
        --ranlib=${_ffmpeg_configure_toolchain}/llvm-ranlib --strip=${_ffmpeg_configure_toolchain}/llvm-strip
        --sysroot=${_ffmpeg_configure_sysroot}
        --enable-pic --disable-asm --disable-x86asm
        --disable-all --disable-debug --disable-network --disable-autodetect
        --enable-avcodec --enable-decoder=alac --enable-static --disable-shared
        ${_ffmpeg_sanitize}
    BUILD_COMMAND ${_ffmpeg_bash} ${_ffmpeg_make_runner} -j${_ffmpeg_jobs}
    INSTALL_COMMAND ${_ffmpeg_bash} ${_ffmpeg_make_runner} install
    BUILD_BYPRODUCTS ${FFMPEG_INSTALL}/lib/libavcodec.a ${FFMPEG_INSTALL}/lib/libavutil.a
    LOG_CONFIGURE TRUE
    LOG_BUILD TRUE
    LOG_INSTALL TRUE
)

file(MAKE_DIRECTORY ${FFMPEG_INSTALL}/include)

add_library(ffmpeg::avutil STATIC IMPORTED)
set_target_properties(ffmpeg::avutil PROPERTIES
    IMPORTED_LOCATION ${FFMPEG_INSTALL}/lib/libavutil.a
    INTERFACE_INCLUDE_DIRECTORIES ${FFMPEG_INSTALL}/include
)
add_library(ffmpeg::avcodec STATIC IMPORTED)
set_target_properties(ffmpeg::avcodec PROPERTIES
    IMPORTED_LOCATION ${FFMPEG_INSTALL}/lib/libavcodec.a
    INTERFACE_INCLUDE_DIRECTORIES ${FFMPEG_INSTALL}/include
    INTERFACE_LINK_LIBRARIES ffmpeg::avutil
)
