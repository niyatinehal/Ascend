package app.levelup2027.domain

import app.levelup2027.data.Completion
import app.levelup2027.data.Goal
import app.levelup2027.data.Milestone
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** One open occurrence with the version the planner recommends today. */
data class FocusItem(
    val occ: Occurrence,
    val useMinimum: Boolean,
    val minutes: Int,
    val score: Int,
    val blockedBy: List<String>,
    val reason: String,
) {
    val taskId: String get() = occ.task.id
}

/**
 * The answer to "what should I do today?". Built locally from the accepted plan; never calls the AI.
 *
 * [ordered] is every open occurrence, most useful first. [ifTime] holds the ones that don't fit the
 * user's daily time budget: they stay visible, they're just not presented as expected.
 */
data class DailyFocus(
    val ordered: List<FocusItem>,
    val mostImportant: FocusItem?,
    val next: FocusItem?,
    val ifTime: Set<String>,
    val plannedMinutes: Int,
    val doneMinutes: Int,
    val capacity: Int?,
    val tradeOff: String?,
) {
    val overCapacity: Boolean get() = ifTime.isNotEmpty()

    companion object {
        val EMPTY = DailyFocus(emptyList(), null, null, emptySet(), 0, 0, null, null)
    }
}

object DailyPlanner {

    /** The slot that fits the current time of day. Work hours only count on weekdays. */
    fun currentSlot(date: LocalDate, now: LocalTime): TimeSlot {
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        return when {
            now.hour < 11 -> TimeSlot.MORNING
            now.hour < 17 && !weekend -> TimeSlot.WORK
            now.hour < 17 -> TimeSlot.FLEXIBLE
            else -> TimeSlot.EVENING
        }
    }

    fun plan(
        date: LocalDate,
        now: LocalTime,
        occurrences: List<Occurrence>,
        allTasks: List<Task>,
        completions: List<Completion>,
        goals: List<Goal>,
        milestones: List<Milestone>,
        capacity: Int?,
        lowEnergy: Boolean,
        preferMinimum: Set<String>,
        minimumByDefault: Boolean = false,
    ): DailyFocus {
        if (occurrences.isEmpty()) return DailyFocus.EMPTY.copy(capacity = capacity)
        val goalById = goals.associateBy { it.id }
        val msById = milestones.associateBy { it.id }
        val taskById = allTasks.associateBy { it.id }
        val doneIds = completions.filter { it.isDone && !it.scheduledDate.isAfter(date) }.mapTo(HashSet()) { it.taskId }
        val openToday = occurrences.filter { it.isOpen }
        val dependedOn = openToday.flatMapTo(HashSet()) { it.task.dependencyIds }

        fun useMin(o: Occurrence) = lowEnergy || minimumByDefault || o.task.id in preferMinimum
        fun minutesOf(o: Occurrence, min: Boolean) = if (min) o.task.estimatedMinimumMinutes else o.task.estimatedIdealMinutes

        val items = openToday.map { o ->
            val t = o.task
            val goal = t.goalId?.let(goalById::get)
            // A dependency only blocks when it's a dated task that hasn't been done yet.
            val blockers = t.dependencyIds.mapNotNull(taskById::get)
                .filter { it.isOneTime && it.id !in doneIds }
                .map { it.title }
            // Deadlines only add urgency for goals that are being pursued.
            val deadline = if (goal != null && !goal.isActive) null
            else listOfNotNull(t.milestoneId?.let(msById::get)?.takeIf { !it.done }?.dueDate, goal?.targetDate)
                .filter { !it.isBefore(date) }
                .minOrNull()
            val daysLeft = deadline?.let { ChronoUnit.DAYS.between(date, it) }
            var score = (4 - t.priority.coerceIn(1, 3)) * 10
            val reasons = mutableListOf<String>()
            if (t.priority == 1) reasons += "high priority"
            if (goal != null) {
                score += if (goal.isActive) (4 - goal.priority.coerceIn(1, 3)) * 5 else -15
            }
            if (daysLeft != null && daysLeft <= 7) { score += 12; reasons += "due within a week" }
            else if (daysLeft != null && daysLeft <= 30) score += 6
            if (t.isOneTime) score += 4
            if (t.id in dependedOn) { score += 5; reasons += "other tasks depend on it" }
            if (blockers.isNotEmpty()) score -= 25
            val min = useMin(o)
            FocusItem(o, min, minutesOf(o, min), score, blockers, reasons.joinToString(", "))
        }.sortedWith(compareByDescending<FocusItem> { it.score }.thenBy { it.occ.task.timeSlot.ordinal }.thenBy { it.occ.task.sortOrder })

        val ready = items.filter { it.blockedBy.isEmpty() }
        val mostImportant = ready.firstOrNull() ?: items.firstOrNull()

        val slot = currentSlot(date, now)
        fun slotDistance(s: TimeSlot) = when {
            s == slot || s == TimeSlot.FLEXIBLE -> 0
            s.ordinal < slot.ordinal -> 1 // earlier slot, still doable
            else -> 2
        }
        val next = ready.minWithOrNull(compareBy<FocusItem> { slotDistance(it.occ.task.timeSlot) }.thenByDescending { it.score })

        val doneMinutes = occurrences.filter { it.isDone }.sumOf {
            minutesOf(it, it.completion?.completionType == app.levelup2027.data.CompletionType.MINIMUM)
        }
        val ifTime = LinkedHashSet<String>()
        if (capacity != null) {
            var used = doneMinutes
            for (item in items) {
                if (used + item.minutes <= capacity || item == mostImportant) used += item.minutes
                else ifTime += item.taskId
            }
        }
        val planned = doneMinutes + items.sumOf { it.minutes }
        val tradeOff = if (capacity != null && ifTime.isNotEmpty()) {
            val minimumTotal = doneMinutes + items.sumOf { it.occ.task.estimatedMinimumMinutes }
            val goalsAffected = items.filter { it.taskId in ifTime }.mapNotNull { it.occ.task.goalId?.let(goalById::get)?.displayName }.distinct()
            buildString {
                append("Today's tasks need about $planned min and your daily budget is $capacity min. ")
                append("The most useful ${items.size - ifTime.size} come first; ${ifTime.size} more are under \"If you have time\".")
                if (minimumTotal <= capacity) append(" Doing minimum versions would fit (about $minimumTotal min).")
                if (goalsAffected.isNotEmpty()) append(" Today favours other goals over ${goalsAffected.joinToString(", ")}.")
            }
        } else null
        return DailyFocus(items, mostImportant, next, ifTime, planned, doneMinutes, capacity, tradeOff)
    }
}

enum class MissedAction { RESCHEDULE, SKIP }

data class MissedSuggestion(val occ: Occurrence, val action: MissedAction, val toDate: LocalDate?, val useMinimum: Boolean, val reason: String)

/**
 * The missed-task policy: one-time tasks from earlier days are not rolled forward automatically.
 * Each gets a suggestion the user confirms: reschedule (into a day with room, at most one moved
 * task per day) or skip. History is always kept, and suggestions never add more than the budget.
 */
object MissedTaskTriage {
    private const val STALE_DAYS = 14

    fun suggest(
        today: LocalDate,
        overdue: List<Occurrence>,
        allTasks: List<Task>,
        goals: List<Goal>,
        milestones: List<Milestone>,
        capacityFor: (LocalDate) -> Int?,
        loadFor: (LocalDate) -> Int,
    ): List<MissedSuggestion> {
        val goalById = goals.associateBy { it.id }
        val msById = milestones.associateBy { it.id }
        val dependedOn = allTasks.filter { it.enabled }.flatMapTo(HashSet()) { it.dependencyIds }
        val usedDays = HashSet<LocalDate>()
        val extraLoad = HashMap<LocalDate, Int>()

        fun importance(o: Occurrence): Int {
            val t = o.task
            val deadline = listOfNotNull(t.milestoneId?.let(msById::get)?.dueDate, t.goalId?.let(goalById::get)?.targetDate)
                .filter { !it.isBefore(today) }.minOrNull()
            var s = 4 - t.priority.coerceIn(1, 3)
            if (t.id in dependedOn) s += 2
            if (deadline != null && ChronoUnit.DAYS.between(today, deadline) <= 21) s += 2
            return s
        }

        return overdue.sortedByDescending(::importance).map { o ->
            val t = o.task
            val age = ChronoUnit.DAYS.between(o.date, today)
            val goal = t.goalId?.let(goalById::get)
            when {
                goal != null && !goal.isActive ->
                    MissedSuggestion(o, MissedAction.SKIP, null, false, "Its goal (${goal.displayName}) isn't active right now.")
                t.isRest -> MissedSuggestion(o, MissedAction.SKIP, null, false, "A missed rest day doesn't need making up.")
                age > STALE_DAYS -> MissedSuggestion(o, MissedAction.SKIP, null, false, "It's from over two weeks ago, so it's probably no longer relevant.")
                importance(o) < 2 -> MissedSuggestion(o, MissedAction.SKIP, null, false, "Low priority, so skipping keeps the week realistic.")
                else -> {
                    val fit = (0L..6L).map { today.plusDays(it) }.firstOrNull { d ->
                        d !in usedDays && run {
                            val cap = capacityFor(d) ?: return@run true
                            loadFor(d) + (extraLoad[d] ?: 0) + t.estimatedIdealMinutes <= cap
                        }
                    }
                    val minimumFit = if (fit == null) (0L..6L).map { today.plusDays(it) }.firstOrNull { d ->
                        d !in usedDays && run {
                            val cap = capacityFor(d) ?: return@run true
                            loadFor(d) + (extraLoad[d] ?: 0) + t.estimatedMinimumMinutes <= cap
                        }
                    } else null
                    val day = fit ?: minimumFit
                    if (day == null) {
                        MissedSuggestion(o, MissedAction.SKIP, null, false, "There's no room this week without overloading a day.")
                    } else {
                        usedDays += day
                        extraLoad[day] = (extraLoad[day] ?: 0) + if (fit != null) t.estimatedIdealMinutes else t.estimatedMinimumMinutes
                        MissedSuggestion(
                            o, MissedAction.RESCHEDULE, day, useMinimum = fit == null,
                            reason = if (fit != null) "Still important, and ${DateFmt.short(day)} has room."
                            else "Still important; the minimum version fits on ${DateFmt.short(day)}.",
                        )
                    }
                }
            }
        }
    }
}
