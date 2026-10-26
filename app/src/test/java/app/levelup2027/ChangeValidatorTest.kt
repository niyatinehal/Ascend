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
}
