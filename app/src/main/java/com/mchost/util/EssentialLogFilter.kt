package com.mchost.util

import com.mchost.data.LogLevel

object EssentialLogFilter {

    private val noise = listOf(
        Regex("""(?i)\[DEBUG\]"""),
        Regex("""(?i)jvm args"""),
        Regex("""(?i)java_home"""),
        Regex("""(?i)ld_library_path"""),
        Regex("""(?i)dlopen"""),
        Regex("""(?i)jli_launch"""),
        Regex("""(?i)redirected stdout"""),
        Regex("""(?i)downloading mojang"""),
        Regex("""(?i)downloading .* library"""),
        Regex("""(?i)applying patches"""),
        Regex("""(?i)patched jre"""),
        Regex("""(?i)extracting bundled jre"""),
        Regex("""(?i)runtime ready"""),
        Regex("""(?i)using cached"""),
        Regex("""(?i)classpath"""),
        Regex("""(?i)bundler"""),
        Regex("""(?i)paperclip"""),
        Regex("""(?i)fetching version"""),
        Regex("""(?i)building processors"""),
    )

    private val essential = listOf(
        Regex("""(?i)^\[INFO\] > """),
        Regex("""(?i)^> """),
        Regex("""(?i)done \(\d"""),
        Regex("""(?i)for help, type"""),
        Regex("""(?i)starting minecraft server"""),
        Regex("""(?i)loading plugins"""),
        Regex("""(?i)enabling \w"""),
        Regex("""(?i)enabled \d plugin"""),
        Regex("""(?i)preparing start region"""),
        Regex("""(?i)time elapsed"""),
        Regex("""(?i)joined the game"""),
        Regex("""(?i)left the game"""),
        Regex("""(?i)lost connection"""),
        Regex("""(?i)issued server command"""),
        Regex("""(?i)whitelist"""),
        Regex("""(?i)saving chunks"""),
        Regex("""(?i)saved the game"""),
        Regex("""(?i)stopping (the )?server"""),
        Regex("""(?i)made .* a server operator"""),
        Regex("""(?i)removed .* from operator"""),
        Regex("""(?i)server process exited"""),
        Regex("""(?i)starting server in"""),
        Regex("""(?i)cannot send command"""),
        Regex("""(?i)upnp"""),
        Regex("""(?i)localxpose"""),
        Regex("""(?i)loclx:"""),
        Regex("""(?i)friends can join"""),
        Regex("""(?i)jar download failed"""),
        Regex("""(?i)runtime:"""),
        Regex("""(?i)unsupported java"""),
        Regex("""(?i)/INFO\]:"""),
        Regex("""(?i)/WARN\]:"""),
        Regex("""(?i)\[Server thread"""),
        Regex("""(?i)\[\w+[\w\s]*\]"""), // plugin tags e.g. [LuckPerms]
    )

    fun isEssential(message: String, level: LogLevel): Boolean {
        if (level == LogLevel.ERROR || level == LogLevel.WARN) return true
        if (level == LogLevel.DEBUG) return false
        if (noise.any { it.containsMatchIn(message) }) return false
        if (essential.any { it.containsMatchIn(message) }) return true
        return false
    }
}
