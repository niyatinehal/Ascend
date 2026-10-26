package app.levelup2027.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.levelup2027.BuildConfig
import app.levelup2027.data.AppSettings
import app.levelup2027.data.BackupFile
import app.levelup2027.data.BackupManager
import app.levelup2027.data.CategoryEntity
import app.levelup2027.data.CategoryIcons
import app.levelup2027.data.DefaultMode
import app.levelup2027.data.Reminder
import app.levelup2027.data.Repository
import app.levelup2027.data.SettingsJson
import app.levelup2027.data.ThemeChoice
import app.levelup2027.data.TimeSlot
import app.levelup2027.data.WidgetSettings
import app.levelup2027.domain.WidgetMode
import app.levelup2027.notify.Notifications
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.LocalNav
import app.levelup2027.ui.Tab
import app.levelup2027.ui.components.Overline
import app.levelup2027.ui.components.SectionCard
import app.levelup2027.ui.plan.AiSettingsSection
import app.levelup2027.ui.plan.CapacityDialog
import app.levelup2027.ui.theme.categoryIcon
import app.levelup2027.ui.tasks.ChipChoice
import app.levelup2027.ui.tasks.SwitchRow
import app.levelup2027.ui.theme.color
import app.levelup2027.ui.theme.icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetSettingsEditor(value: WidgetSettings, categories: List<CategoryEntity>, onChange: (WidgetSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline("Time slot shown")
        ChipChoice(listOf<TimeSlot?>(null) + TimeSlot.entries, value.slot, { it?.label ?: "All of today" }) { onChange(value.copy(slot = it)) }
        Overline("Task text")
        ChipChoice(WidgetMode.entries, value.mode, { if (it == WidgetMode.IDEAL) "Ideal versions" else "Minimum versions" }) { onChange(value.copy(mode = it)) }
        Text(
            "Tapping a widget checkbox completes the version shown. On a low-energy day the widget always shows minimum versions.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow("Show completed tasks", "Off: only what's left today", value.showCompleted) { onChange(value.copy(showCompleted = it)) }
        Overline("Categories shown")
        Text("None selected means all categories.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.filter { it.enabled }.forEach { c ->
                val on = c.id in value.categories
                FilterChip(
                    selected = on,
                    onClick = { onChange(value.copy(categories = if (on) value.categories - c.id else value.categories + c.id)) },
                    label = { Text(c.shortName) },
                    leadingIcon = { Icon(c.icon, null, Modifier.size(16.dp), tint = c.color) },
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settingsState by vm.settings.collectAsState()
    val s = settingsState ?: return
    val categories by vm.categories.collectAsState()
    val changeLog by vm.changeLog.collectAsState()
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var newCategory by remember { mutableStateOf(false) }
    var editingCapacity by remember { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()

    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resumeTick++; onPauseOrDispose { } }
    val canNotify = remember(resumeTick) { Notifications.canPost(context) }
    val batteryExempt = remember(resumeTick) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    var editingReminder by remember { mutableStateOf<Pair<String, Reminder>?>(null) }
    var pendingRestore by remember { mutableStateOf<Pair<String, BackupFile>?>(null) }
    var confirmReset by remember { mutableStateOf<Boolean?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        resumeTick++
        if (!granted) nav.showMessage("Notifications stay off. You can allow them later in Android settings.")
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val json = vm.backup.export()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) } }
            }
            nav.showMessage(if (ok.isSuccess) "Backup saved" else "Export failed: ${ok.exceptionOrNull()?.message}")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                text to BackupManager.parse(text)
            }
            result.onSuccess { pendingRestore = it }.onFailure { nav.showMessage("Couldn't read that file: ${it.message?.take(80)}") }
        }
    }

    fun update(kind: Repository.ChangeKind = Repository.ChangeKind.SETTINGS, f: (AppSettings) -> AppSettings) = vm.updateSettings(kind, f)

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }

        item { SectionCard { AiSettingsSection(vm) } }

        item {
            SectionCard {
                Overline("Daily time budget")
                Text(
                    if (!s.capacity.isSet) "Not set. Set one so Today shows what fits and puts the rest under \"If you have time\"."
                    else "${s.capacity.weekdayMinutes ?: "–"} min on weekdays · ${s.capacity.weekendMinutes ?: "–"} min at weekends",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SettingsButton(if (s.capacity.isSet) "Change time budget" else "Set time budget") { editingCapacity = true }
            }
        }

        item {
            SectionCard {
                Overline("Daily mode")
                Spacer(Modifier.height(6.dp))
                ChipChoice(DefaultMode.entries, s.defaultMode, { it.label }) { m -> update { it.copy(defaultMode = m) } }
                Text(
                    "Decides which version the task cards emphasise and what one tap completes. The Low-energy switch on Today overrides it for one day only.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard {
                Overline("Schedule & tasks")
                SettingsButton("Edit weekly schedule") { nav.openWeekPlan() }
                SettingsButton("Edit task definitions") { nav.goTo(Tab.TASKS) }
                SettingsButton("Add a task") { nav.editTask(null) }
            }
        }

        item {
            SectionCard {
                Overline("Reminders")
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (canNotify) "Notifications are allowed." else "Notifications are off for this app.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    if (!canNotify) {
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && !s.notificationPromptDismissed) {
                                update { it.copy(notificationPromptDismissed = true) }
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            }
                        }) { Text("Allow") }
                    }
                }
                ReminderRow("Morning checklist", s.morningReminder, { r -> update { it.copy(morningReminder = r) } }) { editingReminder = "morning" to s.morningReminder }
                ReminderRow("During-work reminder", s.workReminder, { r -> update { it.copy(workReminder = r) } }) { editingReminder = "work" to s.workReminder }
                ReminderRow("Evening checklist", s.eveningReminder, { r -> update { it.copy(eveningReminder = r) } }) { editingReminder = "evening" to s.eveningReminder }
                ReminderRow("Weekly review (Sunday)", s.weeklyReviewReminder, { r -> update { it.copy(weeklyReviewReminder = r) } }) { editingReminder = "weekly" to s.weeklyReviewReminder }
                Text(
                    "Reminders are skipped when nothing is left in that slot. They use battery-friendly timing and may arrive a few minutes late." +
                        if (!batteryExempt) " If they arrive very late, exempt the app from battery optimisation." else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!batteryExempt) {
                    SettingsButton("Battery optimisation settings") {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            }
        }

        item {
            SectionCard {
                Overline("Categories")
                Text("Turned-off categories are hidden from Today, counts and the widget. Their history is kept. Tap a name to rename or merge it.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                categories.forEach { c ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(c.icon, null, tint = c.color)
                        Spacer(Modifier.size(12.dp))
                        TextButton(onClick = { editingCategory = c }, modifier = Modifier.weight(1f)) {
                            Text(c.name, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
                        }
                        Switch(
                            checked = c.enabled,
                            onCheckedChange = { on ->
                                if (!on && categories.count { it.enabled } <= 1) nav.showMessage("Keep at least one category on.")
                                else vm.saveCategory(c.copy(enabled = on))
                            },
                        )
                    }
                }
                SettingsButton("New category") { newCategory = true }
            }
        }

        item {
            SectionCard {
                Overline("Home-screen widget")
                Spacer(Modifier.height(6.dp))
                WidgetSettingsEditor(s.widget, categories) { w -> update { it.copy(widget = w) } }
                Text(
                    "Add it from your home screen: long-press → Widgets → Ascend. Resize it for more tasks.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard {
                Overline("Appearance")
                Spacer(Modifier.height(6.dp))
                ChipChoice(ThemeChoice.entries, s.theme, { it.label }) { t -> update(Repository.ChangeKind.OTHER) { it.copy(theme = t) } }
            }
        }

        item {
            SectionCard {
                Overline("Your data")
                Text("Your plan and history are stored only on this phone. Export a backup file regularly (e.g. to Google Drive) and restore it on a new phone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsButton("Export backup (JSON)") { exportLauncher.launch("ascend-backup-${LocalDate.now()}.json") }
                SettingsButton("Restore from backup") { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                if (s.seedVersion == 0) SettingsButton("Load the example plan (Level Up 2027)") { vm.loadExamplePlan(); nav.showMessage("Example plan added") }
                else SettingsButton("Reset example plan (keep history)") { confirmReset = false }
                SettingsButton("Delete all my data", danger = true) { confirmErase = true }
            }
        }

        if (changeLog.isNotEmpty()) {
            item {
                SectionCard {
                    Overline("Recent plan changes")
                    changeLog.take(8).forEach { e ->
                        Text(
                            java.time.Instant.ofEpochMilli(e.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let(app.levelup2027.domain.DateFmt::short) +
                                " · " + e.summary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (e.detail.isNotBlank()) Text(e.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
                    }
                }
            }
        }

        item {
            Text("Ascend · v${BuildConfig.VERSION_NAME} · your plan works offline; AI is optional",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    editingReminder?.let { (which, r) ->
        TimeDialog(r, onDismiss = { editingReminder = null }) { h, m ->
            val nr = r.copy(hour = h, minute = m, enabled = true)
            update {
                when (which) {
                    "morning" -> it.copy(morningReminder = nr)
                    "work" -> it.copy(workReminder = nr)
                    "evening" -> it.copy(eveningReminder = nr)
                    else -> it.copy(weeklyReviewReminder = nr)
                }
            }
            editingReminder = null
        }
    }

    pendingRestore?.let { (json, file) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Text("It contains ${file.tasks.size} tasks, ${file.completions.size} completion records and ${file.reviews.size} reviews. Your current data on this phone will be replaced.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val r = runCatching { vm.backup.restore(json) }
                        if (r.isSuccess) {
                            vm.updateSettings(Repository.ChangeKind.SETTINGS) { it } // re-arm reminders, refresh widget
                            nav.showMessage("Backup restored")
                        } else nav.showMessage("Restore failed: ${r.exceptionOrNull()?.message?.take(80)}")
                    }
                }) { Text("Replace my data") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }

    if (editingCapacity) {
        CapacityDialog(s.capacity.weekdayMinutes, s.capacity.weekendMinutes, onDismiss = { editingCapacity = false }) { a, b ->
            update { it.copy(capacity = app.levelup2027.data.Capacity(a, b)) }
            editingCapacity = false
        }
    }
    editingCategory?.let { c ->
        CategoryDialog(c, categories, onDismiss = { editingCategory = null },
            onSave = { vm.saveCategory(it); editingCategory = null },
            onMerge = { into -> vm.mergeCategory(c.id, into); editingCategory = null; nav.showMessage("Merged into ${categories.first { it.id == into }.name}") })
    }
    if (newCategory) {
        CategoryDialog(null, categories, onDismiss = { newCategory = false }, onSave = { vm.createCategory(it.name, it.iconKey); newCategory = false }, onMerge = {})
    }
    if (confirmErase) {
        var forgetKey by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Delete all your data?") },
            text = {
                Column {
                    Text("This deletes every goal, task, completion, progress entry, review and setting on this phone, and removes this phone's usage record from the Ascend AI server. It can't be undone. Export a backup first if you might want it back.")
                    SwitchRow("Also remove my API key", null, forgetKey) { forgetKey = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmErase = false
                    vm.eraseAll(forgetKey) { nav.showMessage("All data deleted") }
                }) { Text("Delete everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancel") } },
        )
    }

    confirmReset?.let { erase ->
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text(if (erase) "Erase everything?" else "Reset the plan?") },
            text = {
                Text(
                    if (erase) "All tasks return to the example plan, and all completions, progress entries and reviews are deleted. Consider exporting a backup first."
                    else "The example plan's tasks, goals and milestones return to their defaults. Any other tasks are removed. Your completions, progress entries and reviews are kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetPlan(clearHistory = erase); confirmReset = null
                    nav.showMessage(if (erase) "Started fresh" else "Plan reset")
                }) { Text(if (erase) "Erase" else "Reset", color = if (erase) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsButton(text: String, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ReminderRow(label: String, r: Reminder, onChange: (Reminder) -> Unit, onEditTime: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onEditTime) { Text(r.label) }
        Switch(r.enabled, { onChange(r.copy(enabled = it)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(r: Reminder, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = r.hour, initialMinute = r.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time") },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onSave(state.hour, state.minute) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(
    original: CategoryEntity?,
    all: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit,
    onMerge: (String) -> Unit,
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var icon by remember { mutableStateOf(original?.iconKey ?: "label") }
    var mergeInto by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Overline("Icon")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategoryIcons.KEYS.forEach { k ->
                        FilterChip(selected = icon == k, onClick = { icon = k }, label = { Icon(categoryIcon(k), k, Modifier.size(18.dp)) })
                    }
                }
                if (original != null) {
                    Overline("Merge into another category")
                    Text("Moves all its tasks and goals there and removes this one. History is unaffected.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        all.filter { it.id != original.id }.forEach { c ->
                            FilterChip(selected = mergeInto == c.id, onClick = { mergeInto = if (mergeInto == c.id) null else c.id }, label = { Text(c.shortName) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = mergeInto
                when {
                    target != null -> onMerge(target)
                    name.isNotBlank() -> onSave((original ?: CategoryEntity("", "", "")).copy(name = name.trim(), shortName = name.trim().take(12), iconKey = icon))
                }
            }) { Text(if (mergeInto != null) "Merge" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
