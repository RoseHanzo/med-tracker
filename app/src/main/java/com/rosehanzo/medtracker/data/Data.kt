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
    SymptomDef("mood", "Mood", "Very low", "Great", highIsGood = true),
    SymptomDef("focus", "Focus / energy", "Very low", "Excellent", highIsGood = true),
    SymptomDef("appetite", "Appetite", "None", "Normal", highIsGood = true),
    SymptomDef("sleep", "Sleep quality", "Very poor", "Great", highIsGood = true),
    SymptomDef("chest", "Chest tightness", "None", "Severe"),
    SymptomDef("drymouth", "Dry mouth", "None", "Severe"),
    SymptomDef("headache", "Headache", "None", "Severe"),
    SymptomDef("anxiety", "Anxiety / restlessness", "None", "Severe"),
    SymptomDef("irritability", "Irritability", "None", "Severe"),
    SymptomDef("stomach", "Stomach ache / nausea", "None", "Severe"),
    SymptomDef("palpitations", "Palpitations", "None", "Severe"),
    SymptomDef("dizziness", "Dizziness", "None", "Severe"),
)

data class Settings(
    val medName: String = "Xaggitin XL 18 mg",
    val dosesPerDay: Int = 1,
    /** Minutes after midnight for dose 1, 2, 3. */
    val weekdayTimes: List<Int> = listOf(8 * 60, 13 * 60, 18 * 60),
    val weekendTimes: List<Int> = listOf(9 * 60 + 30, 14 * 60, 19 * 60),
    /** java.time.DayOfWeek values: 1 = Monday … 7 = Sunday. */
    val weekendDays: Set<Int> = setOf(6, 7),
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
)

data class DoseTaken(val slot: Int, val time: Long)

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
    val settings: Settings = Settings(),
    /** Date (yyyy-MM-dd) -> doses taken that day. */
    val doses: Map<String, List<DoseTaken>> = emptyMap(),
    /** Date -> symptom id -> rating 1..10. */
    val checkins: Map<String, Map<String, Int>> = emptyMap(),
    val metrics: List<Metric> = emptyList(),
)

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
        so.put("dosesPerDay", s.dosesPerDay)
        so.put("weekdayTimes", JSONArray(s.weekdayTimes))
        so.put("weekendTimes", JSONArray(s.weekendTimes))
        so.put("weekendDays", JSONArray(s.weekendDays.toList()))
        so.put("remindersOn", s.remindersOn)
        so.put("nagMinutes", s.nagMinutes)
        so.put("pillsLeft", s.pillsLeft)
        so.put("lowStockDays", s.lowStockDays)
        so.put("hcAutoSync", s.hcAutoSync)
        so.put("hcLastSync", s.hcLastSync)
        so.put("startDate", s.startDate)
        so.put("stockWarnDate", s.stockWarnDate)
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
        d.doses.forEach { (date, list) ->
            val a = JSONArray()
            list.forEach { a.put(JSONObject().put("slot", it.slot).put("time", it.time)) }
            doses.put(date, a)
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

        val root = JSONObject()
        root.put("version", 1)
        root.put("settings", so)
        root.put("doses", doses)
        root.put("checkins", checkins)
        root.put("metrics", metrics)
        return root.toString()
    }

    private fun ints(a: JSONArray?, fallback: List<Int>): List<Int> {
        if (a == null) return fallback
        val list = List(a.length()) { a.getInt(it) }
        // Always keep three dose slots.
        return if (fallback.size == 3 && list.size < 3) list + fallback.drop(list.size) else list
    }

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

        val settings = Settings(
            medName = so.optString("medName", def.medName),
            dosesPerDay = so.optInt("dosesPerDay", def.dosesPerDay).coerceIn(1, 3),
            weekdayTimes = ints(so.optJSONArray("weekdayTimes"), def.weekdayTimes),
            weekendTimes = ints(so.optJSONArray("weekendTimes"), def.weekendTimes),
            weekendDays = ints(so.optJSONArray("weekendDays"), def.weekendDays.toList()).toSet(),
            remindersOn = so.optBoolean("remindersOn", def.remindersOn),
            nagMinutes = so.optInt("nagMinutes", def.nagMinutes),
            pillsLeft = so.optInt("pillsLeft", def.pillsLeft),
            lowStockDays = so.optInt("lowStockDays", def.lowStockDays),
            symptoms = stored + DEFAULT_SYMPTOMS.filter { it.id !in ids },
            hcAutoSync = so.optBoolean("hcAutoSync", def.hcAutoSync),
            hcLastSync = so.optLong("hcLastSync", 0L),
            startDate = so.optString("startDate", def.startDate),
            stockWarnDate = so.optString("stockWarnDate", ""),
        )

        val doses = mutableMapOf<String, List<DoseTaken>>()
        o.optJSONObject("doses")?.let { dob ->
            dob.keys().forEach { k ->
                val a = dob.getJSONArray(k)
                doses[k] = List(a.length()) { i ->
                    val x = a.getJSONObject(i)
                    DoseTaken(x.getInt("slot"), x.getLong("time"))
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

        return AppData(settings, doses, checkins, metrics.sortedBy { it.time })
    }
}
