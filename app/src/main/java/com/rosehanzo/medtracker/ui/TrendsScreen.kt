package com.rosehanzo.medtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosehanzo.medtracker.data.AppData
import com.rosehanzo.medtracker.data.DayStatus
import com.rosehanzo.medtracker.data.Logic
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private val RANGES = listOf(7, 30, 90)

private val ITEM_COLORS = listOf(
    Color(0xFFE88A3C), Color(0xFF9C6ADE), Color(0xFFD4537E), Color(0xFF2EA3A3),
    Color(0xFFC9A227), Color(0xFF6A8CDE), Color(0xFFB0705A), Color(0xFF5DAA6A),
)
private val PULSE_COLOR = Color(0xFF4A90E2)
private val BP_COLOR = Color(0xFFE0564F)
private val WEIGHT_COLOR = Color(0xFF8E7CC3)

private enum class Kind { SYMPTOM, PULSE, BP, WEIGHT }

/** Something you can draw on the dose chart: a symptom or a body reading. */
private data class Item(val key: String, val name: String, val color: Color, val kind: Kind, val unit: String = "")

private fun items(data: AppData): List<Item> {
    val symptoms = data.settings.symptoms.filter { it.visible }.mapIndexed { i, d ->
        Item("s:${d.id}", d.name, ITEM_COLORS[i % ITEM_COLORS.size], Kind.SYMPTOM)
    }
    return symptoms + listOf(
        Item("pulse", "Pulse", PULSE_COLOR, Kind.PULSE, " bpm"),
        Item("bp", "Blood pressure", BP_COLOR, Kind.BP),
        Item("weight", "Weight", WEIGHT_COLOR, Kind.WEIGHT, " kg"),
    )
}

/** Points for an item between [start] and [end]. Blood pressure gives two lists (upper, lower). */
private fun points(data: AppData, item: Item, start: LocalDate, end: LocalDate): List<List<Pair<Double, Double>>> {
    val lo = start.toEpochDay().toDouble()
    val hi = end.toEpochDay() + 1.0
    return when (item.kind) {
        Kind.SYMPTOM -> {
            val id = item.key.removePrefix("s:")
            listOf(
                data.checkins.mapNotNull { (date, values) ->
                    val v = values[id] ?: return@mapNotNull null
                    val x = runCatching { LocalDate.parse(date) }.getOrNull()?.toEpochDay()?.plus(0.5) ?: return@mapNotNull null
                    if (x < lo || x > hi) null else x to v.toDouble()
                }.sortedBy { it.first },
            )
        }
        Kind.PULSE -> listOf(data.metrics.filter { it.type == "pulse" }.map { dayX(it.time) to it.v1 }.filter { it.first in lo..hi })
        Kind.WEIGHT -> listOf(data.metrics.filter { it.type == "weight" }.map { dayX(it.time) to it.v1 }.filter { it.first in lo..hi })
        Kind.BP -> {
            val bp = data.metrics.filter { it.type == "bp" }.filter { dayX(it.time) in lo..hi }
            listOf(bp.map { dayX(it.time) to it.v1 }, bp.map { dayX(it.time) to it.v2 })
        }
    }
}

private fun latestLabel(data: AppData, item: Item): String? = when (item.kind) {
    Kind.SYMPTOM -> {
        val id = item.key.removePrefix("s:")
        data.checkins.entries.filter { id in it.value }.maxByOrNull { it.key }?.value?.get(id)?.toString()
    }
    Kind.PULSE -> data.metrics.lastOrNull { it.type == "pulse" }?.let { fmt1(it.v1) + " bpm" }
    Kind.WEIGHT -> data.metrics.lastOrNull { it.type == "weight" }?.let { fmt1(it.v1) + " kg" }
    Kind.BP -> data.metrics.lastOrNull { it.type == "bp" }?.let { "${fmt1(it.v1)}/${fmt1(it.v2)}" }
}

/** Pills for a day: what you took, otherwise what the plan said. 0 if neither. */
private fun pillsOn(data: AppData, date: LocalDate): Int =
    data.doses[date.toString()]?.pills ?: Logic.planFor(data.settings, date).pills

/** Dose bands: runs of days at the same pill level. */
private fun doseBands(data: AppData, start: LocalDate, end: LocalDate): List<Band> {
    val out = mutableListOf<Band>()
    var d = start
    while (!d.isAfter(end)) {
        val p = pillsOn(data, d)
        val x0 = d.toEpochDay().toDouble()
        val last = out.lastOrNull()
        if (p > 0) {
            if (last != null && last.pills == p && last.x1 == x0) out[out.size - 1] = last.copy(x1 = x0 + 1)
            else out += Band(x0, x0 + 1, p)
        }
        d = d.plusDays(1)
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(data: AppData) {
    var days by rememberSaveable { mutableIntStateOf(30) }
    var combined by rememberSaveable { mutableStateOf(true) }
    val all = items(data)
    var selected by remember {
        val withData = all.filter { it.kind == Kind.SYMPTOM && points(data, it, LocalDate.now().minusDays(89), LocalDate.now()).first().isNotEmpty() }
        mutableStateOf((withData.take(2) + all.filter { it.kind == Kind.PULSE }).map { it.key }.toSet())
    }
    val today = LocalDate.now()
    val start = today.minusDays(days - 1L)
    val xMin = start.toEpochDay().toDouble()
    val xMax = today.toEpochDay() + 1.0

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

        GlanceTiles(data, start, today)

        DoseFeelCard(
            data = data,
            all = all,
            selected = selected,
            onToggle = { key -> selected = if (key in selected) selected - key else selected + key },
            combined = combined,
            onCombined = { combined = it },
            start = start,
            today = today,
            xMin = xMin,
            xMax = xMax,
        )

        ByDoseCard(data)
        Spacer(Modifier.size(8.dp))
    }
}

// ---------- At a glance ----------

@Composable
private fun GlanceTiles(data: AppData, start: LocalDate, today: LocalDate) {
    val s = data.settings
    val zone = ZoneId.systemDefault()

    // Doses taken: planned days you've marked, plus today once marked.
    var taken = 0
    var missed = 0
    var toMark = 0
    var d = start
    while (!d.isAfter(today)) {
        when (Logic.dayStatus(data, d)) {
            DayStatus.TAKEN -> taken++
            DayStatus.MISSED -> missed++
            DayStatus.PENDING -> if (d != today) toMark++
            else -> {}
        }
        d = d.plusDays(1)
    }
    val counted = taken + missed + toMark
    val pct = if (counted > 0) taken * 100 / counted else null
    val takenSub = buildList {
        add("$taken of $counted")
        if (missed > 0) add("$missed missed")
        if (toMark > 0) add("$toMark to mark")
    }.joinToString(" · ")

    // Usual time and how far from plan.
    val times = mutableListOf<Int>()
    val offsets = mutableListOf<Int>()
    d = start
    while (!d.isAfter(today)) {
        data.doses[d.toString()]?.let { dose ->
            val t = Instant.ofEpochMilli(dose.time).atZone(zone).toLocalTime()
            val m = t.hour * 60 + t.minute
            times += m
            offsets += m - Logic.doseMinutes(s, d)
        }
        d = d.plusDays(1)
    }
    val usual = times.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
    val avgOffset = if (offsets.isEmpty()) 0 else offsets.average().toInt()
    val offsetText = when {
        offsets.isEmpty() -> "No doses logged"
        abs(avgOffset) < 10 -> "On time on average"
        avgOffset > 0 -> "${fmtDuration(avgOffset)} late on average"
        else -> "${fmtDuration(-avgOffset)} early on average"
    }

    // Current dose.
    val plan = Logic.planFor(s, today)
    val phaseStart = plan.phase?.let { LocalDate.parse(it.start) }
    val dayOfPhase = phaseStart?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) + 1 }

    // Biggest change in a symptom between your lowest and highest dose.
    val change = biggestChange(data)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Doses taken", pct?.let { "$it%" } ?: "–", takenSub, null, Modifier.weight(1f))
            Tile(
                "Usual time",
                usual?.let { Logic.fmtTime(it) } ?: "–",
                offsetText,
                if (offsets.isNotEmpty() && avgOffset >= 30) TodayFill else null,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(
                "Current dose",
                if (plan.pills > 0) Logic.pillWord(plan.pills) else "None",
                if (phaseStart != null) "since ${shortDate(phaseStart)} · day $dayOfPhase" else "No plan set",
                null,
                Modifier.weight(1f),
            )
            if (change != null) {
                Tile(
                    "Biggest change",
                    change.first,
                    "${fmt1(change.second)} → ${fmt1(change.third)} since ${Logic.pillWord(change.fourth)}",
                    if (change.third > change.second) BadFill else GoodFill,
                    Modifier.weight(1f),
                )
            } else {
                Tile("Biggest change", "–", "Needs check-ins on 2 dose levels", null, Modifier.weight(1f))
            }
        }
    }
}

private fun fmtDuration(min: Int): String = if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"

private class EndLabel(var y: Float, val text: String, val color: Color)

private data class Change(val first: String, val second: Double, val third: Double, val fourth: Int)

/** The symptom whose average moved most between your lowest and highest dose (2+ check-ins each). */
private fun biggestChange(data: AppData): Change? {
    val levels = levelDates(data)
    if (levels.size < 2) return null
    val low = levels.keys.min()
    val high = levels.keys.max()
    var best: Change? = null
    data.settings.symptoms.filter { it.visible }.forEach { def ->
        val a = levels[low].orEmpty().mapNotNull { data.checkins[it]?.get(def.id) }
        val b = levels[high].orEmpty().mapNotNull { data.checkins[it]?.get(def.id) }
        if (a.size < 2 || b.size < 2) return@forEach
        val c = Change(def.name, a.average(), b.average(), low)
        if (best == null || abs(c.third - c.second) > abs(best!!.third - best!!.second)) best = c
    }
    return best?.takeIf { abs(it.third - it.second) >= 0.5 }
}

@Composable
private fun Tile(label: String, value: String, sub: String, subColor: Color?, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = mutedText(), maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = subColor ?: mutedText(), maxLines = 2)
    }
}

// ---------- Dose and how you feel ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoseFeelCard(
    data: AppData,
    all: List<Item>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    combined: Boolean,
    onCombined: (Boolean) -> Unit,
    start: LocalDate,
    today: LocalDate,
    xMin: Double,
    xMax: Double,
) {
    val bands = doseBands(data, start, today)
    val chosen = all.filter { it.key in selected }

    SectionCard("Dose and how you feel", Icons.Filled.Insights) {
        Text(
            "Shaded bands are your dose. Pick what to show on top.",
            style = MaterialTheme.typography.bodySmall,
            color = mutedText(),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            all.forEach { item ->
                val on = item.key in selected
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .border(if (on) 1.5.dp else 1.dp, if (on) item.color else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                        .clickable { onToggle(item.key) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (on) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(item.color))
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(
                        item.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onSurface else mutedText(),
                    )
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = combined,
                onClick = { onCombined(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Combined") }
            SegmentedButton(
                selected = !combined,
                onClick = { onCombined(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Separate") }
        }

        if (chosen.isEmpty()) {
            Text("Pick something above to show it here.", style = MaterialTheme.typography.bodySmall, color = mutedText())
            return@SectionCard
        }

        if (combined) {
            CombinedChart(data, chosen, bands, start, today, xMin, xMax)
            Box(Modifier.padding(start = 8.dp)) { DateEnds(start) }
            Text(
                "Each line uses its own scale, so compare the shape. Latest values are at the right. Red dots are missed days.",
                style = MaterialTheme.typography.labelSmall,
                color = mutedText(),
            )
        } else {
            chosen.forEach { item ->
                val lists = points(data, item, start, today)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(item.color))
                        Spacer(Modifier.size(8.dp))
                        Text(item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        latestLabel(data, item)?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = item.color) }
                    }
                    if (lists.all { it.isEmpty() }) {
                        Text("Nothing logged in this period", style = MaterialTheme.typography.bodySmall, color = mutedText())
                    } else {
                        val series = lists.mapIndexed { i, pts ->
                            Series(item.name, if (i == 0) item.color else item.color.copy(alpha = 0.55f), pts, fill = lists.size == 1)
                        }
                        val sym = item.kind == Kind.SYMPTOM
                        LineChart(series, xMin, xMax, if (sym) 1.0 else null, if (sym) 10.0 else null, 120.dp, bands)
                        Box(Modifier.padding(start = 34.dp)) { DateEnds(start) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CombinedChart(
    data: AppData,
    chosen: List<Item>,
    bands: List<Band>,
    start: LocalDate,
    today: LocalDate,
    xMin: Double,
    xMax: Double,
) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val bandLabel = TextStyle(fontSize = 11.sp, color = muted)
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val missedX = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }
        .filter { Logic.missed(data, it) }.map { it.toEpochDay() + 0.5 }.toList()
    val lines = chosen.map { it to points(data, it, start, today) }

    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val left = 8.dp.toPx()
        val right = size.width - 70.dp.toPx()
        val top = 24.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val span = (xMax - xMin).coerceAtLeast(1e-9)
        fun x(v: Double) = left + ((v - xMin) / span).toFloat() * (right - left)

        bands.forEach { b ->
            val x0 = x(b.x0)
            val x1 = x(b.x1)
            drawRect(bandColor(b.pills), Offset(x0, 4.dp.toPx()), Size(x1 - x0, bottom - 4.dp.toPx()))
            if (x1 - x0 > 44.dp.toPx()) {
                val t = measurer.measure(Logic.pillWord(b.pills), bandLabel)
                drawText(t, topLeft = Offset((x0 + x1) / 2 - t.size.width / 2f, 6.dp.toPx()))
            }
        }

        missedX.forEach { mx -> drawCircle(BadFill, 3.dp.toPx(), Offset(x(mx), size.height - 8.dp.toPx())) }

        // End labels, nudged apart so they don't overlap.
        val labels = mutableListOf<EndLabel>()

        lines.forEach { (item, lists) ->
            val allPts = lists.flatten()
            if (allPts.isEmpty()) return@forEach
            val (lo, hi) = if (item.kind == Kind.SYMPTOM) {
                1.0 to 10.0
            } else {
                val mn = allPts.minOf { it.second }
                val mx = allPts.maxOf { it.second }
                val pad = ((mx - mn) * 0.25).coerceAtLeast(1.0)
                (mn - pad) to (mx + pad)
            }
            fun y(v: Double) = bottom - ((v - lo) / (hi - lo)).toFloat() * (bottom - top)
            lists.forEachIndexed { i, pts ->
                if (pts.isEmpty()) return@forEachIndexed
                val color = if (i == 0) item.color else item.color.copy(alpha = 0.55f)
                val offs = pts.map { Offset(x(it.first), y(it.second)) }
                if (offs.size > 1) {
                    val path = Path()
                    path.moveTo(offs[0].x, offs[0].y)
                    for (k in 1 until offs.size) path.lineTo(offs[k].x, offs[k].y)
                    drawPath(path, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (offs.size <= 45) offs.forEach {
                    drawCircle(cardColor, 3.5.dp.toPx(), it)
                    drawCircle(color, 2.5.dp.toPx(), it)
                }
            }
            val lastY = y(lists.first().lastOrNull()?.second ?: allPts.last().second)
            val value = latestLabel(data, item) ?: ""
            labels += EndLabel(lastY, value, item.color)
        }

        val gap = 14.dp.toPx()
        labels.sortBy { it.y }
        for (i in 1 until labels.size) {
            if (labels[i].y - labels[i - 1].y < gap) labels[i].y = labels[i - 1].y + gap
        }
        labels.forEach { l ->
            val t = measurer.measure(l.text, TextStyle(fontSize = 11.sp, color = l.color, fontWeight = FontWeight.Medium))
            drawText(t, topLeft = Offset(right + 6.dp.toPx(), l.y - t.size.height / 2f))
        }
    }
}

// ---------- By dose level ----------

/** Dates (yyyy-MM-dd) at each pill level, from the start of your plan to today. */
private fun levelDates(data: AppData): Map<Int, List<String>> {
    val first = Logic.sortedPhases(data.settings).firstOrNull()?.start?.let { LocalDate.parse(it) } ?: return emptyMap()
    val today = LocalDate.now()
    val out = mutableMapOf<Int, MutableList<String>>()
    var d = first
    while (!d.isAfter(today)) {
        val p = pillsOn(data, d)
        if (p > 0 && !Logic.missed(data, d)) out.getOrPut(p) { mutableListOf() } += d.toString()
        d = d.plusDays(1)
    }
    return out
}

@Composable
private fun ByDoseCard(data: AppData) {
    val levels = levelDates(data)
    SectionCard("By dose level", Icons.Filled.TableChart) {
        Text(
            "Your averages on each dose, across all your data. Red means clearly higher than on your lowest dose.",
            style = MaterialTheme.typography.bodySmall,
            color = mutedText(),
        )
        if (levels.isEmpty()) {
            Text("Add a dose plan to see this.", style = MaterialTheme.typography.bodySmall, color = mutedText())
            return@SectionCard
        }
        val cols = levels.keys.sorted()

        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.6f))
            cols.forEach {
                Text(
                    Logic.pillWord(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedText(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        fun dayOf(m: Long) = dateOf(m).toString()
        val rows = mutableListOf<Triple<String, List<Double?>, (Double, Double) -> Boolean>>()
        data.settings.symptoms.filter { it.visible }.forEach { def ->
            val avgs = cols.map { lv -> levels[lv].orEmpty().mapNotNull { data.checkins[it]?.get(def.id) }.takeIf { it.isNotEmpty() }?.average() }
            if (avgs.any { it != null }) rows += Triple(def.name, avgs, { base: Double, v: Double -> v - base >= 1.5 })
        }
        fun metricAvg(type: String, pick: (com.rosehanzo.medtracker.data.Metric) -> Double) = cols.map { lv ->
            val set = levels[lv].orEmpty().toSet()
            data.metrics.filter { it.type == type && dayOf(it.time) in set }.map(pick).takeIf { it.isNotEmpty() }?.average()
        }
        val pulse = metricAvg("pulse") { it.v1 }
        if (pulse.any { it != null }) rows += Triple("Pulse", pulse, { base: Double, v: Double -> v - base >= 5 })
        val bpHigh = metricAvg("bp") { it.v1 }
        if (bpHigh.any { it != null }) rows += Triple("BP upper", bpHigh, { base: Double, v: Double -> v - base >= 5 })
        val bpLow = metricAvg("bp") { it.v2 }
        if (bpLow.any { it != null }) rows += Triple("BP lower", bpLow, { base: Double, v: Double -> v - base >= 5 })
        val weight = metricAvg("weight") { it.v1 }
        if (weight.any { it != null }) rows += Triple("Weight", weight, { base: Double, v: Double -> base - v >= 1.0 })

        if (rows.isEmpty()) {
            Text("No check-ins or readings yet.", style = MaterialTheme.typography.bodySmall, color = mutedText())
            return@SectionCard
        }
        rows.forEach { (name, avgs, flag) ->
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.6f), maxLines = 1)
                val base = avgs.firstOrNull { it != null }
                avgs.forEachIndexed { i, v ->
                    val flagged = v != null && base != null && i > 0 && flag(base, v)
                    Text(
                        v?.let { if (name == "Weight") fmt1(it) else if (name.startsWith("BP") || name == "Pulse") "${it.toInt()}" else String.format(java.util.Locale.UK, "%.1f", it) } ?: "–",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (flagged) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (flagged) badText() else MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DateEnds(start: LocalDate) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(shortDate(start), style = MaterialTheme.typography.labelSmall, color = mutedText())
        Text("Today", style = MaterialTheme.typography.labelSmall, color = mutedText())
    }
}
