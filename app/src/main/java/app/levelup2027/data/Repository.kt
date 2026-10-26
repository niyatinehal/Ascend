package app.levelup2027.data

import app.levelup2027.domain.Dates
import app.levelup2027.domain.ScheduleEngine
import app.levelup2027.domain.WidgetMode
import app.levelup2027.domain.WidgetState
import app.levelup2027.domain.WidgetStateBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate

/**
 * All reads and writes go through here. [onChanged] is called after every write that affects
 * what the widget or reminders show (the app wires it to a widget refresh).
 */
class Repository(
    private val taskDao: TaskDao,
    private val completionDao: CompletionDao,
    private val goalDao: GoalDao,
    private val reviewDao: ReviewDao,
    val settings: SettingsSource,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit,
    private val onChanged: suspend (ChangeKind) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class ChangeKind { COMPLETION, TASKS, SETTINGS, REMINDERS, OTHER }

    val tasks: Flow<List<Task>> = taskDao.observeAll()
    val completions: Flow<List<Completion>> = completionDao.observeAll()
    val goals: Flow<List<Goal>> = goalDao.observeGoals()
    val milestones: Flow<List<Milestone>> = goalDao.observeMilestones()
    val progress: Flow<List<ProgressEntry>> = goalDao.observeProgress()
    val reviews: Flow<List<WeeklyReview>> = reviewDao.observeAll()

    // ---- Seeding ----

    suspend fun ensureSeeded() {
        val s = settings.current()
        if (s.seedVersion >= Seed.VERSION) return
        runInTransaction {
            taskDao.insertIfAbsent(Seed.allTasks.map { it.copy(createdAt = clock(), updatedAt = clock()) })
            goalDao.insertGoalsIfAbsent(Seed.goals)
            goalDao.insertMilestonesIfAbsent(Seed.milestones)
            if (s.seedVersion == 0 && goalDao.progressCount() == 0) {
                Seed.initialProgress.forEach { goalDao.insertProgress(it.copy(createdAt = clock())) }
            }
        }
        settings.update { it.copy(seedVersion = Seed.VERSION) }
        onChanged(ChangeKind.TASKS)
    }

    /** Restores the default plan. History (completions, progress, reviews) is kept unless [clearHistory]. */
    suspend fun resetPlan(clearHistory: Boolean) {
        runInTransaction {
            taskDao.deleteAll()
            taskDao.insertAll(Seed.allTasks.map { it.copy(createdAt = clock(), updatedAt = clock()) })
            goalDao.deleteGoals(); goalDao.insertGoals(Seed.goals)
            goalDao.deleteMilestones(); goalDao.insertMilestones(Seed.milestones)
            if (clearHistory) {
                completionDao.deleteAll()
                reviewDao.deleteAll()
                goalDao.deleteProgressAll()
                Seed.initialProgress.forEach { goalDao.insertProgress(it.copy(createdAt = clock())) }
            }
        }
        onChanged(ChangeKind.TASKS)
    }

    // ---- Completions ----

    suspend fun complete(task: Task, date: LocalDate, type: CompletionType, skipReason: SkipReason? = null, notes: String = "") {
        completionDao.put(
            Completion(
                taskId = task.id, taskTitle = task.title, scheduledDate = date, completionType = type,
                completedAt = clock(), skipReason = if (type == CompletionType.SKIPPED) skipReason else null, notes = notes,
            ),
        )
        if (type != CompletionType.SKIPPED && !settings.current().notificationPromptEligible) {
            settings.update { it.copy(notificationPromptEligible = true) }
        }
        onChanged(ChangeKind.COMPLETION)
    }

    suspend fun undo(taskId: String, date: LocalDate) {
        completionDao.delete(taskId, date)
        onChanged(ChangeKind.COMPLETION)
    }

    /**
     * Widget checkbox: completes an open task with the version the widget shows, or clears an
     * existing record (so an accidental tap is reversible from the widget too).
     */
    suspend fun toggleFromWidget(taskId: String, date: LocalDate): CompletionType? {
        val existing = completionDao.get(taskId, date)
        if (existing != null) {
            undo(taskId, date)
            return null
        }
        val task = taskDao.get(taskId) ?: return null
        val s = settings.current()
        val type = if (s.isLowEnergy(date) || s.widget.mode == WidgetMode.MINIMUM) CompletionType.MINIMUM else CompletionType.IDEAL
        complete(task, date, type)
        return type
    }

    // ---- Tasks ----

    suspend fun getTask(id: String) = taskDao.get(id)

    suspend fun saveTask(task: Task) {
        val existing = taskDao.get(task.id)
        taskDao.upsert(task.copy(createdAt = existing?.createdAt ?: clock(), updatedAt = clock()))
        onChanged(ChangeKind.TASKS)
    }

    suspend fun deleteTask(id: String) {
        taskDao.delete(id) // completion history is kept (it stores the task title)
        onChanged(ChangeKind.TASKS)
    }

    // ---- Goals ----

    suspend fun addProgress(goalId: String, metric: String, value: Double, date: LocalDate, notes: String = "") {
        goalDao.insertProgress(ProgressEntry(goalId = goalId, date = date, metric = metric, value = value, notes = notes, createdAt = clock()))
        refreshGoalSummary(goalId)
        onChanged(ChangeKind.OTHER)
    }

    suspend fun deleteProgress(e: ProgressEntry) {
        goalDao.deleteProgress(e)
        refreshGoalSummary(e.goalId)
    }

    suspend fun updateGoal(goal: Goal) = goalDao.upsertGoal(goal)
    suspend fun setMilestoneDone(m: Milestone, done: Boolean) = goalDao.upsertMilestone(m.copy(done = done))

    private suspend fun refreshGoalSummary(goalId: String) {
        val goal = goalDao.getGoals().firstOrNull { it.id == goalId } ?: return
        val latest = latestValues(goalDao.getProgress())
        goalDao.upsertGoal(goal.copy(currentProgress = GoalSummary.headline(goalId, latest)))
    }

    // ---- Reviews ----

    suspend fun reviewFor(weekStart: LocalDate) = reviewDao.forWeek(weekStart)

    suspend fun saveReview(r: WeeklyReview) {
        val existing = reviewDao.forWeek(r.weekStartDate)
        reviewDao.upsert(r.copy(id = existing?.id ?: 0, createdAt = existing?.createdAt ?: clock(), updatedAt = clock()))
    }

    // ---- Settings ----

    suspend fun updateSettings(kind: ChangeKind = ChangeKind.SETTINGS, transform: (AppSettings) -> AppSettings) {
        settings.update(transform)
        onChanged(kind)
    }

    suspend fun setLowEnergy(today: LocalDate, on: Boolean) =
        updateSettings { it.copy(lowEnergyDate = if (on) today else null) }

    // ---- Widget ----

    @OptIn(ExperimentalCoroutinesApi::class)
    fun widgetState(): Flow<WidgetState> = Dates.todayFlow().flatMapLatest { today ->
        combine(tasks, completions, settings.flow) { t, c, s ->
            WidgetStateBuilder.build(today, t, c, s.enabledCategories, s.widget.toConfig(), s.isLowEnergy(today))
        }
    }

    suspend fun widgetStateNow(today: LocalDate = Dates.today()): WidgetState {
        val s = settings.current()
        return WidgetStateBuilder.build(today, taskDao.getAll(), completionDao.forDate(today), s.enabledCategories, s.widget.toConfig(), s.isLowEnergy(today))
    }

    /** Open tasks in a slot today, used for reminder text. */
    suspend fun openTasksFor(today: LocalDate, slot: TimeSlot?): List<Task> {
        val s = settings.current()
        return ScheduleEngine.occurrencesFor(today, taskDao.getAll(), completionDao.forDate(today), s.enabledCategories)
            .filter { it.isOpen && (slot == null || it.task.timeSlot == slot) }
            .map { it.task }
    }

    suspend fun snapshotCompletions() = completions.first()

    companion object {
        fun latestValues(entries: List<ProgressEntry>): Map<String, ProgressEntry> =
            entries.sortedWith(compareBy({ it.date }, { it.createdAt })).associateBy { it.metric }
    }
}

object GoalSummary {
    fun headline(goalId: String, latest: Map<String, ProgressEntry>): String {
        fun v(k: String) = latest[k]?.let { Metrics.format(Metrics.byKey(k), it.value) + if (it.isEstimate) " (estimate)" else "" }
        return when (goalId) {
            GoalIds.CAREER -> v("salary_lpa")?.let { "₹$it" } ?: "Not recorded yet"
            GoalIds.FITNESS -> listOfNotNull(v("weight_kg"), v("pushups")?.let { "push-ups $it" }, v("pullups")?.let { "pull-ups $it" })
                .joinToString(" · ").ifEmpty { "Not recorded yet" }
            GoalIds.TECH -> listOfNotNull(v("eng_exercises")?.let { "$it exercises" }, v("analysed_problems")?.let { "$it problems analysed" })
                .joinToString(" · ").ifEmpty { "Starting out" }
            GoalIds.FAMILY -> v("fund_balance")?.let { "$it saved" } ?: "Not recorded yet"
            GoalIds.BUSINESS -> v("mrr")?.let { "$it MRR (actual)" } ?: "Not recorded yet"
            else -> ""
        }
    }
}
