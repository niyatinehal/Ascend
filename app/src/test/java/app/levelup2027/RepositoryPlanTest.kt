package app.levelup2027

import app.levelup2027.ai.PlanChange
import app.levelup2027.ai.PlanMaterialization
import app.levelup2027.data.AppSettings
import app.levelup2027.data.Capacity
import app.levelup2027.data.Cat
import app.levelup2027.data.CategoryEntity
import app.levelup2027.data.CompletionType
import app.levelup2027.data.Goal
import app.levelup2027.data.GoalStatus
import app.levelup2027.data.OccurrenceOverride
import app.levelup2027.data.SettingsJson
import app.levelup2027.data.Source
import app.levelup2027.data.WidgetSettings
import app.levelup2027.domain.ScheduleEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RepositoryPlanTest {
    private val env = TestEnv()
    private val repo = env.repo
    private val mon = LocalDate.of(2026, 11, 2)

    @Test fun `fresh install gets default categories and no plan, and stays in onboarding`() = runTest {
        repo.ensureDefaults()
        assertEquals(Cat.DEFAULTS.map { it.id }, env.categories.getAll().map { it.id })
        assertTrue(env.tasks.getAll().isEmpty())
        val s = env.settings.current()
        assertFalse(s.onboardingDone)
        assertEquals(AppSettings.CURRENT_VERSION, s.settingsVersion)
    }

    @Test fun `v1 settings are upgraded - example-plan users skip onboarding and keep disabled categories`() = runTest {
        // Settings JSON as written by v1: enum category names, no settingsVersion.
        val v1 = """{"theme":"DARK","enabledCategories":["CAREER","TECHNICAL","FITNESS","FINANCE","FAMILY","PLANNING","RECOVERY"],
            "widget":{"slot":null,"showCompleted":false,"mode":"IDEAL","categories":["CAREER","TECHNICAL","BUSINESS","FITNESS","FINANCE","FAMILY","PLANNING","RECOVERY"]},
            "seedVersion":1}"""
        val parsed = SettingsJson.decodeFromString(AppSettings.serializer(), v1)
        val env = TestEnv(parsed)
        env.repo.ensureDefaults()
        val s = env.settings.current()
        assertTrue(s.onboardingDone)
        assertEquals(LocalDate.of(2026, 10, 26), s.planStartDate)
        assertEquals(emptySet<String>(), s.widget.categories) // "all eight" became "all"
        assertFalse(env.categories.getAll().first { it.id == Cat.BUSINESS }.enabled)
        assertTrue(env.categories.getAll().first { it.id == Cat.LEARNING }.enabled)
        // Running it again changes nothing.
        env.repo.ensureDefaults()
        assertFalse(env.categories.getAll().first { it.id == Cat.BUSINESS }.enabled)
    }

    @Test fun `rescheduling a one-time task moves it and keeps a record on the old day`() = runTest {
        val t = task("once", date = mon)
        env.tasks.upsert(t)
        repo.reschedule(t, mon, mon.plusDays(2))
        val moved = env.tasks.get("once")!!
        assertEquals(mon.plusDays(2), moved.scheduledDate)
        val rec = env.completions.get("once", mon)!!
        assertEquals(CompletionType.RESCHEDULED, rec.completionType)
        assertEquals(mon.plusDays(2), rec.movedToDate)
        // Not overdue, not counted on the old day, open on the new day.
        assertTrue(ScheduleEngine.overdue(mon.plusDays(1), env.tasks.getAll(), env.completions.getAll()).isEmpty())
        assertTrue(ScheduleEngine.occurrencesFor(mon, env.tasks.getAll(), env.completions.getAll()).isEmpty())
        assertTrue(ScheduleEngine.occurrencesFor(mon.plusDays(2), env.tasks.getAll(), env.completions.getAll()).single().isOpen)
    }

    @Test fun `rescheduling a habit occurrence adds a dated copy and leaves the habit unchanged`() = runTest {
        val h = task("gym", rule = "WEEKLY:MON,THU")
        env.tasks.upsert(h)
        val newId = repo.reschedule(h, mon, mon.plusDays(5))
        val copy = env.tasks.get(newId)!!
        assertEquals(mon.plusDays(5), copy.scheduledDate)
        assertEquals("gym", copy.originTaskId)
        assertEquals(mon, copy.originDate)
        assertEquals(h.recurrenceRule, env.tasks.get("gym")!!.recurrenceRule)
        assertTrue(ScheduleEngine.occurrencesFor(mon, env.tasks.getAll(), env.completions.getAll()).isEmpty())
        assertEquals(listOf(newId), ScheduleEngine.occurrencesFor(mon.plusDays(5), env.tasks.getAll(), env.completions.getAll()).map { it.task.id })
        // Next Monday the habit is back as usual.
        assertEquals(listOf("gym"), ScheduleEngine.occurrencesFor(mon.plusWeeks(1), env.tasks.getAll(), env.completions.getAll()).map { it.task.id })
    }

    @Test fun `a completed occurrence can't be rescheduled`() = runTest {
        val t = task("once", date = mon)
        env.tasks.upsert(t)
        repo.complete(t, mon, CompletionType.IDEAL)
        val r = runCatching { repo.reschedule(t, mon, mon.plusDays(1)) }
        assertTrue(r.isFailure)
        assertEquals(CompletionType.IDEAL, env.completions.get("once", mon)!!.completionType)
    }

    @Test fun `widget completes the minimum version for an occurrence planned as minimum`() = runTest {
        env.tasks.upsert(task("a", rule = "DAILY"))
        repo.setPreferMinimum("a", mon, true)
        assertEquals(CompletionType.MINIMUM, repo.toggleFromWidget("a", mon))
        val state = repo.widgetStateNow(mon.plusDays(1))
        assertFalse(state.items.single().useMinimum) // only that day
    }

    @Test fun `applying changes records history instead of deleting anything`() = runTest {
        val habit = task("read", rule = "DAILY")
        val once = task("call", date = mon)
        env.tasks.upsert(habit); env.tasks.upsert(once)
        val goal = Goal("g1", "Learn", "", "", mon.plusMonths(3), "", "", source = Source.AI)
        env.goals.upsertGoal(goal)
        repo.applyChanges(
            listOf(
                PlanChange.Reschedule(once, mon, mon.plusDays(1), "busy"),
                PlanChange.UseMinimum(habit, mon, "tired"),
                PlanChange.DisableTask(habit, "not needed"),
                PlanChange.UpdateGoal(goal, goal.copy(status = GoalStatus.PAUSED), "asked"),
                PlanChange.SetCapacity(Capacity(), Capacity(30, 60), "less time"),
            ),
            "AI_ADJUST", "I have less time",
        )
        assertEquals(CompletionType.RESCHEDULED, env.completions.get("call", mon)!!.completionType)
        assertEquals(mon.plusDays(1), env.tasks.get("call")!!.scheduledDate)
        assertNotNull(env.tasks.get("read")) // paused, not deleted
        assertFalse(env.tasks.get("read")!!.enabled)
        assertEquals(OccurrenceOverride("read", mon, true, "tired", env.plan.overrides.value.single().createdAt), env.plan.overrides.value.single())
        assertEquals(GoalStatus.PAUSED, env.goals.goals["g1"]!!.status)
        assertEquals(Capacity(30, 60), env.settings.current().capacity)
        assertEquals("AI_ADJUST", env.plan.log.value.first().source)
    }

    @Test fun `accepting a plan adds rows, sets capacity and finishes onboarding without touching existing data`() = runTest {
        repo.ensureDefaults()
        val existing = task("mine", rule = "DAILY")
        env.tasks.upsert(existing)
        repo.complete(existing, mon, CompletionType.IDEAL)
        val g = Goal("g-new", "Run 5k", "", "5 km", mon.plusMonths(2), "", "", source = Source.AI)
        val m = PlanMaterialization(
            categories = listOf(CategoryEntity("running", "Running", "Running")),
            goals = listOf(g), milestones = emptyList(), outcomes = emptyList(),
            tasks = listOf(task("t-run", rule = "WEEKLY:TUE,SAT").copy(goalId = "g-new", source = Source.AI)),
            progress = emptyList(), capacity = Capacity(45, 90), skippedDuplicates = emptyList(),
        )
        repo.acceptPlan(m, "draft-1", mon, "Running plan", "")
        assertNotNull(env.tasks.get("t-run"))
        assertNotNull(env.tasks.get("mine"))
        assertEquals(CompletionType.IDEAL, env.completions.get("mine", mon)!!.completionType)
        assertTrue(env.categories.getAll().any { it.id == "running" })
        val s = env.settings.current()
        assertTrue(s.onboardingDone)
        assertEquals(Capacity(45, 90), s.capacity)
        assertEquals(mon, s.planStartDate)
    }

    @Test fun `merging a category moves its tasks and widget filter`() = runTest {
        repo.ensureDefaults()
        env.tasks.upsert(task("a", rule = "DAILY", category = Cat.HOME))
        env.settings.update { it.copy(widget = WidgetSettings(categories = setOf(Cat.HOME))) }
        repo.mergeCategory(Cat.HOME, Cat.GROWTH)
        assertEquals(Cat.GROWTH, env.tasks.get("a")!!.category)
        assertTrue(env.categories.getAll().none { it.id == Cat.HOME })
        assertEquals(setOf(Cat.GROWTH), env.settings.current().widget.categories)
    }

    @Test fun `erasing everything returns to onboarding with default categories`() = runTest {
        repo.loadExamplePlan()
        env.settings.update { it.copy(onboardingDone = true, capacity = Capacity(60, 60)) }
        repo.eraseAll()
        assertTrue(env.tasks.getAll().isEmpty())
        assertTrue(env.completions.getAll().isEmpty())
        assertTrue(env.goals.getGoals().isEmpty())
        assertEquals(Cat.DEFAULTS.size, env.categories.getAll().size)
        assertFalse(env.settings.current().onboardingDone)
        assertNull(env.settings.current().capacity.weekdayMinutes)
    }
}
