package app.levelup2027.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.levelup2027.ai.DraftGoal
import app.levelup2027.ai.DraftTask
import app.levelup2027.ai.IssueLevel
import app.levelup2027.ai.PlanValidator
import app.levelup2027.ai.ValidatedPlan
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.DateFmt
import app.levelup2027.domain.Recurrence
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.LocalCatalog
import app.levelup2027.ui.components.Overline
import app.levelup2027.ui.components.Pill
import app.levelup2027.ui.components.ProgressLine
import app.levelup2027.ui.components.SectionCard
import app.levelup2027.ui.tasks.ChipChoice

const val GOAL_PLACEHOLDER =
    "I want to get fit, change careers, learn Spanish, and start a small business. I work full-time and have about an hour free on weekdays."

/**
 * "Build my plan". [isOnboarding] adds the manual alternatives (start empty / example plan) and
 * hides the close button; afterwards it is opened from Goals to add more goals.
 */
@Composable
fun PlanBuilderScreen(
    vm: AppViewModel,
    builder: PlanBuilderViewModel,
    isOnboarding: Boolean,
    onClose: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val state by builder.state.collectAsState()
    var showConsent by remember { mutableStateOf(false) }
    var regenerate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    BackHandler(enabled = state.step == BuilderStep.QUESTIONS || state.step == BuilderStep.PREVIEW) {
        if (state.step == BuilderStep.PREVIEW && editing) editing = false else builder.backToDescribe()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (state.step) {
                        BuilderStep.PREVIEW -> "Your draft plan"
                        BuilderStep.DONE -> "You're set"
                        else -> "Build my plan"
                    },
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f),
                )
                if (state.step == BuilderStep.PREVIEW || state.step == BuilderStep.QUESTIONS) {
                    TextButton(onClick = { editing = false; builder.discard() }) { Text("Start over") }
                }
                if (!isOnboarding || state.step == BuilderStep.DONE) {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
                }
            }
            state.block?.let { b ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    AiBlockCard(
                        b,
                        onConsent = { builder.dismissBlock(); showConsent = true },
                        onSetup = { builder.dismissBlock(); onOpenAiSettings() },
                        onRetry = {
                            builder.dismissBlock()
                            if (state.intake == null) builder.submitDescription() else builder.generate()
                        },
                        onDismiss = builder::dismissBlock,
                    )
                }
            }
            when (state.step) {
                BuilderStep.DESCRIBE -> DescribeStep(vm, builder, state, isOnboarding, onClose)
                BuilderStep.QUESTIONS -> QuestionsStep(builder, state)
                BuilderStep.WORKING -> Working(state.working, builder::cancelWork)
                BuilderStep.PREVIEW -> state.plan?.let { PreviewStep(builder, it, editing, onEdit = { editing = !editing }, onRegenerate = { regenerate = true }) }
                BuilderStep.DONE -> DoneStep(state.acceptedSummary, onClose)
            }
        }
    }

    if (showConsent) {
        AiConsentDialog(vm, onDismiss = { showConsent = false }) {
            showConsent = false
            if (state.intake == null) builder.submitDescription() else builder.generate()
        }
    }
    if (regenerate) RegenerateDialog(onDismiss = { regenerate = false }) { fb -> regenerate = false; editing = false; builder.generate(fb) }
}

@Composable
private fun DescribeStep(vm: AppViewModel, builder: PlanBuilderViewModel, state: BuilderState, isOnboarding: Boolean, onClose: () -> Unit) {
    var manual by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("What do you want to work toward?", style = MaterialTheme.typography.titleLarge)
        Text(
            "Write it the way you'd say it: one goal or several, plus anything about your week. AI turns it into a plan you can review before anything is saved.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AreaPicker(vm, state.text, builder::setText)
        OutlinedTextField(
            value = state.text,
            onValueChange = builder::setText,
            placeholder = { Text(GOAL_PLACEHOLDER) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            trailingIcon = { MicButton { spoken -> builder.setText((state.text + " " + spoken).trim()) } },
        )
        Button(
            onClick = builder::submitDescription,
            enabled = state.text.trim().length >= 3,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Build my plan") }
        if (isOnboarding) {
            TextButton(onClick = { manual = true }, modifier = Modifier.fillMaxWidth()) { Text("Set up without AI instead") }
        }
    }
    if (manual) {
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text("Set up without AI") },
            text = { Text("Start with an empty plan and add tasks yourself, or load the example \"Level Up 2027\" plan and edit it. You can use AI later from the Goals tab.") },
            confirmButton = {
                TextButton(onClick = {
                    manual = false
                    vm.updateSettings { it.copy(onboardingDone = true, planStartDate = it.planStartDate ?: java.time.LocalDate.now()) }
                    onClose()
                }) { Text("Start empty") }
            },
            dismissButton = {
                TextButton(onClick = { manual = false; vm.loadExamplePlan(); onClose() }) { Text("Load example plan") }
            },
        )
    }
}

@Composable
private fun QuestionsStep(builder: PlanBuilderViewModel, state: BuilderState) {
    val questions = state.intake?.questions.orEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("A few quick questions", style = MaterialTheme.typography.titleLarge)
            Text("Answer what you can; skip anything. The plan will mark what it had to assume.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(questions, key = { it.id }) { q ->
            SectionCard {
                Text(q.question, style = MaterialTheme.typography.titleSmall)
                if (q.why.isNotBlank()) Text(q.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    state.answers[q.id].orEmpty(), { builder.setAnswer(q.id, it) },
                    placeholder = { if (q.placeholder.isNotBlank()) Text(q.placeholder) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { MicButton { s -> builder.setAnswer(q.id, (state.answers[q.id].orEmpty() + " " + s).trim()) } },
                )
            }
        }
        item {
            Button(onClick = { builder.generate() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Create my plan") }
            TextButton(onClick = { questions.forEach { builder.setAnswer(it.id, "") }; builder.generate() }, modifier = Modifier.fillMaxWidth()) {
                Text("Skip questions")
            }
        }
    }
}

@Composable
private fun Working(text: String, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun DoneStep(summary: String, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your plan is ready", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("Tip: add the Ascend widget to your home screen so your next step is always visible (long-press the home screen → Widgets → Ascend).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("See today") }
    }
}

@Composable
private fun PreviewStep(builder: PlanBuilderViewModel, plan: ValidatedPlan, editing: Boolean, onEdit: () -> Unit, onRegenerate: () -> Unit) {
    val d = plan.draft
    var editTask by remember { mutableStateOf<DraftTask?>(null) }
    var editGoal by remember { mutableStateOf<DraftGoal?>(null) }
    var editCapacity by remember { mutableStateOf(false) }
    val catalog = LocalCatalog.current
    val draftCats = d.categories.associateBy { it.key }
    fun catName(key: String) = draftCats[key]?.name ?: catalog.category(key).shortName
    val goalName = d.goals.associate { it.key to it.shortName }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard {
                    Overline("In short")
                    Text(d.summary.ifBlank { "Here's a plan based on what you told me." }, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Starts ${DateFmt.medium(plan.startDate)}. Nothing is added to your schedule until you accept.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val important = d.warnings.map { it.message } + plan.issues.filter { it.level == IssueLevel.WARNING }.map { it.message }
            if (important.isNotEmpty()) {
                item {
                    SectionCard {
                        Overline("Worth knowing", color = MaterialTheme.colorScheme.primary)
                        important.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { TimeCard(plan, editing) { editCapacity = true } }
            item { Overline("Goals", Modifier.padding(top = 6.dp)) }
            items(d.goals, key = { "g-" + it.key }) { g ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.title, style = MaterialTheme.typography.titleSmall)
                            if (g.outcome.isNotBlank()) Text(g.outcome, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (editing) {
                            IconButton(onClick = { editGoal = g }) { Icon(Icons.Outlined.Edit, "Edit ${g.title}") }
                            IconButton(onClick = { builder.removeGoal(g.key) }) { Icon(Icons.Outlined.DeleteOutline, "Remove ${g.title}") }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("Priority ${g.priority}", MaterialTheme.colorScheme.onSurfaceVariant)
                        val deadline = PlanValidator.parseDate(g.deadline)
                        Pill(
                            if (deadline == null) "No deadline" else "By ${DateFmt.medium(deadline)}" + if (g.deadlineAssumed) " (assumed)" else "",
                            if (g.deadlineAssumed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (g.baseline.isNotBlank() || g.target.isNotBlank()) {
                        Text("${g.baseline.ifBlank { "Starting point unknown" }} → ${g.target.ifBlank { "target to be set" }}", style = MaterialTheme.typography.bodySmall)
                    }
                    g.milestones.forEach { m ->
                        Text("◦ ${m.title} · ${PlanValidator.parseDate(m.due)?.let(DateFmt::short) ?: m.due}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Overline("Weekly routine", Modifier.padding(top = 6.dp)) }
            if (d.habits.isEmpty()) item { Text("No recurring habits.", style = MaterialTheme.typography.bodySmall) }
            items(d.habits, key = { "h-" + it.key }) { h ->
                DraftTaskRow(h, Recurrence(PlanValidator.parseDays(h.days)).describe(), catName(h.categoryKey), goalName[h.goalKey], editing,
                    onEdit = { editTask = h }, onRemove = { builder.removeTask(h.key) })
            }
            item { Overline("First 7 days", Modifier.padding(top = 6.dp)) }
            val byDate = d.firstWeek.groupBy { it.date }
            plan.dailyLoad.keys.sorted().forEach { date ->
                val list = byDate[date.toString()].orEmpty()
                item(key = "day-$date") {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(DateFmt.widget(date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        val cap = plan.capacityFor(date)
                        Text("${plan.dailyLoad[date]} min planned" + (cap?.let { " / $it" } ?: ""), style = MaterialTheme.typography.labelMedium,
                            color = if (cap != null && plan.dailyLoad.getValue(date) > cap) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (list.isEmpty()) Text("Only your routine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(list, key = { "w-" + it.key }) { t ->
                    DraftTaskRow(t, t.timeSlot.let { s -> TimeSlot.valueOf(s).label } + if (t.exactTime.isNotBlank()) " · ${t.exactTime}" else "",
                        catName(t.categoryKey), goalName[t.goalKey], editing, onEdit = { editTask = t }, onRemove = { builder.removeTask(t.key) })
                }
            }
            if (d.weeklyOutcomes.isNotEmpty()) {
                item {
                    SectionCard {
                        Overline("This week's outcomes")
                        d.weeklyOutcomes.forEach { Text("• ${goalName[it.goalKey] ?: ""}: ${it.title}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (d.assumptions.isNotEmpty()) {
                item {
                    SectionCard {
                        Overline("Assumptions (edit anything that's wrong)")
                        d.assumptions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            val fixes = plan.issues.filter { it.level == IssueLevel.FIXED }
            if (fixes.isNotEmpty()) {
                item {
                    SectionCard {
                        Overline("Adjusted by the app")
                        fixes.forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRegenerate, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Regenerate") }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (editing) "Done editing" else "Edit Plan") }
            Button(onClick = builder::accept, enabled = d.goals.isNotEmpty(), modifier = Modifier.weight(1.2f).heightIn(min = 48.dp)) { Text("Accept Plan") }
        }
    }

    editTask?.let { t -> DraftTaskDialog(t, onDismiss = { editTask = null }) { builder.updateTask(it); editTask = null } }
    editGoal?.let { g -> DraftGoalDialog(g, onDismiss = { editGoal = null }) { builder.updateGoal(it); editGoal = null } }
    if (editCapacity) CapacityDialog(plan.weekdayMinutes, plan.weekendMinutes, onDismiss = { editCapacity = false }) { a, b -> builder.setCapacity(a, b); editCapacity = false }
}

@Composable
private fun TimeCard(plan: ValidatedPlan, editing: Boolean, onEditCapacity: () -> Unit) {
    val days = plan.dailyLoad.entries.sortedBy { it.key }
    val avg = if (days.isEmpty()) 0 else plan.weeklyMinutes / days.size
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("Time commitment")
                Text("About ${avg} min a day · ${plan.weeklyMinutes / 60}h ${plan.weeklyMinutes % 60}m in the first week", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (plan.weekdayMinutes == null && plan.weekendMinutes == null) "You didn't say how much time you have, so there's no daily limit yet."
                    else "Your time: ${plan.weekdayMinutes ?: "?"} min on weekdays, ${plan.weekendMinutes ?: "?"} min at weekends.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (editing) TextButton(onClick = onEditCapacity) { Text("Change") }
        }
        Spacer(Modifier.height(6.dp))
        days.forEach { (date, minutes) ->
            val cap = plan.capacityFor(date)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(DateFmt.widget(date).take(3), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
                ProgressLine(
                    if (cap != null && cap > 0) minutes.toFloat() / cap else (minutes / 120f),
                    Modifier.weight(1f).height(6.dp),
                    color = if (cap != null && minutes > cap) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(" $minutes", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
            }
        }
    }
}

@Composable
private fun DraftTaskRow(t: DraftTask, schedule: String, category: String, goal: String?, editing: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.material3.Card(
        onClick = { open = !open },
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = if (open) 3 else 1)
                    Text(listOfNotNull(schedule, "${t.idealMinutes}/${t.minimumMinutes} min", goal ?: category).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (editing) {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit ${t.title}") }
                    IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, "Remove ${t.title}") }
                }
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) "Show less" else "Show more",
                    Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) {
                Text("IDEAL · ${t.idealMinutes} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(t.ideal, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text(if (t.isRest) "MINIMUM · rest is fine" else "MINIMUM · ${t.minimumMinutes} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(t.minimum, style = MaterialTheme.typography.bodySmall)
                Text("$category · priority ${t.priority}" + if (t.dependsOn.isNotEmpty()) " · after: ${t.dependsOn.joinToString()}" else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun RegenerateDialog(onDismiss: () -> Unit, onGo: (String) -> Unit) {
    var fb by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Regenerate the plan") },
        text = {
            Column {
                Text("Optional: say what to change, e.g. \"lighter weekdays\" or \"no morning tasks\".", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(fb, { fb = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), trailingIcon = { MicButton { s -> fb = (fb + " " + s).trim() } })
            }
        },
        confirmButton = { TextButton(onClick = { onGo(fb) }) { Text("Regenerate") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DraftTaskDialog(t: DraftTask, onDismiss: () -> Unit, onSave: (DraftTask) -> Unit) {
    var title by remember { mutableStateOf(t.title) }
    var slot by remember { mutableStateOf(TimeSlot.valueOf(t.timeSlot)) }
    var ideal by remember { mutableStateOf(t.ideal) }
    var idealMin by remember { mutableStateOf(t.idealMinutes.toString()) }
    var minimum by remember { mutableStateOf(t.minimum) }
    var minMin by remember { mutableStateOf(t.minimumMinutes.toString()) }
    var priority by remember { mutableStateOf(t.priority) }
    var days by remember { mutableStateOf(PlanValidator.parseDays(t.days)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit task") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth()) }
                item { Overline("Time slot"); ChipChoice(TimeSlot.entries, slot, { it.shortLabel }) { slot = it } }
                if (t.days.isNotEmpty()) item {
                    Overline("Days")
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        java.time.DayOfWeek.entries.forEach { d ->
                            androidx.compose.material3.FilterChip(
                                selected = d in days,
                                onClick = { days = if (d in days) days - d else days + d },
                                label = { Text(app.levelup2027.domain.shortDay(d).take(2)) },
                            )
                        }
                    }
                }
                item { OutlinedTextField(ideal, { ideal = it }, label = { Text("Ideal version") }, modifier = Modifier.fillMaxWidth()) }
                item { MinutesField(idealMin, "Ideal minutes") { idealMin = it } }
                item { OutlinedTextField(minimum, { minimum = it }, label = { Text("Minimum version") }, modifier = Modifier.fillMaxWidth()) }
                item { MinutesField(minMin, "Minimum minutes") { minMin = it } }
                item { Overline("Priority"); ChipChoice(listOf(1, 2, 3), priority, { listOf("High", "Normal", "Low")[it - 1] }) { priority = it } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(t.copy(title = title, timeSlot = slot.name, ideal = ideal, idealMinutes = idealMin.toIntOrNull() ?: t.idealMinutes,
                    minimum = minimum, minimumMinutes = minMin.toIntOrNull() ?: t.minimumMinutes, priority = priority,
                    days = if (t.days.isNotEmpty() && days.isNotEmpty()) days.map(PlanValidator::dayCode) else t.days))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DraftGoalDialog(g: DraftGoal, onDismiss: () -> Unit, onSave: (DraftGoal) -> Unit) {
    var title by remember { mutableStateOf(g.title) }
    var target by remember { mutableStateOf(g.target) }
    var baseline by remember { mutableStateOf(g.baseline) }
    var deadline by remember { mutableStateOf(g.deadline) }
    var priority by remember { mutableStateOf(g.priority) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Goal") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(baseline, { baseline = it }, label = { Text("Where you are now") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(target, { target = it }, label = { Text("Target") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(deadline, { deadline = it }, label = { Text("Deadline (YYYY-MM-DD, or empty)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = deadline.isNotBlank() && PlanValidator.parseDate(deadline) == null)
                Overline("Priority")
                ChipChoice(listOf(1, 2, 3), priority, { listOf("High", "Normal", "Low")[it - 1] }) { priority = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val changedDeadline = deadline != g.deadline
                onSave(g.copy(title = title, baseline = baseline, target = target, deadline = deadline, priority = priority,
                    deadlineAssumed = if (changedDeadline) false else g.deadlineAssumed))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CapacityDialog(weekday: Int?, weekend: Int?, onDismiss: () -> Unit, onSave: (Int?, Int?) -> Unit) {
    var a by remember { mutableStateOf(weekday?.toString() ?: "") }
    var b by remember { mutableStateOf(weekend?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily time budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Roughly how many minutes a day can go to your goals? Leave empty for no limit.", style = MaterialTheme.typography.bodySmall)
                MinutesField(a, "Weekdays (minutes)") { a = it }
                MinutesField(b, "Weekends (minutes)") { b = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(a.toIntOrNull(), b.toIntOrNull()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MinutesField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter(Char::isDigit).take(3)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}
