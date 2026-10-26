package app.levelup2027

import app.levelup2027.ai.DraftCategory
import app.levelup2027.ai.DraftGoal
import app.levelup2027.ai.DraftMilestone
import app.levelup2027.ai.DraftTask
import app.levelup2027.ai.IssueLevel
import app.levelup2027.ai.PlanDraft
import app.levelup2027.ai.PlanValidator
import app.levelup2027.ai.WeekPlan
import app.levelup2027.data.Cat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanValidatorTest {
    private val start = LocalDate.of(2026, 11, 2) // a Monday
    private val cats = Cat.DEFAULTS

    private fun goal(key: String = "fit", deadline: String = "2027-03-01") =
        DraftGoal(key = key, title = "Get fit", categoryKey = "fitness", deadline = deadline, priority = 1,
            milestones = listOf(DraftMilestone("m1", "Run 5k", "2027-01-15")))

    private fun habit(key: String, days: List<String>, minutes: Int = 30, title: String = key) =
        DraftTask(key = key, title = title, goalKey = "fit", categoryKey = "fitness", timeSlot = "EVENING", days = days,
            ideal = "Do it", idealMinutes = minutes, minimum = "Tiny", minimumMinutes = 10)

    private fun dated(key: String, date: LocalDate, minutes: Int = 30, priority: Int = 2, deps: List<String> = emptyList(), title: String = key) =
        DraftTask(key = key, title = title, goalKey = "fit", categoryKey = "fitness", timeSlot = "FLEXIBLE", date = date.toString(),
            ideal = "Do it", idealMinutes = minutes, minimum = "Tiny", minimumMinutes = 10, priority = priority, dependsOn = deps)

    @Test fun `a valid plan passes unchanged apart from normalisation`() {
        val v = PlanValidator.validate(PlanDraft(goals = listOf(goal()), habits = listOf(habit("run", listOf("MON", "THU"))),
            firstWeek = listOf(dated("shoes", start))), start, cats)
        assertFalse(v.hasErrors)
        assertEquals(listOf("run"), v.draft.habits.map { it.key })
        assertEquals(listOf("MON", "THU"), v.draft.habits.single().days)
        assertEquals(Cat.FITNESS, v.draft.habits.single().categoryKey)
        assertEquals(60, v.dailyLoad[start]) // 30 habit + 30 dated
    }

    @Test fun `a plan without goals or without tasks is rejected`() {
        assertTrue(PlanValidator.validate(PlanDraft(habits = listOf(habit("a", listOf("MON")))), start, cats).hasErrors)
        assertTrue(PlanValidator.validate(PlanDraft(goals = listOf(goal())), start, cats).hasErrors)
    }

    @Test fun `tasks outside the first week, bad dates and bad days are dropped with a note`() {
        val v = PlanValidator.validate(
            PlanDraft(
                goals = listOf(goal()),
                habits = listOf(habit("ok", listOf("TUE")), habit("nodays", listOf("XYZ"))),
                firstWeek = listOf(dated("late", start.plusDays(9)), dated("past", start.minusDays(1)), dated("fine", start.plusDays(6)),
                    dated("garbage", start).copy(date = "next tuesday")),
            ),
            start, cats,
        )
        assertEquals(listOf("ok"), v.draft.habits.map { it.key })
        assertEquals(listOf("fine"), v.draft.firstWeek.map { it.key })
        assertEquals(4, v.issues.count { it.level == IssueLevel.FIXED })
    }

    @Test fun `duplicates are removed, including a dated copy of a habit on its day`() {
        val v = PlanValidator.validate(
            PlanDraft(
                goals = listOf(goal()),
                habits = listOf(habit("run", listOf("MON")), habit("run2", listOf("MON"), title = "run")),
                firstWeek = listOf(dated("a", start, title = "Buy shoes"), dated("b", start, title = "buy  shoes!"), dated("c", start, title = "Run")),
            ),
            start, cats,
        )
        assertEquals(1, v.draft.habits.size)
        assertEquals(listOf("a"), v.draft.firstWeek.map { it.key })
    }

    @Test fun `minimum versions are kept meaningful and never longer than the ideal`() {
        val v = PlanValidator.validate(
            PlanDraft(goals = listOf(goal()), firstWeek = listOf(
                dated("long", start).copy(minimumMinutes = 50, idealMinutes = 20),
                dated("empty", start).copy(minimum = ""),
                dated("rest", start).copy(minimum = "", isRest = true, idealMinutes = 0, minimumMinutes = 0),
            )),
            start, cats,
        )
        val byKey = v.draft.firstWeek.associateBy { it.key }
        assertEquals(20, byKey.getValue("long").minimumMinutes)
        assertTrue(byKey.getValue("empty").minimum.isNotBlank())
        assertTrue(v.issues.any { it.level == IssueLevel.WARNING && it.message.contains("no minimum") })
        assertEquals("", byKey.getValue("rest").minimum) // rest may legitimately have no minimum
    }

    @Test fun `dependency cycles are broken and dependents are moved after what they need`() {
        val v = PlanValidator.validate(
            PlanDraft(goals = listOf(goal()), firstWeek = listOf(
                dated("a", start.plusDays(3), deps = listOf("b")),
                dated("b", start.plusDays(1), deps = listOf("a")),
                dated("c", start, deps = listOf("d", "unknown", "c")),
                dated("d", start.plusDays(2)),
            )),
            start, cats,
        )
        val byKey = v.draft.firstWeek.associateBy { it.key }
        assertTrue(byKey.getValue("a").dependsOn.isEmpty() || byKey.getValue("b").dependsOn.isEmpty())
        assertEquals(listOf("d"), byKey.getValue("c").dependsOn)
        assertFalse(LocalDate.parse(byKey.getValue("c").date).isBefore(LocalDate.parse(byKey.getValue("d").date)))
    }

    @Test fun `overloaded days are rebalanced into days with room, otherwise flagged`() {
        val v = PlanValidator.validate(
            PlanDraft(
                goals = listOf(goal()), weekdayMinutes = 60, weekendMinutes = 60,
                habits = listOf(habit("daily", listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"), minutes = 20)),
                firstWeek = listOf(dated("a", start, 30, priority = 1), dated("b", start, 30, priority = 3)),
            ),
            start, cats,
        )
        // Monday: 20 + 30 + 30 = 80 > 60, so the low-priority task moves.
        val b = v.draft.firstWeek.first { it.key == "b" }
        assertTrue(LocalDate.parse(b.date).isAfter(start))
        assertTrue(v.dailyLoad.values.all { it <= 60 })

        val over = PlanValidator.validate(
            PlanDraft(goals = listOf(goal()), weekdayMinutes = 15, weekendMinutes = 15, habits = listOf(habit("big", listOf("MON"), minutes = 90))),
            start, cats,
        )
        assertTrue(over.issues.any { it.level == IssueLevel.WARNING && it.message.contains("minutes planned") })
    }

    @Test fun `goals - past deadlines removed, milestones clamped, priorities clamped, max goals`() {
        val v = PlanValidator.validate(
            PlanDraft(
                goals = listOf(
                    goal("a", deadline = "2020-01-01"),
                    goal("b", deadline = "2026-12-01").copy(priority = 9, milestones = listOf(DraftMilestone("late", "Late one", "2027-06-01"), DraftMilestone("old", "Old", "2025-01-01"))),
                ) + (1..10).map { goal("g$it").copy(title = "Goal $it") },
                habits = listOf(habit("h", listOf("MON"))),
            ),
            start, cats,
        )
        assertEquals(PlanValidator.MAX_GOALS, v.draft.goals.size)
        val a = v.draft.goals.first { it.key == "a" }
        assertEquals("", a.deadline)
        val b = v.draft.goals.first { it.key == "b" }
        assertEquals(3, b.priority)
        assertEquals(listOf("2026-12-01"), b.milestones.map { it.due })
    }

    @Test fun `categories resolve to existing ids by key or name, new ones are kept only if used`() {
        val v = PlanValidator.validate(
            PlanDraft(
                categories = listOf(DraftCategory("Spanish", "Spanish", "language"), DraftCategory("unused", "Unused", "nope")),
                goals = listOf(goal().copy(categoryKey = "Spanish")),
                habits = listOf(habit("h", listOf("MON")).copy(categoryKey = "Health & Wellness"), habit("s", listOf("TUE")).copy(categoryKey = "spanish")),
            ),
            start, cats,
        )
        assertEquals(Cat.HEALTH, v.draft.habits.first { it.key == "h" }.categoryKey)
        assertEquals("spanish", v.draft.habits.first { it.key == "s" }.categoryKey)
        assertEquals(listOf("spanish"), v.draft.categories.map { it.key })
        assertEquals("language", v.draft.categories.single().icon)
    }

    @Test fun `week plans drop tasks that already exist and map goal ids`() {
        val (week, issues) = PlanValidator.validateWeek(
            WeekPlan(tasks = listOf(
                DraftTask("x", "Practise verbs", goalKey = "g-spanish-ab12", date = start.toString(), ideal = "i", idealMinutes = 20, minimum = "m", minimumMinutes = 5),
                DraftTask("y", "Mock interview", goalKey = "g-spanish-ab12", date = start.plusDays(1).toString(), ideal = "i", idealMinutes = 20, minimum = "m", minimumMinutes = 5),
            )),
            start, mapOf("g-spanish-ab12" to "Learn Spanish"), cats, setOf("practise verbs" to start), 60, 60,
        ) { 0 }
        assertEquals(listOf("y"), week.tasks.map { it.key })
        assertEquals("g-spanish-ab12", week.tasks.single().goalKey)
        assertTrue(issues.any { it.message.contains("already planned") })
    }
}
