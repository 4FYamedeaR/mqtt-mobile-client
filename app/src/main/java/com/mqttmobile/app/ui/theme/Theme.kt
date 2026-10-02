package com.mqttmobile.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4967A8),
    secondary = Color(0xFF5C6B84),
    tertiary = Color(0xFF006B60),
    background = Color(0xFFF8F9FD),
    surface = Color(0xFFF8F9FD)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB2C5FF),
    secondary = Color(0xFFBBC6DF),
    tertiary = Color(0xFF50DBC9)
)

@Composable
fun MqttMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
