package com.jacktor.batterylab.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.R
import com.jacktor.batterylab.utilities.Constants.DISABLED_DEBUG_OPTIONS_HOST
import com.jacktor.batterylab.utilities.Constants.ENABLED_DEBUG_OPTIONS_HOST
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.ENABLED_DEBUG_OPTIONS

class DebugOptionsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_SECRET_CODE) return

        val data = intent.data ?: return
        if (data.scheme != "android_secret_code") return
        val host = data.host ?: return

        val pr = goAsync()
        val appCtx = context.applicationContext
        val pref = PreferenceManager.getDefaultSharedPreferences(appCtx)

        when (host) {
            ENABLED_DEBUG_OPTIONS_HOST -> {
                // enable
                pref.edit { putBoolean(ENABLED_DEBUG_OPTIONS, true) }
                Toast.makeText(
                    appCtx,
                    appCtx.getString(R.string.debug_successfully_enabled),
                    Toast.LENGTH_LONG
                ).show()
            }

            DISABLED_DEBUG_OPTIONS_HOST -> {
                // disable
                pref.edit { putBoolean(ENABLED_DEBUG_OPTIONS, false) }
                Toast.makeText(
                    appCtx,
                    appCtx.getString(R.string.debug_successfully_disabled),
                    Toast.LENGTH_LONG
                ).show()
            }

            else -> {
            }
        }

        pr.finish()
    }
}
