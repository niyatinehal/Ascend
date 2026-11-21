package app.levelup2027.ui.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.levelup2027.MainActivity
import app.levelup2027.container
import app.levelup2027.data.ThemeChoice
import app.levelup2027.ui.AppViewModel
import app.levelup2027.ui.plan.AdjustSheet
import app.levelup2027.ui.plan.AdjustViewModel
import app.levelup2027.ui.theme.LevelUpTheme
import app.levelup2027.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Voice/text assistant opened from the home-screen widget; every change needs an explicit Apply. */
class AssistantActivity : ComponentActivity() {
    private val vm: AppViewModel by lazy {
        ViewModelProvider(this, viewModelFactory { initializer { AppViewModel(container) } })[AppViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsState()
            LevelUpTheme(settings?.theme ?: ThemeChoice.DARK) {
                val container = LocalContext.current.container
                val adjust: AdjustViewModel = viewModel(
                    factory = viewModelFactory { initializer { AdjustViewModel(container, alwaysConfirm = true) } },
                )
                val scope = rememberCoroutineScope()
                AdjustSheet(
                    vm, adjust,
                    onDismiss = { finish() },
                    onOpenAiSettings = {
                        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TAB, "settings"))
                        finish()
                    },
                    title = "Ascend assistant",
                    intro = "Say what you'd like to change. Nothing happens until you tap Apply.",
                    listenOnOpen = true,
                    onApplied = {
                        scope.launch {
                            WidgetUpdater.refresh(applicationContext)
                            delay(1500)
                            finish()
                        }
                    },
                )
            }
        }
    }
}
