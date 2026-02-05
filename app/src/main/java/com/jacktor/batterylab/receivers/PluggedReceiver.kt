package com.jacktor.batterylab.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.MainApp.Companion.isPowerConnected
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.tempBatteryLevelWith
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.tempAvailableCapacity
import com.jacktor.batterylab.interfaces.NavigationInterface.Companion.mainActivityRef
import com.jacktor.batterylab.interfaces.NotificationInterface
import com.jacktor.batterylab.services.BatteryLabService

class PluggedReceiver : BroadcastReceiver(), BatteryInfoInterface {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return

        val service = BatteryLabService.instance ?: return
        if (isPowerConnected) return

        val pr = goAsync()

        isPowerConnected = true
        service.isPluggedOrUnplugged = true

        batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        service.batteryLevelWith = service.getBatteryLevel(context) ?: 0
        tempBatteryLevelWith = service.batteryLevelWith
        tempAvailableCapacity = service.getAvailableCapacity(context)

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
