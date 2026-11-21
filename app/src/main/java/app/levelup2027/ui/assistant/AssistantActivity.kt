package app.levelup2027.ui.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModelProvider
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
    private val adjust: AdjustViewModel by lazy {
        ViewModelProvider(this, viewModelFactory { initializer { AdjustViewModel(container, alwaysConfirm = true) } })[AdjustViewModel::class.java]
    }
    /** Each mic tap is a new session: a fresh conversation that starts listening again. */
    private val session = mutableIntStateOf(0)

    // singleTask: tapping the mic while the assistant is still open lands here. Never resurface the
    // previous (possibly stale) proposal; start over and listen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        adjust.reset()
        session.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsState()
            LevelUpTheme(settings?.theme ?: ThemeChoice.DARK) {
                key(session.intValue) {
                    // Inside the key: a new session cancels the previous one's pending close.
                    val scope = rememberCoroutineScope()
                    AdjustSheet(
                        vm, adjust,
                        onDismiss = { finish() },
                        onOpenAiSettings = {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .putExtra(MainActivity.EXTRA_OPEN_TAB, "settings"),
                            )
                            finish()
                        },
                        title = "Ascend assistant",
                        intro = "Say what you'd like to change. Nothing happens until you tap Apply.",
                        listenOnOpen = true,
                        assistantMode = true,
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
}
