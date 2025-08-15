package com.jacktor.batterylab.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainApp
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.interfaces.OverlayInterface
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.services.OverlayService
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AUTO_START_UPDATE_APP
import java.io.File

class UpdateApplicationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val app = context.applicationContext
        val pref = PreferenceManager.getDefaultSharedPreferences(app)

        MainApp.isUpdateApp = true

        val file = File(app.filesDir.path, "script.sh")
        if (file.exists()) {
            file.delete()
        }

        removeOldPreferences(pref, app)
        ServiceHelper.cancelAllJobs(app)

        val autoStart = pref.getBoolean(
            AUTO_START_UPDATE_APP,
            app.resources.getBoolean(R.bool.auto_start_update_app)
        )
        if (!autoStart) return

        if (BatteryLabService.instance == null && !ServiceHelper.isStartedBatteryLabService()) {
            ServiceHelper.startService(app, BatteryLabService::class.java)
        }

        if (OverlayService.instance == null &&
            OverlayInterface.isEnabledOverlay(app) &&
            !ServiceHelper.isStartedOverlayService()
        ) {
            ServiceHelper.startService(app, OverlayService::class.java)
        }
    }

    @Suppress("unused")
    private fun removeOldPreferences(pref: SharedPreferences, context: Context) {
        val keysToRemove = listOf(
            "realtime_kernel"
        )

        pref.edit {
            keysToRemove.forEach { remove(it) }
        }
    }
}
