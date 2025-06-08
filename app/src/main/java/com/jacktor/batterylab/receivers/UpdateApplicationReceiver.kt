package com.jacktor.batterylab.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jacktor.batterylab.MainApp
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.interfaces.OverlayInterface
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.services.OverlayService
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AUTO_START_UPDATE_APP
import com.jacktor.batterylab.utilities.preferences.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UpdateApplicationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        when (intent.action) {

            Intent.ACTION_MY_PACKAGE_REPLACED -> {

                val pref = Prefs(context)

                MainApp.isUpdateApp = true

                removeOldPreferences(context)

                if (!pref.getBoolean(
                        AUTO_START_UPDATE_APP, context.resources.getBoolean(
                            R.bool.auto_start_update_app
                        )
                    )
                ) return

                ServiceHelper.cancelAllJobs(context)

                if (BatteryLabService.instance == null && !ServiceHelper.isStartedBatteryLabService()) ServiceHelper.startService(
                    context, BatteryLabService::class.java
                )

                if (OverlayService.instance == null && OverlayInterface.isEnabledOverlay(context) && !ServiceHelper.isStartedOverlayService()) ServiceHelper.startService(
                    context, OverlayService::class.java
                )
            }
        }
    }

    private fun removeOldPreferences(context: Context) {

        CoroutineScope(Dispatchers.IO).launch {

            val pref = Prefs(context)

            arrayListOf(
                "temperature_in_fahrenheit",
                "voltage_in_mv",
                "is_fps_overlay",
                "is_show_faq",
                "is_show_donate_message",
                "is_show_premium_info_dialog",
                "is_supported",
                "is_show_not_supported_dialog",
                "language",
                "is_enable_fake_battery_wear",
                "fake_battery_wear_value",
                "is_high_battery_wear",
                "is_very_high_battery_wear",
                "is_critical_battery_wear",
                "${context.packageName}_preferences.products.cache.v2_6.version",
                "${context.packageName}_preferences.products.cache.v2_6",
                "${context.packageName}_preferences.products.restored.v2_6",
                "${context.packageName}_preferences.subscriptions.cache.v2_6",
                "${context.packageName}_preferences.subscriptions.cache.v2_6.version",
                "is_battery_wear",
                "is_show_instruction",
                "is_show_backup_information",
                "is_auto_backup_settings",
                "is_backup_settings_to_microsd",
                "frequency_of_auto_backup_settings",
                "is_notify_battery_is_charged_voltage",
                "battery_notify_discharged_voltage",
                "is_notify_charging_current",
                "charging_current_level_notify",
                "is_notify_discharge_current",
                "discharge_current_level_notify",
                "is_fast_charge_debug",
                "realtime_kernel"
            ).forEach {

                with(pref) {
                    apply {

                        if (contains(it)) this.remove(it)

                    }
                }
            }
        }
    }
}
