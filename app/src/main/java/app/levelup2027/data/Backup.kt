package app.levelup2027.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 2,
    val exportedAt: Long,
    val tasks: List<Task>,
    val completions: List<Completion>,
    val goals: List<Goal>,
    val milestones: List<Milestone>,
    val progress: List<ProgressEntry>,
    val reviews: List<WeeklyReview>,
    val settings: AppSettings,
    // Added in version 2; absent in v1 files.
    val categories: List<CategoryEntity> = emptyList(),
    val weeklyOutcomes: List<WeeklyOutcome> = emptyList(),
    val overrides: List<OccurrenceOverride> = emptyList(),
    val changeLog: List<ChangeLogEntry> = emptyList(),
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
            categories = db.categories().getAll(),
            weeklyOutcomes = db.plan().getOutcomes(),
            overrides = db.plan().getOverrides(),
            changeLog = db.plan().getChangeLog(),
        )
        return SettingsJson.encodeToString(BackupFile.serializer(), file)
    }

    /**
     * Validates first, then replaces all data in one transaction. Throws on an invalid file.
     * Backups from v1 are upgraded: their category names map onto the new category ids.
     */
    suspend fun restore(json: String): BackupFile {
        val file = parse(json)
        db.withTransaction {
            db.categories().deleteAll(); db.categories().insertAll(file.categories.ifEmpty { Cat.DEFAULTS })
            db.tasks().deleteAll(); db.tasks().insertAll(file.tasks)
            db.completions().deleteAll(); db.completions().insertAll(file.completions)
            db.goals().deleteGoals(); db.goals().insertGoals(file.goals)
            db.goals().deleteMilestones(); db.goals().insertMilestones(file.milestones)
            db.goals().deleteProgressAll(); db.goals().insertProgressAll(file.progress)
            db.reviews().deleteAll(); db.reviews().insertAll(file.reviews)
            db.plan().deleteOutcomes(); db.plan().upsertOutcomes(file.weeklyOutcomes)
            db.plan().deleteOverrides(); db.plan().insertOverrides(file.overrides)
            db.plan().deleteChangeLog(); db.plan().insertChangeLog(file.changeLog)
        }
        val legacyDisabled = file.settings.legacyDisabledCategoryIds
        if (legacyDisabled.isNotEmpty() && file.categories.isEmpty()) {
            db.categories().getAll().filter { it.id in legacyDisabled }.forEach { db.categories().upsert(it.copy(enabled = false)) }
        }
        // Keep this phone's AI choices (consent, mode); everything else comes from the file.
        settings.update { cur -> file.settings.migrated().copy(seedVersion = maxOf(file.settings.seedVersion, cur.seedVersion), ai = cur.ai) }
        return file
    }

    companion object {
        /** Parses and upgrades a backup file without touching the database. */
        fun parse(json: String): BackupFile {
            val file = SettingsJson.decodeFromString(BackupFile.serializer(), json)
            require(file.format == BackupFile.FORMAT) { "This file is not an Ascend backup." }
            return file.copy(tasks = file.tasks.map { it.copy(category = Cat.fromLegacy(it.category)) })
        }
    }
}
