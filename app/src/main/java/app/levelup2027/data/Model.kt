package app.levelup2027.data

import java.time.LocalDate

/** Campaign window and checkpoints. */
object Campaign {
    val START: LocalDate = LocalDate.of(2026, 10, 26)
    val END: LocalDate = LocalDate.of(2027, 12, 31)
    val FIRST_CHECKPOINT: LocalDate = LocalDate.of(2026, 12, 31)
    val CAREER_CHECKPOINT: LocalDate = LocalDate.of(2027, 6, 30)
}

enum class Category(val label: String, val shortLabel: String) {
    CAREER("Career & Interviews", "Career"),
    TECHNICAL("Technical Learning", "Tech"),
    BUSINESS("Business", "Business"),
    FITNESS("Fitness", "Fitness"),
    FINANCE("Finance", "Finance"),
    FAMILY("Family", "Family"),
    PLANNING("Planning & Review", "Planning"),
    RECOVERY("Personal Care / Recovery", "Recovery"),
}

enum class TimeSlot(val label: String, val shortLabel: String) {
    MORNING("Morning", "Morning"),
    WORK("During Work", "Work"),
    EVENING("Evening", "Evening"),
    FLEXIBLE("Flexible / Anytime", "Flexible"),
}

enum class EnergyLevel(val label: String) { LOW("Low effort"), MEDIUM("Medium effort"), HIGH("High effort") }

enum class CompletionType { IDEAL, MINIMUM, SKIPPED }

enum class SkipReason(val label: String) {
    TOO_TIRED("Too tired"),
    NO_TIME("Not enough time"),
    UNCLEAR("Task was unclear"),
    NOT_RELEVANT("Not relevant today"),
    OTHER("Other"),
}

/** How tasks are shown and what a single tap completes. */
enum class DefaultMode(val label: String) {
    DECIDE("Let me decide each time"),
    IDEAL("Ideal"),
    MINIMUM("Minimum"),
}

enum class ThemeChoice(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Follow system") }

/** Fixed goal ids; tasks link to these. */
object GoalIds {
    const val CAREER = "career"
    const val FITNESS = "fitness"
    const val TECH = "tech"
    const val FAMILY = "family"
    const val BUSINESS = "business"
    val ALL = listOf(CAREER, FITNESS, TECH, FAMILY, BUSINESS)

    fun shortName(id: String?): String = when (id) {
        CAREER -> "Career"
        FITNESS -> "Fitness"
        TECH -> "Tech confidence"
        FAMILY -> "Parents' phones"
        BUSINESS -> "Business"
        else -> "All goals"
    }
}

/** A manually recorded metric belonging to a goal. Values are always "current value as of date". */
data class MetricDef(
    val key: String,
    val goalId: String,
    val label: String,
    val unit: String,
    val isCurrency: Boolean = false,
    val decimals: Int = 0,
)

object Metrics {
    val ALL = listOf(
        MetricDef("salary_lpa", GoalIds.CAREER, "Current salary", "LPA", decimals = 1),
        MetricDef("target_salary_lpa", GoalIds.CAREER, "Target salary", "LPA", decimals = 1),
        MetricDef("applications", GoalIds.CAREER, "Applications sent (total)", ""),
        MetricDef("interviews", GoalIds.CAREER, "Interviews (total)", ""),
        MetricDef("offers", GoalIds.CAREER, "Offers (total)", ""),
        MetricDef("weight_kg", GoalIds.FITNESS, "Body weight", "kg", decimals = 1),
        MetricDef("pushups", GoalIds.FITNESS, "Consecutive push-ups (good form)", "reps"),
        MetricDef("pullups", GoalIds.FITNESS, "Strict pull-ups", "reps"),
        MetricDef("eng_exercises", GoalIds.TECH, "Engineering exercises completed (total)", ""),
        MetricDef("analysed_problems", GoalIds.TECH, "Problems analysed independently (total)", ""),
        MetricDef("fund_balance", GoalIds.FAMILY, "Phone fund saved", "", isCurrency = true),
        MetricDef("fund_target", GoalIds.FAMILY, "Phone fund target", "", isCurrency = true),
        MetricDef("mrr", GoalIds.BUSINESS, "Actual MRR", "", isCurrency = true),
        MetricDef("paying_customers", GoalIds.BUSINESS, "Paying customers", ""),
        MetricDef("conversations", GoalIds.BUSINESS, "Customer conversations (total)", ""),
    )

    fun byKey(key: String) = ALL.firstOrNull { it.key == key }
    fun forGoal(goalId: String) = ALL.filter { it.goalId == goalId }

    fun format(def: MetricDef?, value: Double): String {
        val num = if (def == null || def.decimals == 0) "%,d".format(value.toLong())
        else "%,.${def.decimals}f".format(value)
        return when {
            def?.isCurrency == true -> "₹$num"
            def?.unit.isNullOrBlank() -> num
            else -> "$num ${def!!.unit}"
        }
    }
}
