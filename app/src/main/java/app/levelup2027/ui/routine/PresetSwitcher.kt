package app.levelup2027.ui.routine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.levelup2027.domain.Block
import app.levelup2027.domain.PresetKind
import app.levelup2027.domain.Routine
import app.levelup2027.ui.components.SectionCard
import kotlinx.coroutines.delay
import java.time.LocalTime

private val SHORT = mapOf(PresetKind.DAILY to "Daily", PresetKind.PRODUCTIVE to "Productive", PresetKind.LAZY to "Lazy")

/** Today's preset (switchable any time) and the block running now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetSwitcher(active: PresetKind, blocks: List<Block>, onSelect: (PresetKind) -> Unit) {
    val minute by produceState(LocalTime.now().let { it.hour * 60 + it.minute }) {
        while (true) {
            val now = LocalTime.now()
            value = now.hour * 60 + now.minute
            delay((60 - now.second) * 1000L)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PresetKind.entries.forEachIndexed { i, kind ->
                SegmentedButton(
                    selected = kind == active,
                    onClick = { if (kind != active) onSelect(kind) },
                    shape = SegmentedButtonDefaults.itemShape(i, PresetKind.entries.size),
                ) { Text(SHORT.getValue(kind), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        val current = Routine.currentBlock(blocks, minute)
        if (blocks.isNotEmpty()) {
            Text(
                if (current != null) "Now: ${current.label} · until ${Routine.format(current.endMinute)}" else "Free time",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        } else {
            Text(
                "${active.label} has no blocks yet. Add them in Settings → Routine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }
    }
}

/** One-time card for people who used Ascend before routines existed. */
@Composable
fun RoutinePromptCard(onSetUp: () -> Unit, onDismiss: () -> Unit) {
    SectionCard(title = "Set up your daily routine") {
        Text(
            "Lay out your day in blocks, like 5:00–7:00 Gym. When a block ends, Ascend asks what you got done.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Not now") }
            Row(Modifier.weight(1f)) {}
            TextButton(onClick = onSetUp) { Text("Set up") }
        }
    }
}
