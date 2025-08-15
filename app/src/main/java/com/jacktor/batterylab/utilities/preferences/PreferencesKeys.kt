package com.jacktor.batterylab.utilities.preferences

object PreferencesKeys {

    // Service & Notification
    const val POWER_CONNECTION_SERVICE = "power_connection_service"
    const val SHOW_STOP_SERVICE = "show_stop_service"
    const val SERVICE_TIME = "service_time"
    const val STOP_THE_SERVICE_WHEN_THE_CD = "stop_the_service_when_the_cd"
    const val SHOW_BATTERY_INFORMATION = "show_battery_information"
    const val SHOW_EXPANDED_NOTIFICATION = "show_expanded_notification"
    const val IS_SHOW_BATTERY_INFORMATION = "is_show_battery_information"

    // Battery Status Information
    const val BYPASS_DND = "bypass_dnd_mode"
    const val NOTIFY_OVERHEAT_OVERCOOL = "notify_overheat_overcool"
    const val OVERHEAT_DEGREES = "overheat_degrees"
    const val OVERCOOL_DEGREES = "overcool_degrees"
    const val NOTIFY_BATTERY_IS_FULLY_CHARGED = "notify_battery_is_fully_charged"
    const val NOTIFY_FULL_CHARGE_REMINDER = "notify_full_charge_reminder"
    const val NOTIFY_BATTERY_IS_CHARGED = "notify_battery_is_charged"
    const val BATTERY_LEVEL_NOTIFY_CHARGED = "battery_level_notify_charged"
    const val NOTIFY_BATTERY_IS_DISCHARGED = "notify_battery_is_discharged"
    const val NOTIFY_BATTERY_IS_DISCHARGED_VOLTAGE = "notify_battery_is_discharged_voltage"
    const val BATTERY_LEVEL_NOTIFY_DISCHARGED = "battery_level_notify_discharged"
    const val BATTERY_NOTIFY_DISCHARGED_VOLTAGE = "battery_notify_discharged_voltage"
    const val FULL_CHARGE_REMINDER_FREQUENCY = "full_charge_reminder_frequency"

    // Appearance
    const val AUTO_DARK_MODE = "auto_dark_mode" // Android 10+
    const val DARK_MODE = "dark_mode"
    const val TEXT_SIZE = "text_size"
    const val TEXT_FONT = "text_font"
    const val TEXT_STYLE = "text_style"

    // Misc
    const val FAST_CHARGE_SETTING = "is_fast_charge_setting"
    const val CAPACITY_IN_WH = "capacity_in_wh"
    const val CHARGING_DISCHARGE_CURRENT_IN_WATT = "charging_discharge_current_in_watt"
    const val RESET_SCREEN_TIME_AT_ANY_CHARGE_LEVEL = "reset_screen_time_at_any_charge_level"
    const val TAB_ON_APPLICATION_LAUNCH = "tab_on_application_launch"
    const val UNIT_OF_CHARGE_DISCHARGE_CURRENT = "unit_of_charge_discharge_current"
    const val UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY = "unit_of_measurement_of_current_capacity"
    const val VOLTAGE_UNIT = "voltage_unit"
    const val DESIGN_CAPACITY = "design_capacity"

    // Overlay Preferences
    const val ENABLED_OVERLAY = "enabled_overlay"
    const val ONLY_VALUES_OVERLAY = "only_values_overlay"

    // Overlay Appearance
    const val OVERLAY_SIZE = "overlay_size"
    const val OVERLAY_FONT = "overlay_font"
    const val OVERLAY_TEXT_STYLE = "overlay_text_style"
    const val OVERLAY_TEXT_COLOR = "overlay_text_color"
    const val OVERLAY_OPACITY = "overlay_opacity"
    const val OVERLAY_LOCATION = "overlay_location"

    // Show/Hide
    const val BATTERY_LEVEL_OVERLAY = "battery_level_overlay"
    const val NUMBER_OF_CHARGES_OVERLAY = "number_of_charges_overlay"
    const val NUMBER_OF_FULL_CHARGES_OVERLAY = "number_of_full_charges_overlay"
    const val NUMBER_OF_CYCLES_OVERLAY = "number_of_cycles_overlay"
    const val NUMBER_OF_CYCLES_ANDROID_OVERLAY = "number_of_cycles_android_overlay"
    const val CHARGING_TIME_OVERLAY = "charging_time_overlay"
    const val CHARGING_TIME_REMAINING_OVERLAY = "charging_time_remaining_overlay"
    const val REMAINING_BATTERY_TIME_OVERLAY = "remaining_battery_time_overlay"
    const val SCREEN_TIME_OVERLAY = "screen_time_overlay"
    const val CURRENT_CAPACITY_OVERLAY = "current_capacity_overlay"
    const val CAPACITY_ADDED_OVERLAY = "capacity_added_overlay"
    const val BATTERY_HEALTH_ANDROID_OVERLAY = "battery_health_android_overlay"
    const val RESIDUAL_CAPACITY_OVERLAY = "residual_capacity_overlay"
    const val STATUS_OVERLAY = "status_overlay"
    const val SOURCE_OF_POWER = "source_of_power_overlay"
    const val CHARGE_DISCHARGE_CURRENT_OVERLAY = "charge_discharge_current_overlay"
    const val FAST_CHARGE_OVERLAY = "fast_charge_overlay"
    const val MAX_CHARGE_DISCHARGE_CURRENT_OVERLAY = "max_charge_discharge_current_overlay"
    const val AVERAGE_CHARGE_DISCHARGE_CURRENT_OVERLAY =
        "average_charge_discharge_current_overlay"
    const val MIN_CHARGE_DISCHARGE_CURRENT_OVERLAY = "min_charge_discharge_current_overlay"
    const val CHARGING_CURRENT_LIMIT_OVERLAY = "charging_current_limit_overlay"
    const val TEMPERATURE_OVERLAY = "temperature_overlay"
    const val MAXIMUM_TEMPERATURE_OVERLAY = "maximum_temperature_overlay"
    const val AVERAGE_TEMPERATURE_OVERLAY = "average_temperature_overlay"
    const val MINIMUM_TEMPERATURE_OVERLAY = "minimum_temperature_overlay"
    const val VOLTAGE_OVERLAY = "voltage_overlay"
    const val LAST_CHARGE_TIME_OVERLAY = "last_charge_time_overlay"
    const val BATTERY_WEAR_OVERLAY = "battery_wear_overlay"

    // Debug
    const val ENABLED_DEBUG_OPTIONS = "enabled_debug_options"
    const val FORCIBLY_SHOW_RATE_THE_APP = "forcibly_show_rate_the_app"
    const val AUTO_START_BOOT = "auto_start_boot"
    //const val AUTO_START_OPEN_APP = "auto_start_open_app"
    const val AUTO_START_UPDATE_APP = "auto_start_update_app"

    // Battery Information
    const val NUMBER_OF_CHARGES = "number_of_charges"
    const val CAPACITY_ADDED = "capacity_added"
    const val PERCENT_ADDED = "percent_added"
    const val RESIDUAL_CAPACITY = "residual_capacity"
    const val LAST_CHARGE_TIME = "last_charge_time"
    const val BATTERY_LEVEL_WITH = "battery_level_with"
    const val BATTERY_LEVEL_TO = "battery_level_to"
    const val NUMBER_OF_CYCLES = "number_of_cycles"
    const val NUMBER_OF_FULL_CHARGES = "number_of_full_charges"

    // Power Connection Preferences
    const val AC_CONNECTED_SOUND = "ac_connected_sound"
    const val USB_CONNECTED_SOUND = "usb_connected_sound"
    const val DISCONNECTED_SOUND = "disconnected_sound"
    const val ENABLE_VIBRATION = "enable_vibration"
    const val ENABLE_TOAST = "enable_toast"
    const val VIBRATE_MODE = "vibrate_mode"
    const val CUSTOM_VIBRATE_DURATION = "custom_vibrate_duration"
    const val SOUND_DELAY = "sound_delay"

    // Other
    const val IS_REQUEST_RATE_THE_APP = "is_request_rate_the_app"
    const val UPDATE_TEMP_SCREEN_TIME = "update_temp_screen_time"
}