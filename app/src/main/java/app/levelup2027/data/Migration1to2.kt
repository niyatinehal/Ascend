package app.levelup2027.data

/**
 * Database v1 (Level Up 2027) → v2 (Ascend with AI planning). Adds columns with defaults, creates
 * the new tables, maps the old category enum names onto category rows, and keeps every existing
 * task, completion, goal, progress entry and review. Plain SQL strings so a JVM test can run them
 * against a real SQLite database built from the exported v1 schema (see MigrationTest).
 */
object Migration1to2 {
    private fun q(s: String) = "'" + s.replace("'", "''") + "'"

    fun statements(): List<String> = buildList {
        // tasks
        add("ALTER TABLE `tasks` ADD COLUMN `priority` INTEGER NOT NULL DEFAULT 2")
        add("ALTER TABLE `tasks` ADD COLUMN `milestoneId` TEXT")
        add("ALTER TABLE `tasks` ADD COLUMN `dependsOn` TEXT NOT NULL DEFAULT ''")
        add("ALTER TABLE `tasks` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'MANUAL'")
        add("ALTER TABLE `tasks` ADD COLUMN `exactTime` TEXT")
        add("ALTER TABLE `tasks` ADD COLUMN `weeklyOutcomeId` TEXT")
        add("ALTER TABLE `tasks` ADD COLUMN `originTaskId` TEXT")
        add("ALTER TABLE `tasks` ADD COLUMN `originDate` INTEGER")
        add("ALTER TABLE `tasks` ADD COLUMN `isRest` INTEGER NOT NULL DEFAULT 0")
        add("ALTER TABLE `tasks` ADD COLUMN `planId` TEXT")
        add(
            "UPDATE `tasks` SET `category` = CASE `category` " +
                Cat.LEGACY.entries.joinToString(" ") { (old, new) -> "WHEN ${q(old)} THEN ${q(new)}" } +
                " ELSE `category` END",
        )
        add("UPDATE `tasks` SET `source` = 'SEED' WHERE `isSeed` = 1")
        // v1 bounded recurring example-plan tasks by the campaign implicitly; v2 stores it on the task.
        add("UPDATE `tasks` SET `activeFrom` = ${Campaign.START.toEpochDay()} WHERE `isSeed` = 1 AND `recurrenceRule` IS NOT NULL AND `activeFrom` IS NULL")
        add("UPDATE `tasks` SET `activeUntil` = ${Campaign.END.toEpochDay()} WHERE `isSeed` = 1 AND `recurrenceRule` IS NOT NULL AND `activeUntil` IS NULL")

        // completions
        add("ALTER TABLE `completions` ADD COLUMN `movedToDate` INTEGER")

        // goals
        add("ALTER TABLE `goals` ADD COLUMN `shortName` TEXT NOT NULL DEFAULT ''")
        add("ALTER TABLE `goals` ADD COLUMN `baseline` TEXT NOT NULL DEFAULT ''")
        add("ALTER TABLE `goals` ADD COLUMN `priority` INTEGER NOT NULL DEFAULT 2")
        add("ALTER TABLE `goals` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'MANUAL'")
        add("ALTER TABLE `goals` ADD COLUMN `metricsJson` TEXT NOT NULL DEFAULT ''")
        add("ALTER TABLE `goals` ADD COLUMN `assumptions` TEXT NOT NULL DEFAULT ''")
        add("ALTER TABLE `goals` ADD COLUMN `deadlineAssumed` INTEGER NOT NULL DEFAULT 0")
        add("ALTER TABLE `goals` ADD COLUMN `categoryId` TEXT")
        add("ALTER TABLE `goals` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
        for (g in Seed.goals) {
            add("UPDATE `goals` SET `shortName` = ${q(g.shortName)}, `source` = 'SEED', `categoryId` = ${q(g.categoryId!!)} WHERE `id` = ${q(g.id)}")
        }

        // new tables (CREATE statements copied from the exported v2 schema)
        add("CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `shortName` TEXT NOT NULL, `iconKey` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        add("CREATE TABLE IF NOT EXISTS `weekly_outcomes` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `milestoneId` TEXT, `weekStart` INTEGER NOT NULL, `title` TEXT NOT NULL, `done` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`id`))")
        add("CREATE INDEX IF NOT EXISTS `index_weekly_outcomes_goalId` ON `weekly_outcomes` (`goalId`)")
        add("CREATE INDEX IF NOT EXISTS `index_weekly_outcomes_weekStart` ON `weekly_outcomes` (`weekStart`)")
        add("CREATE TABLE IF NOT EXISTS `occurrence_overrides` (`taskId` TEXT NOT NULL, `date` INTEGER NOT NULL, `preferMinimum` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`taskId`, `date`))")
        add("CREATE TABLE IF NOT EXISTS `ai_usage` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `at` INTEGER NOT NULL, `operation` TEXT NOT NULL, `model` TEXT NOT NULL, `route` TEXT NOT NULL, `inputTokens` INTEGER NOT NULL, `outputTokens` INTEGER NOT NULL, `cacheReadTokens` INTEGER NOT NULL, `success` INTEGER NOT NULL, `errorCode` TEXT NOT NULL)")
        add("CREATE INDEX IF NOT EXISTS `index_ai_usage_at` ON `ai_usage` (`at`)")
        add("CREATE TABLE IF NOT EXISTS `plan_drafts` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `inputText` TEXT NOT NULL, `answersJson` TEXT NOT NULL, `intakeJson` TEXT NOT NULL, `draftJson` TEXT NOT NULL, `lastError` TEXT NOT NULL, PRIMARY KEY(`id`))")
        add("CREATE TABLE IF NOT EXISTS `change_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `at` INTEGER NOT NULL, `source` TEXT NOT NULL, `summary` TEXT NOT NULL, `detail` TEXT NOT NULL)")
        add("CREATE INDEX IF NOT EXISTS `index_change_log_at` ON `change_log` (`at`)")

        for (c in Cat.DEFAULTS) {
            add(
                "INSERT OR IGNORE INTO `categories` (`id`, `name`, `shortName`, `iconKey`, `colorIndex`, `enabled`, `sortOrder`, `builtIn`) VALUES " +
                    "(${q(c.id)}, ${q(c.name)}, ${q(c.shortName)}, ${q(c.iconKey)}, ${c.colorIndex}, 1, ${c.sortOrder}, 1)",
            )
        }
    }
}
