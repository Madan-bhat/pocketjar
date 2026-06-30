package com.mchost.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF121212)
val Surface = Color(0xFF161A14)
val Accent = Color(0xFF4ADE80)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9CA3AF)
val BorderSubtle = Color(0xFF333333)
val ContainerRaised = Color(0xFF1E1E1E)
val NavActivePurple = Color(0xFF7C3AED)
val ErrorRed = Color(0xFFEF4444)
val WarnAmber = Color(0xFFF59E0B)

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    background = Background,
    surface = Surface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorRed,
)

@Composable
fun MCHostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content,
    )
}

fun logColor(level: com.mchost.data.LogLevel): Color = when (level) {
    com.mchost.data.LogLevel.DEBUG -> TextSecondary
    com.mchost.data.LogLevel.INFO -> TextPrimary
    com.mchost.data.LogLevel.WARN -> WarnAmber
    com.mchost.data.LogLevel.ERROR -> ErrorRed
}
