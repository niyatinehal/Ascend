package app.levelup2027.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.levelup2027.ai.PlanValidator
import app.levelup2027.domain.DateFmt
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.components.Overline
import app.levelup2027.ui.components.Pill
import app.levelup2027.ui.components.SectionCard
import java.time.LocalDate

private val EXAMPLES = listOf(
    "I have only 20 minutes tonight.",
    "I'm exhausted. Give me a lighter day.",
    "I missed the last three days.",
    "Move my workout to Saturday.",
    "My work schedule changed.",
    "I have exams next week.",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdjustSheet(vm: AppViewModel, adjust: AdjustViewModel, onDismiss: () -> Unit, onOpenAiSettings: () -> Unit) {
    val state by adjust.state.collectAsState()
    var showConsent by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = { adjust.reset(); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Adjust my plan", style = MaterialTheme.typography.titleLarge)
            Text("Tell me what changed. I'll suggest specific changes, and nothing happens until you approve them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            state.turns.forEach { t ->
                val mine = t.role == "user"
                Text(
                    t.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mine) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(start = if (mine) 32.dp else 0.dp, end = if (mine) 0.dp else 32.dp),
                )
            }
            if (state.working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Looking at your plan…", style = MaterialTheme.typography.bodySmall)
                }
            }
            state.block?.let { b ->
                AiBlockCard(b, onConsent = { adjust.dismissBlock(); showConsent = true }, onSetup = { adjust.dismissBlock(); onDismiss(); onOpenAiSettings() },
                    onRetry = { adjust.dismissBlock(); adjust.send() }, onDismiss = adjust::dismissBlock)
            }

            state.proposal?.let { p ->
                if (p.changes.isNotEmpty()) {
                    SectionCard {
                        Overline("Suggested changes")
                        p.changes.forEachIndexed { i, c ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                    .toggleable(value = i in state.selected, role = Role.Checkbox, onValueChange = { adjust.toggle(i) }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = i in state.selected, onCheckedChange = null)
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.summary, style = MaterialTheme.typography.bodyMedium)
                                    if (c.reason.isNotBlank()) Text(c.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (c.major) Pill("Needs your OK", MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (p.rejected.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("${p.rejected.size} suggestion${if (p.rejected.size == 1) " was" else "s were"} left out because they didn't match your plan or would change history.",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = adjust::rejectProposal, modifier = Modifier.weight(1f)) { Text("No thanks") }
                            Button(onClick = adjust::applySelected, enabled = state.selected.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Apply selected") }
                        }
                    }
                } else if (state.question.isBlank()) {
                    Text("No changes needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.applied.isNotBlank()) {
                Text(state.applied, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }

            if (state.turns.isEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EXAMPLES.forEach { e -> AssistChip(onClick = { adjust.setInput(e) }, label = { Text(e) }) }
                }
            }
            OutlinedTextField(
                state.input, adjust::setInput,
                placeholder = { Text(if (state.question.isNotBlank()) "Your answer" else "What changed?") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.working,
                trailingIcon = {
                    Row {
                        MicButton { s -> adjust.setInput((state.input + " " + s).trim()) }
                        IconButton(onClick = { adjust.send() }, enabled = state.input.isNotBlank() && !state.working) {
                            Icon(Icons.AutoMirrored.Outlined.Send, "Send")
                        }
                    }
                },
            )
            Text("Uses one AI request per message. Ticking tasks never uses AI.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showConsent) AiConsentDialog(vm, onDismiss = { showConsent = false }) { showConsent = false; adjust.send() }
}

/** "Plan next week": one AI request for the coming week's dated tasks, reviewed before saving. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPlanSheet(vm: AppViewModel, week: WeekPlanViewModel, weekStart: LocalDate, onDismiss: () -> Unit, onOpenAiSettings: () -> Unit) {
    val state by week.state.collectAsState()
    var showConsent by remember { mutableStateOf(false) }
    LaunchedEffect(weekStart) { if (state.plan == null && !state.working && state.done.isBlank()) week.generate(weekStart) }
    ModalBottomSheet(onDismissRequest = { week.discard(); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Plan the week of ${DateFmt.short(weekStart)}", style = MaterialTheme.typography.titleLarge)
            when {
                state.working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp)); Text("Planning next week from your goals and last week…")
                }
                state.done.isNotBlank() -> {
                    Text(state.done)
                    Button(onClick = { week.discard(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                }
                state.plan != null -> {
                    val p = state.plan!!
                    if (p.summary.isNotBlank()) Text(p.summary, style = MaterialTheme.typography.bodyMedium)
                    p.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    state.issues.forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (p.weeklyOutcomes.isNotEmpty()) {
                        SectionCard {
                            Overline("Outcomes for the week")
                            p.weeklyOutcomes.forEach { o -> Text("• ${vm.catalog.value.goalName(o.goalKey)}: ${o.title}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    p.tasks.groupBy { it.date }.toSortedMap().forEach { (date, list) ->
                        Overline(PlanValidator.parseDate(date)?.let(DateFmt::widget) ?: date)
                        list.forEach { t ->
                            SectionCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                                        Text("Ideal ${t.idealMinutes} min: ${t.ideal}", style = MaterialTheme.typography.bodySmall)
                                        Text("Minimum ${t.minimumMinutes} min: ${t.minimum}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    TextButton(onClick = { week.removeTask(t.key) }) { Text("Remove") }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { week.discard(); onDismiss() }, modifier = Modifier.weight(1f)) { Text("Discard") }
                        Button(onClick = week::accept, enabled = p.tasks.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Add to my week") }
                    }
                }
                state.block != null -> AiBlockCard(
                    state.block!!,
                    onConsent = { week.dismissBlock(); showConsent = true },
                    onSetup = { week.dismissBlock(); onDismiss(); onOpenAiSettings() },
                    onRetry = { week.dismissBlock(); week.generate(weekStart) },
                    onDismiss = { week.discard(); onDismiss() },
                )
            }
        }
    }
    if (showConsent) AiConsentDialog(vm, onDismiss = { showConsent = false }) { showConsent = false; week.generate(weekStart) }
}
