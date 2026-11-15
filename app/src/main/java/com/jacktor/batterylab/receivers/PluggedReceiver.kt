package com.jacktor.batterylab.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.MainApp.Companion.isPowerConnected
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import com.jacktor.batterylab.helpers.IdleDrainHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.capacityAdded
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.coulombAddedMah
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.lastSessionPluggedType
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.lastSessionStatus
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.percentAdded
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.tempBatteryLevelWith
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.tempAvailableCapacity
import com.jacktor.batterylab.interfaces.NavigationInterface.Companion.mainActivityRef
import com.jacktor.batterylab.interfaces.NotificationInterface
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_COULOMB_ADDED_MAH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_PLUGGED_TYPE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_START_CAPACITY_MAH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_START_LEVEL

class PluggedReceiver : BroadcastReceiver(), BatteryInfoInterface {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return

        val service = BatteryLabService.instance ?: return
        if (isPowerConnected) return

        val pr = goAsync()

        isPowerConnected = true
        service.isPluggedOrUnplugged = true
        service.pauseScreenTime()
        IdleDrainHelper.onPowerConnected(context)

        batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val level = (service.getBatteryLevel(context) ?: 0).coerceIn(0, 100)
        val startCapacityMah = service.getAvailableCapacity(context)
        service.isFull = false
        service.secondsFullCharge = 0
        service.seconds = 0
        service.batteryLevelWith = level
        service.availableCapacity = 0
        tempBatteryLevelWith = level
        tempAvailableCapacity = startCapacityMah
        lastSessionPluggedType = if (isPlugged(plugged)) plugged else 0
        lastSessionStatus = status
        coulombAddedMah = 0.0
        capacityAdded = 0.0
        percentAdded = 0

        BatteryInfoInterface.resetSessionChargeStats(pref)
        pref.edit {
            putInt(CHARGING_START_LEVEL, level)
            putFloat(CHARGING_START_CAPACITY_MAH, startCapacityMah.toFloat())
            putFloat(CHARGING_COULOMB_ADDED_MAH, 0f)
            if (isPlugged(plugged)) putInt(CHARGING_PLUGGED_TYPE, plugged)
        }

        BatteryInfoInterface.resetChargeStats()
        BatteryInfoInterface.resetDischargeStats()
        BatteryInfoInterface.resetTemperatureStats()

        service.isSaveNumberOfCharges = true

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

        mainHandler.post {
            mainActivityRef?.get()?.let { activity ->
                val item = activity.navigation.menu.findItem(R.id.charge_discharge_navigation)
                val iconRes = BatteryLevelHelper.batteryLevelIcon(
                    service.getBatteryLevel(context),
                    status == BatteryManager.BATTERY_STATUS_CHARGING
                )
                item?.icon = ContextCompat.getDrawable(context, iconRes)

                if (activity.fragment != null) {
                    activity.toolbar.title = context.getString(R.string.battery_info)
                }
            }

            try {
                service.onCreateServiceNotification(context.applicationContext)
                service.onUpdateServiceNotification(context.applicationContext)
            } catch (_: Throwable) {
            }

            service.isPluggedOrUnplugged = false
            pr.finish()
        }
    }
}
