package com.mchost.runtime

object JvmNative {
    init {
        System.loadLibrary("mchost_jvm")
    }

    external fun launchJVM(
        args: Array<String>,
        javaHome: String,
        ldLibraryPath: String,
        tmpDir: String,
        workDir: String?,
        logDir: String?,
    ): Int

    external fun writeStdin(data: ByteArray)
    external fun dlopen(path: String): Boolean
    external fun setLdLibraryPath(path: String)
    external fun chdir(path: String): Int
}
