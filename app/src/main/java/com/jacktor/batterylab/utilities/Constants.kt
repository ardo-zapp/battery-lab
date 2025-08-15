package com.jacktor.batterylab.utilities

import kotlin.time.Duration.Companion.minutes

object Constants {
    const val IMPORT_RESTORE_SETTINGS_EXTRA = "import_settings"
    const val GOOGLE_PLAY_APP_LINK =
        "https://play.google.com/store/apps/details?id=com.jacktor.batterylab"
    const val GITHUB_LINK = "https://github.com/ardo-zapp/battery-lab"
    const val GITHUB_LINK_BATTERY_CAPCITY = "https://github.com/Ph03niX-X/CapacityInfo"
    const val BACKEND_API_CONTRIBUTORS =
        "https://jacktor.com/api/get/github-contributors?repo=ardo-zapp/battery-lab"
    const val GOOGLE_PLAY_PACKAGE_NAME = "com.android.vending"
    const val SERVICE_CHANNEL_ID = "service_channel"
    const val OVERHEAT_OVERCOOL_CHANNEL_ID = "overheat_overcool"
    const val FULLY_CHARGED_CHANNEL_ID = "fully_charged_channel"
    const val CHARGED_CHANNEL_ID = "charged_channel"
    const val DISCHARGED_CHANNEL_ID = "discharged_channel"
    const val DISCHARGED_VOLTAGE_CHANNEL_ID = "discharged_voltage_channel"
    const val ENABLED_DEBUG_OPTIONS_HOST = "243243622023" // CIDBGENABLED
    const val DISABLED_DEBUG_OPTIONS_HOST = "2432434722023" // CIDBGDISABLED
    const val NUMBER_OF_CYCLES_PATH = "/sys/class/power_supply/battery/cycle_count"
    const val EXPORT_SETTINGS_REQUEST_CODE = 0
    const val IMPORT_SETTINGS_REQUEST_CODE = 1
    const val OPEN_DOCUMENT_TREE_REQUEST_CODE = 4
    const val OPEN_APP_REQUEST_CODE = 0
    const val CLOSE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE = 0
    const val DISABLE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE = 1
    const val POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE = 2
    const val NOTIFY_FULL_CHARGE_REMINDER_JOB_ID = 0
    const val HISTORY_COUNT_MAX = 1500
    const val EXPORT_HISTORY_REQUEST_CODE = 2
    const val IMPORT_HISTORY_REQUEST_CODE = 3
    const val EXPORT_NOTIFICATION_SOUNDS_REQUEST_CODE = 0
    const val STOP_SERVICE_REQUEST_CODE = 1
    const val NOMINAL_BATTERY_VOLTAGE = 3.87
    const val CHARGING_VOLTAGE_WATT = 5.0
    const val DONT_KILL_MY_APP_LINK = "https://dontkillmyapp.com"

    val SERVICE_WAKELOCK_TIMEOUT = 1.minutes.inWholeMilliseconds
}