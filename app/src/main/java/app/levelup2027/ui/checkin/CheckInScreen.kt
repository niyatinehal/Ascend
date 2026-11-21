package app.levelup2027.ui.checkin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.levelup2027.domain.Block
import app.levelup2027.domain.Occurrence
import app.levelup2027.domain.Routine
import app.levelup2027.ui.components.firstBullet
import java.time.LocalDate

@Composable
fun CheckInScreen(
    date: LocalDate,
    block: Block,
    loading: Boolean,
    occurrences: List<Occurrence>,
    showMinimum: (taskId: String) -> Boolean,
    onToggle: (taskId: String) -> Unit,
    onDone: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("${Routine.format(block.startMinute)}–${Routine.format(block.endMinute)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(block.label, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            val allDone = occurrences.isNotEmpty() && occurrences.all { it.isDone }
            Text(
                when {
                    loading -> ""
                    occurrences.isEmpty() -> "Nothing on your list today."
                    allDone -> "All of today's tasks are done."
                    else -> "Block done. Tick off what you finished."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (allDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(occurrences, key = { it.task.id }) { o -> CheckInRow(o, showMinimum(o.task.id)) { onToggle(o.task.id) } }
                    }
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Done") }
        }
    }
}

@Composable
private fun CheckInRow(o: Occurrence, minimum: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (o.isDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (o.isDone) "Done" else "Not done",
            tint = if (o.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                o.task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (o.isDone) TextDecoration.LineThrough else null,
                color = if (o.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val version = if (minimum) o.task.minimumDescription else o.task.idealDescription
            if (!o.isDone && version.isNotBlank()) {
                Text(firstBullet(version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
