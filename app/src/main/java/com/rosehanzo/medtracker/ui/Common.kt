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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

val GoodFill = Color(0xFF2E7D32)
val WarnFill = Color(0xFFF9A825)
val BadFill = Color(0xFFC62828)

@Composable
fun goodText(): Color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

@Composable
fun badText(): Color = if (isSystemInDarkTheme()) Color(0xFFEF9A9A) else Color(0xFFC62828)

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

/** Colour for a 1–10 rating: green is good, red is bad. */
fun ratingColor(value: Int, highIsGood: Boolean): Color {
    val badness = (if (highIsGood) 10 - value else value - 1) / 9f
    return if (badness < 0.5f) lerp(GoodFill, WarnFill, badness * 2f) else lerp(WarnFill, BadFill, (badness - 0.5f) * 2f)
}

fun onFill(c: Color): Color = if (c.luminance() > 0.5f) Color.Black else Color.White

fun statusColor(s: DayStatus): Color? = when (s) {
    DayStatus.FULL -> GoodFill
    DayStatus.PARTIAL -> WarnFill
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
                    color = if (selected) onFill(fill) else MaterialTheme.colorScheme.onSurfaceVariant,
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

fun Modifier.cardPadding() = this.padding(horizontal = 16.dp, vertical = 12.dp)
