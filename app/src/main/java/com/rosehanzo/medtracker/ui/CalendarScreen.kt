package com.rosehanzo.medtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rosehanzo.medtracker.data.AppData
import com.rosehanzo.medtracker.data.DayStatus
import com.rosehanzo.medtracker.data.Logic
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(data: AppData) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Calendar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            MonthGrid(data, month, selected) { selected = it }
            Legend()
        }

        DayDetails(data, selected)
    }
}

@Composable
private fun MonthGrid(data: AppData, month: YearMonth, selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1 // Monday first
    val cells = offset + month.lengthOfMonth()
    val rows = (cells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    if (dayNum < 1 || dayNum > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayNum)
                        DayCell(
                            date = date,
                            status = Logic.dayStatus(data, date),
                            hasCheckin = data.checkins.containsKey(date.toString()),
                            isSelected = date == selected,
                            isToday = date == today,
                            modifier = Modifier.weight(1f),
                            onClick = { onPick(date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    status: DayStatus,
    hasCheckin: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val fill = statusColor(status)
    val shape = RoundedCornerShape(10.dp)
    var m = modifier
        .aspectRatio(1f)
        .clip(shape)
        .background(fill ?: MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
    if (isSelected) m = m.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    else if (isToday) m = m.border(1.dp, MaterialTheme.colorScheme.outline, shape)
    m = m.clickable(onClick = onClick)

    Box(m, contentAlignment = Alignment.Center) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (fill != null) onFill(fill) else MaterialTheme.colorScheme.onSurface,
        )
        if (hasCheckin) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (fill != null) onFill(fill) else MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(GoodFill, "Taken")
        LegendItem(WarnFill, "Partly")
        LegendItem(BadFill, "Missed")
        LegendItem(MaterialTheme.colorScheme.primary, "Check-in", dot = true)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(if (dot) 6.dp else 10.dp)
                .clip(if (dot) CircleShape else RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayDetails(data: AppData, date: LocalDate) {
    val ctx = LocalContext.current
    val s = data.settings
    val today = LocalDate.now()
    val taken = data.doses[date.toString()].orEmpty()
    val checkin = data.checkins[date.toString()].orEmpty()
    val metrics = data.metrics.filter { dateOf(it.time) == date }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    SectionCard(longDate(date)) {
        Text("Medication", style = MaterialTheme.typography.labelLarge)
        if (date.isAfter(today)) {
            Text("Nothing logged yet", style = MaterialTheme.typography.bodyMedium, color = muted)
        } else {
            for (slot in 0 until s.dosesPerDay) {
                val t = taken.firstOrNull { it.slot == slot }
                val name = if (s.dosesPerDay == 1) "Dose" else "Dose ${slot + 1}"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val missed = t == null && date.isBefore(today) && !date.isBefore(Logic.startDate(s))
                    Text(
                        when {
                            t != null -> "$name: taken at ${timeOf(t.time)}" +
                                if (dateOf(t.time) != date) " (logged ${shortDate(dateOf(t.time))})" else ""
                            missed -> "$name: missed"
                            else -> "$name: not taken"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            t != null -> goodText()
                            missed -> badText()
                            else -> muted
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (t == null) {
                        TextButton(onClick = { Logic.markTaken(ctx, date, slot) }) { Text("Mark taken") }
                    } else {
                        TextButton(onClick = { Logic.unmarkTaken(ctx, date, slot) }) { Text("Undo") }
                    }
                }
            }
        }

        if (checkin.isNotEmpty()) {
            Text("Check-in", style = MaterialTheme.typography.labelLarge)
            s.symptoms.filter { it.id in checkin }.forEach { def ->
                val v = checkin.getValue(def.id)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(def.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    val fill = ratingColor(v, def.highIsGood)
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(fill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$v", style = MaterialTheme.typography.labelMedium, color = onFill(fill))
                    }
                }
            }
        }

        if (metrics.isNotEmpty()) {
            Text("Measurements", style = MaterialTheme.typography.labelLarge)
            metrics.forEach { m ->
                val text = when (m.type) {
                    "weight" -> "Weight ${fmt1(m.v1)} kg"
                    "bp" -> "Blood pressure ${fmt1(m.v1)}/${fmt1(m.v2)}"
                    else -> (if (m.source == "hc") "Average heart rate " else "Pulse ") + "${fmt1(m.v1)} bpm"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$text · ${timeOf(m.time)}" + if (m.source == "hc") " · watch" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (m.source != "hc") {
                        TextButton(onClick = { Logic.deleteMetric(ctx, m.id) }) { Text("Delete") }
                    }
                }
            }
        }

        if (checkin.isEmpty() && metrics.isEmpty() && !date.isAfter(today)) {
            Text("No check-in or measurements this day", style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}
