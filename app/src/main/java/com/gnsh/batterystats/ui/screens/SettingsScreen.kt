package com.gnsh.batterystats.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gnsh.batterystats.data.AppSettings
import com.gnsh.batterystats.data.Fmt
import com.gnsh.batterystats.data.IconType
import com.gnsh.batterystats.ui.MainViewModel
import com.gnsh.batterystats.ui.components.SectionTitle
import kotlin.math.roundToInt

/** One settings row inside a segmented group. */
private enum class TimeTarget { Start, End }

private class Row_(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val onClick: (() -> Unit)? = null,
    val trailing: (@Composable () -> Unit)? = null,
    val below: (@Composable () -> Unit)? = null,
)

@Composable
private fun SettingsGroup(rows: List<Row_>) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { i, r ->
            val shapes = ListItemDefaults.segmentedShapes(i, rows.size)
            val supporting: (@Composable () -> Unit)? = if (r.subtitle != null || r.below != null) {
                {
                    Column {
                        r.subtitle?.let { Text(it) }
                        r.below?.invoke()
                    }
                }
            } else null
            if (r.onClick != null) {
                SegmentedListItem(
                    onClick = r.onClick, shapes = shapes, colors = segColors(),
                    leadingContent = { Icon(r.icon, null) },
                    trailingContent = r.trailing, supportingContent = supporting,
                    content = { Text(r.title) },
                )
            } else {
                SegmentedListItem(
                    shapes = shapes, colors = segColors(),
                    leadingContent = { Icon(r.icon, null) },
                    trailingContent = r.trailing, supportingContent = supporting,
                    content = { Text(r.title) },
                )
            }
        }
    }
}

@Composable
private fun ExpressiveSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        thumbContent = if (checked) {
            { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
        } else null,
    )
}

@Composable
private fun LevelSlider(value: Int, range: IntRange, enabled: Boolean, onDone: (Int) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Slider(
            value = v,
            onValueChange = { v = it },
            onValueChangeFinished = { onDone(v.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / 5 - 1,
            enabled = enabled,
        )
        Text("${v.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SettingsScreen(padding: PaddingValues, vm: MainViewModel, settings: AppSettings) {
    val context = LocalContext.current
    var capacityDialog by rememberSaveable { mutableStateOf(false) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }
    var timeDialog by rememberSaveable { mutableStateOf<TimeTarget?>(null) }
    var iconDialog by rememberSaveable { mutableStateOf(false) }
    val detected = vm.detectedCapacity

    ScreenColumn(padding) {
        item { SectionTitle("Monitoring") }
        item {
            SettingsGroup(
                listOf(
                    Row_(
                        Icons.Rounded.MonitorHeart, "Background monitoring",
                        "Live notification, sessions, health and alarms",
                        onClick = { vm.setMonitor(!settings.monitorEnabled) },
                        trailing = { ExpressiveSwitch(settings.monitorEnabled, vm::setMonitor) },
                    ),
                    Row_(
                        Icons.Rounded.Tune, "Battery optimization",
                        "Exclude the app so Android doesn't stop monitoring",
                        onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                    ),
                ),
            )
        }
        item { SectionTitle("Notification") }
        item {
            SettingsGroup(
                listOf(
                    Row_(
                        Icons.Rounded.Pin, "Status bar icon", settings.iconType.label,
                        onClick = { iconDialog = true },
                    ),
                    Row_(
                        Icons.Rounded.Speed, "Show Active/Idle stats", "Drain per hour with the screen on and off",
                        onClick = { vm.setNotifActiveIdle(!settings.notifActiveIdle) },
                        trailing = { ExpressiveSwitch(settings.notifActiveIdle, vm::setNotifActiveIdle) },
                    ),
                    Row_(
                        Icons.Rounded.Smartphone, "Show Screen on/off stats", "Time, % and mAh for each screen state",
                        onClick = { vm.setNotifScreen(!settings.notifScreen) },
                        trailing = { ExpressiveSwitch(settings.notifScreen, vm::setNotifScreen) },
                    ),
                    Row_(
                        Icons.Rounded.Bedtime, "Show Awake/Deep sleep stats", "How long the phone slept with the screen off",
                        onClick = { vm.setNotifSleep(!settings.notifSleep) },
                        trailing = { ExpressiveSwitch(settings.notifSleep, vm::setNotifSleep) },
                    ),
                ),
            )
        }
        item { SectionTitle("Alarms") }
        item {
            SettingsGroup(
                listOf(
                    Row_(
                        Icons.Rounded.BatteryChargingFull, "Charge limit alarm",
                        "Notify when charging reaches the limit",
                        onClick = { vm.setChargeAlarm(!settings.chargeAlarm) },
                        trailing = { ExpressiveSwitch(settings.chargeAlarm, vm::setChargeAlarm) },
                        below = { LevelSlider(settings.chargeAlarmLevel, 50..100, settings.chargeAlarm, vm::setChargeAlarmLevel) },
                    ),
                    Row_(
                        Icons.Rounded.BatteryAlert, "Low battery alarm",
                        "Notify when the level drops to",
                        onClick = { vm.setLowAlarm(!settings.lowAlarm) },
                        trailing = { ExpressiveSwitch(settings.lowAlarm, vm::setLowAlarm) },
                        below = { LevelSlider(settings.lowAlarmLevel, 5..50, settings.lowAlarm, vm::setLowAlarmLevel) },
                    ),
                ),
            )
        }
        item { SectionTitle("Overnight") }
        item {
            SettingsGroup(
                listOf(
                    Row_(
                        Icons.Rounded.Bedtime, "Morning report",
                        "Notify how much drained overnight when you first use the phone",
                        onClick = { vm.setOvernightReport(!settings.overnightReport) },
                        trailing = { ExpressiveSwitch(settings.overnightReport, vm::setOvernightReport) },
                    ),
                    Row_(
                        Icons.Rounded.NightsStay, "Night starts", Fmt.clock(settings.nightStartMin),
                        onClick = { timeDialog = TimeTarget.Start },
                    ),
                    Row_(
                        Icons.Rounded.WbSunny, "Night ends", Fmt.clock(settings.nightEndMin),
                        onClick = { timeDialog = TimeTarget.End },
                    ),
                ),
            )
        }
        item { SectionTitle("Battery") }
        item {
            SettingsGroup(
                listOf(
                    Row_(
                        Icons.Rounded.Straighten, "Design capacity",
                        when {
                            settings.designCapacityOverride > 0 -> "${Fmt.mah(settings.designCapacityOverride)} (set manually)"
                            detected != null -> "${Fmt.mah(detected)} (detected)"
                            else -> "Not detected. Tap to set it."
                        },
                        onClick = { capacityDialog = true },
                    ),
                ),
            )
        }
        item { SectionTitle("Appearance") }
        item {
            SettingsGroup(
                listOf(
                    Row_(
                        Icons.Rounded.Palette, "Dynamic color", "Use colors from your wallpaper",
                        onClick = { vm.setDynamicColor(!settings.dynamicColor) },
                        trailing = { ExpressiveSwitch(settings.dynamicColor, vm::setDynamicColor) },
                    ),
                    Row_(
                        Icons.Rounded.Thermostat, "Temperature unit",
                        below = {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                            ) {
                                ToggleButton(
                                    checked = !settings.fahrenheit, onCheckedChange = { vm.setFahrenheit(false) },
                                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                    modifier = Modifier.weight(1f),
                                ) { Text("Celsius") }
                                ToggleButton(
                                    checked = settings.fahrenheit, onCheckedChange = { vm.setFahrenheit(true) },
                                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                    modifier = Modifier.weight(1f),
                                ) { Text("Fahrenheit") }
                            }
                        },
                    ),
                ),
            )
        }
        item { SectionTitle("Data") }
        item {
            SettingsGroup(
                listOf(
                    Row_(
                        Icons.Rounded.DeleteSweep, "Clear history",
                        "Delete saved sessions, nights and chart data",
                        onClick = { clearDialog = true },
                    ),
                ),
            )
        }
    }

    if (capacityDialog) {
        var text by remember { mutableStateOf(settings.designCapacityOverride.takeIf { it > 0 }?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { capacityDialog = false },
            title = { Text("Design capacity") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your battery's rated capacity in mAh. Leave empty to use the detected value.")
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter(Char::isDigit).take(5) },
                        label = { Text("mAh") },
                        placeholder = { Text(detected?.toString() ?: "e.g. 5000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setDesignCapacity(text.toIntOrNull()?.takeIf { it in 500..30_000 } ?: 0)
                    capacityDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { capacityDialog = false }) { Text("Cancel") } },
        )
    }
    if (iconDialog) {
        AlertDialog(
            onDismissRequest = { iconDialog = false },
            title = { Text("Status bar icon") },
            text = {
                Column(Modifier.selectableGroup()) {
                    IconType.entries.forEach { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = settings.iconType == t, role = Role.RadioButton,
                                    onClick = { vm.setIconType(t); iconDialog = false },
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.iconType == t, onClick = null)
                            Text(t.label, Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { iconDialog = false }) { Text("Close") } },
        )
    }
    timeDialog?.let { target ->
        val initial = if (target == TimeTarget.Start) settings.nightStartMin else settings.nightEndMin
        val state = rememberTimePickerState(initial / 60, initial % 60)
        AlertDialog(
            onDismissRequest = { timeDialog = null },
            title = { Text(if (target == TimeTarget.Start) "Night starts" else "Night ends") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    if (target == TimeTarget.Start) vm.setNightWindow(m, settings.nightEndMin)
                    else vm.setNightWindow(settings.nightStartMin, m)
                    timeDialog = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { timeDialog = null }) { Text("Cancel") } },
        )
    }
    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text("Clear history?") },
            text = { Text("Saved sessions, nights, chart data and the health estimate will be reset. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); clearDialog = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } },
        )
    }
}
