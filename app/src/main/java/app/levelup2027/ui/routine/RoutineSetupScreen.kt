package app.levelup2027.ui.routine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.levelup2027.domain.PresetKind
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.components.HelpText

/**
 * "Your day": Daily Routine, then Extreme Productive and Lazy Day (skippable), then the full-screen
 * permission. When [required], Daily Routine needs at least one block and the screen can't be closed.
 */
@Composable
fun RoutineSetupScreen(vm: AppViewModel, required: Boolean, onFinished: () -> Unit, onClose: () -> Unit = onFinished) {
    val s by vm.settings.collectAsState()
    val routine = s?.routine ?: return
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = PresetKind.entries.size + 1

    BackHandler(enabled = page > 0 || !required) { if (page > 0) page-- else onClose() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your day · ${page + 1} of $pages", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (!required) IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (page < PresetKind.entries.size) {
                    val kind = PresetKind.entries[page]
                    Text(kind.label, style = MaterialTheme.typography.headlineSmall)
                    HelpText(
                        when (kind) {
                            PresetKind.DAILY -> "Your usual day, block by block. This is what every day starts on."
                            PresetKind.PRODUCTIVE -> "For days you want to push hard. You can set this up later."
                            PresetKind.LAZY -> "For low days. Choosing it also switches tasks to their minimum versions. You can set this up later."
                        },
                    )
                    val saved = routine.presets[kind].orEmpty()
                    PresetEditorPanel(
                        vm = vm,
                        kind = kind,
                        saved = saved,
                        dailyBlocks = routine.presets[PresetKind.DAILY].orEmpty(),
                        saveLabel = "Save and continue",
                        onSaved = { page++ },
                        requireBlocks = kind == PresetKind.DAILY && required,
                        describe = describeSlot(vm, kind),
                    )
                    if (kind == PresetKind.DAILY && required && saved.isEmpty()) {
                        HelpText("Add at least one block to continue.")
                    }
                    if (kind != PresetKind.DAILY || !required || saved.isNotEmpty()) {
                        TextButton(onClick = { page++ }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(if (kind == PresetKind.DAILY) "Continue without changes" else "Set up later")
                        }
                    }
                } else {
                    Text("Block check-ins", style = MaterialTheme.typography.headlineSmall)
                    HelpText("When a block ends, Ascend shows a check-in so you can tick off what you finished. It fills the screen when your phone is locked and shows as a banner while you're using it.")
                    CheckInPermissionRow()
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Finish") }
                }
            }
        }
    }
}

