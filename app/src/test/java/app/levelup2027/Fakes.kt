package app.levelup2027

import app.levelup2027.data.AiUsage
import app.levelup2027.data.AiUsageDao
import app.levelup2027.data.CategoryDao
import app.levelup2027.data.CategoryEntity
import app.levelup2027.data.ChangeLogEntry
import app.levelup2027.data.Completion
import app.levelup2027.data.CompletionDao
import app.levelup2027.data.Goal
import app.levelup2027.data.GoalDao
import app.levelup2027.data.MemorySettings
import app.levelup2027.data.Milestone
import app.levelup2027.data.OccurrenceOverride
import app.levelup2027.data.PlanDao
import app.levelup2027.data.PlanDraftRecord
import app.levelup2027.data.ProgressEntry
import app.levelup2027.data.Repository
import app.levelup2027.data.ReviewDao
import app.levelup2027.data.Task
import app.levelup2027.data.TaskDao
import app.levelup2027.data.WeeklyOutcome
import app.levelup2027.data.WeeklyReview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeTaskDao : TaskDao {
    val state = MutableStateFlow<Map<String, Task>>(emptyMap())
    override fun observeAll(): Flow<List<Task>> = state.map { it.values.toList() }
    override suspend fun getAll() = state.value.values.toList()
    override suspend fun get(id: String) = state.value[id]
    override suspend fun insertIfAbsent(tasks: List<Task>) { state.value = tasks.associateBy { it.id } + state.value }
    override suspend fun upsert(task: Task) { state.value = state.value + (task.id to task) }
    override suspend fun delete(id: String) { state.value = state.value - id }
    override suspend fun deleteAll() { state.value = emptyMap() }
    override suspend fun insertAll(tasks: List<Task>) { state.value = state.value + tasks.associateBy { it.id } }
    override suspend fun reassignCategory(from: String, to: String) {
        state.value = state.value.mapValues { (_, t) -> if (t.category == from) t.copy(category = to) else t }
    }
}

/** Mirrors the unique (taskId, scheduledDate) index: a new record replaces the old one. */
class FakeCompletionDao : CompletionDao {
    val rows = mutableListOf<Completion>()
    private var nextId = 1L
    override fun observeAll(): Flow<List<Completion>> = MutableStateFlow(rows.toList())
    override suspend fun getAll() = rows.toList()
    override suspend fun forDate(date: LocalDate) = rows.filter { it.scheduledDate == date }
    override suspend fun get(taskId: String, date: LocalDate) = rows.firstOrNull { it.taskId == taskId && it.scheduledDate == date }
    override suspend fun put(c: Completion) {
        rows.removeAll { it.taskId == c.taskId && it.scheduledDate == c.scheduledDate }
        rows += c.copy(id = nextId++)
    }
    override suspend fun delete(taskId: String, date: LocalDate) { rows.removeAll { it.taskId == taskId && it.scheduledDate == date } }
    override suspend fun deleteAll() = rows.clear()
    override suspend fun insertAll(list: List<Completion>) { list.forEach { put(it) } }
}

class FakeGoalDao : GoalDao {
    val goals = mutableMapOf<String, Goal>()
    val milestones = mutableMapOf<String, Milestone>()
    val progress = mutableListOf<ProgressEntry>()
    override fun observeGoals(): Flow<List<Goal>> = MutableStateFlow(goals.values.toList())
    override suspend fun getGoals() = goals.values.toList()
    override suspend fun insertGoalsIfAbsent(goals: List<Goal>) { goals.forEach { this.goals.putIfAbsent(it.id, it) } }
    override suspend fun upsertGoal(goal: Goal) { goals[goal.id] = goal }
    override fun observeMilestones(): Flow<List<Milestone>> = MutableStateFlow(milestones.values.toList())
    override suspend fun getMilestones() = milestones.values.toList()
    override suspend fun insertMilestonesIfAbsent(list: List<Milestone>) { list.forEach { milestones.putIfAbsent(it.id, it) } }
    override suspend fun upsertMilestone(m: Milestone) { milestones[m.id] = m }
    override fun observeProgress(): Flow<List<ProgressEntry>> = MutableStateFlow(progress.toList())
    override suspend fun getProgress() = progress.toList()
    override suspend fun progressCount() = progress.size
    override suspend fun insertProgress(e: ProgressEntry) { progress += e.copy(id = progress.size + 1L) }
    override suspend fun deleteProgress(e: ProgressEntry) { progress.removeAll { it.id == e.id } }
    override suspend fun deleteGoals() = goals.clear()
    override suspend fun deleteMilestones() = milestones.clear()
    override suspend fun deleteProgressAll() = progress.clear()
    override suspend fun insertGoals(list: List<Goal>) { list.forEach { goals[it.id] = it } }
    override suspend fun insertMilestones(list: List<Milestone>) { list.forEach { milestones[it.id] = it } }
    override suspend fun insertProgressAll(list: List<ProgressEntry>) { progress += list }
}

class FakeReviewDao : ReviewDao {
    val rows = mutableListOf<WeeklyReview>()
    override fun observeAll(): Flow<List<WeeklyReview>> = MutableStateFlow(rows.toList())
    override suspend fun getAll() = rows.toList()
    override suspend fun forWeek(weekStart: LocalDate) = rows.firstOrNull { it.weekStartDate == weekStart }
    override suspend fun upsert(r: WeeklyReview) {
        rows.removeAll { it.weekStartDate == r.weekStartDate }
        rows += r.copy(id = if (r.id == 0L) rows.size + 1L else r.id)
    }
    override suspend fun deleteAll() = rows.clear()
    override suspend fun insertAll(list: List<WeeklyReview>) { rows += list }
}


class FakeCategoryDao : CategoryDao {
    val state = MutableStateFlow<List<CategoryEntity>>(emptyList())
    override fun observeAll(): Flow<List<CategoryEntity>> = state
    override suspend fun getAll() = state.value.sortedBy { it.sortOrder }
    override suspend fun insertIfAbsent(list: List<CategoryEntity>) { state.value = state.value + list.filter { n -> state.value.none { it.id == n.id } } }
    override suspend fun upsert(c: CategoryEntity) { state.value = state.value.filter { it.id != c.id } + c }
    override suspend fun delete(id: String) { state.value = state.value.filter { it.id != id } }
    override suspend fun deleteAll() { state.value = emptyList() }
    override suspend fun insertAll(list: List<CategoryEntity>) { list.forEach { upsert(it) } }
}

class FakePlanDao : PlanDao {
    val outcomes = MutableStateFlow<List<WeeklyOutcome>>(emptyList())
    val overrides = MutableStateFlow<List<OccurrenceOverride>>(emptyList())
    val drafts = mutableMapOf<String, PlanDraftRecord>()
    val log = MutableStateFlow<List<ChangeLogEntry>>(emptyList())
    override fun observeOutcomes(): Flow<List<WeeklyOutcome>> = outcomes
    override suspend fun getOutcomes() = outcomes.value
    override suspend fun upsertOutcomes(list: List<WeeklyOutcome>) { outcomes.value = outcomes.value.filter { o -> list.none { it.id == o.id } } + list }
    override suspend fun deleteOutcomes() { outcomes.value = emptyList() }
    override fun observeOverrides(): Flow<List<OccurrenceOverride>> = overrides
    override suspend fun getOverrides() = overrides.value
    override suspend fun overridesFor(date: LocalDate) = overrides.value.filter { it.date == date }
    override suspend fun upsertOverride(o: OccurrenceOverride) { deleteOverride(o.taskId, o.date); overrides.value = overrides.value + o }
    override suspend fun deleteOverride(taskId: String, date: LocalDate) { overrides.value = overrides.value.filterNot { it.taskId == taskId && it.date == date } }
    override suspend fun deleteOverrides() { overrides.value = emptyList() }
    override suspend fun insertOverrides(list: List<OccurrenceOverride>) { list.forEach { upsertOverride(it) } }
    override suspend fun draft(id: String) = drafts[id]
    override suspend fun latestOpenDraft(kind: String) = drafts.values.filter { it.kind == kind && it.status == "OPEN" }.maxByOrNull { it.updatedAt }
    override suspend fun upsertDraft(d: PlanDraftRecord) { drafts[d.id] = d }
    override suspend fun deleteDrafts() = drafts.clear()
    override fun observeChangeLog(limit: Int): Flow<List<ChangeLogEntry>> = log.map { it.take(limit) }
    override suspend fun getChangeLog() = log.value
    override suspend fun log(e: ChangeLogEntry) { log.value = listOf(e.copy(id = log.value.size + 1L)) + log.value }
    override suspend fun deleteChangeLog() { log.value = emptyList() }
    override suspend fun insertChangeLog(list: List<ChangeLogEntry>) { log.value = log.value + list }
}

class FakeAiUsageDao : AiUsageDao {
    val rows = mutableListOf<AiUsage>()
    override suspend fun insert(u: AiUsage) { rows += u.copy(id = rows.size + 1L) }
    override fun observeSince(since: Long): Flow<List<AiUsage>> = MutableStateFlow(rows.filter { it.at >= since })
    override suspend fun since(since: Long) = rows.filter { it.at >= since }
    override suspend fun deleteAll() = rows.clear()
}

/** A repository wired to in-memory fakes. */
class TestEnv(settingsInit: app.levelup2027.data.AppSettings = app.levelup2027.data.AppSettings()) {
    val tasks = FakeTaskDao()
    val completions = FakeCompletionDao()
    val goals = FakeGoalDao()
    val reviews = FakeReviewDao()
    val categories = FakeCategoryDao()
    val plan = FakePlanDao()
    val settings = MemorySettings(settingsInit)
    val changes = mutableListOf<Repository.ChangeKind>()
    val repo = Repository(tasks, completions, goals, reviews, categories, plan, settings, { it() }, { changes += it })
}
