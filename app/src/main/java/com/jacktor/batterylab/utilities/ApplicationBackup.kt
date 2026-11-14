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
        val prefsTempList = arrayListOf(
            PreferencesKeys.BATTERY_LEVEL_TO,
            PreferencesKeys.BATTERY_LEVEL_WITH,
            PreferencesKeys.DESIGN_CAPACITY,
            PreferencesKeys.CAPACITY_ADDED,
            PreferencesKeys.PERCENT_ADDED,
            PreferencesKeys.RESIDUAL_CAPACITY
        )
        val savedMap = prefArrays ?: return
        prefsTempList.forEach { key ->
            if (!savedMap.containsKey(key)) {
                pref?.edit { remove(key) }
            } else {
                val value = savedMap[key]
                when (key) {
                    PreferencesKeys.NUMBER_OF_CHARGES ->
                        (value as? Number)?.toLong()?.let { v -> pref?.edit { putLong(key, v) } }

                    PreferencesKeys.BATTERY_LEVEL_TO, PreferencesKeys.BATTERY_LEVEL_WITH,
                    PreferencesKeys.DESIGN_CAPACITY, PreferencesKeys.RESIDUAL_CAPACITY,
                    PreferencesKeys.PERCENT_ADDED ->
                        (value as? Number)?.toInt()?.let { v -> pref?.edit { putInt(key, v) } }

                    PreferencesKeys.CAPACITY_ADDED, PreferencesKeys.NUMBER_OF_CYCLES ->
                        (value as? Number)?.toFloat()?.let { v -> pref?.edit { putFloat(key, v) } }
                }
            }
        }
    }
}