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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
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

/** Ring colour for a day. No ring means no dose was planned. */
private enum class Mark { TAKEN, MISSED, PENDING, PLANNED }

/** The small mark under the ring for the day's check-in. */
private enum class CheckMark { TICK, CROSS, DASH }

/** Form icon colour: done, still to do, or missed. */
private enum class FormMark { DONE, PENDING, MISSED }

val TodayFill = Color(0xFFF08A24)
private val FormBlue = Color(0xFF4A90E2)

private fun markFor(data: AppData, date: LocalDate, today: LocalDate): Mark? {
    // Future days only show what a phase actually covers; carry-on applies once the day arrives.
    if (date.isAfter(today)) return if (Logic.explicitPlanFor(data.settings, date).pills > 0) Mark.PLANNED else null
    return when (Logic.dayStatus(data, date)) {
        DayStatus.TAKEN -> Mark.TAKEN
        DayStatus.MISSED -> Mark.MISSED
        DayStatus.PENDING -> Mark.PENDING
        else -> null
    }
}

private fun checkFor(data: AppData, date: LocalDate, today: LocalDate, mark: Mark?): CheckMark? = when {
    mark == null || mark == Mark.PLANNED -> null
    data.checkins.containsKey(date.toString()) -> CheckMark.TICK
    date == today -> CheckMark.DASH
    else -> CheckMark.CROSS
}

private fun formMarkFor(data: AppData, date: LocalDate, today: LocalDate): FormMark? {
    val forms = Logic.formsOn(data, date)
    if (forms.isEmpty()) return null
    return when {
        forms.all { Logic.formDone(it, date) } -> FormMark.DONE
        date.isBefore(today) -> FormMark.MISSED
        else -> FormMark.PENDING
    }
}

@Composable
private fun ringColor(mark: Mark): Color = when (mark) {
    Mark.TAKEN -> GoodFill
    Mark.MISSED -> BadFill
    Mark.PENDING -> TodayFill
    Mark.PLANNED -> MaterialTheme.colorScheme.outline
}

private fun checkColor(c: CheckMark): Color = when (c) {
    CheckMark.TICK -> GoodFill
    CheckMark.CROSS -> BadFill
    CheckMark.DASH -> TodayFill
}

private fun formColor(f: FormMark): Color = when (f) {
    FormMark.DONE -> GoodFill
    FormMark.MISSED -> BadFill
    FormMark.PENDING -> FormBlue
}

/** Tick, cross or dash, drawn to match the ring. */
@Composable
private fun CheckGlyph(mark: CheckMark, size: Dp = 10.dp) {
    val color = checkColor(mark)
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.6.dp.toPx()
        when (mark) {
            CheckMark.TICK -> {
                val path = Path()
                path.moveTo(w * 0.12f, h * 0.55f)
                path.lineTo(w * 0.40f, h * 0.80f)
                path.lineTo(w * 0.88f, h * 0.25f)
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            CheckMark.CROSS -> {
                drawLine(color, Offset(w * 0.2f, h * 0.2f), Offset(w * 0.8f, h * 0.8f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.8f, h * 0.2f), Offset(w * 0.2f, h * 0.8f), stroke, StrokeCap.Round)
            }
            CheckMark.DASH -> drawLine(color, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.85f, h * 0.5f), stroke * 1.15f, StrokeCap.Round)
        }
    }
}

/** Outline page with a cut corner and two lines, all in one colour. */
@Composable
private fun FormGlyph(color: Color, height: Dp = 11.dp) {
    Canvas(Modifier.size(width = height * 0.8f, height = height)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.dp.toPx()
        val inset = stroke / 2
        val page = Path()
        page.moveTo(inset, inset)
        page.lineTo(w * 0.62f, inset)
        page.lineTo(w - inset, h * 0.3f)
        page.lineTo(w - inset, h - inset)
        page.lineTo(inset, h - inset)
        page.close()
        drawPath(page, color, style = Stroke(stroke, join = StrokeJoin.Round))
        drawLine(color, Offset(w * 0.26f, h * 0.52f), Offset(w * 0.74f, h * 0.52f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.26f, h * 0.72f), Offset(w * 0.74f, h * 0.72f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun MonthGrid(data: AppData, month: YearMonth, selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val offset = month.atDay(1).dayOfWeek.value - 1 // Monday first
    val rows = (offset + month.lengthOfMonth() + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    if (dayNum < 1 || dayNum > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val date = month.atDay(dayNum)
                        val dose = data.doses[date.toString()]
                        val mark = markFor(data, date, today)
                        DayCell(
                            date = date,
                            mark = mark,
                            pills = dose?.pills ?: (if (date.isAfter(today)) Logic.explicitPlanFor(data.settings, date) else Logic.planFor(data.settings, date)).pills,
                            check = checkFor(data, date, today, mark),
                            form = formMarkFor(data, date, today),
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
    mark: Mark?,
    pills: Int,
    check: CheckMark?,
    form: FormMark?,
    isSelected: Boolean,
    isToday: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    var m = modifier.clip(shape)
    if (isSelected) {
        m = m.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
    }
    m = m.clickable(onClick = onClick).padding(top = 3.dp, bottom = 4.dp)

    Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            val ring = mark?.let { ringColor(it) }
            if (ring != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val radius = this.size.minDimension * 0.42f
                    val c = Offset(this.size.width / 2, this.size.height / 2)
                    drawCircle(ring, radius, c, style = Stroke(2.dp.toPx()))
                    // Pill dots: small, evenly spaced, below the number.
                    val n = pills.coerceIn(1, 4)
                    val dot = radius * 0.075f
                    val gap = radius * 0.27f
                    val y = c.y + radius * 0.5f
                    val startX = c.x - gap * (n - 1) / 2f
                    for (i in 0 until n) drawCircle(ring, dot, Offset(startX + gap * i, y))
                }
            }
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                color = if (mark == null) mutedText() else MaterialTheme.colorScheme.onSurface,
                modifier = if (ring != null) Modifier.offset(y = (-3).dp) else Modifier,
            )
        }
        // Check-in mark and form icon, side by side and centred together.
        Row(
            Modifier.height(12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (check != null) CheckGlyph(check)
            if (form != null) FormGlyph(formColor(form))
        }
    }
}

@Composable
private fun Legend() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendRing(GoodFill, "Taken", Modifier.weight(1f))
            LegendRing(BadFill, "Missed", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendRing(TodayFill, "To mark", Modifier.weight(1f))
            LegendRing(MaterialTheme.colorScheme.outline, "Planned", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendGlyph("Check-in done", Modifier.weight(1f)) { CheckGlyph(CheckMark.TICK, 12.dp) }
            LegendGlyph("No check-in", Modifier.weight(1f)) { CheckGlyph(CheckMark.CROSS, 12.dp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendGlyph("Check-in to do", Modifier.weight(1f)) { CheckGlyph(CheckMark.DASH, 12.dp) }
            LegendGlyph("Form", Modifier.weight(1f)) { FormGlyph(FormBlue, 13.dp) }
        }
        Text(
            "Dots in the ring: pills that day. Forms turn green when done.",
            style = MaterialTheme.typography.labelSmall,
            color = mutedText(),
        )
    }
}

@Composable
private fun LegendRing(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).border(2.dp, color, CircleShape))
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = mutedText())
    }
}

@Composable
private fun LegendGlyph(label: String, modifier: Modifier = Modifier, glyph: @Composable () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) { glyph() }
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = mutedText())
    }
}

@Composable
private fun DayDetails(data: AppData, date: LocalDate) {
    val ctx = LocalContext.current
    val s = data.settings
    val today = LocalDate.now()
    val plan = if (date.isAfter(today)) Logic.explicitPlanFor(s, date) else Logic.planFor(s, date)
    val dose = data.doses[date.toString()]
    val checkin = data.checkins[date.toString()].orEmpty()
    val metrics = data.metrics.filter { dateOf(it.time) == date }

    SectionCard(longDate(date), Icons.Filled.Event) {
        // Medication
        val status = Logic.dayStatus(data, date)
        Text("Medication", style = MaterialTheme.typography.labelLarge, color = mutedText())
        Text(
            when {
                dose != null -> "${Logic.pillWord(dose.pills)} taken at ${timeOf(dose.time)}"
                status == DayStatus.MISSED -> "${Logic.pillWord(plan.pills)} marked as missed"
                plan.pills == 0 && plan.phase != null -> "Day off"
                plan.pills == 0 && date.isAfter(today) && Logic.planFor(s, date).pills > 0 ->
                    "No phase covers this day yet. If it's still unplanned when it comes, your latest amount carries on."
                plan.pills == 0 -> "Nothing planned"
                date.isAfter(today) -> "${Logic.pillWord(plan.pills)} planned at ${Logic.fmtTime(Logic.doseMinutes(s, date))}"
                date == today -> "${Logic.pillWord(plan.pills)} due at ${Logic.fmtTime(Logic.doseMinutes(s, date))}"
                else -> "${Logic.pillWord(plan.pills)} not marked yet (was due at ${Logic.fmtTime(Logic.doseMinutes(s, date))})"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = when (status) {
                DayStatus.TAKEN -> goodText()
                DayStatus.MISSED -> badText()
                DayStatus.PENDING -> TodayFill
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        if (!date.isAfter(today)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    dose != null -> {
                        OutlinedButton(onClick = { pickDoseTime(ctx, date, dose.time) }) { Text("Change time") }
                        TextButton(onClick = { Logic.unmarkTaken(ctx, date) }) { Text("Undo") }
                    }
                    status == DayStatus.MISSED -> {
                        TextButton(onClick = { Logic.unmarkMissed(ctx, date) }) { Text("Undo") }
                    }
                    plan.pills > 0 -> {
                        Button(onClick = { Logic.markTaken(ctx, date) }) { Text("Mark taken") }
                        OutlinedButton(onClick = { Logic.markMissed(ctx, date) }) { Text("Mark missed") }
                    }
                }
            }
        }

        val forms = Logic.formsOn(data, date)
        if (forms.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text("Forms", style = MaterialTheme.typography.labelLarge, color = mutedText())
            forms.forEach { f ->
                val done = Logic.formDone(f, date)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when {
                                done -> "Done"
                                date.isBefore(today) -> "Not done (was due at ${Logic.fmtTime(f.time)})"
                                else -> "Due at ${Logic.fmtTime(f.time)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                done -> goodText()
                                date.isBefore(today) -> badText()
                                else -> mutedText()
                            },
                        )
                    }
                    if (!date.isAfter(today)) {
                        TextButton(onClick = { Logic.setFormDone(ctx, f.id, date, !done) }) {
                            Text(if (done) "Undo" else "Mark done")
                        }
                    }
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
