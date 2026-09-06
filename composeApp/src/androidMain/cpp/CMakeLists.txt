cmake_minimum_required(VERSION 3.22.1)
project(kruxx_audio C)

# Keep LAME as a separate shared library so it can be replaced/relinked under LGPL.
file(GLOB LAME_SOURCES CONFIGURE_DEPENDS lame/libmp3lame/*.c)
add_library(mp3lame SHARED ${LAME_SOURCES})
target_include_directories(mp3lame PUBLIC lame/include PRIVATE lame/libmp3lame .)
target_compile_definitions(mp3lame PRIVATE HAVE_CONFIG_H)
target_link_libraries(mp3lame m)
add_library(kruxx_mp3 SHARED mp3_jni.c)
if(NOT ANDROID)
    # The host smoke check calls the exact JNI bridge using generated PCM.
    find_path(KRUXX_JNI_INCLUDE jni.h HINTS "$ENV{JAVA_HOME}/include" REQUIRED)
    find_path(KRUXX_JNI_PLATFORM_INCLUDE jni_md.h
        HINTS "$ENV{JAVA_HOME}/include/linux" "$ENV{JAVA_HOME}/include/darwin" REQUIRED)
    target_include_directories(kruxx_mp3 PRIVATE ${KRUXX_JNI_INCLUDE} ${KRUXX_JNI_PLATFORM_INCLUDE})
endif()
target_link_libraries(kruxx_mp3 mp3lame)
# Android devices with 16 KiB pages must be able to load both libraries.
target_link_options(mp3lame PRIVATE "-Wl,-z,max-page-size=16384")
target_link_options(kruxx_mp3 PRIVATE "-Wl,-z,max-page-size=16384")
