package app.levelup2027

import app.levelup2027.data.Completion
import app.levelup2027.data.CompletionDao
import app.levelup2027.data.CompletionType
import app.levelup2027.data.Goal
import app.levelup2027.data.GoalDao
import app.levelup2027.data.MemorySettings
import app.levelup2027.data.Milestone
import app.levelup2027.data.ProgressEntry
import app.levelup2027.data.Repository
import app.levelup2027.data.ReviewDao
import app.levelup2027.data.Seed
import app.levelup2027.data.SkipReason
import app.levelup2027.data.Task
import app.levelup2027.data.TaskDao
import app.levelup2027.data.WeeklyReview
import app.levelup2027.domain.WidgetMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RepositoryTest {
    private val env = TestEnv()
    private val tasks = env.tasks
    private val completions = env.completions
    private val goals = env.goals
    private val reviews = env.reviews
    private val settings = env.settings
    private val changes = env.changes
    private val repo = env.repo
    private val mon = LocalDate.of(2026, 11, 2)

    @Test fun `seeding is idempotent and never overwrites edits`() = runTest {
        repo.loadExamplePlan()
        val n = tasks.getAll().size
        assertEquals(Seed.allTasks.size, n)
        assertEquals(Seed.initialProgress.size, goals.progress.size)
        tasks.upsert(tasks.get("mon-tech-js")!!.copy(title = "My edit"))
        repo.ensureDefaults()
        settings.update { it.copy(seedVersion = 0) } // force a re-seed attempt
        repo.loadExamplePlan()
        assertEquals(n, tasks.getAll().size)
        assertEquals("My edit", tasks.get("mon-tech-js")!!.title)
        assertEquals(Seed.initialProgress.size, goals.progress.size)
    }

    @Test fun `completing records the completion type and replaces earlier records for the day`() = runTest {
        repo.loadExamplePlan()
        val t = tasks.get("mon-tech-js")!!
        repo.complete(t, mon, CompletionType.MINIMUM)
        assertEquals(CompletionType.MINIMUM, completions.get(t.id, mon)!!.completionType)
        repo.complete(t, mon, CompletionType.IDEAL)
        assertEquals(1, completions.rows.count { it.taskId == t.id && it.scheduledDate == mon })
        assertEquals(CompletionType.IDEAL, completions.get(t.id, mon)!!.completionType)
        repo.undo(t.id, mon)
        assertNull(completions.get(t.id, mon))
    }

    @Test fun `skip stores the reason, other types drop it`() = runTest {
        repo.loadExamplePlan()
        val t = tasks.get("mon-gym")!!
        repo.complete(t, mon, CompletionType.SKIPPED, SkipReason.TOO_TIRED, "long commute")
        assertEquals(SkipReason.TOO_TIRED, completions.get(t.id, mon)!!.skipReason)
        assertEquals("long commute", completions.get(t.id, mon)!!.notes)
        repo.complete(t, mon, CompletionType.MINIMUM, SkipReason.TOO_TIRED)
        assertNull(completions.get(t.id, mon)!!.skipReason)
    }

    @Test fun `editing a task keeps its completion history`() = runTest {
        repo.loadExamplePlan()
        val t = tasks.get("tue-tech-backend")!!
        repo.complete(t, mon.plusDays(1), CompletionType.IDEAL)
        repo.saveTask(t.copy(title = "Backend deep-dive", estimatedIdealMinutes = 45))
        val c = completions.get(t.id, mon.plusDays(1))!!
        assertEquals(CompletionType.IDEAL, c.completionType)
        assertEquals("Backend engineering study", c.taskTitle)
    }

    @Test fun `widget toggle completes with the widget mode, then undoes, and refreshes each time`() = runTest {
        repo.loadExamplePlan()
        changes.clear()
        assertEquals(CompletionType.IDEAL, repo.toggleFromWidget("mon-tech-js", mon))
        assertEquals(CompletionType.IDEAL, completions.get("mon-tech-js", mon)!!.completionType)
        assertNull(repo.toggleFromWidget("mon-tech-js", mon))
        assertNull(completions.get("mon-tech-js", mon))
        assertEquals(2, changes.count { it == Repository.ChangeKind.COMPLETION })

        settings.update { it.copy(widget = it.widget.copy(mode = WidgetMode.MINIMUM)) }
        assertEquals(CompletionType.MINIMUM, repo.toggleFromWidget("mon-tech-js", mon))
    }

    @Test fun `low-energy day makes the widget complete minimum versions, for that day only`() = runTest {
        repo.loadExamplePlan()
        repo.setLowEnergy(mon, true)
        assertEquals(CompletionType.MINIMUM, repo.toggleFromWidget("mon-gym", mon))
        assertEquals(CompletionType.IDEAL, repo.toggleFromWidget("tue-gym", mon.plusDays(1)))
        // Task definitions are untouched.
        assertEquals(Seed.recurringTasks.first { it.id == "mon-gym" }.idealDescription, tasks.get("mon-gym")!!.idealDescription)
    }

    @Test fun `widget state reflects a widget completion`() = runTest {
        repo.loadExamplePlan()
        val before = repo.widgetStateNow(mon)
        val first = before.items.first()
        repo.toggleFromWidget(first.taskId, mon)
        val after = repo.widgetStateNow(mon)
        assertEquals(before.done + 1, after.done)
        assertTrue(after.items.none { it.taskId == first.taskId })
    }

    @Test fun `first completion makes the notification prompt eligible`() = runTest {
        repo.loadExamplePlan()
        assertEquals(false, settings.flow.first().notificationPromptEligible)
        repo.complete(tasks.get("daily-morning")!!, mon, CompletionType.SKIPPED)
        assertEquals(false, settings.flow.first().notificationPromptEligible)
        repo.complete(tasks.get("daily-morning")!!, mon, CompletionType.MINIMUM)
        assertEquals(true, settings.flow.first().notificationPromptEligible)
    }

    @Test fun `progress entries update the goal headline`() = runTest {
        repo.loadExamplePlan()
        repo.addProgress("family", "fund_balance", 5000.0, mon)
        assertEquals("₹5,000 saved", goals.goals["family"]!!.currentProgress)
        repo.addProgress("business", "mrr", 1500.0, mon)
        assertEquals("₹1,500 MRR (actual)", goals.goals["business"]!!.currentProgress)
    }

    @Test fun `reset plan keeps history unless asked to clear it`() = runTest {
        repo.loadExamplePlan()
        repo.complete(tasks.get("mon-gym")!!, mon, CompletionType.IDEAL)
        repo.saveReview(WeeklyReview(weekStartDate = mon, wins = "showed up"))
        repo.resetPlan(clearHistory = false)
        assertEquals(1, completions.rows.size)
        assertEquals(1, reviews.rows.size)
        repo.resetPlan(clearHistory = true)
        assertEquals(0, completions.rows.size)
        assertEquals(0, reviews.rows.size)
        assertEquals(Seed.allTasks.size, tasks.getAll().size)
    }

    @Test fun `saving a review twice for a week updates it`() = runTest {
        repo.saveReview(WeeklyReview(weekStartDate = mon, wins = "a"))
        repo.saveReview(WeeklyReview(weekStartDate = mon, wins = "b"))
        assertEquals(1, reviews.rows.size)
        assertEquals("b", reviews.rows.single().wins)
    }
}
