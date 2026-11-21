package app.levelup2027.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import app.levelup2027.data.ThemePreset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The home-screen widget's colours; always the dark version of the chosen preset. */
data class WidgetColors(val bg: Color, val surfaceHigh: Color, val text: Color, val muted: Color, val ideal: Color, val minimum: Color)

// Gold: the original look, unchanged.
internal val GoldDark = darkColorScheme(
    primary = Palette.Gold,
    onPrimary = Color(0xFF1C1405),
    primaryContainer = Color(0xFF3A2C10),
    onPrimaryContainer = Color(0xFFF7DDA6),
    secondary = Palette.Teal,
    onSecondary = Color(0xFF00201A),
    secondaryContainer = Color(0xFF16352F),
    onSecondaryContainer = Color(0xFFB8EDE0),
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Bg,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextMuted,
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF262B35),
    outline = Color(0xFF454C5A),
    outlineVariant = Palette.Outline,
    error = Color(0xFFF2A0A0),
)

internal val GoldLight = lightColorScheme(
    primary = Palette.GoldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE7BC),
    onPrimaryContainer = Color(0xFF2B1F00),
    secondary = Palette.TealDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEEE6),
    onSecondaryContainer = Color(0xFF00201A),
    background = Color(0xFFF6F5F2),
    onBackground = Color(0xFF1B1C1F),
    surface = Color(0xFFF6F5F2),
    onSurface = Color(0xFF1B1C1F),
    surfaceVariant = Color(0xFFE9E7E2),
    onSurfaceVariant = Color(0xFF55585F),
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0EEEA),
    surfaceContainerHighest = Color(0xFFE6E3DD),
    outlineVariant = Color(0xFFDAD7D0),
)


/** A preset's two accents for one version. Ideal = primary, minimum = secondary. */
private class Accents(val primary: Long, val secondary: Long)

private class PresetColors(val dark: Accents, val light: Accents)

object ThemePresets {
    private val presets = mapOf(
        ThemePreset.OCEAN to PresetColors(Accents(0xFF6FA8F0, 0xFFE9A06B), Accents(0xFF2B5EA8, 0xFF914F17)),
        ThemePreset.FOREST to PresetColors(Accents(0xFF7CC47F, 0xFFB7A0E8), Accents(0xFF2C6D30, 0xFF694EAE)),
        ThemePreset.ROSE to PresetColors(Accents(0xFFEC8FAE, 0xFF7CC9B0), Accents(0xFFA73565, 0xFF286B56)),
        ThemePreset.LAVENDER to PresetColors(Accents(0xFFB59BF2, 0xFFE8C46A), Accents(0xFF684ABE, 0xFF7E5900)),
        ThemePreset.EMBER to PresetColors(Accents(0xFFF08A5D, 0xFF6FB8E0), Accents(0xFFA14319, 0xFF22668C)),
    )

    fun scheme(preset: ThemePreset, dark: Boolean): ColorScheme {
        val p = presets[preset] ?: return if (dark) GoldDark else GoldLight
        return if (dark) darkFrom(p.dark) else lightFrom(p.light)
    }

    fun widget(preset: ThemePreset): WidgetColors = scheme(preset, dark = true).let {
        WidgetColors(it.background, it.surfaceContainerHigh, it.onBackground, it.onSurfaceVariant, it.primary, it.secondary)
    }

    /** WCAG contrast ratio, 1..21. */
    fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Distance between two colours' hues in degrees, 0..180. */
    fun hueDistance(a: Color, b: Color): Float {
        val d = abs(hue(a) - hue(b))
        return min(d, 360f - d)
    }

    private fun hue(c: Color): Float {
        val mx = max(c.red, max(c.green, c.blue))
        val mn = min(c.red, min(c.green, c.blue))
        val d = mx - mn
        if (d == 0f) return 0f
        val h = when (mx) {
            c.red -> ((c.green - c.blue) / d).mod(6f)
            c.green -> (c.blue - c.red) / d + 2f
            else -> (c.red - c.green) / d + 4f
        }
        return h * 60f
    }

    // Surfaces are Gold's, lightly tinted with the preset's primary.
    private fun tint(base: Color, accent: Color, amount: Float) = lerp(base, accent, amount)

    private fun darkFrom(a: Accents): ColorScheme {
        val primary = Color(a.primary)
        val secondary = Color(a.secondary)
        val g = GoldDark
        fun t(c: Color) = tint(c, primary, 0.04f)
        return g.copy(
            primary = primary,
            onPrimary = lerp(primary, Color.Black, 0.85f),
            primaryContainer = lerp(g.background, primary, 0.22f),
            onPrimaryContainer = lerp(primary, Color.White, 0.6f),
            secondary = secondary,
            onSecondary = lerp(secondary, Color.Black, 0.85f),
            secondaryContainer = lerp(g.background, secondary, 0.22f),
            onSecondaryContainer = lerp(secondary, Color.White, 0.6f),
            background = t(g.background),
            surface = t(g.surface),
            surfaceVariant = t(g.surfaceVariant),
            surfaceContainerLowest = t(g.surfaceContainerLowest),
            surfaceContainerLow = t(g.surfaceContainerLow),
            surfaceContainer = t(g.surfaceContainer),
            surfaceContainerHigh = t(g.surfaceContainerHigh),
            surfaceContainerHighest = t(g.surfaceContainerHighest),
            outlineVariant = t(g.outlineVariant),
            surfaceTint = primary,
            inversePrimary = lerp(primary, Color.Black, 0.4f),
        )
    }

    private fun lightFrom(a: Accents): ColorScheme {
        val primary = Color(a.primary)
        val secondary = Color(a.secondary)
        val g = GoldLight
        fun t(c: Color) = tint(c, primary, 0.03f)
        return g.copy(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = lerp(Color.White, primary, 0.2f),
            onPrimaryContainer = lerp(primary, Color.Black, 0.6f),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = lerp(Color.White, secondary, 0.2f),
            onSecondaryContainer = lerp(secondary, Color.Black, 0.6f),
            background = t(g.background),
            surface = t(g.surface),
            surfaceVariant = t(g.surfaceVariant),
            surfaceContainerLow = t(g.surfaceContainerLow),
            surfaceContainer = t(g.surfaceContainer),
            surfaceContainerHigh = t(g.surfaceContainerHigh),
            surfaceContainerHighest = t(g.surfaceContainerHighest),
            outlineVariant = t(g.outlineVariant),
            surfaceTint = primary,
            inversePrimary = lerp(primary, Color.White, 0.4f),
        )
    }
}
