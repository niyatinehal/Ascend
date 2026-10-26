package app.levelup2027.domain

import app.levelup2027.data.Campaign
import app.levelup2027.data.Category
import app.levelup2027.data.Completion
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Recurrence rules are stored as plain strings so they survive export/import unchanged:
 *  - "DAILY"
 *  - "WEEKLY:MON,TUE,THU"
 */
data class Recurrence(val days: Set<DayOfWeek>) {
    fun matches(date: LocalDate) = date.dayOfWeek in days

    fun encode(): String =
        if (days.size == 7) "DAILY"
        else "WEEKLY:" + DayOfWeek.entries.filter { it in days }.joinToString(",") { it.name.take(3) }

    fun describe(): String = when {
        days.size == 7 -> "Every day"
        days == WEEKDAYS -> "Weekdays"
        days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Weekends"
        else -> DayOfWeek.entries.filter { it in days }.joinToString(", ") { shortDay(it) }
    }

    companion object {
        val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        fun parse(rule: String?): Recurrence? {
            if (rule.isNullOrBlank()) return null
            val r = rule.trim().uppercase()
            if (r == "DAILY") return Recurrence(DayOfWeek.entries.toSet())
            if (!r.startsWith("WEEKLY:")) return null
            val days = r.removePrefix("WEEKLY:").split(",").mapNotNull { token ->
                DayOfWeek.entries.firstOrNull { it.name.startsWith(token.trim()) && token.trim().length >= 2 }
            }.toSet()
            return if (days.isEmpty()) null else Recurrence(days)
        }

        fun weekly(vararg days: DayOfWeek) = Recurrence(days.toSet()).encode()
    }
}

fun shortDay(d: DayOfWeek) = d.name.take(1) + d.name.substring(1, 3).lowercase()

/** A task resolved onto a concrete local calendar date, with its completion record if any. */
data class Occurrence(val task: Task, val date: LocalDate, val completion: Completion?) {
    val isDone get() = completion?.isDone == true
    val isSkipped get() = completion?.completionType == app.levelup2027.data.CompletionType.SKIPPED
    val isOpen get() = completion == null
}

object ScheduleEngine {

    /** True if [task] (ignoring superseding) is scheduled on [date]. */
    fun isScheduledOn(task: Task, date: LocalDate): Boolean {
        if (!task.enabled) return false
        task.scheduledDate?.let { return it == date }
        val rec = Recurrence.parse(task.recurrenceRule) ?: return false
        val from = task.activeFrom ?: Campaign.START
        val until = task.activeUntil ?: Campaign.END
        if (date.isBefore(from) || date.isAfter(until)) return false
        return rec.matches(date)
    }

    /**
     * All tasks due on [date]: recurring + one-time, minus recurring tasks superseded by a one-time
     * task on the same date. Each task appears at most once. Sorted by slot, then sortOrder.
     */
    fun tasksFor(date: LocalDate, tasks: List<Task>, enabledCategories: Set<Category>? = null): List<Task> {
        val due = tasks.filter { isScheduledOn(it, date) && (enabledCategories == null || it.category in enabledCategories) }
        val superseded = due.mapNotNullTo(HashSet()) { it.supersedesTaskId }
        return due.filter { it.id !in superseded }
            .distinctBy { it.id }
            .sortedWith(compareBy<Task>({ it.timeSlot.ordinal }, { it.sortOrder }, { it.title }))
    }

    fun occurrencesFor(
        date: LocalDate,
        tasks: List<Task>,
        completions: List<Completion>,
        enabledCategories: Set<Category>? = null,
    ): List<Occurrence> {
        val byTask = completions.filter { it.scheduledDate == date }.associateBy { it.taskId }
        return tasksFor(date, tasks, enabledCategories).map { Occurrence(it, date, byTask[it.id]) }
    }

    /**
     * Overdue = one-time tasks from a past date that were neither completed nor skipped.
     * Recurring tasks are never overdue: they simply return on their next scheduled date,
     * so missed days never build into a backlog.
     */
    fun overdue(
        today: LocalDate,
        tasks: List<Task>,
        completions: List<Completion>,
        enabledCategories: Set<Category>? = null,
    ): List<Occurrence> {
        val recorded = completions.mapTo(HashSet()) { it.taskId to it.scheduledDate }
        return tasks.filter { t ->
            val d = t.scheduledDate
            t.enabled && d != null && d.isBefore(today) &&
                (enabledCategories == null || t.category in enabledCategories) &&
                (t.id to d) !in recorded
        }.sortedWith(compareBy({ it.scheduledDate }, { it.timeSlot.ordinal }, { it.sortOrder }))
            .map { Occurrence(it, it.scheduledDate!!, null) }
    }

    /** Next date on/after [from] when the task is scheduled (looks ahead up to a year). */
    fun nextOccurrence(task: Task, from: LocalDate): LocalDate? {
        task.scheduledDate?.let { return if (!it.isBefore(from)) it else null }
        var d = from
        repeat(370) {
            if (isScheduledOn(task, d)) return d
            d = d.plusDays(1)
        }
        return null
    }

    fun describeSchedule(task: Task): String {
        task.scheduledDate?.let { return DateFmt.medium(it) }
        val rec = Recurrence.parse(task.recurrenceRule) ?: return "Not scheduled"
        return rec.describe()
    }

    fun slotGroups(occ: List<Occurrence>): Map<TimeSlot, List<Occurrence>> =
        TimeSlot.entries.associateWith { s -> occ.filter { it.task.timeSlot == s } }.filterValues { it.isNotEmpty() }
}

object Dates {
    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zone)

    /** Week starts on Monday (the campaign starts on a Monday). */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun millisUntilNextMidnight(now: ZonedDateTime): Long {
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return Duration.between(now, next).toMillis().coerceAtLeast(1_000)
    }

    /**
     * Emits the local date now and again just after each local midnight. Uses the zone at each
     * emission so time-zone changes are picked up on the next tick.
     */
    fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            emit(now.toLocalDate())
            // Re-check at least every 15 minutes in case the clock or zone changed.
            delay(minOf(millisUntilNextMidnight(now) + 500, 15 * 60_000L))
        }
    }.distinctUntilChanged()

    fun campaignDay(date: LocalDate): Long = java.time.temporal.ChronoUnit.DAYS.between(Campaign.START, date) + 1
    fun campaignLength(): Long = java.time.temporal.ChronoUnit.DAYS.between(Campaign.START, Campaign.END) + 1
    fun daysUntil(from: LocalDate, to: LocalDate): Long = java.time.temporal.ChronoUnit.DAYS.between(from, to)
}

object DateFmt {
    private val med = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy")
    private val long = java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")
    private val short = java.time.format.DateTimeFormatter.ofPattern("d MMM")
    private val monthYear = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy")
    private val widget = java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")
    fun medium(d: LocalDate): String = med.format(d)
    fun long(d: LocalDate): String = long.format(d)
    fun short(d: LocalDate): String = short.format(d)
    fun monthYear(d: LocalDate): String = monthYear.format(d)
    fun widget(d: LocalDate): String = widget.format(d)
}
