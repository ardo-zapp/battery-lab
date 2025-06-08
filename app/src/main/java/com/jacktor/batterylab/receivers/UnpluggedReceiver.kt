package com.jacktor.batterylab.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.MainApp.Companion.isPowerConnected
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.capacityAdded
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.percentAdded
import com.jacktor.batterylab.interfaces.NavigationInterface.Companion.mainActivityRef
import com.jacktor.batterylab.interfaces.NotificationInterface
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.utilities.Constants
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_TO
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_WITH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.LAST_CHARGE_TIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CHARGES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.PERCENT_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.RESET_SCREEN_TIME_AT_ANY_CHARGE_LEVEL
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.STOP_THE_SERVICE_WHEN_THE_CD

class UnpluggedReceiver() : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        if (BatteryLabService.instance != null && isPowerConnected)
            when (intent.action) {

                Intent.ACTION_POWER_DISCONNECTED -> {

                    isPowerConnected = false

                    BatteryLabService.instance!!.isPluggedOrUnplugged = true

                    val isCheckedUpdateFromGooglePlay =
                        mainActivityRef?.get()?.isCheckUpdateFromGooglePlay == true

                    mainActivityRef?.get()?.isCheckUpdateFromGooglePlay =
                        !isCheckedUpdateFromGooglePlay

                    val batteryLevel = BatteryLabService.instance!!.getBatteryLevel(context) ?: 0

                    val batteryLevelWith = BatteryLabService.instance!!.batteryLevelWith

                    val numberOfCycles = if (batteryLevel == batteryLevelWith) pref.getFloat(
                        NUMBER_OF_CYCLES, 0f
                    ) + 0.01f else pref.getFloat(
                        NUMBER_OF_CYCLES, 0f
                    ) + (batteryLevel / 100f) - (
                            batteryLevelWith / 100f)

                    pref.edit().apply {

                        if ((BatteryLabService.instance!!.isFull != true) && BatteryLabService.instance!!.seconds > 1) {

                            val numberOfCharges = pref.getLong(NUMBER_OF_CHARGES, 0)

                            putLong(NUMBER_OF_CHARGES, numberOfCharges + 1).apply()

                            putInt(LAST_CHARGE_TIME, BatteryLabService.instance!!.seconds)

                            putInt(
                                BATTERY_LEVEL_WITH, BatteryLabService.instance
                                    ?.batteryLevelWith ?: 0
                            )

                            putInt(BATTERY_LEVEL_TO, batteryLevel)

                            if (BatteryLabService.instance!!.isSaveNumberOfCharges != false)
                                putFloat(NUMBER_OF_CYCLES, numberOfCycles)

                            if (capacityAdded > 0) putFloat(CAPACITY_ADDED, capacityAdded.toFloat())

                            if (percentAdded > 0) putInt(PERCENT_ADDED, percentAdded)

                            percentAdded = 0

                            capacityAdded = 0.0
                        }

                        apply()
                    }

                    batteryIntent = context.registerReceiver(
                        null, IntentFilter(
                            Intent
                                .ACTION_BATTERY_CHANGED
                        )
                    )

                    BatteryLabService.instance!!.seconds = 0

                    if ((batteryLevel >= 90 || pref.getBoolean(
                            RESET_SCREEN_TIME_AT_ANY_CHARGE_LEVEL, context.resources.getBoolean(
                                R.bool.reset_screen_time_at_any_charge_level
                            )
                        ))
                    )
                        BatteryLabService.instance!!.screenTime = 0L

                    BatteryInfoInterface.batteryLevel = 0

                    BatteryInfoInterface.maxChargeCurrent = 0
                    BatteryInfoInterface.averageChargeCurrent = 0
                    BatteryInfoInterface.minChargeCurrent = 0
                    BatteryInfoInterface.maxDischargeCurrent = 0
                    BatteryInfoInterface.averageDischargeCurrent = 0
                    BatteryInfoInterface.minDischargeCurrent = 0
                    BatteryInfoInterface.maximumTemperature = 0.0
                    BatteryInfoInterface.averageTemperature = 0.0
                    BatteryInfoInterface.minimumTemperature = 0.0

                    BatteryLabService.instance!!.secondsFullCharge = 0
                    BatteryLabService.instance!!.isFull = false

                    if (pref.getBoolean(
                            STOP_THE_SERVICE_WHEN_THE_CD,
                            context.resources.getBoolean(R.bool.stop_the_service_when_the_cd)
                        )
                    )

                        ServiceHelper.stopService(context, BatteryLabService::class.java)

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

                    ServiceHelper.cancelJob(context, Constants.NOTIFY_FULL_CHARGE_REMINDER_JOB_ID)

                    BatteryLabService.instance!!.isPluggedOrUnplugged = false
                    BatteryLabService.instance!!.wakeLockRelease()
                }
            }
    }
}
