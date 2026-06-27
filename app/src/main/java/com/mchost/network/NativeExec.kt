package com.mchost.network

object NativeExec {
    init {
        System.loadLibrary("mchost_jvm")
    }

    /** @return child pid, or -1 on failure */
    external fun nativeSpawn(
        binary: String,
        args: Array<String>,
        env: Array<String>,
        logPath: String,
    ): Int

    external fun nativeIsAlive(pid: Int): Boolean

    external fun nativeKill(pid: Int)
}
