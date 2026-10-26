package app.levelup2027

import app.levelup2027.ai.PromptTemplates
import app.levelup2027.data.AiMode
import app.levelup2027.data.AiSettings
import app.levelup2027.data.AppSettings
import app.levelup2027.data.SettingsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplatesTest {
    private val fit = PromptTemplates.DEFAULTS.first { it.id == "fitness" }
    private val lang = PromptTemplates.DEFAULTS.first { it.id == "language" }

    @Test fun `several areas combine into one description and can be removed again`() {
        var text = PromptTemplates.toggle("", fit, true)
        text = PromptTemplates.toggle(text, lang, true)
        assertEquals(fit.text + "\n\n" + lang.text, text)
        assertEquals(text, PromptTemplates.toggle(text, fit, true)) // no duplicates
        assertEquals(lang.text, PromptTemplates.toggle(text, fit, false))
    }

    @Test fun `edited paragraphs and the user's own text are never removed`() {
        val edited = "I work nights.\n\n" + fit.text.replace("3 months", "6 months")
        assertEquals(edited, PromptTemplates.toggle(edited, fit, false))
    }

    @Test fun `defaults are used until customised, and every default is usable`() {
        assertEquals(PromptTemplates.DEFAULTS, PromptTemplates.effective(null))
        assertTrue(PromptTemplates.DEFAULTS.all { it.text.length > 40 && it.label.isNotBlank() })
        assertEquals(PromptTemplates.DEFAULTS.size, PromptTemplates.DEFAULTS.map { it.id }.distinct().size)
        assertEquals("c-new-area", PromptTemplates.newId("New area", PromptTemplates.DEFAULTS))
    }

    @Test fun `2_0 settings without a server move to Gemini and ask for consent again`() {
        val v2 = SettingsJson.decodeFromString(AppSettings.serializer(),
            """{"settingsVersion":2,"onboardingDone":true,"ai":{"mode":"SERVER","serverUrl":"","consentGiven":true}}""")
        val m = v2.migrated()
        assertEquals(AiMode.GEMINI, m.ai.mode)
        assertEquals(false, m.ai.consentGiven)
        assertEquals(AppSettings.CURRENT_VERSION, m.settingsVersion)
        val withServer = v2.copy(ai = AiSettings(mode = AiMode.SERVER, serverUrl = "https://x", consentGiven = true)).migrated()
        assertEquals(AiMode.SERVER, withServer.ai.mode)
        assertTrue(withServer.ai.consentGiven)
    }
}
