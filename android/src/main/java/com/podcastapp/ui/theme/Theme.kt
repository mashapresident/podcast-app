package com.podcastapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.podcastapp.data.local.ThemeMode

private val Purple = Color(0xFF6B4EFF)

private val Light = lightColorScheme(
    primary = Purple, onPrimary = Color.White,
    secondaryContainer = Color(0xFFE6E0FF),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFB8A9FF), onPrimary = Color(0xFF1E0A6B),
    secondaryContainer = Color(0xFF3A3170),
)

@Composable
fun PodcastTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
