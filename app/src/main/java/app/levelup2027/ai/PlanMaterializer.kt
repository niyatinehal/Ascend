package app.levelup2027.ai

import app.levelup2027.data.Capacity
import app.levelup2027.data.Cat
import app.levelup2027.data.CategoryEntity
import app.levelup2027.data.EnergyLevel
import app.levelup2027.data.Goal
import app.levelup2027.data.MetricDef
import app.levelup2027.data.Milestone
import app.levelup2027.data.ProgressEntry
import app.levelup2027.data.SettingsJson
import app.levelup2027.data.Source
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import app.levelup2027.data.WeeklyOutcome
import app.levelup2027.domain.Dates
import app.levelup2027.domain.Recurrence
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate

/** Everything an accepted plan adds. Nothing is deleted or overwritten by accepting a plan. */
data class PlanMaterialization(
    val categories: List<CategoryEntity>,
    val goals: List<Goal>,
    val milestones: List<Milestone>,
    val outcomes: List<WeeklyOutcome>,
    val tasks: List<Task>,
    val progress: List<ProgressEntry>,
    val capacity: Capacity,
    val skippedDuplicates: List<String>,
)

/**
 * Turns a validated draft into database rows with stable ids. Draft keys are local to one AI
 * response; ids are generated here once and never derived from model output again, so later
 * revisions update rows by id instead of recreating them.
 */
class PlanMaterializer(
    private val now: () -> Long = System::currentTimeMillis,
    private val randomSuffix: () -> String = { java.util.UUID.randomUUID().toString().take(6) },
) {
    fun materialize(
        plan: ValidatedPlan,
        planId: String,
        existingCategories: List<CategoryEntity>,
        existingTasks: List<Task>,
        existingGoals: List<Goal>,
    ): PlanMaterialization {
        val d = plan.draft
        val t0 = now()
        fun id(prefix: String, key: String) = "$prefix-${key.take(24)}-${randomSuffix()}"

        // Categories: reuse existing ids; create only genuinely new ones, coloured after the existing palette.
        val existingIds = existingCategories.map { it.id }.toSet()
        var nextColor = (existingCategories.maxOfOrNull { it.colorIndex } ?: -1) + 1
        var nextSort = (existingCategories.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val newCategories = d.categories.filter { it.key !in existingIds }.map { c ->
            CategoryEntity(id = c.key, name = c.name, shortName = c.name.take(12), iconKey = c.icon, colorIndex = nextColor++, sortOrder = nextSort++)
        }
        val knownCategories = existingIds + newCategories.map { it.id }
        fun category(key: String, goalCategory: String?) =
            key.takeIf { it in knownCategories } ?: goalCategory?.takeIf { it in knownCategories } ?: Cat.GROWTH

        // Goals: a goal with the same title as an existing active goal is reused, not duplicated.
        val skipped = mutableListOf<String>()
        val goalIdByKey = HashMap<String, String>()
        val goals = mutableListOf<Goal>()
        val progress = mutableListOf<ProgressEntry>()
        var sort = (existingGoals.maxOfOrNull { it.sortOrder } ?: -1) + 1
        for (g in d.goals) {
            val existing = existingGoals.firstOrNull { it.isActive && PlanValidator.normalizeTitle(it.title) == PlanValidator.normalizeTitle(g.title) }
            if (existing != null) { goalIdByKey[g.key] = existing.id; skipped += "Goal \"${g.title}\" already exists"; continue }
            val gid = id("g", g.key)
            goalIdByKey[g.key] = gid
            val deadline = PlanValidator.parseDate(g.deadline)
            val metrics = g.metrics.map { m ->
                MetricDef(key = m.key, goalId = gid, label = m.label, unit = m.unit, baseline = m.baseline, target = m.target, higherIsBetter = m.higherIsBetter,
                    decimals = if (listOf(m.baseline, m.target).any { it != null && it % 1.0 != 0.0 }) 1 else 0)
            }
            goals += Goal(
                id = gid,
                title = g.title,
                description = g.outcome,
                target = g.target,
                // Goals without a deadline get a one-year horizon for scheduling only, flagged as assumed.
                targetDate = deadline ?: plan.startDate.plusYears(1),
                currentProgress = g.baseline.ifBlank { "Not recorded yet" },
                progressUnit = metrics.firstOrNull()?.unit ?: "",
                sortOrder = sort++,
                shortName = g.shortName,
                baseline = g.baseline,
                priority = g.priority,
                source = Source.AI,
                metricsJson = if (metrics.isEmpty()) "" else SettingsJson.encodeToString(ListSerializer(MetricDef.serializer()), metrics),
                deadlineAssumed = g.deadlineAssumed || deadline == null,
                categoryId = g.categoryKey.takeIf { it in knownCategories },
                createdAt = t0,
            )
            // Baselines the user stated become the first progress entry; nothing is invented.
            metrics.filter { it.baseline != null }.forEach { m ->
                progress += ProgressEntry(goalId = gid, date = plan.startDate, metric = m.key, value = m.baseline!!, notes = "Starting point from your plan", createdAt = t0)
            }
        }
        val goalCategory = d.goals.associate { it.key to it.categoryKey.takeIf { c -> c in knownCategories } }

        val milestoneIdByKey = HashMap<String, String>()
        val milestones = d.goals.filter { goalIdByKey[it.key] in goals.map { g -> g.id } }.flatMap { g ->
            g.milestones.mapIndexed { i, m ->
                val mid = id("m", m.key)
                milestoneIdByKey[m.key] = mid
                Milestone(mid, goalIdByKey.getValue(g.key), PlanValidator.parseDate(m.due)!!, m.title, sortOrder = i)
            }
        }

        val weekStart = Dates.weekStart(plan.startDate)
        val outcomes = d.weeklyOutcomes.mapIndexed { i, o ->
            WeeklyOutcome(id("wo", o.title.take(12).lowercase().replace(' ', '-')), goalIdByKey[o.goalKey] ?: return@mapIndexed null,
                o.milestoneKey.let(milestoneIdByKey::get), weekStart, o.title, sortOrder = i)
        }.filterNotNull()

        // Tasks: two passes so dependencies can be rewritten from draft keys to real ids.
        val taskIdByKey = (d.habits + d.firstWeek).associate { it.key to id("t", it.key) }
        val existingRecurring = existingTasks.filter { it.enabled && it.recurrenceRule != null }
        val existingDated = existingTasks.filter { it.enabled && it.scheduledDate != null }
        val tasks = mutableListOf<Task>()
        var order = 100
        for (h in d.habits) {
            val rule = Recurrence(PlanValidator.parseDays(h.days)).encode()
            val dup = existingRecurring.any { PlanValidator.normalizeTitle(it.title) == PlanValidator.normalizeTitle(h.title) && it.recurrenceRule == rule }
            if (dup) { skipped += "Habit \"${h.title}\" is already in your plan"; continue }
            val goalId = goalIdByKey[h.goalKey]
            tasks += toTask(h, taskIdByKey, goalId, milestoneIdByKey, category(h.categoryKey, goalCategory[h.goalKey]), planId, t0, order++).copy(
                recurrenceRule = rule,
                activeFrom = plan.startDate,
                activeUntil = goals.firstOrNull { it.id == goalId }?.takeIf { !it.deadlineAssumed }?.targetDate,
            )
        }
        for (w in d.firstWeek) {
            val date = PlanValidator.parseDate(w.date)!!
            val dup = existingDated.any { it.scheduledDate == date && PlanValidator.normalizeTitle(it.title) == PlanValidator.normalizeTitle(w.title) }
            if (dup) { skipped += "\"${w.title}\" is already planned on $date"; continue }
            tasks += toTask(w, taskIdByKey, goalIdByKey[w.goalKey], milestoneIdByKey, category(w.categoryKey, goalCategory[w.goalKey]), planId, t0, order++)
                .copy(scheduledDate = date)
        }
        val kept = tasks.map { it.id }.toSet()
        val cleaned = tasks.map { t -> t.copy(dependsOn = t.dependencyIds.filter { it in kept }.joinToString(",")) }

        return PlanMaterialization(
            categories = newCategories,
            goals = goals,
            milestones = milestones,
            outcomes = outcomes,
            tasks = cleaned,
            progress = progress,
            capacity = Capacity(plan.weekdayMinutes, plan.weekendMinutes),
            skippedDuplicates = skipped,
        )
    }

    /** Next-week tasks for existing goals. */
    fun materializeWeek(
        week: WeekPlan,
        weekStart: LocalDate,
        planId: String,
        existingCategories: List<CategoryEntity>,
        goals: List<Goal>,
    ): Pair<List<Task>, List<WeeklyOutcome>> {
        val t0 = now()
        val known = existingCategories.map { it.id }.toSet()
        val goalById = goals.associateBy { it.id }
        val ids = week.tasks.associate { it.key to "t-${it.key.take(24)}-${randomSuffix()}" }
        var order = 200
        val tasks = week.tasks.map { w ->
            val goal = goalById[w.goalKey]
            toTask(w, ids, goal?.id, emptyMap(), w.categoryKey.takeIf { it in known } ?: goal?.categoryId ?: Cat.GROWTH, planId, t0, order++)
                .copy(scheduledDate = PlanValidator.parseDate(w.date))
        }
        val outcomes = week.weeklyOutcomes.mapIndexed { i, o ->
            WeeklyOutcome("wo-${randomSuffix()}", o.goalKey, null, Dates.weekStart(weekStart), o.title, sortOrder = i)
        }
        return tasks to outcomes
    }

    private fun toTask(
        t: DraftTask,
        idByKey: Map<String, String>,
        goalId: String?,
        milestoneIdByKey: Map<String, String>,
        category: String,
        planId: String,
        now: Long,
        order: Int,
    ) = Task(
        id = idByKey.getValue(t.key),
        title = t.title,
        description = t.description,
        category = category,
        timeSlot = TimeSlot.valueOf(t.timeSlot),
        idealDescription = t.ideal,
        minimumDescription = t.minimum,
        estimatedIdealMinutes = t.idealMinutes,
        estimatedMinimumMinutes = t.minimumMinutes,
        goalId = goalId,
        energyLevel = EnergyLevel.valueOf(t.energy),
        sortOrder = order,
        countsAsWorkout = t.isWorkout,
        createdAt = now,
        updatedAt = now,
        priority = t.priority,
        milestoneId = milestoneIdByKey[t.milestoneKey],
        dependsOn = t.dependsOn.mapNotNull(idByKey::get).joinToString(","),
        source = Source.AI,
        exactTime = t.exactTime.ifBlank { null },
        isRest = t.isRest,
        planId = planId,
    )
}
