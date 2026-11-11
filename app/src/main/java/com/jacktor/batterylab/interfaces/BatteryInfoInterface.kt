package com.jacktor.batterylab.interfaces

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.R
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
        private const val EMA_ALPHA = 0.2

        var peakChargeWatt = 0.0
        var peakChargeCurrentmA = 0
        var peakVoltageV = 0.0

        object Smooth {
            private const val DEFAULT_ALPHA = 0.2
            var emaChargeCurrentMa: Double = 0.0
            var emaDischargeCurrentMa: Double = 0.0
            var emaTempC: Double = 0.0
            fun update(prev: Double, value: Double, alpha: Double = DEFAULT_ALPHA): Double {
                return if (prev == 0.0) value else (alpha * value + (1 - alpha) * prev)
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
            resetPeakChargingPower()
        }

        @JvmStatic
        fun resetDischargeStats() {
            maxDischargeCurrent = 0
            averageDischargeCurrent = 0
            minDischargeCurrent = 0
            dischargeSampleCount = 0
            runningDischargeSum = 0.0
            Smooth.emaDischargeCurrentMa = 0.0
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

    fun getChargeDischargeCurrent(context: Context): Int {
        return try {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            var raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) // µA or mA
            if (raw == 0 || raw == Int.MIN_VALUE) {
                raw = readCurrentNowFromKernel() ?: 0
            }

            val isUnitMicroAmp = pref.getString(UNIT_OF_CHARGE_DISCHARGE_CURRENT, "μA") == "μA"
            var currentMa = when {
                abs(raw) > 50_000 -> abs(raw) / 1000.0 // definitely in µA
                isUnitMicroAmp -> abs(raw) / 1000.0
                else -> abs(raw).toDouble()
            }

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

            when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> {
                    currentMa = Smooth.update(Smooth.emaChargeCurrentMa, currentMa).also {
                        Smooth.emaChargeCurrentMa = it
                    }
                    val w = (currentMa * v) / 1000.0
                    if (w.isFinite() && w > peakChargeWatt) {
                        peakChargeWatt = w
                        peakChargeCurrentmA = currentMa.toInt()
                        peakVoltageV = v
                    }
                }

                BatteryManager.BATTERY_STATUS_DISCHARGING -> {
                    currentMa = Smooth.update(Smooth.emaDischargeCurrentMa, currentMa).also {
                        Smooth.emaDischargeCurrentMa = it
                    }
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
        val voltage = if (lastMeasuredVoltageV in 2.5..5.5) lastMeasuredVoltageV
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

    private fun isFastCharge(context: Context) =
        maxChargeCurrent >= context.resources.getInteger(R.integer.fast_charge_min)

    fun isTurboCharge(context: Context): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        return maxChargeCurrent >= pref.getInt(
            DESIGN_CAPACITY, context.resources.getInteger(R.integer.min_design_capacity) - 250
        )
    }

    private fun getFastChargeWatt(context: Context): String {
        val samplemA = runCatching {
            val now = getChargeDischargeCurrent(context)
            if (now > 0) now else (if (averageChargeCurrent > 0) averageChargeCurrent else maxChargeCurrent)
        }.getOrDefault(if (averageChargeCurrent > 0) averageChargeCurrent else maxChargeCurrent)
            .toDouble()
            .coerceAtLeast(0.0)

        val emaMa = if (Smooth.emaChargeCurrentMa > 0) Smooth.emaChargeCurrentMa else samplemA
        Smooth.emaChargeCurrentMa = Smooth.update(Smooth.emaChargeCurrentMa, samplemA, EMA_ALPHA)

        val voltageV = readBatteryVoltageVolts(context)?.also { lastMeasuredVoltageV = it }
            ?: if (lastMeasuredVoltageV > 0) lastMeasuredVoltageV else CHARGING_VOLTAGE_WATT

        val watt = (emaMa * voltageV) / 1000.0
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val ms = bm.computeChargeTimeRemaining()
            if (ms > 0) return TimeHelper.getTime(ms / 1000)
        }

        batteryIntent = batteryIntent ?: context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )
        val level = getBatteryLevel(context)?.coerceIn(0, 100) ?: 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        if (!isCharging || level >= 100) return context.getString(R.string.unknown)

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
        }.coerceAtLeast(100.0)

        val currentMah = getAvailableCapacity(context).coerceAtLeast(0.0)
        val capacityRemainingPercent = (100.0 - level).coerceAtLeast(0.0)
        val capacityToGoFromPercent = (fullMah * capacityRemainingPercent / 100.0)
        val capacityToGoFromCurrent = (fullMah - currentMah).coerceAtLeast(0.0)
        val capacityToGoMah = if (currentMah in 1.0..fullMah) {
            max(capacityToGoFromCurrent, capacityToGoFromPercent * 0.5)
        } else {
            capacityToGoFromPercent
        }

        val emaMa = if (Smooth.emaChargeCurrentMa > 0) Smooth.emaChargeCurrentMa
        else averageChargeCurrent.toDouble()
        val chargeCurrentMa = max(50.0, emaMa)
        val hours = capacityToGoMah / chargeCurrentMa

        val taperFactor = when {
            level >= 95 -> 1.50
            level >= 90 -> 1.30
            level >= 80 -> 1.15
            else -> 1.00
        }

        val turboTrim = when {
            isTurboCharge(context) && chargeCurrentMa >= 1500 -> 0.90
            isTurboCharge(context) && chargeCurrentMa >= 1000 -> 0.95
            else -> 1.00
        }

        val seconds = (hours * taperFactor * turboTrim * 3600.0)
            .coerceIn(0.0, 72 * 3600.0)

        return if (seconds.isFinite() && seconds > 0.0)
            TimeHelper.getTime(seconds.toLong())
        else context.getString(R.string.unknown)
    }

    fun getRemainingBatteryTime(context: Context): String {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val design = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()
        val level = (getBatteryLevel(context) ?: 0).coerceIn(0, 100)

        val currentMah = getAvailableCapacity(context).let { cap ->
            if (cap > 1.0) cap else (level / 100.0) * design
        }

        val discharge = when {
            Smooth.emaDischargeCurrentMa > 0.0 -> Smooth.emaDischargeCurrentMa
            averageDischargeCurrent > 0 -> averageDischargeCurrent.toDouble()
            else -> 0.0
        }

        return if (discharge > 10.0 && currentMah > 0.0) {
            val seconds = ((currentMah / discharge) * 3600.0).toLong().coerceIn(0L, 720 * 3600L)
            TimeHelper.getTime(seconds)
        } else context.getString(R.string.unknown)
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
}
