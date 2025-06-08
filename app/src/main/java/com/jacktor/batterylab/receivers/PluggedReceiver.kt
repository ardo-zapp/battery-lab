package com.jacktor.batterylab.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.MainApp.Companion.isPowerConnected
import com.jacktor.batterylab.R
import com.jacktor.batterylab.fragments.ChargeDischargeFragment
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.tempBatteryLevelWith
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.tempCurrentCapacity
import com.jacktor.batterylab.interfaces.NavigationInterface.Companion.mainActivityRef
import com.jacktor.batterylab.interfaces.NotificationInterface
import com.jacktor.batterylab.services.BatteryLabService

class PluggedReceiver() : BroadcastReceiver(), BatteryInfoInterface {

    override fun onReceive(context: Context, intent: Intent) {

        if (BatteryLabService.instance != null && !isPowerConnected)
            when (intent.action) {

                Intent.ACTION_POWER_CONNECTED -> {

                    isPowerConnected = true

                    BatteryLabService.instance!!.isPluggedOrUnplugged = true

                    val isCheckedUpdateFromGooglePlay =
                        mainActivityRef?.get()?.isCheckUpdateFromGooglePlay == true

                    mainActivityRef?.get()?.isCheckUpdateFromGooglePlay =
                        !isCheckedUpdateFromGooglePlay

                    batteryIntent = context.registerReceiver(
                        null, IntentFilter(
                            Intent
                                .ACTION_BATTERY_CHANGED
                        )
                    )

                    val status = batteryIntent?.getIntExtra(
                        BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN
                    ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN


                    BatteryLabService.instance!!.batteryLevelWith = BatteryLabService.instance
                        ?.getBatteryLevel(context) ?: 0

                    tempBatteryLevelWith = BatteryLabService.instance!!.batteryLevelWith

                    tempCurrentCapacity = BatteryLabService.instance
                        ?.getCurrentCapacity(context) ?: 0.0

                    BatteryInfoInterface.maxChargeCurrent = 0
                    BatteryInfoInterface.averageChargeCurrent = 0
                    BatteryInfoInterface.minChargeCurrent = 0
                    BatteryInfoInterface.maxDischargeCurrent = 0
                    BatteryInfoInterface.averageDischargeCurrent = 0
                    BatteryInfoInterface.minDischargeCurrent = 0
                    BatteryInfoInterface.maximumTemperature = 0.0
                    BatteryInfoInterface.averageTemperature = 0.0
                    BatteryInfoInterface.minimumTemperature = 0.0

                    BatteryLabService.instance!!.isSaveNumberOfCharges = true

                    NotificationInterface.notificationManager?.cancel(
                        NotificationInterface.NOTIFICATION_FULLY_CHARGED_ID
                    )

                    NotificationInterface.notificationManager?.cancel(
                        NotificationInterface.NOTIFICATION_BATTERY_STATUS_ID
                    )

                    NotificationInterface.notificationManager?.cancel(
                        NotificationInterface.NOTIFICATION_BATTERY_OVERHEAT_OVERCOOL_ID
                    )

                    NotificationInterface.isOverheatOvercool = true
                    NotificationInterface.isBatteryFullyCharged = true
                    NotificationInterface.isBatteryCharged = true
                    NotificationInterface.isBatteryDischarged = true
                    NotificationInterface.isBatteryDischargedVoltage = true

                    if (mainActivityRef?.get()?.fragment != null) {

                        if (mainActivityRef?.get()?.fragment is ChargeDischargeFragment)
                            mainActivityRef?.get()?.topAppBar?.title = context.getString(
                                if (status ==
                                    BatteryManager.BATTERY_STATUS_CHARGING
                                ) R.string.charge else
                                    R.string.discharge
                            )

                        val chargeDischargeNavigation = mainActivityRef?.get()?.navigation
                            ?.menu?.findItem(R.id.charge_discharge_navigation)

                        chargeDischargeNavigation?.title = context.getString(
                            if (status ==
                                BatteryManager.BATTERY_STATUS_CHARGING
                            ) R.string.charge else
                                R.string.discharge
                        )

                        chargeDischargeNavigation?.icon = BatteryLevelHelper.batteryLevelIcon(
                            getBatteryLevel(context),
                            status == BatteryManager.BATTERY_STATUS_CHARGING
                        )
                            .let {
                                ContextCompat.getDrawable(context, it)
                            }
                    }

                    BatteryLabService.instance!!.isPluggedOrUnplugged = false
                }
            }
    }
}
