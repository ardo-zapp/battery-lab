package com.jacktor.batterylab.helpers

import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.R
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_CURRENT_START_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_CURRENT_START_LEVEL
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_CURRENT_START_REALTIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_CURRENT_START_UPTIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_IS_ACTIVE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_AWAKE_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_DEEP_SLEEP_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_MAH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_PERCENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_DURATION_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_LAST_TIMESTAMP
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_SESSIONS_COUNT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_TOTAL_AWAKE_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_TOTAL_DEEP_SLEEP_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_TOTAL_MAH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_TOTAL_PERCENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_TOTAL_DURATION_MS
import java.text.DecimalFormat

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
    val totalDrainPercent: Int,
    val totalDrainMah: Float,
    val sessionsCount: Int,
    val totalDrainRatePercentPerHour: Double,
    val totalDrainRateMa: Double,
    val deepSleepPercent: Double,
    val lastDurationMs: Long,
    val lastDeepSleepMs: Long,
    val lastAwakeMs: Long,
    val lastDrainPercent: Int,
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

    private const val MIN_IDLE_DURATION_MS = 20_000L // Minimal 20 detik untuk validasi sesi

    fun onScreenOff(context: Context) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging) {
            pref.edit { putBoolean(IDLE_DRAIN_IS_ACTIVE, false) }
            return
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val level = getBatteryLevel(context) ?: 0
        val capacity = getAvailableCapacity(context).toFloat()

        pref.edit {
            putLong(IDLE_DRAIN_CURRENT_START_REALTIME, nowRealtime)
            putLong(IDLE_DRAIN_CURRENT_START_UPTIME, nowUptime)
            putInt(IDLE_DRAIN_CURRENT_START_LEVEL, level)
            putFloat(IDLE_DRAIN_CURRENT_START_CAPACITY, capacity)
            putBoolean(IDLE_DRAIN_IS_ACTIVE, true)
        }
    }

    fun onScreenOn(context: Context) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val isActive = pref.getBoolean(IDLE_DRAIN_IS_ACTIVE, false)
        if (!isActive) return

        val endRealtime = SystemClock.elapsedRealtime()
        val endUptime = SystemClock.uptimeMillis()
        val startRealtime = pref.getLong(IDLE_DRAIN_CURRENT_START_REALTIME, 0L)
        val startUptime = pref.getLong(IDLE_DRAIN_CURRENT_START_UPTIME, 0L)

        pref.edit { putBoolean(IDLE_DRAIN_IS_ACTIVE, false) }

        if (startRealtime <= 0L || endRealtime <= startRealtime) return

        val durationMs = endRealtime - startRealtime
        if (durationMs < MIN_IDLE_DURATION_MS) return

        val awakeMs = if (endUptime >= startUptime) endUptime - startUptime else 0L
        val deepSleepMs = maxOf(0L, durationMs - awakeMs)

        val startLevel = pref.getInt(IDLE_DRAIN_CURRENT_START_LEVEL, -1)
        val endLevel = getBatteryLevel(context) ?: 0
        val drainPercent = if (startLevel > 0 && endLevel in 0..100) {
            maxOf(0, startLevel - endLevel)
        } else 0

        val startCapacity = pref.getFloat(IDLE_DRAIN_CURRENT_START_CAPACITY, 0f)
        val endCapacity = getAvailableCapacity(context).toFloat()
        val drainMah = if (startCapacity > 0f && endCapacity > 0f) {
            maxOf(0f, startCapacity - endCapacity)
        } else 0f

        val oldTotalDuration = pref.getLong(IDLE_DRAIN_TOTAL_DURATION_MS, 0L)
        val oldTotalDeepSleep = pref.getLong(IDLE_DRAIN_TOTAL_DEEP_SLEEP_MS, 0L)
        val oldTotalAwake = pref.getLong(IDLE_DRAIN_TOTAL_AWAKE_MS, 0L)
        val oldTotalPercent = pref.getInt(IDLE_DRAIN_TOTAL_PERCENT, 0)
        val oldTotalMah = pref.getFloat(IDLE_DRAIN_TOTAL_MAH, 0f)
        val oldSessions = pref.getInt(IDLE_DRAIN_SESSIONS_COUNT, 0)

        pref.edit {
            putLong(IDLE_DRAIN_LAST_DURATION_MS, durationMs)
            putLong(IDLE_DRAIN_LAST_DEEP_SLEEP_MS, deepSleepMs)
            putLong(IDLE_DRAIN_LAST_AWAKE_MS, awakeMs)
            putInt(IDLE_DRAIN_LAST_PERCENT, drainPercent)
            putFloat(IDLE_DRAIN_LAST_MAH, drainMah)
            putLong(IDLE_DRAIN_LAST_TIMESTAMP, System.currentTimeMillis())

            putLong(IDLE_DRAIN_TOTAL_DURATION_MS, oldTotalDuration + durationMs)
            putLong(IDLE_DRAIN_TOTAL_DEEP_SLEEP_MS, oldTotalDeepSleep + deepSleepMs)
            putLong(IDLE_DRAIN_TOTAL_AWAKE_MS, oldTotalAwake + awakeMs)
            putInt(IDLE_DRAIN_TOTAL_PERCENT, oldTotalPercent + drainPercent)
            putFloat(IDLE_DRAIN_TOTAL_MAH, oldTotalMah + drainMah)
            putInt(IDLE_DRAIN_SESSIONS_COUNT, oldSessions + 1)
        }
    }

    fun onPowerConnected(context: Context) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        pref.edit { putBoolean(IDLE_DRAIN_IS_ACTIVE, false) }
    }

    fun onPowerDisconnected(context: Context) {
        resetSinceUnplugged(context)
    }

    fun resetSinceUnplugged(context: Context) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        pref.edit {
            putLong(IDLE_DRAIN_TOTAL_DURATION_MS, 0L)
            putLong(IDLE_DRAIN_TOTAL_DEEP_SLEEP_MS, 0L)
            putLong(IDLE_DRAIN_TOTAL_AWAKE_MS, 0L)
            putInt(IDLE_DRAIN_TOTAL_PERCENT, 0)
            putFloat(IDLE_DRAIN_TOTAL_MAH, 0f)
            putInt(IDLE_DRAIN_SESSIONS_COUNT, 0)
            putBoolean(IDLE_DRAIN_IS_ACTIVE, false)
        }
    }

    fun resetAll(context: Context) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        pref.edit {
            putLong(IDLE_DRAIN_TOTAL_DURATION_MS, 0L)
            putLong(IDLE_DRAIN_TOTAL_DEEP_SLEEP_MS, 0L)
            putLong(IDLE_DRAIN_TOTAL_AWAKE_MS, 0L)
            putInt(IDLE_DRAIN_TOTAL_PERCENT, 0)
            putFloat(IDLE_DRAIN_TOTAL_MAH, 0f)
            putInt(IDLE_DRAIN_SESSIONS_COUNT, 0)
            putLong(IDLE_DRAIN_LAST_DURATION_MS, 0L)
            putLong(IDLE_DRAIN_LAST_DEEP_SLEEP_MS, 0L)
            putLong(IDLE_DRAIN_LAST_AWAKE_MS, 0L)
            putInt(IDLE_DRAIN_LAST_PERCENT, 0)
            putFloat(IDLE_DRAIN_LAST_MAH, 0f)
            putLong(IDLE_DRAIN_LAST_TIMESTAMP, 0L)
            putBoolean(IDLE_DRAIN_IS_ACTIVE, false)
        }
    }

    fun getIdleDrainStats(context: Context): IdleDrainStats {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val totalDurationMs = pref.getLong(IDLE_DRAIN_TOTAL_DURATION_MS, 0L)
        val totalDeepSleepMs = pref.getLong(IDLE_DRAIN_TOTAL_DEEP_SLEEP_MS, 0L)
        val totalAwakeMs = pref.getLong(IDLE_DRAIN_TOTAL_AWAKE_MS, 0L)
        val totalDrainPercent = pref.getInt(IDLE_DRAIN_TOTAL_PERCENT, 0)
        val totalDrainMah = pref.getFloat(IDLE_DRAIN_TOTAL_MAH, 0f)
        val sessionsCount = pref.getInt(IDLE_DRAIN_SESSIONS_COUNT, 0)

        val lastDurationMs = pref.getLong(IDLE_DRAIN_LAST_DURATION_MS, 0L)
        val lastDeepSleepMs = pref.getLong(IDLE_DRAIN_LAST_DEEP_SLEEP_MS, 0L)
        val lastAwakeMs = pref.getLong(IDLE_DRAIN_LAST_AWAKE_MS, 0L)
        val lastDrainPercent = pref.getInt(IDLE_DRAIN_LAST_PERCENT, 0)
        val lastDrainMah = pref.getFloat(IDLE_DRAIN_LAST_MAH, 0f)
        val lastTimestamp = pref.getLong(IDLE_DRAIN_LAST_TIMESTAMP, 0L)

        val totalHours = totalDurationMs / 3_600_000.0
        val totalDrainRatePercentPerHour = if (totalHours > 0.0) totalDrainPercent / totalHours else 0.0
        val totalDrainRateMa = if (totalHours > 0.0) totalDrainMah / totalHours else 0.0
        val deepSleepPercent = if (totalDurationMs > 0L) (totalDeepSleepMs.toDouble() / totalDurationMs) * 100.0 else 0.0

        val lastHours = lastDurationMs / 3_600_000.0
        val lastDrainRatePercentPerHour = if (lastHours > 0.0) lastDrainPercent / lastHours else 0.0
        val lastDeepSleepPercent = if (lastDurationMs > 0L) (lastDeepSleepMs.toDouble() / lastDurationMs) * 100.0 else 0.0

        val isCurrentlyIdle = pref.getBoolean(IDLE_DRAIN_IS_ACTIVE, false)

        val evalStatus: IdleDrainStatus
        val diagnosticSummary: String
        val recommendation: String

        val df = DecimalFormat("#.#")

        if (totalDurationMs < 60_000L) {
            evalStatus = IdleDrainStatus.NO_DATA
            diagnosticSummary = context.getString(R.string.idle_drain_diag_no_data)
            recommendation = context.getString(R.string.idle_drain_rec_no_data)
        } else if (totalDrainRatePercentPerHour < 0.8 && deepSleepPercent >= 80.0) {
            evalStatus = IdleDrainStatus.EXCELLENT
            diagnosticSummary = context.getString(
                R.string.idle_drain_diag_excellent,
                df.format(totalDrainRatePercentPerHour),
                df.format(deepSleepPercent)
            )
            recommendation = context.getString(R.string.idle_drain_rec_excellent)
        } else if (totalDrainRatePercentPerHour <= 1.5) {
            evalStatus = IdleDrainStatus.NORMAL
            diagnosticSummary = context.getString(
                R.string.idle_drain_diag_normal,
                df.format(totalDrainRatePercentPerHour),
                df.format(deepSleepPercent)
            )
            recommendation = context.getString(R.string.idle_drain_rec_normal)
        } else {
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
