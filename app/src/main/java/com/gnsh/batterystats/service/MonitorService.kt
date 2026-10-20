package com.gnsh.batterystats.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gnsh.batterystats.MainActivity
import com.gnsh.batterystats.R
import com.gnsh.batterystats.app
import com.gnsh.batterystats.data.AppSettings
import com.gnsh.batterystats.data.BatteryReader
import com.gnsh.batterystats.data.BatterySnapshot
import com.gnsh.batterystats.data.Fmt
import com.gnsh.batterystats.data.Night
import com.gnsh.batterystats.data.NotificationText
import com.gnsh.batterystats.data.Overnight
import com.gnsh.batterystats.data.ScreenEvent
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date
import com.gnsh.batterystats.data.Sample
import com.gnsh.batterystats.data.SessionTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Samples the battery, records sessions, keeps the live notification and fires charge alarms. */
class MonitorService : LifecycleService() {
    private val dao by lazy { app.db.dao() }
    private val mutex = Mutex()
    private var tracker: SessionTracker? = null
    private var settings = AppSettings()
    private var screenOn = true
    private var lastPersist = 0L
    private var lastPrune = 0L
    private var lastSampleLevel = -1
    private var chargeAlarmFired = false
    private var lowAlarmFired = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var nextNightCheck = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> screenChanged(true)
                Intent.ACTION_SCREEN_OFF -> screenChanged(false)
                Intent.ACTION_BATTERY_CHANGED -> {
                    val snap = BatteryReader.parse(intent, getSystemService(BatteryManager::class.java))
                    lifecycleScope.launch { process(snap) }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(BatteryReader.read(this)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        screenOn = getSystemService(PowerManager::class.java).isInteractive

        lifecycleScope.launch {
            app.settings.settings.collect { settings = it }
        }
        lifecycleScope.launch {
            settings = app.settings.settings.first()
            dao.insertScreenEvent(ScreenEvent(System.currentTimeMillis(), screenOn))
            restoreSession()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            ContextCompat.registerReceiver(this@MonitorService, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            while (isActive) {
                BatteryReader.read(this@MonitorService)?.let { process(it) }
                delay(if (screenOn) 3_000 else 15_000)
            }
        }
    }

    private suspend fun restoreSession() = mutex.withLock {
        val snap = BatteryReader.read(this) ?: return@withLock
        val saved = dao.ongoingSession()
        tracker = when {
            saved == null -> null
            saved.charging == snap.plugged -> SessionTracker.resume(saved, snap, screenOn).also { it.id = saved.id }
            else -> {
                dao.upsertSession(saved.copy(ongoing = false))
                null
            }
        }
    }

    private fun screenChanged(on: Boolean) {
        lifecycleScope.launch {
            mutex.withLock {
                screenOn = on
                val now = System.currentTimeMillis()
                tracker?.onScreen(on, now)
                dao.insertScreenEvent(ScreenEvent(now, on))
            }
            BatteryReader.read(this@MonitorService)?.let { process(it) }
        }
    }

    private suspend fun process(snap: BatterySnapshot) = mutex.withLock {
        val capacity = app.capacityMah(settings.designCapacityOverride)
        var t = tracker
        if (t == null || t.charging != snap.plugged) {
            t?.let { finish(it, snap.timeMs) }
            t = SessionTracker.start(snap, screenOn)
            tracker = t
            chargeAlarmFired = false
            lowAlarmFired = false
            lastPersist = 0
        }
        t.onSample(snap, capacity)
        val session = t.current(snap.timeMs)
        app.liveSession.value = session

        // Persist the session and a chart sample once a minute, plus on every level change.
        val minuteTick = snap.timeMs - lastPersist > 60_000
        if (minuteTick) {
            lastPersist = snap.timeMs
            val id = dao.upsertSession(session)
            if (id > 0) t.id = id
        }
        if (minuteTick || snap.level != lastSampleLevel) {
            lastSampleLevel = snap.level
            dao.insertSample(snap.toSample())
        }
        if (snap.timeMs - lastPrune > 3_600_000) {
            lastPrune = snap.timeMs
            dao.pruneSamples(snap.timeMs - 7 * 86_400_000L)
            dao.pruneScreenEvents(snap.timeMs - 7 * 86_400_000L)
        }
        if (screenOn && snap.timeMs >= nextNightCheck) {
            nextNightCheck = snap.timeMs + 60_000
            saveLastNight(snap.timeMs)
        }

        updateWakeLock(snap.plugged)
        checkAlarms(snap)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(snap))
    }

    /** Saves the night that just ended, the first time the screen is on after the window closes. */
    private suspend fun saveLastNight(now: Long) {
        val zone = ZoneId.systemDefault()
        val (start, end) = Overnight.lastEndedWindow(now, settings.nightStartMin, settings.nightEndMin, zone)
        if (now - end > 12 * 3_600_000L || dao.hasNight(end)) return
        val samples = dao.samplesBetween(start - 12 * 3_600_000L, end + 30 * 60_000L)
        val events = dao.screenEventsBetween(start - 12 * 3_600_000L, end)
        val night = Overnight.compute(start, end, samples, events) ?: return
        dao.insertNight(night)
        if (settings.overnightReport) {
            getSystemService(NotificationManager::class.java).notify(OVERNIGHT_ID, overnightNotification(night))
        }
    }

    private fun overnightNotification(n: Night): Notification {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT)
        val wakes = n.wakeList
        val body = "Drained ${n.drain.coerceAtLeast(0)}% between ${time.format(Date(n.start))} and ${time.format(Date(n.end))}" +
            " (${Fmt.pctRate(n.ratePctPerHour.coerceAtLeast(0f))})" + if (n.plugged) ", includes charging" else ""
        val wakeLine = if (wakes.isEmpty()) "No screen activity. Undisturbed night."
        else "${wakes.size} wake${if (wakes.size > 1) "s" else ""} · ${Fmt.duration(wakes.sumOf { it.durationMs })} screen on"
        return NotificationCompat.Builder(this, CHANNEL_OVERNIGHT)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("Overnight battery")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n$wakeLine"))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
    }

    private fun BatterySnapshot.toSample() =
        Sample(timeMs, level, currentMa, tempC, voltageMv, plugged, screenOn)

    private suspend fun finish(t: SessionTracker, now: Long) {
        val done = t.current(now, ongoing = false)
        val meaningful = done.durationMs >= 2 * 60_000 && (done.levelDelta != 0 || done.mah >= 5f)
        when {
            meaningful -> dao.upsertSession(done)
            done.id > 0 -> dao.deleteSession(done.id)
        }
    }

    /** Hold the CPU awake while plugged in so the charge current is sampled continuously. */
    private fun updateWakeLock(plugged: Boolean) {
        if (plugged) {
            val wl = wakeLock ?: getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BatteryStats:charging").also { wakeLock = it }
            wl.acquire(10 * 60_000L)
        } else {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
    }

    private fun checkAlarms(s: BatterySnapshot) {
        val nm = getSystemService(NotificationManager::class.java)
        if (settings.chargeAlarm && s.plugged && !chargeAlarmFired && s.level >= settings.chargeAlarmLevel) {
            chargeAlarmFired = true
            nm.notify(ALARM_ID, alert("Battery at ${s.level}%", "Unplug now to reduce battery wear."))
        }
        if (settings.lowAlarm && !s.plugged && !lowAlarmFired && s.level <= settings.lowAlarmLevel) {
            lowAlarmFired = true
            nm.notify(ALARM_ID, alert("Battery low: ${s.level}%", "Plug in your charger soon."))
        }
        if (!s.plugged) chargeAlarmFired = false
        if (s.plugged) lowAlarmFired = false
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun alert(title: String, text: String) = NotificationCompat.Builder(this, CHANNEL_ALERTS)
        .setSmallIcon(R.drawable.ic_stat_battery)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setContentIntent(openApp())
        .setAutoCancel(true)
        .build()

    private val statusIcon by lazy { StatusIcon(this) }

    private fun buildNotification(s: BatterySnapshot?): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp())
        statusIcon.icon(s, settings)?.let { b.setSmallIcon(it) } ?: b.setSmallIcon(StatusIcon.FALLBACK)
        if (s == null) return b.setContentTitle("Monitoring battery").build()

        val c = NotificationText.build(s, app.liveSession.value, app.capacityMah(settings.designCapacityOverride), settings)
        return b.setContentTitle(c.title)
            .setContentText(c.text)
            .setSubText(c.subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(c.body))
            .build()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val ALARM_ID = 2
        private const val OVERNIGHT_ID = 3
        const val CHANNEL_OVERNIGHT = "overnight"
        const val CHANNEL_MONITOR = "monitor"
        const val CHANNEL_ALERTS = "alerts"

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_MONITOR, "Live battery status", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, "Battery alarms", NotificationManager.IMPORTANCE_HIGH),
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_OVERNIGHT, "Overnight report", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Morning summary of overnight battery drain" },
            )
        }

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
            context.app.liveSession.value = null
        }
    }
}
