package com.hereliesaz.wavefrom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WaveColorScheme = darkColorScheme(
    primary = WaveCyan,
    secondary = WaveAmber,
    background = WaveDeep,
    surface = WaveSurface,
)

/** WaveFrom always uses a dark scheme — the UI overlays a live camera feed. */
@Composable
fun WaveFromTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WaveColorScheme,
        typography = WaveTypography,
        content = content,
    )
}
