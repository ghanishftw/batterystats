package com.gnsh.batterystats.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import com.gnsh.batterystats.data.Drain
import com.gnsh.batterystats.data.Fmt
import com.gnsh.batterystats.data.Night
import com.gnsh.batterystats.data.Sample
import com.gnsh.batterystats.data.Session
import com.gnsh.batterystats.ui.components.EmptyState
import com.gnsh.batterystats.ui.components.LevelChart
import com.gnsh.batterystats.ui.components.SectionTitle
import com.gnsh.batterystats.ui.components.ShapedIcon
import com.gnsh.batterystats.ui.components.StatTile
import com.gnsh.batterystats.ui.components.TileRow

private data class WeekAverages(val onRate: Float?, val offRate: Float?, val deepSleep: Float?)

private fun weekAverages(sessions: List<Session>, now: Long, capacityMah: Int): WeekAverages {
    val week = sessions.filter { !it.charging && it.endTime >= now - 7 * 86_400_000L }
    val offMs = week.sumOf { it.screenOffMs }
    val (on, off) = Drain.average(week, capacityMah)
    return WeekAverages(
        onRate = on,
        offRate = off,
        deepSleep = if (offMs > 30 * 60_000) week.sumOf { it.deepSleepMs }.toFloat() / offMs else null,
    )
}

@Composable
fun UsageScreen(
    padding: PaddingValues, live: Session?, sessions: List<Session>, lastNight: Night?,
    samples: List<Sample>, now: Long, capacityMah: Int,
) {
    val session = live?.takeIf { !it.charging } ?: sessions.firstOrNull { !it.charging }
    val all = remember(sessions, live) { listOfNotNull(live?.takeIf { !it.charging }) + sessions }
    val week = remember(all, now / 60_000, capacityMah) { weekAverages(all, now, capacityMah) }

    ScreenColumn(padding) {
        if (session == null) {
            item {
                EmptyState(
                    Icons.Rounded.HourglassBottom, "No discharge data yet",
                    "Unplug your phone and use it for a while. Screen on and off drain will show up here.",
                )
            }
        } else {
            item { SectionTitle(if (session === live) "Since unplugged" else "Last discharge") }
            item { ActiveIdleTiles(session, capacityMah) }
            item { DeepSleepCard(session) }
        }
        if (lastNight != null && now - lastNight.end < 36 * 3_600_000L) {
            item { SectionTitle("Last night") }
            item { LastNightCard(lastNight) }
        }
        if (week.onRate != null || week.offRate != null) {
            item { SectionTitle("7-day average") }
            item { ProjectionCard(week) }
        }
        item { SectionTitle("Battery level") }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShapedIcon(
                            Icons.Rounded.Timeline, MaterialShapes.Clover4Leaf,
                            MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Last 24 hours", style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (samples.size < 2) {
                        Text(
                            "Collecting data. The chart fills in as the battery level changes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LevelChart(samples, now, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun DeepSleepCard(session: Session) {
    val fraction = session.deepSleepFraction ?: 0f
    val shown by animateFloatAsState(fraction, MaterialTheme.motionScheme.slowSpatialSpec(), label = "sleep")
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Deep sleep", style = MaterialTheme.typography.labelLarge)
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.displaySmallEmphasized,
            )
            Spacer(Modifier.height(10.dp))
            LinearWavyProgressIndicator(
                progress = { shown },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "${Fmt.duration(session.deepSleepMs)} asleep, ${Fmt.duration(session.awakeMs)} awake " +
                    "of ${Fmt.duration(session.screenOffMs)} with the screen off. " +
                    "Higher deep sleep means fewer apps kept the phone awake.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProjectionCard(week: WeekAverages) {
    TileRow {
        week.onRate?.takeIf { it > 0 }?.let {
            StatTile(
                Icons.Rounded.LightMode, MaterialShapes.Pill, "Screen on, full charge",
                Fmt.duration((100f / it * 3_600_000).toLong()), Modifier.weight(1f),
                caption = Fmt.pctRate(it),
            )
        }
        week.offRate?.takeIf { it > 0 }?.let {
            StatTile(
                Icons.Rounded.Bedtime, MaterialShapes.Arch, "Idle, full charge",
                Fmt.duration((100f / it * 3_600_000).toLong()), Modifier.weight(1f),
                caption = Fmt.pctRate(it) + (week.deepSleep?.let { d -> " · ${(d * 100).toInt()}% deep sleep" } ?: ""),
            )
        }
    }
}

@Composable
private fun LastNightCard(n: Night) {
    val time = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val wakes = n.wakeList
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(
                Icons.Rounded.Bedtime, MaterialShapes.Cookie9Sided,
                MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, size = 56.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (n.drain >= 0) "−${n.drain}%" else "+${-n.drain}%",
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                )
                Text(
                    "${time.format(java.util.Date(n.start))}–${time.format(java.util.Date(n.end))} · " +
                        Fmt.pctRate(n.ratePctPerHour.coerceAtLeast(0f)) + if (n.plugged) " · on charger" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (wakes.isEmpty()) "Undisturbed night"
                    else "${wakes.size} wake${if (wakes.size > 1) "s" else ""} · ${Fmt.duration(wakes.sumOf { it.durationMs })} screen on",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
