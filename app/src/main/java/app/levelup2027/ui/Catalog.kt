package app.levelup2027.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.levelup2027.data.CategoryEntity
import app.levelup2027.data.Goal
import app.levelup2027.data.GoalIds

/** Categories and goals by id, for screens that only hold a task. */
data class Catalog(val categories: List<CategoryEntity> = emptyList(), val goals: List<Goal> = emptyList()) {
    private val catById = categories.associateBy { it.id }
    private val goalById = goals.associateBy { it.id }

    fun category(id: String): CategoryEntity =
        catById[id] ?: CategoryEntity(id = id, name = id.replaceFirstChar { it.uppercase() }, shortName = id.take(12), iconKey = "label", colorIndex = 11)

    fun goal(id: String?): Goal? = id?.let(goalById::get)

    fun goalName(id: String?): String = goal(id)?.displayName ?: if (id == null) "No goal" else GoalIds.shortName(id)
}

val LocalCatalog = staticCompositionLocalOf { Catalog() }
