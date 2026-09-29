package com.ginsengo.steward.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark only. Field use is outdoors at dusk and in shade, and on OLED panels dark pixels
 * cost almost nothing to light, which is most of this screen.
 */
object Gen {
    val Bg = Color(0xFF06100B)
    val Surface = Color(0xFF0D1A13)
    val SurfaceHigh = Color(0xFF15261C)
    val Text = Color(0xFFE6F4EC)
    val TextDim = Color(0xFF8FA89A)
    val Accent = Color(0xFF00FF88)
    val Amber = Color(0xFFFFB02E)
    val Blue = Color(0xFF6FB7FF)
    val Danger = Color(0xFFFF6B5A)
}

@Composable
fun GensingoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gen.Accent,
            onPrimary = Gen.Bg,
            secondary = Gen.Amber,
            background = Gen.Bg,
            onBackground = Gen.Text,
            surface = Gen.Surface,
            onSurface = Gen.Text,
            surfaceVariant = Gen.SurfaceHigh,
            onSurfaceVariant = Gen.TextDim,
            error = Gen.Danger,
        ),
        content = content,
    )
}
