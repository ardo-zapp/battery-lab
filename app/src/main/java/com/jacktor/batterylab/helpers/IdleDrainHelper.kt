package com.jacktor.batterylab.helpers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.R
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.security.NativeCore
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_ACTIVE_SCREEN_DURATION_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_ACTIVE_SCREEN_MAH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_ACTIVE_SCREEN_PERCENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_BASELINE_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_BASELINE_LEVEL
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_BASELINE_REALTIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_BASELINE_SCREEN_TIME_SEC
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_BASELINE_UPTIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_CURRENT_START_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_CURRENT_START_LEVEL
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_CURRENT_START_REALTIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_CURRENT_START_UPTIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_IS_ACTIVE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_AWAKE_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_DEEP_SLEEP_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_DURATION_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_END_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_END_LEVEL
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_MAH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_PERCENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_PERCENT_FLOAT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_SCREEN_ON_REALTIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_TIMESTAMP
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_SESSIONS_COUNT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_TOTAL_AWAKE_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_TOTAL_DEEP_SLEEP_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_TOTAL_DURATION_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_TOTAL_MAH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_TOTAL_PERCENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_TOTAL_PERCENT_FLOAT
import java.text.DecimalFormat
import kotlin.math.roundToInt

enum class IdleDrainStatus {
    EXCELLENT,
    NORMAL,
    HIGH_DRAIN,
    NO_DATA
}

data class IdleDrainStats(
    val totalDurationMs: Long,
    val totalDeepSleepMs: Long,
    val totalAwakeMs: Long,
    val totalDrainPercent: Float,
    val totalDrainMah: Float,
    val sessionsCount: Int,
    val totalDrainRatePercentPerHour: Double,
    val totalDrainRateMa: Double,
    val deepSleepPercent: Double,
    val lastDurationMs: Long,
    val lastDeepSleepMs: Long,
    val lastAwakeMs: Long,
    val lastDrainPercent: Float,
    val lastDrainMah: Float,
    val lastDrainRatePercentPerHour: Double,
    val lastDeepSleepPercent: Double,
    val lastTimestamp: Long,
    val isCurrentlyIdle: Boolean,
    val isCharging: Boolean,
    val status: IdleDrainStatus,
    val diagnosticSummary: String,
    val recommendation: String
)

object IdleDrainHelper : BatteryInfoInterface {

    const val MIN_IDLE_DURATION_MS = 20_000L
    private const val BRIEF_WAKE_THRESHOLD_MS = 30_000L

    private fun isPluggedOrCharging(context: Context): Boolean {
        val bi = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            null
        } ?: batteryIntent
        if (bi != null) batteryIntent = bi

        val plugged = bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val status = bi?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        return plugged > 0 || status == BatteryManager.BATTERY_STATUS_CHARGING
    }

    private fun isInteractive(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isInteractive ?: true
    }

    private fun hasHardwareChargeCounter(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val cc = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0
            cc > 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun getAccurateDesignCapacity(context: Context): Float {
        val design = try {
            getDesignCapacity(context)
        } catch (_: Throwable) {
            0
        }
        return if (design >= 1000) design.toFloat() else 4000f
    }

    private fun getAccurateCapacityMah(context: Context, level: Int): Float {
        val design = getAccurateDesignCapacity(context)
        val raw = try {
            getAvailableCapacity(context).toFloat()
        } catch (_: Throwable) {
            0f
        }
        return when {
            raw in 0.5f..(design * 0.08f) && raw * 1000f <= design * 1.5f -> raw * 1000f
            raw >= design * 0.08f && raw <= design * 2.0f -> raw
            else -> (level.coerceIn(0, 100) / 100f) * design
        }
    }

    /**
     * Computes per-session drain without 1% integer quantization bias on short idle windows.
     */
    private fun computeSessionDrain(
        context: Context,
        durationMs: Long,
        deepSleepMs: Long,
        startLevel: Int,
        endLevel: Int,
        startCapacity: Float,
        endCapacity: Float,
        designCap: Float
    ): Pair<Float, Float> {
        return NativeCore.computeIdleSessionDrain(
            durationMs = durationMs,
            deepSleepMs = deepSleepMs,
            startLevel = startLevel,
            endLevel = endLevel,
            startCap = startCapacity,
            endCap = endCapacity,
            designCap = designCap,
            hasCounter = hasHardwareChargeCounter(context)
        )
    }

    private fun ensureBaselineInitialized(
        pref: SharedPreferences,
        nowRealtime: Long,
        nowUptime: Long,
        level: Int,
        capacity: Float
    ) {
        val baseRealtime = pref.getLong(IDLE_DRAIN_BASELINE_REALTIME, 0L)
        val baseLevel = pref.getInt(IDLE_DRAIN_BASELINE_LEVEL, -1)
        val currentScreenTimeSec = BatteryLabService.instance?.screenTime ?: 0L

        if (baseRealtime <= 0L || nowRealtime < baseRealtime || baseLevel !in 0..100 || level > baseLevel + 1) {
            pref.edit {
                putLong(IDLE_DRAIN_BASELINE_REALTIME, nowRealtime)
                putLong(IDLE_DRAIN_BASELINE_UPTIME, nowUptime)
                putInt(IDLE_DRAIN_BASELINE_LEVEL, level)
                putFloat(IDLE_DRAIN_BASELINE_CAPACITY, capacity)
                putLong(IDLE_DRAIN_BASELINE_SCREEN_TIME_SEC, currentScreenTimeSec)
                putLong(IDLE_DRAIN_ACTIVE_SCREEN_DURATION_MS, 0L)
                putFloat(IDLE_DRAIN_ACTIVE_SCREEN_PERCENT, 0f)
                putFloat(IDLE_DRAIN_ACTIVE_SCREEN_MAH, 0f)
            }
        }
    }

    @Synchronized
    fun syncWithSystemState(context: Context) {
        val charging = isPluggedOrCharging(context)
        if (charging) {
            onPowerConnected(context)
            return
        }

        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val isActive = pref.getBoolean(IDLE_DRAIN_IS_ACTIVE, false)
        val interactive = isInteractive(context)

        if (interactive) {
            if (isActive) {
                onScreenOn(context)
            } else {
                val nowRealtime = SystemClock.elapsedRealtime()
                val nowUptime = SystemClock.uptimeMillis()
                val level = getBatteryLevel(context) ?: 0
                val capacity = getAccurateCapacityMah(context, level)
                ensureBaselineInitialized(pref, nowRealtime, nowUptime, level, capacity)
                if (pref.getLong(IDLE_DRAIN_LAST_SCREEN_ON_REALTIME, 0L) <= 0L) {
                    pref.edit {
                        putLong(IDLE_DRAIN_LAST_SCREEN_ON_REALTIME, nowRealtime)
                        putInt(IDLE_DRAIN_LAST_END_LEVEL, level)
                        putFloat(IDLE_DRAIN_LAST_END_CAPACITY, capacity)
                    }
                }
            }
        } else {
            if (!isActive) {
                onScreenOff(context)
            }
        }
    }

    @Synchronized
    fun onScreenOff(context: Context) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        if (isPluggedOrCharging(context)) {
            pref.edit { putBoolean(IDLE_DRAIN_IS_ACTIVE, false) }
            return
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val level = getBatteryLevel(context) ?: return
        val capacity = getAccurateCapacityMah(context, level)
        val designCap = getAccurateDesignCapacity(context)

        ensureBaselineInitialized(pref, nowRealtime, nowUptime, level, capacity)

        val alreadyActive = pref.getBoolean(IDLE_DRAIN_IS_ACTIVE, false)
        val existingStartRealtime = pref.getLong(IDLE_DRAIN_CURRENT_START_REALTIME, 0L)
        if (alreadyActive && existingStartRealtime in 1..<nowRealtime) {
            return
        }

        val lastScreenOnRealtime = pref.getLong(IDLE_DRAIN_LAST_SCREEN_ON_REALTIME, 0L)
        val lastEndLevel = pref.getInt(IDLE_DRAIN_LAST_END_LEVEL, -1)
        val lastEndCapacity = pref.getFloat(IDLE_DRAIN_LAST_END_CAPACITY, 0f)

        if (lastScreenOnRealtime in 1..nowRealtime && lastEndLevel in 0..100) {
            val screenOnGapMs = nowRealtime - lastScreenOnRealtime
            val gapLevelDrop = (lastEndLevel - level).coerceAtLeast(0)
            val gapRawMahDrop = if (lastEndCapacity > 0f && capacity > 0f) {
                (lastEndCapacity - capacity).coerceAtLeast(0f)
            } else 0f
            val gapMahPercent = if (designCap > 0f) (gapRawMahDrop / designCap) * 100f else 0f

            val lastIdleDuration = pref.getLong(IDLE_DRAIN_LAST_DURATION_MS, 0L)

            if (screenOnGapMs <= BRIEF_WAKE_THRESHOLD_MS && lastIdleDuration >= 10 * 60_000L) {
                // Attribute only the fractional coulomb delta on brief wakes to avoid 1% boundary overlap
                val carryPct = if (gapMahPercent > 0f) {
                    gapMahPercent.coerceAtMost(0.35f)
                } else if (gapLevelDrop == 1 && pref.getFloat(IDLE_DRAIN_LAST_PERCENT_FLOAT, 0f) < 0.2f) {
                    val maxCarry = ((lastIdleDuration / 3_600_000f) * 1.2f).coerceIn(0.15f, 0.8f)
                    maxCarry
                } else 0f

                if (carryPct > 0f) {
                    val carryMah = (carryPct / 100f) * designCap
                    val oldTotalPercentFloat = pref.getFloat(
                        IDLE_DRAIN_TOTAL_PERCENT_FLOAT,
                        pref.getInt(IDLE_DRAIN_TOTAL_PERCENT, 0).toFloat()
                    )
                    val oldTotalMah = pref.getFloat(IDLE_DRAIN_TOTAL_MAH, 0f)
                    val oldLastPercentFloat = pref.getFloat(
                        IDLE_DRAIN_LAST_PERCENT_FLOAT,
                        pref.getInt(IDLE_DRAIN_LAST_PERCENT, 0).toFloat()
                    )
                    val oldLastMah = pref.getFloat(IDLE_DRAIN_LAST_MAH, 0f)

                    val newTotalPercent = oldTotalPercentFloat + carryPct
                    val newLastPercent = oldLastPercentFloat + carryPct

                    pref.edit {
                        putFloat(IDLE_DRAIN_TOTAL_PERCENT_FLOAT, newTotalPercent)
                        putInt(IDLE_DRAIN_TOTAL_PERCENT, newTotalPercent.roundToInt())
                        putFloat(IDLE_DRAIN_TOTAL_MAH, oldTotalMah + carryMah)
                        putFloat(IDLE_DRAIN_LAST_PERCENT_FLOAT, newLastPercent)
                        putInt(IDLE_DRAIN_LAST_PERCENT, newLastPercent.roundToInt())
                        putFloat(IDLE_DRAIN_LAST_MAH, oldLastMah + carryMah)
                    }
                }
            } else {
                val activeGapPct = maxOf(gapLevelDrop.toFloat(), gapMahPercent)
                val activeGapMah = maxOf(gapRawMahDrop, (activeGapPct / 100f) * designCap)
                val oldActiveDuration = pref.getLong(IDLE_DRAIN_ACTIVE_SCREEN_DURATION_MS, 0L)
                val oldActivePercent = pref.getFloat(IDLE_DRAIN_ACTIVE_SCREEN_PERCENT, 0f)
                val oldActiveMah = pref.getFloat(IDLE_DRAIN_ACTIVE_SCREEN_MAH, 0f)

                pref.edit {
                    putLong(IDLE_DRAIN_ACTIVE_SCREEN_DURATION_MS, oldActiveDuration + screenOnGapMs)
                    putFloat(IDLE_DRAIN_ACTIVE_SCREEN_PERCENT, oldActivePercent + activeGapPct)
                    putFloat(IDLE_DRAIN_ACTIVE_SCREEN_MAH, oldActiveMah + activeGapMah)
                }
            }
        }

        pref.edit {
            putLong(IDLE_DRAIN_CURRENT_START_REALTIME, nowRealtime)
            putLong(IDLE_DRAIN_CURRENT_START_UPTIME, nowUptime)
            putInt(IDLE_DRAIN_CURRENT_START_LEVEL, level)
            putFloat(IDLE_DRAIN_CURRENT_START_CAPACITY, capacity)
            putBoolean(IDLE_DRAIN_IS_ACTIVE, true)
        }
    }

    @Synchronized
    fun onScreenOn(context: Context) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val isActive = pref.getBoolean(IDLE_DRAIN_IS_ACTIVE, false)
        val endRealtime = SystemClock.elapsedRealtime()
        val endUptime = SystemClock.uptimeMillis()
        val endLevel = getBatteryLevel(context) ?: -1
        val safeEndLevel = if (endLevel in 0..100) endLevel else pref.getInt(IDLE_DRAIN_CURRENT_START_LEVEL, 0)
        val endCapacity = getAccurateCapacityMah(context, safeEndLevel)
        val designCap = getAccurateDesignCapacity(context)

        if (!isActive) {
            if (pref.getLong(IDLE_DRAIN_LAST_SCREEN_ON_REALTIME, 0L) <= 0L) {
                pref.edit {
                    putLong(IDLE_DRAIN_LAST_SCREEN_ON_REALTIME, endRealtime)
                    putInt(IDLE_DRAIN_LAST_END_LEVEL, safeEndLevel)
                    putFloat(IDLE_DRAIN_LAST_END_CAPACITY, endCapacity)
                }
            }
            return
        }

        val startRealtime = pref.getLong(IDLE_DRAIN_CURRENT_START_REALTIME, 0L)
        val startUptime = pref.getLong(IDLE_DRAIN_CURRENT_START_UPTIME, 0L)

        pref.edit {
            putBoolean(IDLE_DRAIN_IS_ACTIVE, false)
            putLong(IDLE_DRAIN_LAST_SCREEN_ON_REALTIME, endRealtime)
            putInt(IDLE_DRAIN_LAST_END_LEVEL, safeEndLevel)
            putFloat(IDLE_DRAIN_LAST_END_CAPACITY, endCapacity)
        }

        if (startRealtime <= 0L || endRealtime <= startRealtime) return

        val durationMs = endRealtime - startRealtime
        if (durationMs < MIN_IDLE_DURATION_MS) return

        val awakeMs = if (endUptime >= startUptime) {
            (endUptime - startUptime).coerceIn(0L, durationMs)
        } else 0L
        val deepSleepMs = (durationMs - awakeMs).coerceAtLeast(0L)

        val startLevel = pref.getInt(IDLE_DRAIN_CURRENT_START_LEVEL, -1)
        val startCapacity = pref.getFloat(IDLE_DRAIN_CURRENT_START_CAPACITY, 0f)

        val (drainPercentFloat, drainMah) = computeSessionDrain(
            context = context,
            durationMs = durationMs,
            deepSleepMs = deepSleepMs,
            startLevel = startLevel,
            endLevel = safeEndLevel,
            startCapacity = startCapacity,
            endCapacity = endCapacity,
            designCap = designCap
        )

        val oldTotalDuration = pref.getLong(IDLE_DRAIN_TOTAL_DURATION_MS, 0L)
        val oldTotalDeepSleep = pref.getLong(IDLE_DRAIN_TOTAL_DEEP_SLEEP_MS, 0L)
        val oldTotalAwake = pref.getLong(IDLE_DRAIN_TOTAL_AWAKE_MS, 0L)
        val oldTotalPercentFloat = pref.getFloat(
            IDLE_DRAIN_TOTAL_PERCENT_FLOAT,
            pref.getInt(IDLE_DRAIN_TOTAL_PERCENT, 0).toFloat()
        )
        val oldTotalMah = pref.getFloat(IDLE_DRAIN_TOTAL_MAH, 0f)
        val oldSessions = pref.getInt(IDLE_DRAIN_SESSIONS_COUNT, 0)

        val newTotalPercentFloat = oldTotalPercentFloat + drainPercentFloat

        pref.edit {
            putLong(IDLE_DRAIN_LAST_DURATION_MS, durationMs)
            putLong(IDLE_DRAIN_LAST_DEEP_SLEEP_MS, deepSleepMs)
            putLong(IDLE_DRAIN_LAST_AWAKE_MS, awakeMs)
            putInt(IDLE_DRAIN_LAST_PERCENT, drainPercentFloat.roundToInt())
            putFloat(IDLE_DRAIN_LAST_PERCENT_FLOAT, drainPercentFloat)
            putFloat(IDLE_DRAIN_LAST_MAH, drainMah)
            putLong(IDLE_DRAIN_LAST_TIMESTAMP, System.currentTimeMillis())

            putLong(IDLE_DRAIN_TOTAL_DURATION_MS, oldTotalDuration + durationMs)
            putLong(IDLE_DRAIN_TOTAL_DEEP_SLEEP_MS, oldTotalDeepSleep + deepSleepMs)
            putLong(IDLE_DRAIN_TOTAL_AWAKE_MS, oldTotalAwake + awakeMs)
            putInt(IDLE_DRAIN_TOTAL_PERCENT, newTotalPercentFloat.roundToInt())
            putFloat(IDLE_DRAIN_TOTAL_PERCENT_FLOAT, newTotalPercentFloat)
            putFloat(IDLE_DRAIN_TOTAL_MAH, oldTotalMah + drainMah)
            putInt(IDLE_DRAIN_SESSIONS_COUNT, oldSessions + 1)
        }
    }

    @Synchronized
    fun onPowerConnected(context: Context) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        if (pref.getBoolean(IDLE_DRAIN_IS_ACTIVE, false)) {
            onScreenOn(context)
        }
        pref.edit { putBoolean(IDLE_DRAIN_IS_ACTIVE, false) }
    }

    @Synchronized
    fun onPowerDisconnected(context: Context) {
        resetSinceUnplugged(context)
        if (!isInteractive(context)) {
            onScreenOff(context)
        }
    }

    @Synchronized
    fun resetSinceUnplugged(context: Context) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val level = getBatteryLevel(context) ?: 0
        val capacity = getAccurateCapacityMah(context, level)

        pref.edit {
            putLong(IDLE_DRAIN_TOTAL_DURATION_MS, 0L)
            putLong(IDLE_DRAIN_TOTAL_DEEP_SLEEP_MS, 0L)
            putLong(IDLE_DRAIN_TOTAL_AWAKE_MS, 0L)
            putInt(IDLE_DRAIN_TOTAL_PERCENT, 0)
            putFloat(IDLE_DRAIN_TOTAL_PERCENT_FLOAT, 0f)
            putFloat(IDLE_DRAIN_TOTAL_MAH, 0f)
            putInt(IDLE_DRAIN_SESSIONS_COUNT, 0)
            putBoolean(IDLE_DRAIN_IS_ACTIVE, false)
            putLong(IDLE_DRAIN_LAST_SCREEN_ON_REALTIME, nowRealtime)
            putInt(IDLE_DRAIN_LAST_END_LEVEL, level)
            putFloat(IDLE_DRAIN_LAST_END_CAPACITY, capacity)
            putLong(IDLE_DRAIN_BASELINE_REALTIME, nowRealtime)
            putLong(IDLE_DRAIN_BASELINE_UPTIME, nowUptime)
            putInt(IDLE_DRAIN_BASELINE_LEVEL, level)
            putFloat(IDLE_DRAIN_BASELINE_CAPACITY, capacity)
            putLong(IDLE_DRAIN_BASELINE_SCREEN_TIME_SEC, 0L)
            putLong(IDLE_DRAIN_ACTIVE_SCREEN_DURATION_MS, 0L)
            putFloat(IDLE_DRAIN_ACTIVE_SCREEN_PERCENT, 0f)
            putFloat(IDLE_DRAIN_ACTIVE_SCREEN_MAH, 0f)
        }
    }

    @Synchronized
    fun resetAll(context: Context) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val level = getBatteryLevel(context) ?: 0
        val capacity = getAccurateCapacityMah(context, level)
        val currentScreenTimeSec = BatteryLabService.instance?.screenTime ?: 0L

        pref.edit {
            putLong(IDLE_DRAIN_TOTAL_DURATION_MS, 0L)
            putLong(IDLE_DRAIN_TOTAL_DEEP_SLEEP_MS, 0L)
            putLong(IDLE_DRAIN_TOTAL_AWAKE_MS, 0L)
            putInt(IDLE_DRAIN_TOTAL_PERCENT, 0)
            putFloat(IDLE_DRAIN_TOTAL_PERCENT_FLOAT, 0f)
            putFloat(IDLE_DRAIN_TOTAL_MAH, 0f)
            putInt(IDLE_DRAIN_SESSIONS_COUNT, 0)
            putLong(IDLE_DRAIN_LAST_DURATION_MS, 0L)
            putLong(IDLE_DRAIN_LAST_DEEP_SLEEP_MS, 0L)
            putLong(IDLE_DRAIN_LAST_AWAKE_MS, 0L)
            putInt(IDLE_DRAIN_LAST_PERCENT, 0)
            putFloat(IDLE_DRAIN_LAST_PERCENT_FLOAT, 0f)
            putFloat(IDLE_DRAIN_LAST_MAH, 0f)
            putLong(IDLE_DRAIN_LAST_TIMESTAMP, 0L)
            putBoolean(IDLE_DRAIN_IS_ACTIVE, false)
            putLong(IDLE_DRAIN_LAST_SCREEN_ON_REALTIME, nowRealtime)
            putInt(IDLE_DRAIN_LAST_END_LEVEL, level)
            putFloat(IDLE_DRAIN_LAST_END_CAPACITY, capacity)
            putLong(IDLE_DRAIN_BASELINE_REALTIME, nowRealtime)
            putLong(IDLE_DRAIN_BASELINE_UPTIME, nowUptime)
            putInt(IDLE_DRAIN_BASELINE_LEVEL, level)
            putFloat(IDLE_DRAIN_BASELINE_CAPACITY, capacity)
            putLong(IDLE_DRAIN_BASELINE_SCREEN_TIME_SEC, currentScreenTimeSec)
            putLong(IDLE_DRAIN_ACTIVE_SCREEN_DURATION_MS, 0L)
            putFloat(IDLE_DRAIN_ACTIVE_SCREEN_PERCENT, 0f)
            putFloat(IDLE_DRAIN_ACTIVE_SCREEN_MAH, 0f)
        }
        if (!isInteractive(context) && !isPluggedOrCharging(context)) {
            onScreenOff(context)
        }
    }

    fun getIdleDrainStats(context: Context): IdleDrainStats {
        syncWithSystemState(context)

        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val isCharging = isPluggedOrCharging(context)
        val interactive = isInteractive(context)
        val designCap = getAccurateDesignCapacity(context)
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val currentLevel = getBatteryLevel(context) ?: -1
        val currentCapacity = if (currentLevel in 0..100) {
            getAccurateCapacityMah(context, currentLevel)
        } else 0f

        var totalDurationMs = pref.getLong(IDLE_DRAIN_TOTAL_DURATION_MS, 0L)
        var totalDeepSleepMs = pref.getLong(IDLE_DRAIN_TOTAL_DEEP_SLEEP_MS, 0L)
        var totalAwakeMs = pref.getLong(IDLE_DRAIN_TOTAL_AWAKE_MS, 0L)
        var totalDrainPercent = pref.getFloat(
            IDLE_DRAIN_TOTAL_PERCENT_FLOAT,
            pref.getInt(IDLE_DRAIN_TOTAL_PERCENT, 0).toFloat()
        )
        var totalDrainMah = pref.getFloat(IDLE_DRAIN_TOTAL_MAH, 0f)
        var sessionsCount = pref.getInt(IDLE_DRAIN_SESSIONS_COUNT, 0)

        var lastDurationMs = pref.getLong(IDLE_DRAIN_LAST_DURATION_MS, 0L)
        var lastDeepSleepMs = pref.getLong(IDLE_DRAIN_LAST_DEEP_SLEEP_MS, 0L)
        var lastAwakeMs = pref.getLong(IDLE_DRAIN_LAST_AWAKE_MS, 0L)
        var lastDrainPercent = pref.getFloat(
            IDLE_DRAIN_LAST_PERCENT_FLOAT,
            pref.getInt(IDLE_DRAIN_LAST_PERCENT, 0).toFloat()
        )
        var lastDrainMah = pref.getFloat(IDLE_DRAIN_LAST_MAH, 0f)
        var lastTimestamp = pref.getLong(IDLE_DRAIN_LAST_TIMESTAMP, 0L)

        val isCurrentlyIdle = pref.getBoolean(IDLE_DRAIN_IS_ACTIVE, false)

        if (isCurrentlyIdle && !interactive && !isCharging) {
            val startRealtime = pref.getLong(IDLE_DRAIN_CURRENT_START_REALTIME, 0L)
            val startUptime = pref.getLong(IDLE_DRAIN_CURRENT_START_UPTIME, 0L)
            val startLevel = pref.getInt(IDLE_DRAIN_CURRENT_START_LEVEL, -1)
            val startCap = pref.getFloat(IDLE_DRAIN_CURRENT_START_CAPACITY, 0f)

            if (startRealtime in 1..<nowRealtime) {
                val curDur = nowRealtime - startRealtime
                val curAwake = (nowUptime - startUptime).coerceIn(0L, curDur)
                val curSleep = (curDur - curAwake).coerceAtLeast(0L)
                val (curPct, curMah) = computeSessionDrain(
                    context = context,
                    durationMs = curDur,
                    deepSleepMs = curSleep,
                    startLevel = startLevel,
                    endLevel = currentLevel,
                    startCapacity = startCap,
                    endCapacity = currentCapacity,
                    designCap = designCap
                )

                totalDurationMs += curDur
                totalDeepSleepMs += curSleep
                totalAwakeMs += curAwake
                totalDrainPercent += curPct
                totalDrainMah += curMah
            }
        }

        // Reconcile baseline only when service missed > 3 minutes of standby, separating screen-on drain.
        val baseRealtime = pref.getLong(IDLE_DRAIN_BASELINE_REALTIME, 0L)
        val baseUptime = pref.getLong(IDLE_DRAIN_BASELINE_UPTIME, 0L)
        val baseLevel = pref.getInt(IDLE_DRAIN_BASELINE_LEVEL, -1)
        val baseCap = pref.getFloat(IDLE_DRAIN_BASELINE_CAPACITY, 0f)
        val baseScreenSec = pref.getLong(IDLE_DRAIN_BASELINE_SCREEN_TIME_SEC, 0L)

        if (!isCharging && baseRealtime in 1..<nowRealtime && baseLevel in 0..100 && currentLevel in 0..100) {
            val elapsedSinceBaseMs = nowRealtime - baseRealtime
            val kernelDeepSleepMs = ((nowRealtime - baseRealtime) - (nowUptime - baseUptime))
                .coerceIn(0L, elapsedSinceBaseMs)

            val lastScreenOnRealtime = pref.getLong(IDLE_DRAIN_LAST_SCREEN_ON_REALTIME, 0L)
            val currentScreenOnSliceMs = if (interactive && lastScreenOnRealtime in baseRealtime..nowRealtime) {
                nowRealtime - lastScreenOnRealtime
            } else 0L

            val trackedActiveMs = pref.getLong(IDLE_DRAIN_ACTIVE_SCREEN_DURATION_MS, 0L) + currentScreenOnSliceMs
            val serviceScreenSec = BatteryLabService.instance?.screenTime ?: 0L
            val serviceActiveMs = if (serviceScreenSec >= baseScreenSec) {
                (serviceScreenSec - baseScreenSec) * 1000L
            } else 0L

            val maxPossibleActiveMs = (elapsedSinceBaseMs - kernelDeepSleepMs).coerceAtLeast(0L)
            val effectiveActiveMs = maxOf(trackedActiveMs, serviceActiveMs).coerceAtMost(maxPossibleActiveMs)
            val baselineStandbyMs = maxOf(kernelDeepSleepMs, elapsedSinceBaseMs - effectiveActiveMs)
            val untrackedStandbyMs = (baselineStandbyMs - totalDurationMs).coerceAtLeast(0L)

            if (untrackedStandbyMs >= 3 * 60_000L) {
                totalDurationMs = baselineStandbyMs
                if (kernelDeepSleepMs > totalDeepSleepMs) {
                    totalDeepSleepMs = kernelDeepSleepMs.coerceAtMost(totalDurationMs)
                }
                totalAwakeMs = (totalDurationMs - totalDeepSleepMs).coerceAtLeast(0L)

                val totalDropLvl = (baseLevel - currentLevel).coerceAtLeast(0)
                val totalDropRawMah = if (baseCap > 0f && currentCapacity > 0f) {
                    (baseCap - currentCapacity).coerceAtLeast(0f)
                } else 0f
                val totalDropMahPct = if (designCap > 0f) (totalDropRawMah / designCap) * 100f else 0f
                val overallDropPct = if (totalDropMahPct > 0f) totalDropMahPct else totalDropLvl.toFloat()

                val lastEndLevel = pref.getInt(IDLE_DRAIN_LAST_END_LEVEL, currentLevel)
                val lastEndCap = pref.getFloat(IDLE_DRAIN_LAST_END_CAPACITY, currentCapacity)
                val currentSliceDropLvl = if (interactive && lastEndLevel in currentLevel..100) {
                    (lastEndLevel - currentLevel).coerceAtLeast(0)
                } else 0
                val currentSliceDropMah = if (interactive && lastEndCap > currentCapacity && currentCapacity > 0f) {
                    (lastEndCap - currentCapacity).coerceAtLeast(0f)
                } else 0f
                val currentSliceDropMahPct = if (designCap > 0f) (currentSliceDropMah / designCap) * 100f else 0f
                val currentSliceDropPct = maxOf(currentSliceDropLvl.toFloat(), currentSliceDropMahPct)

                var activeDropPct = pref.getFloat(IDLE_DRAIN_ACTIVE_SCREEN_PERCENT, 0f) + currentSliceDropPct
                val untrackedActiveMs = (effectiveActiveMs - trackedActiveMs).coerceAtLeast(0L)
                if (untrackedActiveMs > 60_000L) {
                    val measuredActivePctPerHour = if (trackedActiveMs >= 180_000L && activeDropPct > 0.2f) {
                        (activeDropPct / (trackedActiveMs / 3_600_000f)).coerceIn(6.0f, 13.5f)
                    } else {
                        8.2f
                    }
                    activeDropPct += (untrackedActiveMs / 3_600_000f) * measuredActivePctPerHour
                }

                val sleepRatio = if (totalDurationMs > 0L) {
                    (totalDeepSleepMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                } else 0.8f
                val maxPlausibleTotalIdlePct = (totalDurationMs / 3_600_000f) *
                        (sleepRatio * 0.55f + (1f - sleepRatio) * 3.4f) + 0.4f

                val baselineIdleDropPct = (overallDropPct - activeDropPct)
                    .coerceIn(0f, maxPlausibleTotalIdlePct)

                if (baselineIdleDropPct > totalDrainPercent) {
                    totalDrainPercent = baselineIdleDropPct
                    totalDrainMah = (totalDrainPercent / 100f) * designCap
                }

                if (lastDurationMs < MIN_IDLE_DURATION_MS && totalDurationMs >= MIN_IDLE_DURATION_MS) {
                    lastDurationMs = totalDurationMs
                    lastDeepSleepMs = totalDeepSleepMs
                    lastAwakeMs = totalAwakeMs
                    lastDrainPercent = totalDrainPercent
                    lastDrainMah = totalDrainMah
                    if (lastTimestamp <= 0L) lastTimestamp = System.currentTimeMillis()
                    if (sessionsCount <= 0) sessionsCount = 1
                }
            }
        }

        if (totalDrainPercent > 0f && totalDrainMah <= 0f) {
            totalDrainMah = (totalDrainPercent / 100f) * designCap
        } else if (totalDrainMah > 0f && totalDrainPercent <= 0f && designCap > 0f) {
            totalDrainPercent = (totalDrainMah / designCap) * 100f
        }

        val hasHwCounter = hasHardwareChargeCounter(context) && totalDrainMah > 0.5f
        val diag = NativeCore.evaluateIdleDiagnostics(
            context = context,
            totalDurationMs = totalDurationMs,
            totalDeepSleepMs = totalDeepSleepMs,
            totalAwakeMs = totalAwakeMs,
            totalDrainPct = totalDrainPercent,
            totalDrainMah = totalDrainMah,
            lastDurationMs = lastDurationMs,
            lastDeepSleepMs = lastDeepSleepMs,
            lastDrainPct = lastDrainPercent,
            lastDrainMah = lastDrainMah,
            designCap = designCap,
            hasHwCounter = hasHwCounter
        )

        val totalDrainRatePercentPerHour = diag.totalDrainRatePercentPerHour
        val totalDrainRateMa = diag.totalDrainRateMa
        val deepSleepPercent = diag.deepSleepPercent
        val lastDrainRatePercentPerHour = diag.lastDrainRatePercentPerHour
        val lastDeepSleepPercent = diag.lastDeepSleepPercent

        val evalStatus: IdleDrainStatus
        val diagnosticSummary: String
        val recommendation: String

        val df = DecimalFormat("#.#")

        when (diag.statusCode) {
            1 -> {
                evalStatus = IdleDrainStatus.EXCELLENT
                diagnosticSummary = context.getString(
                    R.string.idle_drain_diag_excellent,
                    df.format(totalDrainRatePercentPerHour),
                    df.format(deepSleepPercent)
                )
                recommendation = context.getString(R.string.idle_drain_rec_excellent)
            }
            2 -> {
                evalStatus = IdleDrainStatus.NORMAL
                diagnosticSummary = context.getString(
                    R.string.idle_drain_diag_normal,
                    df.format(totalDrainRatePercentPerHour),
                    df.format(deepSleepPercent)
                )
                recommendation = context.getString(R.string.idle_drain_rec_normal)
            }
            3 -> {
                evalStatus = IdleDrainStatus.HIGH_DRAIN
                diagnosticSummary = context.getString(
                    R.string.idle_drain_diag_high,
                    df.format(totalDrainRatePercentPerHour),
                    df.format(deepSleepPercent)
                )
                recommendation = context.getString(
                    R.string.idle_drain_rec_high,
                    formatDuration(context, totalAwakeMs)
                )
            }
            else -> {
                evalStatus = IdleDrainStatus.NO_DATA
                diagnosticSummary = context.getString(R.string.idle_drain_diag_no_data)
                recommendation = context.getString(R.string.idle_drain_rec_no_data)
            }
        }

        return IdleDrainStats(
            totalDurationMs = totalDurationMs,
            totalDeepSleepMs = totalDeepSleepMs,
            totalAwakeMs = totalAwakeMs,
            totalDrainPercent = totalDrainPercent,
            totalDrainMah = totalDrainMah,
            sessionsCount = sessionsCount,
            totalDrainRatePercentPerHour = totalDrainRatePercentPerHour,
            totalDrainRateMa = totalDrainRateMa,
            deepSleepPercent = deepSleepPercent,
            lastDurationMs = lastDurationMs,
            lastDeepSleepMs = lastDeepSleepMs,
            lastAwakeMs = lastAwakeMs,
            lastDrainPercent = lastDrainPercent,
            lastDrainMah = lastDrainMah,
            lastDrainRatePercentPerHour = lastDrainRatePercentPerHour,
            lastDeepSleepPercent = lastDeepSleepPercent,
            lastTimestamp = lastTimestamp,
            isCurrentlyIdle = isCurrentlyIdle,
            isCharging = isCharging,
            status = evalStatus,
            diagnosticSummary = diagnosticSummary,
            recommendation = recommendation
        )
    }

    fun formatPercent(value: Float): String {
        val df = DecimalFormat("#.#")
        return df.format(value)
    }

    fun formatDuration(context: Context, durationMs: Long): String {
        val totalSecs = durationMs / 1000
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return when {
            hours > 0 -> context.getString(R.string.duration_hours_minutes, hours, mins)
            mins > 0 -> context.getString(R.string.duration_minutes_seconds, mins, secs)
            else -> context.getString(R.string.duration_seconds, secs)
        }
    }
}
