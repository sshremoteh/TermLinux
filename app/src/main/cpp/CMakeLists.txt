cmake_minimum_required(VERSION 3.22.1)
project(termlinux_native LANGUAGES C CXX)

add_library(termlinux_native SHARED
    pty/pty_manager.cpp
    process/process_manager.cpp
    jni/termlinux_jni.cpp
)

target_include_directories(termlinux_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(termlinux_native
    log
)
