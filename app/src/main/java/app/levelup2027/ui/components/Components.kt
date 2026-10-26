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
import app.levelup2027.data.Category
import app.levelup2027.data.CompletionType
import app.levelup2027.data.DefaultMode
import app.levelup2027.data.GoalIds
import app.levelup2027.data.SkipReason
import app.levelup2027.domain.Occurrence
import app.levelup2027.ui.theme.OverlineStyle
import app.levelup2027.ui.theme.color
import app.levelup2027.ui.theme.icon

@Composable
fun CategoryIcon(category: Category, size: Int = 32) {
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(category.color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, contentDescription = category.label, tint = category.color, modifier = Modifier.size((size * 0.56f).dp))
    }
}

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
 * Compact task card. Shows what to do without opening it; completes in one tap.
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
) {
    val t = occ.task
    val c = occ.completion
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.clickable(onClickLabel = "Open details", onClick = onOpen).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = if (c == null) 6.dp else 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(t.category, 30)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (c == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(t.category.shortLabel); append(" · "); append(GoalIds.shortName(t.goalId))
                            if (showDate) { append(" · "); append(app.levelup2027.domain.DateFmt.short(occ.date)) }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (c != null) {
                    CompletionBadge(c.completionType, c.skipReason)
                    IconButton(onClick = onUndo, modifier = Modifier.semantics { contentDescription = "Undo ${t.title}" }) {
                        Icon(Icons.Outlined.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (c == null) {
                Spacer(Modifier.size(6.dp))
                Column(Modifier.padding(start = 40.dp, end = 8.dp)) {
                    if (mode != DefaultMode.MINIMUM) VersionLine("Ideal", firstBullet(t.idealDescription), t.estimatedIdealMinutes, MaterialTheme.colorScheme.primary)
                    if (mode != DefaultMode.IDEAL) VersionLine("Min", firstBullet(t.minimumDescription), t.estimatedMinimumMinutes, MaterialTheme.colorScheme.secondary)
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 36.dp, top = 4.dp),
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
                    TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 40.dp)) {
                        Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
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
