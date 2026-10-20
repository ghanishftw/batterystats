package com.gnsh.batterystats.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gnsh.batterystats.data.AppSettings
import com.gnsh.batterystats.data.BatterySnapshot
import com.gnsh.batterystats.data.ChargeStatus
import com.gnsh.batterystats.data.Estimates
import com.gnsh.batterystats.data.Fmt
import com.gnsh.batterystats.data.Session
import com.gnsh.batterystats.ui.components.BatteryHero
import com.gnsh.batterystats.ui.components.HeroMood
import com.gnsh.batterystats.ui.components.SectionTitle
import com.gnsh.batterystats.ui.components.StatTile
import com.gnsh.batterystats.ui.components.TileRow

fun statusText(s: BatterySnapshot) = when (s.status) {
    ChargeStatus.Charging -> "Charging"
    ChargeStatus.Full -> "Fully charged"
    ChargeStatus.NotCharging -> if (s.plugged) "Plugged in, not charging" else "Not charging"
    else -> "On battery"
}

@Composable
fun NowScreen(
    padding: PaddingValues,
    snap: BatterySnapshot?,
    live: Session?,
    settings: AppSettings,
    capacityMah: Int,
    onEnableMonitor: () -> Unit,
) {
    if (snap == null) return
    val mood = when {
        snap.charging -> HeroMood.Charging
        !snap.plugged && snap.level <= 20 -> HeroMood.Low
        else -> HeroMood.Discharging
    }
    val eta = Estimates.toFullMs(snap, capacityMah)?.let { "Full in ${Fmt.duration(it)}" }
        ?: Estimates.toEmptyMs(snap, live, capacityMah)?.let { "About ${Fmt.duration(it)} left" }

    ScreenColumn(padding) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BatteryHero(snap.level, mood)
                Spacer(Modifier.height(16.dp))
                AnimatedContent(statusText(snap), label = "status") {
                    Text(it, style = MaterialTheme.typography.headlineSmallEmphasized)
                }
                Text(
                    listOfNotNull(snap.plug.takeIf { snap.plugged }?.label, eta).joinToString(" · ").ifEmpty { " " },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            TileRow {
                StatTile(
                    Icons.Rounded.ElectricMeter, MaterialShapes.Cookie4Sided, "Current",
                    (if (snap.currentMa > 0) "+" else "") + Fmt.ma(snap.currentMa), Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primaryContainer,
                    onAccent = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                StatTile(
                    Icons.Rounded.Bolt, MaterialShapes.Sunny, "Power", Fmt.watts(snap.powerW), Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondaryContainer,
                    onAccent = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        item {
            TileRow {
                StatTile(
                    Icons.Rounded.Thermostat, MaterialShapes.Cookie6Sided, "Temperature",
                    Fmt.temp(snap.tempC, settings.fahrenheit), Modifier.weight(1f),
                )
                StatTile(
                    Icons.Rounded.Speed, MaterialShapes.Pill, "Voltage", Fmt.volts(snap.voltageMv), Modifier.weight(1f),
                )
            }
        }
        item { SessionCard(live, settings.monitorEnabled, onEnableMonitor) }
        if (live != null && !live.charging) item { ActiveIdleTiles(live, capacityMah) }
        item { SectionTitle("Battery") }
        item {
            InfoGroup(
                listOfNotNull(
                    InfoRow(Icons.Rounded.FavoriteBorder, "Reported health", snap.health),
                    snap.technology?.let { InfoRow(Icons.Rounded.Science, "Technology", it) },
                    snap.cycleCount?.let { InfoRow(Icons.Rounded.Autorenew, "Charge cycles", "$it") },
                    InfoRow(Icons.Rounded.Battery5Bar, "Design capacity", Fmt.mah(capacityMah)),
                    snap.chargeCounterMah?.let { InfoRow(Icons.Rounded.Memory, "Charge remaining", Fmt.mah(it)) },
                    InfoRow(Icons.Rounded.Power, "Power source", snap.plug.label),
                ),
            )
        }
    }
}

@Composable
private fun SessionCard(live: Session?, monitorEnabled: Boolean, onEnableMonitor: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (live?.charging == true) colors.primaryContainer else colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (live == null) {
                Text("Session tracking is off", style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    "Background monitoring records charge sessions, screen on/off drain and battery health.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                )
                if (!monitorEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onEnableMonitor) { Text("Turn on monitoring") }
                }
                return@Column
            }
            Text(
                if (live.charging) "This charge" else "Since unplugged",
                style = MaterialTheme.typography.labelLarge,
                color = if (live.charging) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Fmt.signedPct(live.levelChange),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "in ${Fmt.duration(live.durationMs)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Metric(if (live.charging) "Charged" else "Used", Fmt.mah(live.mah))
                Metric("Rate", Fmt.pctRate(live.ratePctPerHour))
                Metric("Avg current", Fmt.ma(live.avgCurrentMa))
            }
            if (live.charging) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Peak ${Fmt.watts(live.peakPowerW)} · started at ${live.startLevel}%",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
fun Metric(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMediumEmphasized)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

