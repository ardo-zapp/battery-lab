package com.jacktor.batterylab.helpers

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.jacktor.batterylab.adapters.WizardStepType
import com.jacktor.batterylab.utilities.Constants
import xyz.kumaraswamy.autostart.Autostart
import xyz.kumaraswamy.autostart.Utils
import java.util.Locale

/**
 * Shared runtime & OEM permission status checks and system settings launchers
 * used across the Setup Wizard and runtime fallback helpers.
 */
object SetupPermissionHelper {

    private val XIAOMI_FAMILY_BRANDS = setOf(
        "XIAOMI",
        "POCO",
        "REDMI",
        "BLACK SHARK",
        "BLACKSHARK"
    )

    fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isNotificationPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    internal fun isXiaomiFamilyDevice(manufacturer: String?, brand: String?): Boolean {
        val mfg = manufacturer?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val brd = brand?.trim()?.uppercase(Locale.ROOT).orEmpty()
        return mfg in XIAOMI_FAMILY_BRANDS || brd in XIAOMI_FAMILY_BRANDS
    }

    fun isXiaomi(@Suppress("UNUSED_PARAMETER") context: Context? = null): Boolean {
        if (!isXiaomiFamilyDevice(Build.MANUFACTURER, Build.BRAND)) return false
        return runCatching { Utils.isOnMiui() }.getOrDefault(false)
    }

    fun isHuawei(): Boolean {
        val manufacturer = Build.MANUFACTURER.uppercase(Locale.ROOT)
        val brand = Build.BRAND.uppercase(Locale.ROOT)
        return manufacturer in setOf("HUAWEI", "HONOR") || brand in setOf("HUAWEI", "HONOR")
    }

    fun needsHuaweiAdvisory(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isHuawei()

    /**
     * Returns true/false when Xiaomi autostart state can be read reliably, or null if OEM blocks lookup.
     */
    fun getXiaomiAutostartState(context: Context): Boolean? {
        if (!isXiaomi()) return null
        return runCatching {
            when (Autostart.getAutoStartState(context)) {
                Autostart.State.ENABLED -> true
                Autostart.State.DISABLED -> false
                Autostart.State.NO_INFO,
                Autostart.State.UNEXPECTED_RESULT -> null
            }
        }.getOrNull()
    }

    fun hasUsageStatsPermission(context: Context): Boolean =
        AppUsageHelper.hasUsageStatsPermission(context)

    fun getMissingEssentialSteps(context: Context): List<WizardStepType> = buildList {
        if (needsNotificationPermission() && !isNotificationPermissionGranted(context)) {
            add(WizardStepType.NOTIFICATIONS)
        }
        if (!isIgnoringBatteryOptimizations(context)) {
            add(WizardStepType.BATTERY_OPTIMIZATION)
        }
        if (isXiaomi() && getXiaomiAutostartState(context) == false) {
            add(WizardStepType.XIAOMI_AUTOSTART)
        }
    }

    fun hasMissingEssentialPermissions(context: Context): Boolean =
        getMissingEssentialSteps(context).isNotEmpty()

    fun openAppNotificationSettings(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            }
        )
        return launchFirstAvailable(context, intents)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Intent.ACTION_VIEW, Constants.DONT_KILL_MY_APP_LINK.toUri())
        )
        return launchFirstAvailable(context, intents)
    }

    fun openXiaomiAutostartSettings(context: Context): Boolean {
        val intents = listOf(
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            Intent(Intent.ACTION_VIEW, "${Constants.DONT_KILL_MY_APP_LINK}/xiaomi".toUri())
        )
        return launchFirstAvailable(context, intents)
    }

    fun openUsageAccessSettings(context: Context): Boolean =
        AppUsageHelper.openUsageAccessSettings(context)

    private fun launchFirstAvailable(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            } catch (_: Throwable) {
            }
        }
        return false
    }
}
