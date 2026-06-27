package com.mchost.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object FileImporter {

    fun copyUri(context: Context, uri: Uri, destFile: File) {
        destFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Cannot read $uri")
    }

    fun copyUriToSubdir(context: Context, uri: Uri, serverDir: File, subdir: String): File {
        val destDir = File(serverDir, subdir).also { it.mkdirs() }
        return copyUriToDir(context, uri, destDir)
    }

    fun copyUriToDir(context: Context, uri: Uri, destDir: File, defaultExtension: String? = "jar"): File {
        destDir.mkdirs()
        var name = queryDisplayName(context, uri)?.trim().orEmpty()
        if (name.isBlank()) {
            val ext = defaultExtension ?: "bin"
            name = "import-${System.currentTimeMillis()}.$ext"
        } else if (!name.contains('.') && defaultExtension != null) {
            name = "$name.$defaultExtension"
        }
        val dest = uniqueFile(destDir, name)
        copyUri(context, uri, dest)
        return dest
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot + 1) else ""
        var n = 2
        while (true) {
            val nextName = if (ext.isBlank()) "$base-$n" else "$base-$n.$ext"
            candidate = File(dir, nextName)
            if (!candidate.exists()) return candidate
            n++
        }
    }

    fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return uri.lastPathSegment
    }
}
