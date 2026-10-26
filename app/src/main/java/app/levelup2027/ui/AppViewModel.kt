package app.levelup2027.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.levelup2027.AppContainer
import app.levelup2027.data.AppSettings
import app.levelup2027.data.Completion
import app.levelup2027.data.CompletionType
import app.levelup2027.data.Goal
import app.levelup2027.data.Milestone
import app.levelup2027.data.ProgressEntry
import app.levelup2027.data.Repository
import app.levelup2027.data.SkipReason
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import app.levelup2027.data.WeeklyReview
import app.levelup2027.domain.DailyProgress
import app.levelup2027.domain.Dates
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

enum class TodayFilter(val label: String, val slot: TimeSlot?) {
    TODAY("Today", null),
    MORNING("Morning", TimeSlot.MORNING),
    WORK("During Work", TimeSlot.WORK),
    EVENING("Evening", TimeSlot.EVENING),
    FLEXIBLE("Flexible", TimeSlot.FLEXIBLE),
    OVERDUE("Overdue", null),
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
)

class AppViewModel(private val c: AppContainer) : ViewModel() {
    val repo: Repository = c.repository
    val backup = c.backup

    private val _today = MutableStateFlow(Dates.today())
    val today: StateFlow<LocalDate> = _today

    val tasks: StateFlow<List<Task>?> = repo.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val completions: StateFlow<List<Completion>> = repo.completions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<AppSettings?> = repo.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val goals: StateFlow<List<Goal>> = repo.goals.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val milestones: StateFlow<List<Milestone>> = repo.milestones.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val progress: StateFlow<List<ProgressEntry>> = repo.progress.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reviews: StateFlow<List<WeeklyReview>> = repo.reviews.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val todayFilter = MutableStateFlow(TodayFilter.TODAY)

    val day: StateFlow<DayState?> = combine(_today, tasks, completions, settings) { date, t, c, s ->
        if (t == null || s == null) return@combine null
        DayState(
            date = date,
            occurrences = ScheduleEngine.occurrencesFor(date, t, c, s.enabledCategories),
            overdue = ScheduleEngine.overdue(date, t, c, s.enabledCategories),
            progress = DailyProgress.of(ScheduleEngine.occurrencesFor(date, t, c, s.enabledCategories)),
            lowEnergy = s.isLowEnergy(date),
            xp = Xp.total(c),
            daysShowedUp = Xp.daysShowedUp(c),
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
    fun saveReview(r: WeeklyReview) = viewModelScope.launch { repo.saveReview(r) }
    fun resetPlan(clearHistory: Boolean) = viewModelScope.launch { repo.resetPlan(clearHistory) }
}
