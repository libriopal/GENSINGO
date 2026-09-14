package com.ginsengo.steward.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PRD §5.1 palette. These are the only colours in the app; nothing hardcodes a hex
 * outside this file.
 */
object Gen {
    val Base = Color(0xFF06100B)          // deep canopy obsidian
    val Surface = Color(0xFF0D1B13)       // moss slate
    val Primary = Color(0xFF00FF88)       // luminous forest green
    val Warning = Color(0xFFFFC857)       // field amber
    val Alert = Color(0xFFFF4D6D)         // steward red
    val TextPrimary = Color(0xFFE6F4EC)   // parchment
    val TextSecondary = Color(0xFF8BA897) // subdued foliage
    val Hairline = Color(0xFF1A3324)      // 1px emerald frame

    /** PRD §5.3: 12dp pill radii on badges, 16dp on floating panels. */
    val PillShape = RoundedCornerShape(12.dp)
    val PanelShape = RoundedCornerShape(16.dp)
}

/**
 * PRD §5.2 asks for Plus Jakarta Sans (display) and Inter (body).
 *
 * Neither is bundled: shipping them means committing binary font files whose licence must
 * travel with them, and downloadable-fonts needs Play Services at runtime, which would
 * break the offline-first promise in exactly the place the app is used. The scale, weights
 * and hierarchy below are the part that carries the design intent, so they are honoured
 * exactly against the platform grotesk. Swapping in the real faces later is a one-file
 * change here. Recorded as open in EINCOL_REPORT.md.
 */
private val Display = FontFamily.SansSerif
private val Body = FontFamily.SansSerif

private val GenTypography = Typography(
    // 32pt hero status numbers
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp),
    // 20pt card titles
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    // 16pt primary actions
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp),
    // 14pt body
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    // 12pt quiet labels
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
)

private val GenColors = darkColorScheme(
    primary = Gen.Primary,
    onPrimary = Gen.Base,
    secondary = Gen.TextSecondary,
    onSecondary = Gen.Base,
    background = Gen.Base,
    onBackground = Gen.TextPrimary,
    surface = Gen.Surface,
    onSurface = Gen.TextPrimary,
    surfaceVariant = Gen.Surface,
    onSurfaceVariant = Gen.TextSecondary,
    error = Gen.Alert,
    onError = Gen.Base,
    outline = Gen.Hairline,
)

/**
 * Dark-only, deliberately. The app is used at dusk under canopy; a light theme there
 * destroys night vision. [isSystemInDarkTheme] is intentionally not consulted.
 */
@Composable
fun GensingoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GenColors, typography = GenTypography, content = content)
}
