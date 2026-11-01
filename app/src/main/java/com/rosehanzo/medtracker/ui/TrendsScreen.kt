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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosehanzo.medtracker.data.AppData
import com.rosehanzo.medtracker.data.DayStatus
import com.rosehanzo.medtracker.data.Logic
import java.time.LocalDate

private val RANGES = listOf(7, 30, 90)

private data class Bar(val x: Double, val pills: Int, val status: DayStatus)

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
        ScreenTitle("Trends", "How your dose, body and symptoms change together")

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            RANGES.forEachIndexed { i, n ->
                SegmentedButton(
                    selected = days == n,
                    onClick = { days = n },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = RANGES.size),
                ) { Text("$n days") }
            }
        }

        MedicationCard(data, start, today, xMin, xMax)

        val primary = MaterialTheme.colorScheme.primary
        val weight = data.metrics.filter { it.type == "weight" && inRange(it.time) }
        ChartCard(
            title = "Weight",
            icon = Icons.Filled.MonitorWeight,
            latest = weight.lastOrNull()?.let { "${fmt1(it.v1)} kg" },
            series = listOf(Series("Weight", primary, weight.map { dayX(it.time) to it.v1 })),
            start = start, xMin = xMin, xMax = xMax,
        )

        val bp = data.metrics.filter { it.type == "bp" && inRange(it.time) }
        ChartCard(
            title = "Blood pressure",
            icon = Icons.Filled.Favorite,
            latest = bp.lastOrNull()?.let { "${fmt1(it.v1)}/${fmt1(it.v2)}" },
            series = listOf(
                Series("Upper", Color(0xFFE0564F), bp.map { dayX(it.time) to it.v1 }, fill = false),
                Series("Lower", primary, bp.map { dayX(it.time) to it.v2 }, fill = false),
            ),
            start = start, xMin = xMin, xMax = xMax,
        )

        val pulse = data.metrics.filter { it.type == "pulse" && inRange(it.time) }
        ChartCard(
            title = "Pulse / heart rate",
            icon = Icons.Filled.MonitorHeart,
            latest = pulse.lastOrNull()?.let { "${fmt1(it.v1)} bpm" },
            series = listOf(Series("Pulse", Color(0xFF9C6ADE), pulse.map { dayX(it.time) to it.v1 })),
            start = start, xMin = xMin, xMax = xMax,
        )

        Text(
            "Check-in",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        val visible = data.settings.symptoms.filter { it.visible }
        if (visible.isEmpty()) {
            Text("Turn on what you want to track in Settings.", style = MaterialTheme.typography.bodyMedium, color = mutedText())
        }
        visible.forEach { def ->
            val pts = data.checkins.mapNotNull { (date, values) ->
                val v = values[def.id] ?: return@mapNotNull null
                val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@mapNotNull null
                if (d.isBefore(start) || d.isAfter(today)) null else (d.toEpochDay() + 0.5) to v.toDouble()
            }
            ChartCard(
                title = def.name,
                icon = Icons.Filled.Mood,
                subtitle = if (def.highIsGood) "Higher is better" else "Higher is worse",
                latest = pts.maxByOrNull { it.first }?.let { "${it.second.toInt()}/10" },
                series = listOf(Series(def.name, if (def.highIsGood) Color(0xFF2E9D6B) else Color(0xFFE88A3C), pts)),
                start = start, xMin = xMin, xMax = xMax,
                yMin = 1.0, yMax = 10.0, chartHeight = 130,
            )
        }
        Spacer(Modifier.size(8.dp))
    }
}

/** Pills taken each day, lined up with the charts below so you can compare. */
@Composable
private fun MedicationCard(data: AppData, start: LocalDate, today: LocalDate, xMin: Double, xMax: Double) {
    val s = data.settings
    val first = maxOf(start, Logic.startDate(s))
    var expected = 0
    var taken = 0
    var d = first
    while (!d.isAfter(today)) {
        if (Logic.planFor(s, d).pills > 0) {
            expected++
            if (Logic.taken(data, d)) taken++
        }
        d = d.plusDays(1)
    }

    val bars = generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { day ->
            val dose = data.doses[day.toString()]
            Bar(day.toEpochDay() + 0.5, dose?.pills ?: Logic.planFor(s, day).pills, Logic.dayStatus(data, day))
        }
        .toList()
    val maxPills = (bars.maxOfOrNull { it.pills } ?: 1).coerceAtLeast(1)

    val pending = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    SectionCard(
        "Medication",
        Icons.Filled.Medication,
        trailing = {
            if (expected > 0) {
                Text(
                    "${taken * 100 / expected}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) {
        Text(
            if (expected == 0) "No doses planned in this period" else "$taken of $expected doses taken",
            style = MaterialTheme.typography.bodyMedium,
            color = mutedText(),
        )
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val left = 34.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val span = (xMax - xMin).coerceAtLeast(1e-9)
            val dayWidth = (right - left) / span.toFloat()
            val barWidth = (dayWidth * 0.62f).coerceIn(2.dp.toPx(), 18.dp.toPx())

            for (p in 0..maxPills) {
                val y = bottom - (p.toFloat() / maxPills) * (bottom - top)
                drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = 1f)
                if (p > 0) {
                    drawText(measurer, "$p", topLeft = Offset(8.dp.toPx(), y - 8.dp.toPx()), style = labelStyle)
                }
            }
            bars.forEach { b ->
                if (b.pills <= 0) return@forEach
                val color = when (b.status) {
                    DayStatus.TAKEN -> GoodFill
                    DayStatus.MISSED -> BadFill.copy(alpha = 0.55f)
                    else -> pending
                }
                val cx = left + ((b.x - xMin) / span).toFloat() * (right - left)
                val h = (b.pills.toFloat() / maxPills) * (bottom - top)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx - barWidth / 2, bottom - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 3, barWidth / 3),
                )
            }
        }
        Box(Modifier.padding(start = 34.dp)) { DateEnds(start) }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(GoodFill, "Taken")
            LegendDot(BadFill.copy(alpha = 0.55f), "Missed")
            LegendDot(pending, "Due today")
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    icon: ImageVector,
    latest: String?,
    series: List<Series>,
    start: LocalDate,
    xMin: Double,
    xMax: Double,
    subtitle: String? = null,
    yMin: Double? = null,
    yMax: Double? = null,
    chartHeight: Int = 160,
) {
    SectionCard(
        title,
        icon,
        trailing = {
            if (latest != null) {
                Text(latest, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
    ) {
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = mutedText())
        }
        if (series.all { it.points.isEmpty() }) {
            Text("Nothing logged in this period", style = MaterialTheme.typography.bodySmall, color = mutedText())
        } else {
            LineChart(series, xMin, xMax, yMin, yMax, chartHeight.dp)
            Box(Modifier.padding(start = 34.dp)) { DateEnds(start) }
            if (series.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    series.forEach { LegendDot(it.color, it.name) }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = mutedText())
    }
}

@Composable
private fun DateEnds(start: LocalDate) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(shortDate(start), style = MaterialTheme.typography.labelSmall, color = mutedText())
        Text("Today", style = MaterialTheme.typography.labelSmall, color = mutedText())
    }
}
