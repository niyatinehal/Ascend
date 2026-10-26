package app.levelup2027.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.levelup2027.data.Category
import app.levelup2027.data.Completion
import app.levelup2027.data.CompletionType
import app.levelup2027.data.GoalIds
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.DateFmt
import app.levelup2027.domain.Recurrence
import app.levelup2027.domain.ScheduleEngine
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.LocalNav
import app.levelup2027.ui.components.CategoryIcon
import app.levelup2027.ui.components.CompletionBadge
import app.levelup2027.ui.components.Overline
import app.levelup2027.ui.components.Pill
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class StateFilter(val label: String) { ANY("Any state"), OPEN("Open"), DONE("Completed"), SKIPPED("Skipped"), DISABLED("Disabled") }

@Composable
fun <T> DropdownChip(label: String, active: Boolean, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { open = true },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) },
            modifier = Modifier.heightIn(min = 40.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTasksScreen(vm: AppViewModel, weekPlanRequested: Boolean, onWeekPlanShown: () -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val completions by vm.completions.collectAsState()
    val today by vm.today.collectAsState()
    val nav = LocalNav.current

    var weekView by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(weekPlanRequested) { if (weekPlanRequested) { weekView = true; onWeekPlanShown() } }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var slot by rememberSaveable { mutableStateOf<TimeSlot?>(null) }
    var goal by rememberSaveable { mutableStateOf<String?>(null) }
    var state by rememberSaveable { mutableStateOf(StateFilter.ANY) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    val all = tasks ?: emptyList()
    val byKey = remember(completions) { completions.associateBy { it.taskId to it.scheduledDate } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text("All tasks", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !weekView, onClick = { weekView = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("List") }
                    SegmentedButton(selected = weekView, onClick = { weekView = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Weekly plan") }
                }
            }
            if (weekView) {
                weekPlan(all, today) { nav.editTask(it) }
            } else {
                item {
                    OutlinedTextField(
                        query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Search by title") },
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            DropdownChip(date?.let { if (it == today) "Today" else DateFmt.short(it) } ?: "Any date", date != null,
                                listOf("Any date" to 0, "Today" to 1, "Pick a date…" to 2)) {
                                when (it) { 0 -> date = null; 1 -> date = today; else -> pickingDate = true }
                            }
                        }
                        item {
                            DropdownChip(category?.shortLabel ?: "Category", category != null,
                                listOf<Pair<String, Category?>>("All categories" to null) + Category.entries.map { it.label to it }) { category = it }
                        }
                        item {
                            DropdownChip(slot?.shortLabel ?: "Time", slot != null,
                                listOf<Pair<String, TimeSlot?>>("Any time" to null) + TimeSlot.entries.map { it.label to it }) { slot = it }
                        }
                        item {
                            DropdownChip(goal?.let { GoalIds.shortName(it) } ?: "Goal", goal != null,
                                listOf<Pair<String, String?>>("All goals" to null) + GoalIds.ALL.map { GoalIds.shortName(it) to it }) { goal = it }
                        }
                        item {
                            DropdownChip(if (state == StateFilter.ANY) "State" else state.label, state != StateFilter.ANY,
                                StateFilter.entries.map { it.label to it }) { state = it }
                        }
                    }
                }

                val base: List<Pair<Task, LocalDate?>> = if (date != null) {
                    ScheduleEngine.tasksFor(date!!, all).map { it to date }
                } else {
                    all.map { t -> t to (t.scheduledDate ?: today.takeIf { ScheduleEngine.isScheduledOn(t, today) }) }
                }
                val rows = base.filter { (t, d) ->
                    val c = d?.let { byKey[t.id to it] }
                    (query.isBlank() || t.title.contains(query.trim(), ignoreCase = true)) &&
                        (category == null || t.category == category) &&
                        (slot == null || t.timeSlot == slot) &&
                        (goal == null || t.goalId == goal) &&
                        when (state) {
                            StateFilter.ANY -> true
                            StateFilter.OPEN -> t.enabled && d != null && c == null
                            StateFilter.DONE -> c?.isDone == true
                            StateFilter.SKIPPED -> c?.completionType == CompletionType.SKIPPED
                            StateFilter.DISABLED -> !t.enabled
                        }
                }.sortedWith(compareBy({ it.first.isOneTime }, { it.first.scheduledDate }, { it.first.timeSlot.ordinal }, { it.first.sortOrder }))

                item {
                    Text(
                        "${rows.size} task${if (rows.size == 1) "" else "s"}" + (date?.let { " scheduled on ${DateFmt.medium(it)}" } ?: "") +
                            if (date == null && state != StateFilter.ANY && state != StateFilter.DISABLED) " · recurring tasks use today's state" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (rows.isEmpty()) {
                    item { Text("No tasks match these filters.", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                var lastOneTime: Boolean? = null
                rows.forEach { (t, d) ->
                    if (date == null && t.isOneTime != lastOneTime) {
                        val header = if (t.isOneTime) "One-time & curriculum" else "Recurring"
                        item(key = "hdr-$header") { Overline(header, Modifier.padding(top = 10.dp, bottom = 2.dp)) }
                        lastOneTime = t.isOneTime
                    }
                    item(key = t.id) { TaskRow(t, d, d?.let { byKey[t.id to it] }) { nav.openTask(t.id, d) } }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.editTask(null) },
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text("New task") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (pickingDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (date ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text("Show") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(st) }
    }
}

@Composable
private fun TaskRow(t: Task, date: LocalDate?, c: Completion?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(t.category, 30)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (t.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${t.timeSlot.shortLabel} · ${ScheduleEngine.describeSchedule(t)} · ${GoalIds.shortName(t.goalId)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            when {
                !t.enabled -> Pill("Off", MaterialTheme.colorScheme.onSurfaceVariant)
                c != null -> CompletionBadge(c.completionType, null)
                date != null && t.isOneTime && date.isBefore(LocalDate.now()) -> Pill("Overdue", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.weekPlan(all: List<Task>, today: LocalDate, onEdit: (Task) -> Unit) {
    item {
        Text(
            "Your recurring week. Tap a task to change its days, time slot, or versions. Dated curriculum tasks replace these on their day.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    DayOfWeek.entries.forEach { dow ->
        val dayTasks = all.filter { t ->
            t.enabled && !t.isOneTime && Recurrence.parse(t.recurrenceRule)?.matches(today.with(java.time.temporal.TemporalAdjusters.nextOrSame(dow))) == true
        }.sortedWith(compareBy({ it.timeSlot.ordinal }, { it.sortOrder }))
        val ideal = dayTasks.filter { it.activeFrom == null || !it.activeFrom.isAfter(today) }.sumOf { it.estimatedIdealMinutes }
        val min = dayTasks.filter { it.activeFrom == null || !it.activeFrom.isAfter(today) }.sumOf { it.estimatedMinimumMinutes }
        item(key = "dow-$dow") {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dow.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Ideal ~${fmtMin(ideal)} · Min ~${fmtMin(min)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        dayTasks.forEach { t ->
            item(key = "dow-$dow-${t.id}") {
                Row(
                    Modifier.fillMaxWidth().clickable { onEdit(t) }.heightIn(min = 44.dp).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryIcon(t.category, 26)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            t.timeSlot.label + (t.activeFrom?.takeIf { it.isAfter(today) }?.let { " · from ${DateFmt.monthYear(it)}" } ?: ""),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("${t.estimatedIdealMinutes}/${t.estimatedMinimumMinutes}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun fmtMin(m: Int) = if (m >= 60) "${m / 60}h${if (m % 60 > 0) " ${m % 60}m" else ""}" else "${m}m"
