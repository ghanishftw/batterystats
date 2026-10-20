package com.gnsh.batterystats.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gnsh.batterystats.data.AppSettings
import com.gnsh.batterystats.data.Drain
import com.gnsh.batterystats.data.Fmt
import com.gnsh.batterystats.data.HealthEstimator
import com.gnsh.batterystats.data.Night
import com.gnsh.batterystats.data.Session
import com.gnsh.batterystats.ui.components.EmptyState
import com.gnsh.batterystats.ui.components.ShapedIcon
import java.text.DateFormat
import java.util.Date

private enum class Filter(val label: String) { All("All"), Charge("Charge"), Discharge("Drain"), Night("Overnight") }

/** A row in the history list: either a session or a night. */
private sealed interface Entry {
    val key: String
    val sortTime: Long
    data class S(val s: Session) : Entry { override val key = "s${s.id}"; override val sortTime = s.endTime }
    data class N(val n: Night) : Entry { override val key = "n${n.end}"; override val sortTime = n.end }
}

@Composable
fun HistoryScreen(padding: PaddingValues, sessions: List<Session>, nights: List<Night>, settings: AppSettings, capacityMah: Int) {
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = remember(sessions, nights, filter) {
        val s = sessions.map { Entry.S(it) }
        val n = nights.map { Entry.N(it) }
        when (filter) {
            Filter.All -> (s + n).sortedByDescending { it.sortTime }
            Filter.Charge -> s.filter { it.s.charging }
            Filter.Discharge -> s.filter { !it.s.charging }
            Filter.Night -> n
        }
    }
    val date = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    ScreenColumn(padding) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                Filter.entries.forEachIndexed { i, f ->
                    ToggleButton(
                        checked = filter == f,
                        onCheckedChange = { filter = f },
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            Filter.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) { Text(f.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                if (filter == Filter.Night) EmptyState(
                    Icons.Rounded.Bedtime, "No nights yet",
                    "Each morning, the drain between ${Fmt.clock(settings.nightStartMin)} and " +
                        "${Fmt.clock(settings.nightEndMin)} is saved here.",
                ) else EmptyState(
                    Icons.Rounded.History, "No sessions yet",
                    "Each time you plug in or unplug, the finished session is saved here.",
                )
            }
        }
        itemsIndexed(shown, key = { _, e -> e.key }) { i, e ->
            val open = expanded == e.key
            val toggle = { expanded = if (open) null else e.key }
            val shapes = ListItemDefaults.segmentedShapes(i, shown.size)
            when (e) {
                is Entry.S -> SessionItem(e.s, open, toggle, shapes, Modifier.animateItem(), settings, capacityMah)
                is Entry.N -> NightItem(e.n, open, toggle, shapes, Modifier.animateItem(), date, time)
            }
        }
    }
}

@Composable
private fun SessionItem(
    s: Session, open: Boolean, onClick: () -> Unit, shapes: androidx.compose.material3.ListItemShapes,
    modifier: Modifier, settings: AppSettings, capacityMah: Int,
) {
    SegmentedListItem(
        onClick = onClick, shapes = shapes, colors = segColors(), modifier = modifier,
        leadingContent = {
            if (s.charging) ShapedIcon(
                Icons.Rounded.BatteryChargingFull, MaterialShapes.SoftBurst,
                MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, size = 40.dp,
            ) else ShapedIcon(
                Icons.Rounded.BatteryStd, MaterialShapes.Cookie6Sided,
                MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, size = 40.dp,
            )
        },
        trailingContent = { Text(Fmt.pctRate(s.ratePctPerHour), style = MaterialTheme.typography.labelLarge) },
        supportingContent = {
            Column {
                Text("${shortDateTime(s.startTime)} · ${s.startLevel}% → ${s.endLevel}%")
                AnimatedVisibility(open) { SessionDetails(s, settings, capacityMah) }
            }
        },
        content = {
            Text(
                "${Fmt.signedPct(s.levelChange)} in ${Fmt.duration(s.durationMs)}",
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
    )
}

@Composable
private fun NightItem(
    n: Night, open: Boolean, onClick: () -> Unit, shapes: androidx.compose.material3.ListItemShapes,
    modifier: Modifier, date: DateFormat, time: DateFormat,
) {
    val wakes = n.wakeList
    SegmentedListItem(
        onClick = onClick, shapes = shapes, colors = segColors(), modifier = modifier,
        leadingContent = {
            ShapedIcon(
                Icons.Rounded.Bedtime, MaterialShapes.Cookie9Sided,
                MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, size = 40.dp,
            )
        },
        trailingContent = {
            Text(
                if (wakes.isEmpty()) "No wakes" else "${wakes.size} wake${if (wakes.size > 1) "s" else ""}",
                style = MaterialTheme.typography.labelLarge,
            )
        },
        supportingContent = {
            Column {
                Text(
                    "${date.format(Date(n.end))} · ${time.format(Date(n.start))}–${time.format(Date(n.end))} · " +
                        Fmt.pctRate(n.ratePctPerHour.coerceAtLeast(0f)),
                )
                AnimatedVisibility(open) {
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (n.plugged) Text("Includes time on the charger", style = MaterialTheme.typography.bodySmall)
                        if (wakes.isEmpty()) {
                            Text("No screen activity. Undisturbed night.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("Wakes during the night", style = MaterialTheme.typography.labelLarge)
                            wakes.forEach { w ->
                                Text(
                                    "${time.format(Date(w.time))} · ${Fmt.duration(w.durationMs)}" +
                                        if (w.drain > 0) " · −${w.drain}%" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        },
        content = {
            Text(
                if (n.drain >= 0) "−${n.drain}% overnight" else "+${-n.drain}% overnight",
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
    )
}

@Composable
private fun SessionDetails(s: Session, settings: AppSettings, capacityMah: Int) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Metric(if (s.charging) "Charged" else "Used", Fmt.mah(s.mah))
            Metric("Avg current", Fmt.ma(s.avgCurrentMa))
            Metric("Max temp", Fmt.temp(s.maxTempC, settings.fahrenheit))
        }
        if (s.charging) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric("Peak power", Fmt.watts(s.peakPowerW))
                Metric("Charger", s.plugType)
            }
            Text(
                HealthEstimator.capacityFrom(s)?.let { "Used for health: ${Fmt.mah(it)} capacity" }
                    ?: "Not used for health: ${HealthEstimator.rejectReason(s)?.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric("Active", Drain.label(Drain.active(s, capacityMah)))
                Metric("Idle", Drain.label(Drain.idle(s, capacityMah)))
                Metric("Deep sleep", s.deepSleepFraction?.let { "${(it * 100).toInt()}%" } ?: "—")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric("Screen on time", Fmt.duration(s.screenOnMs))
                Metric("Screen off time", Fmt.duration(s.screenOffMs))
                Metric("Awake", Fmt.duration(s.awakeMs))
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
