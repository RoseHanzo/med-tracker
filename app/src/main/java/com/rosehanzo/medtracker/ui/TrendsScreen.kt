package com.rosehanzo.medtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rosehanzo.medtracker.data.AppData
import com.rosehanzo.medtracker.data.DayStatus
import com.rosehanzo.medtracker.data.Logic
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(data: AppData) {
    var days by rememberSaveable { mutableIntStateOf(30) }
    val today = LocalDate.now()
    val start = today.minusDays(days - 1L)
    val xMin = start.toEpochDay().toDouble()
    val xMax = today.toEpochDay() + 1.0
    val inRange = { millis: Long -> dayX(millis) >= xMin }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Trends", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { n ->
                FilterChip(selected = days == n, onClick = { days = n }, label = { Text("$n days") })
            }
        }

        AdherenceCard(data, start, today)

        val primary = MaterialTheme.colorScheme.primary
        val weight = data.metrics.filter { it.type == "weight" && inRange(it.time) }
        ChartCard(
            title = "Weight",
            latest = weight.lastOrNull()?.let { "${fmt1(it.v1)} kg" },
            series = listOf(Series("Weight", primary, weight.map { dayX(it.time) to it.v1 })),
            start = start, today = today, xMin = xMin, xMax = xMax,
        )

        val bp = data.metrics.filter { it.type == "bp" && inRange(it.time) }
        ChartCard(
            title = "Blood pressure",
            latest = bp.lastOrNull()?.let { "${fmt1(it.v1)}/${fmt1(it.v2)}" },
            series = listOf(
                Series("Upper (systolic)", Color(0xFFE57373), bp.map { dayX(it.time) to it.v1 }),
                Series("Lower (diastolic)", primary, bp.map { dayX(it.time) to it.v2 }),
            ),
            start = start, today = today, xMin = xMin, xMax = xMax,
        )

        val pulse = data.metrics.filter { it.type == "pulse" && inRange(it.time) }
        ChartCard(
            title = "Pulse / heart rate",
            latest = pulse.lastOrNull()?.let { "${fmt1(it.v1)} bpm" },
            series = listOf(Series("Pulse", Color(0xFFBA68C8), pulse.map { dayX(it.time) to it.v1 })),
            start = start, today = today, xMin = xMin, xMax = xMax,
        )

        Text("Check-in ratings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val visible = data.settings.symptoms.filter { it.visible }
        if (visible.isEmpty()) {
            Text(
                "Turn on what you want to track in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        visible.forEach { def ->
            val pts = data.checkins.mapNotNull { (date, values) ->
                val v = values[def.id] ?: return@mapNotNull null
                val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@mapNotNull null
                if (d.isBefore(start) || d.isAfter(today)) null else (d.toEpochDay() + 0.5) to v.toDouble()
            }
            ChartCard(
                title = def.name,
                subtitle = if (def.highIsGood) "1 = ${def.low.lowercase()}, 10 = ${def.high.lowercase()} (higher is better)"
                else "1 = ${def.low.lowercase()}, 10 = ${def.high.lowercase()} (higher is worse)",
                latest = pts.maxByOrNull { it.first }?.let { "${it.second.toInt()}/10" },
                series = listOf(Series(def.name, if (def.highIsGood) Color(0xFF4DB6AC) else Color(0xFFFF8A65), pts)),
                start = start, today = today, xMin = xMin, xMax = xMax,
                yMin = 1.0, yMax = 10.0, chartHeight = 130,
            )
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun AdherenceCard(data: AppData, start: LocalDate, today: LocalDate) {
    val s = data.settings
    val first = maxOf(start, Logic.startDate(s))
    var expected = 0
    var taken = 0
    var d = first
    while (!d.isAfter(today)) {
        expected += s.dosesPerDay
        taken += Logic.takenCount(data, d)
        d = d.plusDays(1)
    }
    val statuses = generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { Logic.dayStatus(data, it) }
        .toList()
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh

    SectionCard("Medication taken") {
        if (expected == 0) {
            Text("No days to show yet", style = MaterialTheme.typography.bodyMedium)
        } else {
            val pct = taken * 100 / expected
            Text(
                "$taken of $expected doses · $pct%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val n = statuses.size.coerceAtLeast(1)
            val gap = if (n > 40) 1.dp.toPx() else 3.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            statuses.forEachIndexed { i, st ->
                val color = when (st) {
                    DayStatus.FULL -> GoodFill
                    DayStatus.PARTIAL -> WarnFill
                    DayStatus.MISSED -> BadFill
                    else -> empty
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * (w + gap), 0f),
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
            }
        }
        DateEnds(start, today)
    }
}

@Composable
private fun ChartCard(
    title: String,
    latest: String?,
    series: List<Series>,
    start: LocalDate,
    today: LocalDate,
    xMin: Double,
    xMax: Double,
    subtitle: String? = null,
    yMin: Double? = null,
    yMax: Double? = null,
    chartHeight: Int = 170,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (latest != null) {
                Text(latest, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (series.all { it.points.isEmpty() }) {
            Text(
                "Nothing logged in this period",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LineChart(series, xMin, xMax, yMin, yMax, chartHeight.dp)
            Box(Modifier.padding(start = 34.dp)) { DateEnds(start, today) }
            if (series.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    series.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(s.color))
                            Spacer(Modifier.size(4.dp))
                            Text(s.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateEnds(start: LocalDate, end: LocalDate) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val style = MaterialTheme.typography.labelSmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(shortDate(start), style = style, color = color)
        Text("Today", style = style, color = color)
    }
}
