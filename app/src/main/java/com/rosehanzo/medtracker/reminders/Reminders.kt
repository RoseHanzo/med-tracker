package com.rosehanzo.medtracker.reminders

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
import com.rosehanzo.medtracker.MainActivity
import com.rosehanzo.medtracker.R
import com.rosehanzo.medtracker.data.Logic
import com.rosehanzo.medtracker.data.Settings
import com.rosehanzo.medtracker.data.Store
import java.time.LocalDate
import java.time.ZoneId

/**
 * The daily dose reminder.
 *
 *  - DUE fires at the dose time on the next day the plan has pills, posts the reminder and starts nagging.
 *  - NAG repeats every few minutes until the dose is marked taken or the day ends.
 */
object Reminders {
    const val CH_DOSE = "dose_reminders_v1"
    const val CH_STOCK = "pill_supply_v1"

    const val ACTION_DUE = "com.rosehanzo.medtracker.action.DUE"
    const val ACTION_NAG = "com.rosehanzo.medtracker.action.NAG"
    const val ACTION_TAKE = "com.rosehanzo.medtracker.action.TAKE"
    const val EXTRA_DATE = "date"

    private const val REQ_DUE = 100
    private const val REQ_NAG = 200
    private const val REQ_TAKE = 300
    private const val REQ_OPEN = 400
    private const val DOSE_ID = 1000
    private const val STOCK_ID = 2000
    private const val TEST_ID = 2001
    private const val IMMUTABLE = PendingIntent.FLAG_IMMUTABLE
    private val BRAND = 0xFF00696E.toInt()

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val dose = NotificationChannel(CH_DOSE, "Medication reminders", NotificationManager.IMPORTANCE_HIGH)
        dose.description = "Repeats until you mark your dose as taken"
        dose.enableVibration(true)
        nm.createNotificationChannel(dose)
        val stock = NotificationChannel(CH_STOCK, "Pill supply", NotificationManager.IMPORTANCE_DEFAULT)
        stock.description = "Warns you when you're running low"
        nm.createNotificationChannel(stock)
    }

    private fun alarmIntent(ctx: Context, action: String, date: String?): Intent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(action)
        if (date != null) i.putExtra(EXTRA_DATE, date)
        return i
    }

    private fun pending(ctx: Context, action: String, date: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(ctx, req, alarmIntent(ctx, action, date), PendingIntent.FLAG_UPDATE_CURRENT or IMMUTABLE)

    private fun existing(ctx: Context, action: String, req: Int): PendingIntent? =
        PendingIntent.getBroadcast(ctx, req, alarmIntent(ctx, action, null), PendingIntent.FLAG_NO_CREATE or IMMUTABLE)

    private fun setAlarm(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm(ctx: Context, action: String, req: Int) {
        val pi = existing(ctx, action, req) ?: return
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    }

    private fun dueAt(s: Settings, date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(Logic.doseMinutes(s, date).toLong())
            .toInstant().toEpochMilli()

    fun scheduleAll(ctx: Context) {
        val d = Store.data(ctx)
        val s = d.settings
        if (!s.remindersOn) {
            cancelAlarm(ctx, ACTION_DUE, REQ_DUE)
            stopNag(ctx)
            return
        }
        val now = System.currentTimeMillis()
        val today = LocalDate.now()

        // Next day with pills planned, at that day's dose time.
        var date = today
        var scheduled = false
        for (i in 0 until 400) {
            val due = Logic.planFor(s, date).pills > 0 && !(date == today && Logic.taken(d, today))
            if (due) {
                val t = dueAt(s, date)
                if (t > now) {
                    setAlarm(ctx, t, pending(ctx, ACTION_DUE, date.toString(), REQ_DUE))
                    scheduled = true
                    break
                }
            }
            date = date.plusDays(1)
        }
        if (!scheduled) cancelAlarm(ctx, ACTION_DUE, REQ_DUE)

        // Today's dose is overdue: make sure the nagging is running (e.g. after a restart).
        val overdue = Logic.planFor(s, today).pills > 0 &&
            dueAt(s, today) <= now &&
            !Logic.taken(d, today) &&
            !today.isBefore(Logic.startDate(s))
        if (!overdue) {
            stopNag(ctx)
        } else if (existing(ctx, ACTION_NAG, REQ_NAG) == null) {
            setAlarm(ctx, now + 3_000L, pending(ctx, ACTION_NAG, today.toString(), REQ_NAG))
        }
    }

    private fun stopNag(ctx: Context) {
        cancelAlarm(ctx, ACTION_NAG, REQ_NAG)
        NotificationManagerCompat.from(ctx).cancel(DOSE_ID)
    }

    fun onTaken(ctx: Context) {
        stopNag(ctx)
        scheduleAll(ctx)
    }

    fun onAlarm(ctx: Context, intent: Intent) {
        val dateText = intent.getStringExtra(EXTRA_DATE) ?: return
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return
        val d = Store.data(ctx)
        val s = d.settings
        val pills = Logic.planFor(s, date).pills
        val stillDue = s.remindersOn && pills > 0 && date == LocalDate.now() && !Logic.taken(d, date)

        if (!stillDue) {
            stopNag(ctx)
            if (intent.action == ACTION_DUE) scheduleAll(ctx)
            return
        }

        showDose(ctx, s, date, pills)
        val nagAt = System.currentTimeMillis() + s.nagMinutes.coerceAtLeast(1) * 60_000L
        setAlarm(ctx, nagAt, pending(ctx, ACTION_NAG, dateText, REQ_NAG))
        if (intent.action == ACTION_DUE) scheduleAll(ctx)
    }

    private fun showDose(ctx: Context, s: Settings, date: LocalDate, pills: Int) {
        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return

        val take = PendingIntent.getBroadcast(
            ctx, REQ_TAKE, alarmIntent(ctx, ACTION_TAKE, date.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            ctx, REQ_OPEN,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or IMMUTABLE,
        )

        val due = Logic.fmtTime(Logic.doseMinutes(s, date))
        val text = "Due at $due. I'll remind you every ${s.nagMinutes} min until you tap Taken."
        val n = NotificationCompat.Builder(ctx, CH_DOSE)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("Take ${Logic.pillWord(pills)} of ${s.medName}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setColor(BRAND)
            .setContentIntent(open)
            .addAction(R.drawable.ic_check, "Taken", take)
            .build()
        try {
            nmc.notify(DOSE_ID, n)
        } catch (_: SecurityException) {
        }
    }

    fun checkStock(ctx: Context) {
        val d = Store.data(ctx)
        val s = d.settings
        if (s.pillsLeft < 0) return
        val days = Logic.daysOfSupply(d)
        val today = LocalDate.now().toString()
        if (days > s.lowStockDays || s.stockWarnDate == today) return
        Store.update(ctx) { it.copy(settings = it.settings.copy(stockWarnDate = today)) }

        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return
        val text = if (days == 0) {
            "You don't have enough for your next dose. Time to get your repeat prescription."
        } else {
            "${Logic.pillWord(s.pillsLeft)} left, enough for about $days days. Time to order your repeat prescription."
        }
        val n = NotificationCompat.Builder(ctx, CH_STOCK)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("Running low on ${s.medName}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(BRAND)
            .setAutoCancel(true)
            .build()
        try {
            nmc.notify(STOCK_ID, n)
        } catch (_: SecurityException) {
        }
    }

    /** Returns false if notifications are switched off for the app. */
    fun sendTest(ctx: Context): Boolean {
        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return false
        val n = NotificationCompat.Builder(ctx, CH_DOSE)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("Test reminder")
            .setContentText("This is how your dose reminders will look and sound.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(BRAND)
            .setAutoCancel(true)
            .build()
        return try {
            nmc.notify(TEST_ID, n)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Reminders.ACTION_TAKE -> {
                val date = intent.getStringExtra(Reminders.EXTRA_DATE)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                Logic.markTaken(context, date)
            }
            Reminders.ACTION_DUE, Reminders.ACTION_NAG -> Reminders.onAlarm(context, intent)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.createChannels(context)
        Reminders.scheduleAll(context)
    }
}
