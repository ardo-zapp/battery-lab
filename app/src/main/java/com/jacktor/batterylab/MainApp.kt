package com.jacktor.batterylab

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.helpers.ThemeHelper
import com.jacktor.batterylab.interfaces.NavigationInterface.Companion.mainActivityRef
import com.jacktor.batterylab.utilities.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.Serializable
import kotlin.system.exitProcess

class MainApp : Application() {

    companion object {

        var batteryIntent: Intent? = null
        var isPowerConnected = false
        var isUpdateApp = false
        var isInstalledGooglePlay = true

        var currentTheme = -1

        var tempScreenTime = 0L

        @Suppress("DEPRECATION")
        fun isGooglePlay(context: Context) =

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Constants.GOOGLE_PLAY_PACKAGE_NAME == context.packageManager.getInstallSourceInfo(
                    context.packageName
                ).installingPackageName
            else Constants.GOOGLE_PLAY_PACKAGE_NAME == context.packageManager
                .getInstallerPackageName(context.packageName)

        fun restartApp(context: Context, prefArrays: HashMap<String, Any?>) {

            val packageManager = context.packageManager

            val componentName = packageManager.getLaunchIntentForPackage(
                context.packageName
            )?.component

            val intent = Intent.makeRestartActivityTask(componentName)

            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK

            intent?.putExtra(Constants.IMPORT_RESTORE_SETTINGS_EXTRA, prefArrays)

            context.startActivity(intent)

            exitProcess(0)
        }

        fun <T : Serializable?> getSerializable(
            activity: Activity,
            name: String,
            clazz: Class<T>
        ): T? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                activity.intent.getSerializableExtra(name, clazz)
            else {
                @Suppress("DEPRECATION", "UNCHECKED_CAST")
                activity.intent.getSerializableExtra(name) as T
            }
        }
    }

    override fun onCreate() {

        super.onCreate()

        // create a new script file
        val file = File(filesDir.path, Constants.SCRIPT_FILE_NAME)
        if (!file.exists()) {
            file.writeText("#!/bin/bash")
        }

        isInstalledGooglePlay = isInstalledGooglePlay()

        ThemeHelper.setTheme(this)

        currentTheme = ThemeHelper.currentTheme(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {

        super.onConfigurationChanged(newConfig)

        val newTheme = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK or
                newConfig.uiMode and Configuration.UI_MODE_NIGHT_YES or
                newConfig.uiMode and Configuration.UI_MODE_NIGHT_NO

        if (newTheme != currentTheme) {

            MainActivity.Companion.apply {
                tempFragment = mainActivityRef?.get()?.fragment

                isRecreate = true

                mainActivityRef?.get()?.recreate()
            }
        }
    }

    private fun isInstalledGooglePlay(): Boolean {

        return try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                packageManager.getPackageInfo(
                    Constants.GOOGLE_PLAY_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            else packageManager.getPackageInfo(Constants.GOOGLE_PLAY_PACKAGE_NAME, 0)

            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
