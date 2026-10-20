package com.gnsh.batterystats.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the status bar icon shows. The notification title leaves that value out. */
enum class IconType(val label: String) {
    Level("Battery level"), Current("Current (mA)"), Temperature("Temperature"), Power("Power (W)"), AppIcon("App icon")
}

data class AppSettings(
    val monitorEnabled: Boolean = true,
    val chargeAlarm: Boolean = true,
    val chargeAlarmLevel: Int = 80,
    val lowAlarm: Boolean = false,
    val lowAlarmLevel: Int = 20,
    /** 0 means use the detected design capacity. */
    val designCapacityOverride: Int = 0,
    val fahrenheit: Boolean = false,
    val dynamicColor: Boolean = true,
    val overnightReport: Boolean = true,
    /** Night window in minutes after midnight. */
    val nightStartMin: Int = 23 * 60,
    val nightEndMin: Int = 7 * 60,
    val iconType: IconType = IconType.Level,
    val notifActiveIdle: Boolean = true,
    val notifScreen: Boolean = true,
    val notifSleep: Boolean = true,
)

private val Context.store by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val monitor = booleanPreferencesKey("monitor")
        val chargeAlarm = booleanPreferencesKey("charge_alarm")
        val chargeAlarmLevel = intPreferencesKey("charge_alarm_level")
        val lowAlarm = booleanPreferencesKey("low_alarm")
        val lowAlarmLevel = intPreferencesKey("low_alarm_level")
        val capacity = intPreferencesKey("design_capacity")
        val fahrenheit = booleanPreferencesKey("fahrenheit")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val overnight = booleanPreferencesKey("overnight_report")
        val nightStart = intPreferencesKey("night_start")
        val nightEnd = intPreferencesKey("night_end")
        val iconType = intPreferencesKey("icon_type")
        val notifActiveIdle = booleanPreferencesKey("notif_active_idle")
        val notifScreen = booleanPreferencesKey("notif_screen")
        val notifSleep = booleanPreferencesKey("notif_sleep")
    }

    val settings: Flow<AppSettings> = context.store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            monitorEnabled = p[K.monitor] ?: d.monitorEnabled,
            chargeAlarm = p[K.chargeAlarm] ?: d.chargeAlarm,
            chargeAlarmLevel = p[K.chargeAlarmLevel] ?: d.chargeAlarmLevel,
            lowAlarm = p[K.lowAlarm] ?: d.lowAlarm,
            lowAlarmLevel = p[K.lowAlarmLevel] ?: d.lowAlarmLevel,
            designCapacityOverride = p[K.capacity] ?: d.designCapacityOverride,
            fahrenheit = p[K.fahrenheit] ?: d.fahrenheit,
            dynamicColor = p[K.dynamicColor] ?: d.dynamicColor,
            overnightReport = p[K.overnight] ?: d.overnightReport,
            nightStartMin = p[K.nightStart] ?: d.nightStartMin,
            nightEndMin = p[K.nightEnd] ?: d.nightEndMin,
            iconType = p[K.iconType]?.let { IconType.entries.getOrNull(it) } ?: d.iconType,
            notifActiveIdle = p[K.notifActiveIdle] ?: d.notifActiveIdle,
            notifScreen = p[K.notifScreen] ?: d.notifScreen,
            notifSleep = p[K.notifSleep] ?: d.notifSleep,
        )
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.store.edit { it[key] = value }
    }

    suspend fun setMonitor(v: Boolean) = set(K.monitor, v)
    suspend fun setChargeAlarm(v: Boolean) = set(K.chargeAlarm, v)
    suspend fun setChargeAlarmLevel(v: Int) = set(K.chargeAlarmLevel, v)
    suspend fun setLowAlarm(v: Boolean) = set(K.lowAlarm, v)
    suspend fun setLowAlarmLevel(v: Int) = set(K.lowAlarmLevel, v)
    suspend fun setDesignCapacity(v: Int) = set(K.capacity, v)
    suspend fun setFahrenheit(v: Boolean) = set(K.fahrenheit, v)
    suspend fun setDynamicColor(v: Boolean) = set(K.dynamicColor, v)
    suspend fun setIconType(v: IconType) = set(K.iconType, v.ordinal)
    suspend fun setNotifActiveIdle(v: Boolean) = set(K.notifActiveIdle, v)
    suspend fun setNotifScreen(v: Boolean) = set(K.notifScreen, v)
    suspend fun setNotifSleep(v: Boolean) = set(K.notifSleep, v)
    suspend fun setOvernightReport(v: Boolean) = set(K.overnight, v)
    suspend fun setNightWindow(startMin: Int, endMin: Int) = context.store.edit {
        it[K.nightStart] = startMin
        it[K.nightEnd] = endMin
    }
}
