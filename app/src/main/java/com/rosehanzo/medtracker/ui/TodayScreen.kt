package com.rosehanzo.medtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rosehanzo.medtracker.data.AppData
import com.rosehanzo.medtracker.data.Logic
import com.rosehanzo.medtracker.data.Metric
import com.rosehanzo.medtracker.health.HealthSync
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
        Column {
            Text(longDate(today), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                data.settings.medName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DoseCard(data, today)
        CheckInCard(data, today)
        MeasureCard(data)
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun DoseCard(data: AppData, today: LocalDate) {
    val ctx = LocalContext.current
    val s = data.settings
    val taken = data.doses[today.toString()].orEmpty()

    SectionCard(if (s.dosesPerDay == 1) "Today's dose" else "Today's doses") {
        for (slot in 0 until s.dosesPerDay) {
            val t = taken.firstOrNull { it.slot == slot }
            val due = Logic.fmtTime(Logic.slotMinutes(s, today, slot))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (s.dosesPerDay == 1) "Due at $due" else "Dose ${slot + 1} · due at $due",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (t != null) "Taken at ${timeOf(t.time)}" else "Not taken yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (t != null) goodText() else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (t == null) {
                    Button(onClick = { Logic.markTaken(ctx, today, slot) }) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Take")
                    }
                } else {
                    TextButton(onClick = { Logic.unmarkTaken(ctx, today, slot) }) { Text("Undo") }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val days = Logic.daysOfSupply(s)
        val low = days in 0..s.lowStockDays
        Text(
            if (days < 0) "Add your pill count in Settings to track your supply"
            else "${s.pillsLeft} pills left · about $days days",
            style = MaterialTheme.typography.bodyMedium,
            color = if (low) badText() else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CheckInCard(data: AppData, today: LocalDate) {
    val ctx = LocalContext.current
    val defs = data.settings.symptoms.filter { it.visible }
    val saved = data.checkins[today.toString()].orEmpty()
    val values = remember(today, saved) { mutableStateMapOf<String, Int>().apply { putAll(saved) } }

    SectionCard("How are you today?") {
        if (defs.isEmpty()) {
            Text(
                "Everything is hidden. Turn on what you want to track in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        Text(
            "Tap a number for anything you want to log, then save once.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        defs.forEach { def ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(def.name, style = MaterialTheme.typography.titleSmall)
                ScaleRow(values[def.id], def.highIsGood) { v ->
                    if (v == null) values.remove(def.id) else values[def.id] = v
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(def.low, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(def.high, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            modifier = Modifier.fillMaxWidth(),
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

    SectionCard("Measurements") {
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
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save readings") }

        LatestReadings(data)

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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (syncing) "Syncing…" else "Sync from Samsung Health")
            }
            if (data.settings.hcLastSync > 0) {
                Text(
                    "Last synced ${dayTime(data.settings.hcLastSync)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Latest", style = MaterialTheme.typography.labelLarge)
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val style = MaterialTheme.typography.bodySmall
        fun src(m: Metric) = if (m.source == "hc") " · watch" else ""
        if (w != null) Text("Weight ${fmt1(w.v1)} kg · ${dayTime(w.time)}${src(w)}", style = style, color = muted)
        if (bp != null) Text("Blood pressure ${fmt1(bp.v1)}/${fmt1(bp.v2)} · ${dayTime(bp.time)}${src(bp)}", style = style, color = muted)
        if (p != null) {
            val label = if (p.source == "hc") "Average heart rate" else "Pulse"
            Text("$label ${fmt1(p.v1)} bpm · ${dayTime(p.time)}${src(p)}", style = style, color = muted)
        }
    }
}
