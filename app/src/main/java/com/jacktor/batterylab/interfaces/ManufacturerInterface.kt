package com.jacktor.batterylab.interfaces

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.SetupPermissionHelper
import com.jacktor.batterylab.utilities.Constants

interface ManufacturerInterface {

    fun MainActivity.checkManufacturer() {
        try {
            if (
                SetupPermissionHelper.isXiaomi(this) &&
                isIgnoringBatteryOptimizations() &&
                SetupPermissionHelper.getXiaomiAutostartState(this@checkManufacturer) == false &&
                showXiaomiAutostartDialog == null &&
                showRequestIgnoringBatteryOptimizationsDialog == null
            ) {
                showXiaomiAutoStartDialog()
            } else if (SetupPermissionHelper.needsHuaweiAdvisory()) {
                showHuaweiInfo()
            }
        } catch (_: Exception) {
        }
    }

    fun MainActivity.recheckXiaomiAutoStart() {
        val autostartEnabled = SetupPermissionHelper.getXiaomiAutostartState(this)
        if (
            SetupPermissionHelper.isXiaomi(this) &&
            isIgnoringBatteryOptimizations() &&
            autostartEnabled == false &&
            showXiaomiAutostartDialog == null &&
            showRequestIgnoringBatteryOptimizationsDialog == null
        ) {
            showXiaomiAutoStartDialog()
        } else if (autostartEnabled == true) {
            showXiaomiAutostartDialog = null
        }
    }

    fun isXiaomi(): Boolean = SetupPermissionHelper.isXiaomi()

    private fun isHuawei(): Boolean = SetupPermissionHelper.isHuawei()

    private fun MainActivity.showXiaomiAutoStartDialog() {
        isShowXiaomiBackgroundActivityControlDialog = true
        showXiaomiAutostartDialog = MaterialAlertDialogBuilder(this).apply {
            setIcon(R.drawable.ic_instruction_not_supported_24dp)
            setTitle(getString(R.string.information))
            setMessage(getString(R.string.auto_start_xiaomi_dialog))
            setPositiveButton(android.R.string.ok) { _, _ ->
                if (!SetupPermissionHelper.openXiaomiAutostartSettings(this@showXiaomiAutoStartDialog)) {
                    showFailedOpenSecurityMIUIDialog()
                }
                showXiaomiAutostartDialog = null
            }
            setCancelable(false)
            show()
        }
    }

    private fun MainActivity.showFailedOpenSecurityMIUIDialog() {
        if (!SetupPermissionHelper.isXiaomi(this)) return
        MaterialAlertDialogBuilder(this).apply {
            setIcon(R.drawable.ic_instruction_not_supported_24dp)
            setTitle(R.string.error)
            setMessage(R.string.failed_open_security_miui)
            setPositiveButton(android.R.string.ok) { d, _ ->
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "${Constants.DONT_KILL_MY_APP_LINK}/xiaomi".toUri()
                        )
                    )
                } catch (_: ActivityNotFoundException) {
                    d.dismiss()
                }
            }
            setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            setCancelable(false)
            show()
        }
    }

    private fun MainActivity.showHuaweiInfo() {
        if (!isHuawei() || showHuaweiInformation != null) return
        buildAlertDialog(
            messageRes = R.string.huawei_honor_information,
            onDismiss = { showHuaweiInformation = null }
        ).also { dialog ->
            showHuaweiInformation = dialog
        }
    }

    private fun MainActivity.buildAlertDialog(
        messageRes: Int,
        positiveAction: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ): MaterialAlertDialogBuilder {
        return MaterialAlertDialogBuilder(this).apply {
            setIcon(R.drawable.ic_instruction_not_supported_24dp)
            setTitle(getString(R.string.information))
            setMessage(getString(messageRes))
            setPositiveButton(android.R.string.ok) { dialog, _ ->
                positiveAction?.invoke()
                onDismiss?.invoke()
                dialog.dismiss()
            }
            setCancelable(false)
            show()
        }
    }
}
