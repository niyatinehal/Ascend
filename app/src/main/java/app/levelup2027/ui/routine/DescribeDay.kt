package app.levelup2027.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.levelup2027.ai.AiAvailability
import app.levelup2027.ai.AiError
import app.levelup2027.domain.Block
import app.levelup2027.domain.PresetKind
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.components.HelpText
import app.levelup2027.ui.plan.AiConsentDialog
import app.levelup2027.ui.plan.MicButton
import kotlinx.coroutines.launch

/** The "describe your day" slot for [PresetEditorPanel]. Hidden when AI is off or not set up. */
fun describeSlot(vm: AppViewModel, kind: PresetKind): (@Composable (setBlocks: (List<Block>, List<String>) -> Unit) -> Unit) =
    { setBlocks -> DescribeDay(vm, kind, setBlocks) }

@Composable
private fun DescribeDay(vm: AppViewModel, kind: PresetKind, setBlocks: (List<Block>, List<String>) -> Unit) {
    var availability by remember { mutableStateOf<AiAvailability?>(null) }
    LaunchedEffect(Unit) { availability = vm.ai.availability() }
    val a = availability
    if (a == null || a == AiAvailability.OFF || a == AiAvailability.NOT_CONFIGURED) return

    var open by rememberSaveable(kind) { mutableStateOf(false) }
    var text by rememberSaveable(kind) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askConsent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun draft() {
        busy = true; error = null
        scope.launch {
            runCatching { vm.ai.draftRoutine(kind, text) }
                .onSuccess { r ->
                    if (r.blocks.isEmpty() && r.problems.isEmpty()) error = "Couldn't find any blocks in that. Try naming times, like \"gym 5 to 7\"."
                    else setBlocks(r.blocks, r.problems)
                }
                .onFailure { e ->
                    if (e is AiError.NoConsent) askConsent = true
                    else error = (e.message ?: "Something went wrong.") + " You can still add blocks by hand."
                }
            busy = false
        }
    }

    if (!open) {
        OutlinedButton(onClick = { open = true }) { Text("Describe it instead") }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(2000) },
                placeholder = { Text("Gym 5 to 7, breakfast till 8, office 9 to 6, reading 9 to 10 pm") },
                minLines = 2,
                modifier = Modifier.weight(1f),
            )
            MicButton { spoken -> text = (text.trimEnd() + " " + spoken).trim() }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { draft() }, enabled = text.isNotBlank() && !busy) { Text("Make blocks") }
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        HelpText("The blocks replace the list below. Check them, then save.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth()) }
    }

    if (askConsent) AiConsentDialog(vm, onDismiss = { askConsent = false }, onAccepted = { askConsent = false; draft() })
}
