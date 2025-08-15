package com.jacktor.batterylab.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.interfaces.OverlayInterface
import com.jacktor.batterylab.services.OverlayService
import com.jacktor.batterylab.utilities.Constants.NUMBER_OF_CYCLES_PATH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_CHARGE_DISCHARGE_CURRENT_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_TEMPERATURE_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_HEALTH_ANDROID_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_OVERLAY
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
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CHARGES_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES_ANDROID_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_FULL_CHARGES_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.ONLY_VALUES_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERLAY_FONT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERLAY_LOCATION
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERLAY_OPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERLAY_SIZE
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

class OverlayFragment : PreferenceFragmentCompat(), BatteryInfoInterface {

    private lateinit var pref: SharedPreferences
    private lateinit var getResult: ActivityResultLauncher<Intent>

    private var dialogRequestOverlayPermission: MaterialAlertDialogBuilder? = null

    private var overlayScreen: PreferenceScreen? = null
    private var enableOverlay: SwitchPreferenceCompat? = null
    private var onlyValuesOverlay: SwitchPreferenceCompat? = null
    private var overlayLocation: ListPreference? = null

    // Appearance
    private var appearanceCategory: PreferenceCategory? = null
    private var overlaySize: ListPreference? = null
    private var overlayFont: ListPreference? = null
    private var overlayTextStyle: ListPreference? = null
    private var overlayOpacity: SeekBarPreference? = null

    // Show/Hide
    private var overlayCategory: PreferenceCategory? = null
    private var numberOfChargesOverlay: SwitchPreferenceCompat? = null
    private var numberOfFullChargesOverlay: SwitchPreferenceCompat? = null
    private var numberOfCyclesOverlay: SwitchPreferenceCompat? = null
    private var numberOfCyclesAndroidOverlay: SwitchPreferenceCompat? = null
    private var chargingTimeOverlay: SwitchPreferenceCompat? = null
    private var chargingTimeRemainingOverlay: SwitchPreferenceCompat? = null
    private var remainingBatteryTimeOverlay: SwitchPreferenceCompat? = null
    private var screenTimeOverlay: SwitchPreferenceCompat? = null
    private var batteryLevelOverlay: SwitchPreferenceCompat? = null
    private var currentCapacityOverlay: SwitchPreferenceCompat? = null
    private var capacityAddedOverlay: SwitchPreferenceCompat? = null
    private var batteryHealthAndroidOverlay: SwitchPreferenceCompat? = null
    private var batteryHealthOverlay: SwitchPreferenceCompat? = null
    private var residualCapacityOverlay: SwitchPreferenceCompat? = null
    private var statusOverlay: SwitchPreferenceCompat? = null
    private var sourceOfPowerOverlay: SwitchPreferenceCompat? = null
    private var chargeDischargeCurrentOverlay: SwitchPreferenceCompat? = null
    private var fastChargeOverlay: SwitchPreferenceCompat? = null
    private var maxChargeDischargeCurrentOverlay: SwitchPreferenceCompat? = null
    private var averageChargeDischargeCurrentOverlay: SwitchPreferenceCompat? = null
    private var minChargeDischargeCurrentOverlay: SwitchPreferenceCompat? = null
    private var chargingCurrentLimitOverlay: SwitchPreferenceCompat? = null
    private var temperatureOverlay: SwitchPreferenceCompat? = null
    private var maximumTemperatureOverlay: SwitchPreferenceCompat? = null
    private var averageTemperatureOverlay: SwitchPreferenceCompat? = null
    private var minimumTemperatureOverlay: SwitchPreferenceCompat? = null
    private var voltageOverlay: SwitchPreferenceCompat? = null
    private var lastChargeTimeOverlay: SwitchPreferenceCompat? = null
    private var batteryWearOverlay: SwitchPreferenceCompat? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val chargingCurrentLimit = getChargingCurrentLimit(requireContext())

        addPreferencesFromResource(R.xml.overlay_settings)

        getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

        overlayScreen = findPreference("overlay_screen")

        overlayScreen?.isEnabled = Settings.canDrawOverlays(requireContext())

        enableOverlay = findPreference(ENABLED_OVERLAY)

        onlyValuesOverlay = findPreference(ONLY_VALUES_OVERLAY)

        overlayLocation = findPreference(OVERLAY_LOCATION)

        enableOverlay?.setOnPreferenceChangeListener { _, newValue ->

            when (newValue as? Boolean) {

                true -> {

                    @Suppress("KotlinConstantConditions")
                    if (OverlayService.instance == null
                        && OverlayInterface.isEnabledOverlay(
                            requireContext(),
                            newValue == true
                        )
                    )
                        ServiceHelper.startService(
                            requireContext(), OverlayService::class.java,
                            true
                        )
                }

                false -> if (OverlayService.instance != null)
                    ServiceHelper.stopService(requireContext(), OverlayService::class.java)

                null -> {}
            }

            enableAllOverlay(newValue as? Boolean)

            true
        }

        overlayLocation?.apply {
            summary = getOverlayLocationSummary()
            setOnPreferenceChangeListener { preference, newValue ->
                if (OverlayService.instance != null && OverlayInterface.isEnabledOverlay(
                        requireContext(), enableOverlay?.isEnabled == true
                    )
                )
                    ServiceHelper.restartService(context, OverlayService::class.java)
                preference.summary = resources.getStringArray(R.array.overlay_location_list)[
                    (newValue as? String)?.toInt() ?: resources.getInteger(
                        R.integer.overlay_location_default
                    )]
                true
            }
        }

        // Appearance
        appearanceCategory = findPreference("appearance_overlay")
        overlaySize = findPreference(OVERLAY_SIZE)
        overlayFont = findPreference(OVERLAY_FONT)
        overlayTextStyle = findPreference(OVERLAY_TEXT_STYLE)
        overlayOpacity = findPreference("overlay_opacity")

        overlaySize?.apply {
            summary = getOverlayTextSizeSummary()
            setOnPreferenceChangeListener { preference, newValue ->

                preference.summary = resources.getStringArray(R.array.text_size_list)[
                    (newValue as? String)?.toInt() ?: 2]

                true
            }
        }

        overlayFont?.apply {
            summary = getOverlayTextFontSummary()
            setOnPreferenceChangeListener { preference, newValue ->

                preference.summary = resources.getStringArray(R.array.fonts_list)[
                    (newValue as? String)?.toInt() ?: 6]

                true
            }
        }

        overlayTextStyle?.apply {
            summary = getOverlayTextStyleSummary()
            setOnPreferenceChangeListener { preference, newValue ->

                preference.summary = resources.getStringArray(R.array.text_style_list)[
                    (newValue as? String)?.toInt() ?: 0]

                true
            }
        }

        overlayOpacity?.apply {
            summary = getOverlayOpacitySummary()
            setOnPreferenceChangeListener { preference, newValue ->

                val progress = newValue as? Int ?: 0

                preference.summary = "${
                    DecimalFormat("#").format(
                        (progress.toFloat() / 255f) * 100f
                    )
                }%"

                true
            }
        }


        // Show/Hide
        overlayCategory = findPreference("show_hide_pref_category")
        batteryLevelOverlay = findPreference(BATTERY_LEVEL_OVERLAY)
        numberOfChargesOverlay = findPreference(NUMBER_OF_CHARGES_OVERLAY)
        numberOfFullChargesOverlay = findPreference(NUMBER_OF_FULL_CHARGES_OVERLAY)
        numberOfCyclesOverlay = findPreference(NUMBER_OF_CYCLES_OVERLAY)
        numberOfCyclesAndroidOverlay = findPreference(NUMBER_OF_CYCLES_ANDROID_OVERLAY)
        chargingTimeOverlay = findPreference(CHARGING_TIME_OVERLAY)
        chargingTimeRemainingOverlay = findPreference(CHARGING_TIME_REMAINING_OVERLAY)
        remainingBatteryTimeOverlay = findPreference(REMAINING_BATTERY_TIME_OVERLAY)
        screenTimeOverlay = findPreference(SCREEN_TIME_OVERLAY)
        currentCapacityOverlay = findPreference(CURRENT_CAPACITY_OVERLAY)
        capacityAddedOverlay = findPreference(CAPACITY_ADDED_OVERLAY)
        batteryHealthAndroidOverlay = findPreference(BATTERY_HEALTH_ANDROID_OVERLAY)
        batteryHealthOverlay = findPreference(BATTERY_HEALTH_ANDROID_OVERLAY)
        residualCapacityOverlay = findPreference(RESIDUAL_CAPACITY_OVERLAY)
        statusOverlay = findPreference(STATUS_OVERLAY)
        sourceOfPowerOverlay = findPreference(SOURCE_OF_POWER)
        chargeDischargeCurrentOverlay = findPreference(CHARGE_DISCHARGE_CURRENT_OVERLAY)
        fastChargeOverlay = findPreference(FAST_CHARGE_OVERLAY)
        maxChargeDischargeCurrentOverlay = findPreference(MAX_CHARGE_DISCHARGE_CURRENT_OVERLAY)
        averageChargeDischargeCurrentOverlay =
            findPreference(AVERAGE_CHARGE_DISCHARGE_CURRENT_OVERLAY)
        minChargeDischargeCurrentOverlay = findPreference(MIN_CHARGE_DISCHARGE_CURRENT_OVERLAY)
        chargingCurrentLimitOverlay = findPreference(CHARGING_CURRENT_LIMIT_OVERLAY)
        temperatureOverlay = findPreference(TEMPERATURE_OVERLAY)
        maximumTemperatureOverlay = findPreference(MAXIMUM_TEMPERATURE_OVERLAY)
        averageTemperatureOverlay = findPreference(AVERAGE_TEMPERATURE_OVERLAY)
        minimumTemperatureOverlay = findPreference(MINIMUM_TEMPERATURE_OVERLAY)
        voltageOverlay = findPreference(VOLTAGE_OVERLAY)
        lastChargeTimeOverlay = findPreference(LAST_CHARGE_TIME_OVERLAY)
        batteryWearOverlay = findPreference(BATTERY_WEAR_OVERLAY)

        numberOfCyclesAndroidOverlay?.isVisible = File(NUMBER_OF_CYCLES_PATH).exists() // WARNING
        chargingCurrentLimitOverlay?.isVisible = chargingCurrentLimit != null &&
                chargingCurrentLimit.toInt() > 0

        enableAllOverlay(
            pref.getBoolean(
                ENABLED_OVERLAY, resources.getBoolean(
                    R.bool.enabled_overlay
                )
            )
        )

        batteryLevelOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        numberOfChargesOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        numberOfFullChargesOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        numberOfCyclesOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        numberOfCyclesAndroidOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        chargingTimeOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        chargingTimeRemainingOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        remainingBatteryTimeOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        screenTimeOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        currentCapacityOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        capacityAddedOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        batteryHealthAndroidOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        residualCapacityOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        statusOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        sourceOfPowerOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        chargeDischargeCurrentOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        fastChargeOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        maxChargeDischargeCurrentOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        averageChargeDischargeCurrentOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        minChargeDischargeCurrentOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        chargingCurrentLimitOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        temperatureOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        maximumTemperatureOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        averageTemperatureOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        minimumTemperatureOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        voltageOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        lastChargeTimeOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }

        batteryWearOverlay?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue as? Boolean == true && OverlayService.instance == null)
                ServiceHelper.startService(requireContext(), OverlayService::class.java)

            true
        }
    }

    override fun onResume() {

        super.onResume()

        val chargingCurrentLimit = getChargingCurrentLimit(requireContext())

        if (pref.getBoolean(ENABLED_OVERLAY, resources.getBoolean(R.bool.enabled_overlay))
            && OverlayService.instance == null && !ServiceHelper.isStartedOverlayService()
        )
            ServiceHelper.startService(requireContext(), OverlayService::class.java)

        chargingCurrentLimitOverlay?.isVisible = chargingCurrentLimit != null &&
                chargingCurrentLimit.toInt() > 0

        overlaySize?.summary = getOverlayTextSizeSummary()

        overlayTextStyle?.summary = getOverlayTextStyleSummary()

        overlayOpacity?.summary = getOverlayOpacitySummary()

        enableAllOverlay(
            pref.getBoolean(
                ENABLED_OVERLAY, resources.getBoolean(
                    R.bool.enabled_overlay
                )
            )
        )

        val canDrawOverlays = Settings.canDrawOverlays(requireContext())

        overlayScreen?.isEnabled = canDrawOverlays

        if (dialogRequestOverlayPermission == null && !canDrawOverlays)
            requestOverlayPermission()
    }

    private fun getOverlayLocationSummary(): CharSequence? {
        if (pref.getString(
                OVERLAY_LOCATION, "${
                    resources.getInteger(
                        R.integer.overlay_location_default
                    )
                }"
            ) !in
            resources.getStringArray(R.array.overlay_location_values)
        )
            pref.edit {
                putString(
                    OVERLAY_LOCATION, "${
                        resources.getInteger(
                            R.integer.overlay_location_default
                        )
                    }"
                )
            }

        return resources.getStringArray(R.array.overlay_location_list)[pref.getString(
            OVERLAY_LOCATION,
            "${resources.getInteger(R.integer.overlay_location_default)}"
        )!!.toInt()]
    }

    private fun getOverlayTextSizeSummary(): CharSequence? {
        if (pref.getString(OVERLAY_SIZE, "2") !in
            resources.getStringArray(R.array.text_size_values)
        )
            pref.edit { putString(OVERLAY_SIZE, "2") }

        return resources.getStringArray(R.array.text_size_list)[pref.getString(
            OVERLAY_SIZE,
            "2"
        )?.toInt() ?: 2]
    }

    private fun getOverlayTextFontSummary(): CharSequence? {

        if (pref.getString(OVERLAY_FONT, "6") !in
            resources.getStringArray(R.array.fonts_values)
        )
            pref.edit { putString(OVERLAY_FONT, "6") }

        return resources.getStringArray(R.array.fonts_list)[
            pref.getString(OVERLAY_FONT, "6")?.toInt() ?: 6]
    }

    private fun getOverlayTextStyleSummary(): CharSequence? {

        if (pref.getString(OVERLAY_TEXT_STYLE, "0") !in
            resources.getStringArray(R.array.text_style_values)
        )
            pref.edit { putString(OVERLAY_TEXT_STYLE, "0") }

        return resources.getStringArray(R.array.text_style_list)[
            pref.getString(OVERLAY_TEXT_STYLE, "0")?.toInt() ?: 0]
    }

    private fun getOverlayOpacitySummary(): CharSequence {

        if (pref.getInt(
                OVERLAY_OPACITY, resources.getInteger(
                    R.integer.overlay_opacity_default
                )
            ) > resources.getInteger(
                R.integer.overlay_opacity_max
            ) || pref.getInt(
                OVERLAY_OPACITY,
                resources.getInteger(R.integer.overlay_opacity_default)
            ) < 0
        )
            pref.edit {
                putInt(
                    OVERLAY_OPACITY, resources.getInteger(
                        R.integer.overlay_opacity_default
                    )
                )
            }

        return "${
            DecimalFormat("#").format(
                (pref.getInt(
                    OVERLAY_OPACITY,
                    resources.getInteger(R.integer.overlay_opacity_default)
                ).toFloat() / resources
                    .getInteger(R.integer.overlay_opacity_max).toFloat()) * 100f
            )
        }%"
    }

    private fun requestOverlayPermission() {

        dialogRequestOverlayPermission = MaterialAlertDialogBuilder(requireContext()).apply {

            setMessage(getString(R.string.overlay_permission_dialog_message))
            setPositiveButton(getString(android.R.string.ok)) { _, _ ->

                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${requireContext().packageName}".toUri()
                )

                getResult.launch(intent)

                dialogRequestOverlayPermission = null
            }
            setNegativeButton(getString(android.R.string.cancel)) { _, _ ->

                dialogRequestOverlayPermission = null
            }
            setOnCancelListener { dialogRequestOverlayPermission = null }

            show()
        }
    }

    private fun enableAllOverlay(isEnable: Boolean?) {

        onlyValuesOverlay?.isEnabled = (isEnable ?: onlyValuesOverlay?.isEnabled) == true
        overlayLocation?.isEnabled = (isEnable ?: overlayLocation?.isEnabled) == true
        appearanceCategory?.isEnabled = (isEnable ?: appearanceCategory?.isEnabled) == true
        overlayCategory?.isEnabled = (isEnable ?: overlayCategory?.isEnabled) == true
    }
}