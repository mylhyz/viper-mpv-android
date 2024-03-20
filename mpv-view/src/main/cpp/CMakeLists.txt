cmake_minimum_required(VERSION 3.22.1)

project("mpv-view")

add_library(mpv SHARED IMPORTED )
set_target_properties(mpv PROPERTIES IMPORTED_LOCATION ${DEPS_DIR}/native-libs/${ANDROID_ABI}/lib/libmpv.so)

add_library(avcodec SHARED IMPORTED )
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${DEPS_DIR}/native-libs/${ANDROID_ABI}/lib/libavcodec.so)
add_library(avdevice SHARED IMPORTED )
set_target_properties(avdevice PROPERTIES IMPORTED_LOCATION ${DEPS_DIR}/native-libs/${ANDROID_ABI}/lib/libavdevice.so)
add_library(avfilter SHARED IMPORTED )
set_target_properties(avfilter PROPERTIES IMPORTED_LOCATION ${DEPS_DIR}/native-libs/${ANDROID_ABI}/lib/libavfilter.so)
add_library(avformat SHARED IMPORTED )
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION ${DEPS_DIR}/native-libs/${ANDROID_ABI}/lib/libavformat.so)
add_library(avutil SHARED IMPORTED )
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION ${DEPS_DIR}/native-libs/${ANDROID_ABI}/lib/libavutil.so)
add_library(swresample SHARED IMPORTED )
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION ${DEPS_DIR}/native-libs/${ANDROID_ABI}/lib/libswresample.so)
add_library(swscale SHARED IMPORTED )
set_target_properties(swscale PROPERTIES IMPORTED_LOCATION ${DEPS_DIR}/native-libs/${ANDROID_ABI}/lib/libswscale.so)

add_library(postproc SHARED IMPORTED )
set_target_properties(postproc PROPERTIES IMPORTED_LOCATION ${DEPS_DIR}/native-libs/${ANDROID_ABI}/lib/libpostproc.so)

add_library(mpvjni SHARED
        main.cpp
        property.cpp
        render.cpp
        thumbnail.cpp
        log.cpp
        jni_utils.cpp
        event.cpp
)
target_include_directories(mpvjni PRIVATE ${DEPS_DIR}/native-libs/${ANDROID_ABI}/include/)
target_link_libraries(mpvjni android log mpv avcodec avdevice avfilter avformat avutil swresample swscale postproc)
