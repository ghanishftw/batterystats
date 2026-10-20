package com.gnsh.batterystats.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.LightMode

/** Shared scrolling body for every tab. */
@Composable
fun ScreenColumn(padding: PaddingValues, content: LazyListScope.() -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

data class InfoRow(val icon: ImageVector, val label: String, val value: String)

/** A connected group of read-only rows using expressive segmented list shapes. */
@Composable
fun InfoGroup(rows: List<InfoRow>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { i, row ->
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = i, count = rows.size), colors = segColors(),
                leadingContent = { Icon(row.icon, contentDescription = null) },
                trailingContent = {
                    Text(row.value, style = MaterialTheme.typography.titleSmallEmphasized)
                },
                content = { Text(row.label) },
            )
        }
    }
}

/** "Oct 19, 4:32 PM" style, with the year only when it isn't this year. */
@Composable
fun shortDateTime(time: Long): String = android.text.format.DateUtils.formatDateTime(
    androidx.compose.ui.platform.LocalContext.current, time,
    android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_ABBREV_MONTH or
        android.text.format.DateUtils.FORMAT_SHOW_TIME,
)

/** Segmented rows sit on a raised container so groups read as cards against the background. */
@Composable
fun segColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

/** Active (screen on) and idle (screen off) drain tiles, measured from mAh like the original app. */
@Composable
fun ActiveIdleTiles(session: com.gnsh.batterystats.data.Session, capacityMah: Int) {
    val d = com.gnsh.batterystats.data.Drain
    val fmt = com.gnsh.batterystats.data.Fmt
    com.gnsh.batterystats.ui.components.TileRow {
        com.gnsh.batterystats.ui.components.StatTile(
            androidx.compose.material.icons.Icons.Rounded.LightMode,
            androidx.compose.material3.MaterialShapes.Sunny, "Active drain",
            d.label(d.active(session, capacityMah)), Modifier.weight(1f),
            caption = "Screen on ${fmt.duration(session.screenOnMs)} · ${fmt.mah(session.screenOnMah)}" +
                if (session.screenOnMs > 0) " · ${fmt.ma(d.avgMa(session.screenOnMah, session.screenOnMs))}" else "",
            accent = MaterialTheme.colorScheme.primaryContainer,
            onAccent = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        com.gnsh.batterystats.ui.components.StatTile(
            androidx.compose.material.icons.Icons.Rounded.Bedtime,
            androidx.compose.material3.MaterialShapes.Cookie9Sided, "Idle drain",
            d.label(d.idle(session, capacityMah)), Modifier.weight(1f),
            caption = "Screen off ${fmt.duration(session.screenOffMs)} · ${fmt.mah(session.screenOffMah)}" +
                if (session.screenOffMs > 0) " · ${fmt.ma(d.avgMa(session.screenOffMah, session.screenOffMs))}" else "",
        )
    }
}
