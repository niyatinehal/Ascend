package app.levelup2027.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long,
    val tasks: List<Task>,
    val completions: List<Completion>,
    val goals: List<Goal>,
    val milestones: List<Milestone>,
    val progress: List<ProgressEntry>,
    val reviews: List<WeeklyReview>,
    val settings: AppSettings,
) {
    companion object { const val FORMAT = "levelup2027-backup" }
}

class BackupManager(private val db: AppDatabase, private val settings: SettingsSource) {

    suspend fun export(): String {
        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            tasks = db.tasks().getAll(),
            completions = db.completions().getAll(),
            goals = db.goals().getGoals(),
            milestones = db.goals().getMilestones(),
            progress = db.goals().getProgress(),
            reviews = db.reviews().getAll(),
            settings = settings.current(),
        )
        return SettingsJson.encodeToString(BackupFile.serializer(), file)
    }

    /** Validates first, then replaces all data in one transaction. Throws on an invalid file. */
    suspend fun restore(json: String): BackupFile {
        val file = SettingsJson.decodeFromString(BackupFile.serializer(), json)
        require(file.format == BackupFile.FORMAT) { "This file is not a Ascend backup." }
        db.withTransaction {
            db.tasks().deleteAll(); db.tasks().insertAll(file.tasks)
            db.completions().deleteAll(); db.completions().insertAll(file.completions)
            db.goals().deleteGoals(); db.goals().insertGoals(file.goals)
            db.goals().deleteMilestones(); db.goals().insertMilestones(file.milestones)
            db.goals().deleteProgressAll(); db.goals().insertProgressAll(file.progress)
            db.reviews().deleteAll(); db.reviews().insertAll(file.reviews)
        }
        settings.update { file.settings.copy(seedVersion = maxOf(file.settings.seedVersion, it.seedVersion)) }
        return file
    }
}
