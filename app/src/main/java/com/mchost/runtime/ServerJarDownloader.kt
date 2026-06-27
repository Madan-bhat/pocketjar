package com.mchost.runtime

import com.mchost.data.JarType
import com.mchost.data.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ServerJarDownloader {

    suspend fun ensureServerJar(
        serverDir: File,
        config: ServerConfig,
        onProgress: (String) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            writeServerProperties(serverDir, config)
            writeEula(serverDir, config.eulaAccepted)

            val jarFile = jarFileFor(serverDir, config)
            if (config.jarType == JarType.CUSTOM) {
                if (!jarFile.exists()) error("Custom jar not found: ${jarFile.name}")
                return@runCatching jarFile
            }

            if (jarFile.exists() && jarFile.length() > 1024) {
                onProgress("Using cached ${jarFile.name}")
                return@runCatching jarFile
            }

            onProgress("Downloading ${config.jarType.name} ${config.version}…")
            when (config.jarType) {
                JarType.PAPER -> downloadPaper(config.version, jarFile)
                JarType.PURPUR -> downloadPurpur(config.version, jarFile)
                JarType.VANILLA -> downloadVanilla(config.version, jarFile)
                JarType.FABRIC -> downloadFabric(config.version, jarFile)
                JarType.CUSTOM -> error("Custom jar required")
            }
            jarFile
        }
    }

    fun jarFileFor(serverDir: File, config: ServerConfig): File {
        val name = when (config.jarType) {
            JarType.CUSTOM -> config.customJarName
            else -> "server-${config.jarType.name.lowercase()}-${config.version}.jar"
        }
        return File(serverDir, name)
    }

    fun checkJavaCompatibility(config: ServerConfig): String? {
        val major = config.version.substringBefore('.').toIntOrNull() ?: return null
        if (major >= 26) {
            return "Minecraft ${config.version} requires Java 25+. Bundled runtime is Java 21."
        }
        return null
    }

    private fun writeEula(serverDir: File, accepted: Boolean) {
        File(serverDir, "eula.txt").writeText(
            "# By changing the setting below to TRUE you are indicating your agreement to our EULA.\n" +
                "eula=${accepted}\n"
        )
    }

    private fun writeServerProperties(serverDir: File, config: ServerConfig) {
        val props = buildString {
            appendLine("server-port=${config.serverPort}")
            appendLine("max-players=${config.maxPlayers}")
            appendLine("level-name=${config.worldName}")
            appendLine("level-type=${config.worldType}")
            if (config.worldSeed.isNotBlank()) appendLine("level-seed=${config.worldSeed}")
            appendLine("gamemode=${config.gamemode}")
            appendLine("difficulty=${config.difficulty}")
            appendLine("pvp=${config.pvp}")
            appendLine("spawn-protection=16")
            appendLine("view-distance=${config.viewDistance}")
            appendLine("simulation-distance=${config.simulationDistance}")
            appendLine("white-list=${config.whitelist}")
            appendLine("online-mode=${config.onlineMode}")
            appendLine("enable-command-block=true")
            appendLine("motd=${config.serverName}")
        }
        File(serverDir, "server.properties").writeText(props)
    }

    private fun downloadPaper(version: String, out: File) {
        val metaUrl = "https://api.papermc.io/v2/projects/paper/versions/$version"
        val meta = getJson(metaUrl)
        val builds = meta.getJSONArray("builds")
        val latestBuild = builds.getInt(builds.length() - 1)
        val dlUrl = "$metaUrl/builds/$latestBuild/downloads/paper-$version-$latestBuild.jar"
        downloadFile(dlUrl, out)
    }

    private fun downloadPurpur(version: String, out: File) {
        val metaUrl = "https://api.purpurmc.org/v2/purpur/$version"
        val meta = getJson(metaUrl)
        val builds = meta.getJSONObject("builds").keys().asSequence().mapNotNull { it.toIntOrNull() }.maxOrNull()
            ?: error("No Purpur builds for $version")
        val dlUrl = "https://api.purpurmc.org/v2/purpur/$version/$builds/download"
        downloadFile(dlUrl, out)
    }

    private fun downloadVanilla(version: String, out: File) {
        val manifest = getJson("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")
        val versions = manifest.getJSONArray("versions")
        var versionUrl: String? = null
        for (i in 0 until versions.length()) {
            val v = versions.getJSONObject(i)
            if (v.getString("id") == version) {
                versionUrl = v.getString("url")
                break
            }
        }
        val versionMeta = getJson(versionUrl ?: error("Version $version not found"))
        val serverUrl = versionMeta.getJSONObject("downloads").getJSONObject("server").getString("url")
        downloadFile(serverUrl, out)
    }

    private fun downloadFabric(version: String, out: File) {
        val loaderMeta = getJsonArray("https://meta.fabricmc.net/v2/versions/loader")
        val loaderVersion = loaderMeta.getJSONObject(0).getString("version")
        val installerMeta = getJsonArray("https://meta.fabricmc.net/v2/versions/installer")
        val installerVersion = installerMeta.getJSONObject(0).getString("version")
        val dlUrl =
            "https://meta.fabricmc.net/v2/versions/loader/$version/$loaderVersion/$installerVersion/server/jar"
        downloadFile(dlUrl, out)
    }

    private fun getJsonArray(url: String): JSONArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        return conn.inputStream.bufferedReader().use { JSONArray(it.readText()) }
    }

    private fun getJson(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        return conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    }

    private fun downloadFile(url: String, out: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }
        out.parentFile?.mkdirs()
        conn.inputStream.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
