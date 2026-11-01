package com.rosehanzo.medtracker.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rosehanzo.medtracker.data.DayStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Bumped every time the app comes back to the foreground, so permission states refresh. */
val ResumeTick = compositionLocalOf { 0 }

val GoodFill = Color(0xFF2E9D6B)
val WarnFill = Color(0xFFF2B33D)
val BadFill = Color(0xFFE0564F)

@Composable
fun goodText(): Color = if (isSystemInDarkTheme()) Color(0xFF7DD6A8) else Color(0xFF1E7A50)

@Composable
fun badText(): Color = if (isSystemInDarkTheme()) Color(0xFFFF9C94) else Color(0xFFB3261E)

@Composable
fun mutedText(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/** Screen title at the top of each tab. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = mutedText())
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, size: Int = 34) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.56f).dp))
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        IconBadge(icon)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/** Small rounded label, e.g. "2 pills". */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, fontWeight = FontWeight.Medium)
    }
}

/** Colour for a 1–10 rating: green is good, red is bad. */
fun ratingColor(value: Int, highIsGood: Boolean): Color {
    val badness = (if (highIsGood) 10 - value else value - 1) / 9f
    return if (badness < 0.5f) lerp(GoodFill, WarnFill, badness * 2f) else lerp(WarnFill, BadFill, (badness - 0.5f) * 2f)
}

fun onFill(c: Color): Color = if (c.luminance() > 0.5f) Color(0xFF1B1B1B) else Color.White

fun statusColor(s: DayStatus): Color? = when (s) {
    DayStatus.TAKEN -> GoodFill
    DayStatus.MISSED -> BadFill
    else -> null
}

/** Horizontal row of tappable 1–10 circles. Tap the selected one again to clear it. */
@Composable
fun ScaleRow(value: Int?, highIsGood: Boolean, onSelect: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..10) {
            val selected = value == i
            val fill = if (selected) ratingColor(i, highIsGood) else MaterialTheme.colorScheme.surfaceContainerHigh
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(fill)
                    .clickable { onSelect(if (selected) null else i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$i",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) onFill(fill) else mutedText(),
                )
            }
        }
    }
}

@Composable
fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            if (v.length <= 6 && v.all { it.isDigit() || it == '.' || it == ',' }) onChange(v)
        },
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

fun parseNumber(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

private val zone: ZoneId get() = ZoneId.systemDefault()

fun timeOf(millis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(millis).atZone(zone))

fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

fun shortDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))

fun mediumDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK))

fun longDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK))

fun dayTime(millis: Long): String {
    val d = dateOf(millis)
    val day = when (d) {
        LocalDate.now() -> "today"
        LocalDate.now().minusDays(1) -> "yesterday"
        else -> shortDate(d)
    }
    return "$day, ${timeOf(millis)}"
}

/** Day number with fraction, used as the x position in charts. */
fun dayX(millis: Long): Double {
    val z = Instant.ofEpochMilli(millis).atZone(zone)
    return z.toLocalDate().toEpochDay() + z.toLocalTime().toSecondOfDay() / 86400.0
}

fun fmt1(v: Double): String =
    if (abs(v - v.roundToInt()) < 0.05) v.roundToInt().toString() else String.format(Locale.UK, "%.1f", v)

/** Row of equal-width number tiles; the unit goes in the heading above so every label fits. */
@Composable
fun NumberChoiceRow(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { n ->
            val on = n == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSelect(n) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$n",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

val WEEKDAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
val WEEKDAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Row of 7 round day toggles (Monday first). */
@Composable
fun WeekdayToggles(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEKDAY_LETTERS.forEachIndexed { i, label ->
            val day = i + 1
            val on = day in selected
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else mutedText(),
                )
            }
        }
    }
}
