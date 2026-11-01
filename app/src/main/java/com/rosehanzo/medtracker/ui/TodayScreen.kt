package com.rosehanzo.medtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rosehanzo.medtracker.data.AppData
import com.rosehanzo.medtracker.data.Logic
import com.rosehanzo.medtracker.data.Metric
import com.rosehanzo.medtracker.health.HealthSync
import com.rosehanzo.medtracker.update.Updater
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TodayScreen(data: AppData) {
    val today = LocalDate.now()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Cadence", longDate(today))
        UpdateBanner()
        DoseHero(data, today)
        CheckInCard(data, today)
        MeasureCard(data)
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun UpdateBanner() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    val ready = state as? Updater.State.Ready ?: return

    SectionCard("A new version is ready", Icons.Filled.SystemUpdate) {
        Text(
            "Build ${ready.update.build} is available. Your data stays as it is.",
            style = MaterialTheme.typography.bodyMedium,
            color = mutedText(),
        )
        Button(
            onClick = {
                if (!Updater.canInstall(ctx)) {
                    toast(ctx, "Allow Cadence to install updates, then tap Update again")
                    Updater.openInstallPermission(ctx)
                } else {
                    scope.launch { Updater.downloadAndInstall(ctx, ready.update) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Update now") }
    }
}

@Composable
private fun heroBrush(): Brush {
    val colors = if (isSystemInDarkTheme()) {
        listOf(Color(0xFF0B4F52), Color(0xFF136B5F))
    } else {
        listOf(Color(0xFF00696E), Color(0xFF16927F))
    }
    return Brush.linearGradient(colors)
}

@Composable
private fun DoseHero(data: AppData, today: LocalDate) {
    val ctx = LocalContext.current
    val s = data.settings
    val plan = Logic.planFor(s, today)
    val dose = data.doses[today.toString()]
    val due = Logic.fmtTime(Logic.doseMinutes(s, today))
    val white = Color.White
    val soft = Color.White.copy(alpha = 0.78f)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(heroBrush())
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's dose", style = MaterialTheme.typography.labelLarge, color = soft, modifier = Modifier.weight(1f))
                if (plan.pills > 0 && dose == null) {
                    Pill("Due $due", white.copy(alpha = 0.18f), white)
                }
            }

            when {
                dose != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Logic.pillWord(dose.pills), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = white)
                        Text(s.medName, style = MaterialTheme.typography.bodyLarge, color = soft)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = white, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Taken at ${timeOf(dose.time)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = white,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { Logic.unmarkTaken(ctx, today) }) { Text("Undo", color = white) }
                    }
                }
                plan.pills > 0 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Logic.pillWord(plan.pills), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = white)
                        Text(s.medName, style = MaterialTheme.typography.bodyLarge, color = soft)
                    }
                    Button(
                        onClick = { Logic.markTaken(ctx, today) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = white,
                            contentColor = Color(0xFF00585C),
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mark as taken", style = MaterialTheme.typography.titleSmall)
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (plan.phase == null) Icons.Filled.EventAvailable else Icons.Filled.Weekend,
                            contentDescription = null, tint = white, modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (plan.phase == null) "No dose today" else "Rest day",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = white,
                            )
                            Text(nextDoseText(data, today), style = MaterialTheme.typography.bodyMedium, color = soft)
                        }
                    }
                }
            }

            HorizontalDivider(color = white.copy(alpha = 0.2f))
            val days = Logic.daysOfSupply(data)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Inventory2, contentDescription = null, tint = soft, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        days < 0 -> "Add your pill count in Settings to track supply"
                        days >= 730 -> "${Logic.pillWord(s.pillsLeft)} left"
                        else -> "${Logic.pillWord(s.pillsLeft)} left · enough for $days days"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (days in 0..s.lowStockDays) Color(0xFFFFD27A) else soft,
                )
            }
        }
    }
}

private fun nextDoseText(data: AppData, today: LocalDate): String {
    val s = data.settings
    var d = today.plusDays(1)
    repeat(120) {
        val p = Logic.planFor(s, d)
        if (p.pills > 0) {
            val whenText = if (d == today.plusDays(1)) "tomorrow" else mediumDate(d)
            return "Next: ${Logic.pillWord(p.pills)} $whenText at ${Logic.fmtTime(Logic.doseMinutes(s, d))}"
        }
        d = d.plusDays(1)
    }
    return "Nothing planned. Add a phase in Settings."
}

@Composable
private fun CheckInCard(data: AppData, today: LocalDate) {
    val ctx = LocalContext.current
    val defs = data.settings.symptoms.filter { it.visible }
    val saved = data.checkins[today.toString()].orEmpty()
    val values = remember(today, saved) { mutableStateMapOf<String, Int>().apply { putAll(saved) } }

    SectionCard(
        "How are you today?",
        Icons.Filled.Mood,
        trailing = { if (saved.isNotEmpty()) Pill("Saved", GoodFill.copy(alpha = 0.15f), goodText()) },
    ) {
        if (defs.isEmpty()) {
            Text(
                "Everything is hidden. Turn on what you want to track in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = mutedText(),
            )
            return@SectionCard
        }
        Text(
            "Tap a number for anything you want to log, then save once.",
            style = MaterialTheme.typography.bodySmall,
            color = mutedText(),
        )
        defs.forEachIndexed { index, def ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(def.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    val v = values[def.id]
                    if (v != null) {
                        val c = ratingColor(v, def.highIsGood)
                        Pill("$v/10", c.copy(alpha = 0.16f), c)
                    }
                }
                ScaleRow(values[def.id], def.highIsGood) { v ->
                    if (v == null) values.remove(def.id) else values[def.id] = v
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(def.low, style = MaterialTheme.typography.labelSmall, color = mutedText())
                    Text(def.high, style = MaterialTheme.typography.labelSmall, color = mutedText())
                }
            }
        }
        val current = values.toMap()
        val dirty = current != saved
        Button(
            onClick = {
                Logic.saveCheckin(ctx, today, current)
                toast(ctx, "Check-in saved")
            },
            enabled = dirty,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text(
                when {
                    !dirty && saved.isNotEmpty() -> "Saved"
                    saved.isEmpty() -> "Save check-in"
                    else -> "Update check-in"
                },
            )
        }
    }
}

@Composable
private fun MeasureCard(data: AppData) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var weight by remember { mutableStateOf("") }
    var sys by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    val hcAvailable = remember { HealthSync.available(ctx) }

    SectionCard("Measurements", Icons.Filled.MonitorHeart) {
        LatestReadings(data)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Weight (kg)", weight, Modifier.weight(1f)) { weight = it }
            NumberField("Pulse (bpm)", pulse, Modifier.weight(1f)) { pulse = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("BP upper", sys, Modifier.weight(1f)) { sys = it }
            NumberField("BP lower", dia, Modifier.weight(1f)) { dia = it }
        }
        Button(
            onClick = {
                val now = System.currentTimeMillis()
                val list = mutableListOf<Metric>()
                parseNumber(weight)?.takeIf { it in 20.0..400.0 }?.let {
                    list += Metric("m-w-$now", "weight", now, it)
                }
                val sy = parseNumber(sys)
                val di = parseNumber(dia)
                if (sy != null && di != null && sy in 50.0..260.0 && di in 30.0..180.0) {
                    list += Metric("m-bp-$now", "bp", now, sy, di)
                }
                parseNumber(pulse)?.takeIf { it in 25.0..250.0 }?.let {
                    list += Metric("m-p-$now", "pulse", now, it)
                }
                if (list.isEmpty()) {
                    toast(ctx, "Enter a reading first (blood pressure needs both numbers)")
                } else {
                    Logic.addMetrics(ctx, list)
                    weight = ""; sys = ""; dia = ""; pulse = ""
                    toast(ctx, if (list.size == 1) "Reading saved" else "${list.size} readings saved")
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Save readings") }

        if (hcAvailable) {
            OutlinedButton(
                onClick = {
                    syncing = true
                    scope.launch {
                        val msg = runCatching { HealthSync.sync(ctx) }.getOrElse { "Sync failed: ${it.message}" }
                        syncing = false
                        toast(ctx, msg)
                    }
                },
                enabled = !syncing,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (syncing) "Syncing…" else "Sync from Samsung Health")
            }
            if (data.settings.hcLastSync > 0) {
                Text(
                    "Last synced ${dayTime(data.settings.hcLastSync)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedText(),
                )
            }
        }
    }
}

@Composable
private fun LatestReadings(data: AppData) {
    val w = data.metrics.lastOrNull { it.type == "weight" }
    val bp = data.metrics.lastOrNull { it.type == "bp" }
    val p = data.metrics.lastOrNull { it.type == "pulse" }
    if (w == null && bp == null && p == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("Weight", w?.let { "${fmt1(it.v1)}" }, "kg", w?.time, Modifier.weight(1f))
        Stat("Blood pressure", bp?.let { "${fmt1(it.v1)}/${fmt1(it.v2)}" }, "", bp?.time, Modifier.weight(1.3f))
        Stat("Pulse", p?.let { fmt1(it.v1) }, "bpm", p?.time, Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String?, unit: String, time: Long?, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = mutedText(), maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (value != null && unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelSmall, color = mutedText(), modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        Text(time?.let { shortDate(dateOf(it)) } ?: " ", style = MaterialTheme.typography.labelSmall, color = mutedText())
    }
}
