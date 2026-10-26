@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package app.levelup2027.ai

import app.levelup2027.data.CategoryIcons
import app.levelup2027.data.EnergyLevel
import app.levelup2027.data.TimeSlot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * JSON exchanged with the model. snake_case on the wire, camelCase in Kotlin. Lenient on input
 * (the validator, not the parser, decides what is acceptable).
 */
val AiJson = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

// ---- Intake: understand the user's message and ask at most three questions ----

@Serializable
data class UserContext(
    val goals: List<String> = emptyList(),
    val workSchedule: String = "",
    val commute: String = "",
    val weekdayFreeMinutes: Int? = null,
    val weekendFreeMinutes: Int? = null,
    val preferredTime: String = "",
    val energyNotes: String = "",
    val obstacles: List<String> = emptyList(),
    val resources: String = "",
    val habits: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val safetyNotes: List<String> = emptyList(),
    val deadlines: List<String> = emptyList(),
    val baselines: List<String> = emptyList(),
    val unknowns: List<String> = emptyList(),
)

@Serializable
data class ClarifyingQuestion(val id: String, val question: String, val why: String = "", val placeholder: String = "")

@Serializable
data class IntakeResult(
    val context: UserContext = UserContext(),
    val questions: List<ClarifyingQuestion> = emptyList(),
    val readyToPlan: Boolean = false,
)

@Serializable
data class QuestionAnswer(val question: String, val answer: String)

// ---- Plan generation ----

@Serializable
data class PlanWarning(val kind: String, val message: String)

@Serializable
data class DraftCategory(val key: String, val name: String, val icon: String = "label")

@Serializable
data class DraftMetric(
    val key: String,
    val label: String,
    val unit: String = "",
    val baseline: Double? = null,
    val target: Double? = null,
    val higherIsBetter: Boolean = true,
)

@Serializable
data class DraftMilestone(val key: String, val title: String, val due: String)

@Serializable
data class DraftGoal(
    val key: String,
    val title: String,
    val shortName: String = "",
    val categoryKey: String = "",
    val outcome: String = "",
    val baseline: String = "",
    val target: String = "",
    /** YYYY-MM-DD, or "" when there is no sensible deadline. */
    val deadline: String = "",
    val deadlineAssumed: Boolean = false,
    val priority: Int = 2,
    val metrics: List<DraftMetric> = emptyList(),
    val milestones: List<DraftMilestone> = emptyList(),
)

/** A habit (with [days]) or a dated task (with [date]). Keys are local to the draft. */
@Serializable
data class DraftTask(
    val key: String,
    val title: String,
    val description: String = "",
    val goalKey: String = "",
    val milestoneKey: String = "",
    val categoryKey: String = "",
    val timeSlot: String = "FLEXIBLE",
    val exactTime: String = "",
    val days: List<String> = emptyList(),
    val date: String = "",
    val ideal: String = "",
    val idealMinutes: Int = 0,
    val minimum: String = "",
    val minimumMinutes: Int = 0,
    val priority: Int = 2,
    val energy: String = "MEDIUM",
    val dependsOn: List<String> = emptyList(),
    val isRest: Boolean = false,
    val isWorkout: Boolean = false,
)

@Serializable
data class DraftOutcome(val goalKey: String, val milestoneKey: String = "", val title: String)

@Serializable
data class PlanDraft(
    val summary: String = "",
    val assumptions: List<String> = emptyList(),
    val warnings: List<PlanWarning> = emptyList(),
    val weekdayMinutes: Int? = null,
    val weekendMinutes: Int? = null,
    val categories: List<DraftCategory> = emptyList(),
    val goals: List<DraftGoal> = emptyList(),
    val habits: List<DraftTask> = emptyList(),
    val firstWeek: List<DraftTask> = emptyList(),
    val weeklyOutcomes: List<DraftOutcome> = emptyList(),
)

// ---- Next week (progressive generation) ----

@Serializable
data class WeekPlan(
    val summary: String = "",
    val weeklyOutcomes: List<DraftOutcome> = emptyList(),
    val tasks: List<DraftTask> = emptyList(),
    val notes: List<String> = emptyList(),
)

// ---- Adjust my plan ----

object ChangeTypes {
    const val RESCHEDULE = "RESCHEDULE"
    const val USE_MINIMUM = "USE_MINIMUM"
    const val SKIP_OCCURRENCE = "SKIP_OCCURRENCE"
    const val UPDATE_TASK = "UPDATE_TASK"
    const val ADD_TASK = "ADD_TASK"
    const val DISABLE_TASK = "DISABLE_TASK"
    const val UPDATE_GOAL = "UPDATE_GOAL"
    const val SET_CAPACITY = "SET_CAPACITY"
    const val SET_LOW_ENERGY = "SET_LOW_ENERGY"
    val ALL = listOf(RESCHEDULE, USE_MINIMUM, SKIP_OCCURRENCE, UPDATE_TASK, ADD_TASK, DISABLE_TASK, UPDATE_GOAL, SET_CAPACITY, SET_LOW_ENERGY)
}

/** One proposed change. Unused fields are empty / null; the validator reads only what [type] needs. */
@Serializable
data class ChangeOp(
    val type: String,
    val taskId: String = "",
    val date: String = "",
    val newDate: String = "",
    val goalId: String = "",
    val title: String = "",
    val ideal: String = "",
    val minimum: String = "",
    val idealMinutes: Int? = null,
    val minimumMinutes: Int? = null,
    val timeSlot: String = "",
    val days: List<String> = emptyList(),
    val priority: Int? = null,
    val goalStatus: String = "",
    val goalDeadline: String = "",
    val weekdayMinutes: Int? = null,
    val weekendMinutes: Int? = null,
    val newTask: DraftTask? = null,
    val reason: String = "",
)

@Serializable
data class AdjustResult(
    val reply: String = "",
    val needsClarification: Boolean = false,
    val question: String = "",
    val changes: List<ChangeOp> = emptyList(),
)

@Serializable
data class ChatTurn(val role: String, val text: String)

/**
 * JSON Schemas for structured outputs. Every object lists all properties as required and sets
 * additionalProperties=false, as the API requires; optional values are expressed as nullable or empty.
 * No numeric/string length constraints (unsupported): the validator enforces ranges.
 */
object Schemas {
    private val nullableInt = buildJsonObject { putJsonArray("anyOf") { add(obj("type" to "integer")); add(obj("type" to "null")) } }
    private val nullableNumber = buildJsonObject { putJsonArray("anyOf") { add(obj("type" to "number")); add(obj("type" to "null")) } }
    private fun obj(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) })
    private fun arrayOf(items: JsonElement) = buildJsonObject { put("type", "array"); put("items", items) }
    private val stringArray = arrayOf(obj("type" to "string"))

    class JsonObjectBuilderScope {
        val props = LinkedHashMap<String, JsonElement>()
        fun prop(name: String, schema: JsonElement) { props[name] = schema }
        fun string(name: String, enum: List<String>? = null) = prop(name, buildJsonObject {
            put("type", "string")
            if (enum != null) putJsonArray("enum") { enum.forEach { add(JsonPrimitive(it)) } }
        })
        fun integer(name: String) = prop(name, obj("type" to "integer"))
        fun boolean(name: String) = prop(name, obj("type" to "boolean"))
        fun stringList(name: String) = prop(name, stringArray)
    }

    private fun objectSchema(block: JsonObjectBuilderScope.() -> Unit): JsonObject {
        val scope = JsonObjectBuilderScope().apply(block)
        return buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(scope.props))
            putJsonArray("required") { scope.props.keys.forEach { add(JsonPrimitive(it)) } }
            put("additionalProperties", false)
        }
    }

    private val slots = TimeSlot.entries.map { it.name }
    private val energies = EnergyLevel.entries.map { it.name }
    private val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    private val task: JsonObject = objectSchema {
        string("key"); string("title"); string("description")
        string("goal_key"); string("milestone_key"); string("category_key")
        string("time_slot", slots)
        string("exact_time")
        prop("days", arrayOf(buildJsonObject { put("type", "string"); putJsonArray("enum") { days.forEach { add(JsonPrimitive(it)) } } }))
        string("date")
        string("ideal"); integer("ideal_minutes"); string("minimum"); integer("minimum_minutes")
        integer("priority")
        string("energy", energies)
        stringList("depends_on")
        boolean("is_rest"); boolean("is_workout")
    }

    private val outcome = objectSchema { string("goal_key"); string("milestone_key"); string("title") }

    val intake: JsonObject = objectSchema {
        prop("context", objectSchema {
            stringList("goals"); string("work_schedule"); string("commute")
            prop("weekday_free_minutes", nullableInt); prop("weekend_free_minutes", nullableInt)
            string("preferred_time"); string("energy_notes"); stringList("obstacles"); string("resources")
            stringList("habits"); stringList("constraints"); stringList("safety_notes")
            stringList("deadlines"); stringList("baselines"); stringList("unknowns")
        })
        prop("questions", arrayOf(objectSchema { string("id"); string("question"); string("why"); string("placeholder") }))
        boolean("ready_to_plan")
    }

    val plan: JsonObject = objectSchema {
        string("summary")
        stringList("assumptions")
        prop("warnings", arrayOf(objectSchema {
            string("kind", listOf("UNREALISTIC", "CONFLICT", "CAPACITY", "SAFETY", "OTHER")); string("message")
        }))
        prop("weekday_minutes", nullableInt); prop("weekend_minutes", nullableInt)
        prop("categories", arrayOf(objectSchema { string("key"); string("name"); string("icon", CategoryIcons.KEYS) }))
        prop("goals", arrayOf(objectSchema {
            string("key"); string("title"); string("short_name"); string("category_key")
            string("outcome"); string("baseline"); string("target"); string("deadline"); boolean("deadline_assumed")
            integer("priority")
            prop("metrics", arrayOf(objectSchema {
                string("key"); string("label"); string("unit")
                prop("baseline", nullableNumber); prop("target", nullableNumber); boolean("higher_is_better")
            }))
            prop("milestones", arrayOf(objectSchema { string("key"); string("title"); string("due") }))
        }))
        prop("habits", arrayOf(task))
        prop("first_week", arrayOf(task))
        prop("weekly_outcomes", arrayOf(outcome))
    }

    val week: JsonObject = objectSchema {
        string("summary")
        prop("weekly_outcomes", arrayOf(outcome))
        prop("tasks", arrayOf(task))
        stringList("notes")
    }

    val adjust: JsonObject = objectSchema {
        string("reply"); boolean("needs_clarification"); string("question")
        prop("changes", arrayOf(objectSchema {
            string("type", ChangeTypes.ALL)
            string("task_id"); string("date"); string("new_date"); string("goal_id")
            string("title"); string("ideal"); string("minimum")
            prop("ideal_minutes", nullableInt); prop("minimum_minutes", nullableInt)
            string("time_slot")
            stringList("days")
            prop("priority", nullableInt)
            string("goal_status"); string("goal_deadline")
            prop("weekday_minutes", nullableInt); prop("weekend_minutes", nullableInt)
            prop("new_task", buildJsonObject { putJsonArray("anyOf") { add(task); add(obj("type" to "null")) } })
            string("reason")
        }))
    }
}
