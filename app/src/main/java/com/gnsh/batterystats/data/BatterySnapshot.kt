package com.gnsh.batterystats.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ChargeStatus { Charging, Discharging, Full, NotCharging, Unknown }
enum class PlugType(val label: String) { None("Unplugged"), AC("AC charger"), USB("USB"), Wireless("Wireless"), Dock("Dock") }

/** One reading of the battery. Current is positive when flowing into the battery. */
data class BatterySnapshot(
    val timeMs: Long,
    val level: Int,
    val status: ChargeStatus,
    val plug: PlugType,
    val currentMa: Int,
    val voltageMv: Int,
    val tempC: Float,
    val health: String,
    val technology: String?,
    val cycleCount: Int?,
    val chargeCounterMah: Int?,
) {
    val plugged get() = plug != PlugType.None
    val charging get() = status == ChargeStatus.Charging
    val powerW get() = currentMa * voltageMv / 1_000_000f
}

object BatteryReader {
    private val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    fun read(context: Context): BatterySnapshot? {
        val intent = ContextCompat.registerReceiver(context, null, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            ?: return null
        val bm = context.getSystemService(BatteryManager::class.java)
        return parse(intent, bm)
    }

    fun parse(intent: Intent, bm: BatteryManager): BatterySnapshot {
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = if (rawLevel >= 0) (rawLevel * 100f / scale).roundToInt() else
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.Charging
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeStatus.Discharging
            BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.Full
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NotCharging
            else -> ChargeStatus.Unknown
        }
        val plug = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.Wireless
            BatteryManager.BATTERY_PLUGGED_DOCK -> PlugType.Dock
            else -> PlugType.None
        }
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }
        val magnitude = abs(normalizeCurrentMa(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)))
        // Vendors disagree on the sign, so derive direction from the charge status.
        val current = if (status == ChargeStatus.Charging) magnitude else -magnitude
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeIf { it > 0 && it != Int.MIN_VALUE }?.let { if (it > 100_000) it / 1000 else it }

        return BatterySnapshot(
            timeMs = System.currentTimeMillis(),
            level = level.coerceIn(0, 100),
            status = status,
            plug = plug,
            currentMa = current,
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).let { if (it in 1..10) it * 1000 else it },
            tempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            health = health,
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
            cycleCount = intent.getIntExtra("android.os.extra.CYCLE_COUNT", -1).takeIf { it >= 0 },
            chargeCounterMah = counter,
        )
    }

    /** CURRENT_NOW is documented as µA, but some devices report mA. */
    fun normalizeCurrentMa(raw: Int): Int = when {
        raw == Int.MIN_VALUE || raw == Int.MAX_VALUE -> 0
        abs(raw) >= 20_000 -> raw / 1000
        else -> raw
    }

    /** Design capacity from the framework power profile, if the device exposes it. */
    @SuppressLint("PrivateApi")
    fun designCapacityMah(context: Context): Int? = runCatching {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val profile = cls.getConstructor(Context::class.java).newInstance(context)
        (cls.getMethod("getBatteryCapacity").invoke(profile) as Double).roundToInt()
    }.getOrNull()?.takeIf { it in 500..30_000 }
}
