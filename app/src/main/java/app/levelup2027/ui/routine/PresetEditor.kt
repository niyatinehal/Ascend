package app.levelup2027.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.levelup2027.domain.Block
import app.levelup2027.domain.PresetKind
import app.levelup2027.domain.Routine
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.components.HelpText
import kotlinx.coroutines.launch

/**
 * Edits one preset's blocks. Stateless: the caller holds [blocks]. [describe] is an optional slot
 * above the list (the AI "describe your day" box) that can replace [blocks] through [onBlocksChange].
 */
@Composable
fun PresetEditor(
    blocks: List<Block>,
    onBlocksChange: (List<Block>) -> Unit,
    copyFrom: List<Block>?,
    errors: List<String>,
    describe: (@Composable () -> Unit)? = null,
) {
    var picking by remember { mutableStateOf<Pair<Int, Boolean>?>(null) } // (index, isStart)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        describe?.invoke()
        if (blocks.isEmpty()) {
            HelpText("No blocks yet. Add your first one, like 5:00–7:00 Gym.")
            if (!copyFrom.isNullOrEmpty()) {
                OutlinedButton(onClick = { onBlocksChange(copyFrom) }) { Text("Start from a copy of ${PresetKind.DAILY.label}") }
            }
        }
        blocks.forEachIndexed { i, b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { picking = i to true }) { Text(Routine.format(b.startMinute)) }
                Text("–")
                TextButton(onClick = { picking = i to false }) { Text(if (b.endMinute == 1440) "24:00" else Routine.format(b.endMinute)) }
                OutlinedTextField(
                    value = b.label,
                    onValueChange = { v -> onBlocksChange(blocks.toMutableList().also { it[i] = b.copy(label = v.take(Routine.MAX_LABEL)) }) },
                    placeholder = { Text("What you'll do") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onBlocksChange(blocks.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove block")
                }
            }
        }
        TextButton(onClick = {
            val start = (blocks.maxOfOrNull { it.endMinute } ?: 360).coerceAtMost(1380)
            onBlocksChange(blocks + Block(start, (start + 60).coerceAtMost(1440), ""))
        }) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Add block")
        }
        errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    picking?.let { (i, isStart) ->
        val b = blocks.getOrNull(i) ?: run { picking = null; return@let }
        val minute = if (isStart) b.startMinute else b.endMinute % 1440
        BlockTimeDialog(
            title = if (isStart) "Starts at" else "Ends at",
            minute = minute,
            onDismiss = { picking = null },
        ) { picked ->
            // An end of 0:00 means midnight at the end of the day.
            val updated = if (isStart) b.copy(startMinute = picked) else b.copy(endMinute = if (picked == 0) 1440 else picked)
            onBlocksChange(blocks.toMutableList().also { it[i] = updated })
            picking = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockTimeDialog(title: String, minute: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onSave(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The editor plus its Save button, wired to the view model. Used in Settings and in setup.
 * [describe] receives the current blocks setter so an AI draft can fill the editor.
 */
@Composable
fun PresetEditorPanel(
    vm: AppViewModel,
    kind: PresetKind,
    saved: List<Block>,
    dailyBlocks: List<Block>,
    saveLabel: String = "Save",
    onSaved: () -> Unit,
    requireBlocks: Boolean = false,
    describe: (@Composable (setBlocks: (List<Block>, List<String>) -> Unit) -> Unit)? = null,
) {
    var blocks by remember(kind) { mutableStateOf(saved) }
    var errors by remember(kind) { mutableStateOf(emptyList<String>()) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        PresetEditor(
            blocks = blocks,
            onBlocksChange = { blocks = it; errors = emptyList() },
            copyFrom = if (kind == PresetKind.DAILY) null else dailyBlocks,
            errors = errors,
            describe = describe?.let { d -> { d { drafted, problems -> blocks = drafted; errors = problems } } },
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    if (requireBlocks && blocks.isEmpty()) {
                        errors = listOf("Add at least one block.")
                        return@launch
                    }
                    errors = vm.savePreset(kind, blocks)
                    if (errors.isEmpty()) onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) { Text(saveLabel) }
    }
}
