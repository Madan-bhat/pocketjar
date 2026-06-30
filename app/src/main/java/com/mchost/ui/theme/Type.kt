package com.mchost.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mchost.R

val RobotoFlex = FontFamily(
    Font(R.font.roboto_flex, FontWeight.Thin),
    Font(R.font.roboto_flex, FontWeight.Light),
    Font(R.font.roboto_flex, FontWeight.Normal),
    Font(R.font.roboto_flex, FontWeight.Medium),
    Font(R.font.roboto_flex, FontWeight.SemiBold),
    Font(R.font.roboto_flex, FontWeight.Bold),
    Font(R.font.roboto_flex, FontWeight.Black),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private fun sans(weight: FontWeight, size: TextUnit, lineHeight: TextUnit) =
    TextStyle(fontFamily = RobotoFlex, fontWeight = weight, fontSize = size, lineHeight = lineHeight)

val AppTypography = Typography(
    displayLarge = sans(FontWeight.Bold, 57.sp, 64.sp),
    displayMedium = sans(FontWeight.Bold, 45.sp, 52.sp),
    displaySmall = sans(FontWeight.SemiBold, 36.sp, 44.sp),
    headlineLarge = sans(FontWeight.Bold, 32.sp, 40.sp),
    headlineMedium = sans(FontWeight.Bold, 28.sp, 36.sp),
    headlineSmall = sans(FontWeight.SemiBold, 24.sp, 32.sp),
    titleLarge = sans(FontWeight.SemiBold, 22.sp, 28.sp),
    titleMedium = sans(FontWeight.Medium, 16.sp, 24.sp),
    titleSmall = sans(FontWeight.Medium, 14.sp, 20.sp),
    bodyLarge = sans(FontWeight.Normal, 16.sp, 24.sp),
    bodyMedium = sans(FontWeight.Normal, 14.sp, 20.sp),
    bodySmall = sans(FontWeight.Normal, 12.sp, 16.sp),
    labelLarge = sans(FontWeight.Medium, 14.sp, 20.sp),
    labelMedium = sans(FontWeight.Medium, 12.sp, 16.sp),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
