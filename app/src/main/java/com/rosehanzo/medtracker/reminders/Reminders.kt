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
 * Dose reminders.
 *
 * Each dose slot has two alarms:
 *  - DUE: fires at the scheduled time, posts the reminder and starts nagging.
 *  - NAG: repeats every few minutes until the dose is marked taken or the day ends.
 */
object Reminders {
    const val CH_DOSE = "dose_reminders_v1"
    const val CH_STOCK = "pill_supply_v1"

    const val ACTION_DUE = "com.rosehanzo.medtracker.action.DUE"
    const val ACTION_NAG = "com.rosehanzo.medtracker.action.NAG"
    const val ACTION_TAKE = "com.rosehanzo.medtracker.action.TAKE"
    const val EXTRA_SLOT = "slot"
    const val EXTRA_DATE = "date"

    private const val STOCK_ID = 2000
    private const val TEST_ID = 2001
    private const val IMMUTABLE = PendingIntent.FLAG_IMMUTABLE

    private fun reqDue(slot: Int) = 100 + slot
    private fun reqNag(slot: Int) = 200 + slot
    private fun reqTake(slot: Int) = 300 + slot
    private fun reqOpen(slot: Int) = 400 + slot
    private fun notifId(slot: Int) = 1000 + slot

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

    private fun alarmIntent(ctx: Context, action: String, slot: Int, date: String?): Intent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_SLOT, slot)
        if (date != null) i.putExtra(EXTRA_DATE, date)
        return i
    }

    private fun pending(ctx: Context, action: String, slot: Int, date: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, req, alarmIntent(ctx, action, slot, date),
            PendingIntent.FLAG_UPDATE_CURRENT or IMMUTABLE,
        )

    private fun existing(ctx: Context, action: String, slot: Int, req: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            ctx, req, alarmIntent(ctx, action, slot, null),
            PendingIntent.FLAG_NO_CREATE or IMMUTABLE,
        )

    private fun setAlarm(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm(ctx: Context, action: String, slot: Int, req: Int) {
        val pi = existing(ctx, action, slot, req) ?: return
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    }

    private fun dueAt(s: Settings, date: LocalDate, slot: Int): Long {
        val zone = ZoneId.systemDefault()
        return date.atStartOfDay(zone)
            .plusMinutes(Logic.slotMinutes(s, date, slot).toLong())
            .toInstant().toEpochMilli()
    }

    fun scheduleAll(ctx: Context) {
        for (slot in 0..2) scheduleSlot(ctx, slot)
    }

    fun scheduleSlot(ctx: Context, slot: Int) {
        val d = Store.data(ctx)
        val s = d.settings
        if (!s.remindersOn || slot >= s.dosesPerDay) {
            cancelAlarm(ctx, ACTION_DUE, slot, reqDue(slot))
            stopNag(ctx, slot)
            return
        }
        val now = System.currentTimeMillis()
        val today = LocalDate.now()

        // Next scheduled reminder.
        var date = today
        for (i in 0 until 8) {
            val t = dueAt(s, date, slot)
            if (t > now) {
                setAlarm(ctx, t, pending(ctx, ACTION_DUE, slot, date.toString(), reqDue(slot)))
                break
            }
            date = date.plusDays(1)
        }

        // A dose that's overdue today and not being nagged about (e.g. after a restart): start nagging.
        val overdue = dueAt(s, today, slot) <= now && !Logic.taken(d, today, slot)
        if (overdue && !nagActive(ctx, slot) && !today.isBefore(Logic.startDate(s))) {
            setAlarm(ctx, now + 3_000L, pending(ctx, ACTION_NAG, slot, today.toString(), reqNag(slot)))
        }
    }

    private fun nagActive(ctx: Context, slot: Int): Boolean =
        existing(ctx, ACTION_NAG, slot, reqNag(slot)) != null

    private fun stopNag(ctx: Context, slot: Int) {
        cancelAlarm(ctx, ACTION_NAG, slot, reqNag(slot))
        NotificationManagerCompat.from(ctx).cancel(notifId(slot))
    }

    fun onTaken(ctx: Context, slot: Int) {
        stopNag(ctx, slot)
        scheduleSlot(ctx, slot)
    }

    fun onAlarm(ctx: Context, intent: Intent) {
        val slot = intent.getIntExtra(EXTRA_SLOT, 0)
        val dateText = intent.getStringExtra(EXTRA_DATE) ?: return
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return
        val d = Store.data(ctx)
        val s = d.settings
        val stillDue = s.remindersOn && slot < s.dosesPerDay &&
            date == LocalDate.now() && !Logic.taken(d, date, slot)

        if (!stillDue) {
            stopNag(ctx, slot)
            if (intent.action == ACTION_DUE) scheduleSlot(ctx, slot)
            return
        }

        showDose(ctx, s, slot, date)
        val nagAt = System.currentTimeMillis() + s.nagMinutes.coerceAtLeast(1) * 60_000L
        setAlarm(ctx, nagAt, pending(ctx, ACTION_NAG, slot, dateText, reqNag(slot)))
        if (intent.action == ACTION_DUE) scheduleSlot(ctx, slot)
    }

    private fun showDose(ctx: Context, s: Settings, slot: Int, date: LocalDate) {
        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return

        val take = PendingIntent.getBroadcast(
            ctx, reqTake(slot),
            alarmIntent(ctx, ACTION_TAKE, slot, date.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            ctx, reqOpen(slot),
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or IMMUTABLE,
        )

        val due = Logic.fmtTime(Logic.slotMinutes(s, date, slot))
        val which = if (s.dosesPerDay > 1) "Dose ${slot + 1} of ${s.dosesPerDay} was due at $due." else "Due at $due."
        val text = "$which I'll remind you every ${s.nagMinutes} min until you tap Taken."

        val n = NotificationCompat.Builder(ctx, CH_DOSE)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("Time to take your ${s.medName}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setColor(0xFF00696E.toInt())
            .setContentIntent(open)
            .addAction(R.drawable.ic_check, "Taken", take)
            .build()
        try {
            nmc.notify(notifId(slot), n)
        } catch (_: SecurityException) {
        }
    }

    fun checkStock(ctx: Context) {
        val s = Store.data(ctx).settings
        if (s.pillsLeft < 0) return
        val days = Logic.daysOfSupply(s)
        val today = LocalDate.now().toString()
        if (days > s.lowStockDays || s.stockWarnDate == today) return
        Store.update(ctx) { it.copy(settings = it.settings.copy(stockWarnDate = today)) }

        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return
        val text = if (s.pillsLeft == 0) {
            "You've run out. Time to get your repeat prescription."
        } else {
            "${s.pillsLeft} left, about $days days. Time to order your repeat prescription."
        }
        val n = NotificationCompat.Builder(ctx, CH_STOCK)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("Running low on ${s.medName}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFF00696E.toInt())
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
            .setColor(0xFF00696E.toInt())
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
                val slot = intent.getIntExtra(Reminders.EXTRA_SLOT, 0)
                val date = intent.getStringExtra(Reminders.EXTRA_DATE)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                Logic.markTaken(context, date, slot)
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
