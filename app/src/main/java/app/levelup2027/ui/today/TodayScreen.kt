package app.levelup2027.ui.today

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.levelup2027.data.AppSettings
import app.levelup2027.data.Campaign
import app.levelup2027.data.CompletionType
import app.levelup2027.data.DefaultMode
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.DateFmt
import app.levelup2027.domain.Dates
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

    val mode = if (d.lowEnergy) DefaultMode.MINIMUM else s.defaultMode
    val list: List<Occurrence> = when (filter) {
        TodayFilter.TODAY -> d.occurrences
        TodayFilter.OVERDUE -> d.overdue
        TodayFilter.COMPLETED -> d.occurrences.filter { it.isDone }
        else -> d.occurrences.filter { it.task.timeSlot == filter.slot }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Header(d) }
        item { ProgressCard(d, onLowEnergy = { vm.setLowEnergy(!d.lowEnergy) }) }
        if (s.notificationPromptEligible && !s.notificationPromptDismissed) {
            item { NotificationPrompt(vm, s) }
        }
        item { FilterRow(d, filter, onSelect = { vm.todayFilter.value = it }) }
        item { CountLine(filter, list) }

        if (list.isEmpty()) {
            item { EmptyState(filter, d) }
        } else if (filter == TodayFilter.TODAY) {
            TimeSlot.entries.forEach { slot ->
                val group = list.filter { it.task.timeSlot == slot }
                if (group.isNotEmpty()) {
                    item(key = "h-$slot") { SlotHeader(slot, group) }
                    items(group, key = { "${it.task.id}@${it.date}" }) { occ -> Card(occ, mode, false, vm) { skipping = it } }
                }
            }
        } else {
            items(list, key = { "${it.task.id}@${it.date}" }) { occ ->
                Card(occ, mode, showDate = filter == TodayFilter.OVERDUE, vm) { skipping = it }
            }
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

@Composable
private fun Card(occ: Occurrence, mode: DefaultMode, showDate: Boolean, vm: AppViewModel, onSkip: (Occurrence) -> Unit) {
    val nav = LocalNav.current
    TaskCard(
        occ = occ,
        mode = mode,
        showDate = showDate,
        onOpen = { nav.openTask(occ.task.id, occ.date) },
        onComplete = { type -> vm.complete(occ.task, occ.date, type) },
        onSkip = { onSkip(occ) },
        onUndo = { vm.undo(occ.task.id, occ.date) },
    )
}

@Composable
private fun Header(d: DayState) {
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
            ProgressLine(Xp.levelFraction(d.xp), Modifier.width(96.dp).height(3.dp))
        }
    }
    val campaignText = when {
        d.date.isBefore(Campaign.START) -> "Campaign starts ${DateFmt.medium(Campaign.START)}"
        d.date.isAfter(Campaign.END) -> "Campaign complete. ${d.daysShowedUp} days showed up."
        else -> {
            val cp = if (!d.date.isAfter(Campaign.FIRST_CHECKPOINT)) Campaign.FIRST_CHECKPOINT to "first checkpoint"
            else if (!d.date.isAfter(Campaign.CAREER_CHECKPOINT)) Campaign.CAREER_CHECKPOINT to "career checkpoint"
            else Campaign.END to "campaign end"
            "Day ${Dates.campaignDay(d.date)} of ${Dates.campaignLength()} · ${Dates.daysUntil(d.date, cp.first)} days to ${cp.second}"
        }
    }
    Text(campaignText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ProgressCard(d: DayState, onLowEnergy: () -> Unit) {
    val p = d.progress
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
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
        TodayFilter.OVERDUE -> "${list.size} one-time task${if (list.size == 1) "" else "s"} from earlier days. Do them late, or skip with no penalty. Recurring tasks never pile up here."
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
private fun SlotHeader(slot: TimeSlot, group: List<Occurrence>) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(slot.icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Overline(slot.label)
        Spacer(Modifier.weight(1f))
        Text("${group.count { it.isDone }}/${group.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(filter: TodayFilter, d: DayState) {
    val msg = when (filter) {
        TodayFilter.OVERDUE -> "Nothing overdue. Missed recurring tasks simply return on their next day."
        TodayFilter.COMPLETED -> "Nothing completed yet today. One minimum version is a real start."
        TodayFilter.TODAY -> if (d.date.isBefore(Campaign.START)) "Your plan starts on ${DateFmt.medium(Campaign.START)}." else "Nothing scheduled today. Rest is part of the plan."
        else -> "No ${filter.label.lowercase()} tasks today."
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
