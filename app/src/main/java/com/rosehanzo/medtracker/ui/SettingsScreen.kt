package com.rosehanzo.medtracker.ui

import android.app.DatePickerDialog
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.rosehanzo.medtracker.data.AppData
import com.rosehanzo.medtracker.data.Codec
import com.rosehanzo.medtracker.data.Logic
import com.rosehanzo.medtracker.data.FormReminder
import com.rosehanzo.medtracker.data.Phase
import com.rosehanzo.medtracker.data.Settings
import com.rosehanzo.medtracker.data.Store
import com.rosehanzo.medtracker.data.SymptomDef
import com.rosehanzo.medtracker.health.HealthSync
import com.rosehanzo.medtracker.reminders.Reminders
import com.rosehanzo.medtracker.update.Updater
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
        ScreenTitle("Settings")
        PlanSection(s)
        ScheduleSection(s)
        FormsSection(data)
        ReminderSection(s)
        SupplySection(data)
        TrackingSection(s)
        HealthSection(s)
        UpdateSection()
        BackupSection()
        Spacer(Modifier.size(8.dp))
    }
}

// ---------- Dose plan ----------

@Composable
private fun PlanSection(s: Settings) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Phase?>(null) }
    var name by remember { mutableStateOf(s.medName) }
    val phases = Logic.sortedPhases(s)
    val current = Logic.phaseFor(s, LocalDate.now())

    SectionCard("Dose plan", Icons.Filled.Medication) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                if (it.isNotBlank()) Logic.updateSettings(ctx, reschedule = false) { st -> st.copy(medName = it.trim()) }
            },
            label = { Text("Medication") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Taken once a day. Each phase runs until the next one starts, and the last one carries on until you change it.",
            style = MaterialTheme.typography.bodySmall,
            color = mutedText(),
        )
        if (phases.isEmpty()) {
            Text("No phases yet, so there are no reminders.", style = MaterialTheme.typography.bodyMedium, color = badText())
        }
        phases.forEachIndexed { i, p ->
            PhaseRow(p, phases.getOrNull(i + 1), isCurrent = p.id == current?.id) { editing = p }
        }
        OutlinedButton(
            onClick = { editing = newPhase(phases) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add a phase")
        }
    }

    editing?.let { phase ->
        PhaseDialog(
            initial = phase,
            isNew = s.phases.none { it.id == phase.id },
            onDismiss = { editing = null },
            onSave = {
                Logic.savePhase(ctx, it)
                editing = null
            },
            onDelete = {
                Logic.deletePhase(ctx, phase.id)
                editing = null
            },
        )
    }
}

private fun newPhase(phases: List<Phase>): Phase {
    val today = LocalDate.now()
    val last = phases.lastOrNull()
    val start = when {
        last == null -> today
        last.end != null -> LocalDate.parse(last.end).plusDays(1)
        else -> maxOf(today, LocalDate.parse(last.start).plusDays(7))
    }
    return Phase(
        id = "p${System.currentTimeMillis()}",
        start = start.toString(),
        end = null,
        pills = last?.pills ?: 1,
        daysOff = last?.daysOff ?: emptySet(),
    )
}

private fun phaseRange(p: Phase, next: Phase?): String {
    val start = LocalDate.parse(p.start)
    return when {
        p.end != null -> "${shortDate(start)} – ${shortDate(LocalDate.parse(p.end))}"
        next != null -> "${shortDate(start)} – ${shortDate(LocalDate.parse(next.start).minusDays(1))}"
        else -> "From ${shortDate(start)}"
    }
}

private fun phaseSummary(p: Phase): String {
    val amount = "${Logic.pillWord(p.pills)} a day"
    if (p.daysOff.isEmpty()) return "$amount, every day"
    val off = p.daysOff.sorted().joinToString(", ") { WEEKDAY_SHORT[it - 1] }
    return "$amount, none on $off"
}

@Composable
private fun PhaseRow(p: Phase, next: Phase?, isCurrent: Boolean, onClick: () -> Unit) {
    val accent = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(phaseRange(p, next), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(phaseSummary(p), style = MaterialTheme.typography.bodySmall, color = mutedText())
        }
        if (isCurrent) {
            Pill("Now", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        } else {
            Text("Edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun pickDate(ctx: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(
        ctx,
        { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

@Composable
private fun PhaseDialog(
    initial: Phase,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Phase) -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    var start by remember { mutableStateOf(LocalDate.parse(initial.start)) }
    var hasEnd by remember { mutableStateOf(initial.end != null) }
    var end by remember { mutableStateOf(initial.end?.let { LocalDate.parse(it) } ?: LocalDate.parse(initial.start).plusDays(6)) }
    var pills by remember { mutableIntStateOf(initial.pills.coerceIn(1, Logic.MAX_PILLS)) }
    var daysOff by remember { mutableStateOf(initial.daysOff) }
    val endOk = !hasEnd || !end.isBefore(start)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New phase" else "Edit phase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate(ctx, start) { start = it } }, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Starts", style = MaterialTheme.typography.labelSmall)
                            Text(shortDate(start), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = { pickDate(ctx, end) { end = it; hasEnd = true } },
                        enabled = hasEnd,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Ends", style = MaterialTheme.typography.labelSmall)
                            Text(if (hasEnd) shortDate(end) else "Open", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Has an end date", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = hasEnd, onCheckedChange = { hasEnd = it })
                }
                if (!endOk) {
                    Text("The end date is before the start date.", style = MaterialTheme.typography.bodySmall, color = badText())
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pills each day", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { if (pills > 1) pills-- }, enabled = pills > 1) {
                        Icon(Icons.Filled.Remove, contentDescription = "Fewer")
                    }
                    Text(
                        "$pills",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    FilledTonalIconButton(onClick = { if (pills < Logic.MAX_PILLS) pills++ }, enabled = pills < Logic.MAX_PILLS) {
                        Icon(Icons.Filled.Add, contentDescription = "More")
                    }
                }

                Text("Days with no pills", style = MaterialTheme.typography.bodyMedium)
                WeekdayToggles(daysOff) { day -> daysOff = if (day in daysOff) daysOff - day else daysOff + day }
                if (daysOff.size == 7) {
                    Text("Every day is off, so this phase has no doses.", style = MaterialTheme.typography.bodySmall, color = badText())
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = endOk,
                onClick = {
                    onSave(
                        initial.copy(
                            start = start.toString(),
                            end = if (hasEnd) end.toString() else null,
                            pills = pills,
                            daysOff = daysOff,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = badText()) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

// ---------- Dose time ----------

@Composable
private fun ScheduleSection(s: Settings) {
    val ctx = LocalContext.current
    SectionCard("Dose time", Icons.Filled.Schedule) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeButton("Weekdays", s.weekdayTime, Modifier.weight(1f)) { m ->
                Logic.updateSettings(ctx) { it.copy(weekdayTime = m) }
            }
            TimeButton("Weekends", s.weekendTime, Modifier.weight(1f)) { m ->
                Logic.updateSettings(ctx) { it.copy(weekendTime = m) }
            }
        }
        Text("Your weekend days", style = MaterialTheme.typography.labelLarge)
        WeekdayToggles(s.weekendDays) { day ->
            Logic.updateSettings(ctx) {
                it.copy(weekendDays = if (day in it.weekendDays) it.weekendDays - day else it.weekendDays + day)
            }
        }
    }
}

@Composable
private fun TimeButton(label: String, minutes: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
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
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(Logic.fmtTime(minutes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------- Forms ----------

fun repeatLabel(everyWeeks: Int): String = when (everyWeeks) {
    0 -> "Once"
    1 -> "Every week"
    else -> "Every $everyWeeks weeks"
}

private fun formSummary(f: FormReminder, today: LocalDate): String {
    val time = Logic.fmtTime(f.time)
    val start = LocalDate.parse(f.start)
    if (f.everyWeeks == 0) return "${mediumDate(start)} at $time"
    val next = Logic.nextOccurrence(f, today)
    return if (next == null) "${repeatLabel(f.everyWeeks)} at $time"
    else "${repeatLabel(f.everyWeeks)} at $time · next ${mediumDate(next)}"
}

@Composable
private fun FormsSection(data: AppData) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<FormReminder?>(null) }
    val today = LocalDate.now()

    SectionCard("Forms", Icons.Filled.Description) {
        Text(
            "Reminders for forms to fill in. They show on the calendar and remind you at the time you pick, until you tap Done.",
            style = MaterialTheme.typography.bodySmall,
            color = mutedText(),
        )
        if (data.forms.isEmpty()) {
            Text("No form reminders yet.", style = MaterialTheme.typography.bodyMedium, color = mutedText())
        }
        data.forms.sortedBy { Logic.nextOccurrence(it, today) ?: LocalDate.MAX }.forEach { f ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { editing = f }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Filled.Description, size = 30)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(formSummary(f, today), style = MaterialTheme.typography.bodySmall, color = mutedText())
                }
                Text("Edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        OutlinedButton(
            onClick = {
                editing = FormReminder(id = "f${System.currentTimeMillis()}", start = today.plusDays(1).toString())
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add a form reminder")
        }
    }

    editing?.let { form ->
        FormDialog(
            initial = form,
            isNew = data.forms.none { it.id == form.id },
            onDismiss = { editing = null },
            onSave = {
                Logic.saveForm(ctx, it)
                editing = null
            },
            onDelete = {
                Logic.deleteForm(ctx, form.id)
                editing = null
            },
        )
    }
}

@Composable
private fun FormDialog(
    initial: FormReminder,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (FormReminder) -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var start by remember { mutableStateOf(LocalDate.parse(initial.start)) }
    var time by remember { mutableIntStateOf(initial.time) }
    var every by remember { mutableIntStateOf(initial.everyWeeks) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New form reminder" else "Edit form reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate(ctx, start) { start = it } }, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (every == 0) "Date" else "First date", style = MaterialTheme.typography.labelSmall)
                            Text(shortDate(start), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                ctx,
                                { _, h, m -> time = h * 60 + m },
                                time / 60,
                                time % 60,
                                DateFormat.is24HourFormat(ctx),
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Time", style = MaterialTheme.typography.labelSmall)
                            Text(Logic.fmtTime(time), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                Text("Repeats", style = MaterialTheme.typography.bodyMedium)
                Column {
                    listOf(0, 1, 2, 4).forEach { w ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { every = w },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = every == w, onClick = { every = w })
                            Text(repeatLabel(w), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(initial.copy(name = name.trim(), start = start.toString(), time = time, everyWeeks = every))
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = badText()) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
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

    SectionCard("Reminders", Icons.Filled.Notifications) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dose reminders", style = MaterialTheme.typography.bodyLarge)
                Text("Keeps reminding you until you tap Taken", style = MaterialTheme.typography.bodySmall, color = mutedText())
            }
            Switch(checked = s.remindersOn, onCheckedChange = { on -> Logic.updateSettings(ctx) { it.copy(remindersOn = on) } })
        }

        Text("Remind me again every … minutes", style = MaterialTheme.typography.labelLarge)
        NumberChoiceRow(listOf(5, 10, 15, 30), s.nagMinutes) { n ->
            Logic.updateSettings(ctx) { it.copy(nagMinutes = n) }
        }

        if (!notificationsOn) {
            Text("Notifications are switched off for Cadence, so reminders can't appear.", style = MaterialTheme.typography.bodyMedium, color = badText())
            Button(onClick = { openNotificationSettings(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Turn on notifications") }
        }
        if (!exactOk) {
            Text("Reminders may arrive a few minutes late until you allow exact alarms.", style = MaterialTheme.typography.bodyMedium, color = badText())
            Button(onClick = { openExactAlarmSettings(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Allow exact alarms") }
        }
        if (!batteryFree) {
            Text(
                "Samsung phones can put apps to sleep and delay reminders. Allowing unrestricted battery use stops that.",
                style = MaterialTheme.typography.bodySmall,
                color = mutedText(),
            )
            OutlinedButton(onClick = { openBatterySettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Don't put Cadence to sleep")
            }
        }
        OutlinedButton(
            onClick = { if (!Reminders.sendTest(ctx)) toast(ctx, "Turn on notifications first") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Send a test reminder") }
    }
}

private fun openNotificationSettings(ctx: Context) {
    safeStart(
        ctx,
        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName),
    )
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
private fun SupplySection(data: AppData) {
    val ctx = LocalContext.current
    val s = data.settings
    var dialog by remember { mutableStateOf<String?>(null) }

    SectionCard("Pill supply", Icons.Filled.Inventory2) {
        val days = Logic.daysOfSupply(data)
        Text(
            when {
                days < 0 -> "Not set yet"
                days >= 730 -> Logic.pillWord(s.pillsLeft)
                else -> "${Logic.pillWord(s.pillsLeft)} · enough for $days days"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Goes down by the day's pills each time you mark a dose as taken. Days left follow your plan.",
            style = MaterialTheme.typography.bodySmall,
            color = mutedText(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { dialog = "set" }, modifier = Modifier.weight(1f)) { Text("Set count") }
            Button(onClick = { dialog = "add" }, modifier = Modifier.weight(1f)) { Text("Add a refill") }
        }
        Text("Warn me when I have this many days left", style = MaterialTheme.typography.labelLarge)
        NumberChoiceRow(listOf(3, 5, 7, 10, 14, 21, 30), s.lowStockDays) { n ->
            Logic.updateSettings(ctx, reschedule = false) { it.copy(lowStockDays = n, stockWarnDate = "") }
        }
    }

    when (dialog) {
        "set" -> NumberDialog("Pills you have now", if (s.pillsLeft >= 0) s.pillsLeft else 30, { dialog = null }) { n ->
            Logic.updateSettings(ctx, reschedule = false) { it.copy(pillsLeft = n, stockWarnDate = "") }
        }
        "add" -> NumberDialog("Pills in the new pack", 30, { dialog = null }) { n ->
            Logic.updateSettings(ctx, reschedule = false) { it.copy(pillsLeft = maxOf(it.pillsLeft, 0) + n, stockWarnDate = "") }
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

    SectionCard("Daily check-in", Icons.Filled.Mood) {
        Text(
            "Choose what appears in your check-in. Hidden items keep their history.",
            style = MaterialTheme.typography.bodySmall,
            color = mutedText(),
        )
        s.symptoms.forEach { def ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(def.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "1 = ${def.low.lowercase()} · 10 = ${def.high.lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedText(),
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
                    Text(
                        "Rated 1 = none to 10 = severe, like everything else. Name it so more is worse, e.g. \"Low motivation\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedText(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val id = "c${System.currentTimeMillis()}"
                        val def = SymptomDef(id, name.trim(), "None", "Severe", custom = true)
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

    SectionCard("Samsung Health", Icons.Filled.FavoriteBorder) {
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
                        Text("Last synced ${dayTime(s.hcLastSync)}", style = MaterialTheme.typography.labelSmall, color = mutedText())
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sync when I open Cadence", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
                    color = mutedText(),
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

// ---------- Updates ----------

/** Update button, progress and Install button. Shared by Settings and the Today banner. */
@Composable
fun UpdateControls(state: Updater.State) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    when (state) {
        is Updater.State.Ready -> {
            Text("Build ${state.update.build} is ready. Your data stays as it is.", style = MaterialTheme.typography.bodySmall, color = goodText())
            Button(
                onClick = {
                    if (!Updater.canInstall(ctx)) {
                        toast(ctx, "Allow Cadence to install apps, then come back and tap Update again")
                        Updater.openInstallPermission(ctx)
                    } else {
                        scope.launch { Updater.downloadAndInstall(ctx, state.update) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Update now") }
        }
        is Updater.State.Downloading -> {
            Text("Downloading… ${state.percent}%", style = MaterialTheme.typography.bodySmall, color = mutedText())
            LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        is Updater.State.Downloaded -> {
            Text(
                "Build ${state.build} is downloaded. Tap Install, then Update on Android's screen.",
                style = MaterialTheme.typography.bodySmall,
                color = mutedText(),
            )
            Button(
                onClick = {
                    if (!Updater.canInstall(ctx)) {
                        toast(ctx, "Allow Cadence to install apps, then come back and tap Install")
                        Updater.openInstallPermission(ctx)
                    } else {
                        Updater.openInstaller(ctx, java.io.File(state.path))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Install") }
        }
        else -> {}
    }
}

@Composable
private fun UpdateSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()

    SectionCard("Updates", Icons.Filled.SystemUpdate) {
        Text("You're on build ${Updater.currentBuild}", style = MaterialTheme.typography.bodyMedium)
        when (val st = state) {
            is Updater.State.Checking -> Text("Checking…", style = MaterialTheme.typography.bodySmall, color = mutedText())
            is Updater.State.UpToDate -> Text("You have the latest version.", style = MaterialTheme.typography.bodySmall, color = goodText())
            is Updater.State.Ready, is Updater.State.Downloading, is Updater.State.Downloaded -> UpdateControls(st)
            is Updater.State.Failed -> Text(st.message, style = MaterialTheme.typography.bodySmall, color = badText())
            is Updater.State.Idle -> {}
        }
        if (state !is Updater.State.Downloading && state !is Updater.State.Downloaded && state !is Updater.State.Ready) {
            OutlinedButton(onClick = { scope.launch { Updater.check(force = true) } }, modifier = Modifier.fillMaxWidth()) {
                Text("Check for updates")
            }
        }
        Text(
            "Cadence also checks by itself when you open it. Updates keep all your data.",
            style = MaterialTheme.typography.bodySmall,
            color = mutedText(),
        )
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
        if (parsed == null) toast(ctx, "That file isn't a Cadence backup") else pending = parsed
    }

    SectionCard("Backup", Icons.Filled.Backup) {
        Text(
            "Your data stays on this phone. Save a backup now and then, and before changing phones.",
            style = MaterialTheme.typography.bodySmall,
            color = mutedText(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exporter.launch("cadence-backup-${LocalDate.now()}.json") },
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
            text = { Text("This replaces everything currently in Cadence with the backup.") },
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
