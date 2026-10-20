package com.gnsh.batterystats.data

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Text for the live notification, laid out like the original app:
 *  - subtext: time since the session started
 *  - title:   "Now: 450 mA (1.8 W) • 31.2 °C • 9h 30m left" (minus whatever the status icon shows)
 *  - text:    the first body line, usually "Active: 6.2%/h • Idle: 0.8%/h"
 *  - body:    Active/Idle, Screen On/Off with % and mAh, Deep sleep/Awake
 * Kept free of Android APIs so it can be unit-tested.
 */
object NotificationText {
    data class Content(val title: String, val text: String, val body: String, val subText: String?)

    private const val SEP = " • "

    fun build(
        s: BatterySnapshot,
        session: Session?,
        capacityMah: Int,
        settings: AppSettings,
    ): Content {
        val temp = Fmt.temp(s.tempC, settings.fahrenheit)
        val now = "Now: ${Fmt.ma(s.currentMa)}"
        val watts = Fmt.watts(s.powerW)
        val level = "${s.level}%"
        val head = when (settings.iconType) {
            IconType.Level -> "$now ($watts)$SEP$temp"
            IconType.Current -> "$level$SEP$watts$SEP$temp"
            IconType.Temperature -> "$now ($watts)$SEP$level"
            IconType.Power -> "$level$SEP${Fmt.ma(s.currentMa)}$SEP$temp"
            IconType.AppIcon -> "$now $watts$SEP$level$SEP$temp"
        }
        val left = Estimates.toFullMs(s, capacityMah) ?: Estimates.toEmptyMs(s, session, capacityMah)
        val title = head + (left?.let { "$SEP${Fmt.duration(it)} left" } ?: "")

        val body = body(s, session, capacityMah, settings)
        val text = body.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        val subText = session?.let { Fmt.duration(it.screenOnMs + it.screenOffMs) }
        return Content(title, text, body, subText)
    }

    private fun body(s: BatterySnapshot, session: Session?, capacityMah: Int, settings: AppSettings): String {
        val lines = mutableListOf<String>()
        if (session != null) {
            val charging = session.charging
            if (settings.notifActiveIdle && !charging) {
                lines += "Active: ${Drain.label(Drain.active(session, capacityMah))}$SEP" +
                    "Idle: ${Drain.label(Drain.idle(session, capacityMah))}"
            }
            if (settings.notifScreen) {
                lines += "Screen On: ${Fmt.duration(session.screenOnMs)}$SEP" +
                    "${pct1(Drain.pct(session.screenOnMah, capacityMah))} (${mah(session.screenOnMah)})"
                lines += "Screen Off: ${Fmt.duration(session.screenOffMs)}$SEP" +
                    "${pct1(Drain.pct(session.screenOffMah, capacityMah))} (${mah(session.screenOffMah)})"
            }
            if (settings.notifSleep && !charging) {
                val off = session.screenOffMs.coerceAtLeast(1)
                lines += "Deep sleep: ${Fmt.duration(session.deepSleepMs)}$SEP${pct0(session.deepSleepMs * 100f / off)}"
                lines += "Awake: ${Fmt.duration(session.awakeMs)}$SEP${pct0(session.awakeMs * 100f / off)}"
            }
        }
        if (lines.isEmpty()) lines += "Battery temperature: ${Fmt.temp(s.tempC, settings.fahrenheit)}"
        return lines.joinToString("\n")
    }

    private fun pct1(v: Float) = "%.1f%%".format(abs(v))
    private fun pct0(v: Float) = "${v.roundToInt().coerceIn(0, 100)}%"
    private fun mah(v: Float) = "${v.roundToInt()} mAh"
}
