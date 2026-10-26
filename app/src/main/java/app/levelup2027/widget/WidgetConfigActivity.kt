package app.levelup2027.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.levelup2027.container
import app.levelup2027.data.AppSettings
import app.levelup2027.data.WidgetSettings
import app.levelup2027.ui.settings.WidgetSettingsEditor
import app.levelup2027.ui.theme.LevelUpTheme
import kotlinx.coroutines.launch

/**
 * Shown when the widget is added (and when reconfigured on Android 12+). Widget settings are
 * shared by all Ascend widgets and are also editable under Settings → Widget.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_CANCELED, resultValue)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val repo = container.repository
        setContent {
            val settings by repo.settings.flow.collectAsState(initial = null)
            val categories by repo.categories.collectAsState(initial = emptyList())
            val s = settings ?: return@setContent
            LevelUpTheme(s.theme) {
                var draft by remember(s.widget) { mutableStateOf(s.widget) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text("Ascend widget", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Shows today's tasks. Tap a circle to complete a task in place; tap a title to open it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        WidgetSettingsEditor(draft, categories, onChange = { draft = it })
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { save(draft, resultValue) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Add widget") }
                    }
                }
            }
        }
    }

    private fun save(w: WidgetSettings, result: Intent) {
        lifecycleScope.launch {
            container.repository.updateSettings { s: AppSettings -> s.copy(widget = w) }
            WidgetUpdater.refresh(this@WidgetConfigActivity)
            setResult(RESULT_OK, result)
            finish()
        }
    }
}
