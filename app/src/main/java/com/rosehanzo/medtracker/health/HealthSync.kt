package com.rosehanzo.medtracker.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.rosehanzo.medtracker.data.Metric
import com.rosehanzo.medtracker.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Reads weight, blood pressure and heart rate from Health Connect.
 * Samsung Health shares watch and scale readings with Health Connect.
 */
object HealthSync {
    val WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
    val BLOOD_PRESSURE = HealthPermission.getReadPermission(BloodPressureRecord::class)
    val HEART_RATE = HealthPermission.getReadPermission(HeartRateRecord::class)
    val PERMISSIONS: Set<String> = setOf(WEIGHT, BLOOD_PRESSURE, HEART_RATE)

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

        val s = Store.data(ctx).settings
        val zone = ZoneId.systemDefault()
        // Health Connect only lets apps read the last 30 days by default.
        val floor = LocalDate.now().minusDays(29).atStartOfDay(zone).toInstant()
        val fromLast = if (s.hcLastSync > 0) {
            Instant.ofEpochMilli(s.hcLastSync).atZone(zone).toLocalDate()
                .minusDays(1).atStartOfDay(zone).toInstant()
        } else {
            floor
        }
        val since = if (fromLast.isAfter(floor)) fromLast else floor

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

        if (HEART_RATE in granted) {
            // Watches record heart rate constantly, so keep one daily average.
            val samples = readAll(client, HeartRateRecord::class, since).flatMap { it.samples }
            samples.groupBy { it.time.atZone(zone).toLocalDate() }.forEach { (day, list) ->
                found += Metric(
                    id = "hc-hr-$day", type = "pulse",
                    time = list.maxOf { it.time }.toEpochMilli(),
                    v1 = list.map { it.beatsPerMinute.toDouble() }.average(),
                    source = "hc",
                )
            }
        }

        val ids = found.map { it.id }.toSet()
        Store.update(ctx) { d ->
            d.copy(
                metrics = (d.metrics.filterNot { it.id in ids } + found).sortedBy { it.time },
                settings = d.settings.copy(hcLastSync = System.currentTimeMillis()),
            )
        }
        when (found.size) {
            0 -> "Up to date, no new readings"
            1 -> "Synced 1 reading"
            else -> "Synced ${found.size} readings"
        }
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
