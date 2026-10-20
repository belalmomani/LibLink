package com.liblink.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ألوان مأخوذة من أيقونة LibLink
private val scheme = darkColorScheme(
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF031026),
    secondary = Color(0xFF7C5CFF),
    background = Color(0xFF050B2A),
    surface = Color(0xFF0B1445),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun LibLinkTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, content = content)
