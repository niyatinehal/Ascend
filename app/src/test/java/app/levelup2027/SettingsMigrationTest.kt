package app.levelup2027

import app.levelup2027.data.AppSettings
import app.levelup2027.data.TodayLayout
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
}
