package com.rosehanzo.medtracker.data

import android.content.Context
import com.rosehanzo.medtracker.reminders.Reminders
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class DayStatus { TAKEN, MISSED, PENDING, OFF, NONE }

/** What the plan says for one day. [pills] is 0 on a day off or before the plan starts. */
data class PlanDay(val pills: Int, val phase: Phase?, val dayOff: Boolean)

object Logic {

    const val MAX_PILLS = 4

    fun isWeekend(s: Settings, date: LocalDate): Boolean = date.dayOfWeek.value in s.weekendDays

    /** Minutes after midnight when the dose is due on [date]. */
    fun doseMinutes(s: Settings, date: LocalDate): Int = if (isWeekend(s, date)) s.weekendTime else s.weekdayTime

    fun fmtTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun pillWord(n: Int): String = if (n == 1) "1 pill" else "$n pills"

    fun sortedPhases(s: Settings): List<Phase> = s.phases.sortedBy { it.start }

    /** The phase in force on [date]: the latest one that has started. Its amount carries on after its end date. */
    fun phaseFor(s: Settings, date: LocalDate): Phase? {
        val key = date.toString()
        return sortedPhases(s).lastOrNull { it.start <= key }
    }

    fun planFor(s: Settings, date: LocalDate): PlanDay {
        val phase = phaseFor(s, date) ?: return PlanDay(0, null, false)
        val off = date.dayOfWeek.value in phase.daysOff
        return PlanDay(if (off) 0 else phase.pills, phase, off)
    }

    fun taken(d: AppData, date: LocalDate): Boolean = d.doses.containsKey(date.toString())

    fun startDate(s: Settings): LocalDate =
        runCatching { LocalDate.parse(s.startDate) }.getOrDefault(LocalDate.now())

    fun dayStatus(d: AppData, date: LocalDate): DayStatus {
        val today = LocalDate.now()
        if (date.isAfter(today)) return DayStatus.NONE
        if (taken(d, date)) return DayStatus.TAKEN
        val plan = planFor(d.settings, date)
        if (plan.pills == 0) return if (plan.phase == null) DayStatus.NONE else DayStatus.OFF
        // Planned days count as missed even before Cadence was installed; mark them taken in the calendar.
        if (date == today) return DayStatus.PENDING
        return DayStatus.MISSED
    }

    /** How many days the pills you have will last, following the plan. -1 if the count isn't set. */
    fun daysOfSupply(d: AppData): Int {
        val s = d.settings
        if (s.pillsLeft < 0) return -1
        var left = s.pillsLeft
        var date = LocalDate.now()
        if (taken(d, date)) date = date.plusDays(1)
        var days = 0
        while (days < 730) {
            val need = planFor(s, date).pills
            if (need > left) break
            left -= need
            days++
            date = date.plusDays(1)
        }
        return days
    }

    fun markTaken(ctx: Context, date: LocalDate) {
        var changed = false
        Store.update(ctx) { d ->
            if (taken(d, date)) {
                d
            } else {
                changed = true
                val pills = planFor(d.settings, date).pills.coerceAtLeast(1)
                val s = d.settings
                val newSettings = if (s.pillsLeft >= 0) s.copy(pillsLeft = (s.pillsLeft - pills).coerceAtLeast(0)) else s
                d.copy(
                    doses = d.doses + (date.toString() to DoseTaken(System.currentTimeMillis(), pills)),
                    settings = newSettings,
                )
            }
        }
        Reminders.onTaken(ctx)
        if (changed) Reminders.checkStock(ctx)
    }

    fun unmarkTaken(ctx: Context, date: LocalDate) {
        Store.update(ctx) { d ->
            val dose = d.doses[date.toString()]
            if (dose == null) {
                d
            } else {
                val s = d.settings
                val newSettings = if (s.pillsLeft >= 0) s.copy(pillsLeft = s.pillsLeft + dose.pills) else s
                d.copy(doses = d.doses - date.toString(), settings = newSettings)
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

    fun savePhase(ctx: Context, phase: Phase) {
        updateSettings(ctx) { s ->
            val others = s.phases.filterNot { it.id == phase.id }
            s.copy(phases = (others + phase).sortedBy { it.start }, stockWarnDate = "")
        }
    }

    fun deletePhase(ctx: Context, id: String) {
        updateSettings(ctx) { s -> s.copy(phases = s.phases.filterNot { it.id == id }, stockWarnDate = "") }
    }

    // ---------- Forms ----------

    fun formOccurs(f: FormReminder, date: LocalDate): Boolean {
        val start = runCatching { LocalDate.parse(f.start) }.getOrNull() ?: return false
        if (date.isBefore(start)) return false
        if (f.everyWeeks <= 0) return date == start
        return ChronoUnit.DAYS.between(start, date) % (7L * f.everyWeeks) == 0L
    }

    /** Forms due on [date], earliest first. */
    fun formsOn(d: AppData, date: LocalDate): List<FormReminder> =
        d.forms.filter { formOccurs(it, date) }.sortedBy { it.time }

    fun formDone(f: FormReminder, date: LocalDate): Boolean = date.toString() in f.done

    /** Next date on or after [from] that [f] is due, or null if it never is again. */
    fun nextOccurrence(f: FormReminder, from: LocalDate): LocalDate? {
        val start = runCatching { LocalDate.parse(f.start) }.getOrNull() ?: return null
        if (!from.isAfter(start)) return start
        if (f.everyWeeks <= 0) return null
        val step = 7L * f.everyWeeks
        val gap = ChronoUnit.DAYS.between(start, from)
        val periods = (gap + step - 1) / step
        return start.plusDays(periods * step)
    }

    fun setFormDone(ctx: Context, id: String, date: LocalDate, done: Boolean) {
        val key = date.toString()
        Store.update(ctx) { d ->
            d.copy(forms = d.forms.map { f ->
                if (f.id != id) f else f.copy(done = if (done) f.done + key else f.done - key)
            })
        }
        Reminders.scheduleForms(ctx)
    }

    /** From the notification: marks the forms on [date] whose time has come as done (not later ones). */
    fun markFormsDone(ctx: Context, date: LocalDate) {
        val key = date.toString()
        val nowMinutes = if (date == LocalDate.now()) LocalTime.now().toSecondOfDay() / 60 else 24 * 60
        Store.update(ctx) { d ->
            d.copy(forms = d.forms.map { f ->
                if (formOccurs(f, date) && f.time <= nowMinutes) f.copy(done = f.done + key) else f
            })
        }
        Reminders.scheduleForms(ctx)
    }

    fun saveForm(ctx: Context, form: FormReminder) {
        Store.update(ctx) { d -> d.copy(forms = d.forms.filterNot { it.id == form.id } + form) }
        Reminders.scheduleForms(ctx)
    }

    fun deleteForm(ctx: Context, id: String) {
        Store.update(ctx) { d -> d.copy(forms = d.forms.filterNot { it.id == id }) }
        Reminders.scheduleForms(ctx)
    }

    /** Change settings; reschedules reminders unless [reschedule] is false. */
    fun updateSettings(ctx: Context, reschedule: Boolean = true, change: (Settings) -> Settings) {
        Store.update(ctx) { d -> d.copy(settings = change(d.settings)) }
        if (reschedule) Reminders.scheduleAll(ctx)
    }
}
