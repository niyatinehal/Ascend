package app.levelup2027.domain

import app.levelup2027.data.Category
import app.levelup2027.data.Completion
import app.levelup2027.data.CompletionType
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import java.time.LocalDate

enum class WidgetMode { IDEAL, MINIMUM }

data class WidgetConfig(
    val slot: TimeSlot? = null,
    val showCompleted: Boolean = false,
    val mode: WidgetMode = WidgetMode.IDEAL,
    val categories: Set<Category> = Category.entries.toSet(),
)

data class WidgetItem(
    val taskId: String,
    val date: LocalDate,
    val title: String,
    val line: String,
    val minutes: Int,
    val category: Category,
    val slot: TimeSlot,
    val completionType: CompletionType?,
) {
    val done get() = completionType == CompletionType.IDEAL || completionType == CompletionType.MINIMUM
}

data class WidgetState(
    val date: LocalDate,
    val done: Int,
    val scheduled: Int,
    val openCount: Int,
    val items: List<WidgetItem>,
    val config: WidgetConfig,
    val lowEnergy: Boolean,
)

object WidgetStateBuilder {
    fun build(
        date: LocalDate,
        tasks: List<Task>,
        completions: List<Completion>,
        appCategories: Set<Category>,
        config: WidgetConfig,
        lowEnergy: Boolean,
    ): WidgetState {
        val cats = appCategories intersect config.categories
        val occ = ScheduleEngine.occurrencesFor(date, tasks, completions, cats)
            .filter { config.slot == null || it.task.timeSlot == config.slot }
        val progress = DailyProgress.of(occ)
        val useMinimum = lowEnergy || config.mode == WidgetMode.MINIMUM
        fun item(o: Occurrence) = WidgetItem(
            taskId = o.task.id,
            date = o.date,
            title = o.task.title,
            line = firstLine(if (useMinimum) o.task.minimumDescription else o.task.idealDescription),
            minutes = if (useMinimum) o.task.estimatedMinimumMinutes else o.task.estimatedIdealMinutes,
            category = o.task.category,
            slot = o.task.timeSlot,
            completionType = o.completion?.completionType,
        )
        val open = occ.filter { it.isOpen }.map(::item)
        val closed = if (config.showCompleted) occ.filter { it.isDone }.map(::item) else emptyList()
        return WidgetState(date, progress.done, progress.scheduled, open.size, open + closed, config, lowEnergy)
    }

    private fun firstLine(s: String) = s.lineSequence().map { it.trim().removePrefix("•").removePrefix("-").trim() }
        .firstOrNull { it.isNotEmpty() } ?: ""
}
