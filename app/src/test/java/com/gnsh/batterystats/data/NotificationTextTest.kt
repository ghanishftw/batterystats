package com.gnsh.batterystats.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextTest {
    private val snap = BatterySnapshot(
        timeMs = 0, level = 60, status = ChargeStatus.Discharging, plug = PlugType.None,
        currentMa = -400, voltageMv = 4000, tempC = 31f, health = "Good",
        technology = null, cycleCount = null, chargeCounterMah = null,
    )
    private val discharge = Session(
        charging = false, ongoing = true, startTime = 0, endTime = 4 * 3_600_000L,
        startLevel = 80, endLevel = 60, mah = 900f, measuredFraction = 0.5f,
        screenOnMs = 2 * 3_600_000L, screenOffMs = 2 * 3_600_000L, screenOnPct = 16, screenOffPct = 4,
        screenOnMah = 600f, screenOffMah = 100f, deepSleepMs = 90 * 60_000L,
        maxTempC = 33f, avgTempC = 30f, peakPowerW = 3f, avgCurrentMa = 225f, plugType = "Unplugged",
    )
    private val settings = AppSettings()

    @Test fun dischargeMatchesOriginalLayout() {
        val c = NotificationText.build(snap, discharge, 5000, settings)
        // Level is in the status icon, so the title leaves it out.
        assertTrue(c.title, c.title.startsWith("Now: −400 mA (1.6 W) • 31.0 °C • "))
        assertEquals("Active: 6.0%/h • Idle: 1.0%/h", c.text)
        assertEquals(
            listOf(
                "Active: 6.0%/h • Idle: 1.0%/h",
                "Screen On: 2h 0m • 12.0% (600 mAh)",
                "Screen Off: 2h 0m • 2.0% (100 mAh)",
                "Deep sleep: 1h 30m • 75%",
                "Awake: 30m • 25%",
            ),
            c.body.lines(),
        )
        assertEquals("4h 0m", c.subText)
    }

    @Test fun shortSessionsSayMeasuring() {
        val fresh = discharge.copy(screenOnMs = 60_000, screenOffMs = 60_000)
        assertEquals("Active: Measuring • Idle: Measuring", NotificationText.build(snap, fresh, 5000, settings).text)
    }

    @Test fun chargingHidesActiveIdleAndSleep() {
        val charge = discharge.copy(charging = true)
        val c = NotificationText.build(
            snap.copy(status = ChargeStatus.Charging, plug = PlugType.AC, currentMa = 2000), charge, 5000, settings,
        )
        assertTrue(c.text.startsWith("Screen On:"))
        assertFalse(c.body.contains("Active") || c.body.contains("Deep sleep"))
    }

    @Test fun togglesAndIconTypeChangeContent() {
        val off = settings.copy(notifActiveIdle = false, notifScreen = false, notifSleep = false, iconType = IconType.Temperature)
        val c = NotificationText.build(snap, discharge, 5000, off)
        assertEquals("Battery temperature: 31.0 °C", c.body)
        assertTrue(c.title.startsWith("Now: −400 mA (1.6 W) • 60%"))
    }
}
