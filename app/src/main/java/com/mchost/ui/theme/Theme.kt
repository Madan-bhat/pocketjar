package com.mchost.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Background = Color(0xFF0D0D0D)
val Surface = Color(0xFF161A14)
val Accent = Color(0xFF39FF14)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8A8A8A)
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

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 12.sp),
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
