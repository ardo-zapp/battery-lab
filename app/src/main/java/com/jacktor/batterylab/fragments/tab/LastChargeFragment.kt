package com.jacktor.batterylab.fragments.tab

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databinding.LastChargeFragmentBinding
import com.jacktor.batterylab.helpers.TimeHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_CHARGE_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_TEMP_CELSIUS_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AVERAGE_TEMP_FAHRENHEIT_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_TO
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_WITH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_ADDED_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_IN_WH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_TIME_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CURRENT_CAPACITY_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.FAST_CHARGE_WATTS_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IS_FAST_CHARGE_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.LAST_CHARGE_TIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.LAST_CHARGE_TIMESTAMP
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MAX_CHARGE_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MAX_TEMP_CELSIUS_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MAX_TEMP_FAHRENHEIT_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MIN_CHARGE_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MIN_TEMP_CELSIUS_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.MIN_TEMP_FAHRENHEIT_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.PERCENT_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.PERCENT_ADDED_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SOURCE_OF_POWER_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.STATUS_LAST_CHARGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.VOLTAGE_LAST_CHARGE
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LastChargeFragment : Fragment(R.layout.last_charge_fragment), BatteryInfoInterface {

    private var _binding: LastChargeFragmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var pref: SharedPreferences

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.endsWith("_last_charge") || key == LAST_CHARGE_TIMESTAMP || key == CAPACITY_IN_WH) {
            if (isAdded && _binding != null) updateUI()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LastChargeFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        pref.registerOnSharedPreferenceChangeListener(prefListener)
        updateUI()
    }

    override fun onPause() {
        pref.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateUI() {
        val ctx = context ?: return
        val chargingTime = pref.getInt(CHARGING_TIME_LAST_CHARGE, 0)
            .takeIf { it > 0 } ?: pref.getInt(LAST_CHARGE_TIME, 0)
        val timestamp = pref.getLong(LAST_CHARGE_TIMESTAMP, 0L)
        val hasSnapshot = chargingTime > 0 || timestamp > 0L

        binding.cardEmptyState.isVisible = !hasSnapshot
        binding.layoutContent.isVisible = hasSnapshot
        if (!hasSnapshot) return

        val df = DecimalFormat("#.#")
        val df2 = DecimalFormat("#.##")
        val isWh = pref.getBoolean(CAPACITY_IN_WH, resources.getBoolean(R.bool.capacity_in_wh))

        if (timestamp > 0L) {
            val formattedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                .format(Date(timestamp))
            binding.tvRecordedAt.text = getString(R.string.last_charge_recorded_at, formattedDate)
            binding.tvRecordedAt.isVisible = true
        } else {
            binding.tvRecordedAt.isVisible = false
        }

        val startLevel = pref.getInt(BATTERY_LEVEL_WITH, -1)
        val endLevel = pref.getInt(BATTERY_LEVEL_LAST_CHARGE, -1)
            .takeIf { it >= 0 } ?: pref.getInt(BATTERY_LEVEL_TO, 0)
        binding.tvBatteryLevel.text = if (startLevel in 0..endLevel) {
            "$startLevel% → $endLevel%"
        } else {
            "$endLevel%"
        }

        binding.tvChargingDuration.text = TimeHelper.getTime(chargingTime.toLong())

        val capAdded = pref.getFloat(CAPACITY_ADDED_LAST_CHARGE, 0f)
            .takeIf { it > 0f }?.toDouble()
            ?: pref.getFloat(CAPACITY_ADDED, 0f).toDouble()
        val pctAdded = pref.getInt(PERCENT_ADDED_LAST_CHARGE, 0)
            .takeIf { it > 0 } ?: pref.getInt(PERCENT_ADDED, (endLevel - startLevel).coerceAtLeast(0))
        binding.tvCapacityAdded.text = if (isWh) {
            "+${df.format(getCapacityInWh(ctx, capAdded))} Wh ($pctAdded%)"
        } else {
            "+${df.format(capAdded)} mAh ($pctAdded%)"
        }

        val currentCap = pref.getFloat(CURRENT_CAPACITY_LAST_CHARGE, 0f)
            .takeIf { it > 0f }?.toDouble()
            ?: getResidualCapacityMah(ctx)
        binding.tvCurrentCapacity.text = if (isWh) {
            "${df.format(getCapacityInWh(ctx, currentCap))} Wh"
        } else {
            "${df.format(currentCap)} mAh"
        }

        val hasFullTelemetry = timestamp > 0L
        binding.cardCurrentPower.isVisible = hasFullTelemetry
        binding.cardThermalVoltage.isVisible = hasFullTelemetry

        binding.tvEndStatus.text = when (pref.getString(STATUS_LAST_CHARGE, if (endLevel >= 100) "full" else "unknown")) {
            "full" -> getString(R.string.full)
            "charging" -> getString(R.string.charging)
            "discharging" -> getString(R.string.discharging)
            "not_charging" -> getString(R.string.not_charging)
            else -> getString(R.string.unknown)
        }

        binding.tvPowerSource.text = when (pref.getString(SOURCE_OF_POWER_LAST_CHARGE, "na")) {
            "ac" -> getString(R.string.source_of_power_ac)
            "usb" -> getString(R.string.source_of_power_usb)
            "wireless" -> getString(R.string.source_of_power_wireless)
            "dock" -> getString(R.string.source_of_power_dock)
            else -> "N/A"
        }

        val isFast = pref.getBoolean(IS_FAST_CHARGE_LAST_CHARGE, false)
        val fastWatts = pref.getFloat(FAST_CHARGE_WATTS_LAST_CHARGE, 0f).toDouble()
        binding.tvFastCharge.text = if (isFast) {
            getString(R.string.fast_charge_yes, df.format(fastWatts))
        } else {
            getString(R.string.fast_charge_no)
        }

        binding.tvMaxCurrent.text = "${pref.getInt(MAX_CHARGE_LAST_CHARGE, 0)} mA"
        binding.tvAvgCurrent.text = "${pref.getInt(AVERAGE_CHARGE_LAST_CHARGE, 0)} mA"
        binding.tvMinCurrent.text = "${pref.getInt(MIN_CHARGE_LAST_CHARGE, 0)} mA"

        binding.tvMaxTemp.text = formatTempPair(
            df,
            pref.getFloat(MAX_TEMP_CELSIUS_LAST_CHARGE, 0f).toDouble(),
            pref.getFloat(MAX_TEMP_FAHRENHEIT_LAST_CHARGE, 0f).toDouble()
        )
        binding.tvAvgTemp.text = formatTempPair(
            df,
            pref.getFloat(AVERAGE_TEMP_CELSIUS_LAST_CHARGE, 0f).toDouble(),
            pref.getFloat(AVERAGE_TEMP_FAHRENHEIT_LAST_CHARGE, 0f).toDouble()
        )
        binding.tvMinTemp.text = formatTempPair(
            df,
            pref.getFloat(MIN_TEMP_CELSIUS_LAST_CHARGE, 0f).toDouble(),
            pref.getFloat(MIN_TEMP_FAHRENHEIT_LAST_CHARGE, 0f).toDouble()
        )

        val voltage = pref.getFloat(VOLTAGE_LAST_CHARGE, 0f).toDouble()
        binding.tvVoltage.text = "${df2.format(voltage)} V"
    }

    private fun formatTempPair(df: DecimalFormat, celsius: Double, fahrenheit: Double): String =
        "${df.format(celsius)} °C (${df.format(fahrenheit)} °F)"
}
