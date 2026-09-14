cmake_minimum_required(VERSION 3.22.1)

project("grooxtyper")

add_library(
    grooxtyper
    SHARED
    inpainting.cpp
)

find_library(
    log-lib
    log
)

find_library(
    jnigraphics-lib
    jnigraphics
)

target_link_libraries(
    grooxtyper
    ${log-lib}
    ${jnigraphics-lib}
)
