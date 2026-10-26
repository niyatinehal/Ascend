package app.levelup2027.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/**
 * A task definition. Either one-time ([scheduledDate] set) or recurring ([recurrenceRule] set,
 * e.g. "DAILY" or "WEEKLY:MON,THU"), active between [activeFrom] and [activeUntil].
 * A one-time task with [supersedesTaskId] replaces that recurring task on its date, so the same
 * activity never appears twice. A one-time task with [originTaskId] is a rescheduled occurrence of
 * that task from [originDate].
 *
 * [category] holds a category id (see [CategoryEntity]); [dependsOn] is a comma-separated list of
 * task ids that should be done first; [priority] is 1 (high) to 3 (low).
 */
@Serializable
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String,
    val timeSlot: TimeSlot,
    val idealDescription: String,
    val minimumDescription: String,
    val estimatedIdealMinutes: Int,
    val estimatedMinimumMinutes: Int,
    @Serializable(with = LocalDateSerializer::class) val scheduledDate: LocalDate? = null,
    val recurrenceRule: String? = null,
    @Serializable(with = LocalDateSerializer::class) val activeFrom: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val activeUntil: LocalDate? = null,
    val supersedesTaskId: String? = null,
    val goalId: String? = null,
    val energyLevel: EnergyLevel = EnergyLevel.MEDIUM,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val countsAsWorkout: Boolean = false,
    val notes: String = "",
    val resources: String = "",
    val isSeed: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "2") val priority: Int = 2,
    val milestoneId: String? = null,
    @ColumnInfo(defaultValue = "") val dependsOn: String = "",
    @ColumnInfo(defaultValue = "MANUAL") val source: Source = Source.MANUAL,
    /** "HH:mm", only when the user gave a time or asked for time-blocking. */
    val exactTime: String? = null,
    val weeklyOutcomeId: String? = null,
    val originTaskId: String? = null,
    @Serializable(with = LocalDateSerializer::class) val originDate: LocalDate? = null,
    /** A planned rest/recovery slot: its minimum version may legitimately be "rest". */
    @ColumnInfo(defaultValue = "0") val isRest: Boolean = false,
    val planId: String? = null,
) {
    val isOneTime: Boolean get() = scheduledDate != null
    val dependencyIds: List<String> get() = dependsOn.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** One record per (task, date). Kept separate from definitions so edits never erase history. */
@Serializable
@Entity(
    tableName = "completions",
    indices = [Index(value = ["taskId", "scheduledDate"], unique = true), Index("scheduledDate")],
)
data class Completion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val taskTitle: String,
    @Serializable(with = LocalDateSerializer::class) val scheduledDate: LocalDate,
    val completionType: CompletionType,
    val completedAt: Long,
    val skipReason: SkipReason? = null,
    val notes: String = "",
    /** Set when [completionType] is RESCHEDULED. */
    @Serializable(with = LocalDateSerializer::class) val movedToDate: LocalDate? = null,
) {
    val isDone: Boolean get() = completionType == CompletionType.IDEAL || completionType == CompletionType.MINIMUM
}

@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val target: String,
    @Serializable(with = LocalDateSerializer::class) val targetDate: LocalDate,
    val currentProgress: String,
    val progressUnit: String,
    val status: String = GoalStatus.ACTIVE,
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "") val shortName: String = "",
    @ColumnInfo(defaultValue = "") val baseline: String = "",
    @ColumnInfo(defaultValue = "2") val priority: Int = 2,
    @ColumnInfo(defaultValue = "MANUAL") val source: Source = Source.MANUAL,
    /** JSON list of [MetricDef]; empty means "use the built-in metrics for this goal id". */
    @ColumnInfo(defaultValue = "") val metricsJson: String = "",
    @ColumnInfo(defaultValue = "") val assumptions: String = "",
    /** True when the AI picked [targetDate] because the user didn't give one. */
    @ColumnInfo(defaultValue = "0") val deadlineAssumed: Boolean = false,
    val categoryId: String? = null,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
) {
    val metrics: List<MetricDef>
        get() = if (metricsJson.isBlank()) emptyList()
        else runCatching { SettingsJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(MetricDef.serializer()), metricsJson) }.getOrDefault(emptyList())

    val displayName: String get() = shortName.ifBlank { title }
    val isActive: Boolean get() = status == GoalStatus.ACTIVE
}

object GoalStatus {
    const val ACTIVE = "active"
    const val PAUSED = "paused"
    const val DROPPED = "dropped"
    const val DONE = "done"
}

@Serializable
@Entity(tableName = "milestones", indices = [Index("goalId")])
data class Milestone(
    @PrimaryKey val id: String,
    val goalId: String,
    @Serializable(with = LocalDateSerializer::class) val dueDate: LocalDate,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(tableName = "progress_entries", indices = [Index("goalId"), Index("metric")])
data class ProgressEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val metric: String,
    val value: Double,
    val notes: String = "",
    val isEstimate: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
@Entity(tableName = "weekly_reviews", indices = [Index(value = ["weekStartDate"], unique = true)])
data class WeeklyReview(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Serializable(with = LocalDateSerializer::class) val weekStartDate: LocalDate,
    val wins: String = "",
    val blockers: String = "",
    val adjustments: String = "",
    val nextPriority: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** A user-editable category. Colours come from a fixed palette by [colorIndex]; icons by [iconKey]. */
@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shortName: String,
    val iconKey: String = "label",
    val colorIndex: Int = 0,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val builtIn: Boolean = false,
)

/** What a goal should achieve in one week; the AI writes these when it plans a week. */
@Serializable
@Entity(tableName = "weekly_outcomes", indices = [Index("goalId"), Index("weekStart")])
data class WeeklyOutcome(
    @PrimaryKey val id: String,
    val goalId: String,
    val milestoneId: String? = null,
    @Serializable(with = LocalDateSerializer::class) val weekStart: LocalDate,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Int = 0,
    val source: Source = Source.AI,
)

/** A one-day tweak to an occurrence (e.g. "do the minimum version tonight") without editing the task. */
@Serializable
@Entity(tableName = "occurrence_overrides", primaryKeys = ["taskId", "date"])
data class OccurrenceOverride(
    val taskId: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val preferMinimum: Boolean = true,
    val note: String = "",
    val createdAt: Long = 0,
)

/** One AI request, for the usage screen and local quotas. Never stores prompt or response text. */
@Entity(tableName = "ai_usage", indices = [Index("at")])
data class AiUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val operation: String,
    val model: String,
    val route: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val success: Boolean,
    val errorCode: String = "",
)

/**
 * A plan conversation that hasn't been accepted yet. Kept so a failed or interrupted generation
 * never loses what the user typed. Nothing in here is on the schedule until accepted.
 */
@Entity(tableName = "plan_drafts")
data class PlanDraftRecord(
    @PrimaryKey val id: String,
    val kind: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val inputText: String = "",
    val answersJson: String = "",
    val intakeJson: String = "",
    val draftJson: String = "",
    val lastError: String = "",
)

/** An audit trail of plan changes made by the AI or by plan acceptance. */
@Serializable
@Entity(tableName = "change_log", indices = [Index("at")])
data class ChangeLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val source: String,
    val summary: String,
    val detail: String = "",
)
