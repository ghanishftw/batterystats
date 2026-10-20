package com.gnsh.batterystats.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gnsh.batterystats.BatteryStatsApp
import com.gnsh.batterystats.data.AppSettings
import com.gnsh.batterystats.data.BatteryReader
import com.gnsh.batterystats.data.BatterySnapshot
import com.gnsh.batterystats.data.HealthEstimate
import com.gnsh.batterystats.data.HealthEstimator
import com.gnsh.batterystats.data.Night
import com.gnsh.batterystats.data.Sample
import com.gnsh.batterystats.data.Session
import com.gnsh.batterystats.service.MonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BatteryStatsApp
    private val dao = app.db.dao()
    private val repo = app.settings
    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val settings: StateFlow<AppSettings> = repo.settings.state(AppSettings())

    val snapshot: StateFlow<BatterySnapshot?> = flow {
        while (true) {
            emit(BatteryReader.read(app))
            delay(1_500)
        }
    }.flowOn(Dispatchers.IO).state(BatteryReader.read(app))

    val live: StateFlow<Session?> = app.liveSession
    val sessions: StateFlow<List<Session>> = dao.sessions().state(emptyList())
    val nights: StateFlow<List<Night>> = dao.nights().state(emptyList())
    val samples: StateFlow<List<Sample>> =
        dao.samplesSince(System.currentTimeMillis() - 48 * 3_600_000L).state(emptyList())

    val detectedCapacity: Int? get() = app.detectedCapacityMah

    val health: StateFlow<HealthEstimate?> = combine(sessions, snapshot, settings) { list, snap, s ->
        val design = s.designCapacityOverride.takeIf { it > 0 } ?: app.detectedCapacityMah
        HealthEstimator.estimate(list, design, snap)
    }.state(null)

    fun capacityMah(s: AppSettings) = app.capacityMah(s.designCapacityOverride)

    private fun edit(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    fun setMonitor(on: Boolean) = edit {
        repo.setMonitor(on)
        if (on) MonitorService.start(app) else MonitorService.stop(app)
    }
    fun setChargeAlarm(v: Boolean) = edit { repo.setChargeAlarm(v) }
    fun setChargeAlarmLevel(v: Int) = edit { repo.setChargeAlarmLevel(v) }
    fun setLowAlarm(v: Boolean) = edit { repo.setLowAlarm(v) }
    fun setLowAlarmLevel(v: Int) = edit { repo.setLowAlarmLevel(v) }
    fun setDesignCapacity(v: Int) = edit { repo.setDesignCapacity(v) }
    fun setFahrenheit(v: Boolean) = edit { repo.setFahrenheit(v) }
    fun setDynamicColor(v: Boolean) = edit { repo.setDynamicColor(v) }
    fun setIconType(v: com.gnsh.batterystats.data.IconType) = edit { repo.setIconType(v) }
    fun setNotifActiveIdle(v: Boolean) = edit { repo.setNotifActiveIdle(v) }
    fun setNotifScreen(v: Boolean) = edit { repo.setNotifScreen(v) }
    fun setNotifSleep(v: Boolean) = edit { repo.setNotifSleep(v) }
    fun setOvernightReport(v: Boolean) = edit { repo.setOvernightReport(v) }
    fun setNightWindow(startMin: Int, endMin: Int) = edit { repo.setNightWindow(startMin, endMin) }
    fun clearHistory() = edit {
        dao.clearSessions()
        dao.clearSamples()
        dao.clearNights()
        dao.clearScreenEvents()
    }
}
