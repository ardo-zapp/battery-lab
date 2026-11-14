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
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databases.HistoryDB
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import com.jacktor.batterylab.helpers.HistoryHelper
import com.jacktor.batterylab.helpers.TimeHelper
import com.jacktor.batterylab.security.NativeCore
import com.jacktor.batterylab.utilities.Constants.CHARGING_VOLTAGE_WATT
import com.jacktor.batterylab.utilities.Constants.NOMINAL_BATTERY_VOLTAGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_CHARGE_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_TEMP_CELSIUS_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_TEMP_FAHRENHEIT_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_ADDED_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_IN_WH
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
        var detectedCellMultiplier: Int? = null
        private const val EMA_ALPHA = 0.2
        private var lastCellProfile: BatteryCellProfile? = null
        private var lastCellProfileTimeMs = 0L
        const val RESIDUAL_DUAL_CELL_VERIFIED_PREF = "residual_dc_v2"

        var peakChargeWatt = 0.0

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
            val designMah = pref.getInt(
                DESIGN_CAPACITY,
                context.resources.getInteger(R.integer.min_design_capacity)
            ).toDouble()
            val currentCapMah = info.getAvailableCapacity(context).toFloat()
            val resolvedPercent = if (percentAdded > 0) percentAdded else (endLevel - startLevel).coerceAtLeast(0)
            val resolvedCapAdded = when {
                capacityAdded > 0.0 -> capacityAdded
                currentCapMah > 1f && tempAvailableCapacity > 1.0 -> (currentCapMah - tempAvailableCapacity).coerceAtLeast(0.0)
                else -> (resolvedPercent / 100.0) * designMah
            }.toFloat()

            val intent = try {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } catch (_: Throwable) {
                batteryIntent
            }
            val rawStatus = intent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                if (isFullCharge) BatteryManager.BATTERY_STATUS_FULL else BatteryManager.BATTERY_STATUS_CHARGING
            ) ?: if (isFullCharge) BatteryManager.BATTERY_STATUS_FULL else BatteryManager.BATTERY_STATUS_CHARGING
            val statusCode = when {
                isFullCharge -> "full"
                rawStatus == BatteryManager.BATTERY_STATUS_FULL -> "full"
                rawStatus == BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                rawStatus == BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                rawStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                else -> "unknown"
            }

            val rawPlugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val sourceCode = when (rawPlugged) {
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
                putBoolean(IS_FAST_CHARGE_LAST_CHARGE, info.isFastCharge(context))
                putFloat(FAST_CHARGE_WATTS_LAST_CHARGE, info.getFastChargeWattValue(context).toFloat())
                putInt(MAX_CHARGE_LAST_CHARGE, maxChargeCurrent)
                putInt(AVERAGE_CHARGE_LAST_CHARGE, averageChargeCurrent)
                putInt(MIN_CHARGE_LAST_CHARGE, minChargeCurrent)
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
        var raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) // µA or mA
        if (raw == 0 || raw == Int.MIN_VALUE) {
            raw = readCurrentNowFromKernel() ?: 0
        }

        val isUnitMicroAmp = pref.getString(UNIT_OF_CHARGE_DISCHARGE_CURRENT, "μA") == "μA"
        return when {
            abs(raw) > 50_000 -> abs(raw) / 1000.0 // definitely in µA
            isUnitMicroAmp -> abs(raw) / 1000.0
            else -> abs(raw).toDouble()
        }
    }

    fun getChargeDischargeCurrent(context: Context): Int {
        return try {
            var currentMa = readRawInstantCurrentMa(context)

            batteryIntent = batteryIntent ?: context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val status = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )

            val v = readBatteryVoltageVolts(context)?.also { lastMeasuredVoltageV = it }
                ?: if (lastMeasuredVoltageV > 0) lastMeasuredVoltageV else (if (status == BatteryManager.BATTERY_STATUS_CHARGING) CHARGING_VOLTAGE_WATT else NOMINAL_BATTERY_VOLTAGE)

            val nowMs = SystemClock.elapsedRealtime()
            when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> {
                    val shouldUpdateSample =
                        Smooth.emaChargeCurrentMa == 0.0 || (nowMs - lastChargeStatSampleMs >= 450L)
                    if (shouldUpdateSample) {
                        lastChargeStatSampleMs = nowMs
                        currentMa = Smooth.update(Smooth.emaChargeCurrentMa, currentMa).also {
                            Smooth.emaChargeCurrentMa = it
                        }
                        val w = (currentMa * v) / 1000.0
                        if (w.isFinite() && w > peakChargeWatt) {
                            peakChargeWatt = w
                            peakChargeCurrentmA = currentMa.toInt()
                            peakVoltageV = v
                        }
                        val result = currentMa.toInt().coerceAtLeast(0)
                        getMaxAverageMinChargeDischargeCurrent(status, result)
                        updateAutoFastChargeState(context)
                        return result
                    }
                    return Smooth.emaChargeCurrentMa.toInt().coerceAtLeast(0)
                }

                BatteryManager.BATTERY_STATUS_DISCHARGING -> {
                    val shouldUpdateSample =
                        Smooth.emaDischargeCurrentMa == 0.0 || (nowMs - lastDischargeStatSampleMs >= 450L)
                    if (shouldUpdateSample) {
                        lastDischargeStatSampleMs = nowMs
                        currentMa = Smooth.update(Smooth.emaDischargeCurrentMa, currentMa).also {
                            Smooth.emaDischargeCurrentMa = it
                        }
                        val result = currentMa.toInt().coerceAtLeast(0)
                        getMaxAverageMinChargeDischargeCurrent(status, result)
                        return result
                    }
                    return Smooth.emaDischargeCurrentMa.toInt().coerceAtLeast(0)
                }
            }

            val result = currentMa.toInt().coerceAtLeast(0)
            getMaxAverageMinChargeDischargeCurrent(status, result)
            result
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
    ): Double {
        val voltage = if (lastMeasuredVoltageV in 2.5..12.0) lastMeasuredVoltageV
        else (if (isCharging) CHARGING_VOLTAGE_WATT else NOMINAL_BATTERY_VOLTAGE)

        return (chargeDischargeCurrent.toDouble() * voltage) / 1000.0
    }

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
        val bi = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            batteryIntent
        } ?: batteryIntent

        val status = bi?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        val liveCurrentMa = if (Smooth.emaChargeCurrentMa > 0.0) Smooth.emaChargeCurrentMa
        else readRawInstantCurrentMa(context)
        val voltageV = readBatteryVoltageVolts(context)?.also { lastMeasuredVoltageV = it }
            ?: if (lastMeasuredVoltageV > 0.0) lastMeasuredVoltageV else CHARGING_VOLTAGE_WATT
        val fastChargeMinMa = context.resources.getInteger(R.integer.fast_charge_min)
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val minTurboCurrent = pref.getInt(
            DESIGN_CAPACITY, context.resources.getInteger(R.integer.min_design_capacity) - 250
        )

        val eval = NativeCore.evaluateFastCharge(
            plugged = plugged,
            status = status,
            liveCurrentMa = liveCurrentMa,
            voltageV = voltageV,
            maxChargeCurrentMa = maxChargeCurrent,
            fastChargeMinMa = fastChargeMinMa,
            minTurboCurrentMa = minTurboCurrent,
            peakChargeWatt = peakChargeWatt
        )
        isAutoFastChargeDetected = eval.isFastCharge
        return eval.isFastCharge
    }

    fun isFastCharge(context: Context): Boolean = updateAutoFastChargeState(context)

    fun isTurboCharge(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val minTurboCurrent = pref.getInt(
            DESIGN_CAPACITY, context.resources.getInteger(R.integer.min_design_capacity) - 250
        )
        val bi = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            batteryIntent
        } ?: batteryIntent
        val status = bi?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val liveCurrentMa = if (Smooth.emaChargeCurrentMa > 0.0) Smooth.emaChargeCurrentMa
        else readRawInstantCurrentMa(context)
        val voltageV = readBatteryVoltageVolts(context)?.also { lastMeasuredVoltageV = it }
            ?: if (lastMeasuredVoltageV > 0.0) lastMeasuredVoltageV else CHARGING_VOLTAGE_WATT
        val fastChargeMinMa = context.resources.getInteger(R.integer.fast_charge_min)

        return NativeCore.evaluateFastCharge(
            plugged = plugged,
            status = status,
            liveCurrentMa = liveCurrentMa,
            voltageV = voltageV,
            maxChargeCurrentMa = maxChargeCurrent,
            fastChargeMinMa = fastChargeMinMa,
            minTurboCurrentMa = minTurboCurrent,
            peakChargeWatt = peakChargeWatt
        ).isTurboCharge
    }

    fun getFastChargeWattValue(context: Context): Double {
        val samplemA = runCatching {
            val rawNow = readRawInstantCurrentMa(context)
            when {
                Smooth.emaChargeCurrentMa > 0.0 -> Smooth.emaChargeCurrentMa
                rawNow > 0.0 -> rawNow
                averageChargeCurrent > 0 -> averageChargeCurrent.toDouble()
                else -> maxChargeCurrent.toDouble()
            }
        }.getOrDefault(
            if (averageChargeCurrent > 0) averageChargeCurrent.toDouble()
            else maxChargeCurrent.toDouble()
        ).coerceAtLeast(0.0)

        val voltageV = readBatteryVoltageVolts(context)?.also { lastMeasuredVoltageV = it }
            ?: if (lastMeasuredVoltageV > 0) lastMeasuredVoltageV else CHARGING_VOLTAGE_WATT

        val watt = NativeCore.evaluateFastCharge(
            plugged = 1,
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            liveCurrentMa = samplemA,
            voltageV = voltageV,
            maxChargeCurrentMa = maxChargeCurrent,
            fastChargeMinMa = context.resources.getInteger(R.integer.fast_charge_min),
            minTurboCurrentMa = 3000,
            peakChargeWatt = peakChargeWatt
        ).fastChargeWatt
        return if (watt.isFinite()) watt else 0.0
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
                maxDischargeCurrent = 0; averageDischargeCurrent = 0; minDischargeCurrent = 0
                dischargeSampleCount = 0; runningDischargeSum = 0.0

                if (chargeCurrent > maxChargeCurrent) maxChargeCurrent = chargeCurrent
                if (chargeCurrent > 0) {
                    if (minChargeCurrent == 0 || chargeCurrent < minChargeCurrent) {
                        minChargeCurrent = chargeCurrent
                    }
                    chargeSampleCount++
                    runningChargeSum += chargeCurrent
                    averageChargeCurrent = (runningChargeSum / chargeSampleCount).toInt()
                }
            }

            BatteryManager.BATTERY_STATUS_DISCHARGING -> {
                maxChargeCurrent = 0; averageChargeCurrent = 0; minChargeCurrent = 0
                chargeSampleCount = 0; runningChargeSum = 0.0

                if (chargeCurrent > maxDischargeCurrent) maxDischargeCurrent = chargeCurrent
                if (chargeCurrent > 0) {
                    if (minDischargeCurrent == 0 || chargeCurrent < minDischargeCurrent) {
                        minDischargeCurrent = chargeCurrent
                    }
                    dischargeSampleCount++
                    runningDischargeSum += chargeCurrent
                    averageDischargeCurrent = (runningDischargeSum / dischargeSampleCount).toInt()
                }
            }

            else -> {
                maxChargeCurrent = 0; averageChargeCurrent = 0; minChargeCurrent = 0
                maxDischargeCurrent = 0; averageDischargeCurrent = 0; minDischargeCurrent = 0
                chargeSampleCount = 0; runningChargeSum = 0.0
                dischargeSampleCount = 0; runningDischargeSum = 0.0
            }
        }
    }

    fun getChargingCurrentLimit(context: Context): String? {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val f = File("/sys/class/power_supply/battery/constant_charge_current_max")
        var limit: String? = null
        return if (f.exists()) {
            try {
                BufferedReader(FileReader(f)).use { br -> limit = br.readLine() }
                if (pref.getString(UNIT_OF_CHARGE_DISCHARGE_CURRENT, "μA") == "μA")
                    limit = ((limit?.toInt() ?: 0) / 1000).toString()
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
        val t = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.toDouble() ?: 0.0
        if (t > 0.0) {
            val v = t / 10.0
            Smooth.emaTempC = Smooth.update(Smooth.emaTempC, v, alpha = 0.15)
            updateTemperatureStats(v)
        }
        return if (Smooth.emaTempC > 0.0) Smooth.emaTempC else if (t > 0.0) t / 10.0 else 25.0
    }

    private fun updateTemperatureStats(tempC: Double) {
        if (tempC in -30.0..85.0) {
            if (maximumTemperature == 0.0 || tempC > maximumTemperature) maximumTemperature = tempC
            if (minimumTemperature == 0.0 || tempC < minimumTemperature) minimumTemperature = tempC
            tempSampleCount++
            runningTempSum += tempC
            averageTemperature = runningTempSum / tempSampleCount
        }
    }

    fun getTemperatureInFahrenheit(context: Context) =
        (getTemperatureInCelsius(context) * 1.8) + 32.0

    fun getTemperatureInFahrenheit(temperatureInCelsius: Double) =
        (temperatureInCelsius * 1.8) + 32.0

    fun getMaximumTemperature(context: Context, temperature: Double): Double {
        return if (maximumTemperature > 0.0) maximumTemperature else temperature
    }

    fun getAverageTemperature(context: Context, temperatureMax: Double, temperatureMin: Double): Double {
        return if (averageTemperature > 0.0) averageTemperature
        else if (temperatureMax > 0.0 && temperatureMin > 0.0) (temperatureMax + temperatureMin) / 2.0
        else 0.0
    }

    fun getMinimumTemperature(context: Context, temperature: Double): Double {
        return if (minimumTemperature > 0.0) minimumTemperature else temperature
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

    fun getResidualCapacityMah(context: Context): Double {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val design = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()
        val rawResidual = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble()
        val isMicroAh = pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh"
        val residual = normalizeChargeCounterToMah(rawResidual, isMicroAh)

        if (residual > 0.0 && !pref.getBoolean(RESIDUAL_DUAL_CELL_VERIFIED_PREF, false)) {
            val profile = getBatteryCellProfile(context)
            if (profile.chargeCounterMultiplier == 2 && design > 0.0 && residual in (design * 0.35)..(design * 0.65)) {
                return residual * 2.0
            }
        }
        return residual
    }

    fun getAvailableCapacity(context: Context): Double = try {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val cc = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toDouble()
        val isMicroAh = pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh"
        val rawMah = normalizeChargeCounterToMah(cc, isMicroAh)

        if (rawMah > 0.0) {
            rawMah * getBatteryCellProfile(context).chargeCounterMultiplier
        } else {
            val level = (getBatteryLevel(context) ?: 0).coerceIn(0, 100)
            val design = pref.getInt(
                DESIGN_CAPACITY,
                context.resources.getInteger(R.integer.min_design_capacity)
            ).toDouble()
            (level / 100.0) * design
        }
    } catch (_: RuntimeException) {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val level = (getBatteryLevel(context) ?: 0).coerceIn(0, 100)
        val design = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()
        (level / 100.0) * design
    }

    fun getNominalBatteryVoltage(context: Context): Double {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return pref.getString(NOMINAL_BATTERY_VOLTAGE_PREF, null)
            ?.toDoubleOrNull()
            ?.takeIf { it in 2.5..5.0 }
            ?: NOMINAL_BATTERY_VOLTAGE
    }

    fun getCapacityInWh(capacity: Double) = (abs(capacity) * NOMINAL_BATTERY_VOLTAGE) / 1000.0

    fun getCapacityInWh(context: Context, capacity: Double) =
        (abs(capacity) * getNominalBatteryVoltage(context)) / 1000.0

    fun getWearSampleCountUsed(context: Context): Int {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val configured = pref.getString(NUMBER_OF_HISTORY_FOR_BATTERY_WEAR_NEW, null)
            ?.toIntOrNull()
            ?: context.resources.getInteger(R.integer.number_of_history_for_battery_wear_new_default)
        val historyCount = HistoryHelper.getHistoryCount(context).toInt()
        return if (configured > 1 && historyCount > 1) min(configured, historyCount) else 1
    }

    fun getEffectiveResidualCapacityMah(context: Context): Double {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val configured = pref.getString(NUMBER_OF_HISTORY_FOR_BATTERY_WEAR_NEW, null)
            ?.toIntOrNull()
            ?: context.resources.getInteger(R.integer.number_of_history_for_battery_wear_new_default)

        if (configured > 1 && HistoryHelper.isHistoryNotEmpty(context)) {
            val isMicroAh = pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh"
            val design = pref.getInt(
                DESIGN_CAPACITY,
                context.resources.getInteger(R.integer.min_design_capacity)
            ).toDouble()
            val profile = getBatteryCellProfile(context)
            val verifiedDual = pref.getBoolean(RESIDUAL_DUAL_CELL_VERIFIED_PREF, false)

            val entries = HistoryDB(context).readAll()
            if (entries.isNotEmpty()) {
                val samples = entries.takeLast(configured).mapNotNull { entry ->
                    var mah = normalizeChargeCounterToMah(entry.residualCapacity.toDouble(), isMicroAh)
                    if (!verifiedDual && profile.chargeCounterMultiplier == 2 &&
                        design > 0.0 && mah in (design * 0.35)..(design * 0.65)
                    ) {
                        mah *= 2.0
                    }
                    mah.takeIf { it > 0.0 }
                }
                if (samples.isNotEmpty()) return samples.average()
            }
        }
        return getResidualCapacityMah(context)
    }

    fun getCapacityAdded(
        context: Context,
        isOverlay: Boolean = false,
        isOnlyValues: Boolean = false
    ): String {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val isWh =
            pref.getBoolean(CAPACITY_IN_WH, context.resources.getBoolean(R.bool.capacity_in_wh))

        val capacityAddedPref = pref.getFloat(CAPACITY_ADDED, 0f).toDouble()

        batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return when (batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )) {
            BatteryManager.BATTERY_STATUS_CHARGING -> {
                val currentLevel = getBatteryLevel(context) ?: 0
                percentAdded = (currentLevel - tempBatteryLevelWith).coerceAtLeast(0)

                val currentCap = getAvailableCapacity(context)
                val design = pref.getInt(
                    DESIGN_CAPACITY,
                    context.resources.getInteger(R.integer.min_design_capacity)
                ).toDouble()

                capacityAdded = if (currentCap > 1.0 && tempAvailableCapacity > 1.0) {
                    (currentCap - tempAvailableCapacity).coerceAtLeast(0.0)
                } else {
                    (percentAdded.toDouble() / 100.0) * design
                }

                if (isWh) context.getString(
                    if (!isOverlay || !isOnlyValues) R.string.capacity_added_wh else R.string.capacity_added_wh_overlay_only_values,
                    DecimalFormat("#.#").format(getCapacityInWh(context, capacityAdded)), "$percentAdded%"
                ) else context.getString(
                    if (!isOverlay || !isOnlyValues) R.string.capacity_added else R.string.capacity_added_overlay_only_values,
                    DecimalFormat("#.#").format(capacityAdded), "$percentAdded%"
                )
            }

            else -> {
                val percentAddedPref = pref.getInt(PERCENT_ADDED, 0)
                if (isWh) context.getString(
                    if (!isOverlay || !isOnlyValues) R.string.capacity_added_wh else R.string.capacity_added_wh_overlay_only_values,
                    DecimalFormat("#.#").format(getCapacityInWh(context, capacityAddedPref)),
                    "$percentAddedPref%"
                ) else context.getString(
                    if (!isOverlay || !isOnlyValues) R.string.capacity_added else R.string.capacity_added_overlay_only_values,
                    DecimalFormat("#.#").format(capacityAddedPref), "$percentAddedPref%"
                )
            }
        }
    }

    fun getVoltage(context: Context): Double {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        var voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.toDouble() ?: 0.0
        if (voltage <= 0.0) {
            readBatteryVoltageVolts(context)?.let { voltage = it * 1000.0 }
        }

        val isUnitMicroV = pref.getString(VOLTAGE_UNIT, "mV") == "μV"
        val voltageInMv = when {
            voltage > 100_000.0 -> voltage / 1000.0
            isUnitMicroV -> voltage / 1000.0
            voltage in 1.0..10.0 -> voltage * 1000.0
            else -> voltage
        }

        if (voltageInMv > 0.0) {
            lastMeasuredVoltageV = voltageInMv / 1000.0
        }

        return voltageInMv
    }

    fun getBatteryHealth(context: Context): Int? {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val design = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()

        val residual = getEffectiveResidualCapacityMah(context)

        return if (residual > 0 && residual <= design) {
            val wearPercent = (100.0 - ((residual / design) * 100.0)).toInt()
            when (wearPercent) {
                in 0..9 -> R.string.battery_health_great
                in 10..19 -> R.string.battery_health_very_good
                in 20..29 -> R.string.battery_health_good
                in 30..39 -> R.string.battery_health_bad
                in 40..59 -> R.string.battery_health_very_bad
                else -> R.string.battery_health_replacement_required
            }
        } else if (residual > design) R.string.battery_health_great
        else if (residual < 0) R.string.battery_health_replacement_required else null
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
        val isWh =
            pref.getBoolean(CAPACITY_IN_WH, context.resources.getBoolean(R.bool.capacity_in_wh))
        val design = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()

        residualCapacity = getEffectiveResidualCapacityMah(context).coerceAtLeast(0.0)

        val healthPercent = if (design > 0.0 && residualCapacity > 0.0) {
            (residualCapacity / design) * 100.0
        } else 100.0

        return if (isWh) context.getString(
            if (!isOverlay || !isOnlyValues) R.string.residual_capacity_wh else R.string.residual_capacity_wh_overlay_only_values,
            DecimalFormat("#.#").format(getCapacityInWh(context, residualCapacity)),
            "${DecimalFormat("#.#").format(healthPercent)}%"
        ) else context.getString(
            if (!isOverlay || !isOnlyValues) R.string.residual_capacity else R.string.residual_capacity_overlay_only_values,
            DecimalFormat("#.#").format(residualCapacity),
            "${DecimalFormat("#.#").format(healthPercent)}%"
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
        val design = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()
        val isWh =
            pref.getBoolean(CAPACITY_IN_WH, context.resources.getBoolean(R.bool.capacity_in_wh))

        val wear = NativeCore.computeBatteryWear(residualCapacity, design)
        val wearPercent = wear.wearPercent
        val wearCapacity = wear.wearCapacityMah

        return if (isWh)
            context.getString(
                if (!isOverlay || !isOnlyValues) R.string.battery_wear_wh else R.string.battery_wear_wh_overlay_only_values,
                "${DecimalFormat("#.#").format(wearPercent)}%",
                DecimalFormat("#.#").format(getCapacityInWh(context, wearCapacity))
            )
        else context.getString(
            if (!isOverlay || !isOnlyValues) R.string.battery_wear else R.string.battery_wear_overlay_only_values,
            "${DecimalFormat("#.#").format(wearPercent)}%",
            DecimalFormat("#.#").format(wearCapacity)
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
        val level = (getBatteryLevel(context) ?: 0).coerceIn(0, 100)
        batteryIntent = batteryIntent ?: try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            null
        }
        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        if (status == BatteryManager.BATTERY_STATUS_FULL || level >= 100) {
            NativeCore.nativeResetTimeEstimatorState()
            return context.getString(R.string.fully_charged)
        }

        if (status != BatteryManager.BATTERY_STATUS_CHARGING) {
            return context.getString(R.string.unknown)
        }

        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val useAltCalc = pref.getBoolean(
            IS_ALT_CALC_CHARGING_TIME_REMAINING,
            context.resources.getBoolean(R.bool.is_alt_calc_charging_time_remaining)
        )

        if (!useAltCalc && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val ms = bm?.computeChargeTimeRemaining() ?: -1L
                if (ms in 1000..86400000L) {
                    return TimeHelper.getTime(ms / 1000)
                }
            } catch (_: Throwable) {}
        }

        val residualMah = getResidualCapacityMah(context)
        val designMah = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()

        var currentMa = getChargeDischargeCurrent(context).toDouble()
        if (currentMa <= 0.0) {
            try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val avg = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: 0
                val now = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
                val raw = if (avg != 0 && avg != Int.MIN_VALUE) avg else now
                if (raw != 0 && raw != Int.MIN_VALUE) {
                    currentMa = if (abs(raw) > 50_000) abs(raw) / 1000.0 else abs(raw).toDouble()
                }
            } catch (_: Throwable) {}
        }

        if (currentMa <= 0.0) {
            if (Smooth.emaChargeCurrentMa > 100.0) currentMa = Smooth.emaChargeCurrentMa
            else if (averageChargeCurrent > 100) currentMa = averageChargeCurrent.toDouble()
        }

        val seriesScale = getBatteryCellProfile(context).seriesCellCount
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val finalSeconds = NativeCore.nativeEstimateChargingRemainingSec(
            level = level,
            status = status,
            residualMah = residualMah,
            designMah = designMah,
            currentMa = currentMa,
            seriesScale = seriesScale,
            plugged = plugged,
            nowMs = System.currentTimeMillis()
        )
        return when {
            finalSeconds == 0L -> context.getString(R.string.fully_charged)
            finalSeconds < 0L -> context.getString(R.string.unknown)
            else -> TimeHelper.getTime(finalSeconds)
        }
    }

    fun getRemainingBatteryTime(context: Context): String {
        val level = (getBatteryLevel(context) ?: 0).coerceIn(0, 100)
        if (level <= 0) return context.getString(R.string.unknown)

        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val design = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()

        val residualMah = getResidualCapacityMah(context)
        val avail = getAvailableCapacity(context)

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val hwAvgRaw = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: 0
        val isUnitMicro = pref.getString(UNIT_OF_CHARGE_DISCHARGE_CURRENT, "μA") == "μA"
        val seriesScale = getBatteryCellProfile(context).seriesCellCount
        val hwAvgMa = when {
            abs(hwAvgRaw) > 50_000 -> abs(hwAvgRaw) / 1000.0
            isUnitMicro && abs(hwAvgRaw) > 0 -> abs(hwAvgRaw) / 1000.0
            else -> abs(hwAvgRaw).toDouble()
        }

        val sessionAvgMa = if (dischargeSampleCount >= 8 && averageDischargeCurrent > 50) {
            averageDischargeCurrent.toDouble()
        } else 0.0

        val finalSeconds = NativeCore.nativeEstimateDischargeRemainingSec(
            level = level,
            designMah = design,
            residualMah = residualMah,
            availMah = avail,
            hwAvgMa = hwAvgMa,
            emaDischargeMa = Smooth.emaDischargeCurrentMa,
            sessionAvgMa = sessionAvgMa,
            dischargeSampleCount = dischargeSampleCount,
            seriesScale = seriesScale,
            nowMs = SystemClock.elapsedRealtime()
        )
        if (finalSeconds <= 0L) return context.getString(R.string.unknown)
        val approxCapMah = (level / 100.0) * (if (residualMah in (design * 0.5)..(design * 1.4)) residualMah else design).coerceAtLeast(1000.0)
        TimeEstimator.stableDischargeMa = (approxCapMah / (finalSeconds / 3600.0)).coerceIn(170.0, 1650.0)

        return TimeHelper.getTime(finalSeconds)
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
