package app.levelup2027.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.levelup2027.MainActivity
import app.levelup2027.R
import app.levelup2027.container
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.DateFmt
import app.levelup2027.domain.WidgetItem
import app.levelup2027.domain.WidgetState
import app.levelup2027.ui.theme.Palette
import app.levelup2027.ui.theme.categoryColor
import java.time.LocalDate

object WidgetUpdater {
    suspend fun refresh(context: Context) {
        runCatching { LevelUpWidget().updateAll(context) }
    }
}

val KeyTaskId = ActionParameters.Key<String>(MainActivity.EXTRA_TASK_ID)
val KeyDate = ActionParameters.Key<Long>(MainActivity.EXTRA_DATE)

private val SMALL = DpSize(140.dp, 100.dp)
private val MEDIUM = DpSize(250.dp, 180.dp)
private val LARGE = DpSize(250.dp, 330.dp)

class LevelUpWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.container.repository
        // Render from a fresh snapshot immediately, then follow live database changes and date rollovers.
        val initial = repo.widgetStateNow()
        provideContent {
            val flow = remember { repo.widgetState() }
            val state by flow.collectAsState(initial)
            WidgetBody(state)
        }
    }
}

class LevelUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LevelUpWidget()
}

/** Checkbox tap: completes (or un-completes) the task in place without opening the app. */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[KeyTaskId] ?: return
        val date = parameters[KeyDate]?.let(LocalDate::ofEpochDay) ?: return
        context.container.repository.toggleFromWidget(taskId, date) // triggers WidgetUpdater.refresh
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdater.refresh(context)
    }
}

private val text = ColorProvider(Palette.Text)
private val muted = ColorProvider(Palette.TextMuted)
private val gold = ColorProvider(Palette.Gold)
private val teal = ColorProvider(Palette.Teal)

@Composable
private fun WidgetBody(state: WidgetState) {
    val size = LocalSize.current
    val layout = when {
        size.height >= LARGE.height -> 2
        size.height >= MEDIUM.height -> 1
        else -> 0
    }
    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(Palette.Bg).cornerRadius(20.dp).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Header(state, compact = layout == 0)
        Spacer(GlanceModifier.height(6.dp))
        val visible = when (layout) {
            0 -> state.items.filter { !it.done }.take(2)
            1 -> state.items.take(5)
            else -> state.items
        }
        when {
            state.scheduled == 0 -> Message(if (state.config.slot != null) "Nothing scheduled for ${state.config.slot.shortLabel.lowercase()} today." else "Nothing scheduled today. Rest well.")
            state.openCount == 0 && visible.isEmpty() -> Message("All done for today ✓")
            layout == 0 -> Column { visible.forEach { TaskRow(it, showLine = false) } }
            layout == 1 -> LazyColumn { items(visible) { TaskRow(it, showLine = true) } }
            else -> LazyColumn {
                var lastSlot: TimeSlot? = null
                visible.forEach { item ->
                    if (item.slot != lastSlot && state.config.slot == null) {
                        val slot = item.slot
                        item { SlotHeader(slot) }
                        lastSlot = slot
                    }
                    item { TaskRow(item, showLine = true) }
                }
            }
        }
    }
}

@Composable
private fun Header(state: WidgetState, compact: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text("ASCEND", style = TextStyle(color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            val sub = buildString {
                append(DateFmt.widget(state.date))
                state.config.slot?.let { append(" · ").append(it.shortLabel) }
                if (state.lowEnergy) append(" · low-energy")
            }
            if (!compact) Text(sub, style = TextStyle(color = muted, fontSize = 11.sp), maxLines = 1)
        }
        Text(
            "${state.done}/${state.scheduled}",
            style = TextStyle(color = text, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Bold),
        )
        if (!compact) {
            Spacer(GlanceModifier.width(6.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                colorFilter = ColorFilter.tint(muted),
                modifier = GlanceModifier.size(28.dp).padding(4.dp).clickable(actionRunCallback<RefreshAction>()),
            )
        }
    }
    Spacer(GlanceModifier.height(5.dp))
    LinearProgressIndicator(
        progress = if (state.scheduled == 0) 0f else state.done.toFloat() / state.scheduled,
        modifier = GlanceModifier.fillMaxWidth().height(4.dp),
        color = gold,
        backgroundColor = ColorProvider(Palette.SurfaceHigh),
    )
    if (!compact) {
        Spacer(GlanceModifier.height(3.dp))
        Text(
            "${state.done} of ${state.scheduled} tasks completed",
            style = TextStyle(color = muted, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SlotHeader(slot: TimeSlot) {
    Text(
        slot.label.uppercase(),
        style = TextStyle(color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun TaskRow(item: WidgetItem, showLine: Boolean) {
    val params = actionParametersOf(KeyTaskId to item.taskId, KeyDate to item.date.toEpochDay())
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        // 40dp tap target for the checkbox.
        Box(
            modifier = GlanceModifier.size(40.dp).clickable(actionRunCallback<ToggleTaskAction>(params)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(if (item.done) R.drawable.ic_check_circle else R.drawable.ic_circle_outline),
                contentDescription = if (item.done) "Completed: ${item.title}. Tap to undo" else "Complete ${item.title}",
                colorFilter = ColorFilter.tint(
                    when {
                        !item.done -> muted
                        item.completionType == app.levelup2027.data.CompletionType.MINIMUM -> teal
                        else -> gold
                    },
                ),
                modifier = GlanceModifier.size(24.dp),
            )
        }
        Column(
            modifier = GlanceModifier.defaultWeight().padding(start = 4.dp)
                .clickable(actionStartActivity<MainActivity>(params)),
        ) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Box(modifier = GlanceModifier.size(8.dp).cornerRadius(4.dp).background(categoryColor(item.colorIndex))) {}
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    item.title,
                    style = TextStyle(color = if (item.done) muted else text, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
            if (showLine && item.line.isNotEmpty() && !item.done) {
                Text(
                    "${item.line} · ${item.minutes}m",
                    style = TextStyle(color = muted, fontSize = 11.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(start = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun Message(msg: String) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(msg, style = TextStyle(color = muted, fontSize = 13.sp))
    }
}
