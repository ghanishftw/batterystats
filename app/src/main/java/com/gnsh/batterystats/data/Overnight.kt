package com.gnsh.batterystats.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Builds the overnight report from recorded samples and screen events. Pure logic. */
object Overnight {
    /**
     * The night window that ends on [day]. If the start time is later than the end time
     * (23:00 → 07:00), the window starts the evening before.
     */
    fun window(day: LocalDate, startMin: Int, endMin: Int, zone: ZoneId): Pair<Long, Long> {
        val end = day.atTime(LocalTime.of(endMin / 60, endMin % 60)).atZone(zone).toInstant().toEpochMilli()
        val startDay = if (startMin >= endMin) day.minusDays(1) else day
        val start = startDay.atTime(LocalTime.of(startMin / 60, startMin % 60)).atZone(zone).toInstant().toEpochMilli()
        return start to end
    }

    /** The most recent window that has already ended at [now]. */
    fun lastEndedWindow(now: Long, startMin: Int, endMin: Int, zone: ZoneId): Pair<Long, Long> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val w = window(today, startMin, endMin, zone)
        return if (w.second <= now) w else window(today.minusDays(1), startMin, endMin, zone)
    }

    /**
     * [samples] must be sorted and should begin a while before [start] so the starting level is known.
     * Returns null when there isn't enough data to say anything.
     */
    fun compute(start: Long, end: Long, samples: List<Sample>, events: List<ScreenEvent>): Night? {
        fun levelAt(t: Long): Int? =
            samples.lastOrNull { it.time <= t }?.level
                ?: samples.firstOrNull { it.time > t && it.time - t <= 30 * 60_000 }?.level

        val startLevel = levelAt(start) ?: return null
        val endLevel = levelAt(end) ?: return null
        // A sample must exist inside or right after the window, or the service wasn't running.
        if (samples.none { it.time in start..end + 30 * 60_000 }) return null

        val plugged = samples.any { it.time in start..end && it.charging } ||
            samples.lastOrNull { it.time <= start }?.charging == true

        val wakes = mutableListOf<Wake>()
        var openedAt: Long? = if (events.lastOrNull { it.time < start }?.screenOn == true) start else null
        fun close(at: Long) {
            val from = openedAt ?: return
            if (at > from) {
                val drain = ((levelAt(from) ?: 0) - (levelAt(at) ?: 0)).coerceAtLeast(0)
                wakes += Wake(from, at - from, drain)
            }
            openedAt = null
        }
        events.filter { it.time in start..end }.forEach { e ->
            if (e.screenOn) { if (openedAt == null) openedAt = e.time } else close(e.time)
        }
        close(end)

        return Night(end, start, startLevel, endLevel, plugged, Wake.encode(wakes))
    }
}
