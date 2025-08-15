package com.jacktor.batterylab.interfaces

import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.common.IntentSenderForResultStarter
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.isImmediateUpdateAllowed
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.R
import com.jacktor.batterylab.fragments.AboutFragment
import com.jacktor.batterylab.interfaces.NavigationInterface.Companion.mainActivityRef
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UPDATE_TEMP_SCREEN_TIME

interface CheckUpdateInterface {

    fun MainActivity.checkUpdateFromGooglePlay() {
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            val isUpdateAvailable = isUpdateAvailable(appUpdateInfo)
            val updateType = if (appUpdateInfo.isImmediateUpdateAllowed) AppUpdateType.IMMEDIATE
            else AppUpdateType.FLEXIBLE

            if (isUpdateDeveloperTriggered(appUpdateInfo)) {
                intentResultStarter()
                val appUpdateOptions =
                    AppUpdateOptions.newBuilder(updateType).setAllowAssetPackDeletion(false).build()
                startUpdate(
                    appUpdateManager, appUpdateInfo, updateFlowResultLauncher,
                    appUpdateOptions
                )
            } else if (isUpdateAvailable) {
                pref?.apply {
                    if (contains(UPDATE_TEMP_SCREEN_TIME))
                        pref?.edit { remove(UPDATE_TEMP_SCREEN_TIME) }
                }
                intentResultStarter()
                val appUpdateOptions =
                    AppUpdateOptions.newBuilder(updateType).setAllowAssetPackDeletion(false).build()
                isCheckUpdateFromGooglePlay = false
                updateAvailableDialog(appUpdateManager, appUpdateInfo, appUpdateOptions)
            }
        }
    }

    fun AboutFragment.checkUpdateFromGooglePlay() {
        val appUpdateManager = AppUpdateManagerFactory.create(requireContext())
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            val isUpdateAvailable = isUpdateAvailable(appUpdateInfo)
            val updateType = if (appUpdateInfo.isImmediateUpdateAllowed) AppUpdateType.IMMEDIATE
            else AppUpdateType.FLEXIBLE
            if (isUpdateDeveloperTriggered(appUpdateInfo)) {
                val updateFlowResultLauncher = mainActivityRef?.get()?.updateFlowResultLauncher
                mainActivityRef?.get()?.intentResultStarter()
                val appUpdateOptions =
                    AppUpdateOptions.newBuilder(updateType).setAllowAssetPackDeletion(false).build()
                startUpdate(
                    appUpdateManager, appUpdateInfo, updateFlowResultLauncher!!,
                    appUpdateOptions
                )
            }
            if (isUpdateAvailable) {
                pref?.edit {
                    putLong(
                        UPDATE_TEMP_SCREEN_TIME,
                        BatteryLabService.instance?.screenTime ?: 0L
                    )
                }
                mainActivityRef?.get()?.intentResultStarter()
                val updateFlowResultLauncher = mainActivityRef?.get()?.updateFlowResultLauncher
                val appUpdateOptions =
                    AppUpdateOptions.newBuilder(updateType).setAllowAssetPackDeletion(false).build()
                startUpdate(
                    appUpdateManager, appUpdateInfo, updateFlowResultLauncher!!,
                    appUpdateOptions
                )
            } else {
                pref?.apply {
                    if (contains(UPDATE_TEMP_SCREEN_TIME))
                        pref?.edit { remove(UPDATE_TEMP_SCREEN_TIME) }
                }
                mainActivityRef?.get()?.isCheckUpdateFromGooglePlay = true
                Toast.makeText(requireContext(), R.string.update_not_found, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun MainActivity.updateAvailableDialog(
        appUpdateManager: AppUpdateManager,
        appUpdateInfo: AppUpdateInfo,
        appUpdateOptions: AppUpdateOptions
    ) {
        MaterialAlertDialogBuilder(this).apply {
            setIcon(R.drawable.ic_check_update_24dp)
            setTitle(R.string.update_available_dialog_title)
            setMessage(R.string.update_available_dialog_message)
            setPositiveButton(R.string.update) { _, _ ->
                pref?.edit {
                    putLong(
                        UPDATE_TEMP_SCREEN_TIME,
                        BatteryLabService.instance?.screenTime ?: 0L
                    )
                }
                startUpdate(
                    appUpdateManager, appUpdateInfo, updateFlowResultLauncher,
                    appUpdateOptions
                )
            }
            setNegativeButton(R.string.later_update) { _, _ ->
                pref?.apply {
                    if (contains(UPDATE_TEMP_SCREEN_TIME))
                        pref?.edit { remove(UPDATE_TEMP_SCREEN_TIME) }
                }

                isCheckUpdateFromGooglePlay = true
            }
            setCancelable(false)
            show()
        }
    }

    private fun MainActivity.intentResultStarter() =
        IntentSenderForResultStarter { intent, _, fillInIntent, flagsMask, flagsValues,
                                       _, _ ->
            val request = IntentSenderRequest.Builder(intent).setFillInIntent(fillInIntent)
                .setFlags(flagsValues, flagsMask).build()
            updateFlowResultLauncher.launch(request)
        }

    private fun isUpdateAvailable(appUpdateInfo: AppUpdateInfo): Boolean {
        val isUpdateAvailable = appUpdateInfo.updateAvailability() ==
                UpdateAvailability.UPDATE_AVAILABLE
        val isUpdateAllowed = appUpdateInfo.isImmediateUpdateAllowed
                || appUpdateInfo.isFlexibleUpdateAllowed
        return isUpdateAvailable && isUpdateAllowed
    }

    private fun isUpdateDeveloperTriggered(appUpdateInfo: AppUpdateInfo) =
        appUpdateInfo.updateAvailability() ==
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS

    private fun startUpdate(
        appUpdateManager: AppUpdateManager, appUpdateInfo: AppUpdateInfo,
        updateFlowResultLauncher: ActivityResultLauncher<IntentSenderRequest>,
        appUpdateOptions: AppUpdateOptions
    ) = appUpdateManager
        .startUpdateFlowForResult(
            appUpdateInfo, updateFlowResultLauncher,
            appUpdateOptions
        )
}