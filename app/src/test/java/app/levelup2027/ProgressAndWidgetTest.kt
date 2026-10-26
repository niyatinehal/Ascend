package app.levelup2027

import app.levelup2027.data.Category
import app.levelup2027.data.CompletionType
import app.levelup2027.data.SkipReason
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.DailyProgress
import app.levelup2027.domain.ScheduleEngine
import app.levelup2027.domain.WeeklyStatsCalculator
import app.levelup2027.domain.WidgetConfig
import app.levelup2027.domain.WidgetMode
import app.levelup2027.domain.WidgetStateBuilder
import app.levelup2027.domain.Xp
import app.levelup2027.notify.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ProgressAndWidgetTest {
    private val mon = LocalDate.of(2026, 11, 2)

    @Test fun `daily progress counts ideal and minimum as done, skipped as not done`() {
        val tasks = listOf("a", "b", "c", "d").map { task(it, rule = "DAILY") }
        val comps = listOf(
            done("a", mon, CompletionType.IDEAL),
            done("b", mon, CompletionType.MINIMUM),
            done("c", mon, CompletionType.SKIPPED),
        )
        val p = DailyProgress.of(ScheduleEngine.occurrencesFor(mon, tasks, comps))
        assertEquals(4, p.scheduled)
        assertEquals(2, p.done)
        assertEquals(1, p.ideal)
        assertEquals(1, p.minimum)
        assertEquals(1, p.skipped)
        assertEquals(1, p.open)
        assertEquals(0.5f, p.fraction)
    }

    @Test fun `a task is never counted twice in a day`() {
        val t = task("a", rule = "DAILY")
        val occ = ScheduleEngine.occurrencesFor(mon, listOf(t, t), listOf(done("a", mon), done("a", mon, CompletionType.MINIMUM)))
        val p = DailyProgress.of(occ + occ)
        assertEquals(1, p.scheduled)
        assertEquals(1, p.done)
    }

    @Test fun `empty day has zero progress without dividing by zero`() {
        val p = DailyProgress.of(emptyList())
        assertEquals(0f, p.fraction)
    }

    @Test fun `xp rewards minimum completions and never goes negative`() {
        val comps = listOf(done("a", mon), done("b", mon, CompletionType.MINIMUM), done("c", mon, CompletionType.SKIPPED))
        assertEquals(Xp.IDEAL + Xp.MINIMUM, Xp.total(comps))
        assertEquals(1, Xp.level(0))
        assertEquals(2, Xp.level(50))
        assertEquals(1, Xp.daysShowedUp(comps))
    }

    @Test fun `weekly stats count gym and tech sessions and skip patterns`() {
        val gym = task("gym", rule = "WEEKLY:MON,TUE,THU,FRI", category = Category.FITNESS).copy(countsAsWorkout = true)
        val tech = task("tech", rule = "WEEKLY:MON,TUE", category = Category.TECHNICAL)
        val comps = listOf(
            done("gym", mon), done("gym", mon.plusDays(1), CompletionType.MINIMUM),
            done("gym", mon.plusDays(3), CompletionType.SKIPPED).copy(skipReason = SkipReason.TOO_TIRED),
            done("gym", mon.plusDays(4), CompletionType.SKIPPED).copy(skipReason = SkipReason.TOO_TIRED),
            done("tech", mon),
        )
        val stats = WeeklyStatsCalculator.compute(mon, mon.plusDays(6), listOf(gym, tech), comps)
        assertEquals(2, stats.workouts)
        assertEquals(4, stats.workoutsPlanned)
        assertEquals(1, stats.techSessions)
        assertEquals(2, stats.techPlanned)
        assertEquals("gym", stats.frequentlySkipped.single().title)
        assertEquals(SkipReason.TOO_TIRED, stats.frequentlySkipped.single().topReason)
        assertTrue(stats.observations.any { it.contains("Too tired") })
    }

    @Test fun `weekly stats only count days up to today`() {
        val t = task("t", rule = "DAILY")
        val stats = WeeklyStatsCalculator.compute(mon, mon.plusDays(2), listOf(t), emptyList())
        assertEquals(3, stats.progress.scheduled)
    }

    @Test fun `widget shows open tasks first and hides completed by default`() {
        val tasks = listOf(
            task("m", rule = "DAILY", slot = TimeSlot.MORNING),
            task("w", rule = "DAILY", slot = TimeSlot.WORK),
            task("e", rule = "DAILY", slot = TimeSlot.EVENING),
        )
        val comps = listOf(done("m", mon))
        val s = WidgetStateBuilder.build(mon, tasks, comps, Category.entries.toSet(), WidgetConfig(), lowEnergy = false)
        assertEquals(1, s.done)
        assertEquals(3, s.scheduled)
        assertEquals(listOf("w", "e"), s.items.map { it.taskId })
        val withDone = WidgetStateBuilder.build(mon, tasks, comps, Category.entries.toSet(), WidgetConfig(showCompleted = true), false)
        assertEquals(listOf("w", "e", "m"), withDone.items.map { it.taskId })
        assertTrue(withDone.items.last().done)
    }

    @Test fun `widget slot and category filters affect items and counts`() {
        val tasks = listOf(
            task("w1", rule = "DAILY", slot = TimeSlot.WORK, category = Category.TECHNICAL),
            task("w2", rule = "DAILY", slot = TimeSlot.WORK, category = Category.BUSINESS),
            task("e", rule = "DAILY", slot = TimeSlot.EVENING),
        )
        val s = WidgetStateBuilder.build(
            mon, tasks, emptyList(), Category.entries.toSet(),
            WidgetConfig(slot = TimeSlot.WORK, categories = setOf(Category.TECHNICAL)), false,
        )
        assertEquals(listOf("w1"), s.items.map { it.taskId })
        assertEquals(1, s.scheduled)
    }

    @Test fun `widget shows minimum text in minimum mode or on low-energy days`() {
        val tasks = listOf(task("a", rule = "DAILY"))
        val ideal = WidgetStateBuilder.build(mon, tasks, emptyList(), Category.entries.toSet(), WidgetConfig(), false)
        val min = WidgetStateBuilder.build(mon, tasks, emptyList(), Category.entries.toSet(), WidgetConfig(mode = WidgetMode.MINIMUM), false)
        val low = WidgetStateBuilder.build(mon, tasks, emptyList(), Category.entries.toSet(), WidgetConfig(), true)
        assertEquals("ideal", ideal.items.single().line)
        assertEquals("min", min.items.single().line)
        assertEquals("min", low.items.single().line)
        assertEquals(10, low.items.single().minutes)
    }

    @Test fun `reminder trigger is the next future occurrence`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.of(2026, 11, 2, 8, 0, 0, 0, zone) // Monday 08:00
        assertEquals(now.withHour(12).withMinute(30), ReminderScheduler.nextTrigger(now, 12, 30))
        assertEquals(now.plusDays(1).withHour(7).withMinute(0), ReminderScheduler.nextTrigger(now, 7, 0))
        val sunday = ReminderScheduler.nextTrigger(now, 18, 0, DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 8), sunday.toLocalDate())
        // On Sunday after 18:00 the next weekly reminder is a week later.
        val sunEvening = ZonedDateTime.of(2026, 11, 8, 19, 0, 0, 0, zone)
        assertEquals(LocalDate.of(2026, 11, 15), ReminderScheduler.nextTrigger(sunEvening, 18, 0, DayOfWeek.SUNDAY).toLocalDate())
    }
}
