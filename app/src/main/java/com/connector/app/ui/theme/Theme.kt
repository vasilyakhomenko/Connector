package com.connector.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A0A0A),
    onPrimary = Color(0xFFFAFAFA),
    secondary = Color(0xFF71717A),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0A0A0A),
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFF4F4F5),
    outline = Color(0x1A000000)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFAFAFA),
    onPrimary = Color(0xFF09090B),
    secondary = Color(0xFFA1A1AA),
    background = Color(0xFF09090B),
    surface = Color(0xFF18181B),
    onBackground = Color(0xFFFAFAFA),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF27272A),
    outline = Color(0x1AFFFFFF)
)

@Composable
fun ConnectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
