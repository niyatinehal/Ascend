package app.levelup2027.ui.today

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.levelup2027.data.AppSettings
import app.levelup2027.data.CompletionType
import app.levelup2027.data.DefaultMode
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.DateFmt
import app.levelup2027.domain.Dates
import app.levelup2027.domain.FocusItem
import app.levelup2027.domain.MissedAction
import app.levelup2027.domain.Occurrence
import app.levelup2027.domain.Xp
import app.levelup2027.notify.Notifications
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.DayState
import app.levelup2027.ui.LocalNav
import app.levelup2027.ui.TodayFilter
import app.levelup2027.ui.components.Overline
import app.levelup2027.ui.components.Pill
import app.levelup2027.ui.components.ProgressLine
import app.levelup2027.ui.components.SkipDialog
import app.levelup2027.ui.components.CollapsibleCard
import app.levelup2027.ui.components.RescheduleDialog
import app.levelup2027.ui.components.TaskCard
import app.levelup2027.ui.theme.icon
import java.time.LocalTime

@Composable
fun TodayScreen(vm: AppViewModel) {
    val day by vm.day.collectAsState()
    val settings by vm.settings.collectAsState()
    val filter by vm.todayFilter.collectAsState()
    val d = day
    val s = settings
    if (d == null || s == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }
    var skipping by remember { mutableStateOf<Occurrence?>(null) }
    var moving by remember { mutableStateOf<Occurrence?>(null) }
    var showIfTime by remember { mutableStateOf(false) }
    var customising by remember { mutableStateOf(false) }
    // Cards whose expanded state differs from the default (so "default expanded" can flip them all).
    var toggledCards by rememberSaveable { mutableStateOf(setOf<String>()) }
    val nav = LocalNav.current
    val milestones by vm.milestones.collectAsState()
    val goalsNow by vm.goals.collectAsState()
    val allTasksState by vm.tasks.collectAsState()
    val allTasks = allTasksState.orEmpty()
    val layout = s.todayLayout
    val cardLayout = s.cardLayout

    fun isCollapsed(id: String) = id in layout.collapsed
    fun toggleSection(id: String) = vm.updateSettings(app.levelup2027.data.Repository.ChangeKind.OTHER) {
        val c = it.todayLayout.collapsed
        it.copy(todayLayout = it.todayLayout.copy(collapsed = if (id in c) c - id else c + id))
    }
    fun cardKey(o: Occurrence) = "${o.task.id}@${o.date}"
    fun isExpanded(o: Occurrence) = cardLayout.expandedByDefault != (cardKey(o) in toggledCards)
    fun toggleCard(o: Occurrence) { val k = cardKey(o); toggledCards = if (k in toggledCards) toggledCards - k else toggledCards + k }

    val mode = if (d.lowEnergy) DefaultMode.MINIMUM else s.defaultMode
    fun modeFor(o: Occurrence) = if (o.task.id in d.preferMinimum) DefaultMode.MINIMUM else mode
    val list: List<Occurrence> = when (filter) {
        TodayFilter.TODAY -> d.occurrences
        TodayFilter.OVERDUE -> d.overdue
        TodayFilter.COMPLETED -> d.occurrences.filter { it.isDone }
        else -> d.occurrences.filter { it.task.timeSlot == filter.slot }
    }

    @Composable
    fun TaskItem(occ: Occurrence, showDate: Boolean = false) {
        TaskCard(
            occ = occ,
            mode = modeFor(occ),
            showDate = showDate,
            onOpen = { nav.openTask(occ.task.id, occ.date) },
            onComplete = { type -> vm.complete(occ.task, occ.date, type) },
            onSkip = { skipping = occ },
            onUndo = { vm.undo(occ.task.id, occ.date) },
            onReschedule = { moving = occ },
            layout = cardLayout,
            expanded = isExpanded(occ),
            onToggleExpand = { toggleCard(occ) },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Header(d, s, milestones, onAdjust = nav.openAdjust, onCustomise = { customising = true }) }
        if (layout.showProgress) {
            item(key = "progress") {
                val p = d.progress
                CollapsibleCard(
                    "Today's progress", "${p.done}/${p.scheduled} done · ${p.ideal} ideal · ${p.minimum} min" + if (d.lowEnergy) " · low-energy day" else "",
                    isCollapsed("progress"), { toggleSection("progress") },
                ) { ProgressBody(d, onLowEnergy = { vm.setLowEnergy(!d.lowEnergy) }) }
            }
        }
        if (layout.showFocus && filter == TodayFilter.TODAY && d.focus.mostImportant != null) {
            item(key = "focus") {
                CollapsibleCard(
                    "Focus", d.focus.mostImportant.occ.task.title, isCollapsed("focus"), { toggleSection("focus") },
                    titleColor = MaterialTheme.colorScheme.primary,
                ) { FocusBody(d, onOpen = { nav.openTask(it.occ.task.id, it.occ.date) }) }
            }
        }
        if (layout.showMissed && filter == TodayFilter.TODAY && d.missed.isNotEmpty()) {
            item(key = "missed") {
                CollapsibleCard(
                    "From earlier days (${d.missed.size})", "Nothing moved automatically", isCollapsed("missed"), { toggleSection("missed") },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) { MissedBody(vm, d, onMove = { moving = it }) }
            }
        }
        val nextWeek = Dates.weekStart(d.date).plusWeeks(1)
        val wantsWeekPlan = layout.showWeekPlanner && Dates.daysUntil(d.date, nextWeek) <= 3 &&
            goalsNow.any { it.isActive && it.source == app.levelup2027.data.Source.AI } &&
            allTasks.none { it.enabled && it.scheduledDate?.let { sd -> !sd.isBefore(nextWeek) && sd.isBefore(nextWeek.plusDays(7)) } == true && it.source == app.levelup2027.data.Source.AI }
        if (filter == TodayFilter.TODAY && wantsWeekPlan) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Next week isn't planned yet", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { nav.planNextWeek(nextWeek) }) { Text("Plan it") }
                    }
                }
            }
        }
        if (!s.widgetHintDismissed && d.progress.done > 0) {
            item { WidgetHint(onDismiss = { vm.updateSettings { it.copy(widgetHintDismissed = true) } }) }
        }
        if (s.notificationPromptEligible && !s.notificationPromptDismissed) {
            item { NotificationPrompt(vm, s) }
        }
        if (layout.showFilters) item { FilterRow(d, filter, onSelect = { vm.todayFilter.value = it }) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { CountLine(filter, list) }
                val anyExpanded = list.any { isExpanded(it) }
                TextButton(onClick = {
                    // Flip every visible card to the same state.
                    toggledCards = if (anyExpanded) list.filter { cardLayout.expandedByDefault }.map(::cardKey).toSet()
                    else list.filter { !cardLayout.expandedByDefault }.map(::cardKey).toSet()
                }) { Text(if (anyExpanded) "Collapse all" else "Expand all") }
            }
        }

        if (list.isEmpty()) {
            item { EmptyState(filter, d) }
        } else if (filter == TodayFilter.TODAY) {
            // Tasks beyond the daily time budget stay visible, just grouped as optional.
            val main = list.filter { it.task.id !in d.focus.ifTime }
            val extra = list.filter { it.task.id in d.focus.ifTime }
            TimeSlot.entries.forEach { slot ->
                val group = main.filter { it.task.timeSlot == slot }
                if (group.isNotEmpty()) {
                    val id = "slot:${slot.name}"
                    item(key = "h-$slot") { SlotHeader(slot, group, collapsed = isCollapsed(id), onToggle = { toggleSection(id) }) }
                    if (!isCollapsed(id)) items(group, key = ::cardKey) { occ -> TaskItem(occ) }
                }
            }
            if (extra.isNotEmpty()) {
                item(key = "iftime") {
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Overline("If you have time (${extra.size})", Modifier.weight(1f))
                            TextButton(onClick = { showIfTime = !showIfTime }) { Text(if (showIfTime) "Hide" else "Show") }
                        }
                        if (showIfTime) d.focus.tradeOff?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (showIfTime) items(extra, key = { "x-" + cardKey(it) }) { occ -> TaskItem(occ) }
            }
        } else {
            items(list, key = ::cardKey) { occ -> TaskItem(occ, showDate = filter == TodayFilter.OVERDUE) }
        }
    }

    if (customising) CustomiseTodaySheet(vm, s, onDismiss = { customising = false; toggledCards = emptySet() })

    moving?.let { occ ->
        RescheduleDialog(occ.task.title, occ.date, d.date, onDismiss = { moving = null }) { to ->
            vm.reschedule(occ.task, occ.date, to) { r ->
                nav.showMessage(r.fold({ "Moved to ${DateFmt.short(to)}" }, { "Couldn't move it: ${it.message}" }))
            }
            moving = null
        }
    }

    skipping?.let { occ ->
        SkipDialog(
            taskTitle = occ.task.title,
            isRecurring = !occ.task.isOneTime,
            onDismiss = { skipping = null },
            onSkip = { reason, note ->
                vm.complete(occ.task, occ.date, CompletionType.SKIPPED, reason, note)
                skipping = null
            },
        )
    }
}

/** Answers: what matters most today, what to do next, and the minimum useful action. */
@Composable
private fun FocusBody(d: DayState, onOpen: (FocusItem) -> Unit) {
    val top = d.focus.mostImportant ?: return
    val next = d.focus.next
    run {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusLine("Matters most today", top, onOpen)
            if (next != null && next.taskId != top.taskId) FocusLine("Next up", next, onOpen)
            val anchor = next ?: top
            Column {
                Overline("Minimum if you can't do the full task", color = MaterialTheme.colorScheme.secondary)
                Text(
                    app.levelup2027.ui.components.firstBullet(anchor.occ.task.minimumDescription).ifBlank { "Rest is fine today." } +
                        if (anchor.occ.task.estimatedMinimumMinutes > 0) " · ${anchor.occ.task.estimatedMinimumMinutes} min" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (d.focus.capacity != null) {
                Text("${d.focus.plannedMinutes} min planned · budget ${d.focus.capacity} min", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FocusLine(label: String, item: FocusItem, onOpen: (FocusItem) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Open details") { onOpen(item) }) {
        Overline(label, color = MaterialTheme.colorScheme.primary)
        Text(item.occ.task.title, style = MaterialTheme.typography.titleMedium)
        val extra = buildList {
            add("${item.minutes} min" + if (item.useMinimum) " (minimum)" else "")
            if (item.blockedBy.isNotEmpty()) add("waiting on ${item.blockedBy.first()}")
            if (item.reason.isNotBlank()) add(item.reason)
        }
        Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Missed one-time tasks: a suggestion each, confirmed by the user. Nothing rolls forward on its own. */
@Composable
private fun MissedBody(vm: AppViewModel, d: DayState, onMove: (Occurrence) -> Unit) {
    val nav = LocalNav.current
    run {
        Column {
            Text("Nothing was moved automatically. Here's a suggestion for each; your history is kept either way.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            run {
                d.missed.forEach { m ->
                    Spacer(Modifier.height(10.dp))
                    Text(m.occ.task.title + " · " + DateFmt.short(m.occ.date), style = MaterialTheme.typography.bodyMedium)
                    Text(m.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (m.action == MissedAction.RESCHEDULE && m.toDate != null) {
                            FilledTonalButton(onClick = {
                                vm.reschedule(m.occ.task, m.occ.date, m.toDate) { r ->
                                    r.onSuccess { newId -> if (m.useMinimum) vm.setPreferMinimum(newId, m.toDate, true) }
                                    nav.showMessage(r.fold({ "Moved to ${DateFmt.short(m.toDate)}" }, { "Couldn't move it: ${it.message}" }))
                                }
                            }) { Text("Move to ${DateFmt.short(m.toDate)}") }
                        } else {
                            FilledTonalButton(onClick = { vm.complete(m.occ.task, m.occ.date, CompletionType.SKIPPED, app.levelup2027.data.SkipReason.NOT_RELEVANT, "Let go after missing it") }) {
                                Text("Let it go")
                            }
                        }
                        TextButton(onClick = { onMove(m.occ) }) { Text("Pick a day") }
                        if (m.action == MissedAction.RESCHEDULE) {
                            TextButton(onClick = { vm.complete(m.occ.task, m.occ.date, CompletionType.SKIPPED, app.levelup2027.data.SkipReason.NOT_RELEVANT, "Let go after missing it") }) {
                                Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetHint(onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Widgets, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("Keep your next step in view: long-press your home screen → Widgets → Ascend.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    }
}

@Composable
private fun Header(d: DayState, s: AppSettings, milestones: List<app.levelup2027.data.Milestone>, onAdjust: () -> Unit, onCustomise: () -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
    val level = Xp.level(d.xp)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Overline(DateFmt.long(d.date))
            Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Pill("Lv $level · ${d.xp} XP", MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            ProgressLine(Xp.levelFraction(d.xp), Modifier.width(80.dp).height(3.dp))
        }
        IconButton(onClick = onAdjust) { Icon(Icons.Outlined.AutoAwesome, "Adjust my plan", tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = onCustomise) { Icon(Icons.Outlined.Tune, "Customise Today") }
    }
    val start = s.planStartDate
    val nextMilestone = milestones.filter { !it.done && !it.dueDate.isBefore(d.date) }.minByOrNull { it.dueDate }
    val campaignText = listOfNotNull(
        start?.let { if (d.date.isBefore(it)) "Your plan starts ${DateFmt.medium(it)}" else "Day ${Dates.dayNumber(it, d.date)} of your plan" },
        nextMilestone?.let { "${Dates.daysUntil(d.date, it.dueDate)} days to: ${it.title}" },
    ).joinToString(" · ")
    Text(campaignText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ProgressBody(d: DayState, onLowEnergy: () -> Unit) {
    val p = d.progress
    run {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${p.done}/${p.scheduled}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { stateDescription = "${p.done} of ${p.scheduled} tasks completed" },
                )
                Spacer(Modifier.width(8.dp))
                Text("tasks completed today", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            ProgressLine(p.fraction, Modifier.height(8.dp))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("${p.ideal} ideal", MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Pill("${p.minimum} minimum", MaterialTheme.colorScheme.secondary)
                if (p.skipped > 0) {
                    Spacer(Modifier.width(6.dp))
                    Pill("${p.skipped} skipped", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            FilterChip(
                selected = d.lowEnergy,
                onClick = onLowEnergy,
                label = {
                    Text(if (d.lowEnergy) "Low-energy day: showing minimum versions · tap to turn off" else "Low-energy day? Show minimum versions")
                },
                leadingIcon = { Icon(Icons.Outlined.BatteryChargingFull, null, Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconColor = MaterialTheme.colorScheme.secondary,
                ),
            )
        }
    }
}

@Composable
private fun NotificationPrompt(vm: AppViewModel, s: AppSettings) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.updateSettings { it.copy(notificationPromptDismissed = true) }
    }
    if (Notifications.canPost(context)) {
        // Already allowed (e.g. Android < 13); nothing to ask.
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                "Want gentle reminders? Morning ${s.morningReminder.label}, work ${s.workReminder.label}, evening ${s.eveningReminder.label}, and Sunday review.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Column {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.updateSettings { it.copy(notificationPromptDismissed = true) }
                }) { Text("Turn on") }
                TextButton(onClick = { vm.updateSettings { it.copy(notificationPromptDismissed = true) } }) {
                    Text("No thanks", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FilterRow(d: DayState, selected: TodayFilter, onSelect: (TodayFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(TodayFilter.entries) { f ->
            val count = when (f) {
                TodayFilter.TODAY -> d.occurrences.size
                TodayFilter.OVERDUE -> d.overdue.size
                TodayFilter.COMPLETED -> d.occurrences.count { it.isDone }
                else -> d.occurrences.count { it.task.timeSlot == f.slot }
            }
            if (f == TodayFilter.OVERDUE && count == 0 && selected != f) return@items
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = { Text("${f.label} $count") },
                leadingIcon = f.slot?.let { slot -> { Icon(slot.icon, null, Modifier.size(16.dp)) } },
                modifier = Modifier.heightIn(min = 40.dp),
            )
        }
    }
}

@Composable
private fun CountLine(filter: TodayFilter, list: List<Occurrence>) {
    val text = when (filter) {
        TodayFilter.OVERDUE -> "${list.size} one-time task${if (list.size == 1) "" else "s"} from earlier days. Do them late, move them, or skip with no penalty. Recurring tasks never pile up here."
        TodayFilter.COMPLETED -> {
            val i = list.count { it.completion?.completionType == CompletionType.IDEAL }
            "${list.size} completed today · $i ideal · ${list.size - i} minimum"
        }
        TodayFilter.TODAY -> {
            val open = list.count { it.isOpen }
            if (open == 0 && list.isNotEmpty()) "Everything is handled for today." else "$open left · ${list.count { it.isDone }} of ${list.size} done"
        }
        else -> "${filter.label}: ${list.count { it.isDone }} of ${list.size} done"
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 2.dp))
}

@Composable
private fun SlotHeader(slot: TimeSlot, group: List<Occurrence>, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).clickable(onClickLabel = if (collapsed) "Show ${slot.label}" else "Hide ${slot.label}", onClick = onToggle)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(slot.icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Overline(slot.label)
        Spacer(Modifier.weight(1f))
        Text("${group.count { it.isDone }}/${group.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null, Modifier.padding(start = 4.dp).size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(filter: TodayFilter, d: DayState) {
    val msg = when (filter) {
        TodayFilter.OVERDUE -> "Nothing overdue. Missed recurring tasks simply return on their next day."
        TodayFilter.COMPLETED -> "Nothing completed yet today. One minimum version is a real start."
        TodayFilter.TODAY -> "Nothing scheduled today. Rest is part of the plan."
        else -> "No ${filter.label.lowercase()} tasks today."
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
