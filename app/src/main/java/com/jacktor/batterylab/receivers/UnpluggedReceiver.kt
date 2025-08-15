package com.jacktor.batterylab.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainApp
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

class UnpluggedReceiver : BroadcastReceiver() {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_DISCONNECTED) return

        val service = BatteryLabService.instance ?: return
        if (!isPowerConnected) return

        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val isPremium = (context.applicationContext as MainApp).billingManager.isPremium.value

        val pr = goAsync()

        isPowerConnected = false
        service.isPluggedOrUnplugged = true

        val isCheckedUpdateFromGooglePlay =
            mainActivityRef?.get()?.isCheckUpdateFromGooglePlay == true
        mainActivityRef?.get()?.isCheckUpdateFromGooglePlay = !isCheckedUpdateFromGooglePlay

        val batteryLevel = service.getBatteryLevel(context) ?: 0
        val batteryLevelWith = service.batteryLevelWith

        val numberOfCycles = if (batteryLevel == batteryLevelWith)
            pref.getFloat(NUMBER_OF_CYCLES, 0f) + 0.01f
        else
            pref.getFloat(NUMBER_OF_CYCLES, 0f) +
                    (batteryLevel / 100f) - (batteryLevelWith / 100f)

        if (!service.isFull && service.seconds > 1) {
            val numberOfCharges = pref.getLong(NUMBER_OF_CHARGES, 0)
            pref.edit {
                putLong(NUMBER_OF_CHARGES, numberOfCharges + 1)
                    .putInt(LAST_CHARGE_TIME, service.seconds)
                    .putInt(BATTERY_LEVEL_WITH, service.batteryLevelWith)
                    .putInt(BATTERY_LEVEL_TO, batteryLevel)
            }

            if (service.isSaveNumberOfCharges) {
                pref.edit { putFloat(NUMBER_OF_CYCLES, numberOfCycles) }
            }
            if (capacityAdded > 0) pref.edit {
                putFloat(CAPACITY_ADDED, capacityAdded.toFloat())
            }
            if (percentAdded > 0) pref.edit { putInt(PERCENT_ADDED, percentAdded) }

            percentAdded = 0
            capacityAdded = 0.0
        }

        batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        service.seconds = 0

        if (isPremium && (batteryLevel >= 90 ||
                    pref.getBoolean(
                        RESET_SCREEN_TIME_AT_ANY_CHARGE_LEVEL,
                        context.resources.getBoolean(R.bool.reset_screen_time_at_any_charge_level)
                    ))
        ) {
            service.screenTime = 0L
        }

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

        BatteryInfoInterface.resetPeakChargingPower()

        service.secondsFullCharge = 0
        service.isFull = false

        if (isPremium && pref.getBoolean(
                STOP_THE_SERVICE_WHEN_THE_CD,
                context.resources.getBoolean(R.bool.stop_the_service_when_the_cd)
            )
        ) {
            ServiceHelper.stopService(context, BatteryLabService::class.java)
        }

        NotificationInterface.notificationManager?.apply {
            cancel(NotificationInterface.NOTIFICATION_FULLY_CHARGED_ID)
            cancel(NotificationInterface.NOTIFICATION_BATTERY_STATUS_ID)
            cancel(NotificationInterface.NOTIFICATION_BATTERY_OVERHEAT_OVERCOOL_ID)
        }
        NotificationInterface.isOverheatOvercool = false
        NotificationInterface.isBatteryFullyCharged = false
        NotificationInterface.isBatteryCharged = false
        NotificationInterface.isBatteryDischarged = false
        NotificationInterface.isBatteryDischargedVoltage = false

        ServiceHelper.cancelJob(context, Constants.NOTIFY_FULL_CHARGE_REMINDER_JOB_ID)

        mainHandler.post {
            try {
                service.onUpdateServiceNotification(context.applicationContext)
            } catch (_: Throwable) {
                try {
                    service.onCreateServiceNotification(context.applicationContext)
                } catch (_: Throwable) {
                }
            } finally {
                service.isPluggedOrUnplugged = false
                service.wakeLockRelease()
                pr.finish()
            }
        }
    }
}
