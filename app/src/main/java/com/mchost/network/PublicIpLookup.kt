package com.mchost.network

import java.net.HttpURLConnection
import java.net.URL

object PublicIpLookup {
    fun fetch(): String? {
        val endpoints = listOf(
            "https://api.ipify.org",
            "https://checkip.amazonaws.com",
        )
        for (url in endpoints) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                }
                val ip = conn.inputStream.bufferedReader().use { it.readText().trim() }
                if (ip.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) return ip
            } catch (_: Exception) {
            }
        }
        return null
    }
}
