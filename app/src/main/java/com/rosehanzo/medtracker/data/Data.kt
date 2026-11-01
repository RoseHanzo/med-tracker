package com.rosehanzo.medtracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** One thing rated 1–10 in the daily check-in. */
data class SymptomDef(
    val id: String,
    val name: String,
    val low: String,
    val high: String,
    val highIsGood: Boolean = false,
    val visible: Boolean = true,
    val custom: Boolean = false,
)

val DEFAULT_SYMPTOMS = listOf(
    // Every scale runs from 1 = no impact to 10 = the most impact.
    SymptomDef("mood", "Low mood", "None", "Severe"),
    SymptomDef("focus", "Low focus / energy", "None", "Severe"),
    SymptomDef("appetite", "Low appetite", "None", "Severe"),
    SymptomDef("sleep", "Poor sleep", "None", "Severe"),
    SymptomDef("chest", "Chest tightness", "None", "Severe"),
    SymptomDef("drymouth", "Dry mouth", "None", "Severe"),
    SymptomDef("headache", "Headache", "None", "Severe"),
    SymptomDef("anxiety", "Anxiety / restlessness", "None", "Severe"),
    SymptomDef("irritability", "Irritability", "None", "Severe"),
    SymptomDef("stomach", "Stomach ache / nausea", "None", "Severe"),
    SymptomDef("palpitations", "Palpitations", "None", "Severe"),
    SymptomDef("dizziness", "Dizziness", "None", "Severe"),
)

/**
 * A stretch of the dose plan. Runs from [start] until the next phase starts.
 * [end] is optional and only shown for reference: after it, the same amount
 * carries on until a later phase begins.
 */
data class Phase(
    val id: String,
    val start: String,
    val end: String? = null,
    val pills: Int = 1,
    /** java.time.DayOfWeek values (1 = Monday … 7 = Sunday) with no pills. */
    val daysOff: Set<Int> = emptySet(),
)

data class Settings(
    val medName: String = "Xaggitin XL 18 mg",
    /** Minutes after midnight. */
    val weekdayTime: Int = 8 * 60,
    val weekendTime: Int = 9 * 60 + 30,
    /** java.time.DayOfWeek values: 1 = Monday … 7 = Sunday. */
    val weekendDays: Set<Int> = setOf(6, 7),
    val phases: List<Phase> = emptyList(),
    val remindersOn: Boolean = true,
    val nagMinutes: Int = 10,
    /** -1 means "not set". */
    val pillsLeft: Int = -1,
    val lowStockDays: Int = 7,
    val symptoms: List<SymptomDef> = DEFAULT_SYMPTOMS,
    val hcAutoSync: Boolean = true,
    val hcLastSync: Long = 0L,
    val startDate: String = LocalDate.now().toString(),
    val stockWarnDate: String = "",
    /** Date the pill count was last set or topped up. */
    val lastRefill: String = "",
)

/** A reminder to order more pills: [due] is the day supply reached the warning level. */
data class OrderEvent(val due: String, val done: String? = null)

/**
 * A form to fill in. Occurs on [start] and then every [everyWeeks] weeks (0 = once).
 * [done] holds the dates (yyyy-MM-dd) it was marked done.
 */
data class FormReminder(
    val id: String,
    val name: String = "Form",
    val start: String,
    /** Minutes after midnight. */
    val time: Int = 10 * 60,
    val everyWeeks: Int = 0,
    val done: Set<String> = emptySet(),
)

/** The day's dose: taken once, [pills] at a time. */
data class DoseTaken(val time: Long, val pills: Int)

/** type is "weight" (kg), "bp" (v1 systolic, v2 diastolic) or "pulse" (bpm). */
data class Metric(
    val id: String,
    val type: String,
    val time: Long,
    val v1: Double,
    val v2: Double = 0.0,
    val source: String = "manual",
)

data class AppData(
    val settings: Settings = defaultSettings(),
    /** Date (yyyy-MM-dd) -> the dose taken that day. */
    val doses: Map<String, DoseTaken> = emptyMap(),
    /** Date -> symptom id -> rating 1..10. */
    val checkins: Map<String, Map<String, Int>> = emptyMap(),
    val metrics: List<Metric> = emptyList(),
    val forms: List<FormReminder> = emptyList(),
    /** Dates (yyyy-MM-dd) you marked as missed. */
    val missed: Set<String> = emptySet(),
    val orders: List<OrderEvent> = emptyList(),
)

fun defaultSettings(): Settings {
    val s = Settings()
    return s.copy(phases = listOf(Phase("p0", s.startDate, null, 1)))
}

object Store {
    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> = _state
    private var file: File? = null

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "data.json")
        file = f
        _state.value = if (f.exists()) {
            runCatching { Codec.decode(f.readText()) }.getOrElse { AppData() }
        } else {
            AppData().also { save(it) }
        }
    }

    fun data(ctx: Context): AppData {
        init(ctx)
        return _state.value
    }

    @Synchronized
    fun update(ctx: Context, change: (AppData) -> AppData) {
        init(ctx)
        val next = change(_state.value)
        _state.value = next
        save(next)
    }

    @Synchronized
    fun replaceAll(ctx: Context, data: AppData) {
        init(ctx)
        _state.value = data
        save(data)
    }

    private fun save(d: AppData) {
        val f = file ?: return
        val tmp = File(f.path + ".tmp")
        tmp.writeText(Codec.encode(d))
        if (!tmp.renameTo(f)) {
            f.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

object Codec {
    fun encode(d: AppData): String {
        val s = d.settings
        val so = JSONObject()
        so.put("medName", s.medName)
        so.put("weekdayTime", s.weekdayTime)
        so.put("weekendTime", s.weekendTime)
        so.put("weekendDays", JSONArray(s.weekendDays.toList()))
        val phases = JSONArray()
        s.phases.forEach {
            val x = JSONObject()
            x.put("id", it.id)
            x.put("start", it.start)
            if (it.end != null) x.put("end", it.end)
            x.put("pills", it.pills)
            x.put("daysOff", JSONArray(it.daysOff.toList()))
            phases.put(x)
        }
        so.put("phases", phases)
        so.put("remindersOn", s.remindersOn)
        so.put("nagMinutes", s.nagMinutes)
        so.put("pillsLeft", s.pillsLeft)
        so.put("lowStockDays", s.lowStockDays)
        so.put("hcAutoSync", s.hcAutoSync)
        so.put("hcLastSync", s.hcLastSync)
        so.put("startDate", s.startDate)
        so.put("stockWarnDate", s.stockWarnDate)
        so.put("lastRefill", s.lastRefill)
        val syms = JSONArray()
        s.symptoms.forEach {
            val x = JSONObject()
            x.put("id", it.id)
            x.put("name", it.name)
            x.put("low", it.low)
            x.put("high", it.high)
            x.put("highIsGood", it.highIsGood)
            x.put("visible", it.visible)
            x.put("custom", it.custom)
            syms.put(x)
        }
        so.put("symptoms", syms)

        val doses = JSONObject()
        d.doses.forEach { (date, dose) ->
            doses.put(date, JSONObject().put("time", dose.time).put("pills", dose.pills))
        }

        val checkins = JSONObject()
        d.checkins.forEach { (date, values) ->
            val x = JSONObject()
            values.forEach { (k, v) -> x.put(k, v) }
            checkins.put(date, x)
        }

        val metrics = JSONArray()
        d.metrics.forEach {
            val x = JSONObject()
            x.put("id", it.id)
            x.put("type", it.type)
            x.put("time", it.time)
            x.put("v1", it.v1)
            x.put("v2", it.v2)
            x.put("source", it.source)
            metrics.put(x)
        }

        val forms = JSONArray()
        d.forms.forEach {
            val x = JSONObject()
            x.put("id", it.id)
            x.put("name", it.name)
            x.put("start", it.start)
            x.put("time", it.time)
            x.put("everyWeeks", it.everyWeeks)
            x.put("done", JSONArray(it.done.toList()))
            forms.put(x)
        }

        val root = JSONObject()
        root.put("version", 3)
        root.put("forms", forms)
        root.put("missed", JSONArray(d.missed.toList()))
        val orders = JSONArray()
        d.orders.forEach {
            val x = JSONObject().put("due", it.due)
            if (it.done != null) x.put("done", it.done)
            orders.put(x)
        }
        root.put("orders", orders)
        root.put("settings", so)
        root.put("doses", doses)
        root.put("checkins", checkins)
        root.put("metrics", metrics)
        return root.toString()
    }

    private fun ints(a: JSONArray?): List<Int>? = a?.let { List(it.length()) { i -> it.getInt(i) } }

    fun decode(text: String): AppData {
        val o = JSONObject(text)
        val so = o.optJSONObject("settings") ?: JSONObject()
        val def = Settings()

        val stored = so.optJSONArray("symptoms")?.let { a ->
            List(a.length()) { i ->
                val x = a.getJSONObject(i)
                SymptomDef(
                    id = x.getString("id"),
                    name = x.optString("name"),
                    low = x.optString("low"),
                    high = x.optString("high"),
                    highIsGood = x.optBoolean("highIsGood", false),
                    visible = x.optBoolean("visible", true),
                    custom = x.optBoolean("custom", false),
                )
            }
        } ?: DEFAULT_SYMPTOMS
        val ids = stored.map { it.id }.toSet()

        val startDate = so.optString("startDate", def.startDate)

        // Version 1 stored three reminder times per day; keep the first.
        val weekdayTime = if (so.has("weekdayTime")) so.getInt("weekdayTime")
        else ints(so.optJSONArray("weekdayTimes"))?.firstOrNull() ?: def.weekdayTime
        val weekendTime = if (so.has("weekendTime")) so.getInt("weekendTime")
        else ints(so.optJSONArray("weekendTimes"))?.firstOrNull() ?: def.weekendTime

        val phases = so.optJSONArray("phases")?.let { a ->
            List(a.length()) { i ->
                val x = a.getJSONObject(i)
                Phase(
                    id = x.optString("id", "p$i"),
                    start = x.getString("start"),
                    end = if (x.has("end") && !x.isNull("end")) x.getString("end") else null,
                    pills = x.optInt("pills", 1),
                    daysOff = ints(x.optJSONArray("daysOff"))?.toSet() ?: emptySet(),
                )
            }
        } ?: listOf(Phase("p0", startDate, null, 1))

        val settings = Settings(
            medName = so.optString("medName", def.medName),
            weekdayTime = weekdayTime,
            weekendTime = weekendTime,
            weekendDays = ints(so.optJSONArray("weekendDays"))?.toSet() ?: def.weekendDays,
            phases = phases,
            remindersOn = so.optBoolean("remindersOn", def.remindersOn),
            nagMinutes = so.optInt("nagMinutes", def.nagMinutes),
            pillsLeft = so.optInt("pillsLeft", def.pillsLeft),
            lowStockDays = so.optInt("lowStockDays", def.lowStockDays),
            symptoms = stored + DEFAULT_SYMPTOMS.filter { it.id !in ids },
            hcAutoSync = so.optBoolean("hcAutoSync", def.hcAutoSync),
            hcLastSync = so.optLong("hcLastSync", 0L),
            startDate = startDate,
            stockWarnDate = so.optString("stockWarnDate", ""),
            lastRefill = so.optString("lastRefill", ""),
        )

        val doses = mutableMapOf<String, DoseTaken>()
        o.optJSONObject("doses")?.let { dob ->
            dob.keys().forEach { k ->
                when (val v = dob.get(k)) {
                    is JSONObject -> doses[k] = DoseTaken(v.getLong("time"), v.optInt("pills", 1))
                    // Version 1: a list of separate doses. Keep the first as the day's dose.
                    is JSONArray -> if (v.length() > 0) {
                        doses[k] = DoseTaken(v.getJSONObject(0).getLong("time"), v.length())
                    }
                }
            }
        }

        val checkins = mutableMapOf<String, Map<String, Int>>()
        o.optJSONObject("checkins")?.let { cob ->
            cob.keys().forEach { k ->
                val x = cob.getJSONObject(k)
                val values = mutableMapOf<String, Int>()
                x.keys().forEach { id -> values[id] = x.getInt(id) }
                checkins[k] = values
            }
        }

        val metrics = o.optJSONArray("metrics")?.let { a ->
            List(a.length()) { i ->
                val x = a.getJSONObject(i)
                Metric(
                    id = x.getString("id"),
                    type = x.getString("type"),
                    time = x.getLong("time"),
                    v1 = x.getDouble("v1"),
                    v2 = x.optDouble("v2", 0.0),
                    source = x.optString("source", "manual"),
                )
            }
        } ?: emptyList()

        val forms = o.optJSONArray("forms")?.let { a ->
            List(a.length()) { i ->
                val x = a.getJSONObject(i)
                val doneArr = x.optJSONArray("done")
                FormReminder(
                    id = x.optString("id", "f$i"),
                    name = x.optString("name", "Form"),
                    start = x.getString("start"),
                    time = x.optInt("time", 10 * 60),
                    everyWeeks = x.optInt("everyWeeks", 0),
                    done = doneArr?.let { arr -> List(arr.length()) { j -> arr.getString(j) }.toSet() } ?: emptySet(),
                )
            }
        } ?: emptyList()

        val missed = o.optJSONArray("missed")?.let { a -> List(a.length()) { a.getString(it) }.toSet() } ?: emptySet()

        val orders = o.optJSONArray("orders")?.let { a ->
            List(a.length()) { i ->
                val x = a.getJSONObject(i)
                OrderEvent(x.getString("due"), if (x.has("done") && !x.isNull("done")) x.getString("done") else null)
            }
        } ?: emptyList()

        val loaded = AppData(settings, doses, checkins, metrics.sortedBy { it.time }, forms, missed, orders)
        return if (o.optInt("version", 1) < 3) alignScales(loaded) else loaded
    }

    /**
     * Before version 3 some items ran "higher is better" (e.g. mood). Now every scale
     * runs 1 = no impact to 10 = most impact, so those items are renamed and their
     * saved ratings flipped (v becomes 11 - v).
     */
    private fun alignScales(d: AppData): AppData {
        val flip = d.settings.symptoms.filter { it.highIsGood }.map { it.id }.toSet()
        if (flip.isEmpty()) return d
        val symptoms = d.settings.symptoms.map { def ->
            val fresh = DEFAULT_SYMPTOMS.firstOrNull { it.id == def.id }
            when {
                fresh != null -> def.copy(name = fresh.name, low = fresh.low, high = fresh.high, highIsGood = false)
                def.highIsGood -> def.copy(low = def.high, high = def.low, highIsGood = false)
                else -> def
            }
        }
        val checkins = d.checkins.mapValues { (_, values) ->
            values.mapValues { (id, v) -> if (id in flip) (11 - v).coerceIn(1, 10) else v }
        }
        return d.copy(settings = d.settings.copy(symptoms = symptoms), checkins = checkins)
    }
}
