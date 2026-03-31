cmake_minimum_required(VERSION 3.22.1)

project(runtime_native)

add_library(
    runtime_native
    SHARED
    src/main/cpp/native_qnn_bridge.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    runtime_native
    ${log-lib}
)
