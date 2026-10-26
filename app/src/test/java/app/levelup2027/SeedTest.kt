package app.levelup2027

import app.levelup2027.data.Campaign
import app.levelup2027.data.Cat
import app.levelup2027.data.EnergyLevel
import app.levelup2027.data.GoalIds
import app.levelup2027.data.Seed
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.Recurrence
import app.levelup2027.domain.ScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class SeedTest {
    private val all = Seed.allTasks
    private val typicalWeek = LocalDate.of(2026, 11, 30) // a Monday after the curriculum

    @Test fun `task ids are unique and stable`() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(all.any { it.id == "mon-tech-js" })
        assertTrue(all.any { it.id == "cur-w1-mon-js" })
    }

    @Test fun `every task has an ideal and a minimum version and a valid schedule`() {
        all.forEach { t ->
            assertTrue(t.id, t.idealDescription.isNotBlank())
            assertTrue(t.id, t.minimumDescription.isNotBlank())
            assertTrue(t.id, t.estimatedMinimumMinutes <= t.estimatedIdealMinutes)
            assertTrue(t.id, (t.scheduledDate != null) xor (Recurrence.parse(t.recurrenceRule) != null))
            assertTrue(t.id, t.goalId == null || t.goalId in GoalIds.ALL)
        }
    }

    @Test fun `four planned gym sessions per week and none on Wednesday`() {
        val gym = Seed.recurringTasks.filter { it.countsAsWorkout }
        val days = gym.flatMap { Recurrence.parse(it.recurrenceRule)!!.days }
        assertEquals(4, days.size)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), days.toSet())
    }

    @Test fun `during-work tasks are short observations on weekdays only`() {
        val work = Seed.recurringTasks.filter { it.timeSlot == TimeSlot.WORK }
        assertEquals(5, work.size)
        work.forEach {
            assertTrue(it.id, it.estimatedIdealMinutes <= 10)
            val d = Recurrence.parse(it.recurrenceRule)!!.days
            assertFalse(it.id, DayOfWeek.SATURDAY in d || DayOfWeek.SUNDAY in d)
        }
    }

    @Test fun `no weekday has more than one high-effort task`() {
        for (i in 0L..6L) {
            val date = typicalWeek.plusDays(i)
            val high = ScheduleEngine.tasksFor(date, all).count { it.energyLevel == EnergyLevel.HIGH }
            assertTrue("$date has $high high-effort tasks", high <= 1)
        }
    }

    @Test fun `Wednesday is recovery plus business discovery`() {
        val wed = typicalWeek.plusDays(2)
        val ids = ScheduleEngine.tasksFor(wed, all).map { it.id }
        assertTrue("wed-recovery" in ids)
        assertTrue("wed-biz-discovery" in ids)
        assertTrue(ScheduleEngine.tasksFor(wed, all).none { it.countsAsWorkout })
    }

    @Test fun `each day has a reasonable workload and a clearly smaller minimum`() {
        for (i in 0L..6L) {
            val date = typicalWeek.plusDays(i)
            val tasks = ScheduleEngine.tasksFor(date, all)
            val ideal = tasks.sumOf { it.estimatedIdealMinutes }
            val min = tasks.sumOf { it.estimatedMinimumMinutes }
            assertTrue("$date ideal $ideal min", ideal <= 200)
            assertTrue("$date minimum $min min", min <= 60)
            assertTrue(min < ideal)
        }
    }

    @Test fun `every curriculum task replaces a recurring task scheduled that same day`() {
        val curriculum = Seed.curriculumTasks
        assertEquals(29, curriculum.size)
        curriculum.forEach { c ->
            val base = Seed.recurringTasks.first { it.id == c.supersedesTaskId }
            assertTrue(c.id, ScheduleEngine.isScheduledOn(base, c.scheduledDate!!))
            val due = ScheduleEngine.tasksFor(c.scheduledDate!!, all).map { it.id }
            assertTrue(c.id, c.id in due)
            assertFalse("${base.id} duplicated on ${c.scheduledDate}", base.id in due)
        }
        assertEquals(LocalDate.of(2026, 10, 26), curriculum.minOf { it.scheduledDate!! })
        assertEquals(LocalDate.of(2026, 11, 22), curriculum.maxOf { it.scheduledDate!! })
    }

    @Test fun `campaign day one shows the week one plan`() {
        val ids = ScheduleEngine.tasksFor(Campaign.START, all).map { it.id }
        assertTrue("cur-w1-mon-js" in ids)
        assertTrue("cur-w1-mon-gym" in ids)
        assertTrue("daily-morning" in ids)
        assertTrue("mon-work-question" in ids)
        assertFalse("mon-tech-js" in ids)
    }

    @Test fun `roadmap checkpoints fall on Sundays within their milestone month`() {
        assertTrue(Seed.checkpointTasks.isNotEmpty())
        Seed.checkpointTasks.forEach {
            assertEquals(DayOfWeek.SUNDAY, it.scheduledDate!!.dayOfWeek)
            assertEquals(Cat.PLANNING, it.category)
        }
        assertNotNull(Seed.checkpointTasks.firstOrNull { it.id == "checkpoint-2026-12" })
    }

    @Test fun `job search starts in March 2027`() {
        val t = Seed.recurringTasks.first { it.id == "sun-career-search" }
        assertFalse(ScheduleEngine.isScheduledOn(t, LocalDate.of(2027, 2, 28)))
        assertTrue(ScheduleEngine.isScheduledOn(t, LocalDate.of(2027, 3, 7)))
    }

    @Test fun `initial progress only uses values stated in the plan`() {
        val metrics = Seed.initialProgress.associate { it.metric to it.value }
        assertEquals(6.0, metrics["salary_lpa"]!!, 0.0)
        assertEquals(63.0, metrics["weight_kg"]!!, 0.0)
        assertTrue(Seed.initialProgress.first { it.metric == "weight_kg" }.isEstimate)
        assertEquals(35000.0, metrics["fund_target"]!!, 0.0)
        assertFalse("pushups" in metrics)
        assertFalse("pullups" in metrics)
        assertFalse("fund_balance" in metrics)
    }
}
