package com.gnsh.batterystats.data

enum class HealthState { NotYet, Learning, Estimated, Approximate }

data class HealthEstimate(
    val state: HealthState,
    val capacityMah: Int?,
    val designMah: Int?,
    val qualifying: List<Pair<Session, Int>>,
    val estimatedCycles: Float,
) {
    /** Measured capacity vs design, uncapped. */
    val rawPct: Float? get() = if (capacityMah != null && designMah != null && designMah > 0)
        capacityMah * 100f / designMah else null

    /** Health can't exceed 100%: a new battery often holds a little more than rated. */
    val healthPct: Float? get() = rawPct?.coerceIn(0f, 100f)

    /** Far above design usually means the phone reports the wrong design capacity. */
    val designLooksWrong: Boolean get() = (rawPct ?: 0f) > 115f
}

object HealthEstimator {
    const val MIN_LEVEL_DELTA = 30
    const val MIN_MEASURED = 0.8f
    const val SESSIONS_NEEDED = 3

    private fun measuredLevels(s: Session) = s.lastUpLevel - s.firstUpLevel
    private fun measuredMah(s: Session) = s.lastUpMah - s.firstUpMah

    fun capacityFrom(s: Session): Int? =
        if (rejectReason(s) == null) (measuredMah(s) * 100f / measuredLevels(s)).toInt() else null

    fun rejectReason(s: Session): String? = when {
        !s.charging -> "Discharge session"
        s.firstUpLevel < 0 && s.levelDelta >= MIN_LEVEL_DELTA -> "Recorded by an older version"
        measuredLevels(s) < MIN_LEVEL_DELTA -> "Too little charge added to measure capacity"
        s.measuredFraction < MIN_MEASURED || s.lastUpEstimatedMah > 0.05f * measuredMah(s) ->
            "Current wasn't sampled closely enough"
        measuredMah(s) <= 0f -> "No current reported"
        else -> null
    }

    fun estimate(sessions: List<Session>, designMah: Int?, current: BatterySnapshot?): HealthEstimate {
        val qualifying = sessions.mapNotNull { s -> capacityFrom(s)?.let { s to it } }.take(10)
        val cycles = sessions.filter { it.charging }.sumOf { it.levelDelta.coerceAtLeast(0) } / 100f
        val median = qualifying.map { it.second }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val counterEstimate = current?.chargeCounterMah?.takeIf { current.level >= 20 }
            ?.let { it * 100 / current.level }
        val (state, capacity) = when {
            qualifying.size >= SESSIONS_NEEDED -> HealthState.Estimated to median
            qualifying.isNotEmpty() -> HealthState.Learning to median
            counterEstimate != null -> HealthState.Approximate to counterEstimate
            else -> HealthState.NotYet to null
        }
        return HealthEstimate(state, capacity, designMah, qualifying, cycles)
    }
}
