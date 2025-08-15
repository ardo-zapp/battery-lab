package com.jacktor.batterylab.interfaces

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databinding.ChangeDesignCapacityDialogBinding
import com.jacktor.batterylab.fragments.SettingsFragment
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DESIGN_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TAB_ON_APPLICATION_LAUNCH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TEXT_FONT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TEXT_SIZE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TEXT_STYLE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UNIT_OF_CHARGE_DISCHARGE_CURRENT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.VOLTAGE_UNIT

interface SettingsInterface {

    fun SettingsFragment.getOnTextSizeSummary(): String? {

        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        if (pref.getString(TEXT_SIZE, "2") !in
            resources.getStringArray(R.array.text_size_values)
        )
            pref.edit { putString(TEXT_SIZE, "2") }

        return resources.getStringArray(R.array.text_size_list)[
                (pref.getString(TEXT_SIZE, "2") ?: "2").toInt()]
    }

    fun SettingsFragment.getTextFontSummary(): String? {

        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        if (pref.getString(TEXT_FONT, "6") !in
            resources.getStringArray(R.array.fonts_values)
        )
            pref.edit { putString(TEXT_FONT, "6") }

        return resources.getStringArray(R.array.fonts_list)[
                (pref.getString(TEXT_FONT, "6") ?: "6").toInt()]
    }

    fun SettingsFragment.getTextStyleSummary(): String? {

        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        if (pref.getString(TEXT_STYLE, "0") !in
            resources.getStringArray(R.array.text_style_values)
        )
            pref.edit { putString(TEXT_STYLE, "0") }

        return resources.getStringArray(R.array.text_style_list)[
                (pref.getString(TEXT_STYLE, "0") ?: "0").toInt()]
    }

    fun SettingsFragment.getTabOnApplicationLaunchSummary(): String? {

        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        if (pref.getString(TAB_ON_APPLICATION_LAUNCH, "0") !in
            resources.getStringArray(R.array.tab_on_application_launch_values)
        )
            pref.edit { putString(TAB_ON_APPLICATION_LAUNCH, "0") }

        return resources.getStringArray(R.array.tab_on_application_launch_list)[
                (pref.getString(TAB_ON_APPLICATION_LAUNCH, "0") ?: "0").toInt()]
    }

    fun SettingsFragment.getUnitOfChargeDischargeCurrentSummary(): String? {

        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        if (pref.getString(UNIT_OF_CHARGE_DISCHARGE_CURRENT, "μA")
            !in resources.getStringArray(R.array.unit_of_charge_discharge_current_values)
        )
            pref.edit { putString(UNIT_OF_CHARGE_DISCHARGE_CURRENT, "μA") }

        return when (pref.getString(UNIT_OF_CHARGE_DISCHARGE_CURRENT, "μA")) {

            "μA" -> resources.getStringArray(
                R.array.unit_of_charge_discharge_current_list
            )[0]

            "mA" -> resources.getStringArray(
                R.array.unit_of_charge_discharge_current_list
            )[1]

            else -> pref.getString(UNIT_OF_CHARGE_DISCHARGE_CURRENT, "μA")
        }
    }

    fun SettingsFragment.getUnitOfMeasurementOfCurrentCapacitySummary(): String? {

        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        if (pref.getString(UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY, "μAh")
            !in resources.getStringArray(
                R.array.unit_of_measurement_of_current_capacity_values
            )
        )
            pref.edit { putString(UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY, "μAh") }

        return when (pref.getString(UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY, "μAh")) {

            "μAh" -> resources.getStringArray(
                R.array.unit_of_measurement_of_current_capacity_list
            )[0]

            "mAh" -> resources.getStringArray(
                R.array.unit_of_measurement_of_current_capacity_list
            )[1]

            else -> pref.getString(UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY, "μAh")
        }
    }

    fun SettingsFragment.getVoltageUnitSummary(): String? {

        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        if (pref.getString(VOLTAGE_UNIT, "mV")
            !in resources.getStringArray(
                R.array.voltage_unit_values
            )
        )
            pref.edit { putString(VOLTAGE_UNIT, "mV") }

        return when (pref.getString(VOLTAGE_UNIT, "mV")) {

            "μV" -> resources.getStringArray(R.array.voltage_unit_list)[0]

            "mV" -> resources.getStringArray(R.array.voltage_unit_list)[1]

            else -> pref.getString(VOLTAGE_UNIT, "mV")
        }
    }

    fun SettingsFragment.onChangeDesignCapacity(designCapacity: Preference? = null) {

        onChangeDesignCapacity(requireContext(), designCapacity)
    }

    /*fun BatteryInfoFragment.onChangeDesignCapacity(designCapacity: Preference? = null) {

        onChangeDesignCapacity(requireContext(), designCapacity)
    }*/

    private fun onChangeDesignCapacity(context: Context, designCapacity: Preference? = null) {

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val dialog = MaterialAlertDialogBuilder(context)

        val binding = ChangeDesignCapacityDialogBinding.inflate(
            LayoutInflater.from(context),
            null, false
        )

        dialog.setView(binding.root.rootView)

        binding.changeDesignCapacityEdit.setText(
            if (pref.getInt(
                    DESIGN_CAPACITY,
                    context.resources.getInteger(R.integer.min_design_capacity)
                ) >=
                context.resources.getInteger(R.integer.min_design_capacity)
            )
                pref.getInt(
                    DESIGN_CAPACITY, context.resources.getInteger(
                        R.integer.min_design_capacity
                    )
                ).toString() else
                context.resources.getInteger(R.integer.min_design_capacity).toString()
        )

        dialog.setPositiveButton(context.getString(R.string.change)) { _, _ ->

            pref.edit {
                putInt(
                    DESIGN_CAPACITY, binding.changeDesignCapacityEdit.text.toString()
                        .toInt()
                )
            }

            designCapacity?.summary = binding.changeDesignCapacityEdit.text.toString()
        }

        dialog.setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }

        val dialogCreate = dialog.create()

        changeDesignCapacityDialogCreateShowListener(
            context, dialogCreate,
            binding.changeDesignCapacityEdit, pref
        )

        dialogCreate.show()
    }

    private fun changeDesignCapacityDialogCreateShowListener(
        context: Context,
        dialogCreate: AlertDialog,
        changeDesignCapacity: TextInputEditText,
        pref: SharedPreferences
    ) {

        dialogCreate.setOnShowListener {

            dialogCreate.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false

            changeDesignCapacity.addTextChangedListener(object : TextWatcher {

                override fun afterTextChanged(s: Editable) {}

                override fun beforeTextChanged(
                    s: CharSequence, start: Int, count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

                    dialogCreate.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = try {
                        s.isNotEmpty() && s.toString() != pref.getInt(
                            DESIGN_CAPACITY,
                            context.resources.getInteger(R.integer.min_design_capacity)
                        ).toString()
                                && s.toString().toInt() >= context.resources.getInteger(
                            R.integer.min_design_capacity
                        ) && s.toString().toInt() <=
                                context.resources.getInteger(R.integer.max_design_capacity)
                    } catch (e: NumberFormatException) {
                        Toast.makeText(
                            context, e.message ?: e.toString(),
                            Toast.LENGTH_LONG
                        ).show()
                        false
                    }

                }
            })
        }
    }
}