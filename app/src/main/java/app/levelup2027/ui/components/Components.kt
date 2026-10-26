package app.levelup2027.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.levelup2027.data.CardLayout
import app.levelup2027.data.CategoryEntity
import app.levelup2027.data.CompletionType
import app.levelup2027.data.DefaultMode
import app.levelup2027.data.SkipReason
import app.levelup2027.domain.Occurrence
import app.levelup2027.ui.LocalCatalog
import app.levelup2027.ui.theme.OverlineStyle
import app.levelup2027.ui.theme.color
import app.levelup2027.ui.theme.icon

@Composable
fun CategoryIcon(category: CategoryEntity, size: Int = 32) {
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(category.color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, contentDescription = category.name, tint = category.color, modifier = Modifier.size((size * 0.56f).dp))
    }
}

@Composable
fun CategoryIcon(categoryId: String, size: Int = 32) = CategoryIcon(LocalCatalog.current.category(categoryId), size)

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = OverlineStyle, color = color, modifier = modifier)
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun CompletionBadge(type: CompletionType, reason: SkipReason?) {
    when (type) {
        CompletionType.IDEAL -> Pill("Ideal ✓", MaterialTheme.colorScheme.primary)
        CompletionType.MINIMUM -> Pill("Minimum ✓", MaterialTheme.colorScheme.secondary)
        CompletionType.SKIPPED -> Pill("Skipped" + (reason?.let { " · ${it.label}" } ?: ""), MaterialTheme.colorScheme.onSurfaceVariant)
        CompletionType.RESCHEDULED -> Pill("Moved", MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

fun firstBullet(s: String): String =
    s.lineSequence().map { it.trim().removePrefix("•").trim() }.firstOrNull { it.isNotEmpty() } ?: ""

/**
 * Task card. Compact by default: icon, title, one line of details, a one-tap "done" and an
 * expand arrow. Expanded, it shows the ideal and minimum versions and all actions. What appears
 * is set by [layout] (Today → Customise).
 * [mode] decides which version is emphasised; low-energy forces MINIMUM without changing the task.
 */
@Composable
fun TaskCard(
    occ: Occurrence,
    mode: DefaultMode,
    showDate: Boolean,
    onOpen: () -> Unit,
    onComplete: (CompletionType) -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onReschedule: (() -> Unit)? = null,
    layout: CardLayout = CardLayout(expandedByDefault = true),
    expanded: Boolean = true,
    onToggleExpand: (() -> Unit)? = null,
) {
    val t = occ.task
    val c = occ.completion
    val catalog = LocalCatalog.current
    val quickType = if (mode == DefaultMode.MINIMUM) CompletionType.MINIMUM else CompletionType.IDEAL
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.clickable(onClickLabel = "Open details", onClick = onOpen).padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
                if (layout.showIcon) { CategoryIcon(t.category, 28); Spacer(Modifier.width(10.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (c == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (layout.showDetails) {
                        Text(
                            buildList {
                                add(catalog.category(t.category).shortName)
                                if (t.goalId != null) add(catalog.goalName(t.goalId))
                                if (layout.showMinutes && c == null) add("${if (quickType == CompletionType.MINIMUM) t.estimatedMinimumMinutes else t.estimatedIdealMinutes} min")
                                t.exactTime?.let { add(it) }
                                if (showDate) add(app.levelup2027.domain.DateFmt.short(occ.date))
                            }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (c != null) {
                    CompletionBadge(c.completionType, c.skipReason)
                    IconButton(onClick = onUndo, modifier = Modifier.semantics { contentDescription = "Undo ${t.title}" }) {
                        Icon(Icons.Outlined.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    if (!expanded && layout.showQuickDone) {
                        IconButton(
                            onClick = { onComplete(quickType) },
                            modifier = Modifier.semantics { contentDescription = "Mark ${t.title} done (${if (quickType == CompletionType.MINIMUM) "minimum" else "ideal"})" },
                        ) {
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).border(2.dp,
                                    if (quickType == CompletionType.MINIMUM) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {}
                        }
                    }
                    if (onToggleExpand != null) {
                        IconButton(onClick = onToggleExpand) {
                            Icon(
                                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (expanded) "Show less" else "Show more",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (c == null && expanded) {
                val showIdeal = layout.showIdeal && mode != DefaultMode.MINIMUM
                val showMin = layout.showMinimum && mode != DefaultMode.IDEAL
                if (showIdeal || showMin) {
                    Column(Modifier.padding(start = if (layout.showIcon) 38.dp else 0.dp, end = 8.dp, top = 2.dp)) {
                        if (showIdeal) VersionLine("Ideal", firstBullet(t.idealDescription), if (layout.showMinutes) t.estimatedIdealMinutes else 0, MaterialTheme.colorScheme.primary)
                        if (showMin) VersionLine("Min", firstBullet(t.minimumDescription), if (layout.showMinutes) t.estimatedMinimumMinutes else 0, MaterialTheme.colorScheme.secondary)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = if (layout.showIcon) 34.dp else 0.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val pad = PaddingValues(horizontal = 12.dp)
                    when (mode) {
                        DefaultMode.DECIDE -> {
                            FilledTonalButton(onClick = { onComplete(CompletionType.IDEAL) }, contentPadding = pad, modifier = Modifier.heightIn(min = 40.dp)) {
                                Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Ideal")
                            }
                            FilledTonalButton(
                                onClick = { onComplete(CompletionType.MINIMUM) }, contentPadding = pad, modifier = Modifier.heightIn(min = 40.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            ) {
                                Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Minimum")
                            }
                        }
                        DefaultMode.IDEAL -> {
                            Button(onClick = { onComplete(CompletionType.IDEAL) }, contentPadding = pad, modifier = Modifier.heightIn(min = 40.dp)) {
                                Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Done")
                            }
                            TextButton(onClick = { onComplete(CompletionType.MINIMUM) }, modifier = Modifier.heightIn(min = 40.dp)) { Text("Did minimum") }
                        }
                        DefaultMode.MINIMUM -> {
                            Button(
                                onClick = { onComplete(CompletionType.MINIMUM) }, contentPadding = pad, modifier = Modifier.heightIn(min = 40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                            ) {
                                Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Done (min)")
                            }
                            TextButton(onClick = { onComplete(CompletionType.IDEAL) }, modifier = Modifier.heightIn(min = 40.dp)) { Text("Did ideal") }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (onReschedule != null && layout.showMove) {
                        IconButton(onClick = onReschedule, modifier = Modifier.semantics { contentDescription = "Move ${t.title} to another day" }) {
                            Icon(Icons.Outlined.EventRepeat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (layout.showSkip) {
                        TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 40.dp)) {
                            Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** A card whose body folds away; when folded it shows [summary] on the header line. */
@Composable
fun CollapsibleCard(
    title: String,
    summary: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = if (collapsed) "Expand" else "Collapse", onClick = onToggle)
                .padding(start = 14.dp, end = 4.dp).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Overline(title, color = titleColor)
            if (collapsed && summary.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(summary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else Spacer(Modifier.weight(1f))
            Icon(
                if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = if (collapsed) "Expand $title" else "Collapse $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
        if (!collapsed) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) { content() }
    }
}

@Composable
private fun VersionLine(label: String, text: String, minutes: Int, color: Color) {
    Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
        Text(
            label.uppercase(), style = OverlineStyle, color = color,
            modifier = Modifier.width(44.dp).padding(top = 2.dp),
        )
        Text(
            text + if (minutes > 0) " · ${minutes}m" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Skipping is always allowed and never penalised; a reason is optional. */
@Composable
fun SkipDialog(taskTitle: String, isRecurring: Boolean, onDismiss: () -> Unit, onSkip: (SkipReason?, String) -> Unit) {
    var reason by remember { mutableStateOf<SkipReason?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skip today?") },
        text = {
            Column {
                Text(
                    if (isRecurring) "That's okay. \"$taskTitle\" will come back on its next scheduled day."
                    else "That's okay. \"$taskTitle\" stays in your task history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Overline("Reason (optional)")
                SkipReason.entries.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .selectable(selected = reason == r, role = Role.RadioButton, onClick = { reason = if (reason == r) null else r }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(r.label)
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSkip(reason, note.trim()) }) { Text("Skip today") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) { Column(Modifier.padding(14.dp)) { content() } }
}

@Composable
fun VersionBox(label: String, body: String, minutes: Int, color: Color) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.07f))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(label, color = color)
            Spacer(Modifier.weight(1f))
            Text(if (minutes > 0) "~$minutes min" else "rest", style = MaterialTheme.typography.labelMedium, color = color)
        }
        Spacer(Modifier.size(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Move one occurrence to another day. Quick picks for the next few days, or any date up to 60 days out. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RescheduleDialog(title: String, from: java.time.LocalDate, today: java.time.LocalDate, onDismiss: () -> Unit, onPick: (java.time.LocalDate) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    val options = (0L..6L).map { today.plusDays(it) }.filter { it != from }.take(5)
    if (custom) {
        val zone = java.time.ZoneOffset.UTC
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = options.first().atStartOfDay(zone).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(zone).toLocalDate()
                    return !d.isBefore(today) && !d.isAfter(today.plusDays(60)) && d != from
                }
            },
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }
                }) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) { androidx.compose.material3.DatePicker(state) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to another day") },
        text = {
            Column {
                Text("\"$title\" will be marked as moved on ${app.levelup2027.domain.DateFmt.short(from)} and appear on the day you pick. Nothing else changes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                options.forEach { d ->
                    TextButton(onClick = { onPick(d) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (d == today) "Today" else if (d == today.plusDays(1)) "Tomorrow" else app.levelup2027.domain.DateFmt.widget(d))
                    }
                }
                TextButton(onClick = { custom = true }, modifier = Modifier.fillMaxWidth()) { Text("Pick a date…") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
