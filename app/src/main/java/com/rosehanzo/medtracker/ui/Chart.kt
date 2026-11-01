package com.rosehanzo.medtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Series(val name: String, val color: Color, val points: List<Pair<Double, Double>>)

/** Simple line chart. x values are day numbers (see [dayX]). */
@Composable
fun LineChart(
    series: List<Series>,
    xMin: Double,
    xMax: Double,
    yMin: Double? = null,
    yMax: Double? = null,
    height: Dp = 170.dp,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(Modifier.fillMaxWidth().height(height)) {
        val all = series.flatMap { it.points }
        if (all.isEmpty()) return@Canvas

        var lo = yMin ?: all.minOf { it.second }
        var hi = yMax ?: all.maxOf { it.second }
        if (hi - lo < 1e-6) {
            lo -= 1.0
            hi += 1.0
        } else if (yMin == null || yMax == null) {
            val pad = (hi - lo) * 0.15
            if (yMin == null) lo -= pad
            if (yMax == null) hi += pad
        }

        val left = 34.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val xSpan = (xMax - xMin).coerceAtLeast(1e-9)

        fun x(v: Double) = left + ((v - xMin) / xSpan).toFloat() * (right - left)
        fun y(v: Double) = bottom - ((v - lo) / (hi - lo)).toFloat() * (bottom - top)

        for (k in 0..2) {
            val v = lo + (hi - lo) * k / 2.0
            val yy = y(v)
            drawLine(gridColor, Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
            drawText(
                textMeasurer = measurer,
                text = fmt1(v),
                topLeft = Offset(0f, yy - 8.dp.toPx()),
                style = labelStyle,
            )
        }

        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val dot = 3.5.dp.toPx()
        series.forEach { s ->
            val pts = s.points.sortedBy { it.first }.map { Offset(x(it.first), y(it.second)) }
            if (pts.size > 1) {
                val path = Path()
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                drawPath(path, s.color, style = stroke)
            }
            if (pts.size <= 45) pts.forEach { drawCircle(s.color, dot, it) }
        }
    }
}
