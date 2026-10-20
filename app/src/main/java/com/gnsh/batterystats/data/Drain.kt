package com.gnsh.batterystats.data

/**
 * Active (screen on) and idle (screen off) drain, measured from mAh rather than whole
 * level steps so it has decimals right away, like the original app.
 */
object Drain {
    /** How long each state must last before its rate is trusted. */
    const val ACTIVE_MIN_MS = 5 * 60_000L
    const val IDLE_MIN_MS = 15 * 60_000L

    fun pct(mah: Float, capacityMah: Int) = if (capacityMah > 0) mah / capacityMah * 100f else 0f

    private fun rate(mah: Float, ms: Long, capacityMah: Int) = pct(mah, capacityMah) * 3_600_000f / ms

    fun active(s: Session, capacityMah: Int): Float? =
        if (!s.charging && s.screenOnMs >= ACTIVE_MIN_MS) rate(s.screenOnMah, s.screenOnMs, capacityMah) else null

    fun idle(s: Session, capacityMah: Int): Float? =
        if (!s.charging && s.screenOffMs >= IDLE_MIN_MS) rate(s.screenOffMah, s.screenOffMs, capacityMah) else null

    /** "6.2%/h", or "Measuring" while there isn't enough data yet. */
    fun label(rate: Float?) = rate?.let { "%.1f%%/h".format(it) } ?: "Measuring"

    /** Average current over a span. */
    fun avgMa(mah: Float, ms: Long) = if (ms > 0) mah * 3_600_000f / ms else 0f

    /** Weighted active/idle rates across several discharge sessions. */
    fun average(sessions: List<Session>, capacityMah: Int): Pair<Float?, Float?> {
        val d = sessions.filter { !it.charging }
        val onMs = d.sumOf { it.screenOnMs }
        val offMs = d.sumOf { it.screenOffMs }
        val on = if (onMs >= 30 * 60_000) rate(d.sumOf { it.screenOnMah.toDouble() }.toFloat(), onMs, capacityMah) else null
        val off = if (offMs >= 30 * 60_000) rate(d.sumOf { it.screenOffMah.toDouble() }.toFloat(), offMs, capacityMah) else null
        return on to off
    }
}
