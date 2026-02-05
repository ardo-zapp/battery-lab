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
        fun getPeakChargeWattRaw(): Double =
            if (peakChargeWatt.isFinite()) peakChargeWatt else 0.0

        @JvmStatic
        fun getPeakMetrics(): Triple<Double, Int, Double> =
            Triple(getPeakChargeWattRaw(), peakChargeCurrentmA, peakVoltageV)
    }

    fun getDesignCapacity(context: Context): Int {
        val powerProfileClass = "com.android.internal.os.PowerProfile"
        val mPowerProfile = Class.forName(powerProfileClass).getConstructor(
            Context::class.java
        ).newInstance(context)
        val designCapacity = (Class.forName(powerProfileClass).getMethod(
            "getBatteryCapacity"
        ).invoke(mPowerProfile) as Double).toInt()

        return when {
            designCapacity == 0 || designCapacity < context.resources.getInteger(R.integer.min_design_capacity)
                    || designCapacity > context.resources.getInteger(R.integer.max_design_capacity)
                -> context.resources.getInteger(R.integer.min_design_capacity)

            designCapacity < 0 -> -designCapacity
            else -> designCapacity
        }
    }

    fun getBatteryLevel(context: Context) = try {
        (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (_: RuntimeException) {
        val bi = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: RuntimeException) {
            null
        }
        bi?.getStringExtra(BatteryManager.EXTRA_LEVEL)?.toInt() ?: 0
    }

    fun getChargeDischargeCurrent(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) // µA
            var currentMa = abs(raw) / 1000.0

            batteryIntent = batteryIntent ?: context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val status = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )

            when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> {
                    currentMa = Smooth.update(Smooth.emaChargeCurrentMa, currentMa).also {
                        Smooth.emaChargeCurrentMa = it
                    }
                    val v = readBatteryVoltageVolts(context)?.also { lastMeasuredVoltageV = it }
                        ?: if (lastMeasuredVoltageV > 0) lastMeasuredVoltageV else CHARGING_VOLTAGE_WATT
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

    fun getChargeDischargeCurrentInWatt(
        chargeDischargeCurrent: Int,
        isCharging: Boolean = false
    ) = (chargeDischargeCurrent.toDouble() *
            (if (isCharging) CHARGING_VOLTAGE_WATT else NOMINAL_BATTERY_VOLTAGE)) / 1000.0

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
            if (mV > 0) mV / 1000.0 else null
        } catch (_: Exception) {
            null
        }
    }

    fun getMaxAverageMinChargeDischargeCurrent(status: Int?, chargeCurrent: Int) {
        when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> {
                maxDischargeCurrent = 0; averageDischargeCurrent = 0; minDischargeCurrent = 0

                if (chargeCurrent > maxChargeCurrent) maxChargeCurrent = chargeCurrent
                if ((chargeCurrent < minChargeCurrent && chargeCurrent < maxChargeCurrent) ||
                    (minChargeCurrent == 0 && chargeCurrent < maxChargeCurrent)
                ) minChargeCurrent = chargeCurrent

                if (maxChargeCurrent > 0 && minChargeCurrent > 0) {
                    averageChargeCurrent = ((maxChargeCurrent + minChargeCurrent) / 2)
                    if (Smooth.emaChargeCurrentMa > 0.0) {
                        averageChargeCurrent =
                            ((averageChargeCurrent + Smooth.emaChargeCurrentMa).toInt()) / 2
                    }
                }
            }

            BatteryManager.BATTERY_STATUS_DISCHARGING -> {
                maxChargeCurrent = 0; averageChargeCurrent = 0; minChargeCurrent = 0

                if (chargeCurrent > maxDischargeCurrent) maxDischargeCurrent = chargeCurrent
                if ((chargeCurrent < minDischargeCurrent && chargeCurrent < maxDischargeCurrent) ||
                    (minDischargeCurrent == 0 && chargeCurrent < maxDischargeCurrent)
                ) minDischargeCurrent = chargeCurrent

                if (maxDischargeCurrent > 0 && minDischargeCurrent > 0) {
                    averageDischargeCurrent = ((maxDischargeCurrent + minDischargeCurrent) / 2)
                    if (Smooth.emaDischargeCurrentMa > 0.0) {
                        averageDischargeCurrent =
                            ((averageDischargeCurrent + Smooth.emaDischargeCurrentMa).toInt()) / 2
                    }
                }
            }

            else -> {
                maxChargeCurrent = 0; averageChargeCurrent = 0; minChargeCurrent = 0
                maxDischargeCurrent = 0; averageDischargeCurrent = 0; minDischargeCurrent = 0
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
        val v = t / 10.0
        Smooth.emaTempC = Smooth.update(Smooth.emaTempC, v, alpha = 0.15)
        return Smooth.emaTempC
    }

    fun getTemperatureInFahrenheit(context: Context) =
        (getTemperatureInCelsius(context) * 1.8) + 32.0

    fun getTemperatureInFahrenheit(temperatureInCelsius: Double) =
        (temperatureInCelsius * 1.8) + 32.0

    fun getMaximumTemperature(context: Context, temperature: Double): Double {
        batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val c = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.toDouble()
            ?: 0.0) / 10.0
        return if (c >= temperature || temperature == 0.0) c else temperature
    }

    fun getAverageTemperature(context: Context, temperatureMax: Double, temperatureMin: Double) =
        (temperatureMax + temperatureMin) / 2.0

    fun getMinimumTemperature(context: Context, temperature: Double): Double {
        batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val c = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.toDouble()
            ?: 0.0) / 10.0
        return if (c <= temperature || temperature == 0.0) c else temperature
    }

    fun getAvailableCapacity(context: Context) =
        try {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val cc = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toDouble()
            when {
                cc < 0 -> 0.001
                pref.getString(
                    UNIT_OF_MEASUREMENT_OF_available_capacity,
                    "μAh"
                ) == "μAh" -> cc / 1000.0

                else -> cc
            }
        } catch (_: RuntimeException) {
            0.001
        }

    fun getCapacityInWh(capacity: Double) = (capacity * NOMINAL_BATTERY_VOLTAGE) / 1000.0

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
                percentAdded = (getBatteryLevel(context) ?: 0) - tempBatteryLevelWith
                if (percentAdded < 0) percentAdded = 0

                capacityAdded = getAvailableCapacity(context) - tempAvailableCapacity
                if (capacityAdded < 0) capacityAdded = abs(capacityAdded)

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

        val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.toDouble() ?: 0.0

        return if (pref.getString(VOLTAGE_UNIT, "mV") == "μV") voltage * 1000.0 else voltage
    }

    fun getBatteryHealth(context: Context): Int? {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val design = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()
        val residual = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble() /
                if (pref.getString(
                        UNIT_OF_MEASUREMENT_OF_available_capacity,
                        "μAh"
                    ) == "μAh"
                ) 1000.0 else 100.0
        return if (residual > 0 && residual <= design) {
            when ((100.0 - ((residual / design) * 100.0)).toInt()) {
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

        residualCapacity = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble()
        residualCapacity /= if (pref.getString(
                UNIT_OF_MEASUREMENT_OF_available_capacity,
                "μAh"
            ) == "μAh"
        ) 1000.0 else 100.0
        if (residualCapacity < 0.0) residualCapacity = abs(residualCapacity)

        return if (isWh) context.getString(
            if (!isOverlay || !isOnlyValues) R.string.residual_capacity_wh else R.string.residual_capacity_wh_overlay_only_values,
            DecimalFormat("#.#").format(getCapacityInWh(residualCapacity)),
            "${DecimalFormat("#.#").format((residualCapacity / design) * 100.0)}%"
        ) else context.getString(
            if (!isOverlay || !isOnlyValues) R.string.residual_capacity else R.string.residual_capacity_overlay_only_values,
            DecimalFormat("#.#").format(residualCapacity),
            "${DecimalFormat("#.#").format((residualCapacity / design) * 100.0)}%"
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

        return if (isWh)
            context.getString(
                if (!isOverlay || !isOnlyValues) R.string.battery_wear_wh else R.string.battery_wear_wh_overlay_only_values,
                if (residualCapacity > 0 && residualCapacity < design)
                    "${DecimalFormat("#.#").format(100 - ((residualCapacity / design) * 100))}%"
                else "0%",
                if (residualCapacity > 0 && residualCapacity < design)
                    DecimalFormat("#.#").format(getCapacityInWh(design - residualCapacity))
                else "0"
            )
        else context.getString(
            if (!isOverlay || !isOnlyValues) R.string.battery_wear else R.string.battery_wear_overlay_only_values,
            if (residualCapacity > 0 && residualCapacity < design)
                "${DecimalFormat("#.#").format(100 - ((residualCapacity / design) * 100))}%"
            else "0%",
            if (residualCapacity > 0 && residualCapacity < design)
                DecimalFormat("#.#").format(design - residualCapacity) else "0"
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
        if (!isCharging) return context.getString(R.string.unknown)

        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val unitIsuAh = pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh"
        val residualRaw = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble()
        val residualMah = if (residualRaw > 0) {
            if (unitIsuAh) residualRaw / 1000.0 else residualRaw / 100.0
        } else 0.0

        val designMah = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).toDouble()
        val fullMah = when {
            residualMah in 1.0..(designMah * 1.5) -> residualMah
            else -> designMah
        }.coerceAtLeast(100.0)

        val currentMah = getAvailableCapacity(context).coerceAtLeast(0.0)
        val capacityToGoMah = if (currentMah in 1.0..(fullMah * 1.5)) {
            (fullMah - currentMah).coerceAtLeast(0.0)
        } else {
            (fullMah * (100.0 - level) / 100.0).coerceAtLeast(0.0)
        }

        val emaMa = if (Smooth.emaChargeCurrentMa > 0) Smooth.emaChargeCurrentMa
        else averageChargeCurrent.toDouble()
        val chargeCurrentMa = max(1.0, emaMa)
        val hours = capacityToGoMah / chargeCurrentMa

        val taperFactor = when {
            level >= 95 -> 1.30
            level >= 90 -> 1.25
            level >= 80 -> 1.10
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
        val currentMah = getAvailableCapacity(context)
        val discharge = when {
            Smooth.emaDischargeCurrentMa > 0 -> Smooth.emaDischargeCurrentMa
            averageDischargeCurrent > 0 -> averageDischargeCurrent.toDouble()
            else -> 0.0
        }
        return if (discharge > 0.0 && currentMah > 0.0) {
            val seconds = ((currentMah / discharge) * 3600.0).toLong()
            TimeHelper.getTime(seconds)
        } else context.getString(R.string.unknown)
    }

    fun getLastChargeTime(context: Context): String {
        val seconds = PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(LAST_CHARGE_TIME, 0).toLong()
        return TimeHelper.getTime(seconds)
    }

    fun getNumberOfCyclesAndroid(): Int? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            batteryIntent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, 0)
        } else {
            readCycleCountFromKernel()
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
}
