package com.gnsh.batterystats.data

import kotlin.math.abs
import kotlin.math.max

/** Monotonic clocks, injectable for tests. */
interface SystemClocks {
    fun elapsed(): Long
    fun uptime(): Long

    object Android : SystemClocks {
        override fun elapsed() = android.os.SystemClock.elapsedRealtime()
        override fun uptime() = android.os.SystemClock.uptimeMillis()
    }
}

/**
 * Accumulates one charge or discharge session from battery readings and screen events.
 * Pure logic: the service feeds it, and it never touches Android APIs directly.
 */
class SessionTracker private constructor(
    private var base: Session,
    first: BatterySnapshot,
    private var screenOn: Boolean,
    private val clocks: SystemClocks,
) {
    val charging get() = base.charging
    var id: Long = base.id

    private var lastTime = first.timeMs
    private var lastLevel = first.level
    // Only current flowing the session's way counts: into the battery while charging,
    // out of it while discharging. Plugged in but full or held at a limit adds nothing.
    private fun flow(currentMa: Int) = (if (charging) currentMa else -currentMa).coerceAtLeast(0)
    private var lastCurrent = flow(first.currentMa)
    private var screenSince = first.timeMs
    private var offElapsed = clocks.elapsed()
    private var offUptime = clocks.uptime()

    // Restore integrals from the stored averages so resumed sessions stay consistent.
    private var sampledMs = base.durationMs
    private var measuredMs = (base.measuredFraction * base.durationMs).toLong()
    private var tempIntegral = base.avgTempC.toDouble() * base.durationMs
    private var estimatedSinceFirstUp = base.lastUpEstimatedMah

    fun onSample(s: BatterySnapshot, capacityMah: Int) {
        val dt = s.timeMs - lastTime
        if (dt > 0) {
            val cur = flow(s.currentMa)
            val measured = dt <= MAX_GAP_MS
            val mahDelta = if (measured) {
                measuredMs += dt
                (lastCurrent + cur) / 2f * dt / 3_600_000f
            } else {
                // The CPU slept between readings; fall back to the level change in our direction.
                val moved = if (charging) s.level - lastLevel else lastLevel - s.level
                moved.coerceAtLeast(0) / 100f * capacityMah
            }
            if (!measured && base.firstUpLevel >= 0) estimatedSinceFirstUp += mahDelta
            sampledMs += dt
            tempIntegral += s.tempC.toDouble() * dt
            base = base.copy(
                mah = base.mah + mahDelta,
                screenOnMah = base.screenOnMah + if (screenOn) mahDelta else 0f,
                screenOffMah = base.screenOffMah + if (!screenOn) mahDelta else 0f,
            )
        }

        if (charging && s.level > lastLevel) {
            base = if (base.firstUpLevel < 0) base.copy(firstUpLevel = s.level, firstUpMah = base.mah)
            else base
            base = base.copy(lastUpLevel = s.level, lastUpMah = base.mah, lastUpEstimatedMah = estimatedSinceFirstUp)
        }

        val gained = if (charging) s.level - lastLevel else lastLevel - s.level
        if (gained > 0) {
            base = if (screenOn) base.copy(screenOnPct = base.screenOnPct + gained)
            else base.copy(screenOffPct = base.screenOffPct + gained)
        }

        base = base.copy(
            endLevel = s.level,
            endTime = s.timeMs,
            maxTempC = max(base.maxTempC, s.tempC),
            peakPowerW = max(base.peakPowerW, abs(s.powerW)),
        )
        lastTime = s.timeMs
        lastLevel = s.level
        lastCurrent = flow(s.currentMa)
    }

    fun onScreen(on: Boolean, now: Long) {
        if (on == screenOn) return
        base = withPendingScreenTime(base, now, commitSleep = true)
        screenOn = on
        screenSince = now
        if (!on) {
            offElapsed = clocks.elapsed()
            offUptime = clocks.uptime()
        }
    }

    private fun withPendingScreenTime(s: Session, now: Long, commitSleep: Boolean): Session {
        val span = (now - screenSince).coerceAtLeast(0)
        return if (screenOn) s.copy(screenOnMs = s.screenOnMs + span)
        else {
            val sleep = ((clocks.elapsed() - offElapsed) - (clocks.uptime() - offUptime)).coerceIn(0, span)
            if (commitSleep) { offElapsed = clocks.elapsed(); offUptime = clocks.uptime() }
            s.copy(screenOffMs = s.screenOffMs + span, deepSleepMs = s.deepSleepMs + sleep)
        }
    }

    /** The session as of [now], without committing pending screen time. */
    fun current(now: Long, ongoing: Boolean = true): Session {
        val s = withPendingScreenTime(base, now, commitSleep = false)
        val duration = (now - s.startTime).coerceAtLeast(1)
        return s.copy(
            id = id,
            ongoing = ongoing,
            endTime = max(s.endTime, now),
            measuredFraction = (measuredMs.toFloat() / duration).coerceIn(0f, 1f),
            avgTempC = if (sampledMs > 0) (tempIntegral / sampledMs).toFloat() else s.maxTempC,
            avgCurrentMa = s.mah * 3_600_000f / duration,
        )
    }

    companion object {
        const val MAX_GAP_MS = 120_000L

        fun start(s: BatterySnapshot, screenOn: Boolean, clocks: SystemClocks = SystemClocks.Android) = SessionTracker(
            Session(
                charging = s.plugged, ongoing = true,
                startTime = s.timeMs, endTime = s.timeMs,
                startLevel = s.level, endLevel = s.level,
                mah = 0f, measuredFraction = 0f,
                screenOnMs = 0, screenOffMs = 0, screenOnPct = 0, screenOffPct = 0,
                screenOnMah = 0f, screenOffMah = 0f, deepSleepMs = 0,
                maxTempC = s.tempC, avgTempC = s.tempC, peakPowerW = abs(s.powerW), avgCurrentMa = 0f,
                plugType = s.plug.label,
            ),
            s, screenOn, clocks,
        )

        fun resume(saved: Session, s: BatterySnapshot, screenOn: Boolean, clocks: SystemClocks = SystemClocks.Android) =
            SessionTracker(saved, s.copy(timeMs = saved.endTime, level = saved.endLevel, currentMa = 0), screenOn, clocks)
    }
}
