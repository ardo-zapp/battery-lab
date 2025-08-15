package com.jacktor.batterylab.fragments

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databinding.ChangeBatteryIsChargedDischargedVoltageDialogBinding
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.interfaces.NotificationInterface
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.services.FullChargeReminderJobService
import com.jacktor.batterylab.utilities.Constants
import com.jacktor.batterylab.utilities.Constants.EXPORT_NOTIFICATION_SOUNDS_REQUEST_CODE
import com.jacktor.batterylab.utilities.Constants.POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_NOTIFY_CHARGED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_NOTIFY_DISCHARGED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_NOTIFY_DISCHARGED_VOLTAGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.FULL_CHARGE_REMINDER_FREQUENCY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_BATTERY_IS_CHARGED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_BATTERY_IS_DISCHARGED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_BATTERY_IS_DISCHARGED_VOLTAGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_BATTERY_IS_FULLY_CHARGED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_FULL_CHARGE_REMINDER
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_OVERHEAT_OVERCOOL
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERCOOL_DEGREES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERHEAT_DEGREES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import kotlin.time.Duration.Companion.minutes

class BatteryStatusInformationFragment : PreferenceFragmentCompat() {

    private lateinit var pref: SharedPreferences
    private lateinit var getResult: ActivityResultLauncher<Intent>

    private var batteryStatusInformationSettingsPrefCategory: PreferenceCategory? = null
    private var allowAllAppNotifications: Preference? = null
    private var notifyOverheatOvercool: SwitchPreferenceCompat? = null
    private var overheatDegrees: SeekBarPreference? = null
    private var overcoolDegrees: SeekBarPreference? = null
    private var notifyBatteryIsFullyCharged: SwitchPreferenceCompat? = null
    private var notifyFullChargeReminder: SwitchPreferenceCompat? = null
    private var notifyBatteryIsCharged: SwitchPreferenceCompat? = null
    private var batteryLevelNotifyCharged: SeekBarPreference? = null
    private var notifyBatteryIsDischarged: SwitchPreferenceCompat? = null
    private var notifyBatteryIsDischargedVoltage: SwitchPreferenceCompat? = null
    private var batteryLevelNotifyDischarged: SeekBarPreference? = null
    private var batteryNotifyDischargedVoltage: SeekBarPreference? = null
    private var exportNotificationSounds: Preference? = null
    private var fullChargeReminderFrequency: ListPreference? = null

    private var requestCode = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        addPreferencesFromResource(R.xml.battery_status_information_settings)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            permissions.entries.forEach {
                if (it.value) {
                    when (requestCode) {

                        POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE -> {
                            allowAllAppNotifications?.isVisible = !it.value
                            batteryStatusInformationSettingsPrefCategory?.isEnabled = it.value
                        }
                    }
                }
            }
        }

        getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            when (requestCode) {
                EXPORT_NOTIFICATION_SOUNDS_REQUEST_CODE -> if (it.resultCode == Activity.RESULT_OK)
                    exportNotificationSounds(it.data)
            }
        }

        batteryStatusInformationSettingsPrefCategory =
            findPreference("battery_status_information_settings_pref_category")
        allowAllAppNotifications = findPreference("allow_all_app_notifications")
        notifyOverheatOvercool = findPreference(NOTIFY_OVERHEAT_OVERCOOL)
        overheatDegrees = findPreference(OVERHEAT_DEGREES)
        overcoolDegrees = findPreference(OVERCOOL_DEGREES)
        notifyBatteryIsFullyCharged = findPreference(NOTIFY_BATTERY_IS_FULLY_CHARGED)
        notifyFullChargeReminder = findPreference(NOTIFY_FULL_CHARGE_REMINDER)
        notifyBatteryIsCharged = findPreference(NOTIFY_BATTERY_IS_CHARGED)
        batteryLevelNotifyCharged = findPreference(BATTERY_LEVEL_NOTIFY_CHARGED)
        notifyBatteryIsDischarged = findPreference(NOTIFY_BATTERY_IS_DISCHARGED)
        notifyBatteryIsDischargedVoltage = findPreference(NOTIFY_BATTERY_IS_DISCHARGED_VOLTAGE)
        batteryLevelNotifyDischarged = findPreference(BATTERY_LEVEL_NOTIFY_DISCHARGED)
        batteryNotifyDischargedVoltage = findPreference(BATTERY_NOTIFY_DISCHARGED_VOLTAGE)
        exportNotificationSounds = findPreference("export_notification_sounds")
        fullChargeReminderFrequency = findPreference(FULL_CHARGE_REMINDER_FREQUENCY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_DENIED
            ) {
                requestCode = POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
            batteryStatusInformationSettingsPrefCategory?.isEnabled = ContextCompat
                .checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED

            allowAllAppNotifications?.apply {

                isVisible = ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_DENIED

                allowAllAppNotifications?.setOnPreferenceClickListener {
                    startActivity(Intent().apply {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    })

                    true
                }
            }
        }

        overheatDegrees?.apply {
            summary = getOverheatDegreesSummary()
            isEnabled = pref.getBoolean(
                NOTIFY_OVERHEAT_OVERCOOL, resources.getBoolean(
                    R.bool.notify_overheat_overcool
                )
            )
        }

        overcoolDegrees?.apply {
            summary = getOvercoolDegreesSummary()
            isEnabled = pref.getBoolean(
                NOTIFY_OVERHEAT_OVERCOOL, resources.getBoolean(
                    R.bool.notify_overheat_overcool
                )
            )
        }

        notifyFullChargeReminder?.isEnabled = pref.getBoolean(
            NOTIFY_BATTERY_IS_FULLY_CHARGED,
            resources.getBoolean(R.bool.notify_battery_is_fully_charged)
        )

        batteryLevelNotifyCharged?.apply {

            summary = getBatteryLevelNotifyChargingSummary()
            isEnabled = pref.getBoolean(
                NOTIFY_BATTERY_IS_CHARGED, resources.getBoolean(
                    R.bool.notify_battery_is_charged
                )
            )
        }

        batteryLevelNotifyDischarged?.apply {

            summary = getBatteryLevelNotifyDischargeSummary()
            isEnabled = pref.getBoolean(
                NOTIFY_BATTERY_IS_DISCHARGED, resources.getBoolean(
                    R.bool.notify_battery_is_discharged
                )
            )
        }

        batteryNotifyDischargedVoltage?.apply {
            summary = "${
                pref.getInt(
                    BATTERY_NOTIFY_DISCHARGED_VOLTAGE, resources.getInteger(
                        R.integer.battery_notify_discharged_voltage_min
                    )
                )
            }"
            isEnabled = pref.getBoolean(
                NOTIFY_BATTERY_IS_DISCHARGED_VOLTAGE,
                resources.getBoolean(R.bool.notify_battery_is_discharged_voltage)
            )
        }

        notifyOverheatOvercool?.setOnPreferenceChangeListener { _, newValue ->

            overheatDegrees?.isEnabled = newValue as? Boolean == true

            overcoolDegrees?.isEnabled = newValue as? Boolean == true

            NotificationInterface.notificationManager?.cancel(
                NotificationInterface.NOTIFICATION_BATTERY_OVERHEAT_OVERCOOL_ID
            )

            true
        }

        overheatDegrees?.setOnPreferenceChangeListener { preference, newValue ->

            val temperature = (newValue as? Int) ?: resources.getInteger(
                R.integer
                    .overheat_degrees_default
            )

            preference.summary = getString(
                R.string.overheat_overcool_degrees, temperature,
                DecimalFormat("#.#").format((temperature * 1.8) + 32.0)
            )

            true
        }

        overcoolDegrees?.setOnPreferenceChangeListener { preference, newValue ->

            val temperature = (newValue as? Int) ?: resources.getInteger(
                R.integer
                    .overcool_degrees_default
            )

            preference.summary = getString(
                R.string.overheat_overcool_degrees, temperature,
                DecimalFormat("#.#").format((temperature * 1.8) + 32.0)
            )

            true
        }

        notifyBatteryIsFullyCharged?.setOnPreferenceChangeListener { _, newValue ->

            NotificationInterface.notificationManager?.cancel(
                NotificationInterface.NOTIFICATION_FULLY_CHARGED_ID
            )

            val isChecked = (newValue as? Boolean) == true

            notifyFullChargeReminder?.isEnabled = isChecked
            fullChargeReminderFrequency?.isEnabled = isChecked &&
                    notifyFullChargeReminder?.isChecked == true

            if (!isChecked) ServiceHelper.cancelJob(
                requireContext(),
                Constants.NOTIFY_FULL_CHARGE_REMINDER_JOB_ID
            )

            true
        }

        notifyFullChargeReminder?.setOnPreferenceChangeListener { _, newValue ->

            val isChecked = (newValue as? Boolean) == true

            fullChargeReminderFrequency?.isEnabled = isChecked

            val fullChargeReminderFrequency = pref.getString(
                FULL_CHARGE_REMINDER_FREQUENCY,
                "${resources.getInteger(R.integer.full_charge_reminder_frequency_default)}"
            )?.toInt()

            if (isChecked && BatteryLabService.instance?.isFull == true)
                ServiceHelper.jobSchedule(
                    requireContext(),
                    FullChargeReminderJobService::class.java,
                    Constants.NOTIFY_FULL_CHARGE_REMINDER_JOB_ID,
                    fullChargeReminderFrequency?.minutes?.inWholeMilliseconds ?: resources
                        .getInteger(R.integer.full_charge_reminder_frequency_default).minutes
                        .inWholeMilliseconds
                )
            else ServiceHelper.cancelJob(
                requireContext(),
                Constants.NOTIFY_FULL_CHARGE_REMINDER_JOB_ID
            )

            true
        }

        fullChargeReminderFrequency?.apply {
            isEnabled = notifyBatteryIsFullyCharged?.isChecked == true &&
                    notifyFullChargeReminder?.isChecked == true
            summary = getFullChargeReminderFrequencySummary()
            setOnPreferenceChangeListener { preference, newValue ->

                ServiceHelper.cancelJob(
                    requireContext(),
                    Constants.NOTIFY_FULL_CHARGE_REMINDER_JOB_ID
                )

                val fullChargeReminderFrequencyPref = pref.getString(
                    FULL_CHARGE_REMINDER_FREQUENCY,
                    "${resources.getInteger(R.integer.full_charge_reminder_frequency_default)}"
                )

                if (notifyFullChargeReminder?.isChecked == true &&
                    BatteryLabService.instance?.isFull == true
                )
                    ServiceHelper.jobSchedule(
                        requireContext(),
                        FullChargeReminderJobService::class.java,
                        Constants.NOTIFY_FULL_CHARGE_REMINDER_JOB_ID,
                        fullChargeReminderFrequencyPref?.toInt()?.minutes
                            ?.inWholeMilliseconds ?: resources.getInteger(
                            R.integer
                                .full_charge_reminder_frequency_default
                        ).minutes.inWholeMilliseconds
                    )

                preference.summary = resources.getStringArray(
                    R.array
                        .full_charge_reminder_frequency_list
                )[when ((newValue as? String)?.toInt()) {
                    15 -> 0
                    30 -> 1
                    45 -> 2
                    60 -> 3
                    else -> resources.getInteger(
                        R.integer.full_charge_reminder_frequency_default_index
                    )
                }]

                true
            }
        }

        notifyBatteryIsCharged?.setOnPreferenceChangeListener { _, newValue ->

            batteryLevelNotifyCharged?.isEnabled = newValue as? Boolean == true

            NotificationInterface.notificationManager?.cancel(
                NotificationInterface.NOTIFICATION_BATTERY_STATUS_ID
            )

            true
        }

        batteryLevelNotifyCharged?.setOnPreferenceChangeListener { preference, newValue ->

            preference.summary = "${
                ((newValue as? Int) ?: pref.getInt(
                    BATTERY_LEVEL_NOTIFY_CHARGED, 80
                ))
            }%"

            NotificationInterface.notificationManager?.cancel(
                NotificationInterface.NOTIFICATION_BATTERY_STATUS_ID
            )

            true
        }

        notifyBatteryIsDischarged?.setOnPreferenceChangeListener { _, newValue ->

            batteryLevelNotifyDischarged?.isEnabled = newValue as? Boolean == true

            NotificationInterface.notificationManager?.cancel(
                NotificationInterface.NOTIFICATION_BATTERY_STATUS_ID
            )

            true
        }

        batteryLevelNotifyDischarged?.setOnPreferenceChangeListener { preference, newValue ->

            preference.summary = "${
                ((newValue as? Int) ?: pref.getInt(
                    BATTERY_LEVEL_NOTIFY_DISCHARGED, 20
                ))
            }%"

            NotificationInterface.notificationManager?.cancel(
                NotificationInterface.NOTIFICATION_BATTERY_STATUS_ID
            )

            true
        }

        batteryNotifyDischargedVoltage?.setOnPreferenceClickListener {

            changeBatteryNotifyDischargedVoltage()

            true
        }

        notifyBatteryIsDischargedVoltage?.setOnPreferenceChangeListener { _, newValue ->

            batteryNotifyDischargedVoltage?.isEnabled = newValue as? Boolean == true

            NotificationInterface.notificationManager?.cancel(
                NotificationInterface.NOTIFICATION_BATTERY_STATUS_ID
            )

            true
        }

        batteryNotifyDischargedVoltage?.setOnPreferenceChangeListener { preference, newValue ->

            preference.summary = "${
                ((newValue as? Int) ?: pref.getInt(
                    BATTERY_NOTIFY_DISCHARGED_VOLTAGE, resources.getInteger(
                        R.integer
                            .battery_notify_discharged_voltage_min
                    )
                ))
            }"

            NotificationInterface.notificationManager?.cancel(
                NotificationInterface.NOTIFICATION_BATTERY_STATUS_ID
            )

            true
        }

        exportNotificationSounds?.setOnPreferenceClickListener {

            try {
                requestCode = EXPORT_NOTIFICATION_SOUNDS_REQUEST_CODE
                getResult.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    requireContext(), getString(
                        R.string
                            .error_exporting_notification_sounds, e.message ?: e.toString()
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }

            true
        }
    }

    override fun onResume() {

        super.onResume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            batteryStatusInformationSettingsPrefCategory?.isEnabled = ContextCompat
                .checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED

            allowAllAppNotifications?.isVisible = ContextCompat
                .checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_DENIED
        }

        fullChargeReminderFrequency?.apply {
            isEnabled = notifyBatteryIsFullyCharged?.isChecked == true &&
                    notifyFullChargeReminder?.isChecked == true
            summary = getFullChargeReminderFrequencySummary()
        }


        overheatDegrees?.apply {
            summary = getOverheatDegreesSummary()
            isEnabled = pref.getBoolean(
                NOTIFY_OVERHEAT_OVERCOOL, resources.getBoolean(
                    R.bool.notify_overheat_overcool
                )
            )
        }

        overcoolDegrees?.apply {
            summary = getOvercoolDegreesSummary()
            isEnabled = pref.getBoolean(
                NOTIFY_OVERHEAT_OVERCOOL, resources.getBoolean(
                    R.bool.notify_overheat_overcool
                )
            )
        }

        notifyOverheatOvercool?.isChecked = pref.getBoolean(
            NOTIFY_OVERHEAT_OVERCOOL, resources
                .getBoolean(R.bool.notify_overheat_overcool)
        )

        notifyBatteryIsFullyCharged?.isChecked = pref.getBoolean(
            NOTIFY_BATTERY_IS_FULLY_CHARGED,
            resources.getBoolean(R.bool.notify_battery_is_fully_charged)
        )

        notifyFullChargeReminder?.apply {

            isChecked = pref.getBoolean(
                NOTIFY_FULL_CHARGE_REMINDER,
                resources.getBoolean(R.bool.notify_full_charge_reminder_default_value)
            )

            isEnabled = notifyBatteryIsFullyCharged?.isChecked == true
        }

        notifyBatteryIsCharged?.isChecked = pref.getBoolean(
            NOTIFY_BATTERY_IS_CHARGED, resources
                .getBoolean(R.bool.notify_battery_is_charged)
        )

        notifyBatteryIsDischarged?.isChecked = pref.getBoolean(
            NOTIFY_BATTERY_IS_DISCHARGED,
            resources.getBoolean(R.bool.notify_battery_is_discharged)
        )

        notifyBatteryIsDischargedVoltage?.isChecked = pref.getBoolean(
            NOTIFY_BATTERY_IS_DISCHARGED_VOLTAGE, resources.getBoolean(
                R.bool
                    .notify_battery_is_discharged_voltage
            )
        )

        batteryLevelNotifyCharged?.apply {

            summary = getBatteryLevelNotifyChargingSummary()
            isEnabled = pref.getBoolean(
                NOTIFY_BATTERY_IS_CHARGED, resources.getBoolean(
                    R.bool.notify_battery_is_charged
                )
            )
        }

        batteryLevelNotifyDischarged?.apply {

            summary = getBatteryLevelNotifyDischargeSummary()
            isEnabled = pref.getBoolean(
                NOTIFY_BATTERY_IS_DISCHARGED, resources.getBoolean(
                    R.bool.notify_battery_is_discharged
                )
            )
        }

        batteryNotifyDischargedVoltage?.apply {
            summary = "${
                pref.getInt(
                    BATTERY_NOTIFY_DISCHARGED_VOLTAGE, resources.getInteger(
                        R.integer.battery_notify_discharged_voltage_min
                    )
                )
            }"
            isEnabled = pref.getBoolean(
                NOTIFY_BATTERY_IS_DISCHARGED_VOLTAGE,
                resources.getBoolean(R.bool.notify_battery_is_discharged_voltage)
            )
        }
    }

    private fun getFullChargeReminderFrequencySummary(): String {
        if (pref.getString(
                FULL_CHARGE_REMINDER_FREQUENCY,
                "${resources.getInteger(R.integer.full_charge_reminder_frequency_default)}"
            ) !in
            resources.getStringArray(R.array.full_charge_reminder_frequency_values)
        )
            pref.edit {
                putString(
                    FULL_CHARGE_REMINDER_FREQUENCY,
                    "${resources.getInteger(R.integer.full_charge_reminder_frequency_default)}"
                )
            }

        val fullChargeReminderFrequencyPref = pref.getString(
            FULL_CHARGE_REMINDER_FREQUENCY,
            "${resources.getInteger(R.integer.full_charge_reminder_frequency_default)}"
        )

        return resources.getStringArray(R.array.full_charge_reminder_frequency_list)[
            when (fullChargeReminderFrequencyPref?.toInt()) {
                15 -> 0
                30 -> 1
                45 -> 2
                60 -> 3
                else -> resources.getInteger(R.integer.full_charge_reminder_frequency_default_index)
            }]
    }

    private fun getOverheatDegreesSummary(): String {

        if (pref.getInt(OVERHEAT_DEGREES, resources.getInteger(R.integer.overheat_degrees_default))
            > resources.getInteger(R.integer.overheat_degrees_max) ||
            pref.getInt(OVERHEAT_DEGREES, resources.getInteger(R.integer.overheat_degrees_default))
            < resources.getInteger(R.integer.overheat_degrees_min)
        )
            pref.edit {
                putInt(
                    OVERHEAT_DEGREES, resources.getInteger(
                        R
                            .integer.overheat_degrees_default
                    )
                )
            }

        val temperature = pref.getInt(
            OVERHEAT_DEGREES,
            resources.getInteger(R.integer.overheat_degrees_default)
        )

        return getString(
            R.string.overheat_overcool_degrees, temperature, DecimalFormat(
                "#.#"
            ).format((temperature * 1.8) + 32.0)
        )
    }

    private fun getOvercoolDegreesSummary(): String {

        if (pref.getInt(OVERCOOL_DEGREES, resources.getInteger(R.integer.overcool_degrees_default))
            > resources.getInteger(R.integer.overcool_degrees_max) ||
            pref.getInt(OVERCOOL_DEGREES, resources.getInteger(R.integer.overcool_degrees_default))
            < resources.getInteger(R.integer.overcool_degrees_min)
        )
            pref.edit {
                putInt(
                    OVERCOOL_DEGREES, resources.getInteger(
                        R
                            .integer.overcool_degrees_default
                    )
                )
            }

        val temperature = pref.getInt(
            OVERCOOL_DEGREES,
            resources.getInteger(R.integer.overcool_degrees_default)
        )

        return getString(
            R.string.overheat_overcool_degrees, temperature, DecimalFormat(
                "#.#"
            ).format((temperature * 1.8) + 32.0)
        )
    }

    private fun getBatteryLevelNotifyChargingSummary(): String {

        if (pref.getInt(BATTERY_LEVEL_NOTIFY_CHARGED, 80) > 100 ||
            pref.getInt(BATTERY_LEVEL_NOTIFY_CHARGED, 80) < 1
        )
            pref.edit {
                putInt(BATTERY_LEVEL_NOTIFY_CHARGED, 80)
            }

        return "${pref.getInt(BATTERY_LEVEL_NOTIFY_CHARGED, 1)}%"
    }

    private fun getBatteryLevelNotifyDischargeSummary(): String {

        if (pref.getInt(BATTERY_LEVEL_NOTIFY_DISCHARGED, 20) > 99 ||
            pref.getInt(BATTERY_LEVEL_NOTIFY_DISCHARGED, 20) < 1
        )
            pref.edit {
                putInt(BATTERY_LEVEL_NOTIFY_DISCHARGED, 20)
            }

        return "${pref.getInt(BATTERY_LEVEL_NOTIFY_DISCHARGED, 20)}%"
    }

    private fun changeBatteryNotifyDischargedVoltage() {

        val dialog = MaterialAlertDialogBuilder(requireContext())

        val binding = ChangeBatteryIsChargedDischargedVoltageDialogBinding.inflate(
            LayoutInflater.from(context), null, false
        )

        dialog.setView(binding.root.rootView)

        binding.changeBatteryIsChargedDischargedMvEdit.setText(
            if (pref.getInt(
                    BATTERY_NOTIFY_DISCHARGED_VOLTAGE, resources.getInteger(
                        R.integer
                            .battery_notify_discharged_voltage_min
                    )
                ) >= resources.getInteger(
                    R.integer
                        .battery_notify_discharged_voltage_min
                ) || pref.getInt(
                    BATTERY_NOTIFY_DISCHARGED_VOLTAGE, resources.getInteger(
                        R.integer
                            .battery_notify_discharged_voltage_min
                    )
                ) <= resources.getInteger(
                    R.integer
                        .battery_notify_discharged_voltage_max
                )
            ) pref.getInt(
                BATTERY_NOTIFY_DISCHARGED_VOLTAGE, resources.getInteger(
                    R.integer
                        .battery_notify_discharged_voltage_min
                )
            ).toString()
            else resources.getInteger(R.integer.battery_notify_discharged_voltage_min).toString()
        )

        dialog.setPositiveButton(requireContext().getString(R.string.change)) { _, _ ->

            pref.edit {
                putInt(
                    BATTERY_NOTIFY_DISCHARGED_VOLTAGE,
                    binding.changeBatteryIsChargedDischargedMvEdit.text.toString().toInt()
                )
            }
        }

        dialog.setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }

        val dialogCreate = dialog.create()

        changeBatteryNotifyDischargedVoltageDialogCreateShowListener(
            dialogCreate,
            binding.changeBatteryIsChargedDischargedMvEdit
        )

        dialogCreate.show()
    }

    private fun changeBatteryNotifyDischargedVoltageDialogCreateShowListener(
        dialogCreate: AlertDialog,
        changeChargingCurrentLevel:
        TextInputEditText
    ) {

        dialogCreate.setOnShowListener {

            dialogCreate.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false

            changeChargingCurrentLevel.addTextChangedListener(object : TextWatcher {

                override fun afterTextChanged(s: Editable) {}

                override fun beforeTextChanged(
                    s: CharSequence, start: Int, count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

                    dialogCreate.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled =
                        s.isNotEmpty() && s.toString() != pref.getInt(
                            BATTERY_NOTIFY_DISCHARGED_VOLTAGE, resources.getInteger(
                                R.integer
                                    .battery_notify_discharged_voltage_min
                            )
                        ).toString()
                                && s.toString().toInt() >= resources.getInteger(
                            R.integer
                                .battery_notify_discharged_voltage_min
                        )
                                && s.toString().toInt() <= resources.getInteger(
                            R.integer
                                .battery_notify_discharged_voltage_max
                        )
                }
            })
        }
    }

    private fun exportNotificationSounds(intent: Intent?) {

        val notificationSoundsList = arrayListOf(
            R.raw.overheat_overcool,
            R.raw.battery_is_fully_charged, R.raw.battery_is_charged, R.raw.battery_is_discharged,
            R.raw.charging_current
        )

        CoroutineScope(Dispatchers.IO).launch {

            try {

                MainActivity.isOnBackPressed = false

                val pickerDir = intent?.data?.let {
                    context?.let { it1 -> DocumentFile.fromTreeUri(it1, it) }
                }

                notificationSoundsList.forEach {

                    pickerDir?.findFile(
                        "${
                            resources.getResourceEntryName(
                                it
                            )
                        }.mp3"
                    )?.delete()

                    val outputStream = pickerDir?.createFile(
                        "audio/mpeg",
                        resources.getResourceEntryName(it)
                    )?.uri?.let { it1 ->
                        context?.contentResolver?.openOutputStream(it1)
                    }

                    val fileInputStream = resources.openRawResource(it)
                    val buffer = byteArrayOf((1024 * 8).toByte())
                    var read: Int

                    while (true) {

                        read = fileInputStream.read(buffer)

                        if (read != -1)
                            outputStream?.write(buffer, 0, read)
                        else break
                    }

                    fileInputStream.close()
                    outputStream?.flush()
                    outputStream?.close()
                }

                withContext(Dispatchers.Main) {

                    MainActivity.isOnBackPressed = true

                    Toast.makeText(
                        context, context?.getString(
                            R.string
                                .successful_export_of_notification_sounds
                        ), Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {

                withContext(Dispatchers.Main) {

                    MainActivity.isOnBackPressed = true

                    Toast.makeText(
                        context, context?.getString(
                            R.string
                                .error_exporting_notification_sounds,
                            e.message ?: e.toString()
                        ), Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}