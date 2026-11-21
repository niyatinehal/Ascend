package app.levelup2027.ui.routine

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.levelup2027.notify.Notifications
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.levelup2027.domain.PresetKind
import app.levelup2027.notify.CheckInNotifications
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.components.HelpText
import app.levelup2027.ui.components.LinkRow
import app.levelup2027.ui.components.SectionCard
import app.levelup2027.ui.components.SectionTitle

/** Settings → Routine: the three presets, the check-in switch and the full-screen permission. */
@Composable
fun RoutineSettingsSection(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    val routine = s?.routine ?: return
    var editing by remember { mutableStateOf<PresetKind?>(null) }

    SectionCard {
        PresetKind.entries.forEach { kind ->
            val n = routine.presets[kind].orEmpty().size
            LinkRow(kind.label, value = if (n == 0) "Not set up" else "$n block${if (n == 1) "" else "s"}") { editing = kind }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Block check-ins", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(routine.checkInsEnabled, { on -> vm.updateSettings { it.copy(routine = it.routine.copy(checkInsEnabled = on)) } })
        }
        HelpText("When a block ends, a full-screen check-in lets you tick off what you finished. Every day starts on ${PresetKind.DAILY.label}; switch on Today.")
        CheckInPermissionRow()
    }

    editing?.let { kind -> PresetEditorSheet(vm, kind) { editing = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditorSheet(vm: AppViewModel, kind: PresetKind, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsState()
    val routine = s?.routine ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            SectionTitle(kind.label)
            PresetEditorPanel(
                vm = vm,
                kind = kind,
                saved = routine.presets[kind].orEmpty(),
                dailyBlocks = routine.presets[PresetKind.DAILY].orEmpty(),
                onSaved = onDismiss,
                describe = describeSlot(vm, kind),
            )
        }
    }
}

/**
 * What check-ins still need: notification permission (without it nothing is shown at all), then
 * full-screen notifications on Android 14+. Shows nothing when both are allowed.
 */
@Composable
fun CheckInPermissionRow() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val canNotify = remember(tick) { Notifications.canPost(context) }
    val allowed = remember(tick) { CheckInNotifications.canUseFullScreen(context) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    if (!canNotify) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Notifications are off for Ascend, so check-ins can't appear.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                val granted = Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (Build.VERSION.SDK_INT >= 33 && !granted) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Allow") }
        }
        return
    }
    if (allowed || Build.VERSION.SDK_INT < 34) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Allow full-screen notifications so check-ins can fill the screen when your phone is locked.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
        }) { Text("Allow") }
    }
}
