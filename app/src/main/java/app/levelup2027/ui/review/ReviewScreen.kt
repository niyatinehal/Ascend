package app.levelup2027.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.levelup2027.data.Campaign
import app.levelup2027.data.Repository
import app.levelup2027.data.WeeklyReview
import app.levelup2027.domain.DateFmt
import app.levelup2027.domain.Dates
import app.levelup2027.domain.WeeklyStatsCalculator
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.LocalNav
import app.levelup2027.ui.components.Overline
import app.levelup2027.ui.components.SectionCard
import java.time.DayOfWeek

@Composable
fun ReviewScreen(vm: AppViewModel) {
    val today by vm.today.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val completions by vm.completions.collectAsState()
    val settings by vm.settings.collectAsState()
    val reviews by vm.reviews.collectAsState()
    val progress by vm.progress.collectAsState()
    val nav = LocalNav.current

    val currentWeek = Dates.weekStart(today)
    val firstWeek = Dates.weekStart(Campaign.START)
    var weekStart by rememberSaveable { mutableStateOf(currentWeek) }
    val stats = remember(weekStart, today, tasks, completions, settings) {
        WeeklyStatsCalculator.compute(weekStart, today, tasks.orEmpty(), completions, settings?.enabledCategories)
    }
    val saved = reviews.firstOrNull { it.weekStartDate == weekStart }
    var wins by remember(weekStart, saved) { mutableStateOf(saved?.wins ?: "") }
    var blockers by remember(weekStart, saved) { mutableStateOf(saved?.blockers ?: "") }
    var adjustments by remember(weekStart, saved) { mutableStateOf(saved?.adjustments ?: "") }
    var priority by remember(weekStart, saved) { mutableStateOf(saved?.nextPriority ?: "") }
    var notes by remember(weekStart, saved) { mutableStateOf(saved?.notes ?: "") }
    LaunchedEffect(currentWeek) { if (weekStart.isAfter(currentWeek)) weekStart = currentWeek }

    val weekEndDate = weekStart.plusDays(6)
    val latest = Repository.latestValues(progress.filter { !it.date.isAfter(weekEndDate) })
    val beforeWeek = Repository.latestValues(progress.filter { it.date.isBefore(weekStart) })
    val p = stats.progress

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Weekly review", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }, enabled = weekStart.isAfter(firstWeek)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous week")
                }
                Text(
                    "${DateFmt.short(weekStart)} – ${DateFmt.short(stats.weekEnd)}" + if (weekStart == currentWeek) " (this week)" else "",
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }, enabled = weekStart.isBefore(currentWeek)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next week")
                }
            }
            if (weekStart == currentWeek) {
                Text(
                    if (today.dayOfWeek == DayOfWeek.SUNDAY) "It's Sunday: a good time to look back. 15 minutes is enough."
                    else "The review is planned for Sunday evening. Stats so far count only days up to today.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${p.done}/${p.scheduled}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("tasks completed · ${(p.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                IdealMinBar(p.ideal, p.minimum, p.skipped, p.open)
                Spacer(Modifier.height(6.dp))
                Text("${p.ideal} ideal · ${p.minimum} minimum · ${p.skipped} skipped · ${p.open} not marked",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard {
                Stat("Gym sessions", "${stats.workouts} of ${stats.workoutsPlanned} planned")
                Stat("Technical-learning sessions", "${stats.techSessions} of ${stats.techPlanned}")
                Stat("Business actions", "${stats.businessActions} of ${stats.businessPlanned}" +
                    (latest["conversations"]?.let { " · ${it.value.toInt()} conversations total" } ?: "") +
                    (latest["mrr"]?.let { " · actual MRR ₹%,d".format(it.value.toLong()) } ?: ""))
                val fund = latest["fund_balance"]?.value
                val fundBefore = beforeWeek["fund_balance"]?.value
                val target = latest["fund_target"]?.value
                Stat(
                    "Phone fund",
                    when {
                        fund == null -> "Not recorded yet (log it in Goals)"
                        else -> "₹%,d".format(fund.toLong()) + (target?.let { " of ₹%,d".format(it.toLong()) } ?: "") +
                            (fundBefore?.let { val d = fund - it; if (d != 0.0) " · %s₹%,d this week".format(if (d > 0) "+" else "−", kotlin.math.abs(d).toLong()) else "" } ?: "")
                    },
                )
                Stat("Finance check-ins", "${stats.financeActions}")
            }
        }
        if (stats.frequentlySkipped.isNotEmpty()) {
            item {
                SectionCard {
                    Overline("Skipped this week")
                    stats.frequentlySkipped.take(5).forEach {
                        Text("• ${it.title} ×${it.count}" + (it.topReason?.let { r -> " (${r.label.lowercase()})" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("A pattern, not a failure. Consider a smaller minimum or a different day.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionCard {
                Overline("Patterns")
                stats.observations.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        item {
            SectionCard {
                Overline("Reflect")
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(wins, { wins = it }, label = { Text("What went well?") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(blockers, { blockers = it }, label = { Text("What blocked me?") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(adjustments, { adjustments = it }, label = { Text("What should I change next week?") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(priority, { priority = it }, label = { Text("Next week's main priority") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(notes, { notes = it }, label = { Text("Other notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(
                        onClick = {
                            vm.saveReview(WeeklyReview(weekStartDate = weekStart, wins = wins.trim(), blockers = blockers.trim(),
                                adjustments = adjustments.trim(), nextPriority = priority.trim(), notes = notes.trim()))
                            nav.showMessage("Review saved")
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(if (saved == null) "Save review" else "Update review") }
                    saved?.let {
                        Text("Last saved ${java.time.Instant.ofEpochMilli(it.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let(DateFmt::medium)}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val past = reviews.filter { it.weekStartDate != weekStart }
        if (past.isNotEmpty()) {
            item { Overline("Saved reviews", Modifier.padding(top = 8.dp)) }
            items(past, key = { it.id }) { r ->
                PastReview(r) { weekStart = r.weekStartDate }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IdealMinBar(ideal: Int, minimum: Int, skipped: Int, open: Int) {
    val total = (ideal + minimum + skipped + open).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (ideal > 0) Box(Modifier.weight(ideal.toFloat() / total).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        if (minimum > 0) Box(Modifier.weight(minimum.toFloat() / total).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
        val rest = skipped + open
        if (rest > 0) Spacer(Modifier.weight(rest.toFloat() / total))
    }
}

@Composable
private fun PastReview(r: WeeklyReview, onOpen: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Week of ${DateFmt.medium(r.weekStartDate)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpen) { Text("Open") }
        }
        if (r.nextPriority.isNotBlank()) Text("Priority: ${r.nextPriority}", style = MaterialTheme.typography.bodySmall)
        if (r.wins.isNotBlank()) Text("Went well: ${r.wins}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
    }
}
