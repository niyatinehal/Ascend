package app.levelup2027.ai

import app.levelup2027.data.Capacity
import app.levelup2027.data.CategoryEntity
import app.levelup2027.data.Completion
import app.levelup2027.data.CompletionType
import app.levelup2027.data.Goal
import app.levelup2027.data.Milestone
import app.levelup2027.data.Task
import app.levelup2027.domain.DailyPlanner
import app.levelup2027.domain.ScheduleEngine
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

/**
 * Builds the user-turn payloads. Each request carries only what that operation needs: a snapshot
 * of the next week and a summary of the last one, never the whole history, notes or progress log.
 */
object ContextBuilder {

    @Serializable data class IdName(val id: String, val name: String)

    @Serializable data class IntakeInput(val today: String, val userMessage: String)

    @Serializable
    data class PlanInput(
        val today: String,
        val planStart: String,
        val userMessage: String,
        val context: UserContext,
        val answers: List<QuestionAnswer>,
        val existingCategories: List<IdName>,
        val existingGoals: List<IdName>,
        val knownWeekdayMinutes: Int?,
        val knownWeekendMinutes: Int?,
        val previousSummary: String = "",
        val feedback: String = "",
    )

    @Serializable
    data class GoalSnap(val id: String, val title: String, val priority: Int, val deadline: String, val status: String, val nextMilestone: String = "")

    @Serializable
    data class HabitSnap(val taskId: String, val title: String, val days: String, val slot: String, val idealMinutes: Int, val minimumMinutes: Int, val priority: Int, val goalId: String)

    @Serializable
    data class OccSnap(val taskId: String, val title: String, val date: String, val slot: String, val idealMinutes: Int, val minimumMinutes: Int, val priority: Int, val goalId: String, val state: String)

    @Serializable
    data class Adherence(val days: Int, val scheduled: Int, val ideal: Int, val minimum: Int, val skipped: Int, val notMarked: Int)

    @Serializable
    data class AdjustInput(
        val today: String,
        val weekday: String,
        val currentSlot: String,
        val weekdayMinutes: Int?,
        val weekendMinutes: Int?,
        val lowEnergyToday: Boolean,
        val goals: List<GoalSnap>,
        val habits: List<HabitSnap>,
        val nextSevenDays: List<OccSnap>,
        val missedLastSevenDays: List<OccSnap>,
        val lastSevenDays: Adherence,
        val conversation: List<ChatTurn>,
    )

    @Serializable
    data class GoalWeek(val id: String, val title: String, val outcome: String, val deadline: String, val upcomingMilestones: List<String>, val lastWeek: Adherence)

    @Serializable
    data class WeekInput(
        val today: String,
        val weekStart: String,
        val weekdayMinutes: Int?,
        val weekendMinutes: Int?,
        val habitMinutesByDate: Map<String, Int>,
        val goals: List<GoalWeek>,
        val categories: List<IdName>,
        val alreadyScheduled: List<OccSnap>,
        val personContext: String,
    )

    fun intake(today: LocalDate, text: String) = AiJson.encodeToString(IntakeInput.serializer(), IntakeInput(today.toString(), text.trim()))

    fun plan(
        today: LocalDate, start: LocalDate, text: String, intake: IntakeResult?, answers: List<QuestionAnswer>,
        categories: List<CategoryEntity>, goals: List<Goal>, capacity: Capacity, previousSummary: String = "", feedback: String = "",
    ) = AiJson.encodeToString(
        PlanInput.serializer(),
        PlanInput(
            today.toString(), start.toString(), text.trim(), intake?.context ?: UserContext(),
            answers.filter { it.answer.isNotBlank() },
            categories.filter { it.enabled }.map { IdName(it.id, it.name) },
            goals.filter { it.isActive }.map { IdName(it.id, it.title) },
            intake?.context?.weekdayFreeMinutes ?: capacity.weekdayMinutes,
            intake?.context?.weekendFreeMinutes ?: capacity.weekendMinutes,
            previousSummary, feedback.trim(),
        ),
    )

    private fun adherence(from: LocalDate, to: LocalDate, tasks: List<Task>, completions: List<Completion>, filter: (Task) -> Boolean = { true }): Adherence {
        val occ = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .flatMap { d -> ScheduleEngine.occurrencesFor(d, tasks, completions).filter { filter(it.task) } }.toList()
        return Adherence(
            days = (to.toEpochDay() - from.toEpochDay() + 1).toInt(),
            scheduled = occ.size,
            ideal = occ.count { it.completion?.completionType == CompletionType.IDEAL },
            minimum = occ.count { it.completion?.completionType == CompletionType.MINIMUM },
            skipped = occ.count { it.isSkipped },
            notMarked = occ.count { it.isOpen },
        )
    }

    private fun snap(o: app.levelup2027.domain.Occurrence) = OccSnap(
        o.task.id, o.task.title, o.date.toString(), o.task.timeSlot.name, o.task.estimatedIdealMinutes, o.task.estimatedMinimumMinutes,
        o.task.priority, o.task.goalId ?: "",
        when {
            o.completion == null -> "open"
            o.isDone -> o.completion.completionType.name.lowercase()
            else -> "skipped"
        },
    )

    fun adjust(
        today: LocalDate, now: LocalTime, tasks: List<Task>, completions: List<Completion>, goals: List<Goal>, milestones: List<Milestone>,
        capacity: Capacity, lowEnergy: Boolean, hidden: Set<String>, conversation: List<ChatTurn>,
    ): String {
        val enabled = tasks.filter { it.enabled && it.category !in hidden }
        val upcoming = (0L..6L).flatMap { ScheduleEngine.occurrencesFor(today.plusDays(it), enabled, completions) }.map(::snap)
        val missed = (1L..7L).flatMap { ScheduleEngine.occurrencesFor(today.minusDays(it), enabled, completions) }
            .filter { it.isOpen || it.isSkipped }.map(::snap)
        val nextMs = milestones.filter { !it.done && !it.dueDate.isBefore(today) }.groupBy { it.goalId }.mapValues { (_, l) -> l.minBy { it.dueDate } }
        return AiJson.encodeToString(
            AdjustInput.serializer(),
            AdjustInput(
                today = today.toString(),
                weekday = today.dayOfWeek.name,
                currentSlot = DailyPlanner.currentSlot(today, now).name,
                weekdayMinutes = capacity.weekdayMinutes,
                weekendMinutes = capacity.weekendMinutes,
                lowEnergyToday = lowEnergy,
                goals = goals.map { GoalSnap(it.id, it.title, it.priority, it.targetDate.toString(), it.status, nextMs[it.id]?.let { m -> "${m.title} (${m.dueDate})" } ?: "") },
                habits = enabled.filter { it.recurrenceRule != null }.map {
                    HabitSnap(it.id, it.title, ScheduleEngine.describeSchedule(it), it.timeSlot.name, it.estimatedIdealMinutes, it.estimatedMinimumMinutes, it.priority, it.goalId ?: "")
                },
                nextSevenDays = upcoming,
                missedLastSevenDays = missed,
                lastSevenDays = adherence(today.minusDays(7), today.minusDays(1), enabled, completions),
                conversation = conversation.takeLast(6),
            ),
        )
    }

    fun week(
        today: LocalDate, weekStart: LocalDate, tasks: List<Task>, completions: List<Completion>, goals: List<Goal>,
        milestones: List<Milestone>, categories: List<CategoryEntity>, capacity: Capacity, personContext: String,
    ): String {
        val enabled = tasks.filter { it.enabled }
        val days = (0L..6L).map { weekStart.plusDays(it) }
        val habits = enabled.filter { it.recurrenceRule != null }
        val lastWeekStart = weekStart.minusDays(7)
        return AiJson.encodeToString(
            WeekInput.serializer(),
            WeekInput(
                today = today.toString(),
                weekStart = weekStart.toString(),
                weekdayMinutes = capacity.weekdayMinutes,
                weekendMinutes = capacity.weekendMinutes,
                habitMinutesByDate = days.associate { d -> d.toString() to ScheduleEngine.tasksFor(d, habits).sumOf { it.estimatedIdealMinutes } },
                goals = goals.filter { it.isActive }.map { g ->
                    GoalWeek(
                        g.id, g.title, g.description.take(300), g.targetDate.toString(),
                        milestones.filter { it.goalId == g.id && !it.done && !it.dueDate.isBefore(today) }.sortedBy { it.dueDate }.take(2).map { "${it.title} (${it.dueDate})" },
                        adherence(lastWeekStart, lastWeekStart.plusDays(6).coerceAtMost(today), enabled, completions) { it.goalId == g.id },
                    )
                },
                categories = categories.filter { it.enabled }.map { IdName(it.id, it.name) },
                alreadyScheduled = days.flatMap { d -> ScheduleEngine.occurrencesFor(d, enabled.filter { it.isOneTime }, completions) }.map(::snap),
                personContext = personContext.take(1500),
            ),
        )
    }

    private fun LocalDate.coerceAtMost(other: LocalDate) = if (isAfter(other)) other else this
}
