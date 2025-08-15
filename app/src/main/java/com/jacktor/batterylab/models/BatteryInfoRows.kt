package com.jacktor.batterylab.models

sealed class BatteryInfoRow {
    data class Header(
        val chargingTime: String?,
        val chargingTimeRemaining: String?,
        val remainingBatteryTime: String?,
        val screenTime: String,
        val lastChargeTime: String
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