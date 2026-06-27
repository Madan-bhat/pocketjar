package com.mchost.network.modrinth

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mchost.data.JarType
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class ModrinthClient(
    private val gson: Gson = Gson(),
) {
    suspend fun fetchGameVersions(): Result<List<String>> = runCatching {
        getJsonArray("$BASE/tag/game_version")
            .mapNotNull { el ->
                el.asJsonObject.get("version")?.asString
            }
            .filter { it.matches(Regex("""\d+\.\d+(\.\d+)?""")) }
            .distinct()
            .sortedWith { a, b -> compareVersions(b, a) }
            .take(30)
    }

    suspend fun search(
        query: String,
        kind: ModrinthContentKind,
        gameVersion: String,
        jarType: JarType,
        offset: Int = 0,
        limit: Int = 20,
    ): Result<ModrinthSearchResult> = runCatching {
        val facets = buildFacets(kind, gameVersion, jarType)
        val encodedFacets = URLEncoder.encode(facets, Charsets.UTF_8.name())
        val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val url = "$BASE/search?query=$encodedQuery&facets=$encodedFacets&limit=$limit&offset=$offset&index=downloads"
        val root = getJsonObject(url)
        val hits = root.getAsJsonArray("hits") ?: JsonArray()
        val total = root.get("total_hits")?.asInt ?: hits.size()
        val projects = hits.mapNotNull { hit ->
            val obj = hit.asJsonObject
            val id = obj.get("project_id")?.asString ?: return@mapNotNull null
            val title = obj.get("title")?.asString ?: return@mapNotNull null
            ModrinthProject(
                id = id,
                slug = obj.get("slug")?.asString ?: id,
                title = title,
                description = obj.get("description")?.asString?.take(160) ?: "",
                iconUrl = obj.get("icon_url")?.asString,
                downloads = obj.get("downloads")?.asLong ?: 0L,
                author = obj.get("author")?.asString ?: "",
            )
        }
        ModrinthSearchResult(projects, total)
    }

    suspend fun getProjectVersions(
        projectId: String,
        kind: ModrinthContentKind,
        gameVersion: String,
        jarType: JarType,
    ): Result<List<ModrinthVersion>> = runCatching {
        val loaders = modrinthLoaders(jarType, kind)
        val loaderParam = loaders.joinToString(",") { "\"$it\"" }
        val versionParam = URLEncoder.encode("[\"$gameVersion\"]", Charsets.UTF_8.name())
        val loaderEncoded = URLEncoder.encode("[$loaderParam]", Charsets.UTF_8.name())
        val url = "$BASE/project/$projectId/version?game_versions=$versionParam&loaders=$loaderEncoded"
        getJsonArray(url).mapNotNull { el -> parseVersion(el.asJsonObject) }
            .sortedByDescending { it.versionNumber }
    }

    suspend fun getVersion(versionId: String): Result<ModrinthVersion> = runCatching {
        parseVersion(getJsonObject("$BASE/version/$versionId"))
            ?: throw IllegalStateException("Version not found")
    }

    suspend fun downloadVersion(versionId: String, destFile: File): Result<File> = runCatching {
        val version = getVersion(versionId).getOrThrow()
        destFile.parentFile?.mkdirs()
        downloadToFile(version.downloadUrl, destFile)
        destFile
    }

    private fun parseVersion(obj: JsonObject): ModrinthVersion? {
        val id = obj.get("id")?.asString ?: return null
        val versionNumber = obj.get("version_number")?.asString ?: id
        val files = obj.getAsJsonArray("files") ?: return null
        val fileObj = files.firstOrNull { it.asJsonObject.get("primary")?.asBoolean == true }?.asJsonObject
            ?: files.firstOrNull()?.asJsonObject
            ?: return null
        val filename = fileObj.get("filename")?.asString ?: return null
        val url = fileObj.get("url")?.asString ?: return null
        return ModrinthVersion(
            id = id,
            versionNumber = versionNumber,
            filename = filename,
            downloadUrl = url,
            primary = fileObj.get("primary")?.asBoolean == true,
        )
    }

    private fun buildFacets(kind: ModrinthContentKind, gameVersion: String, jarType: JarType): String {
        val loaders = modrinthLoaders(jarType, kind)
        val loaderFacet = if (loaders.isEmpty()) {
            ""
        } else {
            val orLoaders = loaders.joinToString(",") { "\"categories:$it\"" }
            ",[$orLoaders]"
        }
        return """[["project_type:${kind.projectType}"],["versions:$gameVersion"]$loaderFacet]"""
    }

    private fun getJsonObject(url: String): JsonObject {
        val text = httpGet(url)
        return gson.fromJson(text, JsonObject::class.java)
    }

    private fun getJsonArray(url: String): JsonArray {
        val text = httpGet(url)
        return gson.fromJson(text, JsonArray::class.java)
    }

    private fun httpGet(url: String): String {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also {
            conn.disconnect()
        }
    }

    private fun downloadToFile(url: String, dest: File) {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        conn.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
        conn.disconnect()
    }

    companion object {
        private const val BASE = "https://api.modrinth.com/v2"
        private const val USER_AGENT = "MCHost/1.0 (com.mchost; Android)"

        private fun compareVersions(a: String, b: String): Int {
            fun parts(v: String) = v.split('.').map { it.toIntOrNull() ?: 0 }
            val pa = parts(a)
            val pb = parts(b)
            val len = maxOf(pa.size, pb.size)
            for (i in 0 until len) {
                val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
                if (diff != 0) return diff
            }
            return 0
        }
    }
}
