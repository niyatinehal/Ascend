package app.levelup2027.data

import java.time.LocalDate

/** The original Level Up 2027 campaign (used by the example plan only). */
object Campaign {
    val START: LocalDate = LocalDate.of(2026, 10, 26)
    val END: LocalDate = LocalDate.of(2027, 12, 31)
    val FIRST_CHECKPOINT: LocalDate = LocalDate.of(2026, 12, 31)
    val CAREER_CHECKPOINT: LocalDate = LocalDate.of(2027, 6, 30)
}

/**
 * Built-in category ids. Categories themselves are rows in the categories table (user-editable);
 * these constants only name the defaults and map the v1 enum values onto them.
 */
object Cat {
    const val CAREER = "career"
    const val LEARNING = "learning"
    const val FITNESS = "fitness"
    const val HEALTH = "health"
    const val BUSINESS = "business"
    const val FINANCE = "finance"
    const val FAMILY = "family"
    const val RELATIONSHIPS = "relationships"
    const val PROJECTS = "projects"
    const val HOME = "home"
    const val GROWTH = "growth"
    const val PLANNING = "planning"

    /** v1 stored an enum name in tasks.category. */
    val LEGACY = mapOf(
        "CAREER" to CAREER, "TECHNICAL" to LEARNING, "BUSINESS" to BUSINESS, "FITNESS" to FITNESS,
        "FINANCE" to FINANCE, "FAMILY" to FAMILY, "PLANNING" to PLANNING, "RECOVERY" to HEALTH,
    )

    fun fromLegacy(value: String): String = LEGACY[value] ?: value

    /** id, name, short name, icon key. Colours are assigned by position. */
    val DEFAULTS: List<CategoryEntity> = listOf(
        def(CAREER, "Career", "Career", "work"),
        def(LEARNING, "Learning", "Learning", "school"),
        def(FITNESS, "Fitness", "Fitness", "fitness"),
        def(HEALTH, "Health & Wellness", "Health", "wellness"),
        def(BUSINESS, "Business", "Business", "store"),
        def(FINANCE, "Finance", "Finance", "savings"),
        def(FAMILY, "Family", "Family", "family"),
        def(RELATIONSHIPS, "Relationships", "People", "people"),
        def(PROJECTS, "Personal Projects", "Projects", "build"),
        def(HOME, "Home & Life Admin", "Home", "home"),
        def(GROWTH, "Personal Growth", "Growth", "growth"),
        def(PLANNING, "Planning & Review", "Planning", "planning"),
    ).mapIndexed { i, c -> c.copy(colorIndex = i, sortOrder = i) }

    private fun def(id: String, name: String, short: String, icon: String) =
        CategoryEntity(id = id, name = name, shortName = short, iconKey = icon, builtIn = true)
}

object CategoryIcons {
    val KEYS = listOf(
        "work", "school", "fitness", "wellness", "store", "savings", "family", "people", "build", "home", "growth",
        "planning", "code", "language", "music", "art", "book", "travel", "heart", "pets", "food", "sleep", "mind", "label",
    )
}

enum class TimeSlot(val label: String, val shortLabel: String) {
    MORNING("Morning", "Morning"),
    WORK("During Work", "Work"),
    EVENING("Evening", "Evening"),
    FLEXIBLE("Flexible / Anytime", "Flexible"),
}

enum class EnergyLevel(val label: String) { LOW("Low effort"), MEDIUM("Medium effort"), HIGH("High effort") }

/** RESCHEDULED marks an occurrence that was moved to another day; it is neither done nor skipped. */
enum class CompletionType { IDEAL, MINIMUM, SKIPPED, RESCHEDULED }

/** Where a task or goal came from. Shown in the UI and used when revising AI plans. */
enum class Source { SEED, MANUAL, AI }

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

/**
 * A manually recorded metric belonging to a goal. Values are always "current value as of date".
 * Built-in goals use [Metrics.ALL]; AI-planned and user goals store their own list on the goal.
 */
@kotlinx.serialization.Serializable
data class MetricDef(
    val key: String,
    val goalId: String = "",
    val label: String,
    val unit: String = "",
    val isCurrency: Boolean = false,
    val decimals: Int = 0,
    val baseline: Double? = null,
    val target: Double? = null,
    val higherIsBetter: Boolean = true,
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

    /** The goal's own metrics if it has any, otherwise the built-in ones for its id. */
    fun forGoal(goal: Goal): List<MetricDef> =
        goal.metrics.map { it.copy(goalId = goal.id) }.ifEmpty { ALL.filter { it.goalId == goal.id } }

    fun def(goal: Goal?, key: String): MetricDef? =
        goal?.let { g -> forGoal(g).firstOrNull { it.key == key } } ?: byKey(key)

    fun format(def: MetricDef?, value: Double): String {
        val num = if (def == null || def.decimals == 0) "%,d".format(value.toLong())
        else "%,.${def.decimals}f".format(value)
        return when {
            def?.isCurrency == true -> def.unit.ifBlank { "₹" } + num
            def?.unit.isNullOrBlank() -> num
            else -> "$num ${def!!.unit}"
        }
    }
}
