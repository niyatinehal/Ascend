package app.levelup2027.ui.checkin

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import app.levelup2027.container
import app.levelup2027.data.AppSettings
import app.levelup2027.data.DefaultMode
import app.levelup2027.data.Repository
import app.levelup2027.data.ThemeChoice
import app.levelup2027.data.ThemePreset
import app.levelup2027.domain.Block
import app.levelup2027.domain.Routine
import app.levelup2027.domain.ScheduleEngine
import app.levelup2027.notify.CheckInNotifications
import app.levelup2027.notify.PendingCheckIn
import app.levelup2027.ui.theme.LevelUpTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The full-screen block check-in. Shows over the lock screen; lists the block's date's tasks with
 * the ones that match the block first. Ticking here writes the same completions as Today.
 */
class CheckInActivity : ComponentActivity() {
    private var target by mutableStateOf<Pair<LocalDate, Block>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        enableEdgeToEdge()
        if (!accept(intent)) return

        val repo = container.repository
        val data = combine(repo.tasks, repo.completions, repo.settings.flow, repo.categories, repo.overrides) { t, c, s, cats, ov ->
            CheckInInputs(t, c, s, cats, ov)
        }
        setContent {
            val inputs by data.collectAsState(initial = null)
            val (date, block) = target ?: return@setContent
            LevelUpTheme(inputs?.settings?.theme ?: ThemeChoice.DARK, inputs?.settings?.themePreset ?: ThemePreset.GOLD) {
                val i = inputs
                val occ = if (i == null) emptyList() else {
                    val names = i.categories.associate { it.id to it.name }
                    Routine.orderForBlock(block, ScheduleEngine.occurrencesFor(date, i.tasks, i.completions, Repository.hiddenCategories(i.categories)), names)
                        .filter { !it.isSkipped }
                }
                val preferMin = i?.overrides?.filter { it.date == date && it.preferMinimum }?.mapTo(HashSet()) { it.taskId }.orEmpty()
                CheckInScreen(
                    date = date,
                    block = block,
                    loading = i == null,
                    occurrences = occ,
                    showMinimum = { id -> i != null && (i.settings.isLowEnergy(date) || i.settings.defaultMode == DefaultMode.MINIMUM || id in preferMin) },
                    onToggle = { id ->
                        lifecycleScope.launch {
                            val s = repo.settings.current()
                            repo.toggle(id, date, minimumByDefault = s.defaultMode == DefaultMode.MINIMUM)
                        }
                    },
                    onDone = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        accept(intent)
    }

    /** Takes the block from [intent]; finishes if it's missing. */
    private fun accept(intent: Intent?): Boolean {
        val t = PendingCheckIn.from(intent)
        if (t == null) {
            finish()
            return false
        }
        target = t
        NotificationManagerCompat.from(this).cancel(CheckInNotifications.NOTIFICATION_ID)
        return true
    }

    private data class CheckInInputs(
        val tasks: List<app.levelup2027.data.Task>,
        val completions: List<app.levelup2027.data.Completion>,
        val settings: AppSettings,
        val categories: List<app.levelup2027.data.CategoryEntity>,
        val overrides: List<app.levelup2027.data.OccurrenceOverride>,
    )
}
