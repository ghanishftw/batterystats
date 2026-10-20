package com.gnsh.batterystats.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A charge or discharge session. `ongoing` marks the one still being recorded. */
@Entity(tableName = "sessions", indices = [Index("endTime")])
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val charging: Boolean,
    val ongoing: Boolean,
    val startTime: Long,
    val endTime: Long,
    val startLevel: Int,
    val endLevel: Int,
    /** Charge moved in or out of the battery, always positive. */
    val mah: Float,
    /** Share of the session where the current was actually sampled (0..1). */
    val measuredFraction: Float,
    val screenOnMs: Long,
    val screenOffMs: Long,
    val screenOnPct: Int,
    val screenOffPct: Int,
    val screenOnMah: Float,
    val screenOffMah: Float,
    val deepSleepMs: Long,
    val maxTempC: Float,
    val avgTempC: Float,
    val peakPowerW: Float,
    val avgCurrentMa: Float,
    val plugType: String,
    /**
     * Charge sessions only: level and charged mAh at the first and last level increase.
     * Health is measured between these two exact step boundaries, so the rounding of the
     * starting level and any top-off time at 100% don't skew it. -1 means not recorded.
     */
    @ColumnInfo(defaultValue = "-1") val firstUpLevel: Int = -1,
    @ColumnInfo(defaultValue = "0") val firstUpMah: Float = 0f,
    @ColumnInfo(defaultValue = "-1") val lastUpLevel: Int = -1,
    @ColumnInfo(defaultValue = "0") val lastUpMah: Float = 0f,
    /** Part of the first-to-last level-up charge that was estimated from level, not measured. */
    @ColumnInfo(defaultValue = "0") val lastUpEstimatedMah: Float = 0f,
) {
    val durationMs get() = endTime - startTime
    val levelDelta get() = if (charging) endLevel - startLevel else startLevel - endLevel
    /** Signed level change: positive when the level went up. */
    val levelChange get() = endLevel - startLevel
    val ratePctPerHour get() = if (durationMs > 0) kotlin.math.abs(levelChange) * 3_600_000f / durationMs else 0f
    val screenOnRate get() = if (screenOnMs > 60_000) screenOnPct * 3_600_000f / screenOnMs else null
    val screenOffRate get() = if (screenOffMs > 60_000) screenOffPct * 3_600_000f / screenOffMs else null
    /** Time the CPU stayed awake while the screen was off. */
    val awakeMs get() = (screenOffMs - deepSleepMs).coerceAtLeast(0)
    val deepSleepFraction get() = if (screenOffMs > 0) (deepSleepMs.toFloat() / screenOffMs).coerceIn(0f, 1f) else null
}

@Entity(tableName = "samples")
data class Sample(
    @PrimaryKey val time: Long,
    val level: Int,
    val currentMa: Int,
    val tempC: Float,
    val voltageMv: Int,
    val charging: Boolean,
    val screenOn: Boolean,
)

@Entity(tableName = "screen_events")
data class ScreenEvent(@PrimaryKey val time: Long, val screenOn: Boolean)

/** One night's drain, saved once the night window has ended. */
@Entity(tableName = "nights")
data class Night(
    @PrimaryKey val end: Long,
    val start: Long,
    val startLevel: Int,
    val endLevel: Int,
    /** True if the phone was charging at any point in the window. */
    val plugged: Boolean,
    /** Screen wakes, encoded by [Wake.encode]. */
    val wakes: String,
) {
    val drain get() = startLevel - endLevel
    val ratePctPerHour get() = kotlin.math.abs(drain) * 3_600_000f / (end - start).coerceAtLeast(1)
    val wakeList get() = Wake.decode(wakes)
}

data class Wake(val time: Long, val durationMs: Long, val drain: Int) {
    companion object {
        fun encode(list: List<Wake>) = list.joinToString(";") { "${it.time},${it.durationMs},${it.drain}" }
        fun decode(s: String) = s.split(";").filter { it.isNotBlank() }.mapNotNull { part ->
            part.split(",").takeIf { it.size == 3 }?.let { Wake(it[0].toLong(), it[1].toLong(), it[2].toInt()) }
        }
    }
}

@Dao
interface BatteryDao {
    @Upsert suspend fun upsertSession(session: Session): Long
    @Query("SELECT * FROM sessions WHERE ongoing = 1 LIMIT 1") suspend fun ongoingSession(): Session?
    @Query("SELECT * FROM sessions WHERE ongoing = 0 ORDER BY endTime DESC") fun sessions(): Flow<List<Session>>
    @Query("DELETE FROM sessions WHERE ongoing = 0") suspend fun clearSessions()
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun deleteSession(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSample(sample: Sample)
    @Query("SELECT * FROM samples WHERE time >= :since ORDER BY time") fun samplesSince(since: Long): Flow<List<Sample>>
    @Query("DELETE FROM samples WHERE time < :before") suspend fun pruneSamples(before: Long)
    @Query("DELETE FROM samples") suspend fun clearSamples()
    @Query("SELECT * FROM samples WHERE time BETWEEN :from AND :to ORDER BY time") suspend fun samplesBetween(from: Long, to: Long): List<Sample>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertScreenEvent(event: ScreenEvent)
    @Query("SELECT * FROM screen_events WHERE time BETWEEN :from AND :to ORDER BY time") suspend fun screenEventsBetween(from: Long, to: Long): List<ScreenEvent>
    @Query("DELETE FROM screen_events WHERE time < :before") suspend fun pruneScreenEvents(before: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNight(night: Night)
    @Query("SELECT EXISTS(SELECT 1 FROM nights WHERE `end` = :end)") suspend fun hasNight(end: Long): Boolean
    @Query("SELECT * FROM nights ORDER BY `end` DESC") fun nights(): Flow<List<Night>>
    @Query("DELETE FROM nights") suspend fun clearNights()
    @Query("DELETE FROM screen_events") suspend fun clearScreenEvents()
}

@Database(
    entities = [Session::class, Sample::class, ScreenEvent::class, Night::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): BatteryDao

    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, AppDatabase::class.java, "battery.db")
            .build()
    }
}
