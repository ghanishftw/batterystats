package com.gnsh.batterystats

import android.app.Application
import android.content.Context
import com.gnsh.batterystats.data.AppDatabase
import com.gnsh.batterystats.data.BatteryReader
import com.gnsh.batterystats.data.Session
import com.gnsh.batterystats.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

class BatteryStatsApp : Application() {
    val db by lazy { AppDatabase.create(this) }
    val settings by lazy { SettingsRepository(this) }
    val detectedCapacityMah by lazy { BatteryReader.designCapacityMah(this) }

    /** The session the monitor service is recording right now. */
    val liveSession = MutableStateFlow<Session?>(null)

    fun capacityMah(override: Int): Int = override.takeIf { it > 0 } ?: detectedCapacityMah ?: 4000
}

val Context.app get() = applicationContext as BatteryStatsApp
