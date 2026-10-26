package app.levelup2027.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.levelup2027.ai.PromptTemplate
import app.levelup2027.ai.PromptTemplates
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.components.Overline
import app.levelup2027.ui.components.SectionCard
import app.levelup2027.ui.theme.categoryIcon

/**
 * "Pick one or more areas": each chip adds its starter paragraph to the description (tap again to
 * remove it while it's unedited). The text stays fully editable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AreaPicker(vm: AppViewModel, text: String, onText: (String) -> Unit) {
    val settings by vm.settings.collectAsState()
    val templates = PromptTemplates.effective(settings?.promptTemplates)
    var editing by remember { mutableStateOf(false) }
    val paragraphs = text.split("\n\n").map { it.trim() }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("Pick one or more areas to start from", Modifier.weight(1f))
            TextButton(onClick = { editing = true }) { Text("Edit prompts") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            templates.forEach { t ->
                val on = t.text.trim() in paragraphs
                FilterChip(
                    selected = on,
                    onClick = { onText(PromptTemplates.toggle(text, t, !on)) },
                    label = { Text(t.label) },
                    leadingIcon = { Icon(categoryIcon(t.icon), null, Modifier.size(16.dp)) },
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            }
        }
        Text("Then change the details so they're about you: numbers, deadlines, how much time you have.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (editing) PromptTemplatesEditor(vm, onDismiss = { editing = false })
}

/** Edit, add, remove and reset the starter prompts. Saved in settings (and in backups). */
@Composable
fun PromptTemplatesEditor(vm: AppViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val saved = PromptTemplates.effective(settings?.promptTemplates)
    var list by remember { mutableStateOf(saved) }
    var confirmReset by remember { mutableStateOf(false) }
    fun update(t: PromptTemplate) { list = list.map { if (it.id == t.id) t else it } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close without saving") }
                    Text("Plan prompts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            val cleaned = list.filter { it.label.isNotBlank() && it.text.isNotBlank() }
                            vm.updateSettings { it.copy(promptTemplates = if (cleaned == PromptTemplates.DEFAULTS) null else cleaned) }
                            onDismiss()
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Save") }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text("These are the starting texts for each area in Build my plan. Write them the way you'd describe your goals; you can still edit the text each time.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(list, key = { it.id }) { t ->
                        SectionCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(t.label, { update(t.copy(label = it.take(24))) }, label = { Text("Area name") }, singleLine = true, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { list = list - t }) { Icon(Icons.Outlined.DeleteOutline, "Remove ${t.label}") }
                            }
                            Spacer(Modifier.size(6.dp))
                            OutlinedTextField(t.text, { update(t.copy(text = it.take(1500))) }, label = { Text("Prompt") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        }
                    }
                    item {
                        OutlinedButton(onClick = {
                            list = list + PromptTemplate(PromptTemplates.newId("area", list), "New area", "I want to …", "label")
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add an area") }
                        TextButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset to the built-in prompts") }
                    }
                }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset prompts?") },
            text = { Text("Your edited and added areas will be replaced by the built-in ones.") },
            confirmButton = { TextButton(onClick = { list = PromptTemplates.DEFAULTS; confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
