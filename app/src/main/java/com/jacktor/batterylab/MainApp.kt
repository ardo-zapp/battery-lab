package com.jacktor.batterylab

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import com.jacktor.batterylab.helpers.ThemeHelper
import com.jacktor.batterylab.interfaces.NavigationInterface.Companion.mainActivityRef
import com.jacktor.batterylab.utilities.Constants
import com.jacktor.premium.Premium
import java.io.Serializable
import kotlin.system.exitProcess

class MainApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Jacktor Premium
        Premium.init(this, "premium")

        initializeTheme()
        isInstalledGooglePlay = checkIfGooglePlayInstalled()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val newTheme = newConfig.uiMode and (Configuration.UI_MODE_NIGHT_MASK or
                Configuration.UI_MODE_NIGHT_YES or
                Configuration.UI_MODE_NIGHT_NO)

        if (newTheme != currentTheme) {
            MainActivity.apply {
                tempFragment = mainActivityRef?.get()?.fragment
                isRecreate = true
                mainActivityRef?.get()?.recreate()
            }
        }
    }

    private fun initializeTheme() {
        ThemeHelper.setTheme(this)
        currentTheme = ThemeHelper.currentTheme(resources.configuration)
    }

    private fun checkIfGooglePlayInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    Constants.GOOGLE_PLAY_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(Constants.GOOGLE_PLAY_PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    companion object {

        var batteryIntent: Intent? = null
        var isPowerConnected = false
        var isUpdateApp = false
        var isInstalledGooglePlay = true
        var currentTheme = -1
        var tempScreenTime = 0L

        fun isGooglePlay(context: Context): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val info = context.packageManager.getInstallSourceInfo(context.packageName)
                    info.installingPackageName == Constants.GOOGLE_PLAY_PACKAGE_NAME
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getInstallerPackageName(context.packageName) ==
                            Constants.GOOGLE_PLAY_PACKAGE_NAME
                }
            } catch (_: Exception) {
                false
            }
        }

        fun restartApp(context: Context, prefArrays: HashMap<String, Any?>) {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.component
                ?.let { Intent.makeRestartActivityTask(it) }
                ?: return

            launchIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            launchIntent.putExtra(Constants.IMPORT_RESTORE_SETTINGS_EXTRA, prefArrays)
            context.startActivity(launchIntent)

            exitProcess(0)
        }

        fun <T : Serializable?> getSerializable(
            activity: Activity,
            name: String,
            clazz: Class<T>
        ): T? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.intent.getSerializableExtra(name, clazz)
            } else {
                @Suppress("DEPRECATION", "UNCHECKED_CAST")
                activity.intent.getSerializableExtra(name) as T?
            }
        }
    }
}
