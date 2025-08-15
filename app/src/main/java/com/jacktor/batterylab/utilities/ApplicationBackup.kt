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
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        prefArrays = pref.all
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
        prefsTempList.forEach { it ->
            with(prefArrays) {
                when {
                    this?.containsKey(it) == false -> pref?.edit { remove(it) }
                    else -> {
                        this?.forEach {
                            when (it.key) {
                                PreferencesKeys.NUMBER_OF_CHARGES -> pref?.edit {
                                    putLong(
                                        it.key,
                                        it.value as Long
                                    )
                                }

                                PreferencesKeys.BATTERY_LEVEL_TO, PreferencesKeys.BATTERY_LEVEL_WITH, PreferencesKeys.DESIGN_CAPACITY,
                                PreferencesKeys.RESIDUAL_CAPACITY, PreferencesKeys.PERCENT_ADDED -> pref?.edit {
                                    putInt(
                                        it.key,
                                        it.value as Int
                                    )
                                }

                                PreferencesKeys.CAPACITY_ADDED, PreferencesKeys.NUMBER_OF_CYCLES -> pref?.edit {
                                    putFloat(
                                        it.key,
                                        it.value as Float
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}