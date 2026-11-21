package app.levelup2027

import app.levelup2027.data.CompletionType
import app.levelup2027.data.Repository
import app.levelup2027.domain.PresetKind
import app.levelup2027.domain.Routine
import app.levelup2027.domain.RoutineSetup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RepositoryRoutineTest {
    private val today = LocalDate.of(2026, 11, 21)

    @Test fun `saving an invalid preset stores nothing and returns errors`() = runTest {
        val env = TestEnv()
        val errors = env.repo.savePreset(PresetKind.DAILY, listOf(block("05:00", "07:00", "Gym"), block("06:00", "08:00", "Run")))
        assertTrue(errors.isNotEmpty())
        assertTrue(env.settings.current().routine.presets.isEmpty())
    }

    @Test fun `saving a preset stores it sorted and trimmed`() = runTest {
        val env = TestEnv()
        val errors = env.repo.savePreset(PresetKind.DAILY, listOf(block("09:00", "10:00", " Read "), block("05:00", "07:00", "Gym")))
        assertEquals(emptyList<String>(), errors)
        assertEquals(listOf("Gym", "Read"), env.settings.current().routine.presets[PresetKind.DAILY]!!.map { it.label })
    }

    @Test fun `choosing Lazy Day turns on low energy for today`() = runTest {
        val env = TestEnv()
        env.repo.setPreset(today, PresetKind.LAZY)
        val s = env.settings.current()
        assertEquals(PresetKind.LAZY, Routine.activeKind(s.routine, today))
        assertTrue(s.isLowEnergy(today))
    }

    @Test fun `leaving Lazy Day turns low energy off`() = runTest {
        val env = TestEnv()
        env.repo.setPreset(today, PresetKind.LAZY)
        env.repo.setPreset(today, PresetKind.DAILY)
        assertFalse(env.settings.current().isLowEnergy(today))
    }

    @Test fun `switching between Daily and Productive leaves low energy alone`() = runTest {
        val env = TestEnv()
        env.repo.setLowEnergy(today, true)
        env.repo.setPreset(today, PresetKind.PRODUCTIVE)
        env.repo.setPreset(today, PresetKind.DAILY)
        assertTrue(env.settings.current().isLowEnergy(today))
    }

    @Test fun `yesterday's Lazy Day does not turn off today's low energy`() = runTest {
        val env = TestEnv()
        env.repo.setPreset(today.minusDays(1), PresetKind.LAZY)
        env.repo.setLowEnergy(today, true)
        env.repo.setPreset(today, PresetKind.PRODUCTIVE)
        assertTrue(env.settings.current().isLowEnergy(today))
    }

    @Test fun `low-energy toggle is independent of the preset`() = runTest {
        val env = TestEnv()
        env.repo.setPreset(today, PresetKind.LAZY)
        env.repo.setLowEnergy(today, false)
        assertEquals(PresetKind.LAZY, Routine.activeKind(env.settings.current().routine, today))
        assertFalse(env.settings.current().isLowEnergy(today))
        env.repo.setPreset(today, PresetKind.DAILY)
        assertFalse(env.settings.current().isLowEnergy(today))
    }

    @Test fun `saving a preset or switching reports a settings change`() = runTest {
        val env = TestEnv()
        env.repo.savePreset(PresetKind.DAILY, listOf(block("05:00", "07:00", "Gym")))
        assertTrue(Repository.ChangeKind.SETTINGS in env.changes)
        env.changes.clear()
        env.repo.setPreset(today, PresetKind.PRODUCTIVE)
        assertTrue(Repository.ChangeKind.SETTINGS in env.changes)
    }

    @Test fun `setup state and prompt dismissal are stored`() = runTest {
        val env = TestEnv()
        env.repo.setRoutineSetup(RoutineSetup.DONE)
        env.repo.dismissRoutinePrompt()
        val r = env.settings.current().routine
        assertEquals(RoutineSetup.DONE, r.setup)
        assertTrue(r.promptDismissed)
    }

    @Test fun `toggle completes minimum on low-energy day and undoes on second tap`() = runTest {
        val env = TestEnv()
        env.tasks.upsert(task("gym", rule = "DAILY"))
        env.repo.setLowEnergy(today, true)
        assertEquals(CompletionType.MINIMUM, env.repo.toggle("gym", today, minimumByDefault = false))
        assertNull(env.repo.toggle("gym", today, minimumByDefault = false))
        assertNull(env.completions.get("gym", today))
    }

    @Test fun `toggle follows minimum-by-default`() = runTest {
        val env = TestEnv()
        env.tasks.upsert(task("gym", rule = "DAILY"))
        assertEquals(CompletionType.MINIMUM, env.repo.toggle("gym", today, minimumByDefault = true))
        env.repo.toggle("gym", today, minimumByDefault = true)
        assertEquals(CompletionType.IDEAL, env.repo.toggle("gym", today, minimumByDefault = false))
    }
}
