package com.gnsh.batterystats.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gnsh.batterystats.data.BatterySnapshot
import com.gnsh.batterystats.data.Fmt
import com.gnsh.batterystats.data.HealthEstimate
import com.gnsh.batterystats.data.HealthEstimator
import com.gnsh.batterystats.data.HealthState
import com.gnsh.batterystats.ui.components.SectionTitle
import com.gnsh.batterystats.ui.components.StatTile
import com.gnsh.batterystats.ui.components.TileRow

private fun stateLabel(state: HealthState, count: Int) = when (state) {
    HealthState.NotYet -> "Not enough data yet"
    HealthState.Learning -> "Learning · $count of ${HealthEstimator.SESSIONS_NEEDED} sessions"
    HealthState.Estimated -> "Estimated from $count charge sessions"
    HealthState.Approximate -> "Approximate, from the charge counter"
}

private fun verdict(pct: Float) = when {
    pct >= 90 -> "Excellent"
    pct >= 80 -> "Good"
    pct >= 70 -> "Fair"
    else -> "Worn"
}

@Composable
fun HealthScreen(padding: PaddingValues, health: HealthEstimate?, snap: BatterySnapshot?) {
    if (health == null) return
    val pct = health.healthPct
    ScreenColumn(padding) {
        item { HealthHero(health) }
        if (health.designLooksWrong) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Design capacity may be wrong", style = MaterialTheme.typography.titleSmallEmphasized)
                        Text(
                            "Your battery measures ${health.capacityMah?.let(Fmt::mah)}, well above the " +
                                "${health.designMah?.let(Fmt::mah)} your phone reports as its design capacity. " +
                                "Phones often report a placeholder here. Set your battery's rated capacity in " +
                                "Settings for an accurate health reading.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            TileRow {
                StatTile(
                    Icons.Rounded.BatteryChargingFull, MaterialShapes.Cookie7Sided, "Estimated capacity",
                    health.capacityMah?.let(Fmt::mah) ?: "—", Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primaryContainer,
                    onAccent = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                StatTile(
                    Icons.Rounded.Straighten, MaterialShapes.Square, "Design capacity",
                    health.designMah?.let(Fmt::mah) ?: "Unknown", Modifier.weight(1f),
                )
            }
        }
        item {
            TileRow {
                StatTile(
                    Icons.Rounded.Autorenew, MaterialShapes.Clover8Leaf, "Cycles (tracked)",
                    "%.1f".format(health.estimatedCycles), Modifier.weight(1f),
                    caption = "Since you installed the app",
                )
                StatTile(
                    Icons.Rounded.Battery5Bar, MaterialShapes.Gem, "Cycles (system)",
                    snap?.cycleCount?.toString() ?: "—", Modifier.weight(1f),
                    caption = if (snap?.cycleCount == null) "Not reported by device" else "Reported by Android",
                )
            }
        }
        if (health.qualifying.isNotEmpty()) {
            item { SectionTitle("Sessions used") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    health.qualifying.forEachIndexed { i, (s, cap) ->
                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(i, health.qualifying.size), colors = segColors(),
                            supportingContent = {
                                Text("${s.startLevel}% → ${s.endLevel}% · ${Fmt.mah(s.mah)} charged")
                            },
                            trailingContent = { Text(Fmt.mah(cap), style = MaterialTheme.typography.titleSmallEmphasized) },
                            content = { Text(shortDateTime(s.startTime)) },
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Health is measured by counting the charge that flows in during a session " +
                    "(at least ${HealthEstimator.MIN_LEVEL_DELTA}% added) and scaling it to 0–100%. " +
                    "The median of recent sessions is compared with the design capacity." +
                    if (pct == null && health.designMah == null) " Set the design capacity in Settings to see a percentage." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun HealthHero(health: HealthEstimate) {
    val colors = MaterialTheme.colorScheme
    val pct = health.healthPct
    val shown by animateFloatAsState(((pct ?: 0f) / 100f).coerceIn(0f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "health")
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (pct == null || health.state == HealthState.NotYet) {
                ContainedLoadingIndicator(Modifier.size(96.dp))
                Spacer(Modifier.height(16.dp))
                Text("Measuring your battery", style = MaterialTheme.typography.titleLargeEmphasized)
                Text(
                    "Charge your phone by ${HealthEstimator.MIN_LEVEL_DELTA}% or more a few times with monitoring on, " +
                        "and the estimate will appear here.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                return@Column
            }
            Box(
                Modifier.size(176.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(colors.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${pct.toInt()}%", style = MaterialTheme.typography.displayMediumEmphasized, color = colors.onTertiaryContainer)
                    Text(verdict(pct), style = MaterialTheme.typography.titleSmallEmphasized, color = colors.onTertiaryContainer)
                }
            }
            Spacer(Modifier.height(20.dp))
            LinearWavyProgressIndicator(
                progress = { shown },
                modifier = Modifier.fillMaxWidth(),
                color = colors.tertiary,
                trackColor = colors.tertiary.copy(alpha = 0.18f),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (health.state == HealthState.Learning) {
                    LoadingIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stateLabel(health.state, health.qualifying.size),
                    style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

