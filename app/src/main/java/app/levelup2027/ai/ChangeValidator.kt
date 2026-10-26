package app.levelup2027.ai

import app.levelup2027.data.Capacity
import app.levelup2027.data.Completion
import app.levelup2027.data.EnergyLevel
import app.levelup2027.data.Goal
import app.levelup2027.data.GoalStatus
import app.levelup2027.data.Source
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.DateFmt
import app.levelup2027.domain.Recurrence
import app.levelup2027.domain.ScheduleEngine
import java.time.LocalDate

/**
 * A checked change to the plan. [major] changes (goals, deadlines, removing a habit, changing when a
 * habit happens) always need the user's explicit approval; simple ones may be auto-applied when the
 * user turned that on.
 */
sealed class PlanChange {
    abstract val reason: String
    abstract val major: Boolean
    abstract val summary: String

    data class Reschedule(val task: Task, val from: LocalDate, val to: LocalDate, override val reason: String) : PlanChange() {
        override val major get() = false
        override val summary get() = "Move \"${task.title}\" from ${DateFmt.short(from)} to ${DateFmt.short(to)}"
    }

    data class UseMinimum(val task: Task, val date: LocalDate, override val reason: String) : PlanChange() {
        override val major get() = false
        override val summary get() = "Do the minimum version of \"${task.title}\" on ${DateFmt.short(date)}"
    }

    data class SkipOccurrence(val task: Task, val date: LocalDate, override val reason: String) : PlanChange() {
        override val major get() = false
        override val summary get() = "Skip \"${task.title}\" on ${DateFmt.short(date)}"
    }

    data class UpdateTask(val before: Task, val after: Task, override val reason: String) : PlanChange() {
        override val major get() = before.recurrenceRule != after.recurrenceRule
        override val summary: String
            get() {
                val parts = mutableListOf<String>()
                if (before.title != after.title) parts += "rename to \"${after.title}\""
                if (before.recurrenceRule != after.recurrenceRule) parts += "move to ${ScheduleEngine.describeSchedule(after)}"
                if (before.timeSlot != after.timeSlot) parts += "time slot ${after.timeSlot.label}"
                if (before.estimatedIdealMinutes != after.estimatedIdealMinutes || before.estimatedMinimumMinutes != after.estimatedMinimumMinutes)
                    parts += "${after.estimatedIdealMinutes} min ideal / ${after.estimatedMinimumMinutes} min minimum"
                if (before.idealDescription != after.idealDescription || before.minimumDescription != after.minimumDescription) parts += "new ideal/minimum wording"
                if (before.priority != after.priority) parts += "priority ${after.priority}"
                return "Change \"${before.title}\": " + parts.ifEmpty { listOf("small edits") }.joinToString(", ")
            }
    }

    data class AddTask(val task: Task, override val reason: String) : PlanChange() {
        override val major get() = task.recurrenceRule != null
        override val summary get() = "Add \"${task.title}\" (${ScheduleEngine.describeSchedule(task)})"
    }

    data class DisableTask(val task: Task, override val reason: String) : PlanChange() {
        override val major get() = true
        override val summary get() = "Pause \"${task.title}\" (its history is kept)"
    }

    data class UpdateGoal(val before: Goal, val after: Goal, override val reason: String) : PlanChange() {
        override val major get() = true
        override val summary: String
            get() {
                val parts = mutableListOf<String>()
                if (before.status != after.status) parts += "mark as ${after.status}"
                if (before.targetDate != after.targetDate) parts += "deadline ${DateFmt.medium(after.targetDate)}"
                if (before.priority != after.priority) parts += "priority ${after.priority}"
                return "Goal \"${before.displayName}\": " + parts.joinToString(", ")
            }
    }

    data class SetCapacity(val before: Capacity, val after: Capacity, override val reason: String) : PlanChange() {
        override val major get() = false
        override val summary get() = "Daily time budget: ${after.weekdayMinutes ?: "–"} min on weekdays, ${after.weekendMinutes ?: "–"} min at weekends"
    }

    data class SetLowEnergy(val date: LocalDate, override val reason: String) : PlanChange() {
        override val major get() = false
        override val summary get() = "Make ${DateFmt.short(date)} a low-energy day (minimum versions)"
    }
}

/** The current plan, as far as validating changes needs it. */
data class PlanState(
    val today: LocalDate,
    val tasks: List<Task>,
    val completions: List<Completion>,
    val goals: List<Goal>,
    val capacity: Capacity,
)

data class ValidatedChanges(val changes: List<PlanChange>, val rejected: List<String>) {
    val allSimple: Boolean get() = changes.isNotEmpty() && changes.none { it.major }
}

object ChangeValidator {
    /** Changes may look at most this far ahead; beyond that the plan is generated progressively. */
    const val HORIZON_DAYS = 60L

    fun validate(ops: List<ChangeOp>, state: PlanState, newTaskId: (String) -> String): ValidatedChanges {
        val out = mutableListOf<PlanChange>()
        val rejected = mutableListOf<String>()
        val taskById = state.tasks.associateBy { it.id }
        val goalById = state.goals.associateBy { it.id }
        val recorded = state.completions.associateBy { it.taskId to it.scheduledDate }
        val horizon = state.today.plusDays(HORIZON_DAYS)
        fun inRange(d: LocalDate) = !d.isBefore(state.today) && !d.isAfter(horizon)

        for (op in ops.take(20)) {
            val task = taskById[op.taskId]
            val date = PlanValidator.parseDate(op.date)
            val newDate = PlanValidator.parseDate(op.newDate)
            fun reject(why: String) { rejected += "${op.type}: $why" }
            when (op.type) {
                ChangeTypes.RESCHEDULE -> when {
                    task == null -> reject("unknown task")
                    date == null || newDate == null -> reject("missing dates for \"${task.title}\"")
                    date == newDate -> reject("same date")
                    !ScheduleEngine.isScheduledOn(task, date) -> reject("\"${task.title}\" isn't scheduled on $date")
                    recorded[task.id to date] != null -> reject("\"${task.title}\" on $date is already completed or recorded; history isn't changed")
                    date.isBefore(state.today.minusDays(14)) -> reject("too far in the past")
                    !inRange(newDate) -> reject("new date $newDate is outside today..+$HORIZON_DAYS days")
                    else -> out += PlanChange.Reschedule(task, date, newDate, op.reason)
                }
                ChangeTypes.USE_MINIMUM, ChangeTypes.SKIP_OCCURRENCE -> when {
                    task == null -> reject("unknown task")
                    date == null || !inRange(date) -> reject("date must be today or later")
                    !ScheduleEngine.isScheduledOn(task, date) -> reject("\"${task.title}\" isn't scheduled on $date")
                    recorded[task.id to date] != null -> reject("\"${task.title}\" on $date is already recorded")
                    op.type == ChangeTypes.SKIP_OCCURRENCE && date != state.today -> reject("only today's tasks can be skipped in advance; move it instead")
                    op.type == ChangeTypes.USE_MINIMUM -> out += PlanChange.UseMinimum(task, date, op.reason)
                    else -> out += PlanChange.SkipOccurrence(task, date, op.reason)
                }
                ChangeTypes.UPDATE_TASK -> {
                    if (task == null) { reject("unknown task"); continue }
                    val days = PlanValidator.parseDays(op.days)
                    val ideal = op.idealMinutes?.coerceIn(0, PlanValidator.MAX_MINUTES) ?: task.estimatedIdealMinutes
                    val min = (op.minimumMinutes?.coerceIn(0, PlanValidator.MAX_MINUTES) ?: task.estimatedMinimumMinutes).coerceAtMost(ideal)
                    val after = task.copy(
                        title = op.title.trim().ifBlank { task.title }.take(80),
                        idealDescription = op.ideal.trim().ifBlank { task.idealDescription },
                        minimumDescription = op.minimum.trim().ifBlank { task.minimumDescription },
                        estimatedIdealMinutes = ideal,
                        estimatedMinimumMinutes = min,
                        timeSlot = TimeSlot.entries.firstOrNull { it.name == op.timeSlot.uppercase() } ?: task.timeSlot,
                        recurrenceRule = if (task.recurrenceRule != null && days.isNotEmpty()) Recurrence(days).encode() else task.recurrenceRule,
                        priority = op.priority?.coerceIn(1, 3) ?: task.priority,
                    )
                    if (after == task) reject("no actual change to \"${task.title}\"") else out += PlanChange.UpdateTask(task, after, op.reason)
                }
                ChangeTypes.ADD_TASK -> {
                    val n = op.newTask
                    if (n == null || n.title.isBlank()) { reject("new task missing"); continue }
                    val days = PlanValidator.parseDays(n.days)
                    val d = PlanValidator.parseDate(n.date)
                    if (days.isEmpty() && (d == null || !inRange(d))) { reject("\"${n.title}\" needs a date in range or weekdays"); continue }
                    val dup = state.tasks.any { it.enabled && PlanValidator.normalizeTitle(it.title) == PlanValidator.normalizeTitle(n.title) &&
                        (if (days.isNotEmpty()) it.recurrenceRule == Recurrence(days).encode() else it.scheduledDate == d) }
                    if (dup) { reject("\"${n.title}\" already exists"); continue }
                    val ideal = n.idealMinutes.coerceIn(0, PlanValidator.MAX_MINUTES)
                    val goal = goalById[n.goalKey]
                    out += PlanChange.AddTask(
                        Task(
                            id = newTaskId(n.title),
                            title = n.title.trim().take(80),
                            description = n.description.take(400),
                            category = goal?.categoryId ?: "growth",
                            timeSlot = TimeSlot.entries.firstOrNull { it.name == n.timeSlot.uppercase() } ?: TimeSlot.FLEXIBLE,
                            idealDescription = n.ideal.ifBlank { n.title },
                            minimumDescription = n.minimum.ifBlank { "Do the smallest useful part of this." },
                            estimatedIdealMinutes = ideal,
                            estimatedMinimumMinutes = n.minimumMinutes.coerceIn(0, ideal),
                            scheduledDate = if (days.isEmpty()) d else null,
                            recurrenceRule = if (days.isNotEmpty()) Recurrence(days).encode() else null,
                            activeFrom = if (days.isNotEmpty()) state.today else null,
                            goalId = goal?.id,
                            energyLevel = EnergyLevel.entries.firstOrNull { it.name == n.energy } ?: EnergyLevel.MEDIUM,
                            priority = n.priority.coerceIn(1, 3),
                            source = Source.AI,
                            isRest = n.isRest,
                            countsAsWorkout = n.isWorkout,
                        ),
                        op.reason,
                    )
                }
                ChangeTypes.DISABLE_TASK -> when {
                    task == null -> reject("unknown task")
                    !task.enabled -> reject("\"${task.title}\" is already paused")
                    else -> out += PlanChange.DisableTask(task, op.reason)
                }
                ChangeTypes.UPDATE_GOAL -> {
                    val goal = goalById[op.goalId]
                    if (goal == null) { reject("unknown goal"); continue }
                    val status = op.goalStatus.lowercase().takeIf { it in setOf(GoalStatus.ACTIVE, GoalStatus.PAUSED, GoalStatus.DROPPED, GoalStatus.DONE) } ?: goal.status
                    val deadline = PlanValidator.parseDate(op.goalDeadline)?.takeIf { !it.isBefore(state.today) } ?: goal.targetDate
                    val after = goal.copy(status = status, targetDate = deadline, deadlineAssumed = goal.deadlineAssumed && deadline == goal.targetDate,
                        priority = op.priority?.coerceIn(1, 3) ?: goal.priority)
                    if (after == goal) reject("no actual change to goal \"${goal.displayName}\"") else out += PlanChange.UpdateGoal(goal, after, op.reason)
                }
                ChangeTypes.SET_CAPACITY -> {
                    val after = Capacity(op.weekdayMinutes?.coerceIn(0, 16 * 60) ?: state.capacity.weekdayMinutes, op.weekendMinutes?.coerceIn(0, 16 * 60) ?: state.capacity.weekendMinutes)
                    if (after == state.capacity) reject("capacity unchanged") else out += PlanChange.SetCapacity(state.capacity, after, op.reason)
                }
                ChangeTypes.SET_LOW_ENERGY -> {
                    val d = date ?: state.today
                    if (d != state.today) reject("low-energy mode applies to today only") else out += PlanChange.SetLowEnergy(d, op.reason)
                }
                else -> reject("unknown change type")
            }
        }
        // One change per occurrence: keep the first.
        val seen = HashSet<Pair<String, LocalDate?>>()
        val deduped = out.filter { c ->
            val k = when (c) {
                is PlanChange.Reschedule -> c.task.id to c.from
                is PlanChange.UseMinimum -> c.task.id to c.date
                is PlanChange.SkipOccurrence -> c.task.id to c.date
                is PlanChange.UpdateTask -> c.before.id to null
                is PlanChange.DisableTask -> c.task.id to null
                is PlanChange.UpdateGoal -> c.before.id to null
                else -> return@filter true
            }
            seen.add(k)
        }
        return ValidatedChanges(deduped, rejected)
    }
}
