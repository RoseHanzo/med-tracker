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
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.sp
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
        ScreenTitle("Calendar", "Your doses, check-ins and readings by day")

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            MonthGrid(data, month, selected) { selected = it }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Legend()
        }

        DayDetails(data, selected)
    }
}

@Composable
private fun MonthGrid(data: AppData, month: YearMonth, selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val offset = month.atDay(1).dayOfWeek.value - 1 // Monday first
    val rows = (offset + month.lengthOfMonth() + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            WEEKDAY_LETTERS.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedText(),
                )
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    if (dayNum < 1 || dayNum > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayNum)
                        DayCell(
                            date = date,
                            status = Logic.dayStatus(data, date),
                            plannedPills = Logic.planFor(data.settings, date).pills,
                            takenPills = data.doses[date.toString()]?.pills,
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
    plannedPills: Int,
    takenPills: Int?,
    hasCheckin: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val fill = statusColor(status)
    val shape = RoundedCornerShape(12.dp)
    val base = fill ?: MaterialTheme.colorScheme.surfaceContainer
    var m = modifier.aspectRatio(1f).clip(shape).background(base)
    if (isSelected) m = m.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    else if (isToday) m = m.border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
    m = m.clickable(onClick = onClick)

    val textColor = if (fill != null) onFill(fill) else MaterialTheme.colorScheme.onSurface
    val pills = takenPills ?: plannedPills
    val showPills = status != DayStatus.NONE || date.isAfter(LocalDate.now())

    Box(m, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
            )
            if (showPills) {
                Text(
                    if (pills > 0) "●".repeat(pills.coerceAtMost(4)) else "–",
                    fontSize = 6.sp,
                    lineHeight = 7.sp,
                    color = if (fill != null) textColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                )
            }
        }
        if (hasCheckin) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (fill != null) textColor else MaterialTheme.colorScheme.tertiary),
            )
        }
    }
}

@Composable
private fun Legend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(GoodFill, "Taken")
            LegendItem(BadFill, "Missed")
            LegendItem(MaterialTheme.colorScheme.tertiary, "Check-in", dot = true)
        }
        Text(
            "Dots under each date show how many pills are planned. – means a day off.",
            style = MaterialTheme.typography.labelSmall,
            color = mutedText(),
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dot: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(if (dot) 7.dp else 12.dp)
                .clip(if (dot) CircleShape else RoundedCornerShape(4.dp))
                .background(color),
        )
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = mutedText())
    }
}

@Composable
private fun DayDetails(data: AppData, date: LocalDate) {
    val ctx = LocalContext.current
    val s = data.settings
    val today = LocalDate.now()
    val plan = Logic.planFor(s, date)
    val dose = data.doses[date.toString()]
    val checkin = data.checkins[date.toString()].orEmpty()
    val metrics = data.metrics.filter { dateOf(it.time) == date }

    SectionCard(longDate(date), Icons.Filled.Event) {
        // Medication
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Medication", style = MaterialTheme.typography.labelLarge, color = mutedText())
                val status = Logic.dayStatus(data, date)
                Text(
                    when {
                        dose != null -> "${Logic.pillWord(dose.pills)} taken at ${timeOf(dose.time)}" +
                            if (dateOf(dose.time) != date) " (logged ${shortDate(dateOf(dose.time))})" else ""
                        plan.pills == 0 && plan.phase != null -> "Day off"
                        plan.pills == 0 -> "Nothing planned"
                        date.isAfter(today) -> "${Logic.pillWord(plan.pills)} planned at ${Logic.fmtTime(Logic.doseMinutes(s, date))}"
                        status == DayStatus.MISSED -> "${Logic.pillWord(plan.pills)} missed"
                        else -> "${Logic.pillWord(plan.pills)} due at ${Logic.fmtTime(Logic.doseMinutes(s, date))}"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        dose != null -> goodText()
                        status == DayStatus.MISSED -> badText()
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (!date.isAfter(today)) {
                if (dose != null) {
                    TextButton(onClick = { Logic.unmarkTaken(ctx, date) }) { Text("Undo") }
                } else if (plan.pills > 0) {
                    TextButton(onClick = { Logic.markTaken(ctx, date) }) { Text("Mark taken") }
                }
            }
        }

        if (checkin.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text("Check-in", style = MaterialTheme.typography.labelLarge, color = mutedText())
            s.symptoms.filter { it.id in checkin }.forEach { def ->
                val v = checkin.getValue(def.id)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(def.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    val c = ratingColor(v, def.highIsGood)
                    Pill("$v/10", c.copy(alpha = 0.16f), c)
                }
            }
        }

        if (metrics.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text("Measurements", style = MaterialTheme.typography.labelLarge, color = mutedText())
            metrics.forEach { m ->
                val text = when (m.type) {
                    "weight" -> "Weight ${fmt1(m.v1)} kg"
                    "bp" -> "Blood pressure ${fmt1(m.v1)}/${fmt1(m.v2)}"
                    else -> (if (m.source == "hc") "Average heart rate " else "Pulse ") + "${fmt1(m.v1)} bpm"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            timeOf(m.time) + if (m.source == "hc") " · from Samsung Health" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedText(),
                        )
                    }
                    if (m.source != "hc") {
                        TextButton(onClick = { Logic.deleteMetric(ctx, m.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
