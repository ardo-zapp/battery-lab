package com.jacktor.batterylab.interfaces

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.R
import com.jacktor.batterylab.utilities.Constants
import xyz.kumaraswamy.autostart.Autostart
import xyz.kumaraswamy.autostart.Utils
import java.util.Locale

interface ManufacturerInterface {

    fun MainActivity.checkManufacturer() {
        try {
            if (
                isXiaomi() &&
                isIgnoringBatteryOptimizations() &&
                !Autostart.isAutoStartEnabled(this@checkManufacturer) &&
                showXiaomiAutostartDialog == null &&
                showRequestIgnoringBatteryOptimizationsDialog == null
            ) {
                showXiaomiAutoStartDialog()
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isHuawei()) {
                showHuaweiInfo()
            }
        } catch (_: Exception) {
        }
    }

    fun MainActivity.recheckXiaomiAutoStart() {
        if (
            isXiaomi() &&
            isIgnoringBatteryOptimizations() &&
            !Autostart.isAutoStartEnabled(this) &&
            showXiaomiAutostartDialog == null &&
            showRequestIgnoringBatteryOptimizationsDialog == null
        ) {
            showXiaomiAutoStartDialog()
        } else if (Autostart.isAutoStartEnabled(this)) {
            showXiaomiAutostartDialog = null
        }
    }

    private fun getManufacturer(): String =
        Build.MANUFACTURER.uppercase(Locale.getDefault())

    fun isXiaomi(): Boolean =
        getManufacturer() in listOf("XIAOMI", "POCO", "REDMI", "BLACK SHARK") && Utils.isOnMiui()

    private fun isHuawei(): Boolean =
        getManufacturer() in listOf("HUAWEI", "HONOR")

    private fun MainActivity.showXiaomiAutoStartDialog() {
        isShowXiaomiBackgroundActivityControlDialog = true
        showXiaomiAutostartDialog = MaterialAlertDialogBuilder(this).apply {
            setIcon(R.drawable.ic_instruction_not_supported_24dp)
            setTitle(getString(R.string.information))
            setMessage(getString(R.string.auto_start_xiaomi_dialog))
            setPositiveButton(android.R.string.ok) { _, _ ->
                try {
                    startActivity(Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT))
                } catch (_: ActivityNotFoundException) {
                    try {
                        startActivity(
                            Intent().setClassName(
                                "com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity"
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        showFailedOpenSecurityMIUIDialog()
                    }
                } finally {
                    showXiaomiAutostartDialog = null
                }
            }
            setCancelable(false)
            show()
        }
    }

    private fun MainActivity.showFailedOpenSecurityMIUIDialog() {
        if (!isXiaomi()) return
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
