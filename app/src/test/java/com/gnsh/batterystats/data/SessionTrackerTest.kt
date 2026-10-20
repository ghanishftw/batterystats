package com.gnsh.batterystats.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTrackerTest {
    private class FakeClocks : SystemClocks {
        var elapsed = 0L
        var uptime = 0L
        override fun elapsed() = elapsed
        override fun uptime() = uptime
    }

    private fun snap(t: Long, level: Int, ma: Int, plugged: Boolean) = BatterySnapshot(
        timeMs = t, level = level,
        status = if (plugged) ChargeStatus.Charging else ChargeStatus.Discharging,
        plug = if (plugged) PlugType.AC else PlugType.None,
        currentMa = ma, voltageMv = 4000, tempC = 30f, health = "Good",
        technology = null, cycleCount = null, chargeCounterMah = null,
    )

    @Test fun integratesChargeCurrent() {
        val t = SessionTracker.start(snap(0, 20, 2000, true), screenOn = false, clocks = FakeClocks())
        // One hour at 2000 mA, sampled every minute.
        for (i in 1..60) t.onSample(snap(i * 60_000L, 20 + i / 2, 2000, true), 5000)
        val s = t.current(3_600_000L)
        assertEquals(2000f, s.mah, 1f)
        assertEquals(1f, s.measuredFraction, 0.01f)
        assertEquals(30, s.levelDelta)
        assertEquals(30, s.screenOffPct)
    }

    @Test fun splitsScreenOnAndOffDrainAndDeepSleep() {
        val clocks = FakeClocks()
        val t = SessionTracker.start(snap(0, 100, -300, false), screenOn = true, clocks = clocks)
        t.onSample(snap(3_600_000, 90, -300, false), 4000) // 1h screen on, -10%
        t.onScreen(false, 3_600_000)
        clocks.elapsed += 3_600_000; clocks.uptime += 600_000 // 50 of 60 min in deep sleep
        t.onSample(snap(7_200_000, 88, -40, false), 4000)
        val s = t.current(7_200_000)
        assertEquals(10, s.screenOnPct)
        assertEquals(2, s.screenOffPct)
        assertEquals(10f, s.screenOnRate!!, 0.01f)
        assertEquals(2f, s.screenOffRate!!, 0.01f)
        assertEquals(3_000_000L, s.deepSleepMs)
        // The screen-off gap was too long to integrate, so it falls back to level change.
        assertEquals(0.02f * 4000, s.screenOffMah, 0.5f)
    }

    private fun charge(delta: Int, mah: Float, firstUp: Int = 21) = Session(
        charging = true, ongoing = false, startTime = 0, endTime = 3_600_000,
        startLevel = 20, endLevel = 20 + delta, mah = mah, measuredFraction = 1f,
        screenOnMs = 0, screenOffMs = 0, screenOnPct = 0, screenOffPct = 0,
        screenOnMah = 0f, screenOffMah = 0f, deepSleepMs = 0,
        maxTempC = 30f, avgTempC = 30f, peakPowerW = 10f, avgCurrentMa = 0f, plugType = "AC",
        firstUpLevel = firstUp, firstUpMah = 10f, lastUpLevel = firstUp + delta, lastUpMah = 10f + mah,
    )

    @Test fun healthNeedsBigEnoughCharges() {
        assertNull(HealthEstimator.capacityFrom(charge(10, 500f)))
        assertNotNull(HealthEstimator.rejectReason(charge(10, 500f)))
        val est = HealthEstimator.estimate(
            listOf(charge(50, 2250f), charge(60, 2700f), charge(40, 1800f)), 5000, null,
        )
        assertEquals(HealthState.Estimated, est.state)
        assertEquals(4500, est.capacityMah)
        assertEquals(90f, est.healthPct!!, 0.01f)
    }

    @Test fun healthIsCappedAndFlagsBadDesignCapacity() {
        val est = HealthEstimator.estimate(List(3) { charge(50, 2500f) }, 4000, null)
        assertEquals(5000, est.capacityMah)
        assertEquals(100f, est.healthPct!!, 0.01f)
        assertTrue(est.designLooksWrong)
    }

    @Test fun oldSessionsWithoutLevelStepsAreNotUsed() {
        assertEquals("Recorded by an older version", HealthEstimator.rejectReason(charge(50, 2500f, firstUp = -1)))
    }

    @Test fun toppingOffAtFullDoesNotInflateCapacity() {
        val t = SessionTracker.start(snap(0, 50, 2000, true), screenOn = false, clocks = FakeClocks())
        var time = 0L
        // 50% → 100% at 2000 mA: 1% every 54 s on a 3000 mAh battery.
        for (lv in 51..100) { time += 54_000; t.onSample(snap(time, lv, 2000, true), 3000) }
        // Two more hours plugged in at 100%: full, phone draws 300 mA from the battery now and then.
        repeat(240) {
            time += 30_000
            t.onSample(snap(time, 100, -300, true).copy(status = ChargeStatus.Full), 3000)
        }
        val s = t.current(time, ongoing = false)
        assertEquals(51, s.firstUpLevel)
        assertEquals(100, s.lastUpLevel)
        assertEquals(1500f, s.mah, 30f) // nothing added after full
        assertEquals(3000f, HealthEstimator.capacityFrom(s)!!.toFloat(), 60f)
    }
}
