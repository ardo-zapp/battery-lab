package com.jacktor.batterylab.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.interfaces.OverlayInterface
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.services.OverlayService
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AUTO_START_BOOT

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val QUICKBOOT_ACTION = "android.intent.action.QUICKBOOT_POWERON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != QUICKBOOT_ACTION) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val autoStartEnabled = prefs.getBoolean(
            AUTO_START_BOOT,
            context.resources.getBoolean(R.bool.auto_start_boot)
        )

        if (!autoStartEnabled) return

        with(context) {
            ServiceHelper.cancelAllJobs(this)

            if (BatteryLabService.instance == null && !ServiceHelper.isStartedBatteryLabService()) {
                ServiceHelper.startService(this, BatteryLabService::class.java)
            }

            if (
                OverlayService.instance == null &&
                OverlayInterface.isEnabledOverlay(this) &&
                !ServiceHelper.isStartedOverlayService()
            ) {
                ServiceHelper.startService(this, OverlayService::class.java)
            }
        }
    }
}
