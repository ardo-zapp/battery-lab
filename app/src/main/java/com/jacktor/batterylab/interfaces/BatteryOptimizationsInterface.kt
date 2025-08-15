package com.jacktor.batterylab.interfaces

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.R
import com.jacktor.batterylab.utilities.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface BatteryOptimizationsInterface {

    fun MainActivity.isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(packageName) == true
    }

    fun MainActivity.showRequestIgnoringBatteryOptimizationsDialog() {
        if (isIgnoringBatteryOptimizations()) {
            showRequestIgnoringBatteryOptimizationsDialog?.dismiss()
            showRequestIgnoringBatteryOptimizationsDialog = null
            return
        }

        if (showRequestIgnoringBatteryOptimizationsDialog != null) return

        isBatteryOptDialogVisible = true

        val messageResId = if (isXiaomi()) {
            R.string.background_activity_control_xiaomi_dialog
        } else {
            R.string.ignoring_battery_optimizations_dialog_message
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_instruction_not_supported_24dp)
            .setTitle(R.string.information)
            .setMessage(messageResId)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                isBatteryOptDialogVisible = false
                showRequestIgnoringBatteryOptimizationsDialog?.dismiss()
                showRequestIgnoringBatteryOptimizationsDialog = null
                requestIgnoringBatteryOptimizations()
            }
            .setCancelable(false)
            .create()

        dialog.setOnDismissListener {
            isBatteryOptDialogVisible = false
            showRequestIgnoringBatteryOptimizationsDialog = null
        }

        showRequestIgnoringBatteryOptimizationsDialog = dialog
        dialog.show()

        lifecycleScope.launch {
            repeat(10) {
                delay(300)
                if (isIgnoringBatteryOptimizations()) {
                    showRequestIgnoringBatteryOptimizationsDialog?.dismiss()
                    showRequestIgnoringBatteryOptimizationsDialog = null
                    isBatteryOptDialogVisible = false
                    recheckXiaomiAutoStart()
                    return@launch
                }
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun MainActivity.requestIgnoringBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            if (showFailedRequestIgnoringBatteryOptimizationsDialog == null)
                showFailedRequestIgnoringBatteryOptimizationsDialog()
        }
    }

    private fun MainActivity.showFailedRequestIgnoringBatteryOptimizationsDialog() {
        val dlg = MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_instruction_not_supported_24dp)
            .setTitle(R.string.error)
            .setMessage(R.string.failed_request_permission)
            .setPositiveButton(android.R.string.ok) { d, _ ->
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Constants.DONT_KILL_MY_APP_LINK.toUri())
                    )
                } catch (_: ActivityNotFoundException) {
                    d.dismiss()
                } finally {
                    showFailedRequestIgnoringBatteryOptimizationsDialog = null
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                showFailedRequestIgnoringBatteryOptimizationsDialog = null
            }
            .setCancelable(false)
            .create()

        showFailedRequestIgnoringBatteryOptimizationsDialog = dlg
        dlg.show()
    }
}
