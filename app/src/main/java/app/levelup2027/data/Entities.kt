package app.levelup2027.data

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
 * activity never appears twice.
 */
@Serializable
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: Category,
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
) {
    val isOneTime: Boolean get() = scheduledDate != null
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
) {
    val isDone: Boolean get() = completionType != CompletionType.SKIPPED
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
    val status: String = "active",
    val sortOrder: Int = 0,
)

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
