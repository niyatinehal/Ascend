package app.levelup2027

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.levelup2027.data.ThemeChoice
import app.levelup2027.data.TimeSlot
import app.levelup2027.ui.AppRoot
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.OpenRequest
import app.levelup2027.ui.Tab
import app.levelup2027.ui.theme.LevelUpTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_DATE = "date"
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_OPEN_SLOT = "open_slot"
    }

    private val openRequests = MutableStateFlow<OpenRequest?>(null)

    private val vm: AppViewModel by lazy {
        ViewModelProvider(this, viewModelFactory { initializer { AppViewModel(container) } })[AppViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by vm.settings.collectAsState()
            LevelUpTheme(settings?.theme ?: ThemeChoice.DARK) {
                AppRoot(vm, openRequests)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        val date = if (intent.hasExtra(EXTRA_DATE)) LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_DATE, 0)) else null
        val tab = when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
            "review" -> Tab.REVIEW
            "today" -> Tab.TODAY
            else -> null
        }
        val slot = intent.getStringExtra(EXTRA_OPEN_SLOT)?.let { runCatching { TimeSlot.valueOf(it) }.getOrNull() }
        if (taskId != null || tab != null) {
            openRequests.value = OpenRequest(taskId, date, tab ?: Tab.TODAY, slot)
        }
    }
}
