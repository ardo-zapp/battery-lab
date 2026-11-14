package com.jacktor.batterylab.interfaces

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import com.jacktor.batterylab.helpers.TimeHelper
import com.jacktor.batterylab.utilities.Constants.CHARGING_VOLTAGE_WATT
import com.jacktor.batterylab.utilities.Constants.NOMINAL_BATTERY_VOLTAGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_IN_WH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DESIGN_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.LAST_CHARGE_TIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.ONLY_VALUES_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.PERCENT_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.RESIDUAL_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UNIT_OF_CHARGE_DISCHARGE_CURRENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UNIT_OF_MEASUREMENT_OF_available_capacity
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

        var peakChargeWatt = 0.0
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
            var filteredRemainingSeconds: Long = -1L
            var lastCalculationTimeMs: Long = 0L
            var lastLevel: Int = -1

            var stableChargeMa: Double = 0.0
            var filteredChargeRemainingSeconds: Long = -1L
            var lastChargeCalcTimeMs: Long = 0L
            var lastChargeLevel: Int = -1

            fun reset() {
                stableDischargeMa = 0.0
                filteredRemainingSeconds = -1L
                lastCalculationTimeMs = 0L
                lastLevel = -1

                stableChargeMa = 0.0
                filteredChargeRemainingSeconds = -1L
                lastChargeCalcTimeMs = 0L
                lastChargeLevel = -1
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
        val voltage = if (lastMeasuredVoltageV in 2.5..10.0) lastMeasuredVoltageV
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

        if (plugged <= 0 ||
            (status != BatteryManager.BATTERY_STATUS_CHARGING &&
                status != BatteryManager.BATTERY_STATUS_FULL)
        ) {
            isAutoFastChargeDetected = false
            return false
        }

        if (isAutoFastChargeDetected) return true

        val liveCurrentMa = if (Smooth.emaChargeCurrentMa > 0.0) Smooth.emaChargeCurrentMa
        else readRawInstantCurrentMa(context)

        val voltageV = readBatteryVoltageVolts(context)?.also { lastMeasuredVoltageV = it }
            ?: if (lastMeasuredVoltageV > 0.0) lastMeasuredVoltageV else CHARGING_VOLTAGE_WATT
        val liveWatt = (liveCurrentMa * voltageV) / 1000.0
        val fastChargeMinMa = context.resources.getInteger(R.integer.fast_charge_min)

        val byPower = liveWatt >= 12.0 || peakChargeWatt >= 12.0
        val byCurrent = maxChargeCurrent >= fastChargeMinMa || liveCurrentMa >= fastChargeMinMa
        val byHardware = isFastProtocolReportedByKernel() &&
            (liveWatt >= 8.0 || liveCurrentMa >= 1500.0)

        if (byPower || byCurrent || byHardware) {
            isAutoFastChargeDetected = true
        }
        return isAutoFastChargeDetected
    }

    private fun isFastProtocolReportedByKernel(): Boolean {
        val paths = listOf(
            "/sys/class/power_supply/battery/charge_type",
            "/sys/class/power_supply/usb/real_type",
            "/sys/class/power_supply/usb/type"
        )
        for (path in paths) {
            try {
                val f = File(path)
                if (!f.exists() || !f.canRead()) continue
                val value = f.bufferedReader().use { it.readLine() }?.trim()?.lowercase() ?: continue
                if (value.contains("fast") ||
                    value.contains("adaptive") ||
                    value.contains("quick") ||
                    value.contains("turbo") ||
                    value.contains("vooc") ||
                    value.contains("dash") ||
                    value.contains("warp") ||
                    value.contains("flash") ||
                    value.contains("super") ||
                    value.contains("pd")
                ) {
                    return true
                }
            } catch (_: Throwable) {
            }
        }
        return false
    }

    private fun isFastCharge(context: Context): Boolean = updateAutoFastChargeState(context)

    fun isTurboCharge(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val minTurboCurrent = pref.getInt(
            DESIGN_CAPACITY, context.resources.getInteger(R.integer.min_design_capacity) - 250
        )
        return maxChargeCurrent >= minTurboCurrent || peakChargeWatt >= 25.0
    }

    private fun getFastChargeWatt(context: Context): String {
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

        val watt = (samplemA * voltageV) / 1000.0
        return DecimalFormat("#.#").format(if (watt.isFinite()) watt else 0.0)
    }

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

    fun getAvailableCapacity(context: Context): Double = try {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val cc = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toDouble()
        val isMicroAh = pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh"

        when {
            cc > 50_000.0 -> cc / 1000.0 // definitely in µAh
            cc > 0.0 -> if (isMicroAh) cc / 1000.0 else cc
            else -> {
                val level = (getBatteryLevel(context) ?: 0).coerceIn(0, 100)
                val design = pref.getInt(
                    DESIGN_CAPACITY,
                    context.resources.getInteger(R.integer.min_design_capacity)
                ).toDouble()
                (level / 100.0) * design
            }
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

    fun getCapacityInWh(capacity: Double) = (abs(capacity) * NOMINAL_BATTERY_VOLTAGE) / 1000.0

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
                    DecimalFormat("#.#").format(getCapacityInWh(capacityAdded)), "$percentAdded%"
                ) else context.getString(
                    if (!isOverlay || !isOnlyValues) R.string.capacity_added else R.string.capacity_added_overlay_only_values,
                    DecimalFormat("#.#").format(capacityAdded), "$percentAdded%"
                )
            }

            else -> {
                val percentAddedPref = pref.getInt(PERCENT_ADDED, 0)
                if (isWh) context.getString(
                    if (!isOverlay || !isOnlyValues) R.string.capacity_added_wh else R.string.capacity_added_wh_overlay_only_values,
                    DecimalFormat("#.#").format(getCapacityInWh(capacityAddedPref)),
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

        val rawResidual = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble()
        val isMicroAh = pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh"
        val residual = when {
            rawResidual > 50_000.0 -> rawResidual / 1000.0
            isMicroAh && rawResidual > 0.0 -> rawResidual / 1000.0
            else -> rawResidual
        }

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

        val rawResidual = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble()
        val isMicroAh = pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh"

        residualCapacity = when {
            rawResidual > 50_000.0 -> rawResidual / 1000.0
            isMicroAh && rawResidual > 0.0 -> rawResidual / 1000.0
            else -> rawResidual
        }.coerceAtLeast(0.0)

        val healthPercent = if (design > 0.0 && residualCapacity > 0.0) {
            (residualCapacity / design) * 100.0
        } else 100.0

        return if (isWh) context.getString(
            if (!isOverlay || !isOnlyValues) R.string.residual_capacity_wh else R.string.residual_capacity_wh_overlay_only_values,
            DecimalFormat("#.#").format(getCapacityInWh(residualCapacity)),
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

        val hasDegradation = residualCapacity > 0.0 && residualCapacity < design
        val wearPercent = if (hasDegradation) {
            (100.0 - ((residualCapacity / design) * 100.0)).coerceAtLeast(0.0)
        } else 0.0

        val wearCapacity = if (hasDegradation) {
            (design - residualCapacity).coerceAtLeast(0.0)
        } else 0.0

        return if (isWh)
            context.getString(
                if (!isOverlay || !isOnlyValues) R.string.battery_wear_wh else R.string.battery_wear_wh_overlay_only_values,
                "${DecimalFormat("#.#").format(wearPercent)}%",
                DecimalFormat("#.#").format(getCapacityInWh(wearCapacity))
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
            TimeEstimator.filteredChargeRemainingSeconds = 0L
            return context.getString(R.string.fully_charged)
        }

        if (status != BatteryManager.BATTERY_STATUS_CHARGING) {
            return context.getString(R.string.unknown)
        }

        // 1. Try OEM / hardware computation first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val ms = bm?.computeChargeTimeRemaining() ?: -1L
                if (ms in 1000..86400000L) { // Valid 1s to 24h
                    return TimeHelper.getTime(ms / 1000)
                }
            } catch (_: Throwable) {}
        }

        // 2. Proportional software estimation
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val isMicroAh = pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh"
        val residualRaw = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble()
        val residualMah = when {
            residualRaw > 50_000.0 -> residualRaw / 1000.0
            isMicroAh && residualRaw > 0.0 -> residualRaw / 1000.0
            else -> residualRaw
        }

        val designMah = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()
        val fullMah = when {
            residualMah in (designMah * 0.5)..(designMah * 1.5) -> residualMah
            else -> designMah
        }.coerceAtLeast(1000.0)

        val capacityToGoMah = (fullMah * (100 - level).coerceAtLeast(0) / 100.0)
        if (capacityToGoMah <= 0.0) {
            return context.getString(R.string.fully_charged)
        }

        // Sample real charging current
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

        // Operational charging current fallback
        if (currentMa < 200.0) {
            val sourceOfPower = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            currentMa = if (sourceOfPower == BatteryManager.BATTERY_PLUGGED_AC) 2000.0 else 1000.0
        }

        // Smooth rate
        val prevRate = TimeEstimator.stableChargeMa
        val stableRate = if (prevRate <= 0.0) currentMa else (0.15 * currentMa + 0.85 * prevRate)
        TimeEstimator.stableChargeMa = stableRate

        // Constant-Current / Constant-Voltage taper curve
        val taperFactor = when {
            level >= 95 -> 1.40
            level >= 90 -> 1.25
            level >= 80 -> 1.15
            else -> 1.00
        }

        val targetSeconds = ((capacityToGoMah / stableRate.coerceAtLeast(100.0)) * taperFactor * 3600.0).toLong().coerceIn(60L, 86400L)

        // Slew-rate filtering
        val nowMs = System.currentTimeMillis()
        val prevFiltered = TimeEstimator.filteredChargeRemainingSeconds
        val lastTime = TimeEstimator.lastChargeCalcTimeMs

        val finalSeconds: Long = if (prevFiltered <= 0L || lastTime <= 0L || TimeEstimator.lastChargeLevel != level) {
            targetSeconds
        } else {
            val elapsedSec = max(0L, (nowMs - lastTime) / 1000L)
            val naturalCountdown = max(0L, prevFiltered - elapsedSec)
            val maxAllowedShift = max(2L, elapsedSec * 2L)
            naturalCountdown + (targetSeconds - naturalCountdown).coerceIn(-maxAllowedShift, maxAllowedShift)
        }

        TimeEstimator.filteredChargeRemainingSeconds = finalSeconds
        TimeEstimator.lastChargeCalcTimeMs = nowMs
        TimeEstimator.lastChargeLevel = level

        return TimeHelper.getTime(finalSeconds)
    }

    fun getRemainingBatteryTime(context: Context): String {
        val level = (getBatteryLevel(context) ?: 0).coerceIn(0, 100)
        if (level <= 0) return context.getString(R.string.unknown)

        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val design = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()

        // 1. Determine effective full capacity & remaining mAh
        val isMicroAh = pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh"
        val residualRaw = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble()
        val residualMah = when {
            residualRaw > 50_000.0 -> residualRaw / 1000.0
            isMicroAh && residualRaw > 0.0 -> residualRaw / 1000.0
            else -> residualRaw
        }

        val fullCapacityMah = when {
            residualMah in (design * 0.5)..(design * 1.4) -> residualMah
            else -> design
        }.coerceAtLeast(1000.0)

        // Remaining capacity proportional to level (with availableCapacity sanity check)
        val capacityFromLevel = (level / 100.0) * fullCapacityMah
        val avail = getAvailableCapacity(context)
        val remainingMah = if (avail in (capacityFromLevel * 0.7)..(capacityFromLevel * 1.3)) {
            0.6 * capacityFromLevel + 0.4 * avail
        } else {
            capacityFromLevel
        }

        // 2. Determine stable discharge rate
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val hwAvgRaw = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: 0
        val isUnitMicro = pref.getString(UNIT_OF_CHARGE_DISCHARGE_CURRENT, "μA") == "μA"
        val hwAvgMa = when {
            abs(hwAvgRaw) > 50_000 -> abs(hwAvgRaw) / 1000.0
            isUnitMicro && abs(hwAvgRaw) > 0 -> abs(hwAvgRaw) / 1000.0
            else -> abs(hwAvgRaw).toDouble()
        }

        val instantMa = Smooth.emaDischargeCurrentMa
        val sessionAvgMa = if (dischargeSampleCount >= 8 && averageDischargeCurrent > 50) {
            averageDischargeCurrent.toDouble()
        } else 0.0

        val candidateRate = when {
            hwAvgMa in 150.0..2500.0 && sessionAvgMa > 0.0 -> {
                0.5 * hwAvgMa + 0.5 * sessionAvgMa
            }
            hwAvgMa in 150.0..2500.0 -> {
                0.7 * hwAvgMa + 0.3 * (if (instantMa > 0) instantMa else 420.0)
            }
            sessionAvgMa > 0.0 && instantMa > 0.0 -> {
                0.6 * sessionAvgMa + 0.4 * instantMa
            }
            sessionAvgMa > 0.0 -> sessionAvgMa
            instantMa in 100.0..2500.0 -> instantMa
            else -> 420.0
        }

        val prevRate = TimeEstimator.stableDischargeMa
        val smoothedRate = if (prevRate <= 0.0) {
            candidateRate
        } else {
            0.02 * candidateRate + 0.98 * prevRate
        }
        TimeEstimator.stableDischargeMa = smoothedRate

        val safeDischargeRate = smoothedRate.coerceIn(200.0, 1500.0)

        // 3. Target remaining seconds
        val targetSeconds = ((remainingMah / safeDischargeRate) * 3600.0)
            .coerceIn(60.0, 72.0 * 3600.0)

        // 4. Temporal filter with Slew-Rate Limiting (smooth countdown, no jumping)
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val lastMs = TimeEstimator.lastCalculationTimeMs
        val lastSec = TimeEstimator.filteredRemainingSeconds

        val finalSeconds = if (lastSec <= 0L || lastMs <= 0L || (nowMs - lastMs) > 60_000L) {
            targetSeconds.toLong()
        } else {
            val deltaSec = ((nowMs - lastMs) / 1000.0).coerceAtLeast(0.0)
            val elapsedDec = (lastSec.toDouble() - deltaSec).coerceAtLeast(60.0)
            val diff = targetSeconds - elapsedDec
            // Slew rate: limit adjustment to at most 1.5s per second elapsed
            val maxAdjust = (deltaSec * 1.5).coerceAtLeast(1.0)
            val step = diff.coerceIn(-maxAdjust, maxAdjust)
            (elapsedDec + step).toLong().coerceIn(60L, 72L * 3600L)
        }

        TimeEstimator.filteredRemainingSeconds = finalSeconds
        TimeEstimator.lastCalculationTimeMs = nowMs
        TimeEstimator.lastLevel = level

        return TimeHelper.getTime(finalSeconds)
    }

    fun getLastChargeTime(context: Context): String {
        val seconds = PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(LAST_CHARGE_TIME, 0).toLong()
        return TimeHelper.getTime(seconds)
    }

    fun getNumberOfCyclesAndroid(): Int? {
        val fromIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            batteryIntent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it > 0 }
        } else null

        return fromIntent ?: readCycleCountFromKernel()
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
        val currentCached = detectedCellMultiplier
        if (currentCached != null) {
            if (currentCached == 1 && voltageMv >= 6000.0) {
                detectedCellMultiplier = 2
                return 2
            }
            return currentCached
        }
        if (voltageMv > 0.0) {
            val detected = if (voltageMv >= 6000.0) 2 else 1
            detectedCellMultiplier = detected
            return detected
        }
        return 1
    }

    fun getVoltageTintRes(
        voltageMv: Double,
        health: Int = BatteryManager.BATTERY_HEALTH_UNKNOWN
    ): Int {
        // Prioritas 1 — Status Kesehatan BMS (BatteryManager.EXTRA_HEALTH)
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

        // Prioritas 2 — Normalisasi Tegangan Per Sel (Single-Cell vs Dual-Cell)
        val multiplier = resolveBatteryCellMultiplier(voltageMv)
        val cellVoltageMv = (voltageMv / multiplier).toInt()

        return when {
            // MERAH (Danger): Drop kritis (< 3400 mV) atau Over-voltage (> 4550 mV)
            cellVoltageMv < 3400 || cellVoltageMv > 4550 -> R.color.battery_info_voltage_danger
            // KUNING (Warning): Tegangan rendah (3400..3599 mV) atau tinggi mendekati batas (4481..4550 mV)
            cellVoltageMv in 3400..3599 || cellVoltageMv in 4481..4550 -> R.color.battery_info_voltage_warning
            // HIJAU (Normal): 3600..4480 mV (toleransi aman termasuk saat fast charging)
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
