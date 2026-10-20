package com.gnsh.batterystats.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.gnsh.batterystats.data.Sample
import java.text.DateFormat
import java.util.Date

/**
 * Battery level over the last [windowMs]. One series, so no legend: the card title names it.
 * Charging spans get a soft band; press and drag to read exact values.
 */
@Composable
fun LevelChart(samples: List<Sample>, now: Long, modifier: Modifier = Modifier, windowMs: Long = 24 * 3_600_000L) {
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val from = now - windowMs
    val points = remember(samples, from) { samples.filter { it.time >= from } }
    var selectedX by remember { mutableStateOf<Float?>(null) }
    val timeFmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(modifier) {
        // Readout row stays in text ink; the line carries identity.
        val selected = selectedX?.let { x -> points.minByOrNull { kotlin.math.abs(it.time - x) } }
        Text(
            selected?.let { "${timeFmt.format(Date(it.time))} · ${it.level}%" + if (it.charging) " · charging" else "" }
                ?: "Press and drag to inspect",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected != null) colors.onSurface else colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics {
                    contentDescription = points.lastOrNull()
                        ?.let { "Battery level chart, last 24 hours, now ${it.level} percent" } ?: "Battery level chart"
                }
                .pointerInput(from, now) {
                    fun toTime(x: Float) = from + (x / size.width * windowMs).toLong()
                    detectHorizontalDragGestures(
                        onDragStart = { selectedX = toTime(it.x).toFloat() },
                        onDragEnd = { selectedX = null },
                        onDragCancel = { selectedX = null },
                    ) { change, _ -> selectedX = toTime(change.position.x).toFloat() }
                }
                .pointerInput(from, now) {
                    detectTapGestures(onPress = {
                        selectedX = (from + (it.x / size.width * windowMs).toLong()).toFloat()
                        tryAwaitRelease()
                        selectedX = null
                    })
                },
        ) {
            val axisW = 36.dp.toPx()
            val bottomPad = 18.dp.toPx()
            val plotW = size.width - axisW
            val plotH = size.height - bottomPad
            fun x(t: Long) = axisW + (t - from).toFloat() / windowMs * plotW
            fun y(level: Int) = plotH - level / 100f * plotH

            // Recessive grid and axis labels.
            listOf(0, 50, 100).forEach { lv ->
                drawLine(
                    colors.outlineVariant, Offset(axisW, y(lv)), Offset(size.width, y(lv)),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (lv == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
                val label = measurer.measure("$lv%", labelStyle)
                drawText(label, topLeft = Offset(0f, (y(lv) - label.size.height / 2f).coerceIn(0f, plotH)))
            }
            listOf(0L to "24h ago", windowMs / 2 to "12h", windowMs to "Now").forEach { (off, text) ->
                val label = measurer.measure(text, labelStyle)
                val lx = (x(from + off) - label.size.width / 2f).coerceIn(axisW, size.width - label.size.width)
                drawText(label, topLeft = Offset(lx, plotH + 4.dp.toPx()))
            }
            if (points.size < 2) return@Canvas

            // Charging bands.
            var bandStart: Long? = null
            points.forEachIndexed { i, s ->
                val end = i == points.lastIndex
                if (s.charging && bandStart == null) bandStart = s.time
                if ((!s.charging || end) && bandStart != null) {
                    val left = x(bandStart!!)
                    drawRect(
                        colors.primary.copy(alpha = 0.10f),
                        topLeft = Offset(left, 0f), size = Size(x(s.time) - left, plotH),
                    )
                    bandStart = null
                }
            }

            // Line + area, broken across gaps longer than 2 hours.
            val line = Path()
            val area = Path()
            var segStart: Sample? = null
            var prev: Sample? = null
            fun closeArea(last: Sample) {
                val first = segStart ?: return
                area.lineTo(x(last.time), plotH); area.lineTo(x(first.time), plotH); area.close()
            }
            points.forEach { s ->
                val p = prev
                if (p == null || s.time - p.time > 2 * 3_600_000L) {
                    if (p != null) closeArea(p)
                    line.moveTo(x(s.time), y(s.level))
                    area.moveTo(x(s.time), y(s.level))
                    segStart = s
                } else {
                    line.lineTo(x(s.time), y(s.level))
                    area.lineTo(x(s.time), y(s.level))
                }
                prev = s
            }
            prev?.let { closeArea(it) }
            drawPath(area, Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.22f), colors.primary.copy(alpha = 0f)), endY = plotH))
            drawPath(line, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Crosshair and marker with a surface ring.
            val sel = selectedX?.let { t -> points.minByOrNull { kotlin.math.abs(it.time - t) } }
            if (sel != null) {
                val sx = x(sel.time)
                drawLine(colors.outline, Offset(sx, 0f), Offset(sx, plotH), strokeWidth = 1.dp.toPx())
                drawCircle(colors.surfaceContainerHigh, radius = 6.dp.toPx(), center = Offset(sx, y(sel.level)))
                drawCircle(colors.primary, radius = 4.dp.toPx(), center = Offset(sx, y(sel.level)))
            }
        }
    }
}
