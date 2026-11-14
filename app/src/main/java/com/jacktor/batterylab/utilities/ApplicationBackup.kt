package com.jacktor.batterylab.utilities

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.content.SharedPreferences
import android.os.ParcelFileDescriptor
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys

class ApplicationBackup : BackupAgent() {

    private var pref: SharedPreferences? = null
    private var prefArrays: MutableMap<String, *>? = null

    override fun onCreate() {
        super.onCreate()
        pref = PreferenceManager.getDefaultSharedPreferences(this)
        prefArrays = pref?.all
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?, data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) {
    }

    override fun onRestore(
        data: BackupDataInput?, appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) {
    }

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        restorePreservedPrefs(pref, prefArrays)
    }

    companion object {

        val PRESERVED_PREF_KEYS: List<String> = listOf(
            PreferencesKeys.BATTERY_LEVEL_TO,
            PreferencesKeys.BATTERY_LEVEL_WITH,
            PreferencesKeys.DESIGN_CAPACITY,
            PreferencesKeys.CAPACITY_ADDED,
            PreferencesKeys.PERCENT_ADDED,
            PreferencesKeys.RESIDUAL_CAPACITY,
            PreferencesKeys.NUMBER_OF_CHARGES,
            PreferencesKeys.NUMBER_OF_FULL_CHARGES,
            PreferencesKeys.NUMBER_OF_CYCLES,
            PreferencesKeys.LAST_CHARGE_TIME,
            PreferencesKeys.BATTERY_LEVEL_LAST_CHARGE,
            PreferencesKeys.CHARGING_TIME_LAST_CHARGE,
            PreferencesKeys.CURRENT_CAPACITY_LAST_CHARGE,
            PreferencesKeys.CAPACITY_ADDED_LAST_CHARGE,
            PreferencesKeys.PERCENT_ADDED_LAST_CHARGE,
            PreferencesKeys.STATUS_LAST_CHARGE,
            PreferencesKeys.SOURCE_OF_POWER_LAST_CHARGE,
            PreferencesKeys.IS_FAST_CHARGE_LAST_CHARGE,
            PreferencesKeys.FAST_CHARGE_WATTS_LAST_CHARGE,
            PreferencesKeys.MAX_CHARGE_LAST_CHARGE,
            PreferencesKeys.AVERAGE_CHARGE_LAST_CHARGE,
            PreferencesKeys.MIN_CHARGE_LAST_CHARGE,
            PreferencesKeys.MAX_TEMP_CELSIUS_LAST_CHARGE,
            PreferencesKeys.MAX_TEMP_FAHRENHEIT_LAST_CHARGE,
            PreferencesKeys.AVERAGE_TEMP_CELSIUS_LAST_CHARGE,
            PreferencesKeys.AVERAGE_TEMP_FAHRENHEIT_LAST_CHARGE,
            PreferencesKeys.MIN_TEMP_CELSIUS_LAST_CHARGE,
            PreferencesKeys.MIN_TEMP_FAHRENHEIT_LAST_CHARGE,
            PreferencesKeys.VOLTAGE_LAST_CHARGE,
            PreferencesKeys.LAST_CHARGE_TIMESTAMP,
            PreferencesKeys.STRESS_CALIBRATED_RINT_MOHM
        )

        fun restorePreservedPrefs(pref: SharedPreferences?, savedMap: Map<*, *>?) {
            val source = savedMap ?: return
            PRESERVED_PREF_KEYS.forEach { key ->
                if (!source.containsKey(key)) {
                    pref?.edit { remove(key) }
                } else {
                    val value = source[key]
                    when (key) {
                        PreferencesKeys.NUMBER_OF_CHARGES,
                        PreferencesKeys.NUMBER_OF_FULL_CHARGES,
                        PreferencesKeys.LAST_CHARGE_TIMESTAMP ->
                            (value as? Number)?.toLong()?.let { v -> pref?.edit { putLong(key, v) } }

                        PreferencesKeys.BATTERY_LEVEL_TO, PreferencesKeys.BATTERY_LEVEL_WITH,
                        PreferencesKeys.DESIGN_CAPACITY, PreferencesKeys.RESIDUAL_CAPACITY,
                        PreferencesKeys.PERCENT_ADDED, PreferencesKeys.LAST_CHARGE_TIME,
                        PreferencesKeys.BATTERY_LEVEL_LAST_CHARGE,
                        PreferencesKeys.CHARGING_TIME_LAST_CHARGE,
                        PreferencesKeys.PERCENT_ADDED_LAST_CHARGE,
                        PreferencesKeys.MAX_CHARGE_LAST_CHARGE,
                        PreferencesKeys.AVERAGE_CHARGE_LAST_CHARGE,
                        PreferencesKeys.MIN_CHARGE_LAST_CHARGE ->
                            (value as? Number)?.toInt()?.let { v -> pref?.edit { putInt(key, v) } }

                        PreferencesKeys.CAPACITY_ADDED, PreferencesKeys.NUMBER_OF_CYCLES,
                        PreferencesKeys.CURRENT_CAPACITY_LAST_CHARGE,
                        PreferencesKeys.CAPACITY_ADDED_LAST_CHARGE,
                        PreferencesKeys.FAST_CHARGE_WATTS_LAST_CHARGE,
                        PreferencesKeys.MAX_TEMP_CELSIUS_LAST_CHARGE,
                        PreferencesKeys.MAX_TEMP_FAHRENHEIT_LAST_CHARGE,
                        PreferencesKeys.AVERAGE_TEMP_CELSIUS_LAST_CHARGE,
                        PreferencesKeys.AVERAGE_TEMP_FAHRENHEIT_LAST_CHARGE,
                        PreferencesKeys.MIN_TEMP_CELSIUS_LAST_CHARGE,
                        PreferencesKeys.MIN_TEMP_FAHRENHEIT_LAST_CHARGE,
                        PreferencesKeys.VOLTAGE_LAST_CHARGE,
                        PreferencesKeys.STRESS_CALIBRATED_RINT_MOHM ->
                            (value as? Number)?.toFloat()?.let { v -> pref?.edit { putFloat(key, v) } }

                        PreferencesKeys.STATUS_LAST_CHARGE,
                        PreferencesKeys.SOURCE_OF_POWER_LAST_CHARGE ->
                            (value as? String)?.let { v -> pref?.edit { putString(key, v) } }

                        PreferencesKeys.IS_FAST_CHARGE_LAST_CHARGE ->
                            (value as? Boolean)?.let { v -> pref?.edit { putBoolean(key, v) } }
                    }
                }
            }
        }
    }
}