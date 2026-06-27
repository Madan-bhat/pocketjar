package com.mchost.runtime

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import com.mchost.data.RuntimeProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class RuntimeManager(
    private val context: Context,
    private val storage: StorageManager,
) {
    companion object {
        private const val JRE_ASSET = "runtime/jre21-aarch64.zip"
        private const val JRE_DOWNLOAD_URL =
            "https://github.com/QuestCraftPlusPlus/android-openjdk-build-multiarch/releases/latest/download/JRE-21.zip"
    }

    var javaHome: File = storage.runtimeRoot
        private set
    var ldLibraryPath: String = ""
        private set
    private var jvmLibraryPath: String = ""

    suspend fun ensureRuntime(
        allowDownload: Boolean = true,
        onProgress: (RuntimeProgress) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val runtimeDir = storage.runtimeRoot
        val marker = File(runtimeDir, ".extracted")
        if (marker.exists() && File(runtimeDir, "lib").exists()) {
            patchJreRelease(findJreRoot(runtimeDir))
            configureEnv(runtimeDir, context.applicationInfo.nativeLibraryDir)
            return@withContext Result.success(Unit)
        }

        onProgress(RuntimeProgress("Extracting bundled JRE…", 10))
        try {
            context.assets.open(JRE_ASSET).use { input ->
                unzipTo(input, runtimeDir)
            }
        } catch (e: Exception) {
            LogBus.emit(com.mchost.data.LogLevel.WARN, "Asset JRE missing: ${e.message}")
            if (!allowDownload) {
                return@withContext Result.failure(
                    IllegalStateException("JRE not bundled. Run scripts/setup-android-runtime.sh")
                )
            }
            onProgress(RuntimeProgress("Downloading JRE 21…", 30))
            downloadAndExtract(JRE_DOWNLOAD_URL, runtimeDir, onProgress)
        }

        flattenJreRoot(runtimeDir)
        patchJreRelease(findJreRoot(runtimeDir))
        marker.writeText("ok")
        configureEnv(runtimeDir, context.applicationInfo.nativeLibraryDir)
        onProgress(RuntimeProgress("Runtime ready", 100))
        Result.success(Unit)
    }

    /** Pojav-style init — call only from :mcserver JVM thread before JLI_Launch. */
    fun prepareForLaunch(tmpDir: File, nativeLibDir: String) {
        configureEnv(javaHome, nativeLibDir)
        setupEnvForLaunch(javaHome, ldLibraryPath, tmpDir)

        val combinedLd = "$jvmLibraryPath:$ldLibraryPath"
        JvmNative.setLdLibraryPath(combinedLd)
        setEnv("LD_LIBRARY_PATH", combinedLd)

        initJavaRuntime(javaHome)
        LogBus.emit(com.mchost.data.LogLevel.INFO, "JAVA_HOME=${javaHome.absolutePath}")
        LogBus.emit(com.mchost.data.LogLevel.DEBUG, "LD_LIBRARY_PATH=$combinedLd")
    }

    fun setupEnvForLaunch(javaHomeDir: File, ldPath: String, tmpDir: File) {
        setEnv("JAVA_HOME", javaHomeDir.absolutePath)
        setEnv("LD_LIBRARY_PATH", ldPath)
        setEnv("TMPDIR", tmpDir.absolutePath)
        setEnv("HOME", context.filesDir.absolutePath)
        setEnv("PATH", "${javaHomeDir.absolutePath}/bin:" + (Os.getenv("PATH") ?: ""))
    }

    private fun setEnv(key: String, value: String) {
        try {
            Os.setenv(key, value, true)
        } catch (e: ErrnoException) {
            LogBus.emit(com.mchost.data.LogLevel.WARN, "setenv($key) failed: ${e.message}")
        }
    }

    private fun configureEnv(runtimeDir: File, nativeLibDir: String) {
        val jreRoot = findJreRoot(runtimeDir)
        javaHome = jreRoot
        ldLibraryPath = buildLdLibraryPath(jreRoot, nativeLibDir)
        val serverJvm = File(jreRoot, "lib/server/libjvm.so")
        jvmLibraryPath = if (serverJvm.exists()) {
            File(jreRoot, "lib/server").absolutePath
        } else {
            File(jreRoot, "lib").absolutePath
        }
    }

    /** Mirrors PojavLauncher JREUtils.initJavaRuntime(). */
    private fun initJavaRuntime(jreHome: File) {
        dlopen(findInLdLibPath("libjli.so"))
        if (!JvmNative.dlopen(findInLdLibPath("libjvm.so"))) {
            JvmNative.dlopen("$jvmLibraryPath/libjvm.so")
        }
        listOf(
            "libverify.so", "libjava.so", "libnet.so", "libnio.so",
            "libzip.so", "libinstrument.so", "libmanagement.so",
            "libawt.so", "libawt_headless.so", "libfreetype.so", "libfontmanager.so",
        ).forEach { dlopen(findInLdLibPath(it)) }
        locateLibs(File(jreHome, "lib")).forEach { JvmNative.dlopen(it.absolutePath) }
    }

    private fun dlopen(path: String) {
        if (!JvmNative.dlopen(path)) {
            LogBus.emit(com.mchost.data.LogLevel.WARN, "dlopen failed: $path")
        }
    }

    private fun findInLdLibPath(libName: String): String {
        val ldPath = Os.getenv("LD_LIBRARY_PATH") ?: ldLibraryPath
        for (part in ldPath.split(":")) {
            if (part.isBlank()) continue
            val f = File(part, libName)
            if (f.isFile) return f.absolutePath
        }
        return libName
    }

    private fun locateLibs(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        val out = mutableListOf<File>()
        dir.listFiles()?.forEach { f ->
            when {
                f.isFile && f.name.endsWith(".so") -> out.add(f)
                f.isDirectory -> out.addAll(locateLibs(f))
            }
        }
        return out
    }

    private fun buildLdLibraryPath(jreRoot: File, nativeLibDir: String): String {
        val libDir = File(jreRoot, "lib")
        val jliDir = File(libDir, "jli")
        val parts = mutableListOf<String>()
        if (jliDir.isDirectory) parts.add(jliDir.absolutePath)
        parts.add(libDir.absolutePath)
        File(libDir, "server").takeIf { it.isDirectory }?.let { parts.add(it.absolutePath) }
        parts.add("/system/lib64")
        parts.add("/vendor/lib64")
        parts.add("/vendor/lib64/hw")
        parts.add(nativeLibDir)
        return parts.distinct().joinToString(":")
    }

    private fun findJreRoot(base: File): File {
        if (File(base, "lib").exists()) return base
        base.listFiles()?.forEach { child ->
            if (child.isDirectory && File(child, "lib").exists()) return child
        }
        return base
    }

    private fun flattenJreRoot(runtimeDir: File) {
        val nested = findJreRoot(runtimeDir)
        if (nested == runtimeDir) return
        nested.listFiles()?.forEach { f ->
            f.copyRecursively(File(runtimeDir, f.name), overwrite = true)
        }
    }

    /**
     * QuestCraft JRE reports 21.0.3-internal; Paper rejects non-GA versions.
     * Rewrite release metadata so java.version looks like an official build.
     */
    private fun patchJreRelease(jreRoot: File) {
        val release = File(jreRoot, "release")
        if (!release.isFile) return
        val original = release.readText()
        if (!original.contains("-internal")) return
        val patched = original
            .replace(Regex("""JAVA_VERSION="[^"]*""""), """JAVA_VERSION="21.0.4"""")
            .replace(Regex("""IMPLEMENTOR_VERSION="[^"]*""""), """IMPLEMENTOR_VERSION="21.0.4+7"""")
        if (patched != original) {
            release.writeText(patched)
            LogBus.emit(com.mchost.data.LogLevel.INFO, "Patched JRE release metadata for server compatibility")
        }
    }

    private fun unzipTo(input: java.io.InputStream, dest: File) {
        ZipInputStream(BufferedInputStream(input)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(dest, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zis.copyTo(it) }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun downloadAndExtract(
        url: String,
        dest: File,
        onProgress: (RuntimeProgress) -> Unit,
    ) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }
        conn.inputStream.use { input -> unzipTo(input, dest) }
        onProgress(RuntimeProgress("JRE downloaded", 80))
    }
}
