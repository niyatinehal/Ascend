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
import app.levelup2027.ui.components.GroupLabel
import app.levelup2027.ui.components.HelpText
import app.levelup2027.ui.components.LinkRow
import app.levelup2027.ui.components.ScreenHeader
import app.levelup2027.ui.components.SectionTitle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
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
        SectionTitle("Time slot shown")
        ChipChoice(listOf<TimeSlot?>(null) + TimeSlot.entries, value.slot, { it?.label ?: "All of today" }) { onChange(value.copy(slot = it)) }
        SectionTitle("Task text")
        ChipChoice(WidgetMode.entries, value.mode, { if (it == WidgetMode.IDEAL) "Ideal versions" else "Minimum versions" }) { onChange(value.copy(mode = it)) }
        Text(
            "Tapping a widget checkbox completes the version shown. On a low-energy day the widget always shows minimum versions.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow("Show completed tasks", "Off: only what's left today", value.showCompleted) { onChange(value.copy(showCompleted = it)) }
        SectionTitle("Categories shown")
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
    var editingPrompts by remember { mutableStateOf(false) }
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader("Settings") }

        // ---- Planning ----
        item { GroupLabel("Planning") }
        item {
            var aiOpen by rememberSaveable { mutableStateOf(false) }
            SectionCard {
                Row(
                    Modifier.fillMaxWidth().clickable { aiOpen = !aiOpen }.heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("AI planning")
                        HelpText(s.ai.mode.label)
                    }
                    Icon(if (aiOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (aiOpen) "Hide AI settings" else "Show AI settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (aiOpen) AiSettingsSection(vm, showTitle = false)
            }
        }
        item {
            SectionCard(title = "Your day") {
                LinkRow(
                    "Daily time budget",
                    value = if (!s.capacity.isSet) "Not set" else "${s.capacity.weekdayMinutes ?: "–"} / ${s.capacity.weekendMinutes ?: "–"} min",
                ) { editingCapacity = true }
                HelpText("Weekday / weekend. Tasks that don't fit go under \"If you have time\".")
                Spacer(Modifier.height(4.dp))
                Text("Default mode", style = MaterialTheme.typography.bodyLarge)
                ChipChoice(DefaultMode.entries, s.defaultMode, { it.label }) { m -> update { it.copy(defaultMode = m) } }
                HelpText("Which version task cards emphasise. The low-energy switch on Today overrides it for a day.")
            }
        }
        item {
            SectionCard(title = "Plan & schedule") {
                LinkRow("Weekly schedule") { nav.openWeekPlan() }
                LinkRow("All tasks") { nav.goTo(Tab.TASKS) }
                LinkRow("Add a task") { nav.editTask(null) }
                LinkRow("Plan prompts") { editingPrompts = true }
            }
        }

        // ---- Reminders ----
        item { GroupLabel("Reminders") }
        item {
            SectionCard {
                if (!canNotify) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Notifications are off for Ascend.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
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
                ReminderRow("Morning", s.morningReminder, { r -> update { it.copy(morningReminder = r) } }) { editingReminder = "morning" to s.morningReminder }
                ReminderRow("During work", s.workReminder, { r -> update { it.copy(workReminder = r) } }) { editingReminder = "work" to s.workReminder }
                ReminderRow("Evening", s.eveningReminder, { r -> update { it.copy(eveningReminder = r) } }) { editingReminder = "evening" to s.eveningReminder }
                ReminderRow("Weekly review (Sun)", s.weeklyReviewReminder, { r -> update { it.copy(weeklyReviewReminder = r) } }) { editingReminder = "weekly" to s.weeklyReviewReminder }
                HelpText("Skipped when nothing is left in that slot. May arrive a few minutes late.")
                if (!batteryExempt) {
                    LinkRow("Battery optimisation") {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            }
        }

        // ---- Personalise ----
        item { GroupLabel("Personalise") }
        item {
            SectionCard(title = "Appearance") {
                ChipChoice(ThemeChoice.entries, s.theme, { it.label }) { t -> update(Repository.ChangeKind.OTHER) { it.copy(theme = t) } }
            }
        }
        item {
            SectionCard(title = "Categories") {
                HelpText("Off hides them from Today and the widget; history is kept. Tap a name to rename or merge.")
                categories.forEach { c ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(c.icon, null, tint = c.color, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(
                            c.name, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).clickable { editingCategory = c }.padding(vertical = 12.dp),
                        )
                        Switch(
                            checked = c.enabled,
                            onCheckedChange = { on ->
                                if (!on && categories.count { it.enabled } <= 1) nav.showMessage("Keep at least one category on.")
                                else vm.saveCategory(c.copy(enabled = on))
                            },
                        )
                    }
                }
                TextButton(onClick = { newCategory = true }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("New category")
                }
            }
        }
        item {
            var widgetOpen by rememberSaveable { mutableStateOf(false) }
            SectionCard {
                Row(
                    Modifier.fillMaxWidth().clickable { widgetOpen = !widgetOpen }.heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("Home-screen widget")
                        HelpText("Long-press your home screen → Widgets → Ascend")
                    }
                    Icon(if (widgetOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (widgetOpen) "Hide widget options" else "Show widget options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (widgetOpen) WidgetSettingsEditor(s.widget, categories) { w -> update { it.copy(widget = w) } }
            }
        }

        // ---- Data ----
        item { GroupLabel("Your data") }
        item {
            SectionCard {
                HelpText("Everything is stored only on this phone. Export a backup now and then (e.g. to Google Drive).")
                LinkRow("Export backup") { exportLauncher.launch("ascend-backup-${LocalDate.now()}.json") }
                LinkRow("Restore from backup") { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                if (s.seedVersion == 0) LinkRow("Load the example plan") { vm.loadExamplePlan(); nav.showMessage("Example plan added") }
                else LinkRow("Reset example plan") { confirmReset = false }
                LinkRow("Delete all my data", danger = true) { confirmErase = true }
            }
        }

        if (changeLog.isNotEmpty()) {
            item {
                var showAll by rememberSaveable { mutableStateOf(false) }
                SectionCard(title = "Recent plan changes") {
                    changeLog.take(if (showAll) 8 else 3).forEach { e ->
                        Column {
                            Text(
                                java.time.Instant.ofEpochMilli(e.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let(app.levelup2027.domain.DateFmt::short) +
                                    " · " + e.summary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (showAll && e.detail.isNotBlank()) HelpText(e.detail)
                        }
                    }
                    if (changeLog.size > 3 || changeLog.any { it.detail.isNotBlank() }) {
                        TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show less" else "Show more") }
                    }
                }
            }
        }

        item {
            Text("Ascend · v${BuildConfig.VERSION_NAME} · works offline; AI is optional",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp))
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

    if (editingPrompts) app.levelup2027.ui.plan.PromptTemplatesEditor(vm, onDismiss = { editingPrompts = false })
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
private fun ReminderRow(label: String, r: Reminder, onChange: (Reminder) -> Unit, onEditTime: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (r.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onEditTime, enabled = r.enabled) { Text(r.label) }
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
