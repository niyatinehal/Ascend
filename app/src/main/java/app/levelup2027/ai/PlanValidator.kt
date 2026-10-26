package app.levelup2027.ai

import app.levelup2027.data.CategoryEntity
import app.levelup2027.data.CategoryIcons
import app.levelup2027.data.EnergyLevel
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.DateFmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

enum class IssueLevel { FIXED, WARNING, ERROR }

data class PlanIssue(val level: IssueLevel, val message: String)

/** A draft that passed validation, plus what the app computed about it. */
data class ValidatedPlan(
    val draft: PlanDraft,
    val startDate: LocalDate,
    val issues: List<PlanIssue>,
    /** Planned ideal minutes for each of the first seven days (habits + dated tasks). */
    val dailyLoad: Map<LocalDate, Int>,
    val weekdayMinutes: Int?,
    val weekendMinutes: Int?,
) {
    val hasErrors: Boolean get() = issues.any { it.level == IssueLevel.ERROR }
    val weeklyMinutes: Int get() = dailyLoad.values.sum()
    fun capacityFor(date: LocalDate): Int? =
        if (date.dayOfWeek.value >= 6) weekendMinutes ?: weekdayMinutes else weekdayMinutes ?: weekendMinutes
}

/**
 * Deterministic checks on anything the model returns. The model proposes; this decides what is
 * acceptable, fixes what can be fixed safely, and reports the rest. It never invents goals.
 */
object PlanValidator {
    const val MAX_GOALS = 8
    const val MAX_HABITS = 15
    const val MAX_WEEK_TASKS = 40
    const val MAX_MINUTES = 240

    private val DAY_CODES = mapOf(
        "MON" to DayOfWeek.MONDAY, "TUE" to DayOfWeek.TUESDAY, "WED" to DayOfWeek.WEDNESDAY, "THU" to DayOfWeek.THURSDAY,
        "FRI" to DayOfWeek.FRIDAY, "SAT" to DayOfWeek.SATURDAY, "SUN" to DayOfWeek.SUNDAY,
    )

    fun parseDays(codes: List<String>): Set<DayOfWeek> = codes.mapNotNull { DAY_CODES[it.trim().uppercase().take(3)] }.toSet()
    fun dayCode(d: DayOfWeek) = d.name.take(3)

    fun parseDate(s: String): LocalDate? = try {
        if (s.isBlank()) null else LocalDate.parse(s.trim().take(10))
    } catch (_: DateTimeParseException) { null }

    fun normalizeTitle(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    fun slug(s: String, fallback: String = "item") =
        s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32).ifBlank { fallback }

    fun validate(
        input: PlanDraft,
        startDate: LocalDate,
        existingCategories: List<CategoryEntity>,
        fallbackWeekdayMinutes: Int? = null,
        fallbackWeekendMinutes: Int? = null,
    ): ValidatedPlan {
        val issues = mutableListOf<PlanIssue>()
        fun fixed(m: String) { issues += PlanIssue(IssueLevel.FIXED, m) }
        fun warn(m: String) { issues += PlanIssue(IssueLevel.WARNING, m) }

        // Categories: new ones from the draft, matched to existing ones by key or name.
        val existingByKey = existingCategories.associateBy { it.id }
        val existingByName = existingCategories.associateBy { it.name.lowercase() }
        val draftCats = LinkedHashMap<String, DraftCategory>()
        for (c in input.categories) {
            val key = slug(c.key.ifBlank { c.name }, "category")
            if (c.name.isBlank() || key in draftCats) continue
            val icon = if (c.icon in CategoryIcons.KEYS) c.icon else "label"
            draftCats[key] = c.copy(key = key, name = c.name.trim().take(40), icon = icon)
        }
        fun resolveCategory(key: String): String? {
            if (key.isBlank()) return null
            val k = key.trim()
            existingByKey[k]?.let { return it.id }
            existingByName[k.lowercase()]?.let { return it.id }
            val s = slug(k)
            if (s in draftCats) return s
            existingByKey[s]?.let { return it.id }
            return draftCats.values.firstOrNull { it.name.equals(k, ignoreCase = true) }?.key
        }

        // Goals
        val goals = mutableListOf<DraftGoal>()
        val goalKeys = HashSet<String>()
        for (g in input.goals) {
            if (g.title.isBlank()) continue
            if (goals.size == MAX_GOALS) { warn("Only the first $MAX_GOALS goals were kept. Fewer goals at once is easier to follow."); break }
            var key = slug(g.key.ifBlank { g.title }, "goal")
            while (key in goalKeys) key += "-2"
            goalKeys += key
            var deadline = parseDate(g.deadline)
            var assumed = g.deadlineAssumed
            if (g.deadline.isNotBlank() && deadline == null) fixed("\"${g.title}\": the deadline wasn't a valid date, so it was removed.")
            if (deadline != null && deadline.isBefore(startDate)) {
                warn("\"${g.title}\": the deadline ${DateFmt.medium(deadline)} has already passed, so it was removed. Set a new one if you have one.")
                deadline = null
            }
            if (deadline == null) assumed = false
            val milestones = g.milestones.mapNotNull { m ->
                val due = parseDate(m.due)
                when {
                    m.title.isBlank() -> null
                    due == null -> { fixed("Milestone \"${m.title}\" had no valid date and was left out."); null }
                    due.isBefore(startDate) -> { fixed("Milestone \"${m.title}\" was dated in the past and was left out."); null }
                    deadline != null && due.isAfter(deadline) -> {
                        fixed("Milestone \"${m.title}\" was after the goal deadline, so it was moved to the deadline.")
                        m.copy(key = slug(m.key.ifBlank { m.title }, "milestone"), due = deadline.toString())
                    }
                    else -> m.copy(key = slug(m.key.ifBlank { m.title }, "milestone"), due = due.toString())
                }
            }.distinctBy { it.key }
            val metrics = g.metrics.filter { it.label.isNotBlank() }
                .map { it.copy(key = slug(it.key.ifBlank { it.label }, "metric").replace('-', '_')) }
                .distinctBy { it.key }
            goals += g.copy(
                key = key,
                title = g.title.trim().take(80),
                shortName = g.shortName.trim().ifBlank { g.title.trim() }.take(24),
                categoryKey = resolveCategory(g.categoryKey) ?: "",
                priority = g.priority.coerceIn(1, 3),
                deadline = deadline?.toString() ?: "",
                deadlineAssumed = assumed,
                metrics = metrics,
                milestones = milestones,
            )
        }
        if (goals.isEmpty()) issues += PlanIssue(IssueLevel.ERROR, "The plan didn't contain any goals.")
        val goalByKey = goals.associateBy { it.key }
        fun resolveGoal(key: String): String = slug(key).takeIf { it in goalByKey } ?: goals.firstOrNull { it.title.equals(key, true) }?.key ?: ""
        val milestoneKeys = goals.flatMap { g -> g.milestones.map { it.key } }.toSet()

        val taskKeys = HashSet<String>()
        fun cleanTask(t: DraftTask, kind: String): DraftTask? {
            if (t.title.isBlank()) return null
            var key = slug(t.key.ifBlank { t.title }, kind)
            while (key in taskKeys) key += "-2"
            taskKeys += key
            val goalKey = resolveGoal(t.goalKey)
            val slot = TimeSlot.entries.firstOrNull { it.name == t.timeSlot.uppercase() } ?: TimeSlot.FLEXIBLE
            val exact = t.exactTime.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it.padStart(5, '0')) }.getOrNull() }
            val idealMin = t.idealMinutes.coerceIn(0, MAX_MINUTES)
            var minMin = t.minimumMinutes.coerceIn(0, MAX_MINUTES)
            if (minMin > idealMin) { minMin = idealMin; fixed("\"${t.title}\": the minimum version was longer than the ideal one, so it was shortened.") }
            var minimum = t.minimum.trim()
            if (minimum.isBlank() && !t.isRest) {
                minimum = "Do the smallest useful part of this, about ${minOf(10, idealMin.coerceAtLeast(5))} minutes."
                minMin = minOf(10, idealMin.coerceAtLeast(5))
                warn("\"${t.title}\" had no minimum version; a generic one was added. You can edit it.")
            }
            val category = resolveCategory(t.categoryKey) ?: goalByKey[goalKey]?.categoryKey?.takeIf { it.isNotBlank() } ?: ""
            return t.copy(
                key = key,
                title = t.title.trim().take(80),
                description = t.description.trim().take(400),
                goalKey = goalKey,
                milestoneKey = t.milestoneKey.takeIf { slug(it) in milestoneKeys }?.let(::slug) ?: "",
                categoryKey = category,
                timeSlot = slot.name,
                exactTime = exact?.toString() ?: "",
                ideal = t.ideal.trim().ifBlank { t.title.trim() }.take(600),
                idealMinutes = idealMin,
                minimum = minimum.take(400),
                minimumMinutes = minMin,
                priority = t.priority.coerceIn(1, 3),
                energy = EnergyLevel.entries.firstOrNull { it.name == t.energy.uppercase() }?.name ?: EnergyLevel.MEDIUM.name,
            )
        }

        // Habits
        val habits = mutableListOf<DraftTask>()
        for (h in input.habits) {
            val days = parseDays(h.days)
            if (days.isEmpty()) { if (h.title.isNotBlank()) fixed("Habit \"${h.title}\" had no valid days and was left out."); continue }
            val t = cleanTask(h, "habit") ?: continue
            val dup = habits.firstOrNull { normalizeTitle(it.title) == normalizeTitle(t.title) && parseDays(it.days) == days }
            if (dup != null) { fixed("A duplicate of \"${t.title}\" was removed."); continue }
            if (habits.size == MAX_HABITS) { warn("Only the first $MAX_HABITS habits were kept."); break }
            habits += t.copy(days = DayOfWeek.entries.filter { it in days }.map(::dayCode), date = "")
        }

        // First week
        val weekEnd = startDate.plusDays(6)
        val week = mutableListOf<DraftTask>()
        for (w in input.firstWeek) {
            val date = parseDate(w.date)
            if (date == null || date.isBefore(startDate) || date.isAfter(weekEnd)) {
                if (w.title.isNotBlank()) fixed("\"${w.title}\" wasn't dated within the first week and was left out.")
                continue
            }
            val t = cleanTask(w, "task") ?: continue
            val sameDay = week.any { parseDate(it.date) == date && normalizeTitle(it.title) == normalizeTitle(t.title) }
            val habitThatDay = habits.any { normalizeTitle(it.title) == normalizeTitle(t.title) && date.dayOfWeek in parseDays(it.days) }
            if (sameDay || habitThatDay) { fixed("A duplicate of \"${t.title}\" on ${DateFmt.short(date)} was removed."); continue }
            if (week.size == MAX_WEEK_TASKS) { warn("Only the first $MAX_WEEK_TASKS first-week tasks were kept."); break }
            week += t.copy(date = date.toString(), days = emptyList())
        }

        // Dependencies: drop unknown and self references, break cycles, keep dated order.
        val allKeys = (habits + week).map { it.key }.toSet()
        fun cleanDeps(t: DraftTask) = t.copy(dependsOn = t.dependsOn.map(::slug).filter { it in allKeys && it != t.key }.distinct())
        val cleanedHabits = habits.map(::cleanDeps)
        var cleanedWeek = week.map(::cleanDeps)
        val depGraph = (cleanedHabits + cleanedWeek).associate { it.key to it.dependsOn.toMutableList() }
        breakCycles(depGraph) { from, to -> fixed("A circular dependency between tasks was removed ($from → $to).") }
        cleanedWeek = cleanedWeek.map { it.copy(dependsOn = depGraph[it.key].orEmpty()) }
        val finalHabits = cleanedHabits.map { it.copy(dependsOn = depGraph[it.key].orEmpty()) }
        val weekByKey = cleanedWeek.associateBy { it.key }.toMutableMap()
        repeat(cleanedWeek.size) {
            for (t in weekByKey.values.toList()) {
                val d = parseDate(t.date)!!
                val latestDep = t.dependsOn.mapNotNull { weekByKey[it]?.date?.let(::parseDate) }.maxOrNull()
                if (latestDep != null && latestDep.isAfter(d)) {
                    weekByKey[t.key] = t.copy(date = latestDep.toString())
                    fixed("\"${t.title}\" was moved to ${DateFmt.short(latestDep)} so it comes after what it depends on.")
                }
            }
        }
        cleanedWeek = cleanedWeek.map { weekByKey.getValue(it.key) }

        // Capacity: rebalance dated tasks into days with room, then report what still doesn't fit.
        val weekdayCap = (input.weekdayMinutes ?: fallbackWeekdayMinutes)?.coerceIn(0, 16 * 60)
        val weekendCap = (input.weekendMinutes ?: fallbackWeekendMinutes)?.coerceIn(0, 16 * 60)
        fun cap(d: LocalDate) = if (d.dayOfWeek.value >= 6) weekendCap ?: weekdayCap else weekdayCap ?: weekendCap
        val days = (0L..6L).map { startDate.plusDays(it) }
        fun habitLoad(d: LocalDate) = finalHabits.filter { d.dayOfWeek in parseDays(it.days) }.sumOf { it.idealMinutes }
        val placed = cleanedWeek.toMutableList()
        fun load(d: LocalDate) = habitLoad(d) + placed.filter { parseDate(it.date) == d }.sumOf { it.idealMinutes }
        for (d in days) {
            val c = cap(d) ?: continue
            if (load(d) <= c) continue
            val movable = placed.filter { parseDate(it.date) == d }.sortedByDescending { it.priority }
            for (t in movable) {
                if (load(d) <= c) break
                val earliest = maxOf(d.plusDays(1), t.dependsOn.mapNotNull { k -> placed.firstOrNull { it.key == k }?.date?.let(::parseDate) }.maxOrNull() ?: startDate)
                val dependents = placed.filter { t.key in it.dependsOn }.mapNotNull { parseDate(it.date) }.minOrNull()
                val target = days.firstOrNull { o ->
                    !o.isBefore(earliest) && (dependents == null || !o.isAfter(dependents)) &&
                        (cap(o)?.let { load(o) + t.idealMinutes <= it } ?: true)
                } ?: continue
                placed[placed.indexOfFirst { it.key == t.key }] = t.copy(date = target.toString())
                fixed("\"${t.title}\" was moved from ${DateFmt.short(d)} to ${DateFmt.short(target)} to fit your available time.")
            }
            if (load(d) > c) {
                warn("${DateFmt.short(d)} has about ${load(d)} minutes planned but you have about $c. Use minimum versions that day, or remove something.")
            }
        }
        val dailyLoad = days.associateWith(::load)
        val weeklyCap = days.sumOf { cap(it) ?: 0 }
        if (weekdayCap != null || weekendCap != null) {
            val total = dailyLoad.values.sum()
            if (total > weeklyCap && weeklyCap > 0) {
                val order = goals.sortedBy { it.priority }.joinToString(", ") { it.shortName }
                warn("Your goals need about ${total / 60}h ${total % 60}m in the first week but you have about ${weeklyCap / 60}h ${weeklyCap % 60}m. Not every goal can move every day; the suggested order of priority is: $order.")
            }
        }

        val outcomes = input.weeklyOutcomes.mapNotNull { o ->
            val g = resolveGoal(o.goalKey)
            if (o.title.isBlank() || g.isBlank()) null else o.copy(goalKey = g, milestoneKey = o.milestoneKey.takeIf { slug(it) in milestoneKeys }?.let(::slug) ?: "", title = o.title.trim().take(160))
        }

        if (finalHabits.isEmpty() && placed.isEmpty() && goals.isNotEmpty()) {
            issues += PlanIssue(IssueLevel.ERROR, "The plan didn't contain any tasks or habits.")
        }

        val draft = input.copy(
            summary = input.summary.trim().take(1200),
            assumptions = input.assumptions.map { it.trim() }.filter { it.isNotEmpty() }.take(12),
            warnings = input.warnings.filter { it.message.isNotBlank() }.take(10),
            weekdayMinutes = weekdayCap,
            weekendMinutes = weekendCap,
            categories = draftCats.values.filter { c -> (goals.map { it.categoryKey } + finalHabits.map { it.categoryKey } + placed.map { it.categoryKey }).contains(c.key) },
            goals = goals,
            habits = finalHabits,
            firstWeek = placed.sortedWith(compareBy({ it.date }, { TimeSlot.valueOf(it.timeSlot).ordinal }, { it.priority })),
            weeklyOutcomes = outcomes,
        )
        return ValidatedPlan(draft, startDate, issues, dailyLoad, weekdayCap, weekendCap)
    }

    /**
     * Validates a next-week plan against existing goals ([goalIds]); tasks are dated within
     * [weekStart]..+6. Reuses the same task rules as the full plan.
     */
    fun validateWeek(
        input: WeekPlan,
        weekStart: LocalDate,
        goals: Map<String, String>,
        existingCategories: List<CategoryEntity>,
        existingTitlesByDate: Set<Pair<String, LocalDate>>,
        weekdayMinutes: Int?,
        weekendMinutes: Int?,
        habitLoad: (LocalDate) -> Int,
    ): Pair<WeekPlan, List<PlanIssue>> {
        // Express the existing goals as draft goals so the shared rules can resolve references.
        val asDraft = PlanDraft(
            goals = goals.map { (id, title) -> DraftGoal(key = id, title = title) },
            firstWeek = input.tasks.map { t -> t.copy(goalKey = goals.keys.firstOrNull { it == t.goalKey } ?: t.goalKey) },
            weeklyOutcomes = input.weeklyOutcomes,
            weekdayMinutes = weekdayMinutes,
            weekendMinutes = weekendMinutes,
        )
        val v = validate(asDraft, weekStart, existingCategories)
        val issues = v.issues.filter { it.level != IssueLevel.ERROR }.toMutableList()
        val tasks = v.draft.firstWeek.filter { t ->
            val dup = (normalizeTitle(t.title) to parseDate(t.date)!!) in existingTitlesByDate.map { normalizeTitle(it.first) to it.second }
            if (dup) issues += PlanIssue(IssueLevel.FIXED, "\"${t.title}\" is already planned on ${DateFmt.short(parseDate(t.date)!!)}, so the copy was removed.")
            !dup
        }.map { t ->
            // Keys from the shared rules are slugs of goal ids; map back to the real ids.
            t.copy(goalKey = goals.keys.firstOrNull { slug(it) == t.goalKey || it == t.goalKey } ?: "")
        }
        for (d in (0L..6L).map { weekStart.plusDays(it) }) {
            val cap = (if (d.dayOfWeek.value >= 6) weekendMinutes ?: weekdayMinutes else weekdayMinutes ?: weekendMinutes) ?: continue
            val load = habitLoad(d) + tasks.filter { parseDate(it.date) == d }.sumOf { it.idealMinutes }
            if (load > cap) issues += PlanIssue(IssueLevel.WARNING, "${DateFmt.short(d)} would have about $load minutes planned against about $cap available.")
        }
        val outcomes = v.draft.weeklyOutcomes.map { o -> o.copy(goalKey = goals.keys.firstOrNull { slug(it) == o.goalKey || it == o.goalKey } ?: "") }
            .filter { it.goalKey.isNotBlank() }
        return input.copy(tasks = tasks, weeklyOutcomes = outcomes, summary = input.summary.take(800), notes = input.notes.take(6)) to issues
    }

    /** Removes back-edges from [graph] (task key → dependencies) so it is acyclic. */
    fun breakCycles(graph: Map<String, MutableList<String>>, onRemoved: (String, String) -> Unit = { _, _ -> }) {
        val state = HashMap<String, Int>() // 1 = visiting, 2 = done
        fun visit(n: String) {
            state[n] = 1
            val deps = graph[n] ?: return run { state[n] = 2 }
            val it = deps.iterator()
            while (it.hasNext()) {
                val d = it.next()
                when (state[d]) {
                    1 -> { it.remove(); onRemoved(n, d) }
                    null -> visit(d)
                }
            }
            state[n] = 2
        }
        graph.keys.forEach { if (state[it] == null) visit(it) }
    }
}
