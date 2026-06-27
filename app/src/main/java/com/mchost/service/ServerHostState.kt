package com.mchost.service

data class ServerHostState(
    val running: Boolean,
    val exitCode: Int?,
    val pid: Int,
)
