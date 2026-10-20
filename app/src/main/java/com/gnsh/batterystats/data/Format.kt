package com.gnsh.batterystats.data

import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Fmt {
    private val int = NumberFormat.getIntegerInstance()

    fun mah(v: Number) = "${int.format(v.toFloat().roundToInt())} mAh"
    fun ma(v: Number) = "${int.format(v.toFloat().roundToInt()).replace('-', '−')} mA"
    fun watts(v: Float) = "%.1f W".format(abs(v))
    fun volts(mv: Int) = "%.2f V".format(mv / 1000f)
    fun signedPct(n: Int) = when {
        n > 0 -> "+$n%"
        n < 0 -> "−${-n}%"
        else -> "0%"
    }

    fun pctRate(v: Float) = "%.1f %%/h".format(v)
    fun temp(c: Float, fahrenheit: Boolean) =
        if (fahrenheit) "%.1f °F".format(c * 9 / 5 + 32) else "%.1f °C".format(c)

    /** Minutes after midnight as a localized short time, e.g. 23:00 or 11:00 PM. */
    fun clock(minutes: Int): String = java.time.LocalTime.of(minutes / 60, minutes % 60)
        .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

    fun duration(ms: Long): String {
        val totalMin = (ms / 60_000.0).roundToLong().coerceAtLeast(0)
        val d = totalMin / 1440
        val h = (totalMin % 1440) / 60
        val m = totalMin % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }
}

object Estimates {
    /** Time until full, from the live current. Slows a bit above 80 % for the taper. */
    fun toFullMs(s: BatterySnapshot, capacityMah: Int): Long? {
        if (!s.charging || s.currentMa < 50 || s.level >= 100) return null
        val hours = (100 - s.level) / 100f * capacityMah / s.currentMa * if (s.level >= 80) 1.6f else 1.1f
        return (hours * 3_600_000).toLong()
    }

    /** Time until empty, preferring the session's average rate over the noisy live current. */
    fun toEmptyMs(s: BatterySnapshot, session: Session?, capacityMah: Int): Long? {
        if (s.plugged) return null
        val rate = session?.takeIf { !it.charging && it.durationMs > 15 * 60_000 && it.levelDelta >= 2 }?.ratePctPerHour
        val hours = when {
            rate != null && rate > 0 -> s.level / rate
            abs(s.currentMa) > 10 -> s.level / 100f * capacityMah / abs(s.currentMa)
            else -> return null
        }
        return (hours * 3_600_000).toLong()
    }
}
