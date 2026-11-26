cmake_minimum_required(VERSION 3.22.1)
project("empyreal_core" CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(empyreal_core SHARED
        empyreal_core.cpp)

target_link_options(empyreal_core PRIVATE
        "-Wl,--exclude-libs,ALL"
        "-Wl,-z,max-page-size=16384")

target_link_libraries(empyreal_core
        log)
