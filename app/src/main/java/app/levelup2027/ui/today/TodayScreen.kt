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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
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
import app.levelup2027.ui.theme.OverlineStyle
import app.levelup2027.ui.components.HelpText
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
    val chosenFilter by vm.todayFilter.collectAsState()
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
    val goalsNow by vm.goals.collectAsState()
    val allTasksState by vm.tasks.collectAsState()
    val allTasks = allTasksState.orEmpty()
    val layout = s.todayLayout
    val cardLayout = s.cardLayout
    // Without the chips there is no way to leave a filter, so Today always shows the whole day.
    val filter = if (layout.showFilters) chosenFilter else TodayFilter.TODAY

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

    // The "Start here" task lives in the summary card, so it isn't repeated in the list below.
    val startHere = (d.focus.next ?: d.focus.mostImportant)?.takeIf { layout.showFocus && filter == TodayFilter.TODAY && it.occ.isOpen }
    var missedOpen by rememberSaveable { mutableStateOf(false) }
    var doneOpen by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Header(d, onAdjust = nav.openAdjust, onCustomise = { customising = true }) }
        if (filter == TodayFilter.TODAY && (layout.showProgress || (layout.showFocus && d.focus.mostImportant != null))) {
            item(key = "summary") {
                SummaryCard(
                    d, showProgress = layout.showProgress, showFocus = layout.showFocus,
                    onOpen = { nav.openTask(it.occ.task.id, it.occ.date) },
                    onComplete = { f -> vm.complete(f.occ.task, f.occ.date, if (modeFor(f.occ) == DefaultMode.MINIMUM || f.useMinimum) CompletionType.MINIMUM else CompletionType.IDEAL) },
                )
            }
        }

        // Secondary things live behind one quiet line each, never as cards competing with the list.
        if (layout.showMissed && filter == TodayFilter.TODAY && d.missed.isNotEmpty()) {
            item(key = "missed") {
                QuietRow(
                    "${d.missed.size} from earlier days",
                    trailing = { Icon(if (missedOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { missedOpen = !missedOpen },
                )
            }
            if (missedOpen) item(key = "missed-body") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) { MissedBody(vm, d, onMove = { moving = it }) }
                }
            }
        }
        val nextWeek = Dates.weekStart(d.date).plusWeeks(1)
        val wantsWeekPlan = layout.showWeekPlanner && Dates.daysUntil(d.date, nextWeek) <= 3 &&
            goalsNow.any { it.isActive && it.source == app.levelup2027.data.Source.AI } &&
            allTasks.none { it.enabled && it.scheduledDate?.let { sd -> !sd.isBefore(nextWeek) && sd.isBefore(nextWeek.plusDays(7)) } == true && it.source == app.levelup2027.data.Source.AI }
        if (filter == TodayFilter.TODAY && wantsWeekPlan) {
            item(key = "weekplan") {
                QuietRow(
                    "Next week isn't planned yet",
                    trailing = { Text("Plan it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
                    onClick = { nav.planNextWeek(nextWeek) },
                )
            }
        }

        if (layout.showFilters) {
            item(key = "filters") {
                Column(Modifier.padding(top = 6.dp)) {
                    FilterRow(d, filter, onSelect = { vm.todayFilter.value = it })
                    if (filter != TodayFilter.TODAY) CountLine(filter, list)
                }
            }
        }

        if (list.isEmpty()) {
            item { EmptyState(filter, d) }
        } else if (filter == TodayFilter.TODAY) {
            // Only what's left is in the main list; finished tasks fold into "Done" at the bottom.
            val open = list.filter { it.isOpen && it.task.id != startHere?.occ?.task?.id }
            val handled = list.filter { !it.isOpen }
            // Tasks beyond the daily time budget stay visible, just grouped as optional.
            val main = open.filter { it.task.id !in d.focus.ifTime }
            val extra = open.filter { it.task.id in d.focus.ifTime }
            if (open.isEmpty() && startHere == null) item(key = "all-done") { EmptyState(filter, d, allDone = true) }
            TimeSlot.entries.forEach { slot ->
                val group = main.filter { it.task.timeSlot == slot }
                if (group.isNotEmpty()) {
                    val id = "slot:${slot.name}"
                    item(key = "h-$slot") { SlotHeader(slot, group.size, collapsed = isCollapsed(id), onToggle = { toggleSection(id) }) }
                    if (!isCollapsed(id)) items(group, key = ::cardKey) { occ -> TaskItem(occ) }
                }
            }
            if (extra.isNotEmpty()) {
                item(key = "iftime") {
                    Column(Modifier.padding(top = 8.dp)) {
                        QuietRow(
                            "If you have time · ${extra.size}",
                            trailing = { Icon(if (showIfTime) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { showIfTime = !showIfTime },
                        )
                        if (showIfTime) d.focus.tradeOff?.let { HelpText(it, Modifier.padding(start = 4.dp, bottom = 4.dp)) }
                    }
                }
                if (showIfTime) items(extra, key = { "x-" + cardKey(it) }) { occ -> TaskItem(occ) }
            }
            if (handled.isNotEmpty()) {
                item(key = "done") {
                    Box(Modifier.padding(top = 8.dp)) {
                        QuietRow(
                            "Done · ${handled.size}",
                            trailing = { Icon(if (doneOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { doneOpen = !doneOpen },
                        )
                    }
                }
                if (doneOpen) items(handled, key = { "d-" + cardKey(it) }) { occ -> TaskItem(occ) }
            }
        } else {
            items(list, key = ::cardKey) { occ -> TaskItem(occ, showDate = filter == TodayFilter.OVERDUE) }
        }
        item(key = "lowenergy") {
            // The one escape hatch for a hard day, kept out of the way at the bottom.
            TextButton(onClick = { vm.setLowEnergy(!d.lowEnergy) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Icon(Icons.Outlined.BatteryChargingFull, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (d.lowEnergy) "Low-energy day is on · turn off" else "Low on energy today? Show minimum versions",
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
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

/**
 * The one card at the top of Today: how the day is going, and the single thing to start with.
 * Answers "what matters most / what next / what's the minimum" without a wall of text.
 */
@Composable
private fun SummaryCard(d: DayState, showProgress: Boolean, showFocus: Boolean, onOpen: (FocusItem) -> Unit, onComplete: (FocusItem) -> Unit) {
    val p = d.progress
    val next = if (showFocus) d.focus.next ?: d.focus.mostImportant else null
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (showProgress) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { p.fraction },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 5.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                        )
                        Text(
                            "${p.done}/${p.scheduled}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { stateDescription = "${p.done} of ${p.scheduled} tasks completed" },
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    val open = d.occurrences.count { it.isOpen }
                    Column {
                        Text(
                            when {
                                p.scheduled == 0 -> "A free day"
                                open == 0 -> "All done for today"
                                open == 1 -> "1 task left"
                                else -> "$open tasks left"
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (d.lowEnergy) Text("Low-energy day · minimums only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (next != null && next.occ.isOpen) {
                if (showProgress) Spacer(Modifier.height(20.dp))
                Text("START HERE", style = OverlineStyle, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Open details") { onOpen(next) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ring = if (next.useMinimum || d.lowEnergy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    IconButton(
                        onClick = { onComplete(next) },
                        modifier = Modifier.offset(x = (-12).dp).semantics { contentDescription = "Mark ${next.occ.task.title} done" },
                    ) {
                        Box(Modifier.size(26.dp).clip(CircleShape).border(2.dp, ring, CircleShape))
                    }
                    Column(Modifier.weight(1f).offset(x = (-8).dp)) {
                        Text(next.occ.task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${next.minutes} min" + if (next.useMinimum) " · minimum" else "",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One tappable line for secondary things (earlier days, done, plan next week). */
@Composable
private fun QuietRow(text: String, trailing: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Missed one-time tasks: a suggestion each, confirmed by the user. Nothing rolls forward on its own. */
@Composable
private fun MissedBody(vm: AppViewModel, d: DayState, onMove: (Occurrence) -> Unit) {
    val nav = LocalNav.current
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) d.missed else d.missed.take(3)
    Column {
        HelpText("Nothing moved automatically. Decide one at a time; history is kept either way.")
        shown.forEach { m ->
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(m.occ.task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(DateFmt.short(m.occ.date) + "  ·  " + m.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                if (m.action == MissedAction.RESCHEDULE && m.toDate != null) {
                    FilledTonalButton(onClick = {
                        vm.reschedule(m.occ.task, m.occ.date, m.toDate) { r ->
                            r.onSuccess { newId -> if (m.useMinimum) vm.setPreferMinimum(newId, m.toDate, true) }
                            nav.showMessage(r.fold({ "Moved to ${DateFmt.short(m.toDate)}" }, { "Couldn't move it: ${it.message}" }))
                        }
                    }, modifier = Modifier.heightIn(min = 40.dp)) { Text("Move to ${DateFmt.short(m.toDate)}") }
                } else {
                    FilledTonalButton(
                        onClick = { vm.complete(m.occ.task, m.occ.date, CompletionType.SKIPPED, app.levelup2027.data.SkipReason.NOT_RELEVANT, "Let go after missing it") },
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) { Text("Let it go") }
                }
                TextButton(onClick = { onMove(m.occ) }) { Text("Pick a day") }
                if (m.action == MissedAction.RESCHEDULE) {
                    TextButton(onClick = { vm.complete(m.occ.task, m.occ.date, CompletionType.SKIPPED, app.levelup2027.data.SkipReason.NOT_RELEVANT, "Let go after missing it") }) {
                        Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (d.missed.size > 3) {
            HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showAll) "Show fewer" else "Show all ${d.missed.size}")
            }
        }
    }
}

@Composable
private fun Header(d: DayState, onAdjust: () -> Unit, onCustomise: () -> Unit) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(DateFmt.long(d.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onAdjust) { Icon(Icons.Outlined.AutoAwesome, "Adjust my plan", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = onCustomise) { Icon(Icons.Outlined.Tune, "Customise Today", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun FilterRow(d: DayState, selected: TodayFilter, onSelect: (TodayFilter) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TodayFilter.entries) { f ->
            val count = when (f) {
                TodayFilter.TODAY -> d.occurrences.size
                TodayFilter.OVERDUE -> d.overdue.size
                TodayFilter.COMPLETED -> d.occurrences.count { it.isDone }
                else -> d.occurrences.count { it.task.timeSlot == f.slot }
            }
            // Empty filters are just noise; hide them unless they're the current one.
            if (f != TodayFilter.TODAY && count == 0 && selected != f) return@items
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = {
                    Text(f.label)
                    Text("  $count", color = LocalContentColor.current.copy(alpha = 0.6f))
                },
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
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
}

@Composable
private fun SlotHeader(slot: TimeSlot, left: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = if (collapsed) "Show ${slot.label}" else "Hide ${slot.label}", onClick = onToggle)
            .heightIn(min = 36.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(slot.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (collapsed) Text("  ·  $left", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (collapsed) Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(filter: TodayFilter, d: DayState, allDone: Boolean = false) {
    val msg = when (filter) {
        TodayFilter.OVERDUE -> "Nothing overdue. Missed recurring tasks simply return on their next day."
        TodayFilter.COMPLETED -> "Nothing completed yet today. One minimum version is a real start."
        TodayFilter.TODAY -> if (allDone) "Everything is handled for today. Nice work." else "Nothing scheduled today. Rest is part of the plan."
        else -> "No ${filter.label.lowercase()} tasks today."
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
