package app.levelup2027

import app.levelup2027.ai.DraftCategory
import app.levelup2027.ai.DraftGoal
import app.levelup2027.ai.DraftMetric
import app.levelup2027.ai.DraftMilestone
import app.levelup2027.ai.DraftOutcome
import app.levelup2027.ai.DraftTask
import app.levelup2027.ai.PlanDraft
import app.levelup2027.ai.PlanMaterializer
import app.levelup2027.ai.PlanValidator
import app.levelup2027.data.Cat
import app.levelup2027.data.Goal
import app.levelup2027.data.Metrics
import app.levelup2027.data.Source
import app.levelup2027.domain.ScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanMaterializerTest {
    private val start = LocalDate.of(2026, 11, 2)
    private var n = 0
    private val m = PlanMaterializer(now = { 1000L }, randomSuffix = { "x${n++}" })

    private val draft = PlanDraft(
        weekdayMinutes = 45, weekendMinutes = 90,
        categories = listOf(DraftCategory("spanish", "Spanish", "language")),
        goals = listOf(
            DraftGoal("spanish", "Hold a 10-minute conversation in Spanish", "Spanish", "spanish", deadline = "2027-06-30", priority = 1,
                metrics = listOf(DraftMetric("words", "Words known", "", baseline = 200.0, target = 1500.0), DraftMetric("hours", "Hours practised", "h")),
                milestones = listOf(DraftMilestone("a1", "Finish A1", "2027-01-31"))),
            DraftGoal("fit", "Run 5 km", "Running", "fitness", deadline = "", priority = 2),
        ),
        habits = listOf(
            DraftTask("duo", "Spanish practice", goalKey = "spanish", categoryKey = "spanish", timeSlot = "MORNING", days = listOf("MON", "WED", "FRI"),
                ideal = "20 min lesson", idealMinutes = 20, minimum = "5 flashcards", minimumMinutes = 5, milestoneKey = "a1"),
        ),
        firstWeek = listOf(
            DraftTask("app", "Pick a course", goalKey = "spanish", date = start.toString(), ideal = "Compare 3 courses", idealMinutes = 30, minimum = "Pick one", minimumMinutes = 5),
            DraftTask("first", "First lesson", goalKey = "spanish", date = start.plusDays(1).toString(), ideal = "Do lesson 1", idealMinutes = 30, minimum = "Read lesson 1", minimumMinutes = 10, dependsOn = listOf("app")),
            DraftTask("shoes", "Check running shoes", goalKey = "fit", date = start.plusDays(2).toString(), ideal = "Try them on", idealMinutes = 15, minimum = "Find them", minimumMinutes = 2),
        ),
        weeklyOutcomes = listOf(DraftOutcome("spanish", "a1", "Course chosen and first lesson done")),
    )

    @Test fun `materialises goals, habits, dated tasks and outcomes with stable ids`() {
        val v = PlanValidator.validate(draft, start, Cat.DEFAULTS)
        val r = m.materialize(v, "plan-1", Cat.DEFAULTS, emptyList(), emptyList())
        assertEquals(2, r.goals.size)
        val spanish = r.goals.first { it.title.startsWith("Hold") }
        assertEquals(Source.AI, spanish.source)
        assertEquals("spanish", spanish.categoryId)
        assertEquals(listOf("words", "hours"), Metrics.forGoal(spanish).map { it.key })
        // Only the stated baseline becomes a progress entry.
        assertEquals(listOf("words" to 200.0), r.progress.map { it.metric to it.value })
        val running = r.goals.first { it.title == "Run 5 km" }
        assertTrue(running.deadlineAssumed)
        assertEquals(start.plusYears(1), running.targetDate)

        val habit = r.tasks.first { it.recurrenceRule != null }
        assertEquals("WEEKLY:MON,WED,FRI", habit.recurrenceRule)
        assertEquals(start, habit.activeFrom)
        assertEquals(LocalDate.of(2027, 6, 30), habit.activeUntil)
        assertEquals(r.milestones.single().id, habit.milestoneId)

        val first = r.tasks.first { it.title == "First lesson" }
        val pick = r.tasks.first { it.title == "Pick a course" }
        assertEquals(listOf(pick.id), first.dependencyIds)
        assertEquals(spanish.id, first.goalId)
        assertEquals(running.categoryId ?: Cat.FITNESS, r.tasks.first { it.title == "Check running shoes" }.category)
        assertTrue(r.tasks.all { it.planId == "plan-1" && it.source == Source.AI })
        assertEquals(r.tasks.size, r.tasks.map { it.id }.distinct().size)

        assertEquals(listOf("spanish"), r.categories.map { it.id })
        assertEquals(Cat.DEFAULTS.maxOf { it.colorIndex } + 1, r.categories.single().colorIndex)
        assertEquals(spanish.id, r.outcomes.single().goalId)
        assertEquals(45, r.capacity.weekdayMinutes)

        // The habit and dated tasks land on the right days.
        val tuesday = ScheduleEngine.tasksFor(start.plusDays(1), r.tasks)
        assertEquals(listOf("First lesson"), tuesday.map { it.title })
        val wednesday = ScheduleEngine.tasksFor(start.plusDays(2), r.tasks).map { it.title }.toSet()
        assertEquals(setOf("Spanish practice", "Check running shoes"), wednesday)
    }

    @Test fun `accepting the same plan twice adds nothing twice`() {
        val v = PlanValidator.validate(draft, start, Cat.DEFAULTS)
        val first = m.materialize(v, "plan-1", Cat.DEFAULTS, emptyList(), emptyList())
        val second = m.materialize(v, "plan-2", Cat.DEFAULTS + first.categories, first.tasks, first.goals)
        assertTrue(second.goals.isEmpty())
        assertTrue(second.tasks.isEmpty())
        assertTrue(second.categories.isEmpty())
        assertEquals(6, second.skippedDuplicates.size) // 2 goals, 1 habit, 3 dated tasks
    }

    @Test fun `tasks reuse an existing goal with the same title`() {
        val existing = Goal("g-old", "Run 5 km", "", "", start.plusMonths(3), "", "")
        val v = PlanValidator.validate(draft, start, Cat.DEFAULTS)
        val r = m.materialize(v, "p", Cat.DEFAULTS, emptyList(), listOf(existing))
        assertEquals(1, r.goals.size)
        assertEquals("g-old", r.tasks.first { it.title == "Check running shoes" }.goalId)
        assertNull(r.goals.firstOrNull { it.title == "Run 5 km" })
    }
}
