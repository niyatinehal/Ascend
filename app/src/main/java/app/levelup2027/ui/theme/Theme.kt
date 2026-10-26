package app.levelup2027.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.levelup2027.data.CategoryEntity
import app.levelup2027.data.ThemeChoice
import app.levelup2027.data.TimeSlot

object Palette {
    val Gold = Color(0xFFE8B44C)       // ideal / accent
    val GoldDeep = Color(0xFF9A6B00)
    val Teal = Color(0xFF5DBBA6)       // minimum
    val TealDeep = Color(0xFF2F7F6F)
    val Bg = Color(0xFF0F1115)
    val Surface = Color(0xFF171A21)
    val SurfaceHigh = Color(0xFF1F232C)
    val Outline = Color(0xFF2E3440)
    val Text = Color(0xFFE6E8EE)
    val TextMuted = Color(0xFFA3A9B7)
}

/** Category colours by palette position; chosen to stay distinct on both dark and light backgrounds. */
val CategoryPalette = listOf(
    Color(0xFF7FA7E8), Color(0xFFA58BE0), Color(0xFFE07A7A), Color(0xFF6EC3D6),
    Color(0xFFE3A15A), Color(0xFF6CC59A), Color(0xFFE58FB8), Color(0xFFD9C26A),
    Color(0xFF8FB3A0), Color(0xFFB79C86), Color(0xFF9BC46A), Color(0xFF9AA4B2),
)

fun categoryColor(index: Int): Color = CategoryPalette[Math.floorMod(index, CategoryPalette.size)]

/** Keys match [app.levelup2027.data.CategoryIcons.KEYS]; unknown keys fall back to a label icon. */
fun categoryIcon(key: String): ImageVector = when (key) {
    "work" -> Icons.Outlined.Work
    "school" -> Icons.Outlined.School
    "fitness" -> Icons.Outlined.FitnessCenter
    "wellness" -> Icons.Outlined.SelfImprovement
    "store" -> Icons.Outlined.Storefront
    "savings" -> Icons.Outlined.Savings
    "family" -> Icons.Outlined.FamilyRestroom
    "people" -> Icons.Outlined.People
    "build" -> Icons.Outlined.Build
    "home" -> Icons.Outlined.Home
    "growth" -> Icons.Outlined.TrendingUp
    "planning" -> Icons.Outlined.EventNote
    "code" -> Icons.Outlined.Code
    "language" -> Icons.Outlined.Translate
    "music" -> Icons.Outlined.MusicNote
    "art" -> Icons.Outlined.Brush
    "book" -> Icons.Outlined.MenuBook
    "travel" -> Icons.Outlined.Flight
    "heart" -> Icons.Outlined.FavoriteBorder
    "pets" -> Icons.Outlined.Pets
    "food" -> Icons.Outlined.Restaurant
    "sleep" -> Icons.Outlined.Bedtime
    "mind" -> Icons.Outlined.Psychology
    else -> Icons.Outlined.Label
}

val CategoryEntity.color: Color get() = categoryColor(colorIndex)
val CategoryEntity.icon: ImageVector get() = categoryIcon(iconKey)

val TimeSlot.icon: ImageVector
    get() = when (this) {
        TimeSlot.MORNING -> Icons.Outlined.WbSunny
        TimeSlot.WORK -> Icons.Outlined.WorkOutline
        TimeSlot.EVENING -> Icons.Outlined.NightsStay
        TimeSlot.FLEXIBLE -> Icons.Outlined.Schedule
    }

private val DarkColors = darkColorScheme(
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

private val LightColors = lightColorScheme(
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

private val AppTypography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        labelSmall = labelSmall.copy(letterSpacing = 0.4.sp),
    )
}

val OverlineStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)

@Composable
fun LevelUpTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
}

/** Ideal = accent gold, minimum = teal; used consistently across cards, badges and charts. */
object ModeColors {
    @Composable fun ideal() = MaterialTheme.colorScheme.primary
    @Composable fun minimum() = MaterialTheme.colorScheme.secondary
}
