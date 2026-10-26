package app.levelup2027.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.levelup2027.MainActivity
import app.levelup2027.R
import app.levelup2027.container
import app.levelup2027.data.AppSettings
import app.levelup2027.data.Reminder
import app.levelup2027.data.TimeSlot
import app.levelup2027.domain.Dates
import app.levelup2027.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class ReminderKind(val requestCode: Int, val slot: TimeSlot?) {
    MORNING(101, TimeSlot.MORNING),
    WORK(102, TimeSlot.WORK),
    EVENING(103, TimeSlot.EVENING),
    WEEKLY_REVIEW(104, null),
    MIDNIGHT(105, null);

    fun reminder(s: AppSettings): Reminder? = when (this) {
        MORNING -> s.morningReminder
        WORK -> s.workReminder
        EVENING -> s.eveningReminder
        WEEKLY_REVIEW -> s.weeklyReviewReminder
        MIDNIGHT -> null
    }
}

/**
 * Uses inexact alarms (setAndAllowWhileIdle), so no exact-alarm permission is needed. Reminders
 * may arrive a few minutes late under Doze; that's fine for a checklist nudge.
 */
object ReminderScheduler {
    const val ACTION = "app.levelup2027.ALARM"
    const val EXTRA_KIND = "kind"

    /** Next trigger strictly after [now]; [weeklyOn] restricts to one weekday. Pure, for tests. */
    fun nextTrigger(now: ZonedDateTime, hour: Int, minute: Int, weeklyOn: DayOfWeek? = null): ZonedDateTime {
        var t = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
        if (weeklyOn != null) {
            t = t.with(TemporalAdjusters.nextOrSame(weeklyOn))
            if (!t.isAfter(now)) t = t.plusWeeks(1)
        } else if (!t.isAfter(now)) {
            t = now.toLocalDate().plusDays(1).atTime(hour, minute).atZone(now.zone)
        }
        return t
    }

    fun scheduleAll(context: Context, s: AppSettings) {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        for (kind in ReminderKind.entries) {
            if (kind == ReminderKind.MIDNIGHT) {
                val t = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(1)
                set(context, kind, t.toInstant().toEpochMilli(), wake = false)
                continue
            }
            val r = kind.reminder(s)!!
            if (!r.enabled) {
                cancel(context, kind)
            } else {
                val t = nextTrigger(now, r.hour, r.minute, if (kind == ReminderKind.WEEKLY_REVIEW) DayOfWeek.SUNDAY else null)
                set(context, kind, t.toInstant().toEpochMilli(), wake = true)
            }
        }
    }

    private fun pending(context: Context, kind: ReminderKind): PendingIntent =
        PendingIntent.getBroadcast(
            context, kind.requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION).putExtra(EXTRA_KIND, kind.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun set(context: Context, kind: ReminderKind, atMillis: Long, wake: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(if (wake) AlarmManager.RTC_WAKEUP else AlarmManager.RTC, atMillis, pending(context, kind))
    }

    private fun cancel(context: Context, kind: ReminderKind) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, kind))
    }
}

object Notifications {
    const val CHANNEL = "reminders"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = context.getString(R.string.channel_reminders_desc)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(context: Context, kind: ReminderKind, title: String, text: String) {
        if (!canPost(context)) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, if (kind == ReminderKind.WEEKLY_REVIEW) "review" else "today")
            .putExtra(MainActivity.EXTRA_OPEN_SLOT, kind.slot?.name)
        val pi = PendingIntent.getActivity(context, kind.requestCode, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(kind.requestCode, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND)?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() } ?: return
        val result = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                handle(context, kind)
                ReminderScheduler.scheduleAll(context, c.settings.current())
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun handle(context: Context, kind: ReminderKind) {
        val repo = context.container.repository
        val today = Dates.today()
        when (kind) {
            ReminderKind.MIDNIGHT -> WidgetUpdater.refresh(context)
            ReminderKind.WEEKLY_REVIEW -> {
                if (repo.reviewFor(Dates.weekStart(today)) == null) {
                    Notifications.post(context, kind, "Weekly review", "15 minutes is enough: look back at the week and choose next week's main priority.")
                }
            }
            else -> {
                val open = repo.openTasksFor(today, kind.slot)
                if (open.isEmpty()) return // no nagging when there's nothing left
                val minMinutes = open.sumOf { it.estimatedMinimumMinutes }
                val (title, text) = when (kind) {
                    ReminderKind.MORNING -> {
                        val all = repo.openTasksFor(today, null)
                        "Today: ${all.size} task${if (all.size == 1) "" else "s"}" to
                            "Start with: ${open.first().title}."
                    }
                    ReminderKind.WORK -> "A small note at work" to "${open.first().title}. One line is enough."
                    else -> "Evening: ${open.size} task${if (open.size == 1) "" else "s"} left" to
                        "Low on energy? The minimum versions take about $minMinutes min in total."
                }
                Notifications.post(context, kind, title, text)
            }
        }
    }
}

/** Re-arms alarms after reboot, app update, or a clock/time-zone change, and refreshes the widget. */
class SystemEventsReceiver : BroadcastReceiver() {
    private val handled = setOf(
        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in handled) return
        val result = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                ReminderScheduler.scheduleAll(context, c.settings.current())
                WidgetUpdater.refresh(context)
            } finally {
                result.finish()
            }
        }
    }
}
