package app.levelup2027

import androidx.compose.ui.graphics.Color
import app.levelup2027.data.AppSettings
import app.levelup2027.data.SettingsJson
import app.levelup2027.data.ThemePreset
import app.levelup2027.ui.theme.ThemePresets
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresetsTest {
    @Test fun `gold matches today's colours`() {
        val d = ThemePresets.scheme(ThemePreset.GOLD, dark = true)
        assertEquals(Color(0xFFE8B44C), d.primary)
        assertEquals(Color(0xFF5DBBA6), d.secondary)
        assertEquals(Color(0xFF0F1115), d.background)
        assertEquals(Color(0xFF171A21), d.surfaceContainer)
        assertEquals(Color(0xFF1F232C), d.surfaceContainerHigh)
        assertEquals(Color(0xFFE6E8EE), d.onBackground)
        val l = ThemePresets.scheme(ThemePreset.GOLD, dark = false)
        assertEquals(Color(0xFF946600), l.primary)
        assertEquals(Color(0xFF2D7A6A), l.secondary)
        assertEquals(Color(0xFFF6F5F2), l.background)
        assertEquals(Color.White, l.surfaceContainer)
    }

    @Test fun `every preset is readable in both versions`() {
        for (p in ThemePreset.entries) for (dark in listOf(true, false)) {
            val s = ThemePresets.scheme(p, dark)
            val v = if (dark) "dark" else "light"
            val cp = ThemePresets.contrast(s.primary, s.background)
            val cs = ThemePresets.contrast(s.secondary, s.background)
            assertTrue("$p $v primary contrast $cp", cp >= 4.5)
            assertTrue("$p $v secondary contrast $cs", cs >= 4.5)
        }
    }

    @Test fun `ideal and minimum stay distinct`() {
        for (p in ThemePreset.entries) for (dark in listOf(true, false)) {
            val s = ThemePresets.scheme(p, dark)
            val h = ThemePresets.hueDistance(s.primary, s.secondary)
            assertTrue("$p ${if (dark) "dark" else "light"} hue distance $h", h >= 60f)
        }
    }

    @Test fun `presets look different from each other`() {
        val primaries = ThemePreset.entries.map { ThemePresets.scheme(it, true).primary }
        assertEquals(primaries.size, primaries.toSet().size)
        val lightPrimaries = ThemePreset.entries.map { ThemePresets.scheme(it, false).primary }
        assertEquals(lightPrimaries.size, lightPrimaries.toSet().size)
    }

    @Test fun `settings without a preset load as Gold`() {
        assertEquals(ThemePreset.GOLD, AppSettings().themePreset)
        val full = SettingsJson.parseToJsonElement(SettingsJson.encodeToString(AppSettings.serializer(), AppSettings(themePreset = ThemePreset.ROSE))).jsonObject
        assertTrue("themePreset" in full)
        val old = JsonObject(full - "themePreset")
        assertEquals(ThemePreset.GOLD, SettingsJson.decodeFromJsonElement(AppSettings.serializer(), old).themePreset)
    }

    @Test fun `widget colours come from the dark version`() {
        val s = ThemePresets.scheme(ThemePreset.OCEAN, true)
        val w = ThemePresets.widget(ThemePreset.OCEAN)
        assertEquals(s.primary, w.ideal)
        assertEquals(s.secondary, w.minimum)
        assertEquals(s.background, w.bg)
        assertEquals(s.surfaceContainerHigh, w.surfaceHigh)
        assertEquals(s.onBackground, w.text)
        assertEquals(s.onSurfaceVariant, w.muted)
        assertEquals(Color(0xFF1F232C), ThemePresets.widget(ThemePreset.GOLD).surfaceHigh)
    }
}
