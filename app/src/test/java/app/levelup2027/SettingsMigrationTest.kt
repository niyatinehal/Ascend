package app.levelup2027

import app.levelup2027.data.AppSettings
import app.levelup2027.data.SettingsJson
import app.levelup2027.data.TodayLayout
import app.levelup2027.domain.RoutineSettings
import app.levelup2027.domain.RoutineSetup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMigrationTest {
    @Test fun `upgrading from 2_2 hides the Today filter chips once`() {
        val v3 = AppSettings(settingsVersion = 3, todayLayout = TodayLayout(showFilters = true))

        val migrated = v3.migrated()

        assertFalse(migrated.todayLayout.showFilters)
        assertEquals(AppSettings.CURRENT_VERSION, migrated.settingsVersion)
    }

    @Test fun `filter chips turned back on after upgrading stay on`() {
        val current = AppSettings(settingsVersion = AppSettings.CURRENT_VERSION, todayLayout = TodayLayout(showFilters = true))

        assertTrue(current.migrated().todayLayout.showFilters)
    }

    @Test fun `a fresh install starts without filter chips`() {
        assertFalse(AppSettings().migrated().todayLayout.showFilters)
    }

    @Test fun `a fresh install must set up its routine`() {
        assertEquals(RoutineSetup.REQUIRED, AppSettings().migrated().routine.setup)
    }

    @Test fun `upgrading an onboarded 2_3 user gets the prompt`() {
        assertEquals(RoutineSetup.PROMPT, AppSettings(settingsVersion = 4, onboardingDone = true).migrated().routine.setup)
    }

    @Test fun `upgrading a v1 user gets the prompt`() {
        assertEquals(RoutineSetup.PROMPT, AppSettings(settingsVersion = 0, seedVersion = 1).migrated().routine.setup)
    }

    @Test fun `restored 2_3 settings get the prompt, not the forced setup`() {
        val full = Json.parseToJsonElement(SettingsJson.encodeToString(AppSettings.serializer(), AppSettings(settingsVersion = 4, onboardingDone = true))).jsonObject
        val v23File = JsonObject(full - "routine").toString() // a 2.3 file has no "routine"

        val restored = SettingsJson.decodeFromString(AppSettings.serializer(), v23File).migrated()

        assertEquals(RoutineSetup.PROMPT, restored.routine.setup)
    }

    @Test fun `already migrated settings are untouched`() {
        val s = AppSettings(settingsVersion = AppSettings.CURRENT_VERSION, routine = RoutineSettings(setup = RoutineSetup.DONE))
        assertEquals(RoutineSetup.DONE, s.migrated().routine.setup)
    }
}
