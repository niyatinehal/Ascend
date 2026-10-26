package app.levelup2027.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.levelup2027.data.Category
import app.levelup2027.data.EnergyLevel
import app.levelup2027.data.GoalIds
import app.levelup2027.data.Task
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.DateFmt
import app.levelup2027.domain.Recurrence
import app.levelup2027.domain.shortDay
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.LocalNav
import app.levelup2027.ui.components.Overline
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Composable
fun <T> ChipChoice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }, modifier = Modifier.heightIn(min = 40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorDialog(vm: AppViewModel, original: Task?, onDismiss: () -> Unit) {
    val nav = LocalNav.current
    val today by vm.today.collectAsState()
    val base = original

    var title by rememberSaveable { mutableStateOf(base?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(base?.description ?: "") }
    var category by rememberSaveable { mutableStateOf(base?.category ?: Category.TECHNICAL) }
    var slot by rememberSaveable { mutableStateOf(base?.timeSlot ?: TimeSlot.EVENING) }
    var goalId by rememberSaveable { mutableStateOf(base?.goalId) }
    var energy by rememberSaveable { mutableStateOf(base?.energyLevel ?: EnergyLevel.MEDIUM) }
    var oneTime by rememberSaveable { mutableStateOf(base?.isOneTime ?: false) }
    var date by rememberSaveable { mutableStateOf(base?.scheduledDate ?: today) }
    var days by rememberSaveable {
        mutableStateOf(Recurrence.parse(base?.recurrenceRule)?.days ?: setOf(today.dayOfWeek))
    }
    var ideal by rememberSaveable { mutableStateOf(base?.idealDescription ?: "") }
    var minimum by rememberSaveable { mutableStateOf(base?.minimumDescription ?: "") }
    var idealMin by rememberSaveable { mutableStateOf((base?.estimatedIdealMinutes ?: 30).toString()) }
    var minMin by rememberSaveable { mutableStateOf((base?.estimatedMinimumMinutes ?: 10).toString()) }
    var notes by rememberSaveable { mutableStateOf(base?.notes ?: "") }
    var resources by rememberSaveable { mutableStateOf(base?.resources ?: "") }
    var workout by rememberSaveable { mutableStateOf(base?.countsAsWorkout ?: false) }
    var enabled by rememberSaveable { mutableStateOf(base?.enabled ?: true) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val im = idealMin.toIntOrNull()
        val mm = minMin.toIntOrNull()
        error = when {
            title.isBlank() -> "Give the task a title."
            ideal.isBlank() -> "Describe the ideal version."
            minimum.isBlank() -> "Describe the minimum version, so low-energy days still count."
            im == null || im < 0 || im > 600 -> "Ideal minutes must be 0–600."
            mm == null || mm < 0 || mm > 600 -> "Minimum minutes must be 0–600."
            !oneTime && days.isEmpty() -> "Pick at least one day."
            else -> null
        }
        if (error != null) return
        val t = (base ?: Task(
            id = "user-" + UUID.randomUUID().toString().take(8), title = "", description = "", category = category,
            timeSlot = slot, idealDescription = "", minimumDescription = "", estimatedIdealMinutes = 0, estimatedMinimumMinutes = 0,
        )).copy(
            title = title.trim(), description = description.trim(), category = category, timeSlot = slot, goalId = goalId,
            energyLevel = energy, idealDescription = ideal.trim(), minimumDescription = minimum.trim(),
            estimatedIdealMinutes = im!!, estimatedMinimumMinutes = mm!!, notes = notes.trim(), resources = resources.trim(),
            countsAsWorkout = workout, enabled = enabled,
            scheduledDate = if (oneTime) date else null,
            recurrenceRule = if (oneTime) null else Recurrence(days).encode(),
            // A one-time task only replaces a recurring one if it was created that way (curriculum).
            supersedesTaskId = if (oneTime) base?.supersedesTaskId else null,
        )
        vm.saveTask(t)
        nav.showMessage(if (base == null) "Task added" else "Task saved")
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close without saving") }
                    Text(if (base == null) "New task" else "Edit task", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = ::save, modifier = Modifier.padding(end = 8.dp)) { Text("Save") }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text("Short description") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)

                    Overline("Schedule")
                    ChipChoice(listOf(false, true), oneTime, { if (it) "One-time" else "Repeats weekly" }) { oneTime = it }
                    if (oneTime) {
                        OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Date: ${DateFmt.medium(date)}") }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DayOfWeek.entries.forEach { d ->
                                FilterChip(
                                    selected = d in days,
                                    onClick = { days = if (d in days) days - d else days + d },
                                    label = { Text(shortDay(d)) },
                                    modifier = Modifier.heightIn(min = 40.dp),
                                )
                            }
                        }
                        if (base?.activeFrom != null && base.activeFrom.isAfter(today)) {
                            Text("Starts ${DateFmt.medium(base.activeFrom)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Overline("Time of day")
                    ChipChoice(TimeSlot.entries, slot, { it.label }) { slot = it }
                    Overline("Category")
                    ChipChoice(Category.entries, category, { it.label }) { category = it }
                    Overline("Linked goal")
                    ChipChoice(listOf<String?>(null) + GoalIds.ALL, goalId, { GoalIds.shortName(it) }) { goalId = it }
                    Overline("Effort")
                    ChipChoice(EnergyLevel.entries, energy, { it.label }) { energy = it }

                    Overline("Ideal version", color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(ideal, { ideal = it }, label = { Text("One step per line") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    OutlinedTextField(idealMin, { idealMin = it.filter(Char::isDigit).take(3) }, label = { Text("Ideal minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Overline("Minimum version", color = MaterialTheme.colorScheme.secondary)
                    OutlinedTextField(minimum, { minimum = it }, label = { Text("The smallest version that still counts") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(minMin, { minMin = it.filter(Char::isDigit).take(3) }, label = { Text("Minimum minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)

                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(resources, { resources = it }, label = { Text("Learning resources / links (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

                    SwitchRow("Counts as a gym session", "Used for weekly gym totals", workout) { workout = it }
                    SwitchRow("Enabled", "Disabled tasks are hidden from Today and the widget", enabled) { enabled = it }

                    if (base != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete task", color = MaterialTheme.colorScheme.error) }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (confirmDelete && base != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${base.title}\"?") },
            text = { Text("Past completions stay in your history. If you only want a break from it, turn off \"Enabled\" instead.") },
            confirmButton = { TextButton(onClick = { vm.deleteTask(base.id); confirmDelete = false; onDismiss() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.size(8.dp))
        Switch(checked, onChange)
    }
}
