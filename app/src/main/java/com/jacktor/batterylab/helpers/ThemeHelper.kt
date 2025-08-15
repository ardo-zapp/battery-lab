package com.jacktor.batterylab.helpers

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.R
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AUTO_DARK_MODE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DARK_MODE

object ThemeHelper {

    fun setTheme(context: Context, isDarkMode: Boolean? = null, isAutoDarkMode: Boolean? = null) {

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {

            AppCompatDelegate.setDefaultNightMode(
                if (isDarkMode ?: pref.getBoolean(
                        DARK_MODE, context.resources.getBoolean(R.bool.dark_mode)
                    )
                )
                    AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )

            if (pref.contains(AUTO_DARK_MODE)) pref.edit { remove(AUTO_DARK_MODE) }
        } else if (isAutoDarkMode ?: pref.getBoolean(
                AUTO_DARK_MODE, context.resources.getBoolean(
                    R.bool.auto_dark_mode
                )
            )
        )
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        else
            AppCompatDelegate.setDefaultNightMode(
                if (isDarkMode ?: pref.getBoolean(
                        DARK_MODE, context.resources.getBoolean(R.bool.dark_mode)
                    )
                )
                    AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
    }

    /*fun isSystemDarkMode(configuration: Configuration): Boolean {

        val currentNightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }*/

    fun currentTheme(configuration: Configuration) =
        configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK or
                configuration.uiMode and Configuration.UI_MODE_NIGHT_YES or
                configuration.uiMode and Configuration.UI_MODE_NIGHT_NO
}