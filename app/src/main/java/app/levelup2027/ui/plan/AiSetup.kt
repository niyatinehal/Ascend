package app.levelup2027.ui.plan

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.levelup2027.ai.AiAvailability
import app.levelup2027.ai.ModelPrices
import app.levelup2027.data.AiMode
import app.levelup2027.data.AiUsage
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.LocalNav
import app.levelup2027.ui.components.Overline
import app.levelup2027.ui.tasks.ChipChoice
import app.levelup2027.ui.tasks.SwitchRow
import kotlinx.coroutines.launch

/** Shown once before the first AI request: what is sent, to whom, and what isn't. */
@Composable
fun AiConsentDialog(vm: AppViewModel, onDismiss: () -> Unit, onAccepted: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val mode = settings?.ai?.mode ?: AiMode.SERVER
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before using AI planning") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "To build and adjust your plan, Ascend sends some of your information to Anthropic's Claude AI" +
                        if (mode == AiMode.OWN_KEY) ", directly from this phone using your API key." else " through the Ascend AI server, which holds the API key and counts requests.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Overline("Sent when you use an AI feature")
                Text("• What you type in Build my plan or Adjust my plan, and your answers\n• Your goal titles, deadlines and priorities\n• Task titles, times and durations for the next 7 days, and your habits\n• Counts of completed, minimum and skipped tasks for the last 7 days",
                    style = MaterialTheme.typography.bodySmall)
                Overline("Never sent")
                Text("• Notes, progress entries, weekly reviews, backups\n• Your full completion history\n• Anything when you just tick tasks, use the widget or reminders: those work entirely on the phone",
                    style = MaterialTheme.typography.bodySmall)
                Text(
                    "Your plan itself is stored only on this phone. The AI server keeps request counts per device, not content. You can turn AI off or delete your data at any time in Settings.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.updateSettings { it.copy(ai = it.ai.copy(consentGiven = true)) }
                onAccepted()
            }) { Text("I agree") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

/** Explains why an AI call couldn't happen, with the next step. The user's input stays where it was. */
@Composable
fun AiBlockCard(block: AiBlock, onConsent: () -> Unit, onSetup: () -> Unit, onRetry: (() -> Unit)?, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (block) {
                        AiBlock.NeedsConsent -> "Please review what's shared with the AI first."
                        AiBlock.NeedsSetup -> "AI planning isn't set up on this phone yet."
                        is AiBlock.Failed -> block.message
                    },
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                )
            }
            if (block == AiBlock.NeedsSetup) {
                Text("You can add your own Anthropic API key in Settings → AI, or set up manually without AI.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (block is AiBlock.Failed) {
                Text("Nothing you typed was lost. Your existing plan keeps working without AI.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Close") }
                when (block) {
                    AiBlock.NeedsConsent -> TextButton(onClick = onConsent) { Text("Review") }
                    AiBlock.NeedsSetup -> TextButton(onClick = onSetup) { Text("Set up AI") }
                    is AiBlock.Failed -> if (block.retryable && onRetry != null) TextButton(onClick = onRetry) { Text("Try again") }
                }
            }
        }
    }
}

/** Returns a function that starts system speech recognition, or null when the phone has none. */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe what you want to work toward")
    }
    val available = remember { intent.resolveActivity(context.packageManager) != null }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
        }
    }
    return if (available) ({ launcher.launch(intent) }) else null
}

@Composable
fun MicButton(onText: (String) -> Unit) {
    val start = rememberVoiceInput(onText) ?: return
    IconButton(onClick = start) { Icon(Icons.Outlined.Mic, contentDescription = "Speak instead of typing") }
}

/** Settings → AI. */
@Composable
fun AiSettingsSection(vm: AppViewModel) {
    val settingsState by vm.settings.collectAsState()
    val s = settingsState ?: return
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var availability by remember { mutableStateOf<AiAvailability?>(null) }
    var usage by remember { mutableStateOf<List<AiUsage>>(emptyList()) }
    var serverUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.ai, tick) {
        availability = vm.ai.availability()
        usage = vm.ai.usageThisMonth()
        serverUrl = vm.ai.serverUrl()
    }
    var keyInput by remember { mutableStateOf("") }
    var showConsent by remember { mutableStateOf(false) }
    var urlInput by remember(s.ai.serverUrl) { mutableStateOf(s.ai.serverUrl) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline("AI planning")
        Text(
            when (availability) {
                AiAvailability.READY -> "Ready."
                AiAvailability.NEEDS_CONSENT -> "Set up. You'll be asked to review what's shared before the first request."
                AiAvailability.NOT_CONFIGURED -> if (s.ai.mode == AiMode.OWN_KEY) "Add your API key below." else "This build has no AI server address. Use your own API key, or enter a server address."
                AiAvailability.OFF -> "Off. Everything else in the app works without AI."
                null -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        ChipChoice(AiMode.entries, s.ai.mode, { it.label }) { m -> vm.updateSettings { it.copy(ai = it.ai.copy(mode = m)) } }

        when (s.ai.mode) {
            AiMode.OWN_KEY -> {
                Text(
                    "Requests go straight from this phone to Anthropic and are billed to your Anthropic account. Keys start with sk-ant-. The key is stored encrypted on this phone and isn't included in backups.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (vm.ai.hasApiKey().also { tick }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("API key saved", Modifier.weight(1f))
                        TextButton(onClick = { vm.ai.setApiKey(null); tick++ }) { Text("Remove") }
                    }
                } else {
                    OutlinedTextField(
                        keyInput, { keyInput = it }, label = { Text("Anthropic API key") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = {
                            if (!keyInput.trim().startsWith("sk-ant-")) nav.showMessage("That doesn't look like an Anthropic API key.")
                            else { vm.ai.setApiKey(keyInput); keyInput = ""; tick++; nav.showMessage("API key saved") }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Save key") }
                }
                var limit by remember(s.ai.monthlyRequestLimit) { mutableStateOf(s.ai.monthlyRequestLimit.toString()) }
                OutlinedTextField(
                    limit, { v -> limit = v.filter(Char::isDigit).take(4); limit.toIntOrNull()?.let { n -> if (n > 0) vm.updateSettings { it.copy(ai = it.ai.copy(monthlyRequestLimit = n)) } } },
                    label = { Text("Monthly request limit (safety cap)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
            AiMode.SERVER -> {
                Text(
                    "Uses the Ascend AI server, which keeps the API key off your phone and limits how many requests each device can make." +
                        (serverUrl?.takeIf { it.isNotBlank() }?.let { "\nServer: $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    urlInput, { urlInput = it.trim() }, label = { Text("Server address (optional override)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                )
                if (urlInput != s.ai.serverUrl) {
                    OutlinedButton(onClick = {
                        if (urlInput.isNotBlank() && !urlInput.startsWith("https://")) nav.showMessage("The server address must start with https://")
                        else vm.updateSettings { it.copy(ai = it.ai.copy(serverUrl = urlInput)) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save server address") }
                }
            }
            AiMode.OFF -> Unit
        }

        if (s.ai.mode != AiMode.OFF) {
            SwitchRow(
                "Apply small changes without asking",
                "When you ask for a lighter day or to move one task, apply it straight away. Goals, deadlines and habits always need your OK.",
                s.ai.autoApplySimple,
            ) { on -> vm.updateSettings { it.copy(ai = it.ai.copy(autoApplySimple = on)) } }

            val ok = usage.filter { it.success }
            val inTok = ok.sumOf { it.inputTokens }
            val outTok = ok.sumOf { it.outputTokens }
            val cost = ok.sumOf { ModelPrices.estimateUsd(it.model, it.inputTokens, it.outputTokens) }
            Text(
                "This month: ${usage.size} AI request${if (usage.size == 1) "" else "s"} (${usage.size - ok.size} failed) · ${"%,d".format(inTok)} input / ${"%,d".format(outTok)} output tokens" +
                    if (s.ai.mode == AiMode.OWN_KEY && ok.isNotEmpty()) " · about $${"%.2f".format(cost)} at list prices" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.ai.consentGiven) "You agreed to share planning data with the AI." else "You haven't reviewed what's shared yet.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (s.ai.consentGiven) TextButton(onClick = { vm.updateSettings { it.copy(ai = it.ai.copy(consentGiven = false)) } }) { Text("Withdraw") }
                else TextButton(onClick = { showConsent = true }) { Text("Review") }
            }
        }
    }
    if (showConsent) AiConsentDialog(vm, onDismiss = { showConsent = false }) { showConsent = false; scope.launch { tick++ } }
}
