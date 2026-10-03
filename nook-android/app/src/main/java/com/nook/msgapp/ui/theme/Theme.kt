package com.nook.msgapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nook.core.Format
import com.nook.msgapp.R

/** NOOK design tokens. Every colour, size and duration used by the UI lives here. */
object Palette {
    val Black = Color(0xFF000000)
    val Charcoal = Color(0xFF1E1B18)
    val CharcoalRaised = Color(0xFF2A2622)
    val Border = Color(0xFF2E2A26)
    val Cream = Color(0xFFF5EFE6)
    val CreamBg = Color(0xFFF7F2EA)
    val CreamSurface = Color(0xFFFFFFFF)
    val CreamRaised = Color(0xFFEDE5D8)
    val CreamBorder = Color(0xFFE3DACB)
    val Muted = Color(0xFF9A938A)
    val MutedLight = Color(0xFF7A7268)
    val Orange = Color(0xFFFF6A33)
    val Amber = Color(0xFFE8B04B)
    val Coral = Color(0xFFF26B5E)
    val Mint = Color(0xFFCDEFD9)
    val Sky = Color(0xFFD4E6FB)
    val Peach = Color(0xFFFBE3C0)
    val Lavender = Color(0xFFDDD0F5)
    val Rose = Color(0xFFF6C9C4)
    val Navy = Color(0xFF1F3A52)
    val Online = Color(0xFF5BD68A)
    val Ink = Color(0xFF1A1714)
}

@Immutable
data class NookColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    /** Text drawn on top of `primary` (cream pill buttons, my bubbles). */
    val onPrimary: Color,
    val primary: Color,
    val accent: Color,
    val info: Color,
    val danger: Color,
    val highlight: Color,
    val onHighlight: Color,
    val online: Color,
    val overlay: Color,
    val onPastel: Color = Palette.Ink,
) {
    val pastels = listOf(Palette.Mint, Palette.Sky, Palette.Peach, Palette.Lavender, Palette.Rose)

    /** Stable pastel per user id / name. */
    fun pastelFor(key: String): Color = pastels[Format.hashString(key) % pastels.size]
}

val DarkColors = NookColors(
    isDark = true,
    background = Palette.Black,
    surface = Palette.Charcoal,
    surfaceRaised = Palette.CharcoalRaised,
    border = Palette.Border,
    text = Palette.Cream,
    textMuted = Palette.Muted,
    onPrimary = Palette.Black,
    primary = Palette.Cream,
    accent = Palette.Orange,
    info = Palette.Amber,
    danger = Palette.Coral,
    highlight = Palette.Navy,
    onHighlight = Palette.Cream,
    online = Palette.Online,
    overlay = Color(0xB8000000),
)

val LightColors = NookColors(
    isDark = false,
    background = Palette.CreamBg,
    surface = Palette.CreamSurface,
    surfaceRaised = Palette.CreamRaised,
    border = Palette.CreamBorder,
    text = Palette.Ink,
    textMuted = Palette.MutedLight,
    onPrimary = Palette.Cream,
    primary = Palette.Ink,
    accent = Palette.Orange,
    info = Color(0xFFC98F25),
    danger = Color(0xFFD9503F),
    highlight = Palette.Navy,
    onHighlight = Palette.Cream,
    online = Color(0xFF2FB566),
    overlay = Color(0x731A1714),
)

object Fonts {
    val Serif = FontFamily(
        Font(R.font.instrument_serif, FontWeight.Normal),
        Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
    )
    val Sans = FontFamily(
        Font(R.font.dm_sans_regular, FontWeight.Normal),
        Font(R.font.dm_sans_medium, FontWeight.Medium),
        Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
        Font(R.font.dm_sans_bold, FontWeight.Bold),
    )
}

/** Type scale (kept compact on purpose). */
object NookType {
    val display = TextStyle(fontFamily = Fonts.Serif, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-0.01).em)
    val title = TextStyle(fontFamily = Fonts.Serif, fontSize = 31.sp, lineHeight = 35.sp, letterSpacing = (-0.01).em)
    val headline = TextStyle(fontFamily = Fonts.Serif, fontSize = 24.sp, lineHeight = 28.sp)
    val subhead = TextStyle(fontFamily = Fonts.Serif, fontSize = 20.sp, lineHeight = 24.sp)
    val bodyLarge = TextStyle(fontFamily = Fonts.Sans, fontSize = 15.sp, lineHeight = 21.sp)
    val body = TextStyle(fontFamily = Fonts.Sans, fontSize = 14.sp, lineHeight = 19.sp)
    val bodyBold = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp)
    val label = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = Fonts.Sans, fontSize = 12.sp, lineHeight = 16.sp)
    val captionBold = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
    val micro = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, lineHeight = 13.sp, letterSpacing = 0.03.em)
}

object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object Radius {
    val sm = 12.dp
    val md = 16.dp
    val card = 20.dp
    val pill = 999.dp
}

object Motion {
    const val FAST = 140
    const val BASE = 220
    const val SLOW = 380
}

val LocalNookColors = staticCompositionLocalOf { DarkColors }

/** Short accessor: `Nook.colors.text`. */
object Nook {
    val colors: NookColors
        @Composable get() = LocalNookColors.current
}

enum class ThemeMode(val wire: String) {
    System("system"), Dark("dark"), Light("light");

    companion object {
        fun from(s: String?) = entries.firstOrNull { it.wire == s } ?: Dark
    }
}

private fun materialFrom(c: NookColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    // Every Material slot is mapped to a NOOK token, so no default blue/purple can leak through.
    return base.copy(
        primary = c.primary,
        onPrimary = c.onPrimary,
        primaryContainer = c.surfaceRaised,
        onPrimaryContainer = c.text,
        inversePrimary = c.accent,
        secondary = c.accent,
        onSecondary = Palette.Black,
        secondaryContainer = c.surfaceRaised,
        onSecondaryContainer = c.text,
        tertiary = c.info,
        onTertiary = Palette.Black,
        tertiaryContainer = c.surfaceRaised,
        onTertiaryContainer = c.text,
        background = c.background,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.surfaceRaised,
        onSurfaceVariant = c.textMuted,
        surfaceTint = Color.Transparent,
        inverseSurface = c.text,
        inverseOnSurface = c.background,
        error = c.danger,
        onError = Palette.Black,
        errorContainer = c.surfaceRaised,
        onErrorContainer = c.danger,
        outline = c.border,
        outlineVariant = c.border,
        scrim = c.overlay,
        surfaceBright = c.surfaceRaised,
        surfaceDim = c.background,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceRaised,
        surfaceContainerHighest = c.surfaceRaised,
        surfaceContainerLow = c.surface,
        surfaceContainerLowest = c.background,
    )
}

@Composable
fun NookTheme(mode: ThemeMode = ThemeMode.Dark, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val colors = if (dark) DarkColors else LightColors
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalNookColors provides colors,
        // Text size stays the same whatever the phone's font-size setting is.
        LocalDensity provides Density(density.density, fontScale = 1f),
    ) {
        MaterialTheme(colorScheme = materialFrom(colors), content = content)
    }
}
