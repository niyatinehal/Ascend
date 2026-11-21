package app.levelup2027

import app.levelup2027.ai.ChangeOp
import app.levelup2027.ai.ChangeTypes
import app.levelup2027.ai.ChangeValidator
import app.levelup2027.ai.DraftTask
import app.levelup2027.ai.PlanChange
import app.levelup2027.ai.PlanState
import app.levelup2027.data.Capacity
import app.levelup2027.data.Completion
import app.levelup2027.data.CompletionType
import app.levelup2027.data.Goal
import app.levelup2027.data.GoalStatus
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.PresetKind
import app.levelup2027.domain.RoutineSettings
import app.levelup2027.ai.DraftBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChangeValidatorTest {
    private val today = LocalDate.of(2026, 11, 4) // Wednesday
    private val gym = task("gym", rule = "WEEKLY:MON,WED,FRI")
    private val call = task("call", date = today.plusDays(1))
    private val goal = Goal("g1", "Get fit", "", "", today.plusMonths(4), "", "")
    private val done = Completion(taskId = "gym", taskTitle = "gym", scheduledDate = today.minusDays(2), completionType = CompletionType.IDEAL, completedAt = 0)
    private val state = PlanState(today, listOf(gym, call), listOf(done), listOf(goal), Capacity(60, 90))
    private fun v(vararg ops: ChangeOp) = ChangeValidator.validate(ops.toList(), state) { "new-$it" }

    @Test fun `simple changes are accepted and marked simple`() {
        val r = v(
            ChangeOp(ChangeTypes.RESCHEDULE, taskId = "gym", date = today.toString(), newDate = today.plusDays(3).toString()),
            ChangeOp(ChangeTypes.USE_MINIMUM, taskId = "call", date = today.plusDays(1).toString()),
            ChangeOp(ChangeTypes.SET_LOW_ENERGY, date = today.toString()),
        )
        assertEquals(3, r.changes.size)
        assertTrue(r.allSimple)
        assertTrue(r.rejected.isEmpty())
    }

    @Test fun `history is never changed and unknown ids are rejected`() {
        val r = v(
            ChangeOp(ChangeTypes.RESCHEDULE, taskId = "gym", date = today.minusDays(2).toString(), newDate = today.toString()),
            ChangeOp(ChangeTypes.RESCHEDULE, taskId = "nope", date = today.toString(), newDate = today.plusDays(1).toString()),
            ChangeOp(ChangeTypes.RESCHEDULE, taskId = "gym", date = today.plusDays(1).toString(), newDate = today.plusDays(2).toString()),
            ChangeOp(ChangeTypes.UPDATE_GOAL, goalId = "ghost", goalStatus = "paused"),
            ChangeOp("DELETE_EVERYTHING"),
        )
        assertTrue(r.changes.isEmpty())
        assertEquals(5, r.rejected.size)
    }

    @Test fun `dates outside the horizon or in the past are rejected`() {
        val r = v(
            ChangeOp(ChangeTypes.RESCHEDULE, taskId = "gym", date = today.toString(), newDate = today.plusDays(90).toString()),
            ChangeOp(ChangeTypes.RESCHEDULE, taskId = "gym", date = today.toString(), newDate = today.minusDays(1).toString()),
            ChangeOp(ChangeTypes.SKIP_OCCURRENCE, taskId = "call", date = today.plusDays(1).toString()),
        )
        assertTrue(r.changes.isEmpty())
    }

    @Test fun `goal, deadline and habit-schedule changes are major`() {
        val r = v(
            ChangeOp(ChangeTypes.UPDATE_GOAL, goalId = "g1", goalStatus = "paused", goalDeadline = today.plusMonths(6).toString()),
            ChangeOp(ChangeTypes.UPDATE_TASK, taskId = "gym", days = listOf("SAT")),
            ChangeOp(ChangeTypes.DISABLE_TASK, taskId = "call"),
        )
        assertEquals(3, r.changes.size)
        assertTrue(r.changes.all { it.major })
        assertFalse(r.allSimple)
        val g = r.changes.filterIsInstance<PlanChange.UpdateGoal>().single()
        assertEquals(GoalStatus.PAUSED, g.after.status)
        assertEquals("WEEKLY:SAT", r.changes.filterIsInstance<PlanChange.UpdateTask>().single().after.recurrenceRule)
    }

    @Test fun `task edits are clamped and no-op edits rejected`() {
        val r = v(
            ChangeOp(ChangeTypes.UPDATE_TASK, taskId = "call", idealMinutes = 999, minimumMinutes = 500, timeSlot = "MORNING"),
            ChangeOp(ChangeTypes.UPDATE_TASK, taskId = "gym"),
        )
        val u = r.changes.single() as PlanChange.UpdateTask
        assertEquals(240, u.after.estimatedIdealMinutes)
        assertEquals(240, u.after.estimatedMinimumMinutes)
        assertEquals(TimeSlot.MORNING, u.after.timeSlot)
        assertFalse(u.major)
        assertEquals(1, r.rejected.size)
    }

    @Test fun `new tasks get validated and duplicates are refused`() {
        val r = v(
            ChangeOp(ChangeTypes.ADD_TASK, newTask = DraftTask("x", "Stretch", goalKey = "g1", days = listOf("TUE"), ideal = "10 min stretch", idealMinutes = 10, minimum = "1 stretch", minimumMinutes = 2)),
            ChangeOp(ChangeTypes.ADD_TASK, newTask = DraftTask("y", "call", date = today.plusDays(1).toString(), ideal = "i", idealMinutes = 5)),
            ChangeOp(ChangeTypes.ADD_TASK, newTask = DraftTask("z", "Someday", date = today.plusDays(200).toString())),
        )
        val add = r.changes.single() as PlanChange.AddTask
        assertEquals("new-Stretch", add.task.id)
        assertEquals("WEEKLY:TUE", add.task.recurrenceRule)
        assertEquals("g1", add.task.goalId)
        assertTrue(add.major) // a new habit changes the routine
        assertEquals(2, r.rejected.size)
    }

    @Test fun `one change per occurrence`() {
        val r = v(
            ChangeOp(ChangeTypes.RESCHEDULE, taskId = "gym", date = today.toString(), newDate = today.plusDays(1).toString()),
            ChangeOp(ChangeTypes.USE_MINIMUM, taskId = "gym", date = today.toString()),
        )
        assertEquals(1, r.changes.size)
    }

    // ---- assistant change types ----
    private val routine = RoutineSettings(
        presets = mapOf(PresetKind.DAILY to listOf(block("05:00", "07:00", "Gym"), block("09:00", "10:00", "Read")), PresetKind.LAZY to emptyList()),
        activeKind = PresetKind.DAILY, activeDate = today,
    )
    private val rState = state.copy(
        tasks = listOf(gym, call, task("daily", rule = "WEEKLY:MON,TUE,WED,THU,FRI,SAT,SUN"), task("skipme", rule = "WEEKLY:MON,TUE,WED,THU,FRI,SAT,SUN")),
        completions = listOf(
            Completion(taskId = "gym", taskTitle = "gym", scheduledDate = today, completionType = CompletionType.IDEAL, completedAt = 0),
            Completion(taskId = "skipme", taskTitle = "skipme", scheduledDate = today, completionType = CompletionType.SKIPPED, completedAt = 0),
        ),
        routine = routine,
    )
    private fun rv(vararg ops: ChangeOp) = ChangeValidator.validate(ops.toList(), rState) { "new-$it" }
    private fun blocks(vararg b: Triple<String, String, String>) = b.map { DraftBlock(it.first, it.second, it.third) }
    private val okBlocks = blocks(Triple("05:00", "07:00", "Gym"))

    @Test fun `preset names are understood`() {
        for ((name, kind) in listOf("lazy" to PresetKind.LAZY, "Lazy Day" to PresetKind.LAZY, "PRODUCTIVE" to PresetKind.PRODUCTIVE, "Extreme Productive" to PresetKind.PRODUCTIVE)) {
            val c = rv(ChangeOp(ChangeTypes.SET_PRESET, preset = name)).changes.single() as PlanChange.SetPreset
            assertEquals(kind, c.kind)
        }
        assertEquals(1, rv(ChangeOp(ChangeTypes.SET_PRESET, preset = "weekend")).rejected.size)
    }

    @Test fun `switching to the current preset is rejected`() {
        val r = rv(ChangeOp(ChangeTypes.SET_PRESET, preset = "daily"))
        assertTrue(r.changes.isEmpty()); assertEquals(1, r.rejected.size)
    }

    @Test fun `today blocks default to today's preset`() {
        val c = rv(ChangeOp(ChangeTypes.SET_TODAY_BLOCKS, blocks = okBlocks)).changes.single() as PlanChange.SetTodayBlocks
        assertEquals(PresetKind.DAILY, c.kind)
        assertEquals(listOf(block("05:00", "07:00", "Gym")), c.blocks)
        assertEquals("Today only (Daily Routine): 5:00–7:00 Gym", c.summary)
    }

    @Test fun `today blocks follow a preset switch in the same request`() {
        val r = rv(ChangeOp(ChangeTypes.SET_PRESET, preset = "lazy"), ChangeOp(ChangeTypes.SET_TODAY_BLOCKS, blocks = okBlocks))
        assertEquals(PresetKind.LAZY, (r.changes[1] as PlanChange.SetTodayBlocks).kind)
    }

    @Test fun `invalid blocks are rejected with the reason`() {
        val overlap = blocks(Triple("05:00", "07:00", "Gym"), Triple("06:00", "08:00", "Run"))
        val r = rv(
            ChangeOp(ChangeTypes.SET_TODAY_BLOCKS, blocks = overlap),
            ChangeOp(ChangeTypes.SET_PRESET_BLOCKS, preset = "daily", blocks = blocks(Triple("9am", "10:00", "Read"))),
            ChangeOp(ChangeTypes.SET_PRESET, preset = "lazy"),
        )
        assertEquals(1, r.changes.size)
        assertEquals(2, r.rejected.size)
        assertTrue(r.rejected[0], r.rejected[0].contains("overlap"))
        assertTrue(r.rejected[1], r.rejected[1].contains("Read"))
    }

    @Test fun `empty today blocks clear the day`() {
        val c = rv(ChangeOp(ChangeTypes.SET_TODAY_BLOCKS)).changes.single() as PlanChange.SetTodayBlocks
        assertTrue(c.blocks.isEmpty())
    }

    @Test fun `permanent blocks need a preset`() {
        val r = rv(ChangeOp(ChangeTypes.SET_PRESET_BLOCKS, blocks = okBlocks))
        assertTrue(r.changes.isEmpty()); assertEquals(1, r.rejected.size)
        val ok = rv(ChangeOp(ChangeTypes.SET_PRESET_BLOCKS, preset = "productive", blocks = okBlocks)).changes.single() as PlanChange.SetPresetBlocks
        assertEquals("Extreme Productive, from now on: 5:00–7:00 Gym", ok.summary)
    }

    @Test fun `the later block change for a preset wins`() {
        val a = blocks(Triple("05:00", "06:00", "A")); val b = blocks(Triple("06:00", "07:00", "B"))
        val r = rv(
            ChangeOp(ChangeTypes.SET_PRESET_BLOCKS, preset = "daily", blocks = a),
            ChangeOp(ChangeTypes.SET_PRESET_BLOCKS, preset = "daily", blocks = b),
            ChangeOp(ChangeTypes.SET_TODAY_BLOCKS, blocks = a),
            ChangeOp(ChangeTypes.SET_TODAY_BLOCKS, blocks = b),
        )
        assertEquals(2, r.changes.size)
        assertEquals("B", (r.changes.filterIsInstance<PlanChange.SetPresetBlocks>().single()).blocks.single().label)
        assertEquals("B", (r.changes.filterIsInstance<PlanChange.SetTodayBlocks>().single()).blocks.single().label)
    }

    @Test fun `completing an already-done task is rejected`() {
        val r = rv(ChangeOp(ChangeTypes.COMPLETE_TASK, taskId = "gym", date = today.toString()))
        assertTrue(r.changes.isEmpty()); assertTrue(r.rejected.single().contains("already done"))
    }

    @Test fun `a skipped task can be completed`() {
        val c = rv(ChangeOp(ChangeTypes.COMPLETE_TASK, taskId = "skipme", completion = "minimum")).changes.single() as PlanChange.CompleteTask
        assertEquals(CompletionType.MINIMUM, c.type)
        assertEquals("Mark \"skipme\" done (minimum)", c.summary)
        val d = rv(ChangeOp(ChangeTypes.COMPLETE_TASK, taskId = "skipme")).changes.single() as PlanChange.CompleteTask
        assertEquals(null, d.type)
        assertEquals("Mark \"skipme\" done", d.summary)
    }

    @Test fun `completing a task not scheduled today is rejected`() {
        val r = rv(ChangeOp(ChangeTypes.COMPLETE_TASK, taskId = "call"), ChangeOp(ChangeTypes.COMPLETE_TASK, taskId = "nope"))
        assertTrue(r.changes.isEmpty()); assertEquals(2, r.rejected.size)
    }

    @Test fun `only one completion per task`() {
        val r = rv(ChangeOp(ChangeTypes.COMPLETE_TASK, taskId = "daily"), ChangeOp(ChangeTypes.COMPLETE_TASK, taskId = "daily", completion = "MINIMUM"))
        assertEquals(1, r.changes.size)
    }

    @Test fun `new change types always need approval`() {
        val r = rv(
            ChangeOp(ChangeTypes.SET_PRESET, preset = "lazy"),
            ChangeOp(ChangeTypes.SET_TODAY_BLOCKS, blocks = okBlocks),
            ChangeOp(ChangeTypes.SET_PRESET_BLOCKS, preset = "daily", blocks = okBlocks),
            ChangeOp(ChangeTypes.COMPLETE_TASK, taskId = "daily"),
        )
        assertEquals(4, r.changes.size)
        assertTrue(r.changes.all { it.major })
        assertFalse(r.allSimple)
    }
}
