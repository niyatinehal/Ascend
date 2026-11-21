package app.levelup2027

import app.levelup2027.ai.PlanChange
import app.levelup2027.data.AppSettings
import app.levelup2027.data.CompletionType
import app.levelup2027.data.DefaultMode
import app.levelup2027.data.OccurrenceOverride
import app.levelup2027.data.Repository
import app.levelup2027.domain.Dates
import app.levelup2027.domain.PresetKind
import app.levelup2027.domain.Routine
import app.levelup2027.domain.RoutineSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantChangesTest {
    // applyChanges reads the real clock, so these tests use the real today.
    private val today = Dates.today()
    private val daily = listOf(block("05:00", "07:00", "Gym"))
    private fun env(defaultMode: DefaultMode = DefaultMode.DECIDE) = TestEnv(
        AppSettings(defaultMode = defaultMode, routine = RoutineSettings(presets = mapOf(PresetKind.DAILY to daily))),
    )
    private val t = task("read", rule = "WEEKLY:MON,TUE,WED,THU,FRI,SAT,SUN")

    @Test fun `switching to Lazy through the assistant turns on minimums`() = runTest {
        val e = env()
        e.repo.applyChanges(listOf(PlanChange.SetPreset(PresetKind.LAZY, "tired")), "assistant", "I'm tired")
        val s = e.settings.current()
        assertEquals(PresetKind.LAZY, Routine.activeKind(s.routine, today))
        assertTrue(s.isLowEnergy(today))
        e.repo.applyChanges(listOf(PlanChange.SetPreset(PresetKind.DAILY, "better")), "assistant", "better")
        assertTrue(!e.settings.current().isLowEnergy(today))
    }

    @Test fun `today-only blocks don't change the saved preset`() = runTest {
        val e = env()
        val one = listOf(block("08:00", "09:00", "Walk"))
        e.repo.applyChanges(listOf(PlanChange.SetTodayBlocks(PresetKind.DAILY, one, "")), "assistant", "x")
        val r = e.settings.current().routine
        assertEquals(daily, r.presets[PresetKind.DAILY])
        assertEquals(today, r.todayOverride!!.date)
        assertEquals(one, Routine.blocksFor(r, today))
    }

    @Test fun `permanent blocks change the saved preset`() = runTest {
        val e = env()
        val one = listOf(block("09:00", "10:00", " Read "), block("08:00", "09:00", "Walk"))
        e.repo.applyChanges(listOf(PlanChange.SetPresetBlocks(PresetKind.DAILY, one, "")), "assistant", "x")
        val r = e.settings.current().routine
        assertEquals(listOf("Walk", "Read"), r.presets[PresetKind.DAILY]!!.map { it.label })
        assertNull(r.todayOverride)
    }

    @Test fun `completing uses minimum on a low-energy day and replaces a skip`() = runTest {
        val e = env()
        e.repo.setLowEnergy(today, true)
        e.repo.complete(t, today, CompletionType.SKIPPED)
        e.repo.applyChanges(listOf(PlanChange.CompleteTask(t, today, null, "")), "assistant", "did it")
        assertEquals(CompletionType.MINIMUM, e.completions.rows.single().completionType)
    }

    @Test fun `completing uses ideal by default and minimum by default mode or preference`() = runTest {
        val e = env()
        e.repo.applyChanges(listOf(PlanChange.CompleteTask(t, today, null, "")), "assistant", "x")
        assertEquals(CompletionType.IDEAL, e.completions.rows.single().completionType)

        val m = env(DefaultMode.MINIMUM)
        m.repo.applyChanges(listOf(PlanChange.CompleteTask(t, today, null, "")), "assistant", "x")
        assertEquals(CompletionType.MINIMUM, m.completions.rows.single().completionType)

        val p = env()
        p.plan.upsertOverride(OccurrenceOverride(t.id, today, true, "", 0))
        p.repo.applyChanges(listOf(PlanChange.CompleteTask(t, today, null, "")), "assistant", "x")
        assertEquals(CompletionType.MINIMUM, p.completions.rows.single().completionType)
    }

    @Test fun `completing as minimum when asked`() = runTest {
        val e = env()
        e.repo.applyChanges(listOf(PlanChange.CompleteTask(t, today, CompletionType.MINIMUM, "")), "assistant", "x")
        assertEquals(CompletionType.MINIMUM, e.completions.rows.single().completionType)
    }

    @Test fun `applied changes are logged and re-arm alarms`() = runTest {
        val e = env()
        e.repo.applyChanges(listOf(PlanChange.SetPreset(PresetKind.PRODUCTIVE, "")), "assistant", "go hard")
        val entry = e.plan.log.value.single()
        assertEquals("assistant", entry.source)
        assertEquals("Switch today to Extreme Productive", entry.detail)
        assertTrue(Repository.ChangeKind.SETTINGS in e.changes)
    }
}
