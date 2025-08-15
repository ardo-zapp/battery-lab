package com.jacktor.batterylab.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.MainApp
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.R
import com.jacktor.batterylab.adapters.BatteryInfoAdapter
import com.jacktor.batterylab.databinding.BatteryInfoFragmentBinding
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import com.jacktor.batterylab.helpers.TextAppearanceHelper
import com.jacktor.batterylab.helpers.TimeHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.averageChargeCurrent
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.averageDischargeCurrent
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.averageTemperature
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.getPeakMetrics
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.maxChargeCurrent
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.maxDischargeCurrent
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.maximumTemperature
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.minChargeCurrent
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.minDischargeCurrent
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.minimumTemperature
import com.jacktor.batterylab.interfaces.SettingsInterface
import com.jacktor.batterylab.models.BatteryInfoRow
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.utilities.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import kotlin.time.Duration.Companion.seconds

class BatteryInfoFragment : Fragment(R.layout.battery_info_fragment), SettingsInterface,
    BatteryInfoInterface {

    private lateinit var binding: BatteryInfoFragmentBinding
    private lateinit var pref: SharedPreferences

    private var mainContext: MainActivity? = null
    private var job: Job? = null
    private var isJob = false
    private var isChargingDischargeCurrentInWatt = false

    private lateinit var adapter: BatteryInfoAdapter

    private var lastRows: List<BatteryInfoRow> = emptyList()

    var pluggedTypeState = BatteryManager.BATTERY_PLUGGED_USB

    private var cacheWasPlugged: Boolean? = null
    private var cacheLastPluggedType: Int? = null

    private object Keys {
        const val TEXT_STYLE = "text_style"
        const val TEXT_FONT = "text_font"
        const val TEXT_SIZE = "text_size"
        const val CHARGING_DISCHARGE_CURRENT_IN_WATT = "charging_discharge_current_in_watt"
        const val UPDATE_TEMP_SCREEN_TIME = "update_temp_screen_time"
        const val BATTERY_LEVEL_WITH = "battery_level_with"
        const val BATTERY_LEVEL_TO = "battery_level_to"
        const val NUMBER_OF_CHARGES = "number_of_charges"
        const val NUMBER_OF_FULL_CHARGES = "number_of_full_charges"
        const val NUMBER_OF_CYCLES = "number_of_cycles"
        const val DESIGN_CAPACITY = "design_capacity"
        const val CAPACITY_IN_WH = "capacity_in_wh"
        const val LAST_PLUGGED_TYPE = "last_plugged_type"
        const val WAS_PLUGGED = "was_plugged"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BatteryInfoFragmentBinding.inflate(inflater, container, false)
        pref = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        pluggedTypeState = pref.getInt(Keys.LAST_PLUGGED_TYPE, BatteryManager.BATTERY_PLUGGED_AC)
        cacheWasPlugged = pref.getBoolean(Keys.WAS_PLUGGED, false)
        cacheLastPluggedType = pref.getInt(Keys.LAST_PLUGGED_TYPE, pluggedTypeState)

        setupRecycler()
        return binding.root
    }

    private fun setupRecycler() {
        adapter = BatteryInfoAdapter(
            textStyle = {
                Triple(
                    pref.getString(Keys.TEXT_STYLE, "0") ?: "0",
                    pref.getString(Keys.TEXT_FONT, "6") ?: "6",
                    pref.getString(Keys.TEXT_SIZE, "2") ?: "2"
                )
            },
            applySubtitle = { tv ->
                TextAppearanceHelper.setTextAppearance(
                    requireContext(),
                    tv as AppCompatTextView,
                    pref.getString(Keys.TEXT_STYLE, "0") ?: "0",
                    pref.getString(Keys.TEXT_FONT, "6") ?: "6",
                    pref.getString(Keys.TEXT_SIZE, "2") ?: "2",
                    subTitle = true
                )
            }
        )
        val glm = GridLayoutManager(requireContext(), 2)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (adapter.getItemViewType(position)) {
                    0, 1 -> 2
                    else -> 1
                }
            }
        }
        binding.recyclerView.layoutManager = glm
        binding.recyclerView.adapter = adapter

        (binding.recyclerView.itemAnimator as? SimpleItemAnimator)?.apply {
            supportsChangeAnimations = false
        }
        binding.recyclerView.itemAnimator?.apply {
            changeDuration = 0
            moveDuration = 0
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainContext = activity as? MainActivity
    }

    override fun onResume() {
        super.onResume()
        batteryIntent =
            requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        isJob = true
        isChargingDischargeCurrentInWatt = pref.getBoolean(
            Keys.CHARGING_DISCHARGE_CURRENT_IN_WATT,
            resources.getBoolean(R.bool.charging_discharge_current_in_watt)
        )
        startJob()
    }

    override fun onStop() {
        super.onStop()
        isJob = false
        job?.cancel(); job = null
    }

    override fun onDestroy() {
        isJob = false
        job?.cancel(); job = null
        super.onDestroy()
    }

    private fun startJob() {
        if (job == null) job = CoroutineScope(Dispatchers.Default).launch {
            while (isJob) {
                val status = batteryIntent?.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN
                ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

                val sourceOfPower =
                    batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

                withContext(Dispatchers.Main) {
                    updateToolbar(status)
                    val rows = buildRows(status, sourceOfPower)
                    if (rows != lastRows) {
                        adapter.submit(rows)
                        lastRows = rows
                    }
                }

                when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> delay(0.975.seconds)
                    else -> delay(1.5.seconds)
                }
            }
        }
    }

    private fun updateToolbar(status: Int) {
        mainContext?.toolbar?.title = getString(R.string.battery_info)
        mainContext?.navigation?.menu?.findItem(R.id.charge_discharge_navigation)?.apply {
            title = getString(R.string.battery_info)
            icon = ContextCompat.getDrawable(
                requireContext(),
                BatteryLevelHelper.batteryLevelIcon(
                    getBatteryLevel(requireContext()),
                    status == BatteryManager.BATTERY_STATUS_CHARGING
                )
            )
        }
    }

    @SuppressLint("InlinedApi")
    private fun buildRows(status: Int, sourceOfPower: Int): List<BatteryInfoRow> {
        val rows = mutableListOf<BatteryInfoRow>()

        // ===== HEADER: CHARGING TIME, BATTERY TIME, SCREEN TIME, LAST CHARGE =====
        val seconds = BatteryLabService.instance?.seconds ?: 0
        val chargingTime = if (seconds > 1) getChargingTime(requireContext(), seconds) else null
        val chargingTimeRemaining =
            if (sourceOfPower == BatteryManager.BATTERY_PLUGGED_AC && status == BatteryManager.BATTERY_STATUS_CHARGING)
                getString(
                    R.string.charging_time_remaining,
                    getChargingTimeRemaining(requireContext())
                )
            else null
        val remainingBatteryTime =
            if (sourceOfPower != BatteryManager.BATTERY_PLUGGED_AC && getCurrentCapacity(
                    requireContext()
                ) > 0.0
            )
                getString(
                    R.string.remaining_battery_time,
                    getRemainingBatteryTime(requireContext())
                )
            else null

        val screenTimeValue = BatteryLabService.instance?.screenTime
            ?: MainApp.tempScreenTime.takeIf { it > 0 }
            ?: (if (MainApp.isUpdateApp) pref.getLong(Keys.UPDATE_TEMP_SCREEN_TIME, 0L) else 0L)
        val screenTime = getString(R.string.screen_time, TimeHelper.getTime(screenTimeValue))

        val lastChargeTime = getString(
            R.string.last_charge_time,
            getLastChargeTime(requireContext()),
            "${pref.getInt(Keys.BATTERY_LEVEL_WITH, 0)}%",
            "${pref.getInt(Keys.BATTERY_LEVEL_TO, 0)}%"
        )
        rows += BatteryInfoRow.Header(
            chargingTime,
            chargingTimeRemaining,
            remainingBatteryTime,
            screenTime,
            lastChargeTime
        )

        // ===== BATTERY CYCLE COUNTS =====
        rows += BatteryInfoRow.Counts(
            charges = getString(
                R.string.number_of_charges,
                pref.getLong(Keys.NUMBER_OF_CHARGES, 0)
            ),
            fullCharges = getString(
                R.string.number_of_full_charges,
                pref.getLong(Keys.NUMBER_OF_FULL_CHARGES, 0)
            ),
            cycles = getString(
                R.string.number_of_cycles,
                DecimalFormat("#.##").format(pref.getFloat(Keys.NUMBER_OF_CYCLES, 0f))
            ),
            cyclesAndroid = getNumberOfCyclesAndroid().toString(),
            showCyclesAndroid = getNumberOfCyclesAndroid() != null
        )

        // ===== BATTERY LEVEL =====
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val level = getBatteryLevel(requireContext())
        val lvlIcon = BatteryLevelHelper.batteryLevelIcon(level, isCharging)
        val levelColor = when {
            isCharging -> R.color.battery_info_level_charging
            level in 0..20 -> R.color.battery_info_level_low
            else -> R.color.battery_info_level_normal
        }
        rows += BatteryInfoRow.Stat(
            icon = lvlIcon,
            iconTint = levelColor,
            title = getString(R.string.battery_level_title),
            primary = getString(R.string.battery_level, "$level%")
        )

        // ===== BATTERY STATUS =====
        rows += BatteryInfoRow.Stat(
            icon = R.drawable.ic_battery_status_24,
            iconTint = R.color.battery_info_power_source_connected,
            title = getString(R.string.battery_status_title),
            primary = getStatus(requireContext(), status)
        )

        // ===== POWER SOURCE =====
        run {
            val ctx = requireContext()
            val plugged = isPlugged(sourceOfPower)

            val primary = if (plugged) {
                getSourceOfPower(ctx, sourceOfPower, isOverlay = false, isOnlyValues = true)
            } else {
                getString(R.string.not_plugged)
            }

            val secondary: List<String> = buildList {
                if (plugged && isChargingStatus(status)) {
                    getFastCharge(ctx)
                        .takeIf { it.isNotBlank() && it != "N/A" }
                        ?.let { add(it) }

                    val (peakW, peakmA, peakV) = getPeakMetrics()
                    if (peakW > 0.0) {
                        val nf1 = java.text.NumberFormat.getNumberInstance()
                            .apply { maximumFractionDigits = 1 }
                        val nf2 = java.text.NumberFormat.getNumberInstance()
                            .apply { maximumFractionDigits = 2 }
                        add(
                            getString(
                                R.string.peak_charge_power_verbose,
                                nf1.format(peakW),
                                peakmA,
                                nf2.format(peakV)
                            )
                        )
                    }
                }
            }

            val newPluggedType = when (sourceOfPower) {
                BatteryManager.BATTERY_PLUGGED_AC -> BatteryManager.BATTERY_PLUGGED_AC
                BatteryManager.BATTERY_PLUGGED_USB -> BatteryManager.BATTERY_PLUGGED_USB
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> BatteryManager.BATTERY_PLUGGED_WIRELESS
                BatteryManager.BATTERY_PLUGGED_DOCK -> BatteryManager.BATTERY_PLUGGED_DOCK
                else -> sourceOfPower
            }

            val wasPlugged = cacheWasPlugged ?: false
            val lastType = cacheLastPluggedType ?: pluggedTypeState

            if (plugged) {
                if (!wasPlugged || lastType != newPluggedType) {
                    pref.edit {
                        putBoolean(
                            Keys.WAS_PLUGGED,
                            true
                        )
                        putInt(Keys.LAST_PLUGGED_TYPE, newPluggedType)
                    }
                    cacheWasPlugged = true
                    cacheLastPluggedType = newPluggedType
                }
                pluggedTypeState = newPluggedType
            } else {
                if (wasPlugged) {
                    pref.edit {
                        putBoolean(Keys.WAS_PLUGGED, false)
                    }
                    cacheWasPlugged = false
                }
            }

            val iconRes: Int = if (plugged) {
                when (newPluggedType) {
                    BatteryManager.BATTERY_PLUGGED_AC -> R.drawable.ic_ac_plugged_24
                    BatteryManager.BATTERY_PLUGGED_USB -> R.drawable.ic_usb_plugged_24
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.drawable.ic_wireless_plugged_24
                    BatteryManager.BATTERY_PLUGGED_DOCK -> R.drawable.ic_dock_plugged_24
                    else -> R.drawable.ic_ac_plugged_24
                }
            } else {
                when (cacheLastPluggedType ?: pluggedTypeState) {
                    BatteryManager.BATTERY_PLUGGED_AC -> R.drawable.ic_ac_unplugged_24
                    BatteryManager.BATTERY_PLUGGED_USB -> R.drawable.ic_usb_unplugged_24
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.drawable.ic_wireless_unplugged_24
                    BatteryManager.BATTERY_PLUGGED_DOCK -> R.drawable.ic_dock_unplugged_24
                    else -> R.drawable.ic_ac_unplugged_24
                }
            }

            val tintRes = if (plugged) {
                R.color.battery_info_power_source_connected
            } else {
                R.color.battery_info_power_source_unplugged
            }

            rows += BatteryInfoRow.Stat(
                icon = iconRes,
                iconTint = tintRes,
                title = getString(R.string.plugged_type_title),
                primary = primary,
                secondary = secondary
            )
        }

        // ===== HEALTH =====
        rows += BatteryInfoRow.Stat(
            icon = R.drawable.ic_health_24,
            iconTint = R.color.battery_info_health,
            title = getString(R.string.health_title),
            primary = getString(
                R.string.battery_health_android,
                getBatteryHealthAndroid(requireContext())
            ),
            secondary = listOf(
                getString(
                    R.string.battery_health,
                    getString(getBatteryHealth(requireContext()) ?: R.string.battery_health_great)
                )
            )
        )

        // ===== CAPACITY =====
        val designCapacityMah =
            pref.getInt(Keys.DESIGN_CAPACITY, resources.getInteger(R.integer.min_design_capacity))
        val designCapacityWh =
            (designCapacityMah.toDouble() * Constants.NOMINAL_BATTERY_VOLTAGE) / 1000.0
        val isCapacityInWh =
            pref.getBoolean(Keys.CAPACITY_IN_WH, resources.getBoolean(R.bool.capacity_in_wh))
        val designText = if (isCapacityInWh)
            getString(R.string.design_capacity_wh, DecimalFormat("#.#").format(designCapacityWh))
        else
            getString(R.string.design_capacity, "$designCapacityMah")

        val capSecondary = mutableListOf<String>()
        val currentCap = getCurrentCapacity(requireContext())
        if (currentCap > 0.0) {
            val currentText = if (isCapacityInWh)
                getString(
                    R.string.current_capacity_wh,
                    DecimalFormat("#.#").format(getCapacityInWh(currentCap))
                )
            else
                getString(R.string.current_capacity, DecimalFormat("#.#").format(currentCap))
            capSecondary += currentText
            capSecondary += getCapacityAdded(requireContext())
        }
        capSecondary += getResidualCapacity(requireContext())
        capSecondary += getBatteryWear(requireContext())
        capSecondary += getString(
            R.string.battery_technology,
            batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
                ?: getString(R.string.unknown)
        )
        rows += BatteryInfoRow.Stat(
            icon = R.drawable.ic_battery_capacity_24,
            iconTint = R.color.battery_info_capacity,
            title = getString(R.string.capacity_title),
            primary = designText,
            secondary = capSecondary
        )

        // ===== TEMPERATURE =====
        val tempC = DecimalFormat("#.#").format(getTemperatureInCelsius(requireContext()))
        val tempF = DecimalFormat("#.#").format(getTemperatureInFahrenheit(requireContext()))
        val t = getTemperature(requireContext()) ?: 0
        val tempColor = when {
            t >= 500 -> R.color.battery_info_temp_very_hot
            t >= 400 -> R.color.battery_info_temp_hot
            t >= 300 -> R.color.battery_info_temp_warm
            t >= 200 -> R.color.battery_info_temp_normal
            else -> R.color.battery_info_temp_cold
        }
        rows += BatteryInfoRow.Stat(
            icon = R.drawable.ic_temperature_24,
            iconTint = tempColor,
            title = getString(R.string.temperature_title),
            primary = "$tempC°C / $tempF°F",
            secondary = listOf(
                getString(
                    R.string.maximum_temperature,
                    DecimalFormat("#.#").format(maximumTemperature),
                    DecimalFormat("#.#").format(getTemperatureInFahrenheit(maximumTemperature))
                ),
                getString(
                    R.string.minimum_temperature,
                    DecimalFormat("#.#").format(minimumTemperature),
                    DecimalFormat("#.#").format(getTemperatureInFahrenheit(minimumTemperature))
                ),
                getString(
                    R.string.average_temperature,
                    DecimalFormat("#.#").format(averageTemperature),
                    DecimalFormat("#.#").format(getTemperatureInFahrenheit(averageTemperature))
                )
            )
        )

        // ===== THERMAL & POWER SAVE =====
        run {
            val thermal = getThermalStatusLabel(requireContext())
            val saverOn = isPowerSaveEnabled(requireContext())
            rows += BatteryInfoRow.Stat(
                icon = R.drawable.ic_battery_info_24,
                iconTint = getThermalTintRes(requireContext()),
                title = getString(R.string.thermal_title),
                primary = getString(R.string.thermal_label, thermal),
                secondary = listOf(
                    getString(
                        R.string.powersave_label,
                        getString(if (saverOn) R.string.on else R.string.off)
                    )
                )
            )
        }

        // ===== POWER MONITOR =====
        val curr = getChargeDischargeCurrent(requireContext())
        val chargeDischargePrimary = if (isChargingDischargeCurrentInWatt) {
            if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                val value =
                    DecimalFormat("#.##").format(getChargeDischargeCurrentInWatt(curr, true))
                "$value W - ${getString(R.string.charge_current_label)}"
            } else {
                val value = DecimalFormat("#.##").format(getChargeDischargeCurrentInWatt(curr))
                "$value W - ${getString(R.string.discharge_current_label)}"
            }
        } else {
            if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                "$curr mA - ${getString(R.string.charge_current_label)}"
            } else {
                "$curr mA - ${getString(R.string.discharge_current_label)}"
            }
        }

        val chargingCurrentLimit = getChargingCurrentLimit(requireContext())?.toIntOrNull()
        val currentLimitText = chargingCurrentLimit?.let {
            if (isChargingDischargeCurrentInWatt)
                getString(
                    R.string.charging_current_limit_watt,
                    DecimalFormat("#.##").format(getChargeDischargeCurrentInWatt(it, true))
                )
            else
                getString(R.string.charging_current_limit, it.toString())
        }

        val currentStats = mutableListOf<String>().apply {
            add(
                getString(
                    R.string.voltage,
                    DecimalFormat("#.#").format(getVoltage(requireContext()))
                )
            )
            currentLimitText?.let { add(it) }
            if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
                add(
                    if (isChargingDischargeCurrentInWatt)
                        getString(
                            R.string.max_charge_current_watt,
                            DecimalFormat("#.##").format(
                                getChargeDischargeCurrentInWatt(
                                    maxChargeCurrent,
                                    true
                                )
                            )
                        )
                    else
                        getString(R.string.max_charge_current, maxChargeCurrent)
                )
                add(
                    if (isChargingDischargeCurrentInWatt)
                        getString(
                            R.string.average_charge_current_watt,
                            DecimalFormat("#.##").format(
                                getChargeDischargeCurrentInWatt(
                                    averageChargeCurrent,
                                    true
                                )
                            )
                        )
                    else
                        getString(R.string.average_charge_current, averageChargeCurrent)
                )
                add(
                    if (isChargingDischargeCurrentInWatt)
                        getString(
                            R.string.min_charge_current_watt,
                            DecimalFormat("#.##").format(
                                getChargeDischargeCurrentInWatt(
                                    minChargeCurrent,
                                    true
                                )
                            )
                        )
                    else
                        getString(R.string.min_charge_current, minChargeCurrent)
                )
            } else {
                add(
                    if (isChargingDischargeCurrentInWatt)
                        getString(
                            R.string.max_discharge_current_watt,
                            DecimalFormat("#.##").format(
                                getChargeDischargeCurrentInWatt(maxDischargeCurrent)
                            )
                        )
                    else
                        getString(R.string.max_discharge_current, maxDischargeCurrent)
                )
                add(
                    if (isChargingDischargeCurrentInWatt)
                        getString(
                            R.string.average_discharge_current_watt,
                            DecimalFormat("#.##").format(
                                getChargeDischargeCurrentInWatt(averageDischargeCurrent)
                            )
                        )
                    else
                        getString(R.string.average_discharge_current, averageDischargeCurrent)
                )
                add(
                    if (isChargingDischargeCurrentInWatt)
                        getString(
                            R.string.min_discharge_current_watt,
                            DecimalFormat("#.##").format(
                                getChargeDischargeCurrentInWatt(minDischargeCurrent)
                            )
                        )
                    else
                        getString(R.string.min_discharge_current, minDischargeCurrent)
                )
            }
        }
        rows += BatteryInfoRow.Stat(
            icon = R.drawable.ic_voltage_24,
            iconTint = R.color.battery_info_power_monitor,
            title = getString(R.string.power_monitor_title),
            primary = chargeDischargePrimary,
            secondary = currentStats
        )

        return rows
    }
}
