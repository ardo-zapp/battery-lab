package com.jacktor.batterylab.fragments

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.MainApp
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.helpers.ThemeHelper.setTheme
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.interfaces.DebugOptionsInterface
import com.jacktor.batterylab.interfaces.NavigationInterface
import com.jacktor.batterylab.interfaces.SettingsInterface
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AUTO_DARK_MODE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_IN_WH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_DISCHARGE_CURRENT_IN_WATT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DARK_MODE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DESIGN_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.FAST_CHARGE_SETTING
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IS_SHOW_BATTERY_INFORMATION
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CHARGES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_FULL_CHARGES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.RESET_SCREEN_TIME_AT_ANY_CHARGE_LEVEL
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SERVICE_TIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SHOW_BATTERY_INFORMATION
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SHOW_EXPANDED_NOTIFICATION
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SHOW_STOP_SERVICE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.STOP_THE_SERVICE_WHEN_THE_CD
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TAB_ON_APPLICATION_LAUNCH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TEXT_FONT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TEXT_SIZE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TEXT_STYLE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UNIT_OF_CHARGE_DISCHARGE_CURRENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.VOLTAGE_UNIT
import com.jacktor.premium.ui.PremiumActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class SettingsFragment() : PreferenceFragmentCompat(),
    SettingsInterface, DebugOptionsInterface,
    BatteryInfoInterface, NavigationInterface {

    private lateinit var pref: SharedPreferences
    private var mainActivity: MainActivity? = null
    private var premium: Preference? = null

    private var isPremium = false

    // Service & Notification
    private var stopService: SwitchPreferenceCompat? = null
    private var serviceTime: SwitchPreferenceCompat? = null
    private var isStopTheServiceWhenTheCD: SwitchPreferenceCompat? = null
    private var isShowBatteryInformation: SwitchPreferenceCompat? = null
    private var isShowExtendedNotification: SwitchPreferenceCompat? = null
    private var batteryStatusInformation: Preference? = null
    private var powerConnection: Preference? = null

    // Appearance
    private var autoDarkMode: SwitchPreferenceCompat? = null
    private var darkMode: SwitchPreferenceCompat? = null
    private var textSize: ListPreference? = null
    private var textFont: ListPreference? = null
    private var textStyle: ListPreference? = null

    // Misc
    private var fastChargeSetting: SwitchPreferenceCompat? = null
    private var capacityInWh: SwitchPreferenceCompat? = null
    private var chargeDischargingCurrentInWatt: SwitchPreferenceCompat? = null
    private var resetScreenTime: SwitchPreferenceCompat? = null
    private var tabOnApplicationLaunch: ListPreference? = null
    private var unitOfChargeDischargeCurrent: ListPreference? = null
    private var unitOfMeasurementOfCurrentCapacity: ListPreference? = null
    private var voltageUnit: ListPreference? = null
    private var backupSettings: Preference? = null
    private var moreOther: Preference? = null
    private var changeDesignCapacity: Preference? = null
    private var overlay: Preference? = null
    private var resetToZeroTheNumberOfCharges: Preference? = null
    private var resetToZeroTheNumberOfCycles: Preference? = null
    private var resetTheNumberOfFullChargesToZero: Preference? = null
    private var debug: Preference? = null

    // About & Feedback
    private var about: Preference? = null
    private var feedback: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        isPremium = (requireContext().applicationContext as MainApp).billingManager.isPremium.value

        pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        addPreferencesFromResource(R.xml.settings)

        mainActivity = activity as? MainActivity

        premium = findPreference("premium")

        premium?.apply {
            isVisible = !isPremium

            if (isVisible)
                setOnPreferenceClickListener {
                    val intent = Intent(context, PremiumActivity::class.java)
                    startActivity(intent)

                    true
                }
        }

        // Service & Notification
        stopService = findPreference(SHOW_STOP_SERVICE)

        serviceTime = findPreference(SERVICE_TIME)

        isStopTheServiceWhenTheCD = findPreference(STOP_THE_SERVICE_WHEN_THE_CD)

        isShowBatteryInformation = findPreference(SHOW_BATTERY_INFORMATION)

        isShowExtendedNotification = findPreference(SHOW_EXPANDED_NOTIFICATION)

        powerConnection = findPreference("connected_disconnected_sound")

        stopService?.apply {
            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        isStopTheServiceWhenTheCD?.apply {
            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        isShowBatteryInformation?.apply {
            isEnabled = premium?.isVisible == false

            summary = getString(
                if (!isEnabled) R.string.premium_feature
                else R.string.service_restart_required
            )

            setOnPreferenceChangeListener { preference, value ->

                preference.isEnabled = false
                isShowExtendedNotification?.isEnabled = false

                try {
                    ServiceHelper.restartService(
                        requireContext(), BatteryLabService::class.java,
                        preference
                    )
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.toString(), Toast.LENGTH_LONG).show()
                }

                CoroutineScope(Dispatchers.Main).launch {
                    delay(3.5.seconds)
                    isShowExtendedNotification?.isEnabled = (value as? Boolean) == true
                }

                true
            }
        }

        isShowExtendedNotification?.apply {

            isEnabled = if (!isPremium) true
            else pref.getBoolean(
                SHOW_BATTERY_INFORMATION, requireContext().resources.getBoolean(
                    R.bool.show_battery_information
                )
            )

            setOnPreferenceChangeListener { preference, _ ->

                preference.isEnabled = false

                ServiceHelper.restartService(
                    requireContext(), BatteryLabService::class.java,
                    preference
                )

                true
            }
        }

        batteryStatusInformation = findPreference("battery_status_information")

        batteryStatusInformation?.apply {

            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null

            if (isEnabled)
                setOnPreferenceClickListener {
                    mainActivity?.fragment = BatteryStatusInformationFragment()

                    mainActivity?.toolbar?.title = requireContext().getString(
                        R.string.battery_status_information
                    )

                    mainActivity?.toolbar?.navigationIcon =
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_arrow_back_24dp
                        )

                    mainActivity?.loadFragment(
                        mainActivity?.fragment ?: BatteryStatusInformationFragment(),
                        true
                    )

                    true
                }
        }

        powerConnection?.apply {

            //isEnabled = premium?.isVisible == false
            //summary = if(!isEnabled) getString(R.string.premium_feature) else null

            //if(isEnabled)
            setOnPreferenceClickListener {
                mainActivity?.fragment = PowerConnectionSettingsFragment()

                mainActivity?.toolbar?.title = requireContext().getString(
                    R.string.power_connection
                )

                mainActivity?.toolbar?.navigationIcon =
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_arrow_back_24dp)

                mainActivity?.loadFragment(
                    mainActivity?.fragment ?: PowerConnectionSettingsFragment(),
                    true
                )

                true
            }
        }

        // Appearance
        autoDarkMode = findPreference(AUTO_DARK_MODE)

        darkMode = findPreference(DARK_MODE)

        textSize = findPreference(TEXT_SIZE)

        textFont = findPreference(TEXT_FONT)

        textStyle = findPreference(TEXT_STYLE)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) darkMode?.isEnabled =
            !pref.getBoolean(AUTO_DARK_MODE, resources.getBoolean(R.bool.auto_dark_mode))

        textSize?.summary = getOnTextSizeSummary()

        textFont?.apply {
            isEnabled = premium?.isVisible == false
            summary = if (isEnabled) getTextFontSummary() else getString(R.string.premium_feature)

            setOnPreferenceChangeListener { preference, newValue ->

                preference.summary = resources.getStringArray(R.array.fonts_list)[
                    (newValue as? String)?.toInt() ?: 0]

                true
            }
        }

        textStyle?.summary = getTextStyleSummary()


        autoDarkMode?.setOnPreferenceChangeListener { _, newValue ->

            darkMode?.isEnabled = (newValue as? Boolean) == false

            setTheme(requireContext(), isAutoDarkMode = newValue as? Boolean == true)

            true
        }

        darkMode?.setOnPreferenceChangeListener { _, newValue ->

            setTheme(requireContext(), isDarkMode = newValue as? Boolean == true)

            true
        }

        textSize?.setOnPreferenceChangeListener { preference, newValue ->

            preference.summary = resources.getStringArray(R.array.text_size_list)[
                (newValue as? String)?.toInt() ?: 2]

            true
        }

        textStyle?.setOnPreferenceChangeListener { preference, newValue ->

            preference.summary = resources.getStringArray(R.array.text_style_list)[
                (newValue as? String)?.toInt() ?: 0]

            true
        }

        // Misc
        fastChargeSetting = findPreference(FAST_CHARGE_SETTING)

        capacityInWh = findPreference(CAPACITY_IN_WH)

        chargeDischargingCurrentInWatt = findPreference(CHARGING_DISCHARGE_CURRENT_IN_WATT)

        resetScreenTime = findPreference(RESET_SCREEN_TIME_AT_ANY_CHARGE_LEVEL)

        moreOther = findPreference("more_other")

        backupSettings = findPreference("backup_settings")

        tabOnApplicationLaunch = findPreference(TAB_ON_APPLICATION_LAUNCH)

        unitOfChargeDischargeCurrent = findPreference(UNIT_OF_CHARGE_DISCHARGE_CURRENT)

        unitOfMeasurementOfCurrentCapacity =
            findPreference(UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY)

        voltageUnit = findPreference(VOLTAGE_UNIT)

        changeDesignCapacity = findPreference("change_design_capacity")

        overlay = findPreference("overlay")

        resetToZeroTheNumberOfCharges = findPreference("reset_to_zero_the_number_of_charges")

        resetToZeroTheNumberOfCycles = findPreference("reset_to_zero_the_number_of_cycles")

        resetTheNumberOfFullChargesToZero =
            findPreference("reset_the_number_of_full_charges_to_zero")

        debug = findPreference("debug")

        fastChargeSetting?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as? Boolean == true)
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.information)
                    setIcon(R.drawable.ic_instruction_not_supported_24dp)
                    setMessage(R.string.fast_charge_dialog_message)
                    setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
                    show()
                }

            true
        }

        capacityInWh?.apply {

            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        chargeDischargingCurrentInWatt?.apply {

            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        resetScreenTime?.apply {

            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        backupSettings?.apply {

            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null

            if (isEnabled)
                setOnPreferenceClickListener {
                    mainActivity?.fragment = BackupSettingsFragment()

                    mainActivity?.toolbar?.title = requireContext().getString(
                        R.string.backup
                    )

                    mainActivity?.toolbar?.navigationIcon =
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_arrow_back_24dp
                        )

                    mainActivity?.loadFragment(
                        mainActivity?.fragment ?: BackupSettingsFragment(), true
                    )

                    true
                }
        }

        moreOther?.setOnPreferenceClickListener {

            if (it.title == requireContext().getString(R.string.more)) {

                it.icon =
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_more_less_24dp)
                it.title = getString(R.string.hide)

                tabOnApplicationLaunch?.apply {
                    isVisible = true
                    isEnabled = premium?.isVisible == false
                    summary = if (!isEnabled) getString(R.string.premium_feature)
                    else getTabOnApplicationLaunchSummary()
                }

                unitOfChargeDischargeCurrent?.apply {
                    isVisible = true
                    summary = getUnitOfChargeDischargeCurrentSummary()
                }

                unitOfMeasurementOfCurrentCapacity?.apply {
                    isVisible = true
                    summary = getUnitOfMeasurementOfCurrentCapacitySummary()
                }

                voltageUnit?.apply {
                    isVisible = true
                    summary = getVoltageUnitSummary()
                }

                unitOfChargeDischargeCurrent?.apply {
                    isVisible = true
                    summary = getUnitOfChargeDischargeCurrentSummary()
                }
                unitOfMeasurementOfCurrentCapacity?.apply {
                    isVisible = true
                    summary = getUnitOfMeasurementOfCurrentCapacitySummary()
                }
                changeDesignCapacity?.apply {
                    isVisible = true
                    summary = getString(
                        R.string.change_design_summary,
                        pref.getInt(
                            DESIGN_CAPACITY,
                            resources.getInteger(R.integer.min_design_capacity)
                        )
                    )
                }
                unitOfChargeDischargeCurrent?.apply {
                    isVisible = true
                    summary = getUnitOfChargeDischargeCurrentSummary()
                }
                unitOfMeasurementOfCurrentCapacity?.apply {
                    isVisible = true
                    summary = getUnitOfMeasurementOfCurrentCapacitySummary()
                }
                changeDesignCapacity?.apply {
                    isVisible = true
                    summary = getString(
                        R.string.change_design_summary,
                        pref.getInt(
                            DESIGN_CAPACITY,
                            resources.getInteger(R.integer.min_design_capacity)
                        )
                    )
                }

                overlay?.isVisible = true
                resetToZeroTheNumberOfCharges?.apply {

                    isVisible = true
                    isEnabled = pref.getLong(NUMBER_OF_CHARGES, 0) > 0
                }
                resetToZeroTheNumberOfCycles?.apply {

                    isVisible = true
                    isEnabled = pref.getFloat(NUMBER_OF_CYCLES, 0f) > 0f
                }
                resetTheNumberOfFullChargesToZero?.apply {

                    isVisible = true
                    isEnabled = pref.getLong(NUMBER_OF_FULL_CHARGES, 0) > 0
                }

                debug?.apply {
                    isVisible = pref.getBoolean(
                        PreferencesKeys.ENABLED_DEBUG_OPTIONS,
                        resources.getBoolean(R.bool.enabled_debug_options)
                    )
                    isEnabled = premium?.isVisible == false
                    summary = if (!isEnabled) getString(R.string.premium_feature) else null
                }
            } else {

                it.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_more_24dp)
                it.title = requireContext().getString(R.string.more)

                tabOnApplicationLaunch?.isVisible = false
                unitOfChargeDischargeCurrent?.isVisible = false
                unitOfMeasurementOfCurrentCapacity?.isVisible = false
                voltageUnit?.isVisible = false
                changeDesignCapacity?.isVisible = false
                overlay?.isVisible = false
                resetToZeroTheNumberOfCharges?.isVisible = false
                resetToZeroTheNumberOfCycles?.isVisible = false
                resetTheNumberOfFullChargesToZero?.isVisible = false
                debug?.isVisible = false
            }

            true
        }

        tabOnApplicationLaunch?.setOnPreferenceChangeListener { preference, newValue ->

            val tab = //if (HistoryHelper.isHistoryNotEmpty(requireContext()))
                (newValue as? String)?.toInt() ?: 0 //else 0

            preference.summary =
                resources.getStringArray(R.array.tab_on_application_launch_list)[tab]

            //HistoryHelper.isHistoryNotEmpty(requireContext())
            true
        }

        unitOfChargeDischargeCurrent?.apply {

            setOnPreferenceClickListener {

                CoroutineScope(Dispatchers.Main).launch {
                    delay(0.5.seconds)
                    MaterialAlertDialogBuilder(requireContext()).apply {
                        setIcon(R.drawable.ic_instruction_not_supported_24dp)
                        setTitle(R.string.information)
                        setMessage(R.string.setting_is_intended_to_correct)
                        setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
                        show()
                    }
                }

                true
            }

            setOnPreferenceChangeListener { preference, newValue ->

                if ((newValue as? String) == "μA")
                    preference.summary = resources.getStringArray(
                        R.array
                            .unit_of_charge_discharge_current_list
                    )[0]
                else preference.summary = resources.getStringArray(
                    R.array
                        .unit_of_charge_discharge_current_list
                )[1]

                BatteryInfoInterface.apply {
                    maxChargeCurrent = 0
                    maxDischargeCurrent = 0
                    averageChargeCurrent = 0
                    averageDischargeCurrent = 0
                    minChargeCurrent = 0
                    minDischargeCurrent = 0
                }

                true

            }
        }

        unitOfMeasurementOfCurrentCapacity?.apply {

            setOnPreferenceClickListener {

                CoroutineScope(Dispatchers.Main).launch {
                    delay(0.5.seconds)
                    MaterialAlertDialogBuilder(requireContext()).apply {
                        setIcon(R.drawable.ic_instruction_not_supported_24dp)
                        setTitle(R.string.information)
                        setMessage(R.string.setting_is_intended_to_correct)
                        setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
                        show()
                    }
                }

                true
            }

            setOnPreferenceChangeListener { preference, newValue ->

                if ((newValue as? String) == "μAh")
                    preference.summary = resources.getStringArray(
                        R.array
                            .unit_of_measurement_of_current_capacity_list
                    )[0]
                else preference.summary = resources.getStringArray(
                    R.array
                        .unit_of_measurement_of_current_capacity_list
                )[1]

                true
            }
        }

        voltageUnit?.apply {

            setOnPreferenceClickListener {

                Toast.makeText(
                    requireContext(), getString(R.string.setting_is_intended_to_correct),
                    Toast.LENGTH_LONG
                ).show()

                true
            }

            setOnPreferenceChangeListener { preference, newValue ->

                if ((newValue as? String) == "μV")
                    preference.summary = resources.getStringArray(R.array.voltage_unit_list)[0]
                else preference.summary = resources.getStringArray(R.array.voltage_unit_list)[1]

                true
            }
        }

        changeDesignCapacity?.setOnPreferenceClickListener {

            onChangeDesignCapacity(it)

            true
        }

        overlay?.apply {

            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null

            if (isEnabled)
                setOnPreferenceClickListener {
                    mainActivity?.fragment = OverlayFragment()

                    mainActivity?.toolbar?.title = requireContext().getString(
                        R.string.overlay
                    )

                    mainActivity?.toolbar?.navigationIcon =
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_arrow_back_24dp
                        )

                    mainActivity?.loadFragment(
                        mainActivity?.fragment ?: OverlayFragment(), true
                    )

                    true
                }
        }

        resetToZeroTheNumberOfCharges?.setOnPreferenceClickListener {

            if (pref.getLong(NUMBER_OF_CHARGES, 0) > 0)
                MaterialAlertDialogBuilder(requireContext()).apply {

                    setMessage(getString(R.string.reset_to_zero_the_number_of_charges_dialog_message))

                    setPositiveButton(getString(android.R.string.ok)) { _, _ ->

                        pref.edit { remove(NUMBER_OF_CHARGES) }

                        it.isEnabled = pref.getLong(NUMBER_OF_CHARGES, 0) > 0

                        if (!it.isEnabled)
                            Toast.makeText(
                                requireContext(),
                                R.string.number_of_charges_was_success_reset_to_zero,
                                Toast.LENGTH_LONG
                            ).show()
                    }

                    setNegativeButton(getString(android.R.string.cancel)) { d, _ -> d.dismiss() }

                    show()
                }
            else it.isEnabled = false

            true
        }

        resetToZeroTheNumberOfCycles?.setOnPreferenceClickListener {

            if (pref.getFloat(NUMBER_OF_CYCLES, 0f) > 0f)
                MaterialAlertDialogBuilder(requireContext()).apply {

                    setMessage(getString(R.string.reset_to_zero_the_number_of_cycles_dialog_message))

                    setPositiveButton(getString(android.R.string.ok)) { _, _ ->

                        pref.edit { remove(NUMBER_OF_CYCLES) }

                        it.isEnabled = pref.getFloat(NUMBER_OF_CYCLES, 0f) > 0f

                        if (!it.isEnabled) Toast.makeText(
                            requireContext(),
                            R.string.number_of_cycles_was_success_reset_to_zero,
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    setNegativeButton(getString(android.R.string.cancel)) { d, _ -> d.dismiss() }

                    show()
                }
            else it.isEnabled = false

            true
        }

        resetTheNumberOfFullChargesToZero?.setOnPreferenceClickListener {

            if (pref.getLong(NUMBER_OF_FULL_CHARGES, 0) > 0)
                MaterialAlertDialogBuilder(requireContext()).apply {

                    setMessage(
                        getString(
                            R.string
                                .reset_the_number_of_full_charges_to_zero_dialog_message
                        )
                    )

                    setPositiveButton(getString(android.R.string.ok)) { _, _ ->

                        pref.edit { remove(NUMBER_OF_FULL_CHARGES) }

                        it.isEnabled = pref.getLong(NUMBER_OF_FULL_CHARGES, 0) > 0

                        if (!it.isEnabled) Toast.makeText(
                            requireContext(),
                            R.string.number_of_full_charges_was_success_reset_to_zero,
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    setNegativeButton(getString(android.R.string.cancel)) { d, _ -> d.dismiss() }

                    show()
                }
            else it.isEnabled = false

            true
        }

        debug?.setOnPreferenceClickListener {

            mainActivity?.fragment = DebugFragment()

            mainActivity?.toolbar?.title = requireContext().getString(R.string.debug)

            mainActivity?.toolbar?.navigationIcon = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_arrow_back_24dp
            )

            mainActivity?.loadFragment(
                mainActivity?.fragment ?: DebugFragment(),
                true
            )

            true
        }

        // About & Feedback
        about = findPreference("about")

        feedback = findPreference("feedback")

        about?.setOnPreferenceClickListener {

            mainActivity?.fragment = AboutFragment()

            mainActivity?.toolbar?.title = requireContext().getString(
                R.string.about
            )

            mainActivity?.toolbar?.navigationIcon =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_arrow_back_24dp)

            mainActivity?.loadFragment(
                mainActivity?.fragment ?: AboutFragment(), true
            )

            true
        }

        feedback?.setOnPreferenceClickListener {

            mainActivity?.fragment = FeedbackFragment()

            mainActivity?.toolbar?.title = requireContext().getString(
                R.string.feedback
            )

            mainActivity?.toolbar?.navigationIcon =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_arrow_back_24dp)

            mainActivity?.loadFragment(
                mainActivity?.fragment ?: FeedbackFragment(), true
            )

            true
        }
    }

    override fun onResume() {

        super.onResume()

        if (premium?.isVisible == true) premium?.isVisible = !isPremium

        stopService?.apply {
            isEnabled = isPremium
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        isStopTheServiceWhenTheCD?.apply {
            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        isShowBatteryInformation?.apply {
            isEnabled = premium?.isVisible == false
            summary = getString(
                if (!isEnabled) R.string.premium_feature
                else R.string.service_restart_required
            )
        }

        isShowExtendedNotification?.apply {
            val isShowBatteryInformationPref = pref.getBoolean(
                IS_SHOW_BATTERY_INFORMATION,
                requireContext().resources.getBoolean(R.bool.is_show_battery_information)
            )
            isEnabled = if (premium?.isVisible == false && isShowBatteryInformationPref) true
            else isShowBatteryInformationPref
        }

        batteryStatusInformation?.apply {
            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        capacityInWh?.apply {

            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        chargeDischargingCurrentInWatt?.apply {

            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        resetScreenTime?.apply {
            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        backupSettings?.apply {
            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        overlay?.apply {
            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) darkMode?.isEnabled =
            !pref.getBoolean(AUTO_DARK_MODE, resources.getBoolean(R.bool.auto_dark_mode))

        textSize?.summary = getOnTextSizeSummary()

        textFont?.apply {
            isEnabled = premium?.isVisible == false
            summary = if (isEnabled) getTextFontSummary() else getString(R.string.premium_feature)
        }

        textStyle?.summary = getTextStyleSummary()

        tabOnApplicationLaunch?.apply {
            if (isVisible) {
                isEnabled = premium?.isVisible == false
                summary = if (!isEnabled) getString(R.string.premium_feature)
                else getTabOnApplicationLaunchSummary()
            }
        }

        unitOfChargeDischargeCurrent?.apply {
            if (isVisible) summary = getUnitOfChargeDischargeCurrentSummary()
        }

        unitOfMeasurementOfCurrentCapacity?.apply {
            if (isVisible) summary = getUnitOfMeasurementOfCurrentCapacitySummary()
        }

        voltageUnit?.apply {
            if (isVisible) summary = getVoltageUnitSummary()
        }

        changeDesignCapacity?.apply {
            if (isVisible) summary = getString(
                R.string.change_design_summary,
                pref.getInt(DESIGN_CAPACITY, resources.getInteger(R.integer.min_design_capacity))
            )
        }

        resetToZeroTheNumberOfCharges?.isEnabled = pref.getLong(NUMBER_OF_CHARGES, 0) > 0

        resetToZeroTheNumberOfCycles?.isEnabled = pref.getFloat(NUMBER_OF_CYCLES, 0f) > 0f

        debug?.apply {
            isVisible = moreOther?.title == getString(R.string.hide) && pref.getBoolean(
                PreferencesKeys.ENABLED_DEBUG_OPTIONS, resources.getBoolean(
                    R.bool.enabled_debug_options
                )
            )
            isEnabled = premium?.isVisible == false
            summary = if (!isEnabled) getString(R.string.premium_feature) else null
        }
    }
}