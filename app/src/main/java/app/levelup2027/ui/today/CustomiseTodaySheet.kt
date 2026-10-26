package app.levelup2027.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.levelup2027.data.AppSettings
import app.levelup2027.data.CardLayout
import app.levelup2027.data.Repository
import app.levelup2027.data.TodayLayout
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.components.Overline
import app.levelup2027.ui.tasks.SwitchRow

/** Choose which parts of Today show and what each task card contains. Saved immediately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomiseTodaySheet(vm: AppViewModel, s: AppSettings, onDismiss: () -> Unit) {
    val t = s.todayLayout
    val c = s.cardLayout
    fun today(f: (TodayLayout) -> TodayLayout) = vm.updateSettings(Repository.ChangeKind.OTHER) { it.copy(todayLayout = f(it.todayLayout)) }
    fun card(f: (CardLayout) -> CardLayout) = vm.updateSettings(Repository.ChangeKind.OTHER) { it.copy(cardLayout = f(it.cardLayout)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Customise Today", style = MaterialTheme.typography.titleLarge)
            Text("Tap any section header on Today to fold or unfold it.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Overline("Sections")
            SwitchRow("Today's progress", "Done count, ideal vs minimum, low-energy switch", t.showProgress) { v -> today { it.copy(showProgress = v) } }
            SwitchRow("Focus", "What matters most, what's next, the minimum", t.showFocus) { v -> today { it.copy(showFocus = v) } }
            SwitchRow("Tasks from earlier days", "Suggestions for missed one-off tasks", t.showMissed) { v -> today { it.copy(showMissed = v) } }
            SwitchRow("Plan next week reminder", null, t.showWeekPlanner) { v -> today { it.copy(showWeekPlanner = v) } }
            SwitchRow("Filter chips", "Morning, During work, Evening, Missed, Completed…", t.showFilters) { v -> today { it.copy(showFilters = v) } }

            Spacer(Modifier.height(10.dp))
            Overline("Task cards")
            SwitchRow("Open cards by default", "Off: cards start as one line; tap ⌄ to see more", c.expandedByDefault) { v -> card { it.copy(expandedByDefault = v) } }
            SwitchRow("Category icon", null, c.showIcon) { v -> card { it.copy(showIcon = v) } }
            SwitchRow("Category, goal and time line", null, c.showDetails) { v -> card { it.copy(showDetails = v) } }
            SwitchRow("Minutes", null, c.showMinutes) { v -> card { it.copy(showMinutes = v) } }
            SwitchRow("One-tap done circle", "On folded cards; completes the version you're shown", c.showQuickDone) { v -> card { it.copy(showQuickDone = v) } }
            SwitchRow("Ideal version", "When a card is open", c.showIdeal) { v -> card { it.copy(showIdeal = v) } }
            SwitchRow("Minimum version", "When a card is open", c.showMinimum) { v -> card { it.copy(showMinimum = v) } }
            SwitchRow("Move to another day", null, c.showMove) { v -> card { it.copy(showMove = v) } }
            SwitchRow("Skip", null, c.showSkip) { v -> card { it.copy(showSkip = v) } }

            TextButton(onClick = { vm.updateSettings(Repository.ChangeKind.OTHER) { it.copy(todayLayout = TodayLayout(), cardLayout = CardLayout()) } }) {
                Text("Reset to default layout")
            }
        }
    }
}
