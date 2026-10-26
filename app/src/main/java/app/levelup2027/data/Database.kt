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

/**
 * Schema version history is exported to app/schemas. When the schema changes, bump [version]
 * and add a Migration to [MIGRATIONS]; destructive fallback is intentionally not enabled.
 */
@Database(
    entities = [Task::class, Completion::class, Goal::class, Milestone::class, ProgressEntry::class, WeeklyReview::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun completions(): CompletionDao
    abstract fun goals(): GoalDao
    abstract fun reviews(): ReviewDao

    companion object {
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "levelup2027.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
