package com.jacktor.batterylab.models

import com.jacktor.batterylab.R

sealed class BatteryInfoRow {
    data class Header(
        val level: Int,
        val isCharging: Boolean,
        val statusText: String,
        val voltageText: String,
        val temperatureText: String,
        val timeHeroText: String,
        val chargingTime: String?,
        val chargingTimeRemaining: String?,
        val remainingBatteryTime: String?,
        val screenTime: String,
        val lastChargeTime: String,
        val tempTint: Int = R.color.battery_info_temp_normal,
        val voltageTint: Int = R.color.battery_info_voltage_normal,
        val timeTint: Int = R.color.battery_info_temp_normal
    ) : BatteryInfoRow()

    data class Section(
        val title: String
    ) : BatteryInfoRow()

    data class Counts(
        val charges: String,
        val fullCharges: String,
        val cycles: String,
        val cyclesAndroid: String?,
        val showCyclesAndroid: Boolean
    ) : BatteryInfoRow()

    data class Stat(
        val icon: Int,
        val iconTint: Int,
        val title: String,
        val primary: String,
        val secondary: List<String> = emptyList()
    ) : BatteryInfoRow()
}
