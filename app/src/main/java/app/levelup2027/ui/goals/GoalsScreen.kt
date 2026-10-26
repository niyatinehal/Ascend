package app.levelup2027.ui.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.levelup2027.data.Cat
import app.levelup2027.data.Completion
import app.levelup2027.data.CompletionType
import app.levelup2027.data.Goal
import app.levelup2027.data.GoalIds
import app.levelup2027.data.GoalStatus
import app.levelup2027.data.WeeklyOutcome
import app.levelup2027.domain.Dates
import app.levelup2027.ui.Catalog
import app.levelup2027.ui.LocalCatalog
import app.levelup2027.ui.LocalNav
import app.levelup2027.data.MetricDef
import app.levelup2027.data.Metrics
import app.levelup2027.data.Milestone
import app.levelup2027.data.ProgressEntry
import app.levelup2027.data.Repository
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.DateFmt
import app.levelup2027.domain.Xp
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.components.Overline
import app.levelup2027.ui.components.Pill
import app.levelup2027.ui.components.ProgressLine
import app.levelup2027.ui.tasks.ChipChoice
import app.levelup2027.ui.theme.color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun goalColor(goal: Goal, catalog: Catalog): Color = catalog.category(
    goal.categoryId ?: when (goal.id) {
        GoalIds.CAREER -> Cat.CAREER
        GoalIds.FITNESS -> Cat.FITNESS
        GoalIds.TECH -> Cat.LEARNING
        GoalIds.FAMILY -> Cat.FAMILY
        else -> Cat.BUSINESS
    },
).color

@Composable
fun GoalsScreen(vm: AppViewModel) {
    val goals by vm.goals.collectAsState()
    val milestones by vm.milestones.collectAsState()
    val progress by vm.progress.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val completions by vm.completions.collectAsState()
    val today by vm.today.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var recording by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var editingDate by remember { mutableStateOf<Goal?>(null) }
    val outcomes by vm.outcomes.collectAsState()
    val nav = LocalNav.current
    val catalog = LocalCatalog.current

    // Latest value per metric, per goal (two goals may use the same metric key).
    val latestByGoal = remember(progress) { progress.groupBy { it.goalId }.mapValues { (_, l) -> Repository.latestValues(l) } }
    val taskGoal = remember(tasks) { tasks.orEmpty().associate { it.id to it.goalId } }
    val xp = Xp.total(completions)

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Goals", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Level ${Xp.level(xp)} · ${Xp.daysShowedUp(completions)} days showed up · ${completions.count { it.isDone }} tasks done",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = nav.openPlanBuilder, modifier = Modifier.weight(1f).heightIn(min = 44.dp)) {
                    Text(if (goals.isEmpty()) "Build my plan" else "Add goals with AI")
                }
                OutlinedButton(onClick = nav.openAdjust, enabled = goals.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text("Adjust my plan") }
            }
        }
        if (goals.isEmpty()) {
            item {
                Text("No goals yet. Describe what you want to work toward and AI will draft a plan for you to review, or add tasks yourself from the Tasks tab.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(goals.sortedBy { if (it.isActive) 0 else 1 }, key = { it.id }) { g ->
            val goalCompletions = completions.filter { taskGoal[it.taskId] == g.id }
            GoalCard(
                goal = g,
                milestones = milestones.filter { it.goalId == g.id },
                latest = latestByGoal[g.id].orEmpty(),
                entries = progress.filter { it.goalId == g.id },
                outcomes = outcomes.filter { it.goalId == g.id && it.weekStart == Dates.weekStart(today) },
                color = goalColor(g, catalog),
                onOutcome = { o, done -> vm.setOutcomeDone(o, done) },
                onStatus = { st -> vm.updateGoal(g.copy(status = st)) },
                linked = tasks.orEmpty().filter { it.goalId == g.id && it.enabled && !it.isOneTime },
                goalCompletions = goalCompletions,
                today = today,
                expanded = g.id in expanded,
                onToggle = { expanded = if (g.id in expanded) expanded - g.id else expanded + g.id },
                onRecord = { metric -> recording = g.id to metric },
                onMilestone = { m, done -> vm.setMilestoneDone(m, done) },
                onDeleteEntry = { vm.deleteProgress(it) },
                onEditDate = { editingDate = g },
            )
        }
        item {
            Text(
                "Targets are goals, not predictions. Projected numbers are never shown as actual results. Only values you record count as progress.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    recording?.let { (goalId, metric) ->
        val goal = goals.firstOrNull { it.id == goalId } ?: return@let
        RecordDialog(
            defs = Metrics.forGoal(goal),
            initial = metric,
            latest = latestByGoal[goalId].orEmpty(),
            onDismiss = { recording = null },
            onSave = { key, value, note ->
                vm.addProgress(goalId, key, value, note)
                recording = null
            },
        )
    }
    editingDate?.let { g -> TargetDateDialog(g, onDismiss = { editingDate = null }) { vm.updateGoal(g.copy(targetDate = it, deadlineAssumed = false)); editingDate = null } }
}

@Composable
private fun GoalCard(
    goal: Goal,
    milestones: List<Milestone>,
    latest: Map<String, ProgressEntry>,
    entries: List<ProgressEntry>,
    outcomes: List<WeeklyOutcome>,
    color: Color,
    onOutcome: (WeeklyOutcome, Boolean) -> Unit,
    onStatus: (String) -> Unit,
    linked: List<Task>,
    goalCompletions: List<Completion>,
    today: LocalDate,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRecord: (String?) -> Unit,
    onMilestone: (Milestone, Boolean) -> Unit,
    onDeleteEntry: (ProgressEntry) -> Unit,
    onEditDate: () -> Unit,
) {
    val next = milestones.firstOrNull { !it.done }
    val defs = Metrics.forGoal(goal)
    fun v(k: String) = latest[k]?.value
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        onClick = onToggle,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Overline(goal.displayName, color = color)
                    Text(goal.title, style = MaterialTheme.typography.titleMedium)
                }
                if (!goal.isActive) Pill(goal.status.replaceFirstChar { it.uppercase() }, MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse" else "Expand")
            }
            Spacer(Modifier.height(6.dp))
            Text("Target: ${goal.target}" + " · by ${DateFmt.medium(goal.targetDate)}" + if (goal.deadlineAssumed) " (assumed)" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Now: ${goal.currentProgress}", style = MaterialTheme.typography.bodyMedium)

            // Measurable progress bars, only where a real numeric target exists.
            when (goal.id) {
                GoalIds.FAMILY -> {
                    val target = v("fund_target") ?: 35000.0
                    val saved = v("fund_balance") ?: 0.0
                    Spacer(Modifier.height(6.dp))
                    ProgressLine((saved / target).toFloat(), Modifier.height(6.dp), color)
                    Text("${Metrics.format(Metrics.byKey("fund_balance"), saved)} of ${Metrics.format(Metrics.byKey("fund_target"), target)} · by ${DateFmt.medium(goal.targetDate)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GoalIds.BUSINESS -> {
                    val mrr = v("mrr") ?: 0.0
                    Spacer(Modifier.height(6.dp))
                    ProgressLine((mrr / 10000.0).toFloat(), Modifier.height(6.dp), color)
                    Text("Actual MRR ${Metrics.format(Metrics.byKey("mrr"), mrr)} of ₹10,000 target · ${v("paying_customers")?.toInt() ?: 0} paying · ${v("conversations")?.toInt() ?: 0} conversations",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GoalIds.FITNESS -> {
                    Spacer(Modifier.height(6.dp))
                    StrengthBar("Push-ups", v("pushups"), 50.0, color)
                    StrengthBar("Pull-ups", v("pullups"), 5.0, color)
                }
                GoalIds.CAREER -> {
                    Text(
                        "Applications ${v("applications")?.toInt() ?: 0} · Interviews ${v("interviews")?.toInt() ?: 0} · Offers ${v("offers")?.toInt() ?: 0}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GoalIds.TECH -> {
                    val sessions = goalCompletions.count { it.isDone }
                    Text(
                        "$sessions tech tasks done from your checklist · ${v("analysed_problems")?.toInt() ?: 0} problems analysed independently (recorded)",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    // Goals with their own metrics: a bar for each metric that has a numeric target.
                    defs.filter { it.target != null }.take(3).forEach { d ->
                        val now = v(d.key) ?: d.baseline
                        val base = d.baseline ?: 0.0
                        val fraction = when {
                            now == null -> 0f
                            d.higherIsBetter -> if (d.target!! == base) 1f else ((now - base) / (d.target - base)).toFloat()
                            else -> if (base == d.target!!) 1f else ((base - now) / (base - d.target)).toFloat()
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(d.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(96.dp), maxLines = 1)
                            ProgressLine(fraction, Modifier.weight(1f).height(6.dp), color)
                            Spacer(Modifier.width(8.dp))
                            Text((now?.let { Metrics.format(d, it) } ?: "not recorded") + " / " + Metrics.format(d, d.target!!),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val done = goalCompletions.count { it.isDone }
                    if (done > 0) Text("$done tasks done for this goal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (outcomes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Overline("This week")
                outcomes.forEach { o ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = o.done, onCheckedChange = { onOutcome(o, it) })
                        Text(o.title, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            next?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("Next · ${DateFmt.monthYear(it.dueDate)}", color)
                    Spacer(Modifier.width(8.dp))
                    Text(it.title, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }

            AnimatedVisibility(expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(goal.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (goal.id == GoalIds.FITNESS) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Track strength and consistency, not weight alone. Aim for slow change (well under 1 kg a week). If weight drops fast or you feel unwell, ease off and consider professional advice.",
                            style = MaterialTheme.typography.bodySmall, color = color,
                        )
                    }

                    if (goal.assumptions.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Assumed: ${goal.assumptions}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp)); Overline("Status")
                    ChipChoice(listOf(GoalStatus.ACTIVE, GoalStatus.PAUSED, GoalStatus.DONE, GoalStatus.DROPPED), goal.status, { it.replaceFirstChar { c -> c.uppercase() } }, onStatus)
                    Text("Paused or dropped goals keep their history; their tasks are lowered in priority but not deleted.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(Modifier.height(12.dp)); Overline("Record progress")
                    if (defs.isEmpty()) Text("This goal has no numeric measures; it's tracked by the tasks you complete.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    defs.forEach { d ->
                        val e = latest[d.key]
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    e?.let { Metrics.format(d, it.value) + (if (it.isEstimate) " (estimate)" else "") + " · " + DateFmt.short(it.date) } ?: "Not recorded yet",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onRecord(d.key) }) { Text("Update") }
                        }
                    }
                    OutlinedButton(onClick = onEditDate, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text("Target date: ${DateFmt.medium(goal.targetDate)}" + if (goal.deadlineAssumed) " (assumed · tap to set)" else "")
                    }

                    Spacer(Modifier.height(12.dp)); Overline("Timeline")
                    if (milestones.isEmpty()) {
                        Text("No dated milestones; progress is tracked by the values above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    milestones.forEach { m ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = m.done, onCheckedChange = { onMilestone(m, it) })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    DateFmt.monthYear(m.dueDate) + if (!m.done && m.dueDate.isBefore(today)) " · review at checkpoint" else "",
                                    style = MaterialTheme.typography.labelSmall, color = if (m.done) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text("Target: ${m.title}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp)); Overline("Linked recurring tasks")
                    val ideal = goalCompletions.count { it.completionType == CompletionType.IDEAL }
                    val min = goalCompletions.count { it.completionType == CompletionType.MINIMUM }
                    Text("${linked.size} tasks · $ideal ideal and $min minimum completions so far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    linked.sortedWith(compareBy<Task>({ it.timeSlot.ordinal }, { it.sortOrder })).forEach {
                        Text("• ${it.title} (${app.levelup2027.domain.ScheduleEngine.describeSchedule(it)}${if (it.timeSlot == TimeSlot.WORK) ", at work" else ""})", style = MaterialTheme.typography.bodySmall)
                    }

                    if (entries.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp)); Overline("History")
                        entries.take(8).forEach { e ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${DateFmt.short(e.date)} · ${Metrics.def(goal, e.metric)?.label ?: e.metric}: ${Metrics.format(Metrics.def(goal, e.metric), e.value)}" +
                                        (if (e.isEstimate) " (estimate)" else "") + (if (e.notes.isNotBlank()) " · ${e.notes}" else ""),
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { onDeleteEntry(e) }) {
                                    Icon(Icons.Outlined.Delete, "Delete entry", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (defs.isNotEmpty()) FilledTonalButton(onClick = { onRecord(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("Record progress") }
                }
            }
        }
    }
}

@Composable
private fun StrengthBar(label: String, value: Double?, target: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
        ProgressLine(((value ?: 0.0) / target).toFloat(), Modifier.weight(1f).height(6.dp), color)
        Spacer(Modifier.width(8.dp))
        Text(
            value?.let { "${it.toInt()}/${target.toInt()}" } ?: "not recorded",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordDialog(
    defs: List<MetricDef>,
    initial: String?,
    latest: Map<String, ProgressEntry>,
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Unit,
) {
    var key by remember { mutableStateOf(initial ?: defs.first().key) }
    var value by remember(key) { mutableStateOf(latest[key]?.value?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var note by remember { mutableStateOf("") }
    val def = defs.first { it.key == key }
    val parsed = value.replace(",", "").toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record progress") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipChoice(defs.map { it.key }, key, { k -> defs.first { it.key == k }.label }) { key = it }
                OutlinedTextField(
                    value, { value = it }, singleLine = true,
                    label = { Text(def.label + if (def.isCurrency) " (${def.unit.ifBlank { "₹" }})" else if (def.unit.isNotBlank()) " (${def.unit})" else "") },
                    supportingText = { Text("Enter the current total, recorded for today.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = value.isNotBlank() && (parsed == null || parsed < 0),
                )
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null && parsed >= 0, onClick = { onSave(key, parsed!!, note.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetDateDialog(goal: Goal, onDismiss: () -> Unit, onSave: (LocalDate) -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = goal.targetDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { st.selectedDateMillis?.let { onSave(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(st) }
}
