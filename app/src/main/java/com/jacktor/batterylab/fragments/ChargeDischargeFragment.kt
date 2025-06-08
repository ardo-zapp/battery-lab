package com.jacktor.batterylab.fragments

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.MainApp
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databinding.ChargeDischargeFragmentBinding
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import com.jacktor.batterylab.helpers.TextAppearanceHelper
import com.jacktor.batterylab.helpers.TimeHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.interfaces.SettingsInterface
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.utilities.Constants
import com.jacktor.batterylab.utilities.Constants.NUMBER_OF_CYCLES_PATH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_IN_WH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_DISCHARGE_CURRENT_IN_WATT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TEXT_FONT
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TEXT_SIZE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TEXT_STYLE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UPDATE_TEMP_SCREEN_TIME
import com.jacktor.batterylab.utilities.preferences.Prefs
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import kotlin.time.Duration.Companion.seconds

class ChargeDischargeFragment : Fragment(R.layout.charge_discharge_fragment), SettingsInterface,
    BatteryInfoInterface {

    private lateinit var binding: ChargeDischargeFragmentBinding
    private lateinit var pref: Prefs

    private var mainContext: MainActivity? = null
    private var job: Job? = null
    private var isJob = false
    private var isChargingDischargeCurrentInWatt = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {

        binding = ChargeDischargeFragmentBinding.inflate(inflater, container, false)

        pref = Prefs(requireContext())

        return binding.root.rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        mainContext = context as? MainActivity

        updateTextAppearance()

        binding.designCapacity.setOnClickListener {

            onChangeDesignCapacity()

            (it as? AppCompatTextView)?.text = getDesignCapacity()
        }
    }

    override fun onResume() {

        super.onResume()

        binding.designCapacity.text = getDesignCapacity()

        binding.numberOfCyclesAndroid.visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || File(NUMBER_OF_CYCLES_PATH).exists() || Shell.cmd("test -e $NUMBER_OF_CYCLES_PATH")
                    .exec().isSuccess
            ) View.VISIBLE else View.GONE

        binding.batteryHealth.apply {
            visibility = View.VISIBLE
            text = getString(
                R.string.battery_health, getString(
                    getBatteryHealth(requireContext()) ?: R.string.battery_health_great
                )
            )
        }

        binding.batteryHealthAndroid.text = getString(
            R.string.battery_health_android, getBatteryHealthAndroid(requireContext())
        )

        binding.residualCapacity.text = getString(R.string.residual_capacity, "0", "0%")

        binding.batteryWear.text = getString(R.string.battery_wear, "0%", "0")

        batteryIntent = requireContext().registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        isJob = true

        isChargingDischargeCurrentInWatt = pref.getBoolean(
            CHARGING_DISCHARGE_CURRENT_IN_WATT,
            resources.getBoolean(R.bool.charging_discharge_current_in_watt)
        )

        chargeDischargeInformationJob()
    }

    override fun onStop() {

        super.onStop()

        isJob = false
        job?.cancel()
        job = null
    }

    override fun onDestroy() {

        isJob = false
        job?.cancel()
        job = null

        super.onDestroy()
    }


    private fun getDesignCapacity(): String {

        val designCapacity = pref.getInt(
            PreferencesKeys.DESIGN_CAPACITY, resources.getInteger(
                R.integer.min_design_capacity
            )
        )

        val designCapacityWh =
            (designCapacity.toDouble() * Constants.NOMINAL_BATTERY_VOLTAGE) / 1000.0

        val isCapacityInWh = pref.getBoolean(
            CAPACITY_IN_WH, resources.getBoolean(
                R.bool.capacity_in_wh
            )
        )

        return if (isCapacityInWh) getString(
            R.string.design_capacity_wh, DecimalFormat("#.#").format(designCapacityWh)
        )
        else getString(R.string.design_capacity, "$designCapacity")
    }


    private fun chargeDischargeInformationJob() {

        if (job == null) job = CoroutineScope(Dispatchers.Default).launch {
            while (isJob) {

                withContext(Dispatchers.Main) {

                    updateTextAppearance()

                }

                val status = batteryIntent?.getIntExtra(
                    BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN
                ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
                val sourceOfPower = batteryIntent?.getIntExtra(
                    BatteryManager.EXTRA_PLUGGED, -1
                ) ?: -1


                //ICON
                withContext(Dispatchers.Main) {

                    //percentage icon
                    binding.icBatteryLevel.setImageResource(
                        BatteryLevelHelper.batteryLevelIcon(
                            getBatteryLevel(requireContext()),
                            status == BatteryManager.BATTERY_STATUS_CHARGING
                        )

                    )

                    //battery level color
                    if (status != BatteryManager.BATTERY_STATUS_CHARGING) {
                        when (getBatteryLevel(requireContext())) {
                            in 0..20 -> binding.icBatteryLevel.setColorFilter(
                                ContextCompat.getColor(
                                    requireContext(), R.color.red
                                )
                            )

                            else -> binding.icBatteryLevel.setColorFilter(
                                ContextCompat.getColor(
                                    requireContext(), R.color.green
                                )
                            )
                        }
                    } else {
                        binding.icBatteryLevel.setColorFilter(
                            ContextCompat.getColor(
                                requireContext(), R.color.blue
                            )
                        )
                    }

                    //plugged icon
                    val pluggedTypeIcon = binding.icPluggedType
                    when (sourceOfPower) {
                        0 -> {
                            when (pref.getInt("last_plugged", 1)) {
                                1 -> pluggedTypeIcon.setImageResource(R.drawable.ic_ac_unplugged_24)
                                2 -> pluggedTypeIcon.setImageResource(R.drawable.ic_usb_unplugged_24)
                                3 -> pluggedTypeIcon.setImageResource(R.drawable.ic_ac_unplugged_24)
                            }

                            pluggedTypeIcon.alpha = 0.66f
                            pluggedTypeIcon.setColorFilter(
                                ContextCompat.getColor(
                                    requireContext(), R.color.battery_off
                                )
                            )
                        }

                        1 -> {
                            pluggedTypeIcon.setImageResource(R.drawable.ic_ac_plugged_24)
                            pluggedTypeIcon.alpha = 1.0f
                            pluggedTypeIcon.setColorFilter(
                                ContextCompat.getColor(
                                    requireContext(), R.color.battery_charge
                                )
                            )
                            pref.setInt("last_plugged", 1)
                        }

                        2 -> {
                            pluggedTypeIcon.setImageResource(R.drawable.ic_usb_plugged_24)
                            pluggedTypeIcon.alpha = 1.0f
                            pluggedTypeIcon.setColorFilter(
                                ContextCompat.getColor(
                                    requireContext(), R.color.battery_charge
                                )
                            )
                            pref.setInt("last_plugged", 2)
                        }

                        3 -> {
                            pluggedTypeIcon.setImageResource(R.drawable.ic_wireless_charging_24)
                            pluggedTypeIcon.alpha = 1.0f
                            pluggedTypeIcon.setColorFilter(
                                ContextCompat.getColor(
                                    requireContext(), R.color.battery_charge
                                )
                            )
                            pref.setInt("last_plugged", 3)
                        }
                    }


                    //temp icon
                    if (getTemperature(requireContext())!! <= 190) {
                        binding.icTemperature.setColorFilter(
                            ContextCompat.getColor(
                                requireContext(), R.color.battery_temp_cold
                            )
                        )
                    }
                    if (getTemperature(requireContext())!! >= 200) {
                        binding.icTemperature.setColorFilter(
                            ContextCompat.getColor(
                                requireContext(), R.color.battery_temp_normal
                            )
                        )
                    }
                    if (getTemperature(requireContext())!! >= 300) {
                        binding.icTemperature.setColorFilter(
                            ContextCompat.getColor(
                                requireContext(), R.color.battery_temp_warm
                            )
                        )
                    }
                    if (getTemperature(requireContext())!! >= 400) {
                        binding.icTemperature.setColorFilter(
                            ContextCompat.getColor(
                                requireContext(), R.color.battery_temp_hot
                            )
                        )
                    }
                    if (getTemperature(requireContext())!! >= 500) {
                        binding.icTemperature.setColorFilter(
                            ContextCompat.getColor(
                                requireContext(), R.color.battery_temp_very_hot
                            )
                        )
                    }

                    //Toast.makeText(context, getTemperature(requireContext()).toString(), Toast.LENGTH_SHORT).show()


                }

                withContext(Dispatchers.Main) {

                    mainContext?.topAppBar?.title = getString(
                        if (status == BatteryManager.BATTERY_STATUS_CHARGING) R.string.charge
                        else R.string.discharge
                    )

                    val chargeDischargeNavigation =
                        mainContext?.navigation?.menu?.findItem(R.id.charge_discharge_navigation)

                    chargeDischargeNavigation?.title = getString(
                        if (status == BatteryManager.BATTERY_STATUS_CHARGING) R.string.charge else R.string.discharge
                    )

                    chargeDischargeNavigation?.icon = BatteryLevelHelper.batteryLevelIcon(
                        getBatteryLevel(requireContext()),
                        status == BatteryManager.BATTERY_STATUS_CHARGING
                    ).let {
                        ContextCompat.getDrawable(requireContext(), it)
                    }

                    binding.batteryLevel.text = getString(
                        R.string.battery_level, "${getBatteryLevel(requireContext())}%"
                    )
                    if ((BatteryLabService.instance?.seconds ?: 0) > 1) {

                        binding.chargingTime.visibility = View.VISIBLE

                        binding.chargingTime.text = getChargingTime(
                            requireContext(), BatteryLabService.instance?.seconds ?: 0
                        )
                    } else if (binding.chargingTime.visibility == View.VISIBLE) binding.chargingTime.visibility =
                        View.GONE

                    binding.lastChargeTime.text = getString(
                        R.string.last_charge_time,
                        getLastChargeTime(requireContext()),
                        "${pref.getInt(PreferencesKeys.BATTERY_LEVEL_WITH, 0)}%",
                        "${pref.getInt(PreferencesKeys.BATTERY_LEVEL_TO, 0)}%"
                    )

                    if (sourceOfPower == BatteryManager.BATTERY_PLUGGED_AC && status == BatteryManager.BATTERY_STATUS_CHARGING) {

                        if (binding.chargingTimeRemaining.visibility == View.GONE) binding.chargingTimeRemaining.visibility =
                            View.VISIBLE

                        if (binding.remainingBatteryTime.visibility == View.VISIBLE) binding.remainingBatteryTime.visibility =
                            View.GONE

                        binding.chargingTimeRemaining.text = getString(
                            R.string.charging_time_remaining,
                            getChargingTimeRemaining(requireContext())
                        )
                    } else {

                        if (binding.chargingTimeRemaining.visibility == View.VISIBLE) binding.chargingTimeRemaining.visibility =
                            View.GONE

                        if (getCurrentCapacity(requireContext()) > 0.0) {

                            if (binding.remainingBatteryTime.visibility == View.GONE) binding.remainingBatteryTime.visibility =
                                View.VISIBLE

                            binding.remainingBatteryTime.text = getString(
                                R.string.remaining_battery_time, getRemainingBatteryTime(
                                    requireContext()
                                )
                            )
                        }
                    }




                    binding.numberOfCharges.text = getString(
                        R.string.number_of_charges,
                        pref.getLong(PreferencesKeys.NUMBER_OF_CHARGES, 0)
                    )

                    binding.numberOfFullCharges.text = getString(
                        R.string.number_of_full_charges,
                        pref.getLong(PreferencesKeys.NUMBER_OF_FULL_CHARGES, 0)
                    )

                    binding.numberOfCycles.text = getString(
                        R.string.number_of_cycles, DecimalFormat("#.##").format(
                            pref.getFloat(
                                PreferencesKeys.NUMBER_OF_CYCLES, 0f
                            )
                        )
                    )

                    binding.numberOfCyclesAndroid.apply {

                        if (visibility == View.VISIBLE) text = getString(
                            R.string.number_of_cycles_android, getNumberOfCyclesAndroid()
                        )
                    }

                    binding.residualCapacity.text = getResidualCapacity(requireContext())

                    binding.batteryWear.text = getBatteryWear(requireContext())
                }

                withContext(Dispatchers.Main) {

                    this@ChargeDischargeFragment.binding.status.text =
                        getStatus(requireContext(), status)

                    if (getSourceOfPower(requireContext(), sourceOfPower) != "N/A") {

                        this@ChargeDischargeFragment.binding.sourceOfPower.text =
                            getSourceOfPower(requireContext(), sourceOfPower)
                    } else this@ChargeDischargeFragment.binding.sourceOfPower.text =
                        getString(R.string.not_plugged)
                }

                withContext(Dispatchers.Main) {

                    binding.temperature.text = getString(
                        R.string.temperature,
                        DecimalFormat("#.#").format(
                            getTemperatureInCelsius(
                                requireContext()
                            )
                        ),
                        DecimalFormat("#.#").format(getTemperatureInFahrenheit(requireContext()))
                    )

                    binding.maximumTemperature.text = getString(
                        R.string.maximum_temperature, DecimalFormat("#.#").format(
                            BatteryInfoInterface.maximumTemperature
                        ), DecimalFormat("#.#").format(
                            getTemperatureInFahrenheit(
                                BatteryInfoInterface.maximumTemperature
                            )
                        )
                    )

                    binding.averageTemperature.text = getString(
                        R.string.average_temperature, DecimalFormat("#.#").format(
                            BatteryInfoInterface.averageTemperature
                        ), DecimalFormat("#.#").format(
                            getTemperatureInFahrenheit(
                                BatteryInfoInterface.averageTemperature
                            )
                        )
                    )

                    binding.minimumTemperature.text = getString(
                        R.string.minimum_temperature, DecimalFormat("#.#").format(
                            BatteryInfoInterface.minimumTemperature
                        ), DecimalFormat("#.#").format(
                            getTemperatureInFahrenheit(
                                BatteryInfoInterface.minimumTemperature
                            )
                        )
                    )

                    binding.voltage.text = getString(
                        R.string.voltage,
                        DecimalFormat("#.#").format(getVoltage(requireContext()))
                    )
                }

                withContext(Dispatchers.Main) {

                    binding.batteryTechnology.text = getString(
                        R.string.battery_technology, batteryIntent?.getStringExtra(
                            BatteryManager.EXTRA_TECHNOLOGY
                        ) ?: getString(R.string.unknown)
                    )

                    binding.batteryHealth.apply {
                        visibility = View.VISIBLE
                        text = getString(
                            R.string.battery_health, getString(
                                getBatteryHealth(requireContext())
                                    ?: R.string.battery_health_great
                            )
                        )
                    }

                    binding.batteryHealthAndroid.text = getString(
                        R.string.battery_health_android, getBatteryHealthAndroid(requireContext())
                    )
                }


                if (getCurrentCapacity(requireContext()) > 0.0) {

                    if (binding.currentCapacityChargeDischarge.visibility == View.GONE) withContext(
                        Dispatchers.Main
                    ) {
                        binding.currentCapacityChargeDischarge.visibility = View.VISIBLE
                    }

                    withContext(Dispatchers.Main) {

                        val isCapacityInWh = pref.getBoolean(
                            CAPACITY_IN_WH, resources.getBoolean(R.bool.capacity_in_wh)
                        )

                        binding.currentCapacityChargeDischarge.text = getString(
                            if (isCapacityInWh) R.string.current_capacity_wh
                            else R.string.current_capacity, DecimalFormat("#.#").format(
                                if (isCapacityInWh) getCapacityInWh(
                                    getCurrentCapacity(requireContext())
                                )
                                else getCurrentCapacity(requireContext())
                            )
                        )

                        when {
                            getSourceOfPower(requireContext(), sourceOfPower) != "N/A" -> {

                                if (binding.capacityAddedChargeDischarge.visibility == View.GONE) binding.capacityAddedChargeDischarge.visibility =
                                    View.VISIBLE

                                binding.capacityAddedChargeDischarge.text =
                                    getCapacityAdded(requireContext())
                            }

                            getSourceOfPower(requireContext(), sourceOfPower) == "N/A" -> {

                                if (binding.capacityAddedChargeDischarge.visibility == View.GONE) binding.capacityAddedChargeDischarge.visibility =
                                    View.VISIBLE

                                binding.capacityAddedChargeDischarge.text =
                                    getCapacityAdded(requireContext())
                            }
                        }
                    }
                } else {

                    if (binding.currentCapacityChargeDischarge.visibility == View.VISIBLE) withContext(
                        Dispatchers.Main
                    ) {
                        binding.currentCapacityChargeDischarge.visibility = View.GONE
                    }

                    if (binding.capacityAddedChargeDischarge.visibility == View.GONE && pref.getFloat(
                            PreferencesKeys.CAPACITY_ADDED, 0f
                        ) > 0f
                    ) withContext(Dispatchers.Main) {
                        binding.capacityAddedChargeDischarge.visibility = View.VISIBLE
                    }
                    else withContext(Dispatchers.Main) {
                        binding.capacityAddedChargeDischarge.visibility = View.GONE
                    }
                }

                when (status) {

                    BatteryManager.BATTERY_STATUS_CHARGING -> {

                        if (binding.chargeCurrent.visibility == View.GONE) withContext(Dispatchers.Main) {
                            binding.chargeCurrent.visibility = View.VISIBLE
                        }

                        withContext(Dispatchers.Main) {

                            binding.chargeCurrent.text =
                                if (isChargingDischargeCurrentInWatt) getString(
                                    R.string.charge_current_watt, DecimalFormat("#.##").format(
                                        getChargeDischargeCurrentInWatt(
                                            getChargeDischargeCurrent(requireContext()), true
                                        )
                                    )
                                )
                                else getString(
                                    R.string.charge_current,
                                    "${getChargeDischargeCurrent(requireContext())}"
                                )
                        }
                    }

                    BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> {

                        if (binding.chargeCurrent.visibility == View.GONE) withContext(Dispatchers.Main) {
                            binding.chargeCurrent.visibility = View.VISIBLE
                        }

                        withContext(Dispatchers.Main) {

                            binding.chargeCurrent.text =
                                if (isChargingDischargeCurrentInWatt) getString(
                                    R.string.discharge_current_watt, DecimalFormat("#.##").format(
                                        getChargeDischargeCurrentInWatt(
                                            getChargeDischargeCurrent(requireContext())
                                        )
                                    )
                                )
                                else getString(
                                    R.string.discharge_current,
                                    "${getChargeDischargeCurrent(requireContext())}"
                                )
                        }
                    }

                    else -> {

                        if (binding.chargeCurrent.visibility == View.VISIBLE) withContext(
                            Dispatchers.Main
                        ) {
                            binding.chargeCurrent.visibility = View.GONE
                        }
                    }
                }

                when (status) {

                    BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> {

                        if (binding.fastCharge.visibility == View.GONE) {
                            withContext(Dispatchers.Main) {
                                binding.fastCharge.visibility = View.VISIBLE
                            }
                        }

                        withContext(Dispatchers.Main) {
                            binding.fastCharge.text = getFastCharge(requireContext())
                        }

                        withContext(Dispatchers.Main) {

                            if (binding.maxChargeDischargeCurrent.visibility == View.GONE) binding.maxChargeDischargeCurrent.visibility =
                                View.VISIBLE

                            if (binding.averageChargeDischargeCurrent.visibility == View.GONE) binding.averageChargeDischargeCurrent.visibility =
                                View.VISIBLE

                            if (binding.minChargeDischargeCurrent.visibility == View.GONE) binding.minChargeDischargeCurrent.visibility =
                                View.VISIBLE

                            binding.maxChargeDischargeCurrent.text =
                                if (isChargingDischargeCurrentInWatt) getString(
                                    R.string.max_charge_current_watt, DecimalFormat("#.##").format(
                                        getChargeDischargeCurrentInWatt(
                                            BatteryInfoInterface.maxChargeCurrent, true
                                        )
                                    )
                                )
                                else getString(
                                    R.string.max_charge_current,
                                    BatteryInfoInterface.maxChargeCurrent
                                )

                            binding.averageChargeDischargeCurrent.text =
                                if (isChargingDischargeCurrentInWatt) getString(
                                    R.string.average_charge_current_watt,
                                    DecimalFormat("#.##").format(
                                        getChargeDischargeCurrentInWatt(
                                            BatteryInfoInterface.averageChargeCurrent, true
                                        )
                                    )
                                )
                                else getString(
                                    R.string.average_charge_current,
                                    BatteryInfoInterface.averageChargeCurrent
                                )

                            binding.minChargeDischargeCurrent.text =
                                if (isChargingDischargeCurrentInWatt) getString(
                                    R.string.min_charge_current_watt, DecimalFormat("#.##").format(
                                        getChargeDischargeCurrentInWatt(
                                            BatteryInfoInterface.minChargeCurrent, true
                                        )
                                    )
                                )
                                else getString(
                                    R.string.min_charge_current,
                                    BatteryInfoInterface.minChargeCurrent
                                )
                        }
                    }

                    BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> withContext(
                        Dispatchers.Main
                    ) {

                        if (binding.fastCharge.visibility == View.VISIBLE) binding.fastCharge.visibility =
                            View.GONE
                        //binding.fastCharge.text = getString(R.string.fast_charge_discharging)

                        if (binding.maxChargeDischargeCurrent.visibility == View.GONE) binding.maxChargeDischargeCurrent.visibility =
                            View.VISIBLE

                        if (binding.averageChargeDischargeCurrent.visibility == View.GONE) binding.averageChargeDischargeCurrent.visibility =
                            View.VISIBLE

                        if (binding.minChargeDischargeCurrent.visibility == View.GONE) binding.minChargeDischargeCurrent.visibility =
                            View.VISIBLE

                        binding.maxChargeDischargeCurrent.text =
                            if (isChargingDischargeCurrentInWatt) getString(
                                R.string.max_discharge_current_watt, DecimalFormat("#.##").format(
                                    getChargeDischargeCurrentInWatt(
                                        BatteryInfoInterface.maxDischargeCurrent
                                    )
                                )
                            )
                            else getString(
                                R.string.max_discharge_current,
                                BatteryInfoInterface.maxDischargeCurrent
                            )

                        binding.averageChargeDischargeCurrent.text =
                            if (isChargingDischargeCurrentInWatt) getString(
                                R.string.average_discharge_current_watt,
                                DecimalFormat("#.##").format(
                                    getChargeDischargeCurrentInWatt(
                                        BatteryInfoInterface.averageDischargeCurrent
                                    )
                                )
                            )
                            else getString(
                                R.string.average_discharge_current,
                                BatteryInfoInterface.averageDischargeCurrent
                            )

                        binding.minChargeDischargeCurrent.text =
                            if (isChargingDischargeCurrentInWatt) getString(
                                R.string.min_discharge_current_watt, DecimalFormat("#.##").format(
                                    getChargeDischargeCurrentInWatt(
                                        BatteryInfoInterface.minDischargeCurrent
                                    )
                                )
                            )
                            else getString(
                                R.string.min_discharge_current,
                                BatteryInfoInterface.minDischargeCurrent
                            )
                    }

                    else -> {

                        withContext(Dispatchers.Main) {

                            if (binding.maxChargeDischargeCurrent.visibility == View.VISIBLE) binding.maxChargeDischargeCurrent.visibility =
                                View.GONE

                            if (binding.averageChargeDischargeCurrent.visibility == View.VISIBLE) binding.averageChargeDischargeCurrent.visibility =
                                View.GONE

                            if (binding.minChargeDischargeCurrent.visibility == View.VISIBLE) binding.minChargeDischargeCurrent.visibility =
                                View.GONE
                        }
                    }
                }

                val chargingCurrentLimit = getChargingCurrentLimit(requireContext())

                withContext(Dispatchers.Main) {

                    if (chargingCurrentLimit != null && chargingCurrentLimit.toInt() > 0) {

                        if (binding.chargingCurrentLimit.visibility == View.GONE) this@ChargeDischargeFragment.binding.chargingCurrentLimit.visibility =
                            View.VISIBLE

                        if (isChargingDischargeCurrentInWatt) binding.chargingCurrentLimit.text =
                            getString(
                                R.string.charging_current_limit_watt, DecimalFormat("#.##").format(
                                    getChargeDischargeCurrentInWatt(
                                        chargingCurrentLimit.toInt(), true
                                    )
                                )
                            )
                        else binding.chargingCurrentLimit.text = getString(
                            R.string.charging_current_limit, chargingCurrentLimit
                        )
                    } else if (this@ChargeDischargeFragment.binding.chargingCurrentLimit.visibility == View.VISIBLE) this@ChargeDischargeFragment.binding.chargingCurrentLimit.visibility =
                        View.GONE

                    binding.screenTime.text = getString(
                        R.string.screen_time, TimeHelper.getTime(
                            BatteryLabService.instance?.screenTime
                                ?: if (MainApp.tempScreenTime > 0) MainApp.tempScreenTime
                                else if (MainApp.isUpdateApp)
                                    pref.getLong(UPDATE_TEMP_SCREEN_TIME, 0L) else 0L
                        )
                    )
                }

                when (status) {

                    BatteryManager.BATTERY_STATUS_CHARGING -> delay(
                        if (getCurrentCapacity(
                                requireContext()
                            ) > 0.0
                        ) 0.972.seconds else 0.979.seconds
                    )

                    else -> delay(1.5.seconds)
                }
            }

        }
    }

    private fun updateTextAppearance() {
        with(binding) {
            val textViewArrayList = arrayListOf(
                batteryLevel,
                chargingTime,
                chargingTimeRemaining,
                remainingBatteryTime,
                screenTime,
                currentCapacityChargeDischarge,
                capacityAddedChargeDischarge,
                status,
                sourceOfPower,
                chargeCurrent,
                maxChargeDischargeCurrent,
                averageChargeDischargeCurrent,
                minChargeDischargeCurrent,
                chargingCurrentLimit,
                temperature,
                maximumTemperature,
                averageTemperature,
                minimumTemperature,
                voltage,
                lastChargeTime,
                fastCharge,
                designCapacity,
                numberOfCharges,
                numberOfFullCharges,
                numberOfCycles,
                numberOfCyclesAndroid,
                batteryTechnology,
                batteryHealthAndroid,
                batteryHealth,
                residualCapacity,
                batteryLevelTitle,
                batteryWear,
                batteryHealthTitle,
                batteryStatusTitle,
                pluggedTypeTitle,
                capacityTitle,
                temperatureTitle,
                powerMonitorTitle
            )

            val subTitleTrueList = setOf(
                fastCharge,
                batteryTechnology,
                batteryHealth,
                residualCapacity,
                batteryWear,
                currentCapacityChargeDischarge,
                capacityAddedChargeDischarge,
                chargeCurrent,
                maxChargeDischargeCurrent,
                averageChargeDischargeCurrent,
                minChargeDischargeCurrent,
                chargingCurrentLimit,
                maximumTemperature,
                averageTemperature,
                minimumTemperature
            )

            textViewArrayList.forEach { textView ->
                val isSubTitle = textView in subTitleTrueList
                TextAppearanceHelper.setTextAppearance(
                    requireContext(),
                    textView,
                    pref.getString(TEXT_STYLE, "0"),
                    pref.getString(TEXT_FONT, "6"),
                    pref.getString(TEXT_SIZE, "2"),
                    subTitle = isSubTitle
                )
            }
        }
    }
}
