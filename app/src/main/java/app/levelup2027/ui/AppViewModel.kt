package app.levelup2027.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.levelup2027.AppContainer
import app.levelup2027.data.AppSettings
import app.levelup2027.data.CategoryEntity
import app.levelup2027.data.ChangeLogEntry
import app.levelup2027.data.Completion
import app.levelup2027.data.CompletionType
import app.levelup2027.data.DefaultMode
import app.levelup2027.data.Goal
import app.levelup2027.data.Milestone
import app.levelup2027.data.OccurrenceOverride
import app.levelup2027.data.ProgressEntry
import app.levelup2027.data.Repository
import app.levelup2027.data.SkipReason
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import app.levelup2027.data.WeeklyOutcome
import app.levelup2027.data.WeeklyReview
import app.levelup2027.domain.DailyFocus
import app.levelup2027.domain.DailyPlanner
import app.levelup2027.domain.DailyProgress
import app.levelup2027.domain.Dates
import app.levelup2027.domain.MissedSuggestion
import app.levelup2027.domain.MissedTaskTriage
import app.levelup2027.domain.Occurrence
import app.levelup2027.domain.ScheduleEngine
import app.levelup2027.domain.Xp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class TodayFilter(val label: String, val slot: TimeSlot?) {
    TODAY("Today", null),
    MORNING("Morning", TimeSlot.MORNING),
    WORK("During Work", TimeSlot.WORK),
    EVENING("Evening", TimeSlot.EVENING),
    FLEXIBLE("Flexible", TimeSlot.FLEXIBLE),
    OVERDUE("Missed", null),
    COMPLETED("Completed", null);

    companion object {
        fun forSlot(slot: TimeSlot?) = entries.firstOrNull { it.slot == slot && slot != null } ?: TODAY
    }
}

data class DayState(
    val date: LocalDate,
    val occurrences: List<Occurrence>,
    val overdue: List<Occurrence>,
    val progress: DailyProgress,
    val lowEnergy: Boolean,
    val xp: Int,
    val daysShowedUp: Int,
    val focus: DailyFocus,
    val missed: List<MissedSuggestion>,
    val preferMinimum: Set<String>,
)

class AppViewModel(private val c: AppContainer) : ViewModel() {
    val repo: Repository = c.repository
    val backup = c.backup
    val ai = c.ai

    private val _today = MutableStateFlow(Dates.today())
    val today: StateFlow<LocalDate> = _today

    val tasks: StateFlow<List<Task>?> = repo.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val completions: StateFlow<List<Completion>> = repo.completions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<AppSettings?> = repo.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val goals: StateFlow<List<Goal>> = repo.goals.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val milestones: StateFlow<List<Milestone>> = repo.milestones.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val progress: StateFlow<List<ProgressEntry>> = repo.progress.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reviews: StateFlow<List<WeeklyReview>> = repo.reviews.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories: StateFlow<List<CategoryEntity>> = repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val overrides: StateFlow<List<OccurrenceOverride>> = repo.overrides.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val outcomes: StateFlow<List<WeeklyOutcome>> = repo.outcomes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val changeLog: StateFlow<List<ChangeLogEntry>> = repo.changeLog.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val catalog: StateFlow<Catalog> = combine(categories, goals) { c, g -> Catalog(c, g) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Catalog())

    val hiddenCategories: StateFlow<Set<String>> = combine(categories, _today) { c, _ -> Repository.hiddenCategories(c) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val todayFilter = MutableStateFlow(TodayFilter.TODAY)

    private data class Inputs(val tasks: List<Task>, val completions: List<Completion>, val settings: AppSettings, val hidden: Set<String>)

    private val inputs = combine(tasks, completions, settings, hiddenCategories) { t, c, s, h ->
        if (t == null || s == null) null else Inputs(t, c, s, h)
    }

    val day: StateFlow<DayState?> = combine(_today, inputs, goals, milestones, overrides) { date, i, g, ms, ov ->
        if (i == null) return@combine null
        val s = i.settings
        val occ = ScheduleEngine.occurrencesFor(date, i.tasks, i.completions, i.hidden)
        val overdue = ScheduleEngine.overdue(date, i.tasks, i.completions, i.hidden)
        val preferMin = ov.filter { it.date == date && it.preferMinimum }.mapTo(HashSet()) { it.taskId }
        val lowEnergy = s.isLowEnergy(date)
        fun load(d: LocalDate) = ScheduleEngine.occurrencesFor(d, i.tasks, i.completions, i.hidden).filter { !it.isSkipped }.sumOf { it.task.estimatedIdealMinutes }
        DayState(
            date = date,
            occurrences = occ,
            overdue = overdue,
            progress = DailyProgress.of(occ),
            lowEnergy = lowEnergy,
            xp = Xp.total(i.completions),
            daysShowedUp = Xp.daysShowedUp(i.completions),
            focus = DailyPlanner.plan(
                date, LocalTime.now(), occ, i.tasks, i.completions, g, ms, s.capacity.forDate(date), lowEnergy, preferMin,
                minimumByDefault = s.defaultMode == DefaultMode.MINIMUM,
            ),
            missed = MissedTaskTriage.suggest(date, overdue, i.tasks, g, ms, s.capacity::forDate, ::load),
            preferMinimum = preferMin,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { Dates.todayFlow().collect { _today.value = it } }
    }

    /** Called on resume so a phone left overnight shows the new day immediately. */
    fun refreshDate() { _today.value = Dates.today() }

    fun complete(task: Task, date: LocalDate, type: CompletionType, reason: SkipReason? = null, notes: String = "") =
        viewModelScope.launch { repo.complete(task, date, type, reason, notes) }

    fun undo(taskId: String, date: LocalDate) = viewModelScope.launch { repo.undo(taskId, date) }

    fun reschedule(task: Task, from: LocalDate, to: LocalDate, onDone: (Result<String>) -> Unit = {}) =
        viewModelScope.launch { onDone(runCatching { repo.reschedule(task, from, to) }) }

    fun setPreferMinimum(taskId: String, date: LocalDate, on: Boolean) = viewModelScope.launch { repo.setPreferMinimum(taskId, date, on) }

    fun setLowEnergy(on: Boolean) = viewModelScope.launch { repo.setLowEnergy(_today.value, on) }

    fun saveTask(task: Task) = viewModelScope.launch { repo.saveTask(task) }
    fun deleteTask(id: String) = viewModelScope.launch { repo.deleteTask(id) }

    fun updateSettings(kind: Repository.ChangeKind = Repository.ChangeKind.SETTINGS, f: (AppSettings) -> AppSettings) =
        viewModelScope.launch { repo.updateSettings(kind, f) }

    fun addProgress(goalId: String, metric: String, value: Double, notes: String) =
        viewModelScope.launch { repo.addProgress(goalId, metric, value, _today.value, notes) }

    fun deleteProgress(e: ProgressEntry) = viewModelScope.launch { repo.deleteProgress(e) }
    fun updateGoal(g: Goal) = viewModelScope.launch { repo.updateGoal(g) }
    fun setMilestoneDone(m: Milestone, done: Boolean) = viewModelScope.launch { repo.setMilestoneDone(m, done) }
    fun setOutcomeDone(o: WeeklyOutcome, done: Boolean) = viewModelScope.launch { repo.setOutcomeDone(o, done) }
    fun saveReview(r: WeeklyReview) = viewModelScope.launch { repo.saveReview(r) }
    fun resetPlan(clearHistory: Boolean) = viewModelScope.launch { repo.resetPlan(clearHistory) }
    fun loadExamplePlan() = viewModelScope.launch { repo.loadExamplePlan() }

    fun saveCategory(cat: CategoryEntity) = viewModelScope.launch { repo.saveCategory(cat) }
    fun mergeCategory(from: String, to: String) = viewModelScope.launch { repo.mergeCategory(from, to) }
    fun createCategory(name: String, icon: String) = viewModelScope.launch { repo.createCategory(name, icon) }

    /** Deletes everything on this phone (and this device's record on the AI server). */
    fun eraseAll(alsoForgetKey: Boolean, onDone: () -> Unit = {}) = viewModelScope.launch {
        runCatching { ai.forgetDeviceOnServer() }
        if (alsoForgetKey) ai.setApiKey(null)
        c.db.aiUsage().deleteAll()
        repo.eraseAll()
        onDone()
    }
}
