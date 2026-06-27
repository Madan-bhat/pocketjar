cmake_minimum_required(VERSION 3.22.1)
project("mchost_jvm")

add_library(mchost_jvm SHARED
        jre_launcher.c
        utils.c
        native_exec.c
)

find_library(log-lib log)

target_link_libraries(mchost_jvm ${log-lib})
