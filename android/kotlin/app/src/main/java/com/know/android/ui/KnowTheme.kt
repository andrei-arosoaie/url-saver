package com.know.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Directly ported from webapp/src/global.css. Do not enable Material dynamic colors.
private val Light = lightColorScheme(
    primary = Color(0xFF1660EC), onPrimary = Color.White,
    background = Color(0xFFF5F7FC), onBackground = Color(0xFF17233D),
    surface = Color.White, onSurface = Color(0xFF17233D),
    onSurfaceVariant = Color(0xFF66738D), outline = Color(0xFFDCE3EF),
    secondaryContainer = Color(0xFFEDF4FF), onSecondaryContainer = Color(0xFF1660EC),
    error = Color(0xFFA32D3B),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8BB6FF), onPrimary = Color(0xFF111827),
    background = Color(0xFF111827), onBackground = Color(0xFFE8EFFF),
    surface = Color(0xFF182235), onSurface = Color(0xFFE8EFFF),
    onSurfaceVariant = Color(0xFFA6B5D1), outline = Color(0xFF34435C),
    secondaryContainer = Color(0xFF203453), onSecondaryContainer = Color(0xFF8BB6FF),
    error = Color(0xFFF59CA4),
)

enum class ThemePreference { SYSTEM, LIGHT, DARK }

@Composable
fun KnowTheme(preference: ThemePreference = ThemePreference.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 38.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 19.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 16.sp),
            labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 10.sp, lineHeight = 14.sp),
        ),
        content = content,
    )
}
