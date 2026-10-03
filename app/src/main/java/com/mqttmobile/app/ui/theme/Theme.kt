package com.mqttmobile.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F4E5),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF41558B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E6FF),
    onSecondaryContainer = Color(0xFF101A40),
    tertiary = Color(0xFF9A5800),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB5),
    onTertiaryContainer = Color(0xFF321A00),
    background = Color(0xFFF4F8F6),
    onBackground = Color(0xFF10211D),
    surface = Color(0xFFFAFCFB),
    onSurface = Color(0xFF10211D),
    surfaceDim = Color(0xFFD7E1DD),
    surfaceBright = Color(0xFFFAFCFB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F7F4),
    surfaceContainer = Color(0xFFEBF2EF),
    surfaceContainerHigh = Color(0xFFE4ECE8),
    surfaceContainerHighest = Color(0xFFDDE7E2),
    surfaceVariant = Color(0xFFE2ECE8),
    onSurfaceVariant = Color(0xFF4E635D),
    outline = Color(0xFF81958F),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6DE7BF),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF07513F),
    onPrimaryContainer = Color(0xFF8FF8D2),
    secondary = Color(0xFFB8C4FF),
    onSecondary = Color(0xFF202C5B),
    secondaryContainer = Color(0xFF394574),
    onSecondaryContainer = Color(0xFFE0E5FF),
    tertiary = Color(0xFFFFC978),
    onTertiary = Color(0xFF432B00),
    tertiaryContainer = Color(0xFF624000),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF070C14),
    onBackground = Color(0xFFE9F2F0),
    surface = Color(0xFF0C1420),
    onSurface = Color(0xFFE9F2F0),
    surfaceDim = Color(0xFF070C14),
    surfaceBright = Color(0xFF273541),
    surfaceContainerLowest = Color(0xFF04080D),
    surfaceContainerLow = Color(0xFF0A121C),
    surfaceContainer = Color(0xFF101A25),
    surfaceContainerHigh = Color(0xFF1A2530),
    surfaceContainerHighest = Color(0xFF25323D),
    surfaceVariant = Color(0xFF1A2934),
    onSurfaceVariant = Color(0xFFA9B9C1),
    outline = Color(0xFF536872),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.35).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 25.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 22.sp)
    )
}

@Composable
fun MqttMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
