package com.jacktor.batterylab.interfaces

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.MainApp.Companion.isPowerConnected
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databases.HistoryDB
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import com.jacktor.batterylab.helpers.HistoryHelper
import com.jacktor.batterylab.helpers.TimeHelper
import com.jacktor.batterylab.security.NativeCore
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.utilities.Constants.CHARGING_VOLTAGE_WATT
import com.jacktor.batterylab.utilities.Constants.NOMINAL_BATTERY_VOLTAGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_CHARGE_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_TEMP_CELSIUS_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_TEMP_FAHRENHEIT_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_ADDED_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_IN_WH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_COULOMB_ADDED_MAH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_PLUGGED_TYPE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_SESSION_AVG_CURRENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_SESSION_FAST_CHARGE_WATTS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_SESSION_IS_FAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_SESSION_MAX_CURRENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_SESSION_MIN_CURRENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_SESSION_PEAK_WATT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_SESSION_SAMPLE_COUNT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_SESSION_SUM_CURRENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_START_CAPACITY_MAH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_START_LEVEL
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_TIME_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CURRENT_CAPACITY_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DESIGN_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.FAST_CHARGE_WATTS_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IS_ALT_CALC_CHARGING_TIME_REMAINING
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IS_FAST_CHARGE_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.LAST_CHARGE_TIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.LAST_CHARGE_TIMESTAMP
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MAX_CHARGE_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MAX_TEMP_CELSIUS_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MAX_TEMP_FAHRENHEIT_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MIN_CHARGE_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MIN_TEMP_CELSIUS_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MIN_TEMP_FAHRENHEIT_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOMINAL_BATTERY_VOLTAGE_PREF
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_HISTORY_FOR_BATTERY_WEAR_NEW
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.ONLY_VALUES_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.PERCENT_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.PERCENT_ADDED_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.RESIDUAL_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SOURCE_OF_POWER_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.STATUS_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UNIT_OF_CHARGE_DISCHARGE_CURRENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UNIT_OF_MEASUREMENT_OF_available_capacity
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.VOLTAGE_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.VOLTAGE_UNIT
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@SuppressWarnings("PrivateApi")
interface BatteryInfoInterface {

    data class BatteryCellProfile(
        val isDualCell: Boolean,
        val seriesCellCount: Int,
        val chargeCounterMultiplier: Int
    )

    companion object {
        var tempAvailableCapacity = 0.0
        var capacityAdded = 0.0
        var coulombAddedMah = 0.0
        var liveEstimatedResidualMah = 0.0
        var tempBatteryLevelWith = 0
        var percentAdded = 0
        var residualCapacity = 0.0
        var batteryLevel = 0
        var tempBatteryLevel = 0

        var maxChargeCurrent = 0
        var averageChargeCurrent = 0
        var minChargeCurrent = 0

        var maxDischargeCurrent = 0
        var averageDischargeCurrent = 0
        var minDischargeCurrent = 0

        var maximumTemperature = 0.0
        var averageTemperature = 0.0
        var minimumTemperature = 0.0

        var chargeSampleCount = 0
        var runningChargeSum = 0.0

        var dischargeSampleCount = 0
        var runningDischargeSum = 0.0

        var tempSampleCount = 0
        var runningTempSum = 0.0

        var lastMeasuredVoltageV = 0.0
        @Volatile
        var lastSessionPluggedType = 0
        @Volatile
        var lastSessionStatus = BatteryManager.BATTERY_STATUS_UNKNOWN
        var detectedCellMultiplier: Int? = null
        private const val EMA_ALPHA = 0.2
        private var lastCellProfile: BatteryCellProfile? = null
        private var lastCellProfileTimeMs = 0L
        const val RESIDUAL_DUAL_CELL_VERIFIED_PREF = "residual_dc_v2"
        const val RESIDUAL_UPSTREAM_CALIBRATED_PREF = "residual_upstream_v1"

        var peakChargeWatt = 0.0
        var sessionMaxChargeCurrent = 0
        var sessionAverageChargeCurrent = 0
        var sessionMinChargeCurrent = 0
        var sessionChargeSampleCount = 0
        var sessionChargeSum = 0.0
        var sessionHasRealChargeSample = false
        var sessionPeakChargeWatt = 0.0
        var sessionIsFastChargeDetected = false
        var sessionFastChargeWatts = 0.0

        @JvmStatic
        fun persistChargingSessionStats(pref: SharedPreferences) {
            pref.edit {
                putInt(CHARGING_SESSION_MAX_CURRENT, sessionMaxChargeCurrent)
                putInt(CHARGING_SESSION_AVG_CURRENT, sessionAverageChargeCurrent)
                putInt(CHARGING_SESSION_MIN_CURRENT, sessionMinChargeCurrent)
                putInt(CHARGING_SESSION_SAMPLE_COUNT, sessionChargeSampleCount)
                putFloat(CHARGING_SESSION_SUM_CURRENT, sessionChargeSum.toFloat())
                putBoolean(CHARGING_SESSION_IS_FAST_CHARGE, sessionIsFastChargeDetected)
                putFloat(CHARGING_SESSION_FAST_CHARGE_WATTS, sessionFastChargeWatts.toFloat())
                putFloat(CHARGING_SESSION_PEAK_WATT, sessionPeakChargeWatt.toFloat())
            }
        }

        @JvmStatic
        fun restoreChargingSessionStats(pref: SharedPreferences) {
            val savedMax = pref.getInt(CHARGING_SESSION_MAX_CURRENT, 0).coerceAtLeast(0)
            val savedAvg = pref.getInt(CHARGING_SESSION_AVG_CURRENT, 0).coerceAtLeast(0)
            val savedMin = pref.getInt(CHARGING_SESSION_MIN_CURRENT, 0).coerceAtLeast(0)
            val savedCount = pref.getInt(CHARGING_SESSION_SAMPLE_COUNT, 0).coerceAtLeast(0)
            val savedSum = pref.getFloat(CHARGING_SESSION_SUM_CURRENT, 0f).toDouble().coerceAtLeast(0.0)
            val savedFast = pref.getBoolean(CHARGING_SESSION_IS_FAST_CHARGE, false)
            val savedFastWatts = pref.getFloat(CHARGING_SESSION_FAST_CHARGE_WATTS, 0f).toDouble().coerceAtLeast(0.0)
            val savedPeakWatt = pref.getFloat(CHARGING_SESSION_PEAK_WATT, 0f).toDouble().coerceAtLeast(0.0)

            if (savedMax > sessionMaxChargeCurrent) sessionMaxChargeCurrent = savedMax
            if (savedAvg > 0 && sessionAverageChargeCurrent <= 0) sessionAverageChargeCurrent = savedAvg
            if (savedMin > 0 && (sessionMinChargeCurrent == 0 || savedMin < sessionMinChargeCurrent)) {
                sessionMinChargeCurrent = savedMin
            }
            if (savedCount > sessionChargeSampleCount) sessionChargeSampleCount = savedCount
            if (savedSum > sessionChargeSum) sessionChargeSum = savedSum
            if (savedMax >= 50) sessionHasRealChargeSample = true
            if (savedFast) {
                sessionIsFastChargeDetected = true
                isAutoFastChargeDetected = true
            }
            if (savedFastWatts > sessionFastChargeWatts) sessionFastChargeWatts = savedFastWatts
            if (savedPeakWatt > sessionPeakChargeWatt) sessionPeakChargeWatt = savedPeakWatt

            if (maxChargeCurrent == 0 && sessionMaxChargeCurrent > 0) {
                maxChargeCurrent = sessionMaxChargeCurrent
                averageChargeCurrent = sessionAverageChargeCurrent
                minChargeCurrent = sessionMinChargeCurrent
                chargeSampleCount = sessionChargeSampleCount
                runningChargeSum = sessionChargeSum
                peakChargeWatt = sessionPeakChargeWatt
            }
        }

        @JvmStatic
        fun resetSessionChargeStats(pref: SharedPreferences? = null) {
            sessionMaxChargeCurrent = 0
            sessionAverageChargeCurrent = 0
            sessionMinChargeCurrent = 0
            sessionChargeSampleCount = 0
            sessionChargeSum = 0.0
            sessionHasRealChargeSample = false
            sessionPeakChargeWatt = 0.0
            sessionIsFastChargeDetected = false
            sessionFastChargeWatts = 0.0
            pref?.edit {
                remove(CHARGING_SESSION_MAX_CURRENT)
                remove(CHARGING_SESSION_AVG_CURRENT)
                remove(CHARGING_SESSION_MIN_CURRENT)
                remove(CHARGING_SESSION_SAMPLE_COUNT)
                remove(CHARGING_SESSION_SUM_CURRENT)
                remove(CHARGING_SESSION_IS_FAST_CHARGE)
                remove(CHARGING_SESSION_FAST_CHARGE_WATTS)
                remove(CHARGING_SESSION_PEAK_WATT)
            }
        }

        fun saveLastChargeSnapshot(
            info: BatteryInfoInterface,
            context: Context,
            pref: SharedPreferences,
            chargingTimeSec: Int,
            startLevel: Int,
            endLevel: Int,
            isFullCharge: Boolean
        ) {
            if (chargingTimeSec <= 1 && !isFullCharge) return
            val currentCapMah = info.getAvailableCapacity(context).toFloat()
            val resolvedPercent = if (percentAdded > 0) percentAdded else (endLevel - startLevel).coerceAtLeast(0)
            val resolvedCapAdded = if (capacityAdded > 0.0) {
                capacityAdded.toFloat()
            } else if (tempAvailableCapacity > 0.0) {
                var diff = currentCapMah - tempAvailableCapacity.toFloat()
                if (diff < 0f) diff /= -1f
                diff
            } else 0f

            val persistedMax = pref.getInt(CHARGING_SESSION_MAX_CURRENT, 0).coerceAtLeast(0)
            val persistedAvg = pref.getInt(CHARGING_SESSION_AVG_CURRENT, 0).coerceAtLeast(0)
            val persistedMin = pref.getInt(CHARGING_SESSION_MIN_CURRENT, 0).coerceAtLeast(0)
            val persistedFast = pref.getBoolean(CHARGING_SESSION_IS_FAST_CHARGE, false)
            val persistedFastWatts = pref.getFloat(CHARGING_SESSION_FAST_CHARGE_WATTS, 0f).toDouble().coerceAtLeast(0.0)

            val resolvedMaxCurrent = maxOf(sessionMaxChargeCurrent, maxChargeCurrent, persistedMax)
            val resolvedAvgCurrent = when {
                sessionAverageChargeCurrent > 0 -> sessionAverageChargeCurrent
                averageChargeCurrent > 0 -> averageChargeCurrent
                persistedAvg > 0 -> persistedAvg
                else -> resolvedMaxCurrent
            }
            val resolvedMinCurrent = when {
                sessionMinChargeCurrent > 0 -> sessionMinChargeCurrent
                minChargeCurrent > 0 -> minChargeCurrent
                persistedMin > 0 -> persistedMin
                else -> resolvedMaxCurrent
            }

            val fastMinMa = context.resources.getInteger(R.integer.fast_charge_min)
            val resolvedIsFast = sessionIsFastChargeDetected ||
                isAutoFastChargeDetected ||
                persistedFast ||
                resolvedMaxCurrent >= fastMinMa ||
                info.isFastCharge(context)
            val computedFastWatts = (resolvedMaxCurrent.toDouble() * CHARGING_VOLTAGE_WATT) / 1000.0
            val resolvedFastWatts = maxOf(
                sessionFastChargeWatts,
                persistedFastWatts,
                sessionPeakChargeWatt,
                computedFastWatts,
                if (resolvedIsFast) info.getFastChargeWattValue(context) else 0.0
            ).toFloat()

            // Avoid overwriting a real completed session when plugging briefly into an already-full battery
            val hasExistingSnapshot = pref.getLong(LAST_CHARGE_TIMESTAMP, 0L) > 0L
            val isNoOpAtFull = startLevel >= 100 && endLevel >= 100 &&
                resolvedPercent <= 0 && resolvedCapAdded < 1f && resolvedMaxCurrent < 50
            if (hasExistingSnapshot && (isNoOpAtFull || (
                chargingTimeSec < 5 &&
                resolvedPercent <= 0 &&
                resolvedCapAdded < 1f &&
                resolvedMaxCurrent <= 0
            ))) {
                return
            }

            val intent = try {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } catch (_: Throwable) {
                batteryIntent
            }
            val rawStatus = intent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                if (isFullCharge) BatteryManager.BATTERY_STATUS_FULL else BatteryManager.BATTERY_STATUS_CHARGING
            ) ?: if (isFullCharge) BatteryManager.BATTERY_STATUS_FULL else BatteryManager.BATTERY_STATUS_CHARGING
            val rawPlugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val effectivePlugged = when {
                info.isPlugged(rawPlugged) -> rawPlugged
                info.isPlugged(lastSessionPluggedType) -> lastSessionPluggedType
                else -> pref.getInt(CHARGING_PLUGGED_TYPE, 0).takeIf { info.isPlugged(it) }
                    ?: pref.getInt("last_plugged_type", 0).takeIf { info.isPlugged(it) }
                    ?: 0
            }
            val effectiveStatus = when {
                isFullCharge || rawStatus == BatteryManager.BATTERY_STATUS_FULL ->
                    BatteryManager.BATTERY_STATUS_FULL
                rawStatus == BatteryManager.BATTERY_STATUS_CHARGING ->
                    BatteryManager.BATTERY_STATUS_CHARGING
                lastSessionStatus == BatteryManager.BATTERY_STATUS_FULL ||
                    lastSessionStatus == BatteryManager.BATTERY_STATUS_CHARGING -> lastSessionStatus
                info.isPlugged(effectivePlugged) -> BatteryManager.BATTERY_STATUS_CHARGING
                else -> rawStatus
            }
            val statusCode = when (effectiveStatus) {
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                else -> "unknown"
            }

            val sourceCode = when (effectivePlugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                BatteryManager.BATTERY_PLUGGED_DOCK -> "dock"
                else -> "na"
            }

            val voltageMv = info.getVoltage(context)
            val voltageVolts = if (voltageMv > 0.0) (voltageMv / 1000.0).toFloat()
            else lastMeasuredVoltageV.toFloat()

            val maxTempC = maximumTemperature.toFloat()
            val avgTempC = averageTemperature.toFloat()
            val minTempC = minimumTemperature.toFloat()

            pref.edit {
                putInt(BATTERY_LEVEL_LAST_CHARGE, endLevel)
                putInt(CHARGING_TIME_LAST_CHARGE, chargingTimeSec)
                putFloat(CURRENT_CAPACITY_LAST_CHARGE, currentCapMah)
                putFloat(CAPACITY_ADDED_LAST_CHARGE, resolvedCapAdded)
                putInt(PERCENT_ADDED_LAST_CHARGE, resolvedPercent)
                putString(STATUS_LAST_CHARGE, statusCode)
                putString(SOURCE_OF_POWER_LAST_CHARGE, sourceCode)
                putBoolean(IS_FAST_CHARGE_LAST_CHARGE, resolvedIsFast)
                putFloat(FAST_CHARGE_WATTS_LAST_CHARGE, resolvedFastWatts)
                putInt(MAX_CHARGE_LAST_CHARGE, resolvedMaxCurrent)
                putInt(AVERAGE_CHARGE_LAST_CHARGE, resolvedAvgCurrent)
                putInt(MIN_CHARGE_LAST_CHARGE, resolvedMinCurrent)
                putFloat(MAX_TEMP_CELSIUS_LAST_CHARGE, maxTempC)
                putFloat(MAX_TEMP_FAHRENHEIT_LAST_CHARGE, info.getTemperatureInFahrenheit(maxTempC.toDouble()).toFloat())
                putFloat(AVERAGE_TEMP_CELSIUS_LAST_CHARGE, avgTempC)
                putFloat(AVERAGE_TEMP_FAHRENHEIT_LAST_CHARGE, info.getTemperatureInFahrenheit(avgTempC.toDouble()).toFloat())
                putFloat(MIN_TEMP_CELSIUS_LAST_CHARGE, minTempC)
                putFloat(MIN_TEMP_FAHRENHEIT_LAST_CHARGE, info.getTemperatureInFahrenheit(minTempC.toDouble()).toFloat())
                putFloat(VOLTAGE_LAST_CHARGE, voltageVolts)
                putLong(LAST_CHARGE_TIMESTAMP, System.currentTimeMillis())
            }
        }
        var peakChargeCurrentmA = 0
        var peakVoltageV = 0.0
        var isAutoFastChargeDetected = false
        private var lastChargeStatSampleMs = 0L
        private var lastDischargeStatSampleMs = 0L

        object Smooth {
            private const val DEFAULT_ALPHA = 0.2
            var emaChargeCurrentMa: Double = 0.0
            var emaDischargeCurrentMa: Double = 0.0
            var emaTempC: Double = 0.0
            fun update(prev: Double, value: Double, alpha: Double = DEFAULT_ALPHA): Double {
                return if (prev == 0.0) value else (alpha * value + (1 - alpha) * prev)
            }
        }

        object TimeEstimator {
            var stableDischargeMa: Double = 0.0

            fun reset() {
                stableDischargeMa = 0.0
            }
        }

        @JvmStatic
        fun resetPeakChargingPower() {
            peakChargeWatt = 0.0
            peakChargeCurrentmA = 0
            peakVoltageV = 0.0
        }

        @JvmStatic
        fun resetChargeStats() {
            maxChargeCurrent = 0
            averageChargeCurrent = 0
            minChargeCurrent = 0
            chargeSampleCount = 0
            runningChargeSum = 0.0
            Smooth.emaChargeCurrentMa = 0.0
            isAutoFastChargeDetected = false
            lastChargeStatSampleMs = 0L
            resetPeakChargingPower()
            TimeEstimator.reset()
            NativeCore.nativeResetFastChargeState()
            NativeCore.nativeResetTimeEstimatorState()
        }

        @JvmStatic
        fun resetDischargeStats() {
            maxDischargeCurrent = 0
            averageDischargeCurrent = 0
            minDischargeCurrent = 0
            dischargeSampleCount = 0
            runningDischargeSum = 0.0
            Smooth.emaDischargeCurrentMa = 0.0
            lastDischargeStatSampleMs = 0L
            TimeEstimator.reset()
            NativeCore.nativeResetTimeEstimatorState()
        }

        @JvmStatic
        fun resetTemperatureStats() {
            maximumTemperature = 0.0
            averageTemperature = 0.0
            minimumTemperature = 0.0
            tempSampleCount = 0
            runningTempSum = 0.0
            Smooth.emaTempC = 0.0
        }

        @JvmStatic
        fun getPeakChargeWattRaw(): Double =
            if (peakChargeWatt.isFinite()) peakChargeWatt else 0.0

        @JvmStatic
        fun getPeakMetrics(): Triple<Double, Int, Double> =
            Triple(getPeakChargeWattRaw(), peakChargeCurrentmA, peakVoltageV)
    }

    fun getDesignCapacity(context: Context): Int {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val minCap = context.resources.getInteger(R.integer.min_design_capacity)
        val maxCap = context.resources.getInteger(R.integer.max_design_capacity)

        val designCapacity = try {
            val powerProfileClass = "com.android.internal.os.PowerProfile"
            val mPowerProfile = Class.forName(powerProfileClass).getConstructor(
                Context::class.java
            ).newInstance(context)
            val cap = Class.forName(powerProfileClass).getMethod(
                "getBatteryCapacity"
            ).invoke(mPowerProfile) as Double
            cap.toInt()
        } catch (_: Throwable) {
            readDesignCapacityFromKernel() ?: pref.getInt(DESIGN_CAPACITY, minCap)
        }

        return when {
            designCapacity in minCap..maxCap -> designCapacity
            designCapacity < 0 -> (-designCapacity).coerceIn(minCap, maxCap)
            designCapacity > maxCap -> maxCap
            else -> minCap
        }
    }

    private fun readDesignCapacityFromKernel(): Int? {
        val paths = listOf(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design"
        )
        for (path in paths) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    val raw = f.bufferedReader().use { it.readLine() }?.trim()?.toLongOrNull() ?: continue
                    if (raw > 0) {
                        val capMah = if (raw > 100_000) (raw / 1000).toInt() else raw.toInt()
                        if (capMah in 500..30000) return capMah
                    }
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    fun getBatteryLevel(context: Context): Int? = try {
        (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
            ?: run {
                val bi = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (level >= 0 && scale > 0) {
                    ((level.toDouble() / scale) * 100.0).toInt().coerceIn(0, 100)
                } else null
            }
    } catch (_: RuntimeException) {
        val bi = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: RuntimeException) {
            null
        }
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level >= 0 && scale > 0) {
            ((level.toDouble() / scale) * 100.0).toInt().coerceIn(0, 100)
        } else 0
    }

    fun readRawInstantCurrentMa(context: Context): Double {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        var chargeCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (chargeCurrent < 0) chargeCurrent /= -1
        if (pref.getString(UNIT_OF_CHARGE_DISCHARGE_CURRENT, "μA") == "μA")
            chargeCurrent /= 1000
        return chargeCurrent.toDouble()
    }

    fun getChargeDischargeCurrent(context: Context): Int {
        return try {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            var chargeCurrent = batteryManager.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CURRENT_NOW
            )
            val status = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
            if (chargeCurrent < 0) chargeCurrent /= -1
            if (pref.getString(UNIT_OF_CHARGE_DISCHARGE_CURRENT, "μA") == "μA")
                chargeCurrent /= 1000

            getMaxAverageMinChargeDischargeCurrent(status, chargeCurrent)

            val isSessionFull = BatteryLabService.instance?.isFull == true
            if (status == BatteryManager.BATTERY_STATUS_CHARGING && !isSessionFull) {
                Smooth.emaChargeCurrentMa = chargeCurrent.toDouble()
                val v = readBatteryVoltageVolts(context)?.also { lastMeasuredVoltageV = it }
                    ?: if (lastMeasuredVoltageV > 0.0) lastMeasuredVoltageV else CHARGING_VOLTAGE_WATT
                val w = (chargeCurrent.toDouble() * CHARGING_VOLTAGE_WATT) / 1000.0
                if (w.isFinite() && chargeCurrent > 0) {
                    if (w > peakChargeWatt) {
                        peakChargeWatt = w
                        peakChargeCurrentmA = chargeCurrent
                        peakVoltageV = v
                    }
                    if (w > sessionPeakChargeWatt) {
                        sessionPeakChargeWatt = w
                    }
                }
                updateAutoFastChargeState(context)
            } else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) {
                Smooth.emaDischargeCurrentMa = chargeCurrent.toDouble()
            }

            chargeCurrent
        } catch (_: RuntimeException) {
            val status = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
            getMaxAverageMinChargeDischargeCurrent(status, 0)
            0
        }
    }

    private fun readCurrentNowFromKernel(): Int? {
        val paths = listOf(
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/bms/current_now"
        )
        for (path in paths) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    val raw = f.bufferedReader().use { it.readLine() }?.trim()?.toIntOrNull() ?: continue
                    if (raw != 0) return raw
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    fun getChargeDischargeCurrentInWatt(
        chargeDischargeCurrent: Int,
        isCharging: Boolean = false
    ): Double = (chargeDischargeCurrent.toDouble() *
        if (isCharging) CHARGING_VOLTAGE_WATT else NOMINAL_BATTERY_VOLTAGE) / 1000.0

    fun getFastCharge(context: Context): String {
        return if (isFastCharge(context))
            context.getString(R.string.fast_charge_yes, getFastChargeWatt(context))
        else context.getString(R.string.fast_charge_no)
    }

    fun getFastChargeOverlay(context: Context): String {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return if (!pref.getBoolean(
                ONLY_VALUES_OVERLAY,
                context.resources.getBoolean(R.bool.only_values_overlay)
            )
        )
            getFastCharge(context)
        else if (isFastCharge(context))
            context.getString(R.string.fast_charge_yes_overlay_only_values)
        else context.getString(R.string.fast_charge_no_overlay_only_values)
    }

    fun updateAutoFastChargeState(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val fastMin = context.resources.getInteger(R.integer.fast_charge_min)
        val peakCurrentMa = max(maxChargeCurrent, sessionMaxChargeCurrent)
        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isSessionFull = BatteryLabService.instance?.isFull == true
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        if (status == BatteryManager.BATTERY_STATUS_CHARGING && !isSessionFull && isPlugged(plugged)) {
            val minTurboCurrent = pref.getInt(
                DESIGN_CAPACITY, context.resources.getInteger(R.integer.min_design_capacity)
            ) - 250
            val voltageV = lastMeasuredVoltageV.takeIf { it > 0.0 } ?: CHARGING_VOLTAGE_WATT
            val nativeEval = try {
                NativeCore.evaluateFastCharge(
                    plugged = plugged,
                    status = status,
                    liveCurrentMa = Smooth.emaChargeCurrentMa,
                    voltageV = voltageV,
                    maxChargeCurrentMa = peakCurrentMa,
                    fastChargeMinMa = fastMin,
                    minTurboCurrentMa = minTurboCurrent.coerceAtLeast(1000),
                    peakChargeWatt = max(peakChargeWatt, sessionPeakChargeWatt)
                )
            } catch (_: Throwable) {
                null
            }
            if (peakCurrentMa >= fastMin || nativeEval?.isFastCharge == true) {
                isAutoFastChargeDetected = true
                sessionIsFastChargeDetected = true
            }
            val currentFastWatts = maxOf(
                sessionFastChargeWatts,
                sessionPeakChargeWatt,
                (peakCurrentMa.toDouble() * CHARGING_VOLTAGE_WATT) / 1000.0
            )
            if (sessionIsFastChargeDetected && currentFastWatts > sessionFastChargeWatts) {
                sessionFastChargeWatts = currentFastWatts
            }
        } else if (peakCurrentMa >= fastMin && (isPowerConnected || isSessionFull || status == BatteryManager.BATTERY_STATUS_FULL)) {
            isAutoFastChargeDetected = true
            sessionIsFastChargeDetected = true
        }

        val fast = isAutoFastChargeDetected || sessionIsFastChargeDetected || peakCurrentMa >= fastMin
        isAutoFastChargeDetected = fast
        return fast
    }

    fun isFastCharge(context: Context): Boolean = updateAutoFastChargeState(context)

    fun isTurboCharge(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val minTurboCurrent = pref.getInt(
            DESIGN_CAPACITY, context.resources.getInteger(R.integer.min_design_capacity)
        ) - 250
        val peakCurrentMa = max(maxChargeCurrent, sessionMaxChargeCurrent)
        return peakCurrentMa >= minTurboCurrent.coerceAtLeast(1000)
    }

    fun getFastChargeWattValue(context: Context): Double {
        val peakCurrentMa = max(maxChargeCurrent, sessionMaxChargeCurrent)
        return maxOf(
            (peakCurrentMa.toDouble() * CHARGING_VOLTAGE_WATT) / 1000.0,
            sessionFastChargeWatts,
            sessionPeakChargeWatt
        )
    }

    private fun getFastChargeWatt(context: Context): String =
        DecimalFormat("#.#").format(getFastChargeWattValue(context))

    private fun readBatteryVoltageVolts(context: Context): Double? {
        try {
            val f = File("/sys/class/power_supply/battery/voltage_now")
            if (f.exists()) {
                BufferedReader(FileReader(f)).use { br ->
                    val microV = br.readLine()?.trim()?.toLongOrNull()
                    if (microV != null && microV > 0) return microV / 1_000_000.0
                }
            }
        } catch (_: Exception) { /* ignore */
        }

        return try {
            val bi = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val mV = bi?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            if (mV > 0) {
                if (mV > 100_000) mV / 1_000_000.0 else mV / 1000.0
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun getMaxAverageMinChargeDischargeCurrent(status: Int?, chargeCurrent: Int) {
        when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> {
                maxDischargeCurrent = 0
                averageDischargeCurrent = 0
                minDischargeCurrent = 0

                if (BatteryLabService.instance?.isFull == true) return

                if (chargeCurrent > maxChargeCurrent) maxChargeCurrent = chargeCurrent

                if (chargeCurrent < minChargeCurrent && chargeCurrent < maxChargeCurrent)
                    minChargeCurrent = chargeCurrent
                else if (minChargeCurrent == 0 && chargeCurrent < maxChargeCurrent)
                    minChargeCurrent = chargeCurrent

                if (maxChargeCurrent > 0 && minChargeCurrent > 0)
                    averageChargeCurrent = (maxChargeCurrent + minChargeCurrent) / 2

                if (chargeCurrent > sessionMaxChargeCurrent) sessionMaxChargeCurrent = chargeCurrent
                if (chargeCurrent > 0) {
                    if (chargeCurrent < sessionMinChargeCurrent && chargeCurrent < sessionMaxChargeCurrent)
                        sessionMinChargeCurrent = chargeCurrent
                    else if (sessionMinChargeCurrent == 0 && chargeCurrent < sessionMaxChargeCurrent)
                        sessionMinChargeCurrent = chargeCurrent
                }
                if (sessionMaxChargeCurrent > 0 && sessionMinChargeCurrent > 0)
                    sessionAverageChargeCurrent = (sessionMaxChargeCurrent + sessionMinChargeCurrent) / 2
            }

            BatteryManager.BATTERY_STATUS_DISCHARGING -> {
                maxChargeCurrent = 0
                averageChargeCurrent = 0
                minChargeCurrent = 0

                if (chargeCurrent > maxDischargeCurrent) maxDischargeCurrent = chargeCurrent

                if (chargeCurrent < minDischargeCurrent && chargeCurrent < maxDischargeCurrent)
                    minDischargeCurrent = chargeCurrent
                else if (minDischargeCurrent == 0 && chargeCurrent < maxDischargeCurrent)
                    minDischargeCurrent = chargeCurrent

                if (maxDischargeCurrent > 0 && minDischargeCurrent > 0)
                    averageDischargeCurrent = (maxDischargeCurrent + minDischargeCurrent) / 2
            }

            BatteryManager.BATTERY_STATUS_UNKNOWN -> {
                maxChargeCurrent = 0
                averageChargeCurrent = 0
                minChargeCurrent = 0
                maxDischargeCurrent = 0
                averageDischargeCurrent = 0
                minDischargeCurrent = 0
            }
        }
    }

    fun getChargingCurrentLimit(context: Context): String? {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val f = File("/sys/class/power_supply/battery/constant_charge_current_max")
        var limit: String? = null
        return if (f.exists()) {
            try {
                BufferedReader(FileReader(f)).use { br -> limit = br.readLine()?.trim() }
                if (pref.getString(UNIT_OF_CHARGE_DISCHARGE_CURRENT, "μA") == "μA")
                    limit = ((limit?.toIntOrNull() ?: 0) / 1000).toString()
                limit
            } catch (_: IOException) {
                limit
            }
        } else null
    }

    fun getTemperature(context: Context): Int? {
        batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
    }

    fun getTemperatureInCelsius(context: Context): Double {
        batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temperatureInCelsius = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE, 0
        )?.toDouble() ?: 0.0
        return temperatureInCelsius / 10.0
    }

    fun getTemperatureInFahrenheit(context: Context) =
        (getTemperatureInCelsius(context) * 1.8) + 32.0

    fun getTemperatureInFahrenheit(temperatureInCelsius: Double) =
        (temperatureInCelsius * 1.8) + 32.0

    fun getMaximumTemperature(context: Context, temperature: Double): Double {
        val temperatureInCelsius = getTemperatureInCelsius(context)
        return if (temperatureInCelsius >= temperature || temperature == 0.0) temperatureInCelsius
        else temperature
    }

    fun getAverageTemperature(context: Context, temperatureMax: Double, temperatureMin: Double): Double =
        (temperatureMax + temperatureMin) / 2.0

    fun getMinimumTemperature(context: Context, temperature: Double): Double {
        val temperatureInCelsius = getTemperatureInCelsius(context)
        return if (temperatureInCelsius <= temperature || temperature == 0.0) temperatureInCelsius
        else temperature
    }

    private fun normalizeChargeCounterToMah(rawCc: Double, isMicroAh: Boolean): Double = when {
        rawCc > 50_000.0 -> rawCc / 1000.0
        rawCc > 0.0 -> if (isMicroAh) rawCc / 1000.0 else rawCc
        else -> 0.0
    }

    fun getBatteryCellProfile(context: Context): BatteryCellProfile {
        val nowMs = SystemClock.elapsedRealtime()
        val cached = lastCellProfile
        if (cached != null && (nowMs - lastCellProfileTimeMs) < 2000L) {
            return cached
        }

        val voltageMv = if (lastMeasuredVoltageV > 0.0) {
            lastMeasuredVoltageV * 1000.0
        } else {
            getVoltage(context)
        }

        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val isMicroAh = pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh"
        val designMah = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()
        val level = (getBatteryLevel(context) ?: 0).coerceIn(0, 100)

        var rawCcMah = 0.0
        var energyNwh = Long.MIN_VALUE
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val ccRaw = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.toDouble() ?: 0.0
            rawCcMah = normalizeChargeCounterToMah(ccRaw, isMicroAh)
            energyNwh = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) ?: Long.MIN_VALUE
        } catch (_: Throwable) {}

        val bi = batteryIntent ?: try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            null
        }
        val health = bi?.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_UNKNOWN
        ) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val prefCycles = pref.getFloat(NUMBER_OF_CYCLES, 0f)
        val androidOrKernelCycles = getNumberOfCyclesAndroid()?.toFloat() ?: 0f

        val eval = NativeCore.detectDualCellProfile(
            context = context,
            voltageMv = voltageMv,
            rawCcMah = rawCcMah,
            energyNwh = energyNwh,
            level = level,
            designMah = designMah,
            nominalVoltage = getNominalBatteryVoltage(context),
            prefCycles = prefCycles,
            androidCycles = androidOrKernelCycles,
            isHealthGood = health == BatteryManager.BATTERY_HEALTH_GOOD
        )
        detectedCellMultiplier = eval.seriesCellCount

        return BatteryCellProfile(
            isDualCell = eval.isDualCell,
            seriesCellCount = eval.seriesCellCount,
            chargeCounterMultiplier = eval.chargeCounterMultiplier
        ).also {
            lastCellProfile = it
            lastCellProfileTimeMs = nowMs
        }
    }

    private fun ensureUpstreamResidualMigration(pref: SharedPreferences) {
        if (!pref.getBoolean(RESIDUAL_UPSTREAM_CALIBRATED_PREF, false)) {
            pref.edit {
                remove(RESIDUAL_CAPACITY)
                putBoolean(RESIDUAL_UPSTREAM_CALIBRATED_PREF, true)
            }
        }
    }

    fun getResidualCapacityMah(context: Context): Double {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        ensureUpstreamResidualMigration(pref)
        var residual = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble() / if (pref.getString(
                UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh"
            ) == "μAh"
        ) 1000.0 else 100.0
        if (residual < 0.0) residual /= -1.0
        return residual
    }

    fun estimateLiveResidualCapacityMah(context: Context): Double = getResidualCapacityMah(context)

    fun getAvailableCapacity(context: Context): Double = try {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val isDualCellBattery = pref.getBoolean("is_dual_cell_battery", false) ||
            getBatteryCellProfile(context).chargeCounterMultiplier == 2
        val currentCapacity = if (isDualCellBattery)
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toDouble() * 2
        else
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toDouble()

        when {
            currentCapacity < 0 -> 0.001
            pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh" ->
                currentCapacity / 1000.0
            else -> currentCapacity
        }
    } catch (_: RuntimeException) {
        0.001
    }

    fun getNominalBatteryVoltage(context: Context): Double {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return pref.getString(NOMINAL_BATTERY_VOLTAGE_PREF, null)
            ?.toDoubleOrNull()
            ?.takeIf { it in 2.5..5.0 }
            ?: NOMINAL_BATTERY_VOLTAGE
    }

    fun getCapacityInWh(capacity: Double) = (capacity * NOMINAL_BATTERY_VOLTAGE) / 1000.0

    fun getCapacityInWh(context: Context, capacity: Double) =
        (capacity * getNominalBatteryVoltage(context)) / 1000.0

    private fun getConfiguredWearHistoryCount(context: Context): Int {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultCount = context.resources.getInteger(
            R.integer.number_of_history_for_battery_wear_new_default
        )
        return runCatching { pref.getString(NUMBER_OF_HISTORY_FOR_BATTERY_WEAR_NEW, null)?.toIntOrNull() }
            .getOrNull()
            ?: runCatching { pref.getInt(NUMBER_OF_HISTORY_FOR_BATTERY_WEAR_NEW, defaultCount) }
                .getOrDefault(defaultCount)
    }

    fun getWearSampleCountUsed(context: Context): Int {
        val configured = getConfiguredWearHistoryCount(context)
        val historyCount = HistoryHelper.getHistoryCount(context).toInt()
        return if (configured > 1 && historyCount > 1) min(configured, historyCount) else 1
    }

    fun getResidualCapacityAverage(context: Context, residualCapacity: Int): Int {
        val numberOfHistoryForBatteryWearNew = getConfiguredWearHistoryCount(context)
        return if (HistoryHelper.getHistoryCount(context) < numberOfHistoryForBatteryWearNew)
            residualCapacity
        else {
            val historyList = HistoryDB(context).readAll()
            var residualCapacityAverage = residualCapacity
            for (i in historyList.lastIndex downTo historyList.lastIndex -
                    numberOfHistoryForBatteryWearNew + 1) {
                residualCapacityAverage += historyList[i].residualCapacity
            }
            residualCapacityAverage /= numberOfHistoryForBatteryWearNew + 1
            residualCapacityAverage
        }
    }

    fun getEffectiveResidualCapacityMah(context: Context): Double = getResidualCapacityMah(context)

    fun getCapacityAdded(
        context: Context,
        isOverlay: Boolean = false,
        isOnlyValues: Boolean = false
    ): String {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val isCapacityInWh =
            pref.getBoolean(CAPACITY_IN_WH, context.resources.getBoolean(R.bool.capacity_in_wh))
        val capacityAddedPref = pref.getFloat(CAPACITY_ADDED, 0f).toDouble()

        batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return when (batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )) {
            BatteryManager.BATTERY_STATUS_CHARGING -> {
                if (tempAvailableCapacity <= 0.0) {
                    val savedStartCap = pref.getFloat(CHARGING_START_CAPACITY_MAH, 0f).toDouble()
                    tempAvailableCapacity = if (savedStartCap > 0.0) savedStartCap else getAvailableCapacity(context)
                }
                if (tempBatteryLevelWith <= 0) {
                    val savedStartLevel = pref.getInt(CHARGING_START_LEVEL, -1)
                    tempBatteryLevelWith = if (savedStartLevel >= 0) savedStartLevel else (getBatteryLevel(context) ?: 0)
                }
                percentAdded = (getBatteryLevel(context) ?: 0) - tempBatteryLevelWith
                if (percentAdded < 0) percentAdded = 0

                capacityAdded = getAvailableCapacity(context) - tempAvailableCapacity
                if (capacityAdded < 0) capacityAdded /= -1

                if (isCapacityInWh)
                    context.getString(
                        if (!isOverlay || !isOnlyValues) R.string.capacity_added_wh
                        else R.string.capacity_added_wh_overlay_only_values,
                        DecimalFormat("#.#").format(getCapacityInWh(capacityAdded)),
                        "$percentAdded%"
                    )
                else context.getString(
                    if (!isOverlay || !isOnlyValues) R.string.capacity_added
                    else R.string.capacity_added_overlay_only_values,
                    DecimalFormat("#.#").format(capacityAdded),
                    "$percentAdded%"
                )
            }

            else -> {
                val percentAddedPref = pref.getInt(PERCENT_ADDED, 0)
                if (isCapacityInWh)
                    context.getString(
                        if (!isOverlay || !isOnlyValues) R.string.capacity_added_wh
                        else R.string.capacity_added_wh_overlay_only_values,
                        DecimalFormat("#.#").format(getCapacityInWh(capacityAddedPref)),
                        "$percentAddedPref%"
                    )
                else context.getString(
                    if (!isOverlay || !isOnlyValues) R.string.capacity_added
                    else R.string.capacity_added_overlay_only_values,
                    DecimalFormat("#.#").format(capacityAddedPref),
                    "$percentAddedPref%"
                )
            }
        }
    }

    fun getVoltage(context: Context): Double {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        var voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.toDouble() ?: 0.0
        if (pref.getString(VOLTAGE_UNIT, "mV") == "μV") voltage /= 1000.0.pow(2.0)
        if (voltage > 0.0) lastMeasuredVoltageV = voltage / 1000.0
        return voltage
    }

    fun getBatteryHealth(context: Context): Int? {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        ensureUpstreamResidualMigration(pref)
        val designCapacity = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()
        val residualCapacity = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble() / if (pref.getString(
                UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh"
            ) == "μAh"
        ) 1000.0 else 100.0
        return if (residualCapacity > 0 && residualCapacity <= designCapacity) {
            when ((100.0 - ((residualCapacity / designCapacity) * 100.0)).toInt()) {
                in 0..4 -> R.string.battery_health_great
                in 5..11 -> R.string.battery_health_very_good
                in 12..19 -> R.string.battery_health_good
                in 20..29 -> R.string.battery_health_bad
                in 30..39 -> R.string.battery_health_very_bad
                else -> R.string.battery_health_replacement_required
            }
        } else if (residualCapacity > designCapacity) R.string.battery_health_great
        else if (residualCapacity < 0) R.string.battery_health_replacement_required else null
    }

    fun getBatteryHealthAndroid(context: Context): String {
        batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return when (batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_UNKNOWN
        )) {
            BatteryManager.BATTERY_HEALTH_GOOD -> context.getString(R.string.battery_health_good_android)
            BatteryManager.BATTERY_HEALTH_DEAD -> context.getString(R.string.battery_health_dead_android)
            BatteryManager.BATTERY_HEALTH_COLD -> context.getString(R.string.battery_health_cold_android)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> context.getString(R.string.battery_health_overheat_android)
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> context.getString(R.string.battery_health_over_voltage_android)
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> context.getString(R.string.battery_health_unspecified_failure_android)
            else -> context.getString(R.string.unknown)
        }
    }

    fun getResidualCapacity(
        context: Context,
        isOverlay: Boolean = false,
        isOnlyValues: Boolean = false
    ): String {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        ensureUpstreamResidualMigration(pref)
        val isCapacityInWh =
            pref.getBoolean(CAPACITY_IN_WH, context.resources.getBoolean(R.bool.capacity_in_wh))
        val designCapacity = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()

        residualCapacity = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble()
        residualCapacity /= if (pref.getString(
                UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh"
            ) == "μAh"
        ) 1000.0 else 100.0
        if (residualCapacity < 0.0) residualCapacity /= -1.0

        return if (isCapacityInWh) context.getString(
            if (!isOverlay || !isOnlyValues) R.string.residual_capacity_wh
            else R.string.residual_capacity_wh_overlay_only_values,
            DecimalFormat("#.#").format(getCapacityInWh(residualCapacity)),
            "${DecimalFormat("#.#").format((residualCapacity / designCapacity) * 100.0)}%"
        ) else context.getString(
            if (!isOverlay || !isOnlyValues) R.string.residual_capacity
            else R.string.residual_capacity_overlay_only_values,
            DecimalFormat("#.#").format(residualCapacity),
            "${DecimalFormat("#.#").format((residualCapacity / designCapacity) * 100.0)}%"
        )
    }

    fun getStatus(context: Context, extraStatus: Int): String = when (extraStatus) {
        BatteryManager.BATTERY_STATUS_DISCHARGING -> context.getString(R.string.discharging)
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> context.getString(R.string.not_charging)
        BatteryManager.BATTERY_STATUS_CHARGING -> context.getString(R.string.charging)
        BatteryManager.BATTERY_STATUS_FULL -> context.getString(R.string.full)
        else -> context.getString(R.string.unknown)
    }

    fun getSourceOfPower(
        context: Context,
        extraPlugged: Int,
        isOverlay: Boolean = false,
        isOnlyValues: Boolean = false
    ): String = when (extraPlugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> context.getString(
            if (!isOverlay || !isOnlyValues) R.string.source_of_power else R.string.source_of_power_overlay_only_values,
            context.getString(R.string.source_of_power_ac)
        )

        BatteryManager.BATTERY_PLUGGED_USB -> context.getString(
            if (!isOverlay || !isOnlyValues) R.string.source_of_power else R.string.source_of_power_overlay_only_values,
            context.getString(R.string.source_of_power_usb)
        )

        BatteryManager.BATTERY_PLUGGED_WIRELESS -> context.getString(
            if (!isOverlay || !isOnlyValues) R.string.source_of_power else R.string.source_of_power_overlay_only_values,
            context.getString(R.string.source_of_power_wireless)
        )

        BatteryManager.BATTERY_PLUGGED_DOCK -> context.getString(
            if (!isOverlay || !isOnlyValues) R.string.source_of_power else R.string.source_of_power_overlay_only_values,
            context.getString(R.string.source_of_power_dock)
        )

        else -> "N/A"
    }

    fun getBatteryWear(
        context: Context,
        isOverlay: Boolean = false,
        isOnlyValues: Boolean = false
    ): String {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        ensureUpstreamResidualMigration(pref)
        val designCapacity = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()
        val isCapacityInWh =
            pref.getBoolean(CAPACITY_IN_WH, context.resources.getBoolean(R.bool.capacity_in_wh))

        residualCapacity = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble()
        residualCapacity /= if (pref.getString(
                UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh"
            ) == "μAh"
        ) 1000.0 else 100.0
        if (residualCapacity < 0.0) residualCapacity /= -1.0

        return if (isCapacityInWh)
            context.getString(
                if (!isOverlay || !isOnlyValues) R.string.battery_wear_wh
                else R.string.battery_wear_wh_overlay_only_values,
                if (residualCapacity > 0 && residualCapacity < designCapacity)
                    "${DecimalFormat("#.#").format(100 - ((residualCapacity / designCapacity) * 100))}%"
                else "0%",
                if (residualCapacity > 0 && residualCapacity < designCapacity)
                    DecimalFormat("#.#").format(getCapacityInWh(designCapacity - residualCapacity))
                else "0"
            )
        else context.getString(
            if (!isOverlay || !isOnlyValues) R.string.battery_wear
            else R.string.battery_wear_overlay_only_values,
            if (residualCapacity > 0 && residualCapacity < designCapacity)
                "${DecimalFormat("#.#").format(100 - ((residualCapacity / designCapacity) * 100))}%"
            else "0%",
            if (residualCapacity > 0 && residualCapacity < designCapacity)
                DecimalFormat("#.#").format(designCapacity - residualCapacity)
            else "0"
        )
    }

    fun getChargingTime(
        context: Context,
        seconds: Int,
        isOverlay: Boolean = false,
        isOnlyValues: Boolean = false
    ): String {
        return context.getString(
            if (!isOverlay || !isOnlyValues) R.string.charging_time else R.string.charging_time_overlay_only_values,
            TimeHelper.getTime(seconds.toLong())
        )
    }

    fun getChargingTimeRemaining(context: Context): String {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        ensureUpstreamResidualMigration(pref)

        if (!pref.getBoolean(
                IS_ALT_CALC_CHARGING_TIME_REMAINING,
                context.resources.getBoolean(R.bool.is_alt_calc_charging_time_remaining)
            )
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val chargingTimeRemaining = batteryManager.computeChargeTimeRemaining() / 1000
                return TimeHelper.getTime(chargingTimeRemaining.coerceAtLeast(0L))
            }
        }

        var chargingTimeRemaining: Double
        val batteryLevel = getBatteryLevel(context) ?: 0
        val currentCapacity = getAvailableCapacity(context)
        val residualCapacity = if (pref.getString(
                UNIT_OF_MEASUREMENT_OF_available_capacity,
                "μAh"
            ) == "μAh"
        ) pref.getInt(RESIDUAL_CAPACITY, 0).toDouble() / 1000.0
        else pref.getInt(RESIDUAL_CAPACITY, 0).toDouble() / 100.0

        val chargeDischargeCurrent = getChargeDischargeCurrent(context).toDouble()

        return if (currentCapacity > 0.0 && currentCapacity < residualCapacity) {
            val capacity = residualCapacity - currentCapacity
            if (chargeDischargeCurrent > 0.0) {
                chargingTimeRemaining = if (chargeDischargeCurrent > 500.0) {
                    (capacity / chargeDischargeCurrent) * if (isTurboCharge(context)) {
                        if (chargeDischargeCurrent >= 1500) 1.2
                        else if (chargeDischargeCurrent >= 1000) 0.8 else 0.4
                    } else 1.1
                } else capacity / chargeDischargeCurrent
                chargingTimeRemaining *= 3600.0
                TimeHelper.getTime(chargingTimeRemaining.toLong())
            } else context.getString(R.string.unknown)
        } else if (currentCapacity > 0.0 && (currentCapacity >= residualCapacity || currentCapacity >= pref.getInt(
                DESIGN_CAPACITY, context.resources.getInteger(R.integer.min_design_capacity)
            ).toDouble()) && residualCapacity > 0.0
        ) {
            context.getString(R.string.unknown)
        } else if (currentCapacity > 0.0 && residualCapacity == 0.0) {
            val capacity = pref.getInt(
                DESIGN_CAPACITY, context.resources.getInteger(R.integer.min_design_capacity)
            ) - currentCapacity
            if (chargeDischargeCurrent > 0.0) {
                chargingTimeRemaining = if (chargeDischargeCurrent > 500.0)
                    (capacity / chargeDischargeCurrent) * 1.1 else capacity / chargeDischargeCurrent
                chargingTimeRemaining *= 3600.0
                TimeHelper.getTime(chargingTimeRemaining.toLong())
            } else context.getString(R.string.unknown)
        } else {
            val designCapacity = pref.getInt(
                DESIGN_CAPACITY, context.resources.getInteger(R.integer.min_design_capacity)
            ).toDouble()
            val capacity = designCapacity - (designCapacity * (batteryLevel.toDouble() / 100.0))
            if (chargeDischargeCurrent > 0.0) {
                chargingTimeRemaining = if (chargeDischargeCurrent > 500.0)
                    (capacity / chargeDischargeCurrent) * 1.1
                else capacity / chargeDischargeCurrent
                chargingTimeRemaining *= 3600.0
                TimeHelper.getTime(chargingTimeRemaining.toLong())
            } else context.getString(R.string.unknown)
        }
    }

    fun getRemainingBatteryTime(context: Context): String {
        val currentCapacity = getAvailableCapacity(context)
        val dischargeCurrent = getChargeDischargeCurrent(context)
        return if (dischargeCurrent > 0) {
            val denominator = (dischargeCurrent + averageDischargeCurrent + minDischargeCurrent) / 3
            if (denominator > 0) {
                val remainingBatteryTime = ((currentCapacity / denominator) * 3600).toLong()
                TimeHelper.getTime(remainingBatteryTime)
            } else context.getString(R.string.unknown)
        } else context.getString(R.string.unknown)
    }

    fun getLastChargeTime(context: Context): String {
        val seconds = PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(LAST_CHARGE_TIME, 0).toLong()
        return TimeHelper.getTime(seconds)
    }

    fun getNumberOfCyclesAndroid(context: Context? = null): Int? {
        val intent = if (context != null) {
            try {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    ?.also { batteryIntent = it }
            } catch (_: Throwable) {
                batteryIntent
            }
        } else batteryIntent

        val fromIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (intent?.hasExtra(BatteryManager.EXTRA_CYCLE_COUNT) == true) {
                intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
            } else null
        } else null

        return fromIntent ?: readCycleCountFromKernel()
    }

    fun getHardwareEnergyCounterWh(context: Context): Double? {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
            val energyNwh = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            if (energyNwh in 100_000_000L..50_000_000_000L) {
                energyNwh.toDouble() / 1_000_000_000.0
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    fun getAndroidCapacityLevelRes(context: Context): Int? {
        if (Build.VERSION.SDK_INT < 36) return null
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.also { batteryIntent = it }
        } catch (_: Throwable) {
            batteryIntent
        } ?: return null
        if (!intent.hasExtra(BatteryManager.EXTRA_CAPACITY_LEVEL)) return null

        return when (intent.getIntExtra(BatteryManager.EXTRA_CAPACITY_LEVEL, BatteryManager.BATTERY_CAPACITY_LEVEL_UNSUPPORTED)) {
            BatteryManager.BATTERY_CAPACITY_LEVEL_CRITICAL -> R.string.capacity_level_critical
            BatteryManager.BATTERY_CAPACITY_LEVEL_LOW -> R.string.capacity_level_low
            BatteryManager.BATTERY_CAPACITY_LEVEL_NORMAL -> R.string.capacity_level_normal
            BatteryManager.BATTERY_CAPACITY_LEVEL_HIGH -> R.string.capacity_level_high
            BatteryManager.BATTERY_CAPACITY_LEVEL_FULL -> R.string.capacity_level_full
            else -> null
        }
    }

    fun getAndroidChargingPolicyRes(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.also { batteryIntent = it }
        } catch (_: Throwable) {
            batteryIntent
        } ?: return null
        if (!intent.hasExtra(BatteryManager.EXTRA_CHARGING_STATUS)) return null

        return when (intent.getIntExtra(BatteryManager.EXTRA_CHARGING_STATUS, -1)) {
            1 -> R.string.charging_policy_default
            2 -> R.string.charging_policy_adaptive_aon
            3 -> R.string.charging_policy_adaptive_ac
            4 -> R.string.charging_policy_adaptive_longlife
            else -> null
        }
    }

    private fun readCycleCountFromKernel(): Int? {
        val candidates = listOf(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/battery/cycle_counts",
            "/sys/class/power_supply/battery/batt_cycle",
            "/sys/class/power_supply/battery/fg_cycle",
            "/sys/class/power_supply/bms/cycle_count",
            "/sys/class/power_supply/max170xx_battery/cycle_count",
            "/sys/class/power_supply/max17048/cycle_count",
            "/sys/class/power_supply/fg/cycle_count"
        )

        for (path in candidates) {
            try {
                val f = File(path)
                if (!f.exists() || !f.canRead()) continue
                val raw = f.bufferedReader().use { it.readLine() }?.trim().orEmpty()
                if (raw.isEmpty()) continue

                val num = raw.split(Regex("\\s+|:")).lastOrNull()?.toDoubleOrNull()
                    ?: raw.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                    ?: continue

                val normalized = when {
                    num >= 1.0 -> num
                    num in 0.01..0.99 -> num * 100.0
                    else -> num
                }

                val intVal = normalized.toInt()
                if (intVal in 0..100000) return intVal
            } catch (_: Throwable) {
                // skip
            }
        }
        return null
    }

    fun isPlugged(extraPlugged: Int): Boolean = when (extraPlugged) {
        BatteryManager.BATTERY_PLUGGED_AC,
        BatteryManager.BATTERY_PLUGGED_USB,
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> true

        else -> false
    }

    fun isChargingStatus(status: Int): Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING

    fun isPowerSaveEnabled(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isPowerSaveMode
    }

    @SuppressLint("InlinedApi")
    fun getThermalStatusLabel(context: Context): String {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pm.currentThermalStatus
        else PowerManager.THERMAL_STATUS_NONE
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> context.getString(R.string.thermal_none)
            PowerManager.THERMAL_STATUS_LIGHT -> context.getString(R.string.thermal_light)
            PowerManager.THERMAL_STATUS_MODERATE -> context.getString(R.string.thermal_moderate)
            PowerManager.THERMAL_STATUS_SEVERE -> context.getString(R.string.thermal_severe)
            PowerManager.THERMAL_STATUS_CRITICAL -> context.getString(R.string.thermal_critical)
            PowerManager.THERMAL_STATUS_EMERGENCY -> context.getString(R.string.thermal_emergency)
            PowerManager.THERMAL_STATUS_SHUTDOWN -> context.getString(R.string.thermal_shutdown)
            else -> context.getString(R.string.unknown)
        }
    }

    @SuppressLint("InlinedApi")
    fun getThermalTintRes(context: Context): Int {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pm.currentThermalStatus
        else PowerManager.THERMAL_STATUS_NONE

        return when (status) {
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT -> R.color.battery_info_temp_normal

            PowerManager.THERMAL_STATUS_MODERATE -> R.color.battery_info_temp_warm
            PowerManager.THERMAL_STATUS_SEVERE -> R.color.battery_info_temp_hot
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> R.color.battery_info_temp_very_hot

            else -> R.color.battery_info_temp_normal
        }
    }

    fun getTemperatureTintRes(tempC: Double): Int {
        val t = (tempC * 10.0).toInt()
        return when {
            t >= 480 -> R.color.battery_info_temp_very_hot
            t >= 420 -> R.color.battery_info_temp_hot
            t >= 380 -> R.color.battery_info_temp_warm
            t >= 150 -> R.color.battery_info_temp_normal
            else -> R.color.battery_info_temp_cold
        }
    }

    fun getTemperatureTintRes(context: Context): Int {
        return getTemperatureTintRes(getTemperatureInCelsius(context))
    }

    fun resolveBatteryCellMultiplier(voltageMv: Double): Int {
        val m = NativeCore.nativeResolveCellMultiplier(voltageMv)
        if (detectedCellMultiplier != m) {
            detectedCellMultiplier = m
            lastCellProfile = null
        }
        return m
    }

    fun getVoltageTintRes(
        voltageMv: Double,
        health: Int = BatteryManager.BATTERY_HEALTH_UNKNOWN
    ): Int {
        when (health) {
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE,
            BatteryManager.BATTERY_HEALTH_DEAD,
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> {
                return R.color.battery_info_voltage_danger
            }
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> {
                return R.color.battery_info_voltage_warning
            }
        }

        if (voltageMv <= 0.0) {
            return R.color.battery_info_voltage_normal
        }

        val multiplier = resolveBatteryCellMultiplier(voltageMv)
        val cellVoltageMv = (voltageMv / multiplier).toInt()

        return when {
            cellVoltageMv < 3400 || cellVoltageMv > 4550 -> R.color.battery_info_voltage_danger
            cellVoltageMv in 3400..3599 || cellVoltageMv in 4481..4550 -> R.color.battery_info_voltage_warning
            else -> R.color.battery_info_voltage_normal
        }
    }

    fun getVoltageTintRes(context: Context): Int {
        val bi = batteryIntent ?: try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            null
        }
        val health = bi?.getIntExtra(
            BatteryManager.EXTRA_HEALTH,
            BatteryManager.BATTERY_HEALTH_UNKNOWN
        ) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        return getVoltageTintRes(getVoltage(context), health)
    }

    fun getBatteryHealthTintRes(context: Context): Int {
        val bi = batteryIntent ?: try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            null
        }
        val health = bi?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN

        return when (health) {
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.color.battery_info_temp_very_hot
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.color.battery_info_temp_hot
            BatteryManager.BATTERY_HEALTH_DEAD -> R.color.battery_info_level_low
            BatteryManager.BATTERY_HEALTH_COLD -> R.color.battery_info_temp_cold
            BatteryManager.BATTERY_HEALTH_GOOD -> R.color.battery_info_health
            else -> R.color.battery_info_health
        }
    }

    fun getTimeTintRes(level: Int, isCharging: Boolean, isFull: Boolean = false): Int {
        return BatteryLevelHelper.batteryLevelTintRes(level, isCharging, isFull)
    }
}
