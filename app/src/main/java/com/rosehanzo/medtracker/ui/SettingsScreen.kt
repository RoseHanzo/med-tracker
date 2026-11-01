package com.rosehanzo.medtracker.ui

import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.rosehanzo.medtracker.BuildConfig
import com.rosehanzo.medtracker.data.AppData
import com.rosehanzo.medtracker.data.Codec
import com.rosehanzo.medtracker.data.Logic
import com.rosehanzo.medtracker.data.Settings
import com.rosehanzo.medtracker.data.Store
import com.rosehanzo.medtracker.data.SymptomDef
import com.rosehanzo.medtracker.health.HealthSync
import com.rosehanzo.medtracker.reminders.Reminders
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(data: AppData) {
    val s = data.settings
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        MedicationSection(s)
        ReminderSection(s)
        SupplySection(s)
        TrackingSection(s)
        HealthSection(s)
        BackupSection()
        Text(
            "Med Tracker ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

// ---------- Medication & schedule ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationSection(s: Settings) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(s.medName) }

    SectionCard("Medication") {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                if (it.isNotBlank()) Logic.updateSettings(ctx, reschedule = false) { st -> st.copy(medName = it.trim()) }
            },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Doses a day", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..3).forEach { n ->
                    FilterChip(
                        selected = s.dosesPerDay == n,
                        onClick = { Logic.updateSettings(ctx) { it.copy(dosesPerDay = n) } },
                        label = { Text("$n") },
                    )
                }
            }
        }
        Text(
            "One pill per dose. Each dose has its own reminder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Reminder times", style = MaterialTheme.typography.labelLarge)
        for (slot in 0 until s.dosesPerDay) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (s.dosesPerDay == 1) "Dose" else "Dose ${slot + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TimeButton("Weekdays", s.weekdayTimes[slot]) { m ->
                    Logic.updateSettings(ctx) { it.copy(weekdayTimes = it.weekdayTimes.toMutableList().also { l -> l[slot] = m }) }
                }
                TimeButton("Weekends", s.weekendTimes[slot]) { m ->
                    Logic.updateSettings(ctx) { it.copy(weekendTimes = it.weekendTimes.toMutableList().also { l -> l[slot] = m }) }
                }
            }
        }

        Text("Weekend days", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, label ->
                val day = i + 1
                val on = day in s.weekendDays
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            Logic.updateSettings(ctx) {
                                it.copy(weekendDays = if (on) it.weekendDays - day else it.weekendDays + day)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeButton(label: String, minutes: Int, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                ctx,
                { _, h, m -> onPick(h * 60 + m) },
                minutes / 60,
                minutes % 60,
                DateFormat.is24HourFormat(ctx),
            ).show()
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(Logic.fmtTime(minutes), style = MaterialTheme.typography.titleSmall)
        }
    }
}

// ---------- Reminders ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(s: Settings) {
    val ctx = LocalContext.current
    val tick = ResumeTick.current
    val notificationsOn = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val exactOk = remember(tick) { Reminders.canExact(ctx) }
    val batteryFree = remember(tick) {
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true
    }

    SectionCard("Reminders") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dose reminders", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Keeps reminding you until you tap Taken",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = s.remindersOn, onCheckedChange = { on -> Logic.updateSettings(ctx) { it.copy(remindersOn = on) } })
        }

        Text("Remind me again every", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(5, 10, 15, 30).forEach { n ->
                FilterChip(
                    selected = s.nagMinutes == n,
                    onClick = { Logic.updateSettings(ctx) { it.copy(nagMinutes = n) } },
                    label = { Text("$n min") },
                )
            }
        }

        if (!notificationsOn) {
            Warning("Notifications are switched off for this app, so reminders can't appear.")
            Button(onClick = { openNotificationSettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Turn on notifications")
            }
        }
        if (!exactOk) {
            Warning("Reminders may arrive a few minutes late until you allow exact alarms.")
            Button(onClick = { openExactAlarmSettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Allow exact alarms")
            }
        }
        if (!batteryFree) {
            Text(
                "Samsung phones can put apps to sleep and delay reminders. Allowing unrestricted battery use stops that.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { openBatterySettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Don't put this app to sleep")
            }
        }
        OutlinedButton(
            onClick = {
                if (!Reminders.sendTest(ctx)) toast(ctx, "Turn on notifications first")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Send a test reminder") }
    }
}

@Composable
private fun Warning(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = badText())
}

private fun openNotificationSettings(ctx: Context) {
    val i = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
    safeStart(ctx, i)
}

private fun openExactAlarmSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 31) {
        safeStart(ctx, Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
    }
}

@android.annotation.SuppressLint("BatteryLife")
private fun openBatterySettings(ctx: Context) {
    val direct = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
    if (!safeStart(ctx, direct)) safeStart(ctx, Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

private fun safeStart(ctx: Context, intent: Intent): Boolean = try {
    ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

// ---------- Pill supply ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplySection(s: Settings) {
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }

    SectionCard("Pill supply") {
        val days = Logic.daysOfSupply(s)
        Text(
            if (days < 0) "Not set yet" else "${s.pillsLeft} pills · about $days days",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Goes down by one each time you mark a dose as taken.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { dialog = "set" }, modifier = Modifier.weight(1f)) { Text("Set count") }
            Button(onClick = { dialog = "add" }, modifier = Modifier.weight(1f)) { Text("Add a refill") }
        }
        Text("Warn me when I have this many days left", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(3, 5, 7, 14).forEach { n ->
                FilterChip(
                    selected = s.lowStockDays == n,
                    onClick = { Logic.updateSettings(ctx, reschedule = false) { it.copy(lowStockDays = n, stockWarnDate = "") } },
                    label = { Text("$n") },
                )
            }
        }
    }

    when (dialog) {
        "set" -> NumberDialog("Pills you have now", if (s.pillsLeft >= 0) s.pillsLeft else 30, { dialog = null }) { n ->
            Logic.updateSettings(ctx, reschedule = false) { it.copy(pillsLeft = n, stockWarnDate = "") }
        }
        "add" -> NumberDialog("Pills in the new pack", 30, { dialog = null }) { n ->
            Logic.updateSettings(ctx, reschedule = false) {
                it.copy(pillsLeft = maxOf(it.pillsLeft, 0) + n, stockWarnDate = "")
            }
        }
    }
}

@Composable
private fun NumberDialog(title: String, initial: Int, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) text = v },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                text.toIntOrNull()?.let(onDone)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------- What to track ----------

@Composable
private fun TrackingSection(s: Settings) {
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }

    SectionCard("Daily check-in") {
        Text(
            "Choose what appears in your check-in. Hidden items keep their history.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        s.symptoms.forEach { def ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(def.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "1 = ${def.low.lowercase()} · 10 = ${def.high.lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (def.custom) {
                    IconButton(onClick = {
                        Logic.updateSettings(ctx, reschedule = false) { st -> st.copy(symptoms = st.symptoms.filterNot { it.id == def.id }) }
                    }) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${def.name}") }
                }
                Switch(
                    checked = def.visible,
                    onCheckedChange = { on ->
                        Logic.updateSettings(ctx, reschedule = false) { st ->
                            st.copy(symptoms = st.symptoms.map { if (it.id == def.id) it.copy(visible = on) else it })
                        }
                    },
                )
            }
        }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add your own") }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var highIsGood by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Track something new") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 30) name = it },
                        label = { Text("Name, e.g. Jaw clenching") },
                        singleLine = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = highIsGood, onCheckedChange = { highIsGood = it })
                        Text("Higher is better (like mood)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val def = if (highIsGood) {
                            SymptomDef("c${System.currentTimeMillis()}", name.trim(), "Very low", "Great", highIsGood = true, custom = true)
                        } else {
                            SymptomDef("c${System.currentTimeMillis()}", name.trim(), "None", "Severe", custom = true)
                        }
                        Logic.updateSettings(ctx, reschedule = false) { it.copy(symptoms = it.symptoms + def) }
                        adding = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}

// ---------- Samsung Health ----------

@Composable
private fun HealthSection(s: Settings) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = ResumeTick.current
    val status = remember(tick) { HealthSync.status(ctx) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var syncing by remember { mutableStateOf(false) }

    LaunchedEffect(tick) { granted = HealthSync.granted(ctx) }

    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { result ->
        granted = result.intersect(HealthSync.PERMISSIONS)
        if (granted.isNotEmpty()) {
            scope.launch { toast(ctx, runCatching { HealthSync.sync(ctx) }.getOrElse { "Sync failed: ${it.message}" }) }
        }
    }

    SectionCard("Samsung Health") {
        when (status) {
            HealthConnectClient.SDK_AVAILABLE -> {
                if (granted.isEmpty()) {
                    Text(
                        "Bring in weight, blood pressure and heart rate from your watch and scale automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { launcher.launch(HealthSync.PERMISSIONS) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Connect")
                    }
                } else {
                    val parts = listOfNotNull(
                        "weight".takeIf { HealthSync.WEIGHT in granted },
                        "blood pressure".takeIf { HealthSync.BLOOD_PRESSURE in granted },
                        "heart rate".takeIf { HealthSync.HEART_RATE in granted },
                    )
                    Text("Connected: ${parts.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = goodText())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                syncing = true
                                scope.launch {
                                    val msg = runCatching { HealthSync.sync(ctx) }.getOrElse { "Sync failed: ${it.message}" }
                                    syncing = false
                                    toast(ctx, msg)
                                }
                            },
                            enabled = !syncing,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (syncing) "Syncing…" else "Sync now") }
                        OutlinedButton(onClick = { launcher.launch(HealthSync.PERMISSIONS) }, modifier = Modifier.weight(1f)) {
                            Text("Permissions")
                        }
                    }
                    if (s.hcLastSync > 0) {
                        Text(
                            "Last synced ${dayTime(s.hcLastSync)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sync when I open the app", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = s.hcAutoSync,
                        onCheckedChange = { on -> Logic.updateSettings(ctx, reschedule = false) { it.copy(hcAutoSync = on) } },
                    )
                }
                Text(
                    "Samsung Health has to share with Health Connect too: in Samsung Health, open Settings, " +
                        "then Health Connect, and allow weight, blood pressure and heart rate. " +
                        "Samsung keeps stress scores inside Samsung Health, so other apps can't read them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                Text("Health Connect needs installing or updating first.", style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                        if (!safeStart(ctx, market)) {
                            safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Get Health Connect") }
            }
            else -> Text(
                "Health Connect isn't available on this phone, so readings need entering by hand.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ---------- Backup ----------

@Composable
private fun BackupSection() {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<AppData?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Codec.encode(Store.data(ctx)).toByteArray()) }
        }.isSuccess
        toast(ctx, if (ok) "Backup saved" else "Couldn't save the backup")
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val parsed = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { Codec.decode(it.readBytes().decodeToString()) }
        }.getOrNull()
        if (parsed == null) toast(ctx, "That file isn't a Med Tracker backup") else pending = parsed
    }

    SectionCard("Backup") {
        Text(
            "Your data stays on this phone. Save a backup before reinstalling or changing phones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exporter.launch("med-tracker-backup-${LocalDate.now()}.json") },
                modifier = Modifier.weight(1f),
            ) { Text("Save backup") }
            OutlinedButton(
                onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.weight(1f),
            ) { Text("Restore") }
        }
    }

    pending?.let { restored ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore this backup?") },
            text = { Text("This replaces everything currently in the app with the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    Store.replaceAll(ctx, restored)
                    Reminders.scheduleAll(ctx)
                    pending = null
                    toast(ctx, "Backup restored")
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}
