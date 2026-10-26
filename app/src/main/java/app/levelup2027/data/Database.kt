package app.levelup2027.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class Converters {
    @TypeConverter fun fromDate(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun toDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY timeSlot, sortOrder, title")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): Task?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(tasks: List<Task>)

    @Upsert
    suspend fun upsert(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>)

    @Query("UPDATE tasks SET category = :to WHERE category = :from")
    suspend fun reassignCategory(from: String, to: String)
}

@Dao
interface CompletionDao {
    @Query("SELECT * FROM completions ORDER BY scheduledDate DESC, completedAt DESC")
    fun observeAll(): Flow<List<Completion>>

    @Query("SELECT * FROM completions")
    suspend fun getAll(): List<Completion>

    @Query("SELECT * FROM completions WHERE scheduledDate = :date")
    suspend fun forDate(date: LocalDate): List<Completion>

    @Query("SELECT * FROM completions WHERE taskId = :taskId AND scheduledDate = :date")
    suspend fun get(taskId: String, date: LocalDate): Completion?

    /** The (taskId, scheduledDate) unique index makes this replace any earlier record for that day. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(c: Completion)

    @Query("DELETE FROM completions WHERE taskId = :taskId AND scheduledDate = :date")
    suspend fun delete(taskId: String, date: LocalDate)

    @Query("DELETE FROM completions")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Completion>)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY sortOrder")
    fun observeGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals")
    suspend fun getGoals(): List<Goal>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGoalsIfAbsent(goals: List<Goal>)

    @Upsert
    suspend fun upsertGoal(goal: Goal)

    @Query("SELECT * FROM milestones ORDER BY dueDate, sortOrder")
    fun observeMilestones(): Flow<List<Milestone>>

    @Query("SELECT * FROM milestones")
    suspend fun getMilestones(): List<Milestone>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMilestonesIfAbsent(list: List<Milestone>)

    @Upsert
    suspend fun upsertMilestone(m: Milestone)

    @Query("SELECT * FROM progress_entries ORDER BY date DESC, createdAt DESC")
    fun observeProgress(): Flow<List<ProgressEntry>>

    @Query("SELECT * FROM progress_entries")
    suspend fun getProgress(): List<ProgressEntry>

    @Query("SELECT COUNT(*) FROM progress_entries")
    suspend fun progressCount(): Int

    @Insert
    suspend fun insertProgress(e: ProgressEntry)

    @Delete
    suspend fun deleteProgress(e: ProgressEntry)

    @Query("DELETE FROM goals") suspend fun deleteGoals()
    @Query("DELETE FROM milestones") suspend fun deleteMilestones()
    @Query("DELETE FROM progress_entries") suspend fun deleteProgressAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGoals(list: List<Goal>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMilestones(list: List<Milestone>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProgressAll(list: List<ProgressEntry>)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM weekly_reviews ORDER BY weekStartDate DESC")
    fun observeAll(): Flow<List<WeeklyReview>>

    @Query("SELECT * FROM weekly_reviews")
    suspend fun getAll(): List<WeeklyReview>

    @Query("SELECT * FROM weekly_reviews WHERE weekStartDate = :weekStart")
    suspend fun forWeek(weekStart: LocalDate): WeeklyReview?

    @Upsert
    suspend fun upsert(r: WeeklyReview)

    @Query("DELETE FROM weekly_reviews") suspend fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<WeeklyReview>)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun getAll(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(list: List<CategoryEntity>)

    @Upsert
    suspend fun upsert(c: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM categories") suspend fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<CategoryEntity>)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM weekly_outcomes ORDER BY weekStart DESC, sortOrder")
    fun observeOutcomes(): Flow<List<WeeklyOutcome>>

    @Query("SELECT * FROM weekly_outcomes")
    suspend fun getOutcomes(): List<WeeklyOutcome>

    @Upsert suspend fun upsertOutcomes(list: List<WeeklyOutcome>)
    @Query("DELETE FROM weekly_outcomes") suspend fun deleteOutcomes()

    @Query("SELECT * FROM occurrence_overrides")
    fun observeOverrides(): Flow<List<OccurrenceOverride>>

    @Query("SELECT * FROM occurrence_overrides")
    suspend fun getOverrides(): List<OccurrenceOverride>

    @Query("SELECT * FROM occurrence_overrides WHERE date = :date")
    suspend fun overridesFor(date: LocalDate): List<OccurrenceOverride>

    @Upsert suspend fun upsertOverride(o: OccurrenceOverride)
    @Query("DELETE FROM occurrence_overrides WHERE taskId = :taskId AND date = :date") suspend fun deleteOverride(taskId: String, date: LocalDate)
    @Query("DELETE FROM occurrence_overrides") suspend fun deleteOverrides()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOverrides(list: List<OccurrenceOverride>)

    @Query("SELECT * FROM plan_drafts WHERE id = :id") suspend fun draft(id: String): PlanDraftRecord?
    @Query("SELECT * FROM plan_drafts WHERE status = 'OPEN' AND kind = :kind ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestOpenDraft(kind: String): PlanDraftRecord?
    @Upsert suspend fun upsertDraft(d: PlanDraftRecord)
    @Query("DELETE FROM plan_drafts") suspend fun deleteDrafts()

    @Query("SELECT * FROM change_log ORDER BY at DESC LIMIT :limit")
    fun observeChangeLog(limit: Int = 50): Flow<List<ChangeLogEntry>>
    @Query("SELECT * FROM change_log") suspend fun getChangeLog(): List<ChangeLogEntry>
    @Insert suspend fun log(e: ChangeLogEntry)
    @Query("DELETE FROM change_log") suspend fun deleteChangeLog()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChangeLog(list: List<ChangeLogEntry>)
}

@Dao
interface AiUsageDao {
    @Insert suspend fun insert(u: AiUsage)

    @Query("SELECT * FROM ai_usage WHERE at >= :since ORDER BY at DESC")
    fun observeSince(since: Long): Flow<List<AiUsage>>

    @Query("SELECT * FROM ai_usage WHERE at >= :since")
    suspend fun since(since: Long): List<AiUsage>

    @Query("DELETE FROM ai_usage") suspend fun deleteAll()
}

/**
 * Schema version history is exported to app/schemas. When the schema changes, bump [version]
 * and add a Migration to [MIGRATIONS]; destructive fallback is intentionally not enabled.
 */
@Database(
    entities = [
        Task::class, Completion::class, Goal::class, Milestone::class, ProgressEntry::class, WeeklyReview::class,
        CategoryEntity::class, WeeklyOutcome::class, OccurrenceOverride::class, AiUsage::class, PlanDraftRecord::class,
        ChangeLogEntry::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun completions(): CompletionDao
    abstract fun goals(): GoalDao
    abstract fun reviews(): ReviewDao
    abstract fun categories(): CategoryDao
    abstract fun plan(): PlanDao
    abstract fun aiUsage(): AiUsageDao

    companion object {
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(
            object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    Migration1to2.statements().forEach(db::execSQL)
                }
            },
        )

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "levelup2027.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
