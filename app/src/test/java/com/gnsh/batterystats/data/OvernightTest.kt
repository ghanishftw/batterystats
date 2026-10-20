package com.gnsh.batterystats.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class OvernightTest {
    private val zone = ZoneOffset.UTC
    private val h = 3_600_000L
    private val window = Overnight.window(LocalDate.of(2026, 10, 19), 23 * 60, 7 * 60, zone)
    private val start = window.first
    private val end = window.second

    private fun sample(t: Long, level: Int, charging: Boolean = false) =
        Sample(t, level, -50, 25f, 3900, charging, screenOn = false)

    @Test fun windowSpansMidnight() {
        assertEquals(8 * h, end - start)
        assertEquals(LocalDate.of(2026, 10, 18).atTime(23, 0).toInstant(zone).toEpochMilli(), start)
    }

    @Test fun lastEndedWindowPicksYesterdayBeforeEnd() {
        val (_, e) = Overnight.lastEndedWindow(end - h, 23 * 60, 7 * 60, zone)
        assertEquals(end - 24 * h, e)
        assertEquals(end, Overnight.lastEndedWindow(end + h, 23 * 60, 7 * 60, zone).second)
    }

    @Test fun drainAndWakes() {
        val samples = listOf(
            sample(start - 2 * h, 80),
            sample(start + 2 * h, 79),
            sample(start + 3 * h + 5 * 60_000, 78), // during the wake
            sample(start + 6 * h, 76),
        )
        val events = listOf(
            ScreenEvent(start - h, false),
            ScreenEvent(start + 3 * h, true),
            ScreenEvent(start + 3 * h + 10 * 60_000, false),
        )
        val n = Overnight.compute(start, end, samples, events)!!
        assertEquals(4, n.drain)
        assertEquals(0.5f, n.ratePctPerHour, 0.001f)
        assertFalse(n.plugged)
        val w = n.wakeList.single()
        assertEquals(start + 3 * h, w.time)
        assertEquals(10 * 60_000L, w.durationMs)
        assertEquals(1, w.drain)
    }

    @Test fun screenOnAtWindowStartCountsAsWake() {
        val samples = listOf(sample(start - h, 50), sample(start + h, 49))
        val events = listOf(ScreenEvent(start - 10 * 60_000, true), ScreenEvent(start + 20 * 60_000, false))
        assertEquals(20 * 60_000L, Overnight.compute(start, end, samples, events)!!.wakeList.single().durationMs)
    }

    @Test fun chargingIsFlaggedAndNoDataGivesNull() {
        val n = Overnight.compute(start, end, listOf(sample(start - h, 30, true), sample(start + h, 60, true)), emptyList())!!
        assertTrue(n.plugged)
        assertEquals(-30, n.drain)
        assertNull(Overnight.compute(start, end, listOf(sample(start - 10 * h, 50)), emptyList()))
    }
}
