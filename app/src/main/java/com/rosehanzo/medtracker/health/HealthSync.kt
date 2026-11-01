package com.rosehanzo.medtracker.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.rosehanzo.medtracker.data.Metric
import com.rosehanzo.medtracker.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Reads weight, blood pressure, heart rate, sleep and steps from Health Connect.
 * Samsung Health shares watch and scale readings with Health Connect.
 * From heart rate and sleep it also works out sleeping heart rate and heart rate after each dose.
 */
object HealthSync {
    val WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
    val BLOOD_PRESSURE = HealthPermission.getReadPermission(BloodPressureRecord::class)
    val HEART_RATE = HealthPermission.getReadPermission(HeartRateRecord::class)
    val SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val STEPS = HealthPermission.getReadPermission(StepsRecord::class)
    val PERMISSIONS: Set<String> = setOf(WEIGHT, BLOOD_PRESSURE, HEART_RATE, SLEEP, STEPS)

    private val NOT_ASLEEP = setOf(
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
    )

    fun status(ctx: Context): Int = HealthConnectClient.getSdkStatus(ctx)

    fun available(ctx: Context): Boolean = status(ctx) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(ctx: Context): Set<String> = withContext(Dispatchers.IO) {
        if (!available(ctx)) return@withContext emptySet<String>()
        runCatching {
            HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()
                .intersect(PERMISSIONS)
        }.getOrDefault(emptySet())
    }

    /** Pulls new readings into the app. Returns a short message for the user. */
    suspend fun sync(ctx: Context): String = withContext(Dispatchers.IO) {
        if (!available(ctx)) return@withContext "Health Connect isn't available on this phone"
        val client = HealthConnectClient.getOrCreate(ctx)
        val granted = client.permissionController.getGrantedPermissions()
        if (granted.none { it in PERMISSIONS }) {
            return@withContext "Connect Samsung Health in Settings first"
        }

        val zone = ZoneId.systemDefault()
        // Always read the full 30 days Health Connect allows: Samsung Health can share
        // older readings late (e.g. right after sharing is turned on). Duplicates are
        // replaced by id below, so re-reading is safe.
        val since = LocalDate.now().minusDays(29).atStartOfDay(zone).toInstant()

        val found = mutableListOf<Metric>()

        if (WEIGHT in granted) {
            readAll(client, WeightRecord::class, since).forEach { r ->
                found += Metric(
                    id = "hc-w-${r.metadata.id}", type = "weight",
                    time = r.time.toEpochMilli(), v1 = r.weight.inKilograms, source = "hc",
                )
            }
        }

        if (BLOOD_PRESSURE in granted) {
            readAll(client, BloodPressureRecord::class, since).forEach { r ->
                found += Metric(
                    id = "hc-bp-${r.metadata.id}", type = "bp", time = r.time.toEpochMilli(),
                    v1 = r.systolic.inMillimetersOfMercury, v2 = r.diastolic.inMillimetersOfMercury,
                    source = "hc",
                )
            }
        }

        val samples = if (HEART_RATE in granted) {
            readAll(client, HeartRateRecord::class, since).flatMap { it.samples }.sortedBy { it.time }
        } else {
            emptyList()
        }
        fun avgBetween(from: Instant, to: Instant): Double? =
            samples.filter { !it.time.isBefore(from) && it.time.isBefore(to) }
                .map { it.beatsPerMinute.toDouble() }.takeIf { it.isNotEmpty() }?.average()

        if (samples.isNotEmpty()) {
            // Watches record heart rate constantly, so keep one daily average.
            samples.groupBy { it.time.atZone(zone).toLocalDate() }.forEach { (day, list) ->
                found += Metric(
                    id = "hc-hr-$day", type = "pulse",
                    time = list.maxOf { it.time }.toEpochMilli(),
                    v1 = list.map { it.beatsPerMinute.toDouble() }.average(),
                    source = "hc",
                )
            }
            // Heart rate in the 4 hours after each dose (v1), and the 2 hours before (v2).
            val data = Store.data(ctx)
            data.doses.forEach { (date, dose) ->
                val t = Instant.ofEpochMilli(dose.time)
                if (t.isBefore(since)) return@forEach
                val after = avgBetween(t, t.plus(Duration.ofHours(4))) ?: return@forEach
                val before = avgBetween(t.minus(Duration.ofHours(2)), t) ?: 0.0
                found += Metric("hc-dhr-$date", "dosehr", dose.time, after, before, source = "hc")
            }
        }

        if (SLEEP in granted) {
            // One night per wake-up day: hours asleep (v1) and bedtime as minutes after noon (v2).
            val sessions = readAll(client, SleepSessionRecord::class, since)
            sessions.groupBy { it.endTime.atZone(zone).toLocalDate() }.forEach { (day, list) ->
                val asleepMs = list.sumOf { session ->
                    if (session.stages.isEmpty()) {
                        Duration.between(session.startTime, session.endTime).toMillis()
                    } else {
                        session.stages.filter { it.stage !in NOT_ASLEEP }
                            .sumOf { Duration.between(it.startTime, it.endTime).toMillis() }
                    }
                }
                val main = list.maxByOrNull { Duration.between(it.startTime, it.endTime) } ?: return@forEach
                val bed = main.startTime.atZone(zone).toLocalTime()
                val bedAfterNoon = ((bed.hour * 60 + bed.minute) - 12 * 60 + 24 * 60) % (24 * 60)
                found += Metric(
                    "hc-sleep-$day", "sleep", main.endTime.toEpochMilli(),
                    asleepMs / 3_600_000.0, bedAfterNoon.toDouble(), source = "hc",
                )
                avgBetween(main.startTime, main.endTime)?.let {
                    found += Metric("hc-shr-$day", "sleephr", main.endTime.toEpochMilli(), it, source = "hc")
                }
            }
        }

        if (STEPS in granted) {
            // Health Connect's daily totals remove overlaps between phone and watch.
            val startLocal = LocalDate.now().minusDays(29).atStartOfDay()
            val endLocal = LocalDate.now().plusDays(1).atStartOfDay()
            val days = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startLocal, endLocal),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            )
            days.forEach { bucket ->
                val count = bucket.result[StepsRecord.COUNT_TOTAL] ?: return@forEach
                val day = bucket.startTime.toLocalDate()
                found += Metric(
                    "hc-steps-$day", "steps",
                    day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                    count.toDouble(), source = "hc",
                )
            }
        }

        val ids = found.map { it.id }.toSet()
        val known = Store.data(ctx).metrics.map { it.id }.toSet()
        val fresh = found.count { it.id !in known }
        Store.update(ctx) { d ->
            d.copy(
                metrics = (d.metrics.filterNot { it.id in ids } + found).sortedBy { it.time },
                settings = d.settings.copy(hcLastSync = System.currentTimeMillis()),
            )
        }
        when {
            found.isEmpty() -> "No readings in Health Connect for the last 30 days"
            fresh == 0 -> "Up to date (${found.size} readings from the last 30 days)"
            fresh == 1 -> "Synced 1 new reading"
            else -> "Synced $fresh new readings"
        }
    }

    /** One line of the Health Connect check. */
    data class CheckRow(
        val label: String,
        val permitted: Boolean,
        val count: Int,
        val sources: List<String>,
        val latest: Instant?,
        val error: String?,
    )

    private fun appName(pkg: String): String = when (pkg) {
        "com.sec.android.app.shealth" -> "Samsung Health"
        "com.google.android.apps.fitness" -> "Google Fit"
        "com.google.android.apps.healthdata" -> "Health Connect"
        else -> pkg
    }

    /**
     * Looks at what Health Connect itself holds for the last 30 days, regardless of what
     * Cadence has already synced. Shows whether the gap is Samsung Health → Health Connect
     * or Health Connect → Cadence.
     */
    suspend fun check(ctx: Context): List<CheckRow> = withContext(Dispatchers.IO) {
        val client = HealthConnectClient.getOrCreate(ctx)
        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        val since = LocalDate.now().minusDays(29).atStartOfDay(ZoneId.systemDefault()).toInstant()

        suspend fun <T : Record> row(label: String, perm: String, type: KClass<T>, timeOf: (T) -> Instant): CheckRow {
            if (perm !in granted) return CheckRow(label, false, 0, emptyList(), null, null)
            return try {
                val records = readAll(client, type, since)
                CheckRow(
                    label = label,
                    permitted = true,
                    count = records.size,
                    sources = records.map { appName(it.metadata.dataOrigin.packageName) }.distinct(),
                    latest = records.maxOfOrNull(timeOf),
                    error = null,
                )
            } catch (e: Exception) {
                CheckRow(label, true, 0, emptyList(), null, e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
            }
        }

        listOf(
            row("Weight", WEIGHT, WeightRecord::class) { it.time },
            row("Blood pressure", BLOOD_PRESSURE, BloodPressureRecord::class) { it.time },
            row("Heart rate", HEART_RATE, HeartRateRecord::class) { it.endTime },
            row("Sleep", SLEEP, SleepSessionRecord::class) { it.endTime },
            row("Steps", STEPS, StepsRecord::class) { it.endTime },
        )
    }

    private suspend fun <T : Record> readAll(
        client: HealthConnectClient,
        type: KClass<T>,
        start: Instant,
    ): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.after(start),
                    pageToken = token,
                ),
            )
            out += response.records
            token = response.pageToken
        } while (!token.isNullOrEmpty())
        return out
    }
}
