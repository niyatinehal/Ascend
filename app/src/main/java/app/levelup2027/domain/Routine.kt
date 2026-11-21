package app.levelup2027.domain

import app.levelup2027.data.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

/** The three fixed day presets. Users fill in their blocks; they can't add, delete or rename presets. */
@Serializable
enum class PresetKind(val label: String) {
    DAILY("Daily Routine"),
    PRODUCTIVE("Extreme Productive"),
    LAZY("Lazy Day"),
}

/** REQUIRED: must set up Daily Routine before using the app. PROMPT: one-time card on Today. DONE: nothing to ask. */
@Serializable
enum class RoutineSetup { REQUIRED, PROMPT, DONE }

/** A labelled stretch of time. Minutes since local midnight; [endMinute] may be 1440 (ends at midnight). */
@Serializable
data class Block(val startMinute: Int, val endMinute: Int, val label: String)

/** Blocks for one preset on one date only. Never touches the saved preset; stale once [date] has passed. */
@Serializable
data class DayOverride(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val kind: PresetKind,
    val blocks: List<Block>,
)

@Serializable
data class RoutineSettings(
    val presets: Map<PresetKind, List<Block>> = emptyMap(),
    /** Only meaningful on [activeDate]; every other day runs Daily Routine. */
    val activeKind: PresetKind = PresetKind.DAILY,
    @Serializable(with = LocalDateSerializer::class) val activeDate: LocalDate? = null,
    val checkInsEnabled: Boolean = true,
    val setup: RoutineSetup = RoutineSetup.REQUIRED,
    val promptDismissed: Boolean = false,
    val todayOverride: DayOverride? = null,
)

object Routine {
    const val MAX_LABEL = 40
    private const val DAY_MINUTES = 1440

    private val STOP_WORDS = setOf("the", "and", "for", "with", "time", "day")

    /** Empty means valid. Messages are shown to the user as-is. */
    fun validate(blocks: List<Block>): List<String> {
        val problems = mutableListOf<String>()
        for (b in blocks) {
            val name = b.label.trim().ifBlank { "A block" }
            when {
                b.label.isBlank() -> problems += "Every block needs a name."
                b.label.trim().length > MAX_LABEL -> problems += "\"${name.take(20)}…\" is longer than $MAX_LABEL characters."
            }
            if (b.startMinute < 0 || b.endMinute > DAY_MINUTES) problems += "$name must stay within one day."
            else if (b.endMinute <= b.startMinute) problems += "$name has to end after it starts."
        }
        val sorted = blocks.filter { it.endMinute > it.startMinute }.sortedBy { it.startMinute }
        sorted.zipWithNext().forEach { (a, b) ->
            if (b.startMinute < a.endMinute) problems += "${a.label.trim()} and ${b.label.trim()} overlap."
        }
        return problems
    }

    fun normalized(blocks: List<Block>): List<Block> = blocks.map { it.copy(label = it.label.trim()) }.sortedBy { it.startMinute }

    fun activeKind(r: RoutineSettings, date: LocalDate): PresetKind = if (r.activeDate == date) r.activeKind else PresetKind.DAILY

    fun blocksFor(r: RoutineSettings, date: LocalDate): List<Block> {
        val kind = activeKind(r, date)
        val o = r.todayOverride
        return if (o != null && o.date == date && o.kind == kind) o.blocks else r.presets[kind].orEmpty()
    }

    fun currentBlock(blocks: List<Block>, minute: Int): Block? = blocks.firstOrNull { minute >= it.startMinute && minute < it.endMinute }

    fun nextEnding(blocks: List<Block>, minute: Int): Block? = blocks.filter { it.endMinute > minute }.minByOrNull { it.endMinute }

    /**
     * Today's occurrences with the ones that seem to belong to [block] first: an exact time inside
     * the block, or a word of the block's label in the task title or its category name. Stable.
     */
    fun orderForBlock(block: Block, occ: List<Occurrence>, categoryNames: Map<String, String>): List<Occurrence> {
        val labelWords = words(block.label)
        fun matches(o: Occurrence): Boolean {
            val t = o.task.exactTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            if (t != null && t.toSecondOfDay() / 60 in block.startMinute until block.endMinute) return true
            if (labelWords.isEmpty()) return false
            val taskWords = words(o.task.title) + words(categoryNames[o.task.category].orEmpty())
            return labelWords.any { it in taskWords }
        }
        val (first, rest) = occ.partition(::matches)
        return first + rest
    }

    private fun words(s: String): Set<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in STOP_WORDS }.toSet()

    fun format(minute: Int): String {
        val m = minute % DAY_MINUTES
        return "%d:%02d".format(m / 60, m % 60)
    }
}
