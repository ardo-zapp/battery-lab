package com.jacktor.batterylab.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.jacktor.batterylab.MainApp.Companion.isGooglePlay
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databases.HistoryDB
import com.jacktor.batterylab.helpers.DateHelper
import com.jacktor.batterylab.helpers.HistoryHelper
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.interfaces.DebugOptionsInterface
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.services.OverlayService
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DESIGN_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.FORCIBLY_SHOW_RATE_THE_APP
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IS_ENABLE_CHECK_UPDATE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IS_ENABLE_WAKELOCK
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOMINAL_BATTERY_VOLTAGE_PREF
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_HISTORY_FOR_BATTERY_WEAR_NEW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class DebugFragment : PreferenceFragmentCompat(), DebugOptionsInterface {

    private lateinit var pref: SharedPreferences

    private var forciblyShowRateTheApp: SwitchPreferenceCompat? = null
    private var isEnableWakelock: SwitchPreferenceCompat? = null
    private var isEnableCheckUpdate: SwitchPreferenceCompat? = null
    private var nominalBatteryVoltage: ListPreference? = null
    private var numberOfHistoryForBatteryWearNew: ListPreference? = null
    private var addSetting: Preference? = null
    private var changeSetting: Preference? = null
    private var resetSetting: Preference? = null
    private var resetSettings: Preference? = null
    private var resetScreenTime: Preference? = null
    private var addCustomHistory: Preference? = null
    private var addHistory: Preference? = null
    private var addTenHistory: Preference? = null
    private var addFiftyHistory: Preference? = null
    private var historyCount: Preference? = null
    private var startBatteryLabService: Preference? = null
    private var stopBatteryLabService: Preference? = null
    private var restartBatteryLabService: Preference? = null
    private var stopOverlayService: Preference? = null
    private var restartOverlayService: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        addPreferencesFromResource(R.xml.debug_settings)

        forciblyShowRateTheApp = findPreference(FORCIBLY_SHOW_RATE_THE_APP)

        isEnableWakelock = findPreference(IS_ENABLE_WAKELOCK)

        isEnableCheckUpdate = findPreference(IS_ENABLE_CHECK_UPDATE)

        nominalBatteryVoltage = findPreference(NOMINAL_BATTERY_VOLTAGE_PREF)

        numberOfHistoryForBatteryWearNew = findPreference(NUMBER_OF_HISTORY_FOR_BATTERY_WEAR_NEW)

        addSetting = findPreference("add_setting")

        changeSetting = findPreference("change_setting")

        resetSetting = findPreference("reset_setting")

        resetSettings = findPreference("reset_settings")

        resetScreenTime = findPreference("reset_screen_time")

        addCustomHistory = findPreference("add_custom_history")

        addHistory = findPreference("add_history")

        addTenHistory = findPreference("add_ten_history")

        addFiftyHistory = findPreference("add_fifty_history")

        historyCount = findPreference("history_count")

        startBatteryLabService = findPreference("start_battery_lab_service")

        stopBatteryLabService = findPreference("stop_battery_lab_service")

        restartBatteryLabService = findPreference("restart_battery_lab_service")

        stopOverlayService = findPreference("stop_overlay_service")

        restartOverlayService = findPreference("restart_overlay_service")

        forciblyShowRateTheApp?.isVisible = !isGooglePlay(requireContext())

        isEnableWakelock?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == false) BatteryLabService.instance?.wakeLockRelease()
            true
        }

        nominalBatteryVoltage?.apply {
            summary = "${pref.getString(NOMINAL_BATTERY_VOLTAGE_PREF, "3.87")} V"
            setOnPreferenceChangeListener { prefView, newValue ->
                prefView.summary = "${newValue ?: "3.87"} V"
                true
            }
        }

        numberOfHistoryForBatteryWearNew?.apply {
            val defaultSamples =
                "${resources.getInteger(R.integer.number_of_history_for_battery_wear_new_default)}"
            val currentSamples = pref.getString(
                NUMBER_OF_HISTORY_FOR_BATTERY_WEAR_NEW, defaultSamples
            ) ?: defaultSamples
            summary = getString(R.string.number_of_history_for_battery_wear_summary, currentSamples)
            setOnPreferenceChangeListener { prefView, newValue ->
                prefView.summary = getString(
                    R.string.number_of_history_for_battery_wear_summary,
                    (newValue as? String) ?: defaultSamples
                )
                true
            }
        }

        resetScreenTime?.isEnabled = (BatteryLabService.instance?.screenTime ?: 0) > 0L

        addCustomHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())

        addHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())

        addTenHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())

        addFiftyHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())

        resetScreenTime?.setOnPreferenceClickListener {

            if ((BatteryLabService.instance?.screenTime ?: 0L) > 0L) {

                try {
                    BatteryLabService.instance!!.screenTime = 0L
                    Toast.makeText(
                        requireContext(), getString(R.string.success),
                        Toast.LENGTH_LONG
                    ).show()
                } catch (_: KotlinNullPointerException) {
                    Toast.makeText(
                        requireContext(), getString(R.string.error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else Toast.makeText(
                requireContext(), getString(R.string.error),
                Toast.LENGTH_LONG
            ).show()

            true
        }

        addCustomHistory?.setOnPreferenceClickListener {

            onAddCustomHistory(
                pref, arrayListOf(it, addHistory, addTenHistory, addFiftyHistory),
                historyCount
            )

            true
        }

        addHistory?.setOnPreferenceClickListener {

            addCustomHistory?.isEnabled = false
            it.isEnabled = false
            addTenHistory?.isEnabled = false
            addFiftyHistory?.isEnabled = false

            val designCapacity = pref.getInt(
                DESIGN_CAPACITY, resources.getInteger(
                    R.integer.min_design_capacity
                )
            )
            val date = DateHelper.getDate(
                (1..31).random(), (1..12).random(),
                DateHelper.getCurrentYear()
            )
            val residualCapacity = if (pref.getString(
                    PreferencesKeys
                        .UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh"
                ) == "μAh"
            ) ((
                    designCapacity * 0.01).toInt() * 1000..(designCapacity + (
                    (designCapacity / 1000) * 5)) * 1000).random()
            else ((designCapacity * 0.01).toInt() * 100..(designCapacity + (
                    (designCapacity / 1000) * 5)) * 100).random()

            HistoryHelper.addHistory(requireContext(), date, residualCapacity)

            val historyDB = HistoryDB(requireContext()).readAll()

            if (historyDB.isNotEmpty() && historyDB[historyDB.size - 1].date == date)
                Toast.makeText(
                    requireContext(), "$date: $residualCapacity",
                    Toast.LENGTH_LONG
                ).show()
            else Toast.makeText(requireContext(), "0.0.0: 0", Toast.LENGTH_LONG).show()

            addCustomHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())
            it.isEnabled = !HistoryHelper.isHistoryMax(requireContext())
            addTenHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())
            addFiftyHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())
            historyCount?.summary = "${HistoryHelper.getHistoryCount(requireContext())}"

            true
        }

        addTenHistory?.setOnPreferenceClickListener {

            addCustomHistory?.isEnabled = false
            addHistory?.isEnabled = false
            it.isEnabled = false
            addFiftyHistory?.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {

                for (i in 1..10) {

                    if (HistoryHelper.isHistoryMax(requireContext())) break

                    val designCapacity = pref.getInt(
                        DESIGN_CAPACITY, resources.getInteger(
                            R.integer.min_design_capacity
                        )
                    )
                    val date = DateHelper.getDate(
                        (1..31).random(), (1..12).random(),
                        DateHelper.getCurrentYear()
                    )
                    val residualCapacity = if (pref.getString(
                            PreferencesKeys
                                .UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh"
                        ) == "μAh"
                    ) ((
                            designCapacity * 0.01).toInt() * 1000..(designCapacity + (
                            (designCapacity / 1000) * 5)) * 1000).random()
                    else ((designCapacity * 0.01).toInt() * 100..(designCapacity + (
                            (designCapacity / 1000) * 5)) * 100).random()

                    HistoryHelper.addHistory(requireContext(), date, residualCapacity)

                    val historyDB = HistoryDB(requireContext()).readAll()

                    withContext(Dispatchers.Main) {
                        if (i == 10 && historyDB.isNotEmpty() && historyDB[historyDB.size - 1]
                                .date == date
                        )
                            Toast.makeText(
                                requireContext(), "$date: $residualCapacity",
                                Toast.LENGTH_LONG
                            ).show()
                        else if (historyDB.isEmpty() || historyDB[historyDB.size - 1].date != date) {
                            Toast.makeText(
                                requireContext(), "$i: 0.0.0: 0",
                                Toast.LENGTH_LONG
                            ).show()
                            delay(3.5.seconds)
                        }
                    }
                }

                withContext(Dispatchers.Main) {

                    addCustomHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())
                    addHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())
                    it.isEnabled = !HistoryHelper.isHistoryMax(requireContext())
                    addFiftyHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())
                    historyCount?.summary = "${HistoryHelper.getHistoryCount(requireContext())}"
                }
            }

            true
        }

        addFiftyHistory?.setOnPreferenceClickListener {

            addCustomHistory?.isEnabled = false
            addHistory?.isEnabled = false
            addTenHistory?.isEnabled = false
            it.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {

                for (i in 1..50) {

                    if (HistoryHelper.isHistoryMax(requireContext())) break

                    val designCapacity = pref.getInt(
                        DESIGN_CAPACITY, resources.getInteger(
                            R.integer.min_design_capacity
                        )
                    )
                    val date = DateHelper.getDate(
                        (1..31).random(), (1..12).random(),
                        DateHelper.getCurrentYear()
                    )
                    val residualCapacity = if (pref.getString(
                            PreferencesKeys
                                .UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh"
                        ) == "μAh"
                    ) ((
                            designCapacity * 0.01).toInt() * 1000..(designCapacity + (
                            (designCapacity / 1000) * 5)) * 1000).random()
                    else ((designCapacity * 0.01).toInt() * 100..(designCapacity + (
                            (designCapacity / 1000) * 5)) * 100).random()

                    HistoryHelper.addHistory(requireContext(), date, residualCapacity)

                    val historyDB = HistoryDB(requireContext()).readAll()

                    withContext(Dispatchers.Main) {
                        if (i == 10 && historyDB.isNotEmpty() && historyDB[historyDB.size - 1]
                                .date == date
                        )
                            Toast.makeText(
                                requireContext(), "$date: $residualCapacity",
                                Toast.LENGTH_LONG
                            ).show()
                        else if (historyDB.isEmpty() || historyDB[historyDB.size - 1].date != date) {
                            Toast.makeText(
                                requireContext(), "$i: 0.0.0: 0",
                                Toast.LENGTH_LONG
                            ).show()
                            delay(3.5.seconds)
                        }
                    }
                }

                withContext(Dispatchers.Main) {

                    addCustomHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())
                    addHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())
                    addTenHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())
                    it.isEnabled = !HistoryHelper.isHistoryMax(requireContext())
                    historyCount?.summary = "${HistoryHelper.getHistoryCount(requireContext())}"
                }
            }

            true
        }

        startBatteryLabService?.isEnabled = BatteryLabService.instance == null && !ServiceHelper
            .isStartedBatteryLabService()

        stopBatteryLabService?.isEnabled = BatteryLabService.instance != null

        restartBatteryLabService?.isEnabled = BatteryLabService.instance != null

        stopOverlayService?.isEnabled = OverlayService.instance != null

        restartOverlayService?.isEnabled = OverlayService.instance != null

        addSetting?.setOnPreferenceClickListener {

            addSettingDialog(pref)

            true
        }

        changeSetting?.setOnPreferenceClickListener {

            changeSettingDialog(pref)

            true
        }

        resetSetting?.setOnPreferenceClickListener {

            resetSettingDialog(pref)

            true
        }

        resetSettings?.setOnPreferenceClickListener {

            resetSettingsDialog(pref)

            true
        }

        startBatteryLabService?.setOnPreferenceClickListener {

            it.isEnabled = false

            ServiceHelper.startService(requireContext(), BatteryLabService::class.java)

            CoroutineScope(Dispatchers.Main).launch {

                delay(3.7.seconds)
                it.isEnabled = BatteryLabService.instance == null && !ServiceHelper
                    .isStartedBatteryLabService()

                stopBatteryLabService?.isEnabled = BatteryLabService.instance != null

                restartBatteryLabService?.isEnabled = BatteryLabService.instance != null
            }

            true
        }

        stopBatteryLabService?.setOnPreferenceClickListener {

            it.isEnabled = false

            restartBatteryLabService?.isEnabled = false

            ServiceHelper.stopService(requireContext(), BatteryLabService::class.java)

            CoroutineScope(Dispatchers.Main).launch {

                delay(2.5.seconds)
                startBatteryLabService?.isEnabled = BatteryLabService.instance == null
                        && !ServiceHelper.isStartedBatteryLabService()

                it.isEnabled = BatteryLabService.instance != null

                restartBatteryLabService?.isEnabled = BatteryLabService.instance != null
            }

            true
        }

        restartBatteryLabService?.setOnPreferenceClickListener {

            it.isEnabled = false

            stopBatteryLabService?.isEnabled = false

            ServiceHelper.restartService(requireContext(), BatteryLabService::class.java)

            CoroutineScope(Dispatchers.Main).launch {

                delay(6.2.seconds)
                startBatteryLabService?.isEnabled = BatteryLabService.instance == null
                        && !ServiceHelper.isStartedBatteryLabService()

                stopBatteryLabService?.isEnabled = BatteryLabService.instance != null

                it.isEnabled = BatteryLabService.instance != null
            }

            true
        }

        stopOverlayService?.setOnPreferenceClickListener {

            it.isEnabled = false

            restartOverlayService?.isEnabled = false

            ServiceHelper.stopService(requireContext(), OverlayService::class.java)

            CoroutineScope(Dispatchers.Main).launch {

                delay(1.5.seconds)
                it.isEnabled = OverlayService.instance != null

                restartOverlayService?.isEnabled = OverlayService.instance != null
            }

            true
        }

        restartOverlayService?.setOnPreferenceClickListener {

            it.isEnabled = false

            stopOverlayService?.isEnabled = false

            ServiceHelper.restartService(requireContext(), OverlayService::class.java)

            CoroutineScope(Dispatchers.Main).launch {

                delay(4.8.seconds)
                stopOverlayService?.isEnabled = OverlayService.instance != null

                it.isEnabled = OverlayService.instance != null
            }

            true
        }
    }

    override fun onResume() {

        super.onResume()

        resetScreenTime?.isEnabled = (BatteryLabService.instance?.screenTime ?: 0) > 0L

        addCustomHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())

        addHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())

        addTenHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())

        addFiftyHistory?.isEnabled = !HistoryHelper.isHistoryMax(requireContext())

        historyCount?.summary = "${HistoryHelper.getHistoryCount(requireContext())}"

        nominalBatteryVoltage?.summary =
            "${pref.getString(NOMINAL_BATTERY_VOLTAGE_PREF, "3.87")} V"

        val defaultSamples =
            "${resources.getInteger(R.integer.number_of_history_for_battery_wear_new_default)}"
        val currentSamples = pref.getString(
            NUMBER_OF_HISTORY_FOR_BATTERY_WEAR_NEW, defaultSamples
        ) ?: defaultSamples
        numberOfHistoryForBatteryWearNew?.summary = getString(
            R.string.number_of_history_for_battery_wear_summary, currentSamples
        )

        startBatteryLabService?.isEnabled = BatteryLabService.instance == null && !ServiceHelper
            .isStartedBatteryLabService()

        stopBatteryLabService?.isEnabled = BatteryLabService.instance != null

        restartBatteryLabService?.isEnabled = BatteryLabService.instance != null

        stopOverlayService?.isEnabled = OverlayService.instance != null

        restartOverlayService?.isEnabled = OverlayService.instance != null

        if (!pref.getBoolean(
                PreferencesKeys.ENABLED_DEBUG_OPTIONS, resources.getBoolean(
                    R.bool
                        .enabled_debug_options
                )
            )
        )
            requireActivity().onBackPressedDispatcher.onBackPressed()
    }
}