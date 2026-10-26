package app.levelup2027.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.levelup2027.data.CompletionType
import app.levelup2027.domain.DateFmt
import app.levelup2027.domain.ScheduleEngine
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.LocalNav
import app.levelup2027.ui.components.CategoryIcon
import app.levelup2027.ui.components.CompletionBadge
import app.levelup2027.ui.components.Overline
import app.levelup2027.ui.components.Pill
import app.levelup2027.ui.components.SkipDialog
import app.levelup2027.ui.components.VersionBox
import app.levelup2027.ui.LocalCatalog
import app.levelup2027.ui.components.RescheduleDialog
import app.levelup2027.ui.theme.color
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailSheet(vm: AppViewModel, taskId: String, requestedDate: LocalDate?, onDismiss: () -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val completions by vm.completions.collectAsState()
    val today by vm.today.collectAsState()
    val nav = LocalNav.current
    val task = tasks?.firstOrNull { it.id == taskId }
    LaunchedEffect(tasks, task) {
        if (tasks != null && task == null) { nav.showMessage("That task no longer exists."); onDismiss() }
    }
    task ?: return

    val date = requestedDate
        ?: task.scheduledDate
        ?: (if (ScheduleEngine.isScheduledOn(task, today)) today else ScheduleEngine.nextOccurrence(task, today))
        ?: today
    val completion = completions.firstOrNull { it.taskId == task.id && it.scheduledDate == date }
    val scheduledThatDay = ScheduleEngine.isScheduledOn(task, date)
    val canAct = scheduledThatDay && !date.isAfter(today)
    var note by remember(task.id, date) { mutableStateOf(completion?.notes ?: "") }
    var skipping by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    val catalog = LocalCatalog.current
    val overrides by vm.overrides.collectAsState()
    val preferMin = overrides.any { it.taskId == task.id && it.date == date && it.preferMinimum }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(task.category, 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (date == today) "Today · ${task.timeSlot.label}" else "${DateFmt.medium(date)} · ${task.timeSlot.label}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onDismiss(); nav.editTask(task) }) {
                    Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(4.dp)); Text("Edit")
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val cat = catalog.category(task.category)
                Pill(cat.name, cat.color)
                if (task.goalId != null) Pill("Goal: ${catalog.goalName(task.goalId)}", MaterialTheme.colorScheme.onSurfaceVariant)
                Pill(listOf("High", "Normal", "Low")[task.priority.coerceIn(1, 3) - 1] + " priority", MaterialTheme.colorScheme.onSurfaceVariant)
                task.exactTime?.let { Pill("At $it", MaterialTheme.colorScheme.onSurfaceVariant) }
                Pill(ScheduleEngine.describeSchedule(task), MaterialTheme.colorScheme.onSurfaceVariant)
                Pill(task.energyLevel.label, MaterialTheme.colorScheme.onSurfaceVariant)
                if (!task.enabled) Pill("Disabled", MaterialTheme.colorScheme.error)
            }
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(task.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            VersionBox("Ideal version", task.idealDescription, task.estimatedIdealMinutes, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            VersionBox("Minimum version", task.minimumDescription, task.estimatedMinimumMinutes, MaterialTheme.colorScheme.secondary)

            if (task.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp)); Overline("Notes"); Text(task.notes, style = MaterialTheme.typography.bodyMedium)
            }
            if (task.resources.isNotBlank()) {
                Spacer(Modifier.height(12.dp)); Overline("Learning resources"); Text(task.resources, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))
            if (completion != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompletionBadge(completion.completionType, completion.skipReason)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.undo(task.id, date) }) { Text("Undo") }
                }
                if (completion.notes.isNotBlank()) Text(completion.notes, style = MaterialTheme.typography.bodySmall)
            } else if (canAct) {
                OutlinedTextField(note, { note = it }, label = { Text("Note for this completion (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.complete(task, date, CompletionType.IDEAL, notes = note.trim()); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Complete Ideal") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.complete(task, date, CompletionType.MINIMUM, notes = note.trim()); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                ) { Text("Complete Minimum") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { skipping = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Skip Today") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { moving = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Reschedule") }
                TextButton(onClick = { vm.setPreferMinimum(task.id, date, !preferMin) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (preferMin) "Planned as minimum today · undo" else "Plan the minimum version for this day")
                }
            } else {
                Text(
                    when {
                        !task.enabled -> "This task is disabled. Enable it in Edit to schedule it again."
                        date.isAfter(today) -> "Scheduled for ${DateFmt.medium(date)}. You can complete it on the day."
                        else -> "Not scheduled on ${DateFmt.medium(date)}."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.dependencyIds.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Do after: " + task.dependencyIds.joinToString { id -> tasks?.firstOrNull { it.id == id }?.title ?: "a removed task" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task.originTaskId != null && task.originDate != null) {
                Spacer(Modifier.height(12.dp))
                Text("Moved here from ${DateFmt.medium(task.originDate)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            task.supersedesTaskId?.let { base ->
                Spacer(Modifier.height(12.dp))
                Text(
                    "This dated task replaces the recurring \"${tasks?.firstOrNull { it.id == base }?.title ?: base}\" on ${DateFmt.short(task.scheduledDate!!)}, so it isn't counted twice.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (moving) {
        RescheduleDialog(task.title, date, today, onDismiss = { moving = false }) { to ->
            vm.reschedule(task, date, to) { r -> nav.showMessage(r.fold({ "Moved to ${DateFmt.short(to)}" }, { "Couldn't move it: ${it.message}" })) }
            moving = false
            onDismiss()
        }
    }

    if (skipping) {
        SkipDialog(task.title, !task.isOneTime, onDismiss = { skipping = false }) { reason, n ->
            vm.complete(task, date, CompletionType.SKIPPED, reason, n.ifBlank { note.trim() })
            skipping = false
            onDismiss()
        }
    }
}
