package app.levelup2027.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.levelup2027.R
import app.levelup2027.container
import app.levelup2027.data.AppSettings
import app.levelup2027.domain.Block
import app.levelup2027.domain.Routine
import app.levelup2027.domain.RoutineSettings
import app.levelup2027.ui.checkin.CheckInActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** A check-in due when [block] (from [date]'s active preset) ends at [at]. */
data class PendingCheckIn(val date: LocalDate, val block: Block, val at: ZonedDateTime) {
    fun putInto(intent: Intent): Intent = intent
        .putExtra(CheckInScheduler.EXTRA_DATE, date.toEpochDay())
        .putExtra(CheckInScheduler.EXTRA_START, block.startMinute)
        .putExtra(CheckInScheduler.EXTRA_END, block.endMinute)
        .putExtra(CheckInScheduler.EXTRA_LABEL, block.label)

    companion object {
        /** Reads what [putInto] wrote; null if anything is missing. */
        fun from(intent: Intent?): Pair<LocalDate, Block>? {
            if (intent == null || !intent.hasExtra(CheckInScheduler.EXTRA_DATE)) return null
            val label = intent.getStringExtra(CheckInScheduler.EXTRA_LABEL) ?: return null
            val start = intent.getIntExtra(CheckInScheduler.EXTRA_START, -1)
            val end = intent.getIntExtra(CheckInScheduler.EXTRA_END, -1)
            if (start < 0 || end <= start) return null
            return LocalDate.ofEpochDay(intent.getLongExtra(CheckInScheduler.EXTRA_DATE, 0)) to Block(start, end, label)
        }
    }
}

/**
 * Keeps exactly one alarm armed: the end of the next block in today's active preset. Re-armed by
 * [ReminderScheduler.scheduleAll], which runs on every settings change, boot, time change and midnight.
 */
object CheckInScheduler {
    const val REQUEST_CODE = 106
    const val ACTION = "app.levelup2027.CHECK_IN"
    const val EXTRA_DATE = "checkin_date"
    const val EXTRA_START = "checkin_start"
    const val EXTRA_END = "checkin_end"
    const val EXTRA_LABEL = "checkin_label"

    /** Pure. Null when check-ins are off, today's preset is empty, or every block has ended. */
    fun next(r: RoutineSettings, now: ZonedDateTime): PendingCheckIn? {
        if (!r.checkInsEnabled) return null
        val date = now.toLocalDate()
        val minute = now.hour * 60 + now.minute
        val block = Routine.nextEnding(Routine.blocksFor(r, date), minute) ?: return null
        return PendingCheckIn(date, block, date.atStartOfDay(now.zone).plusMinutes(block.endMinute.toLong()))
    }

    fun schedule(context: Context, s: AppSettings) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val p = next(s.routine, ZonedDateTime.now(ZoneId.systemDefault()))
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            (p?.putInto(Intent(context, CheckInReceiver::class.java)) ?: Intent(context, CheckInReceiver::class.java)).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (p == null) {
            am.cancel(pi)
            return
        }
        val at = p.at.toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 2 * 60_000L, pi)
        }
    }
}

object CheckInNotifications {
    const val CHANNEL = "checkins"
    const val NOTIFICATION_ID = 2001

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, context.getString(R.string.channel_checkins), NotificationManager.IMPORTANCE_HIGH)
        ch.description = context.getString(R.string.channel_checkins_desc)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    /** Android 14+ lets the user turn full-screen notifications off per app. */
    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    fun post(context: Context, p: PendingCheckIn) {
        if (!Notifications.canPost(context)) return
        val open = p.putInto(Intent(context, CheckInActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(context, CheckInScheduler.REQUEST_CODE, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${p.block.label} block done")
            .setContentText("Tick off what you finished.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
        if (canUseFullScreen(context)) b.setFullScreenIntent(pi, true)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, b.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}

class CheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                val s = c.settings.current()
                PendingCheckIn.from(intent)?.let { (date, block) ->
                    if (s.routine.checkInsEnabled) CheckInNotifications.post(context, PendingCheckIn(date, block, ZonedDateTime.now()))
                }
                CheckInScheduler.schedule(context, s)
            } finally {
                result.finish()
            }
        }
    }
}
