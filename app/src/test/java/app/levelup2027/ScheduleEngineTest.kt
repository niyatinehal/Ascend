package app.levelup2027

import app.levelup2027.data.Campaign
import app.levelup2027.data.Category
import app.levelup2027.data.Completion
import app.levelup2027.data.CompletionType
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.Dates
import app.levelup2027.domain.Recurrence
import app.levelup2027.domain.ScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

fun task(
    id: String,
    rule: String? = null,
    date: LocalDate? = null,
    slot: TimeSlot = TimeSlot.EVENING,
    category: Category = Category.TECHNICAL,
    supersedes: String? = null,
    enabled: Boolean = true,
    activeFrom: LocalDate? = null,
) = Task(
    id = id, title = id, description = "", category = category, timeSlot = slot,
    idealDescription = "• ideal", minimumDescription = "• min", estimatedIdealMinutes = 30, estimatedMinimumMinutes = 10,
    scheduledDate = date, recurrenceRule = rule, supersedesTaskId = supersedes, enabled = enabled, activeFrom = activeFrom,
)

fun done(id: String, date: LocalDate, type: CompletionType = CompletionType.IDEAL) =
    Completion(taskId = id, taskTitle = id, scheduledDate = date, completionType = type, completedAt = 0)

class ScheduleEngineTest {
    private val mon = LocalDate.of(2026, 11, 2) // Monday
    private val tue = mon.plusDays(1)
    private val wed = mon.plusDays(2)
    private val sun = mon.plusDays(6)

    @Test fun `recurrence rules parse and encode`() {
        assertEquals(DayOfWeek.entries.toSet(), Recurrence.parse("DAILY")!!.days)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), Recurrence.parse("WEEKLY:MON,THU")!!.days)
        assertEquals("WEEKLY:TUE,SAT", Recurrence(setOf(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY)).encode())
        assertEquals("DAILY", Recurrence(DayOfWeek.entries.toSet()).encode())
        assertNull(Recurrence.parse("WEEKLY:"))
        assertNull(Recurrence.parse("garbage"))
        // "TH" must be Thursday, "TU" Tuesday; ambiguous single letters are rejected.
        assertEquals(setOf(DayOfWeek.THURSDAY), Recurrence.parse("WEEKLY:TH")!!.days)
        assertNull(Recurrence.parse("WEEKLY:T"))
    }

    @Test fun `weekly task appears only on its weekdays`() {
        val t = task("gym", rule = "WEEKLY:MON,TUE,THU,FRI")
        assertTrue(ScheduleEngine.isScheduledOn(t, mon))
        assertTrue(ScheduleEngine.isScheduledOn(t, tue))
        assertFalse(ScheduleEngine.isScheduledOn(t, wed))
        assertFalse(ScheduleEngine.isScheduledOn(t, sun))
    }

    @Test fun `recurring tasks respect campaign window and activeFrom`() {
        val daily = task("d", rule = "DAILY")
        assertFalse(ScheduleEngine.isScheduledOn(daily, Campaign.START.minusDays(1)))
        assertTrue(ScheduleEngine.isScheduledOn(daily, Campaign.START))
        assertTrue(ScheduleEngine.isScheduledOn(daily, Campaign.END))
        assertFalse(ScheduleEngine.isScheduledOn(daily, Campaign.END.plusDays(1)))
        val later = task("later", rule = "DAILY", activeFrom = LocalDate.of(2027, 3, 1))
        assertFalse(ScheduleEngine.isScheduledOn(later, LocalDate.of(2027, 2, 28)))
        assertTrue(ScheduleEngine.isScheduledOn(later, LocalDate.of(2027, 3, 1)))
    }

    @Test fun `one-time task appears only on its date and never repeats`() {
        val t = task("once", date = tue)
        assertTrue(ScheduleEngine.isScheduledOn(t, tue))
        assertFalse(ScheduleEngine.isScheduledOn(t, tue.plusWeeks(1)))
        assertFalse(ScheduleEngine.isScheduledOn(t, tue.minusDays(1)))
    }

    @Test fun `dated curriculum task replaces its recurring equivalent without duplication`() {
        val rec = task("mon-tech-js", rule = "WEEKLY:MON")
        val cur = task("cur-w2-mon-js", date = mon, supersedes = "mon-tech-js")
        val ids = ScheduleEngine.tasksFor(mon, listOf(rec, cur)).map { it.id }
        assertEquals(listOf("cur-w2-mon-js"), ids)
        // The following Monday, the recurring task is back.
        assertEquals(listOf("mon-tech-js"), ScheduleEngine.tasksFor(mon.plusWeeks(1), listOf(rec, cur)).map { it.id })
    }

    @Test fun `disabled tasks and disabled categories are excluded`() {
        val a = task("a", rule = "DAILY", enabled = false)
        val b = task("b", rule = "DAILY", category = Category.BUSINESS)
        val c = task("c", rule = "DAILY", category = Category.FITNESS)
        val ids = ScheduleEngine.tasksFor(mon, listOf(a, b, c), enabledCategories = setOf(Category.FITNESS)).map { it.id }
        assertEquals(listOf("c"), ids)
    }

    @Test fun `tasks are ordered by time slot and a work task stays in work`() {
        val e = task("e", rule = "DAILY", slot = TimeSlot.EVENING)
        val m = task("m", rule = "DAILY", slot = TimeSlot.MORNING)
        val w = task("w", rule = "DAILY", slot = TimeSlot.WORK)
        val f = task("f", rule = "DAILY", slot = TimeSlot.FLEXIBLE)
        val occ = ScheduleEngine.occurrencesFor(mon, listOf(e, f, w, m), emptyList())
        assertEquals(listOf("m", "w", "e", "f"), occ.map { it.task.id })
        val groups = ScheduleEngine.slotGroups(occ)
        assertEquals(listOf("w"), groups[TimeSlot.WORK]!!.map { it.task.id })
        assertEquals(listOf("e"), groups[TimeSlot.EVENING]!!.map { it.task.id })
    }

    @Test fun `completions attach only to their own date`() {
        val t = task("t", rule = "DAILY")
        val occMon = ScheduleEngine.occurrencesFor(mon, listOf(t), listOf(done("t", mon)))
        val occTue = ScheduleEngine.occurrencesFor(tue, listOf(t), listOf(done("t", mon)))
        assertTrue(occMon.single().isDone)
        assertTrue(occTue.single().isOpen)
    }

    @Test fun `overdue contains only past one-time tasks without a record`() {
        val rec = task("rec", rule = "DAILY")
        val pastOpen = task("pastOpen", date = mon)
        val pastDone = task("pastDone", date = mon)
        val pastSkipped = task("pastSkipped", date = mon)
        val todayTask = task("todayTask", date = wed)
        val completions = listOf(done("pastDone", mon), done("pastSkipped", mon, CompletionType.SKIPPED))
        val overdue = ScheduleEngine.overdue(wed, listOf(rec, pastOpen, pastDone, pastSkipped, todayTask), completions)
        assertEquals(listOf("pastOpen"), overdue.map { it.task.id })
        assertEquals(mon, overdue.single().date)
    }

    @Test fun `missed recurring tasks never accumulate as overdue`() {
        val rec = task("rec", rule = "DAILY")
        val overdue = ScheduleEngine.overdue(mon.plusDays(30), listOf(rec), emptyList())
        assertTrue(overdue.isEmpty())
    }

    @Test fun `next occurrence finds the next scheduled weekday`() {
        val t = task("t", rule = "WEEKLY:FRI")
        assertEquals(mon.plusDays(4), ScheduleEngine.nextOccurrence(t, mon))
        assertEquals(mon.plusDays(4), ScheduleEngine.nextOccurrence(t, mon.plusDays(4)))
        assertNull(ScheduleEngine.nextOccurrence(task("o", date = mon), tue))
    }

    @Test fun `local date at a date boundary depends on the zone`() {
        // 2026-11-02 18:45 UTC is already 3 Nov 00:15 in India.
        val instant = Instant.parse("2026-11-02T18:45:00Z")
        assertEquals(LocalDate.of(2026, 11, 3), instant.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 2), instant.atZone(ZoneId.of("UTC")).toLocalDate())
        val beforeMidnight = ZonedDateTime.of(2026, 11, 2, 23, 59, 30, 0, ZoneId.of("Asia/Kolkata"))
        assertEquals(30_000L, Dates.millisUntilNextMidnight(beforeMidnight))
    }

    @Test fun `midnight wait handles daylight-saving days`() {
        val ny = ZoneId.of("America/New_York")
        // 1 Nov 2026 is a 25-hour day in New York.
        val start = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, ny)
        assertEquals(25 * 3_600_000L, Dates.millisUntilNextMidnight(start))
    }

    @Test fun `week starts on Monday`() {
        assertEquals(mon, Dates.weekStart(sun))
        assertEquals(mon, Dates.weekStart(mon))
        assertEquals(Campaign.START, Dates.weekStart(Campaign.START))
    }
}
