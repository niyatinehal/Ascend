package app.levelup2027.domain

import app.levelup2027.data.Completion
import app.levelup2027.data.CompletionType
import app.levelup2027.data.SkipReason
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import java.time.LocalDate

data class DailyProgress(
    val scheduled: Int,
    val done: Int,
    val ideal: Int,
    val minimum: Int,
    val skipped: Int,
) {
    val open: Int get() = scheduled - done - skipped
    val fraction: Float get() = if (scheduled == 0) 0f else done.toFloat() / scheduled

    companion object {
        /** Each occurrence counts once, whatever its completion history. */
        fun of(occurrences: List<Occurrence>): DailyProgress {
            val unique = occurrences.distinctBy { it.task.id to it.date }
            return DailyProgress(
                scheduled = unique.size,
                done = unique.count { it.isDone },
                ideal = unique.count { it.completion?.completionType == CompletionType.IDEAL },
                minimum = unique.count { it.completion?.completionType == CompletionType.MINIMUM },
                skipped = unique.count { it.isSkipped },
            )
        }
    }
}

/** Gentle RPG layer: every completion earns XP, minimum completions still earn most of it. */
object Xp {
    const val IDEAL = 10
    const val MINIMUM = 6

    fun total(completions: List<Completion>): Int = completions.sumOf {
        when (it.completionType) {
            CompletionType.IDEAL -> IDEAL
            CompletionType.MINIMUM -> MINIMUM
            CompletionType.SKIPPED, CompletionType.RESCHEDULED -> 0
        }
    }

    /** Level n needs 50 * (n-1)^2 XP: early levels come quickly, later ones slower. */
    fun level(xp: Int): Int = kotlin.math.floor(kotlin.math.sqrt(xp / 50.0)).toInt() + 1
    fun xpForLevel(level: Int): Int = 50 * (level - 1) * (level - 1)
    fun levelFraction(xp: Int): Float {
        val l = level(xp)
        val lo = xpForLevel(l)
        val hi = xpForLevel(l + 1)
        return (xp - lo).toFloat() / (hi - lo)
    }

    /** Days with at least one completion. Never resets, so a missed day costs nothing. */
    fun daysShowedUp(completions: List<Completion>): Int =
        completions.filter { it.isDone }.mapTo(HashSet()) { it.scheduledDate }.size
}

data class SkippedTask(val title: String, val count: Int, val topReason: SkipReason?)

/** Done vs. planned for one category or goal in a period. */
data class GroupCount(val id: String?, val done: Int, val planned: Int)

data class WeeklyStats(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val progress: DailyProgress,
    val workouts: Int,
    val workoutsPlanned: Int,
    val byCategory: List<GroupCount>,
    val byGoal: List<GroupCount>,
    val frequentlySkipped: List<SkippedTask>,
    val observations: List<String>,
)

object WeeklyStatsCalculator {

    fun compute(
        weekStart: LocalDate,
        today: LocalDate,
        tasks: List<Task>,
        completions: List<Completion>,
        hiddenCategories: Set<String> = emptySet(),
    ): WeeklyStats {
        val weekEnd = weekStart.plusDays(6)
        // Only count days that have happened (including today) so a half-finished week isn't judged as failed.
        val lastDay = if (today.isBefore(weekEnd)) today else weekEnd
        val occ = generateSequence(weekStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDay) }
            .flatMap { ScheduleEngine.occurrencesFor(it, tasks, completions, hiddenCategories) }
            .toList()
        val progress = DailyProgress.of(occ)

        fun count(pred: (Occurrence) -> Boolean) = occ.count { pred(it) && it.isDone } to occ.count(pred)
        val (workouts, workoutsPlanned) = count { it.task.countsAsWorkout }
        fun groups(key: (Occurrence) -> String?) = occ.groupBy(key)
            .map { (id, l) -> GroupCount(id, l.count { it.isDone }, l.size) }
            .sortedByDescending { it.planned }

        val skipped = occ.filter { it.isSkipped }
            .groupBy { it.task.id }
            .map { (_, list) ->
                SkippedTask(
                    title = list.first().task.title,
                    count = list.size,
                    topReason = list.mapNotNull { it.completion?.skipReason }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
                )
            }
            .sortedByDescending { it.count }

        return WeeklyStats(
            weekStart, weekEnd, progress, workouts, workoutsPlanned,
            groups { it.task.category }, groups { it.task.goalId },
            skipped, observations(occ),
        )
    }

    /** Pattern notes phrased as observations, never judgements. */
    fun observations(occ: List<Occurrence>): List<String> {
        val out = mutableListOf<String>()
        if (occ.isEmpty()) return listOf("No tasks were scheduled in this period yet.")
        val bySlot = occ.groupBy { it.task.timeSlot }
        val rates = bySlot.mapValues { (_, l) -> l.count { it.isDone }.toFloat() / l.size }
        val weakest = rates.filter { (bySlot[it.key]?.size ?: 0) >= 3 }.minByOrNull { it.value }
        val strongest = rates.filter { (bySlot[it.key]?.size ?: 0) >= 3 }.maxByOrNull { it.value }
        if (strongest != null && strongest.value >= 0.6f) {
            out += "${strongest.key.label} tasks went best (${(strongest.value * 100).toInt()}% done)."
        }
        if (weakest != null && weakest != strongest && weakest.value < 0.5f) {
            out += "${weakest.key.label} tasks were hardest to fit in. Shorter minimum versions or a different slot might help."
        }
        val min = occ.count { it.completion?.completionType == CompletionType.MINIMUM }
        val done = occ.count { it.isDone }
        if (done > 0 && min * 2 >= done) {
            out += "About half or more of completions were minimum versions. That kept the habit alive on low-energy days."
        }
        val eveningMin = occ.count { it.task.timeSlot == TimeSlot.EVENING && it.completion?.completionType == CompletionType.MINIMUM }
        if (eveningMin >= 3) out += "Evenings often called for the minimum version. Consider lighter evening plans after long commutes."
        val reasons = occ.mapNotNull { it.completion?.skipReason }.groupingBy { it }.eachCount()
        reasons.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.let {
            out += "Most common skip reason: \"${it.key.label}\" (${it.value}×)."
        }
        if (out.isEmpty() && done > 0) out += "Steady week: $done tasks done."
        return out
    }
}
