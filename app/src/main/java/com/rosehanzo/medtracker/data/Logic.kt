package com.rosehanzo.medtracker.data

import android.content.Context
import com.rosehanzo.medtracker.reminders.Reminders
import java.time.LocalDate

enum class DayStatus { FULL, PARTIAL, MISSED, PENDING, NONE }

object Logic {

    fun isWeekend(s: Settings, date: LocalDate): Boolean = date.dayOfWeek.value in s.weekendDays

    /** Minutes after midnight when [slot] is due on [date]. */
    fun slotMinutes(s: Settings, date: LocalDate, slot: Int): Int {
        val times = if (isWeekend(s, date)) s.weekendTimes else s.weekdayTimes
        return times.getOrElse(slot) { 8 * 60 }
    }

    fun fmtTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun taken(d: AppData, date: LocalDate, slot: Int): Boolean =
        d.doses[date.toString()].orEmpty().any { it.slot == slot }

    fun takenCount(d: AppData, date: LocalDate): Int =
        d.doses[date.toString()].orEmpty().map { it.slot }.filter { it < d.settings.dosesPerDay }.toSet().size

    fun startDate(s: Settings): LocalDate =
        runCatching { LocalDate.parse(s.startDate) }.getOrDefault(LocalDate.now())

    fun dayStatus(d: AppData, date: LocalDate): DayStatus {
        val today = LocalDate.now()
        if (date.isAfter(today)) return DayStatus.NONE
        val n = takenCount(d, date)
        if (n >= d.settings.dosesPerDay) return DayStatus.FULL
        if (n > 0) return DayStatus.PARTIAL
        if (date.isBefore(startDate(d.settings))) return DayStatus.NONE
        if (date == today) return DayStatus.PENDING
        return DayStatus.MISSED
    }

    fun daysOfSupply(s: Settings): Int = if (s.pillsLeft < 0) -1 else s.pillsLeft / maxOf(1, s.dosesPerDay)

    fun markTaken(ctx: Context, date: LocalDate, slot: Int) {
        var changed = false
        Store.update(ctx) { d ->
            if (taken(d, date, slot)) {
                d
            } else {
                changed = true
                val key = date.toString()
                val list = d.doses[key].orEmpty() + DoseTaken(slot, System.currentTimeMillis())
                val s = d.settings
                val newSettings = if (s.pillsLeft > 0) s.copy(pillsLeft = s.pillsLeft - 1) else s
                d.copy(doses = d.doses + (key to list), settings = newSettings)
            }
        }
        Reminders.onTaken(ctx, slot)
        if (changed) Reminders.checkStock(ctx)
    }

    fun unmarkTaken(ctx: Context, date: LocalDate, slot: Int) {
        Store.update(ctx) { d ->
            if (!taken(d, date, slot)) {
                d
            } else {
                val key = date.toString()
                val list = d.doses[key].orEmpty().filterNot { it.slot == slot }
                val s = d.settings
                val newSettings = if (s.pillsLeft >= 0) s.copy(pillsLeft = s.pillsLeft + 1) else s
                val doses = if (list.isEmpty()) d.doses - key else d.doses + (key to list)
                d.copy(doses = doses, settings = newSettings)
            }
        }
        Reminders.scheduleAll(ctx)
    }

    fun saveCheckin(ctx: Context, date: LocalDate, values: Map<String, Int>) {
        val key = date.toString()
        Store.update(ctx) { d ->
            d.copy(checkins = if (values.isEmpty()) d.checkins - key else d.checkins + (key to values))
        }
    }

    fun addMetrics(ctx: Context, list: List<Metric>) {
        Store.update(ctx) { d -> d.copy(metrics = (d.metrics + list).sortedBy { it.time }) }
    }

    fun deleteMetric(ctx: Context, id: String) {
        Store.update(ctx) { d -> d.copy(metrics = d.metrics.filterNot { it.id == id }) }
    }

    /** Change settings; reschedules reminders unless [reschedule] is false. */
    fun updateSettings(ctx: Context, reschedule: Boolean = true, change: (Settings) -> Settings) {
        Store.update(ctx) { d -> d.copy(settings = change(d.settings)) }
        if (reschedule) Reminders.scheduleAll(ctx)
    }
}
