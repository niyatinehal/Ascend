package app.levelup2027

import app.levelup2027.data.Completion
import app.levelup2027.data.CompletionType
import app.levelup2027.data.Goal
import app.levelup2027.data.GoalStatus
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.DailyPlanner
import app.levelup2027.domain.MissedAction
import app.levelup2027.domain.MissedTaskTriage
import app.levelup2027.domain.ScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DailyPlannerTest {
    private val wed = LocalDate.of(2026, 11, 4)

    private fun plan(tasks: List<app.levelup2027.data.Task>, completions: List<Completion> = emptyList(), capacity: Int? = null,
                     goals: List<Goal> = emptyList(), now: LocalTime = LocalTime.of(19, 0), lowEnergy: Boolean = false, preferMin: Set<String> = emptySet()) =
        DailyPlanner.plan(wed, now, ScheduleEngine.occurrencesFor(wed, tasks, completions), tasks, completions, goals, emptyList(), capacity, lowEnergy, preferMin)

    @Test fun `the highest-priority task matters most, the next one fits the time of day`() {
        val tasks = listOf(
            task("low", rule = "DAILY", slot = TimeSlot.EVENING).copy(priority = 3),
            task("high", rule = "DAILY", slot = TimeSlot.MORNING).copy(priority = 1),
            task("eve", rule = "DAILY", slot = TimeSlot.EVENING),
        )
        val f = plan(tasks)
        assertEquals("high", f.mostImportant!!.taskId)
        assertEquals("eve", f.next!!.taskId) // evening now; normal priority beats low
    }

    @Test fun `tasks over the daily budget go under if-you-have-time, never the most important`() {
        val tasks = (1..4).map { task("t$it", rule = "DAILY").copy(priority = if (it == 1) 1 else 2) } // 30 min each
        val f = plan(tasks, capacity = 70)
        assertEquals(2, f.ifTime.size)
        assertTrue("t1" !in f.ifTime)
        assertTrue(f.tradeOff!!.contains("budget is 70"))
        assertTrue(f.tradeOff!!.contains("minimum versions would fit")) // 4 × 10 min
        assertNull(plan(tasks).tradeOff) // no budget, no trade-off
    }

    @Test fun `done work counts against the budget and low energy uses minimum minutes`() {
        val tasks = (1..3).map { task("t$it", rule = "DAILY") }
        val done = listOf(Completion(taskId = "t1", taskTitle = "t1", scheduledDate = wed, completionType = CompletionType.IDEAL, completedAt = 0))
        assertEquals(1, plan(tasks, done, capacity = 60).ifTime.size) // 30 done + 30 + (30 doesn't fit)
        val low = plan(tasks, done, capacity = 60, lowEnergy = true)
        assertTrue(low.ifTime.isEmpty()) // 30 done + 10 + 10
        assertTrue(low.ordered.all { it.useMinimum })
    }

    @Test fun `a task waiting on an unfinished dated task is not suggested next`() {
        val setup = task("setup", date = wed.minusDays(1))
        val build = task("build", rule = "DAILY").copy(dependsOn = "setup", priority = 1)
        val other = task("other", rule = "DAILY")
        val f = plan(listOf(setup, build, other))
        assertEquals("other", f.next!!.taskId)
        assertEquals(listOf("setup"), f.ordered.first { it.taskId == "build" }.blockedBy)
    }

    @Test fun `tasks of paused goals sink`() {
        val goals = listOf(Goal("g", "Paused", "", "", wed.plusMonths(1), "", "", status = GoalStatus.PAUSED))
        val f = plan(listOf(task("a", rule = "DAILY").copy(goalId = "g", priority = 1), task("b", rule = "DAILY")), goals = goals)
        assertEquals("b", f.mostImportant!!.taskId)
    }

    @Test fun `missed tasks - important ones are rescheduled into days with room, one per day, others let go`() {
        val tasks = listOf(
            task("imp1", date = wed.minusDays(2)).copy(priority = 1),
            task("imp2", date = wed.minusDays(1)).copy(priority = 1),
            task("low", date = wed.minusDays(1)).copy(priority = 3),
            task("old", date = wed.minusDays(20)).copy(priority = 1),
            task("rest", date = wed.minusDays(1)).copy(isRest = true, priority = 1),
        )
        val overdue = ScheduleEngine.overdue(wed, tasks, emptyList())
        val load = mapOf(wed to 50) // today already has 50 of 60 minutes
        val s = MissedTaskTriage.suggest(wed, overdue, tasks, emptyList(), emptyList(), { 60 }, { load[it] ?: 0 }).associateBy { it.occ.task.id }
        assertEquals(MissedAction.RESCHEDULE, s.getValue("imp1").action)
        assertEquals(MissedAction.RESCHEDULE, s.getValue("imp2").action)
        val days = listOf(s.getValue("imp1").toDate, s.getValue("imp2").toDate)
        assertEquals(2, days.toSet().size) // never two moved tasks on one day
        assertTrue(days.none { it == wed }) // today has no room for 30 more minutes
        assertEquals(MissedAction.SKIP, s.getValue("low").action)
        assertEquals(MissedAction.SKIP, s.getValue("old").action)
        assertEquals(MissedAction.SKIP, s.getValue("rest").action)
    }

    @Test fun `missed tasks - only the minimum fits, or nothing fits`() {
        val t = task("imp", date = wed.minusDays(1)).copy(priority = 1)
        val overdue = ScheduleEngine.overdue(wed, listOf(t), emptyList())
        val minOnly = MissedTaskTriage.suggest(wed, overdue, listOf(t), emptyList(), emptyList(), { 40 }, { 25 }).single()
        assertEquals(MissedAction.RESCHEDULE, minOnly.action)
        assertTrue(minOnly.useMinimum)
        val none = MissedTaskTriage.suggest(wed, overdue, listOf(t), emptyList(), emptyList(), { 40 }, { 40 }).single()
        assertEquals(MissedAction.SKIP, none.action)
    }
}
