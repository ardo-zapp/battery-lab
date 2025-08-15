package com.jacktor.batterylab.interfaces

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databinding.OverlayLayoutBinding
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.helpers.TextAppearanceHelper
import com.jacktor.batterylab.helpers.TimeHelper
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.services.OverlayService
import com.jacktor.batterylab.utilities.Constants.NUMBER_OF_CYCLES_PATH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_CHARGE_DISCHARGE_CURRENT_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_TEMPERATURE_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_HEALTH_ANDROID_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_TO
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_WITH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_WEAR_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_ADDED_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGE_DISCHARGE_CURRENT_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_CURRENT_LIMIT_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_TIME_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_TIME_REMAINING_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CURRENT_CAPACITY_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.ENABLED_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.FAST_CHARGE_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.LAST_CHARGE_TIME_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MAXIMUM_TEMPERATURE_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MAX_CHARGE_DISCHARGE_CURRENT_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MINIMUM_TEMPERATURE_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MIN_CHARGE_DISCHARGE_CURRENT_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CHARGES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CHARGES_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES_ANDROID_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_FULL_CHARGES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_FULL_CHARGES_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.ONLY_VALUES_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERLAY_FONT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERLAY_LOCATION
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERLAY_OPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERLAY_SIZE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERLAY_TEXT_COLOR
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERLAY_TEXT_STYLE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.REMAINING_BATTERY_TIME_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.RESIDUAL_CAPACITY_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SCREEN_TIME_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SOURCE_OF_POWER
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.STATUS_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TEMPERATURE_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.VOLTAGE_OVERLAY
import java.io.File
import java.text.DecimalFormat

interface OverlayInterface : BatteryInfoInterface {

    companion object {

        private lateinit var binding: OverlayLayoutBinding

        private lateinit var layoutParams: ViewGroup.LayoutParams
        private lateinit var pref: SharedPreferences
        var linearLayout: LinearLayoutCompat? = null
        var windowManager: WindowManager? = null

        fun isEnabledOverlay(context: Context, isEnabledOverlay: Boolean = false): Boolean {

            val pref = PreferenceManager.getDefaultSharedPreferences(context)

            with(pref) {

                val overlayArray = arrayListOf(
                    getBoolean(
                        BATTERY_LEVEL_OVERLAY,
                        context.resources.getBoolean(R.bool.battery_level_overlay)
                    ),
                    getBoolean(
                        NUMBER_OF_CHARGES_OVERLAY, context.resources.getBoolean(
                            R.bool.number_of_charges_overlay
                        )
                    ), getBoolean(
                        NUMBER_OF_FULL_CHARGES_OVERLAY, context.resources.getBoolean(
                            R.bool.number_of_full_charges_overlay
                        )
                    ), getBoolean(
                        NUMBER_OF_CYCLES_OVERLAY, context.resources.getBoolean(
                            R.bool.number_of_cycles_overlay
                        )
                    ), getBoolean(
                        NUMBER_OF_CYCLES_ANDROID_OVERLAY, context.resources.getBoolean(
                            R.bool
                                .number_of_cycles_android_overlay
                        )
                    ), getBoolean(
                        CHARGING_TIME_OVERLAY, context.resources.getBoolean(
                            R.bool
                                .charging_time_overlay
                        )
                    ), getBoolean(
                        CHARGING_TIME_REMAINING_OVERLAY, context.resources.getBoolean(
                            R.bool.charging_time_remaining_overlay
                        )
                    ), getBoolean(
                        REMAINING_BATTERY_TIME_OVERLAY, context.resources.getBoolean(
                            R.bool.remaining_battery_time_overlay
                        )
                    ), getBoolean(
                        SCREEN_TIME_OVERLAY, context.resources.getBoolean(
                            R.bool.screen_time_overlay
                        )
                    ), getBoolean(
                        CURRENT_CAPACITY_OVERLAY, context.resources.getBoolean(
                            R.bool.current_capacity_overlay
                        )
                    ), getBoolean(
                        CAPACITY_ADDED_OVERLAY, context.resources.getBoolean(
                            R.bool.capacity_added_overlay
                        )
                    ), getBoolean(
                        BATTERY_HEALTH_ANDROID_OVERLAY, context.resources.getBoolean(
                            R.bool.battery_health_android_overlay
                        )
                    ), getBoolean(
                        RESIDUAL_CAPACITY_OVERLAY, context.resources.getBoolean(
                            R.bool.residual_capacity_overlay
                        )
                    ), getBoolean(
                        STATUS_OVERLAY,
                        context.resources.getBoolean(R.bool.status_overlay)
                    ), getBoolean(
                        SOURCE_OF_POWER, context.resources.getBoolean(
                            R.bool.source_of_power_overlay
                        )
                    ), getBoolean(
                        CHARGE_DISCHARGE_CURRENT_OVERLAY, context.resources.getBoolean(
                            R.bool.charge_discharge_current_overlay
                        )
                    ), getBoolean(
                        FAST_CHARGE_OVERLAY, context.resources.getBoolean(
                            R.bool.fast_charge_overlay
                        )
                    ), getBoolean(
                        MAX_CHARGE_DISCHARGE_CURRENT_OVERLAY, context.resources.getBoolean(
                            R.bool.max_charge_discharge_current_overlay
                        )
                    ), getBoolean(
                        AVERAGE_CHARGE_DISCHARGE_CURRENT_OVERLAY, context.resources.getBoolean(
                            R.bool.average_charge_discharge_current_overlay
                        )
                    ), getBoolean(
                        MIN_CHARGE_DISCHARGE_CURRENT_OVERLAY, context.resources.getBoolean(
                            R.bool.min_charge_discharge_current_overlay
                        )
                    ),
                    getBoolean(
                        CHARGING_CURRENT_LIMIT_OVERLAY, context.resources.getBoolean(
                            R.bool.charging_current_limit_overlay
                        )
                    ), getBoolean(
                        TEMPERATURE_OVERLAY, context.resources.getBoolean(
                            R.bool.temperature_overlay
                        )
                    ), getBoolean(
                        MAXIMUM_TEMPERATURE_OVERLAY, context.resources.getBoolean(
                            R.bool.maximum_temperature_overlay
                        )
                    ), getBoolean(
                        AVERAGE_TEMPERATURE_OVERLAY, context.resources.getBoolean(
                            R.bool.average_temperature_overlay
                        )
                    ), getBoolean(
                        MINIMUM_TEMPERATURE_OVERLAY, context.resources.getBoolean(
                            R.bool.minimum_temperature_overlay
                        )
                    ), getBoolean(
                        VOLTAGE_OVERLAY,
                        context.resources.getBoolean(R.bool.voltage_overlay)
                    ), getBoolean(
                        LAST_CHARGE_TIME_OVERLAY, context.resources.getBoolean(
                            R.bool.last_charge_time_overlay
                        )
                    ), getBoolean(
                        BATTERY_WEAR_OVERLAY, context.resources.getBoolean(
                            R.bool.battery_wear_overlay
                        )
                    )
                )

                overlayArray.forEach {

                    if (Settings.canDrawOverlays(context) && (getBoolean(
                            ENABLED_OVERLAY,
                            context.resources.getBoolean(R.bool.enabled_overlay)
                        )
                                || isEnabledOverlay) && it
                    ) return true
                }
            }

            return false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun onCreateOverlay(context: Context) {

        if (isEnabledOverlay(context)) {

            pref = PreferenceManager.getDefaultSharedPreferences(context)

            windowManager = context.getSystemService(WINDOW_SERVICE) as? WindowManager

            val parameters =
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams
                        .TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )

            val overlayLocation = pref.getString(
                OVERLAY_LOCATION, "${
                    context.resources.getInteger(
                        R.integer.overlay_location_default
                    )
                }"
            )

            parameters.gravity = when (overlayLocation?.toInt()) {
                0 -> Gravity.TOP
                1 -> Gravity.TOP or Gravity.START
                2 -> Gravity.TOP or Gravity.END
                3 -> Gravity.CENTER
                4 -> Gravity.CENTER or Gravity.START
                5 -> Gravity.CENTER or Gravity.END
                6 -> Gravity.BOTTOM
                7 -> Gravity.BOTTOM or Gravity.START
                8 -> Gravity.BOTTOM or Gravity.END
                else -> Gravity.TOP
            }

            parameters.x = 0
            parameters.y = 0

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            onCreateViews(context)

            if (linearLayout != null && linearLayout?.windowToken == null)
                windowManager?.addView(linearLayout, parameters)
            else if (OverlayService.instance != null) {

                Toast.makeText(context, R.string.unknown_error, Toast.LENGTH_LONG).show()

                ServiceHelper.stopService(context, OverlayService::class.java)

                return
            }

            linearLayout?.setOnTouchListener(onLinearLayoutOnTouchListener(parameters))
        } else if (OverlayService.instance != null)
            ServiceHelper.stopService(context, OverlayService::class.java)
    }

    private fun onCreateViews(context: Context) {

        binding = OverlayLayoutBinding.inflate(
            LayoutInflater.from(context), null,
            false
        )

        linearLayout = binding.overlayLinearLayout

        onUpdateOverlay(context)
    }

    fun onUpdateOverlay(context: Context) {
        try {
            batteryIntent = context.registerReceiver(
                null, IntentFilter(
                    Intent
                        .ACTION_BATTERY_CHANGED
                )
            )

            val status = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

            val extraPlugged = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                -1
            ) ?: -1

            val sourceOfPower = getSourceOfPower(
                context, extraPlugged, true,
                pref.getBoolean(
                    ONLY_VALUES_OVERLAY, context.resources.getBoolean(
                        R.bool.only_values_overlay
                    )
                )
            )

            linearLayout?.setBackgroundColor(onSetBackgroundLinearLayout(context))

            onUpdateBatteryLevelOverlay()
            onUpdateNumberOfChargesOverlay()
            onUpdateNumberOfFullChargesOverlay()
            onUpdateNumberOfCyclesOverlay()
            onUpdateNumberOfCyclesAndroidOverlay()
            onUpdateChargingTimeOverlay()
            onUpdateChargingTimeRemainingOverlay(status)
            onUpdateRemainingBatteryTimeOverlay(status)
            onUpdateScreenTimeOverlay()
            onUpdateCurrentCapacityOverlay()
            onUpdateCapacityAddedOverlay()
            onUpdateBatteryHealthOverlay()
            onUpdateResidualCapacityOverlay()
            onUpdateStatusOverlay(status)
            onUpdateSourceOfPowerOverlay(sourceOfPower)
            onUpdateChargeDischargeCurrentOverlay(status)
            onUpdateFastChargeOverlay(status)
            onUpdateMaxChargeDischargeCurrentOverlay(status)
            onUpdateAverageChargeDischargeCurrentOverlay(status)
            onUpdateMinChargeDischargeCurrentOverlay(status)
            onUpdateChargingCurrentLimitOverlay()
            onUpdateTemperatureOverlay()
            onUpdateMaximumTemperatureOverlay()
            onUpdateAverageTemperatureOverlay()
            onUpdateMinimumTemperatureOverlay()
            onUpdateVoltageOverlay()
            onUpdateLastChargeTimeOverlay()
            onUpdateBatteryWearOverlay()
        } catch (_: Exception) {
            return
        }
    }

    private fun onSetBackgroundLinearLayout(context: Context) =
        Color.argb(
            if (pref.getInt(
                    OVERLAY_OPACITY, context.resources.getInteger(
                        R.integer.overlay_opacity_default
                    )
                ) > context.resources.getInteger(
                    R.integer.overlay_opacity_max
                ) || pref.getInt(
                    OVERLAY_OPACITY,
                    context.resources.getInteger(R.integer.overlay_opacity_default)
                ) < 0
            )
                context.resources.getInteger(R.integer.overlay_opacity_default)
            else pref.getInt(
                OVERLAY_OPACITY, context.resources.getInteger(
                    R.integer.overlay_opacity_default
                )
            ), 0, 0, 0
        )

    private fun onUpdateBatteryLevelOverlay() {

        if (pref.getBoolean(
                BATTERY_LEVEL_OVERLAY, binding.batteryLevelOverlay.resources
                    .getBoolean(R.bool.battery_level_overlay)
            ) ||
            binding.batteryLevelOverlay.isVisible
        )
            binding.batteryLevelOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context,
                    this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    ) R.string.battery_level_with_title
                    else R.string.battery_level_overlay_only_values,
                    "${getBatteryLevel(context)}%"
                )

                visibility = if (pref.getBoolean(
                        BATTERY_LEVEL_OVERLAY, context.resources
                            .getBoolean(R.bool.battery_level_overlay)
                    )
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateNumberOfChargesOverlay() {

        if (pref.getBoolean(
                NUMBER_OF_CHARGES_OVERLAY, binding.numberOfChargesOverlay.context
                    .resources.getBoolean(R.bool.number_of_charges_overlay)
            ) ||
            binding.numberOfChargesOverlay.isVisible
        )
            binding.numberOfChargesOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    ) R.string.number_of_charges
                    else R.string.number_of_charges_overlay_only_values, pref.getLong(
                        NUMBER_OF_CHARGES,
                        0
                    )
                )

                visibility = if (pref.getBoolean(
                        NUMBER_OF_CHARGES_OVERLAY, context.resources
                            .getBoolean(R.bool.number_of_charges_overlay)
                    )
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateNumberOfFullChargesOverlay() {

        if (pref.getBoolean(
                NUMBER_OF_FULL_CHARGES_OVERLAY, binding.numberOfFullChargesOverlay
                    .context.resources.getBoolean(R.bool.number_of_charges_overlay)
            )
            || binding.numberOfFullChargesOverlay.isVisible
        )
            binding.numberOfFullChargesOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY,
                            context.resources.getBoolean(R.bool.only_values_overlay)
                        )
                    )
                        R.string.number_of_full_charges else R.string
                        .number_of_full_charges_overlay_only_values, pref.getLong(
                        NUMBER_OF_FULL_CHARGES, 0
                    )
                )

                visibility = if (pref.getBoolean(
                        NUMBER_OF_FULL_CHARGES_OVERLAY,
                        context.resources.getBoolean(R.bool.number_of_full_charges_overlay)
                    )
                )
                    View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateNumberOfCyclesOverlay() {

        if (pref.getBoolean(
                NUMBER_OF_CYCLES_OVERLAY, binding.numberOfCyclesOverlay.context
                    .resources.getBoolean(R.bool.number_of_cycles_overlay)
            ) ||
            binding.numberOfCyclesOverlay.isVisible
        )
            binding.numberOfCyclesOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    ) R.string.number_of_cycles
                    else R.string.number_of_cycles_overlay_only_values,
                    DecimalFormat("#.##").format(
                        pref.getFloat(NUMBER_OF_CYCLES, 0f)
                    )
                )

                visibility = if (pref.getBoolean(
                        NUMBER_OF_CYCLES_OVERLAY, context.resources
                            .getBoolean(R.bool.number_of_cycles_overlay)
                    )
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateNumberOfCyclesAndroidOverlay() {

        if ((pref.getBoolean(
                NUMBER_OF_CYCLES_ANDROID_OVERLAY, binding
                    .numberOfCyclesAndroidOverlay.context.resources.getBoolean(
                        R.bool.number_of_cycles_android_overlay
                    )
            ) && File(
                NUMBER_OF_CYCLES_PATH
            ).exists()) ||
            binding.numberOfCyclesAndroidOverlay.isVisible
        )
            binding.numberOfCyclesAndroidOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    ) R.string
                        .number_of_cycles_android else R.string
                        .number_of_cycles_android_overlay_only_values, getNumberOfCyclesAndroid()
                )

                visibility = if (pref.getBoolean(
                        NUMBER_OF_CYCLES_ANDROID_OVERLAY,
                        context.resources.getBoolean(R.bool.number_of_cycles_android_overlay)
                    )
                )
                    View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateChargingTimeOverlay() {

        if ((pref.getBoolean(
                CHARGING_TIME_OVERLAY, binding.chargingTimeOverlay.context.resources
                    .getBoolean(R.bool.charging_time_overlay)
            )
                    && (BatteryLabService.instance?.seconds ?: 0) > 1) ||
            binding.chargingTimeOverlay.isVisible
        )
            binding.chargingTimeOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = getChargingTime(
                    context, (BatteryLabService.instance?.seconds ?: 0),
                    true, pref.getBoolean(
                        ONLY_VALUES_OVERLAY, context.resources.getBoolean(
                            R.bool.only_values_overlay
                        )
                    )
                )

                visibility = if (pref.getBoolean(
                        CHARGING_TIME_OVERLAY, context.resources
                            .getBoolean(R.bool.charging_time_overlay)
                    ) && (
                            BatteryLabService.instance?.seconds ?: 0) > 1
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateChargingTimeRemainingOverlay(status: Int) {

        if ((pref.getBoolean(
                CHARGING_TIME_REMAINING_OVERLAY, binding.chargingTimeRemainingOverlay
                    .context.resources.getBoolean(R.bool.charging_time_remaining_overlay)
            )
                    && status == BatteryManager.BATTERY_STATUS_CHARGING) ||
            binding.chargingTimeRemainingOverlay.isVisible
        )
            binding.chargingTimeRemainingOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY,
                            context.resources.getBoolean(R.bool.only_values_overlay)
                        )
                    )
                        R.string.charging_time_remaining
                    else R.string.charging_time_remaining_overlay_only_values,
                    getChargingTimeRemaining(context)
                )

                visibility = if (pref.getBoolean(
                        CHARGING_TIME_REMAINING_OVERLAY, context
                            .resources.getBoolean(R.bool.charging_time_remaining_overlay)
                    ) &&
                    status == BatteryManager.BATTERY_STATUS_CHARGING
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateRemainingBatteryTimeOverlay(status: Int) {

        if ((pref.getBoolean(
                REMAINING_BATTERY_TIME_OVERLAY, binding.remainingBatteryTimeOverlay
                    .context.resources.getBoolean(R.bool.remaining_battery_time_overlay)
            )
                    && status != BatteryManager.BATTERY_STATUS_CHARGING) ||
            binding.remainingBatteryTimeOverlay.isVisible
        )
            binding.remainingBatteryTimeOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    ) R.string.remaining_battery_time
                    else R.string.remaining_battery_time_overlay_only_values,
                    getRemainingBatteryTime(
                        context
                    )
                )

                visibility = if (pref.getBoolean(
                        REMAINING_BATTERY_TIME_OVERLAY, context
                            .resources.getBoolean(R.bool.remaining_battery_time_overlay)
                    ) &&
                    status != BatteryManager.BATTERY_STATUS_CHARGING
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateScreenTimeOverlay() {

        if (pref.getBoolean(
                SCREEN_TIME_OVERLAY, binding.screenTimeOverlay.context.resources
                    .getBoolean(R.bool.screen_time_overlay)
            ) ||
            binding.screenTimeOverlay.isVisible
        )

            binding.screenTimeOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY,
                            context.resources.getBoolean(R.bool.only_values_overlay)
                        )
                    )
                        R.string.screen_time else R.string.screen_time_overlay_only_values,
                    TimeHelper.getTime(BatteryLabService.instance?.screenTime ?: 0L)
                )

                visibility = if (pref.getBoolean(
                        SCREEN_TIME_OVERLAY, context.resources
                            .getBoolean(R.bool.screen_time_overlay)
                    )
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateCurrentCapacityOverlay() {

        if ((pref.getBoolean(
                CURRENT_CAPACITY_OVERLAY, binding.currentCapacityOverlay.context
                    .resources.getBoolean(R.bool.current_capacity_overlay)
            )) ||
            binding.currentCapacityOverlay.isVisible
        )
            binding.currentCapacityOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                val isCapacityInWh = pref.getBoolean(
                    PreferencesKeys.CAPACITY_IN_WH,
                    context.resources.getBoolean(R.bool.capacity_in_wh)
                )

                text = if (isCapacityInWh) context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources.getBoolean(
                                R.bool.only_values_overlay
                            )
                        )
                    ) R.string.current_capacity_wh
                    else R.string.current_capacity_wh_overlay_only_values,
                    DecimalFormat("#.#").format(
                        getCapacityInWh(getCurrentCapacity(context))
                    )
                )
                else context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context
                                .resources.getBoolean(R.bool.only_values_overlay)
                        )
                    )
                        R.string.current_capacity else R.string.current_capacity_overlay_only_values,
                    DecimalFormat("#.#").format(getCurrentCapacity(context))
                )

                visibility = if (pref.getBoolean(
                        CURRENT_CAPACITY_OVERLAY,
                        context.resources.getBoolean(R.bool.current_capacity_overlay)
                    )
                )
                    View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateCapacityAddedOverlay() {

        if ((pref.getBoolean(
                CAPACITY_ADDED_OVERLAY, binding.capacityAddedOverlay.context
                    .resources.getBoolean(R.bool.capacity_added_overlay)
            )) ||
            binding.capacityAddedOverlay.isVisible
        )
            binding.capacityAddedOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = getCapacityAdded(
                    context, true, pref.getBoolean(
                        ONLY_VALUES_OVERLAY,
                        context.resources.getBoolean(R.bool.only_values_overlay)
                    )
                )

                visibility = if (pref.getBoolean(
                        CAPACITY_ADDED_OVERLAY, context.resources
                            .getBoolean(R.bool.capacity_added_overlay)
                    )
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateBatteryHealthOverlay() {

        if (pref.getBoolean(
                BATTERY_HEALTH_ANDROID_OVERLAY,
                binding.batteryHealthAndroidOverlay.context.resources
                    .getBoolean(R.bool.battery_health_android_overlay)
            ) ||
            binding.batteryHealthAndroidOverlay.isVisible
        )
            binding.batteryHealthAndroidOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    ) R.string.battery_health_android
                    else R.string.battery_health_overlay_only_values, getBatteryHealth(context)
                )

                visibility = if (pref.getBoolean(
                        BATTERY_HEALTH_ANDROID_OVERLAY, context.resources
                            .getBoolean(R.bool.battery_health_android_overlay)
                    )
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateResidualCapacityOverlay() {

        if ((pref.getBoolean(
                RESIDUAL_CAPACITY_OVERLAY, binding.residualCapacityOverlay.context
                    .resources.getBoolean(R.bool.residual_capacity_overlay)
            )) ||
            binding.residualCapacityOverlay.isVisible
        )
            binding.residualCapacityOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = getResidualCapacity(
                    context, true,
                    pref.getBoolean(
                        ONLY_VALUES_OVERLAY, context.resources.getBoolean(
                            R.bool.only_values_overlay
                        )
                    )
                )

                visibility = if (pref.getBoolean(
                        RESIDUAL_CAPACITY_OVERLAY,
                        context.resources.getBoolean(R.bool.residual_capacity_overlay)
                    )
                )
                    View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateStatusOverlay(status: Int) {

        if (pref.getBoolean(
                STATUS_OVERLAY, binding.statusOverlay.context.resources.getBoolean(
                    R.bool.status_overlay
                )
            ) || binding.statusOverlay.isVisible
        )
            binding.statusOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    ) R.string.status
                    else R.string.status_overlay_only_values, getStatus(context, status)
                )

                visibility = if (pref.getBoolean(
                        STATUS_OVERLAY, context.resources.getBoolean(
                            R.bool.status_overlay
                        )
                    )
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateSourceOfPowerOverlay(sourceOfPower: String) {

        if ((pref.getBoolean(
                SOURCE_OF_POWER, binding.sourceOfPowerOverlay.context.resources
                    .getBoolean(R.bool.source_of_power_overlay)
            ) && sourceOfPower != "N/A")
            || binding.sourceOfPowerOverlay.isVisible
        )
            binding.sourceOfPowerOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = sourceOfPower

                visibility = if (pref.getBoolean(
                        SOURCE_OF_POWER, context.resources.getBoolean(
                            R.bool.source_of_power_overlay
                        )
                    ) && sourceOfPower != "N/A"
                ) View.VISIBLE
                else View.GONE
            }
    }

    private fun onUpdateChargeDischargeCurrentOverlay(status: Int) {

        if (pref.getBoolean(
                CHARGE_DISCHARGE_CURRENT_OVERLAY,
                binding.chargeDischargeCurrentOverlay.context.resources.getBoolean(
                    R.bool.charge_discharge_current_overlay
                )
            ) ||
            binding.chargeDischargeCurrentOverlay.isVisible
        )
            binding.chargeDischargeCurrentOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                val isChargingDischargeCurrentInWatt = pref.getBoolean(
                    PreferencesKeys.CHARGING_DISCHARGE_CURRENT_IN_WATT,
                    context.resources.getBoolean(R.bool.charging_discharge_current_in_watt)
                )

                if (isChargingDischargeCurrentInWatt)
                    text = if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    )
                        context.getString(
                            if (status == BatteryManager.BATTERY_STATUS_CHARGING)
                                R.string.charge_current_watt else R.string.discharge_current_watt,
                            DecimalFormat("#.##").format(
                                getChargeDischargeCurrentInWatt(
                                    getChargeDischargeCurrent(context),
                                    status == BatteryManager.BATTERY_STATUS_CHARGING
                                )
                            )
                        )
                    else context.getString(
                        R.string.charging_discharge_current_watt_overlay_only_values,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                getChargeDischargeCurrent(context),
                                status == BatteryManager.BATTERY_STATUS_CHARGING
                            )
                        )
                    )
                else text = if (!pref.getBoolean(
                        ONLY_VALUES_OVERLAY, context.resources
                            .getBoolean(R.bool.only_values_overlay)
                    )
                )
                    context.getString(
                        if (status == BatteryManager.BATTERY_STATUS_CHARGING)
                            R.string.charge_current else R.string.discharge_current,
                        getChargeDischargeCurrent(context).toString()
                    )
                else context.getString(
                    R.string.charge_discharge_overlay_only_values,
                    getChargeDischargeCurrent(context)
                )

                visibility = if (pref.getBoolean(
                        CHARGE_DISCHARGE_CURRENT_OVERLAY, context
                            .resources.getBoolean(R.bool.charge_discharge_current_overlay)
                    )
                )
                    View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateFastChargeOverlay(status: Int) {

        if (pref.getBoolean(
                FAST_CHARGE_OVERLAY, binding.fastChargeOverlay.context.resources
                    .getBoolean(R.bool.fast_charge_overlay)
            ) ||
            binding.fastChargeOverlay.isVisible
        )
            binding.fastChargeOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = getFastChargeOverlay(context)

                visibility = if (pref.getBoolean(
                        FAST_CHARGE_OVERLAY, context.resources
                            .getBoolean(R.bool.fast_charge_overlay)
                    ) &&
                    status == BatteryManager.BATTERY_STATUS_CHARGING
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateMaxChargeDischargeCurrentOverlay(status: Int) {

        if (pref.getBoolean(
                MAX_CHARGE_DISCHARGE_CURRENT_OVERLAY,
                binding.maxChargeDischargeCurrentOverlay.context.resources.getBoolean(
                    R.bool.max_charge_discharge_current_overlay
                )
            )
            || binding.maxChargeDischargeCurrentOverlay.isVisible
        )
            binding.maxChargeDischargeCurrentOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                val isChargingDischargeCurrentInWatt = pref.getBoolean(
                    PreferencesKeys.CHARGING_DISCHARGE_CURRENT_IN_WATT,
                    resources.getBoolean(R.bool.charging_discharge_current_in_watt)
                )

                if (isChargingDischargeCurrentInWatt)
                    text = if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    ) if (status ==
                        BatteryManager.BATTERY_STATUS_CHARGING
                    ) context.getString(
                        R.string
                            .max_charge_current_watt, DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                BatteryInfoInterface.maxChargeCurrent,
                                true
                            )
                        )
                    )
                    else context.getString(
                        R.string.max_discharge_current_watt,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                BatteryInfoInterface.maxDischargeCurrent
                            )
                        )
                    )
                    else if (status == BatteryManager.BATTERY_STATUS_CHARGING) context.getString(
                        R.string.charging_discharge_current_watt_overlay_only_values,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                BatteryInfoInterface.maxChargeCurrent, true
                            )
                        )
                    )
                    else context.getString(
                        R.string.charging_discharge_current_watt_overlay_only_values,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                BatteryInfoInterface.maxDischargeCurrent
                            )
                        )
                    )
                else text = if (!pref.getBoolean(
                        ONLY_VALUES_OVERLAY, context.resources
                            .getBoolean(R.bool.only_values_overlay)
                    )
                ) if (status ==
                    BatteryManager.BATTERY_STATUS_CHARGING
                ) context.getString(
                    R.string
                        .max_charge_current, BatteryInfoInterface.maxChargeCurrent
                ) else context
                    .getString(
                        R.string.max_discharge_current, BatteryInfoInterface
                            .maxDischargeCurrent
                    ) else if (status == BatteryManager
                        .BATTERY_STATUS_CHARGING
                ) context.getString(
                    R.string.max_charge_discharge_overlay_only_values, BatteryInfoInterface
                        .maxChargeCurrent
                ) else context.getString(
                    R.string.max_charge_discharge_overlay_only_values, BatteryInfoInterface
                        .maxDischargeCurrent
                )

                visibility = if (pref.getBoolean(
                        MAX_CHARGE_DISCHARGE_CURRENT_OVERLAY, context
                            .resources.getBoolean(R.bool.max_charge_discharge_current_overlay)
                    )
                )
                    View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateAverageChargeDischargeCurrentOverlay(status: Int) {

        if (pref.getBoolean(
                AVERAGE_CHARGE_DISCHARGE_CURRENT_OVERLAY,
                binding.averageChargeDischargeCurrentOverlay.context.resources.getBoolean(
                    R.bool.residual_capacity_overlay
                )
            ) ||
            binding.averageChargeDischargeCurrentOverlay.isVisible
        )
            binding.averageChargeDischargeCurrentOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                val isChargingDischargeCurrentInWatt = pref.getBoolean(
                    PreferencesKeys.CHARGING_DISCHARGE_CURRENT_IN_WATT,
                    resources.getBoolean(R.bool.charging_discharge_current_in_watt)
                )

                if (isChargingDischargeCurrentInWatt)
                    text = if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    ) if (status ==
                        BatteryManager.BATTERY_STATUS_CHARGING
                    ) context.getString(
                        R.string
                            .average_charge_current_watt, DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                BatteryInfoInterface.averageChargeCurrent,
                                true
                            )
                        )
                    )
                    else context.getString(
                        R.string.average_discharge_current_watt,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                BatteryInfoInterface.averageDischargeCurrent
                            )
                        )
                    )
                    else if (status == BatteryManager.BATTERY_STATUS_CHARGING) context.getString(
                        R.string.charging_discharge_current_watt_overlay_only_values,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                BatteryInfoInterface.averageChargeCurrent,
                                true
                            )
                        )
                    ) else context.getString(
                        R.string.charging_discharge_current_watt_overlay_only_values,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                BatteryInfoInterface.averageDischargeCurrent
                            )
                        )
                    )
                else text = if (!pref.getBoolean(
                        ONLY_VALUES_OVERLAY, context.resources
                            .getBoolean(R.bool.only_values_overlay)
                    )
                ) if (status ==
                    BatteryManager.BATTERY_STATUS_CHARGING
                ) context.getString(
                    R.string
                        .average_charge_current, BatteryInfoInterface.averageChargeCurrent
                ) else
                    context.getString(
                        R.string.average_discharge_current, BatteryInfoInterface
                            .averageDischargeCurrent
                    ) else if (status == BatteryManager
                        .BATTERY_STATUS_CHARGING
                ) context.getString(
                    R.string.average_charge_discharge_overlay_only_values, BatteryInfoInterface
                        .averageChargeCurrent
                ) else context.getString(
                    R.string.average_charge_discharge_overlay_only_values, BatteryInfoInterface
                        .averageDischargeCurrent
                )

                visibility = if (pref.getBoolean(
                        AVERAGE_CHARGE_DISCHARGE_CURRENT_OVERLAY, this
                            .resources.getBoolean(R.bool.average_charge_discharge_current_overlay)
                    )
                )
                    View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateMinChargeDischargeCurrentOverlay(status: Int) {

        if (pref.getBoolean(
                MIN_CHARGE_DISCHARGE_CURRENT_OVERLAY,
                binding.minChargeDischargeCurrentOverlay.context.resources.getBoolean(
                    R.bool.min_charge_discharge_current_overlay
                )
            ) ||
            binding.minChargeDischargeCurrentOverlay.isVisible
        )
            binding.minChargeDischargeCurrentOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                val isChargingDischargeCurrentInWatt = pref.getBoolean(
                    PreferencesKeys.CHARGING_DISCHARGE_CURRENT_IN_WATT,
                    resources.getBoolean(R.bool.charging_discharge_current_in_watt)
                )

                if (isChargingDischargeCurrentInWatt)
                    text = if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    ) if (status ==
                        BatteryManager.BATTERY_STATUS_CHARGING
                    ) context.getString(
                        R.string
                            .min_charge_current_watt, DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                BatteryInfoInterface.minChargeCurrent,
                                true
                            )
                        )
                    )
                    else context.getString(
                        R.string.min_discharge_current_watt,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                BatteryInfoInterface.minDischargeCurrent
                            )
                        )
                    )
                    else if (status == BatteryManager.BATTERY_STATUS_CHARGING) context.getString(
                        R.string.charging_discharge_current_watt_overlay_only_values,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                BatteryInfoInterface.minChargeCurrent, true
                            )
                        )
                    )
                    else context.getString(
                        R.string.charging_discharge_current_watt_overlay_only_values,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                BatteryInfoInterface.minDischargeCurrent
                            )
                        )
                    )
                else text = if (!pref.getBoolean(
                        ONLY_VALUES_OVERLAY, context.resources
                            .getBoolean(R.bool.only_values_overlay)
                    )
                ) if (status ==
                    BatteryManager.BATTERY_STATUS_CHARGING
                ) context.getString(
                    R.string
                        .min_charge_current, BatteryInfoInterface.minChargeCurrent
                ) else context
                    .getString(
                        R.string.min_discharge_current, BatteryInfoInterface
                            .minDischargeCurrent
                    ) else if (status == BatteryManager
                        .BATTERY_STATUS_CHARGING
                ) context.getString(
                    R.string.min_charge_discharge_overlay_only_values, BatteryInfoInterface
                        .minChargeCurrent
                ) else context.getString(
                    R.string.min_charge_discharge_overlay_only_values, BatteryInfoInterface
                        .minDischargeCurrent
                )

                visibility = if (pref.getBoolean(
                        MIN_CHARGE_DISCHARGE_CURRENT_OVERLAY, context
                            .resources.getBoolean(R.bool.min_charge_discharge_current_overlay)
                    )
                )
                    View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateChargingCurrentLimitOverlay() {

        if (pref.getBoolean(
                CHARGING_CURRENT_LIMIT_OVERLAY, binding.chargingCurrentLimitOverlay.context
                    .resources.getBoolean(R.bool.min_charge_discharge_current_overlay)
            ) ||
            binding.chargingCurrentLimitOverlay.isVisible
        )
            binding.chargingCurrentLimitOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                val isChargingDischargeCurrentInWatt = pref.getBoolean(
                    PreferencesKeys.CHARGING_DISCHARGE_CURRENT_IN_WATT,
                    resources.getBoolean(R.bool.charging_discharge_current_in_watt)
                )

                val chargingCurrentLimit = getChargingCurrentLimit(context)


                if (chargingCurrentLimit != null)
                    text = if (isChargingDischargeCurrentInWatt)
                        if (!pref.getBoolean(
                                ONLY_VALUES_OVERLAY, context.resources
                                    .getBoolean(R.bool.only_values_overlay)
                            )
                        ) context.getString(
                            R.string.charging_current_limit_watt, DecimalFormat("#.##")
                                .format(
                                    getChargeDischargeCurrentInWatt(
                                        getChargingCurrentLimit(context)!!.toInt(), true
                                    )
                                )
                        )
                        else context.getString(
                            R.string.charging_discharge_current_watt_overlay_only_values,
                            getChargingCurrentLimit(context)
                        )
                    else if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    ) context.getString(
                        R.string.charging_current_limit, getChargingCurrentLimit(context)
                    )
                    else context.getString(
                        R.string.charging_current_limit_overlay_only_values,
                        getChargingCurrentLimit(context)
                    )

                visibility = if (pref.getBoolean(
                        CHARGING_CURRENT_LIMIT_OVERLAY, context
                            .resources.getBoolean(R.bool.charging_current_limit_overlay)
                    )
                )
                    View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateTemperatureOverlay() {

        if (pref.getBoolean(
                TEMPERATURE_OVERLAY, binding.temperatureOverlay.resources.getBoolean(
                    R.bool.temperature_overlay
                )
            ) || binding.temperatureOverlay.isVisible
        )
            binding.temperatureOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = if (!pref.getBoolean(
                        ONLY_VALUES_OVERLAY, context.resources
                            .getBoolean(R.bool.only_values_overlay)
                    )
                ) context.getString(
                    R.string
                        .temperature_with_title,
                    DecimalFormat().format(getTemperatureInCelsius(context)),
                    DecimalFormat().format(getTemperatureInFahrenheit(context))
                ) else context
                    .getString(
                        R.string.temperature_overlay_only_values, DecimalFormat().format(
                            getTemperatureInCelsius(context)
                        ), DecimalFormat().format(
                            getTemperatureInFahrenheit(context)
                        )
                    )

                visibility = if (pref.getBoolean(
                        TEMPERATURE_OVERLAY, this.resources.getBoolean(
                            R.bool.temperature_overlay
                        )
                    )
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateMaximumTemperatureOverlay() {

        if (pref.getBoolean(
                MAXIMUM_TEMPERATURE_OVERLAY, binding.maximumTemperatureOverlay
                    .resources.getBoolean(R.bool.maximum_temperature_overlay)
            ) ||
            binding.maximumTemperatureOverlay.isVisible
        )
            binding.maximumTemperatureOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = if (!pref.getBoolean(
                        ONLY_VALUES_OVERLAY, context.resources
                            .getBoolean(R.bool.only_values_overlay)
                    )
                ) context.getString(
                    R.string
                        .maximum_temperature,
                    DecimalFormat().format(BatteryInfoInterface.maximumTemperature),
                    DecimalFormat().format(
                        getTemperatureInFahrenheit(
                            BatteryInfoInterface.maximumTemperature
                        )
                    )
                ) else context.getString(
                    R.string.temperature_overlay_only_values, DecimalFormat().format(
                        BatteryInfoInterface.maximumTemperature
                    ), DecimalFormat().format(
                        getTemperatureInFahrenheit(BatteryInfoInterface.maximumTemperature)
                    )
                )

                visibility = if (pref.getBoolean(
                        MAXIMUM_TEMPERATURE_OVERLAY,
                        this.resources.getBoolean(R.bool.maximum_temperature_overlay)
                    )
                )
                    View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateAverageTemperatureOverlay() {

        if (pref.getBoolean(
                AVERAGE_TEMPERATURE_OVERLAY, binding.averageTemperatureOverlay
                    .resources.getBoolean(R.bool.average_temperature_overlay)
            ) ||
            binding.averageTemperatureOverlay.isVisible
        )
            binding.averageTemperatureOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = if (!pref.getBoolean(
                        ONLY_VALUES_OVERLAY, context.resources
                            .getBoolean(R.bool.only_values_overlay)
                    )
                ) context.getString(
                    R.string
                        .average_temperature,
                    DecimalFormat().format(BatteryInfoInterface.averageTemperature),
                    DecimalFormat().format(
                        getTemperatureInFahrenheit(
                            BatteryInfoInterface.averageTemperature
                        )
                    )
                ) else context.getString(
                    R.string.temperature_overlay_only_values, DecimalFormat().format(
                        BatteryInfoInterface.averageTemperature
                    ), DecimalFormat().format(
                        getTemperatureInFahrenheit(BatteryInfoInterface.averageTemperature)
                    )
                )

                visibility = if (pref.getBoolean(
                        AVERAGE_TEMPERATURE_OVERLAY,
                        this.resources.getBoolean(R.bool.average_temperature_overlay)
                    )
                )
                    View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateMinimumTemperatureOverlay() {

        if (pref.getBoolean(
                MINIMUM_TEMPERATURE_OVERLAY, binding.minimumTemperatureOverlay
                    .resources.getBoolean(R.bool.minimum_temperature_overlay)
            ) ||
            binding.minimumTemperatureOverlay.isVisible
        )
            binding.minimumTemperatureOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = if (!pref.getBoolean(
                        ONLY_VALUES_OVERLAY, context.resources
                            .getBoolean(R.bool.only_values_overlay)
                    )
                ) context.getString(
                    R.string
                        .minimum_temperature,
                    DecimalFormat().format(BatteryInfoInterface.minimumTemperature),
                    DecimalFormat().format(
                        getTemperatureInFahrenheit(
                            BatteryInfoInterface.minimumTemperature
                        )
                    )
                ) else context.getString(
                    R.string.temperature_overlay_only_values, DecimalFormat().format(
                        BatteryInfoInterface.minimumTemperature
                    ), DecimalFormat().format(
                        getTemperatureInFahrenheit(BatteryInfoInterface.minimumTemperature)
                    )
                )

                visibility = if (pref.getBoolean(
                        MINIMUM_TEMPERATURE_OVERLAY,
                        this.resources.getBoolean(R.bool.minimum_temperature_overlay)
                    )
                )
                    View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateVoltageOverlay() {

        if (pref.getBoolean(
                VOLTAGE_OVERLAY, binding.voltageOverlay.resources.getBoolean(
                    R.bool.voltage_overlay
                )
            ) || binding.voltageOverlay.isVisible
        )
            binding.voltageOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = if (!pref.getBoolean(
                        ONLY_VALUES_OVERLAY, context.resources
                            .getBoolean(R.bool.only_values_overlay)
                    )
                ) context.getString(
                    R.string.voltage,
                    DecimalFormat("#.#").format(getVoltage(context))
                ) else context.getString(
                    R.string.voltage_overlay_only_values, DecimalFormat("#.#").format(
                        getVoltage(context)
                    )
                )

                visibility = if (pref.getBoolean(
                        VOLTAGE_OVERLAY, this.resources.getBoolean(
                            R.bool.voltage_overlay
                        )
                    )
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onUpdateLastChargeTimeOverlay() {

        if (pref.getBoolean(
                LAST_CHARGE_TIME_OVERLAY, binding.lastChargeTimeOverlay.context
                    .resources.getBoolean(R.bool.last_charge_time_overlay)
            )
            || binding.lastChargeTimeOverlay.isVisible
        )
            binding.lastChargeTimeOverlay.apply {
                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )
                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))
                text = context.getString(
                    if (!pref.getBoolean(
                            ONLY_VALUES_OVERLAY, context.resources
                                .getBoolean(R.bool.only_values_overlay)
                        )
                    ) R.string.last_charge_time else
                        R.string.last_charge_time_overlay_only_values, getLastChargeTime(context),
                    "${pref.getInt(BATTERY_LEVEL_WITH, 0)}%", "${
                        pref.getInt(
                            BATTERY_LEVEL_TO,
                            0
                        )
                    }%"
                )
                visibility = if (pref.getBoolean(
                        LAST_CHARGE_TIME_OVERLAY, context
                            .resources.getBoolean(R.bool.last_charge_time_overlay)
                    )
                ) View.VISIBLE else
                    View.GONE
            }
    }

    private fun onUpdateBatteryWearOverlay() {

        if ((pref.getBoolean(
                BATTERY_WEAR_OVERLAY, binding.batteryWearOverlay.resources
                    .getBoolean(R.bool.battery_wear_overlay)
            )) || binding.batteryWearOverlay.isVisible
        )
            binding.batteryWearOverlay.apply {

                TextAppearanceHelper.setTextAppearance(
                    context, this,
                    pref.getString(OVERLAY_TEXT_STYLE, "0"),
                    pref.getString(OVERLAY_FONT, "6"),
                    pref.getString(OVERLAY_SIZE, "2"),
                    false
                )

                setTextColor(pref.getInt(OVERLAY_TEXT_COLOR, Color.WHITE))

                text = getBatteryWear(
                    context, true, pref.getBoolean(
                        ONLY_VALUES_OVERLAY,
                        context.resources.getBoolean(R.bool.only_values_overlay)
                    )
                )

                visibility = if (pref.getBoolean(
                        BATTERY_WEAR_OVERLAY, this.resources.getBoolean(
                            R.bool.battery_wear_overlay
                        )
                    )
                ) View.VISIBLE else View.GONE
            }
    }

    private fun onLinearLayoutOnTouchListener(parameters: WindowManager.LayoutParams) =

        object : View.OnTouchListener {

            var updatedParameters = parameters
            var x = 0.0
            var y = 0.0
            var pressedX = 0.0
            var pressedY = 0.0

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        x = updatedParameters.x.toDouble()
                        y = updatedParameters.y.toDouble()
                        pressedX = event.rawX.toDouble()
                        pressedY = event.rawY.toDouble()
                    }

                    MotionEvent.ACTION_MOVE -> {
                        updatedParameters.x = (x + (event.rawX - pressedX)).toInt()
                        updatedParameters.y = (y + (event.rawY - pressedY)).toInt()
                        windowManager?.updateViewLayout(linearLayout, updatedParameters)
                    }
                }

                return false
            }
        }
}