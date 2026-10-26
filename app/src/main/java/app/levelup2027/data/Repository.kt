package app.levelup2027.data

import app.levelup2027.ai.PlanChange
import app.levelup2027.ai.PlanMaterialization
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
    private val categoryDao: CategoryDao,
    private val planDao: PlanDao,
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
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val overrides: Flow<List<OccurrenceOverride>> = planDao.observeOverrides()
    val outcomes: Flow<List<WeeklyOutcome>> = planDao.observeOutcomes()
    val changeLog: Flow<List<ChangeLogEntry>> = planDao.observeChangeLog()

    // ---- Start-up ----

    /**
     * Runs on every start: default categories on a fresh install, the one-time v1 → v2 settings
     * upgrade, and new example-plan rows for users who already use the example plan.
     */
    suspend fun ensureDefaults() {
        if (categoryDao.getAll().isEmpty()) categoryDao.insertIfAbsent(Cat.DEFAULTS)
        val s = settings.current()
        if (s.settingsVersion < AppSettings.CURRENT_VERSION) {
            val disabled = s.legacyDisabledCategoryIds
            if (disabled.isNotEmpty()) {
                categoryDao.getAll().filter { it.id in disabled }.forEach { categoryDao.upsert(it.copy(enabled = false)) }
            }
            settings.update { it.migrated() }
        }
        if (s.seedVersion in 1 until Seed.VERSION) insertExamplePlan(updateSettings = true)
        onChanged(ChangeKind.TASKS)
    }

    /** Loads the "Level Up 2027" example plan. Existing rows with the same ids are left untouched. */
    suspend fun loadExamplePlan() {
        insertExamplePlan(updateSettings = true)
        settings.update { it.copy(onboardingDone = true, planStartDate = it.planStartDate ?: Campaign.START) }
        onChanged(ChangeKind.TASKS)
    }

    private suspend fun insertExamplePlan(updateSettings: Boolean) {
        val first = settings.current().seedVersion == 0
        runInTransaction {
            if (categoryDao.getAll().isEmpty()) categoryDao.insertIfAbsent(Cat.DEFAULTS)
            taskDao.insertIfAbsent(Seed.allTasks.map { it.copy(createdAt = clock(), updatedAt = clock()) })
            goalDao.insertGoalsIfAbsent(Seed.goals)
            goalDao.insertMilestonesIfAbsent(Seed.milestones)
            if (first && goalDao.progressCount() == 0) {
                Seed.initialProgress.forEach { goalDao.insertProgress(it.copy(createdAt = clock())) }
            }
        }
        if (updateSettings) settings.update { it.copy(seedVersion = Seed.VERSION) }
    }

    /** Restores the example plan. History (completions, progress, reviews) is kept unless [clearHistory]. */
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

    /** Deletes every plan, record and setting on this phone and returns to onboarding. */
    suspend fun eraseAll() {
        runInTransaction {
            taskDao.deleteAll(); completionDao.deleteAll(); reviewDao.deleteAll()
            goalDao.deleteGoals(); goalDao.deleteMilestones(); goalDao.deleteProgressAll()
            planDao.deleteOutcomes(); planDao.deleteOverrides(); planDao.deleteDrafts(); planDao.deleteChangeLog()
            categoryDao.deleteAll(); categoryDao.insertAll(Cat.DEFAULTS)
        }
        settings.update { AppSettings(settingsVersion = AppSettings.CURRENT_VERSION, theme = it.theme) }
        onChanged(ChangeKind.SETTINGS)
    }

    // ---- Completions ----

    suspend fun complete(task: Task, date: LocalDate, type: CompletionType, skipReason: SkipReason? = null, notes: String = "") {
        completionDao.put(
            Completion(
                taskId = task.id, taskTitle = task.title, scheduledDate = date, completionType = type,
                completedAt = clock(), skipReason = if (type == CompletionType.SKIPPED) skipReason else null, notes = notes,
            ),
        )
        if ((type == CompletionType.IDEAL || type == CompletionType.MINIMUM) && !settings.current().notificationPromptEligible) {
            settings.update { it.copy(notificationPromptEligible = true) }
        }
        onChanged(ChangeKind.COMPLETION)
    }

    suspend fun undo(taskId: String, date: LocalDate) {
        completionDao.delete(taskId, date)
        onChanged(ChangeKind.COMPLETION)
    }

    /**
     * Moves one occurrence to [to]. The original day keeps a RESCHEDULED record so history shows
     * what happened. A one-time task simply changes date; a recurring task gets a dated copy for
     * [to] (the habit itself is unchanged). Returns the id of the task that now carries [to].
     */
    suspend fun reschedule(task: Task, from: LocalDate, to: LocalDate, note: String = ""): String {
        require(from != to) { "Pick a different day." }
        require(completionDao.get(task.id, from)?.isDone != true) { "\"${task.title}\" is already done on that day." }
        var movedId = task.id
        runInTransaction {
            completionDao.put(
                Completion(
                    taskId = task.id, taskTitle = task.title, scheduledDate = from, completionType = CompletionType.RESCHEDULED,
                    completedAt = clock(), notes = note, movedToDate = to,
                ),
            )
            planDao.deleteOverride(task.id, from)
            if (task.isOneTime) {
                taskDao.upsert(task.copy(scheduledDate = to, updatedAt = clock()))
                // A record already on the new date would hide the moved task.
                if (completionDao.get(task.id, to)?.completionType == CompletionType.RESCHEDULED) completionDao.delete(task.id, to)
            } else {
                movedId = "${task.id}~$to"
                taskDao.upsert(
                    task.copy(
                        id = movedId, scheduledDate = to, recurrenceRule = null, activeFrom = null, activeUntil = null,
                        supersedesTaskId = null, originTaskId = task.id, originDate = from, isSeed = false,
                        createdAt = clock(), updatedAt = clock(),
                    ),
                )
            }
        }
        onChanged(ChangeKind.TASKS)
        return movedId
    }

    suspend fun setPreferMinimum(taskId: String, date: LocalDate, on: Boolean) {
        if (on) planDao.upsertOverride(OccurrenceOverride(taskId, date, preferMinimum = true, createdAt = clock()))
        else planDao.deleteOverride(taskId, date)
        onChanged(ChangeKind.COMPLETION)
    }

    /**
     * Widget checkbox: completes an open task with the version the widget shows, or clears an
     * existing record (so an accidental tap is reversible from the widget too).
     */
    suspend fun toggleFromWidget(taskId: String, date: LocalDate): CompletionType? {
        val existing = completionDao.get(taskId, date)
        if (existing != null && existing.completionType != CompletionType.RESCHEDULED) {
            undo(taskId, date)
            return null
        }
        val task = taskDao.get(taskId) ?: return null
        val s = settings.current()
        val preferMin = planDao.overridesFor(date).any { it.taskId == taskId && it.preferMinimum }
        val type = if (s.isLowEnergy(date) || s.widget.mode == WidgetMode.MINIMUM || preferMin) CompletionType.MINIMUM else CompletionType.IDEAL
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

    // ---- Categories ----

    suspend fun saveCategory(c: CategoryEntity) {
        categoryDao.upsert(c)
        onChanged(ChangeKind.TASKS)
    }

    /** Moves every task and goal in [fromId] to [toId], then removes [fromId]. History is unaffected. */
    suspend fun mergeCategory(fromId: String, toId: String) {
        require(fromId != toId)
        runInTransaction {
            taskDao.reassignCategory(fromId, toId)
            goalDao.getGoals().filter { it.categoryId == fromId }.forEach { goalDao.upsertGoal(it.copy(categoryId = toId)) }
            categoryDao.delete(fromId)
            val s = settings.current()
            if (fromId in s.widget.categories) settings.update { it.copy(widget = it.widget.copy(categories = it.widget.categories - fromId + toId)) }
        }
        onChanged(ChangeKind.TASKS)
    }

    suspend fun createCategory(name: String, iconKey: String): CategoryEntity {
        val all = categoryDao.getAll()
        var id = "c-" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24).ifBlank { "category" }
        while (all.any { it.id == id }) id += "-2"
        val c = CategoryEntity(id, name.trim(), name.trim().take(12), iconKey, (all.maxOfOrNull { it.colorIndex } ?: -1) + 1, true, (all.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        categoryDao.upsert(c)
        return c
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

    suspend fun updateGoal(goal: Goal) { goalDao.upsertGoal(goal); onChanged(ChangeKind.TASKS) }
    suspend fun setMilestoneDone(m: Milestone, done: Boolean) = goalDao.upsertMilestone(m.copy(done = done))
    suspend fun setOutcomeDone(o: WeeklyOutcome, done: Boolean) = planDao.upsertOutcomes(listOf(o.copy(done = done)))

    private suspend fun refreshGoalSummary(goalId: String) {
        val goal = goalDao.getGoals().firstOrNull { it.id == goalId } ?: return
        val latest = latestValues(goalDao.getProgress().filter { it.goalId == goalId })
        goalDao.upsertGoal(goal.copy(currentProgress = GoalSummary.headline(goal, latest)))
    }

    // ---- Reviews ----

    suspend fun reviewFor(weekStart: LocalDate) = reviewDao.forWeek(weekStart)

    suspend fun saveReview(r: WeeklyReview) {
        val existing = reviewDao.forWeek(r.weekStartDate)
        reviewDao.upsert(r.copy(id = existing?.id ?: 0, createdAt = existing?.createdAt ?: clock(), updatedAt = clock()))
    }

    // ---- AI plans and changes (validated before they get here) ----

    suspend fun getAllTasks() = taskDao.getAll()
    suspend fun getAllCompletions() = completionDao.getAll()
    suspend fun getGoals() = goalDao.getGoals()
    suspend fun getMilestones() = goalDao.getMilestones()
    suspend fun getCategories() = categoryDao.getAll()
    suspend fun getOutcomes() = planDao.getOutcomes()

    suspend fun saveDraft(d: PlanDraftRecord) = planDao.upsertDraft(d.copy(updatedAt = clock()))
    suspend fun draft(id: String) = planDao.draft(id)
    suspend fun latestOpenDraft(kind: String) = planDao.latestOpenDraft(kind)

    /** Adds an accepted plan in one transaction. Existing tasks, goals and history are untouched. */
    suspend fun acceptPlan(m: PlanMaterialization, draftId: String, startDate: LocalDate, summary: String, contextJson: String) {
        runInTransaction {
            categoryDao.insertIfAbsent(m.categories)
            goalDao.insertGoalsIfAbsent(m.goals)
            goalDao.insertMilestonesIfAbsent(m.milestones)
            planDao.upsertOutcomes(m.outcomes)
            taskDao.insertIfAbsent(m.tasks)
            m.progress.forEach { goalDao.insertProgress(it) }
            planDao.draft(draftId)?.let { planDao.upsertDraft(it.copy(status = DraftStatus.ACCEPTED, updatedAt = clock())) }
            planDao.log(ChangeLogEntry(at = clock(), source = "PLAN_ACCEPTED", summary = summary,
                detail = "${m.goals.size} goals, ${m.tasks.size} tasks" + if (m.skippedDuplicates.isEmpty()) "" else "; skipped: ${m.skippedDuplicates.joinToString("; ")}"))
        }
        settings.update {
            it.copy(
                onboardingDone = true,
                planStartDate = it.planStartDate ?: startDate,
                capacity = if (m.capacity.isSet) m.capacity else it.capacity,
                userContextJson = contextJson.ifBlank { it.userContextJson },
            )
        }
        onChanged(ChangeKind.SETTINGS)
    }

    suspend fun acceptWeek(tasks: List<Task>, outcomes: List<WeeklyOutcome>, draftId: String?, summary: String) {
        val existing = taskDao.getAll()
        val fresh = tasks.filter { t -> existing.none { it.enabled && it.scheduledDate == t.scheduledDate && it.title.equals(t.title, ignoreCase = true) } }
        runInTransaction {
            taskDao.insertIfAbsent(fresh)
            planDao.upsertOutcomes(outcomes)
            draftId?.let { id -> planDao.draft(id)?.let { planDao.upsertDraft(it.copy(status = DraftStatus.ACCEPTED, updatedAt = clock())) } }
            planDao.log(ChangeLogEntry(at = clock(), source = "WEEK_PLANNED", summary = summary, detail = "${fresh.size} tasks"))
        }
        onChanged(ChangeKind.TASKS)
    }

    /**
     * Applies validated changes. Nothing here deletes history: skipped/moved occurrences get a
     * record, removed habits are disabled, and completed records are never edited.
     */
    suspend fun applyChanges(changes: List<PlanChange>, source: String, requestText: String) {
        if (changes.isEmpty()) return
        val today = Dates.today()
        for (c in changes) {
            when (c) {
                is PlanChange.Reschedule -> reschedule(c.task, c.from, c.to, c.reason.take(200))
                is PlanChange.UseMinimum -> planDao.upsertOverride(OccurrenceOverride(c.task.id, c.date, true, c.reason.take(200), clock()))
                is PlanChange.SkipOccurrence -> completionDao.put(
                    Completion(taskId = c.task.id, taskTitle = c.task.title, scheduledDate = c.date, completionType = CompletionType.SKIPPED,
                        completedAt = clock(), skipReason = SkipReason.OTHER, notes = c.reason.take(200)),
                )
                is PlanChange.UpdateTask -> taskDao.upsert(c.after.copy(updatedAt = clock()))
                is PlanChange.AddTask -> taskDao.insertIfAbsent(listOf(c.task.copy(createdAt = clock(), updatedAt = clock())))
                is PlanChange.DisableTask -> taskDao.upsert(c.task.copy(enabled = false, updatedAt = clock()))
                is PlanChange.UpdateGoal -> goalDao.upsertGoal(c.after)
                is PlanChange.SetCapacity -> settings.update { it.copy(capacity = c.after) }
                is PlanChange.SetLowEnergy -> settings.update { it.copy(lowEnergyDate = today) }
            }
        }
        planDao.log(ChangeLogEntry(at = clock(), source = source, summary = requestText.take(200), detail = changes.joinToString("\n") { it.summary }))
        onChanged(ChangeKind.SETTINGS)
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
        combine(tasks, completions, settings.flow, categories, overrides) { t, c, s, cats, ov ->
            buildWidget(today, t, c, s, cats, ov)
        }
    }

    suspend fun widgetStateNow(today: LocalDate = Dates.today()): WidgetState =
        buildWidget(today, taskDao.getAll(), completionDao.forDate(today), settings.current(), categoryDao.getAll(), planDao.overridesFor(today))

    private fun buildWidget(today: LocalDate, t: List<Task>, c: List<Completion>, s: AppSettings, cats: List<CategoryEntity>, ov: List<OccurrenceOverride>) =
        WidgetStateBuilder.build(
            today, t, c, hiddenCategories(cats), s.widget.toConfig(), s.isLowEnergy(today),
            preferMinimum = ov.filter { it.date == today && it.preferMinimum }.mapTo(HashSet()) { it.taskId },
            categoryColors = cats.associate { it.id to it.colorIndex },
        )

    /** Open tasks in a slot today, used for reminder text. */
    suspend fun openTasksFor(today: LocalDate, slot: TimeSlot?): List<Task> =
        ScheduleEngine.occurrencesFor(today, taskDao.getAll(), completionDao.forDate(today), hiddenCategories(categoryDao.getAll()))
            .filter { it.isOpen && (slot == null || it.task.timeSlot == slot) }
            .map { it.task }

    suspend fun snapshotCompletions() = completions.first()

    companion object {
        fun latestValues(entries: List<ProgressEntry>): Map<String, ProgressEntry> =
            entries.sortedWith(compareBy({ it.date }, { it.createdAt })).associateBy { it.metric }

        fun hiddenCategories(cats: List<CategoryEntity>): Set<String> = cats.filter { !it.enabled }.mapTo(HashSet()) { it.id }
    }
}

object DraftStatus {
    const val OPEN = "OPEN"
    const val ACCEPTED = "ACCEPTED"
    const val DISCARDED = "DISCARDED"
}

object DraftKind {
    const val ONBOARDING = "ONBOARDING"
    const val WEEK = "WEEK"
}

object GoalSummary {
    fun headline(goal: Goal, latest: Map<String, ProgressEntry>): String {
        fun v(k: String) = latest[k]?.let { Metrics.format(Metrics.def(goal, k), it.value) + if (it.isEstimate) " (estimate)" else "" }
        return when (goal.id) {
            GoalIds.CAREER -> v("salary_lpa")?.let { "₹$it" } ?: "Not recorded yet"
            GoalIds.FITNESS -> listOfNotNull(v("weight_kg"), v("pushups")?.let { "push-ups $it" }, v("pullups")?.let { "pull-ups $it" })
                .joinToString(" · ").ifEmpty { "Not recorded yet" }
            GoalIds.TECH -> listOfNotNull(v("eng_exercises")?.let { "$it exercises" }, v("analysed_problems")?.let { "$it problems analysed" })
                .joinToString(" · ").ifEmpty { "Starting out" }
            GoalIds.FAMILY -> v("fund_balance")?.let { "$it saved" } ?: "Not recorded yet"
            GoalIds.BUSINESS -> v("mrr")?.let { "$it MRR (actual)" } ?: "Not recorded yet"
            else -> Metrics.forGoal(goal).mapNotNull { d -> v(d.key)?.let { "${d.label}: $it" } }.take(2).joinToString(" · ")
                .ifEmpty { goal.baseline.ifBlank { "Not recorded yet" } }
        }
    }
}
