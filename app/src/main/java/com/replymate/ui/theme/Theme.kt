package com.replymate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colours from spec section 7.
val Teal = Color(0xFF1F6F5C)
val LightTeal = Color(0xFFEAF4F1)
val TealDark = Color(0xFF4FB39A)
val SurfaceDark = Color(0xFF1C1F1E)
val OutlineDark = Color(0xFF2E3532)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = LightTeal,
    onPrimaryContainer = Teal,
    secondaryContainer = LightTeal,
    onSecondaryContainer = Teal,
)

private val DarkColors = darkColorScheme(
    primary = TealDark,
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF1A3D34),
    onPrimaryContainer = Color(0xFFBCE9DB),
    secondaryContainer = Color(0xFF1A3D34),
    onSecondaryContainer = Color(0xFFBCE9DB),
    background = SurfaceDark,
    surface = SurfaceDark,
    surfaceContainer = Color(0xFF232826),
    outline = OutlineDark,
    outlineVariant = OutlineDark,
)

@Composable
fun ReplyMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
