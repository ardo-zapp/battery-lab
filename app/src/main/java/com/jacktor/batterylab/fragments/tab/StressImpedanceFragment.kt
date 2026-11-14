package com.jacktor.batterylab.fragments.tab

import android.animation.AnimatorSet
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databinding.StressImpedanceFragmentBinding
import com.jacktor.batterylab.helpers.MotionHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.security.NativeCore
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DESIGN_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.STRESS_CALIBRATED_RINT_MOHM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import kotlin.math.roundToInt
import kotlin.math.sin

class StressImpedanceFragment : Fragment(R.layout.stress_impedance_fragment), BatteryInfoInterface {

    private var _binding: StressImpedanceFragmentBinding? = null
    private val binding get() = _binding!!

    private var realtimeJob: Job? = null
    private var pulseTestJob: Job? = null
    private var pulseIconAnimator: AnimatorSet? = null
    private var hasAnimatedEntrance = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = StressImpedanceFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        updateStressMetrics(forceSample = true)
        if (!hasAnimatedEntrance) {
            hasAnimatedEntrance = true
            val contentColumn = (binding.root as? ViewGroup)?.getChildAt(0) as? ViewGroup
            if (contentColumn != null) {
                MotionHelper.animateStaggeredChildren(contentColumn)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStressMetrics(forceSample = false)
        startRealtimeMonitoring()
    }

    override fun onPause() {
        super.onPause()
        stopRealtimeMonitoring()
        cancelPulseTest()
    }

    private fun startRealtimeMonitoring() {
        stopRealtimeMonitoring()
        realtimeJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(1500L)
                updateStressMetrics(forceSample = false)
            }
        }
    }

    private fun stopRealtimeMonitoring() {
        realtimeJob?.cancel()
        realtimeJob = null
    }

    private fun cancelPulseTest() {
        pulseTestJob?.cancel()
        pulseTestJob = null
        if (_binding != null) {
            MotionHelper.stopAnimator(pulseIconAnimator, binding.imgStressIcon)
            pulseIconAnimator = null
            binding.btnPulseTest.isEnabled = true
            binding.btnPulseTest.setText(R.string.stress_btn_pulse)
        } else {
            pulseIconAnimator?.cancel()
            pulseIconAnimator = null
        }
    }

    private fun setupButtons() {
        MotionHelper.bindPressScale(binding.btnPulseTest, binding.btnResetImpedance)

        binding.btnPulseTest.setOnClickListener {
            runActivePulseTest()
        }
        binding.btnResetImpedance.setOnClickListener {
            cancelPulseTest()
            NativeCore.nativeResetStressState()
            context?.let { ctx ->
                PreferenceManager.getDefaultSharedPreferences(ctx).edit {
                    remove(STRESS_CALIBRATED_RINT_MOHM)
                }
            }
            updateStressMetrics(forceSample = true)
        }
    }

    private fun runActivePulseTest() {
        val ctx = context ?: return
        if (pulseTestJob?.isActive == true) return

        val bi = try {
            ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            batteryIntent
        }
        val status = bi?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.stress_pulse_result_title)
                .setMessage(R.string.stress_pulse_unplug_required)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        binding.btnPulseTest.isEnabled = false
        binding.btnPulseTest.setText(R.string.stress_btn_pulse_running)
        MotionHelper.stopAnimator(pulseIconAnimator, binding.imgStressIcon)
        pulseIconAnimator = MotionHelper.startBreathingPulse(
            view = binding.imgStressIcon,
            minScale = 1.0f,
            maxScale = 1.22f,
            minAlpha = 0.65f,
            durationMs = 520L
        )

        pulseTestJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val baseSamples = mutableListOf<Pair<Double, Double>>()
                repeat(3) {
                    val v = getVoltage(ctx)
                    val i = readRawInstantCurrentMa(ctx)
                    if (v > 0.0) baseSamples += v to i
                    delay(150L)
                }

                val loadedSamples = mutableListOf<Pair<Double, Double>>()
                withContext(Dispatchers.Default) {
                    val endNs = System.nanoTime() + 2_000_000_000L
                    val workerCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
                    val workers = List(workerCount) { idx ->
                        async {
                            var acc = 1.0001 + idx * 0.01
                            while (isActive && System.nanoTime() < endNs) {
                                for (k in 0..4000) {
                                    acc = sin(acc) * 1.0001 + 1.00001
                                }
                            }
                            acc
                        }
                    }

                    delay(1550L)
                    repeat(3) {
                        val v = getVoltage(ctx)
                        val i = readRawInstantCurrentMa(ctx)
                        if (v > 0.0) loadedSamples += v to i
                        delay(150L)
                    }
                    workers.awaitAll()
                }

                if (baseSamples.isEmpty() || loadedSamples.isEmpty()) {
                    if (isAdded) {
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle(R.string.stress_pulse_result_title)
                            .setMessage(getString(R.string.stress_pulse_result_low_delta, 0, 0))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                    return@launch
                }

                val baseVoltageMv = baseSamples.map { it.first }.average()
                val baseCurrentMa = baseSamples.map { it.second }.average()

                val loadVoltageMv = loadedSamples.map { it.first }.average()
                val loadCurrentMa = loadedSamples.map { it.second }.average()

                val pulse = NativeCore.evaluatePulseImpedance(
                    context = ctx,
                    baseVoltageMv = baseVoltageMv,
                    baseCurrentMa = baseCurrentMa,
                    loadVoltageMv = loadVoltageMv,
                    loadCurrentMa = loadCurrentMa
                )

                if (pulse.success) {
                    val measuredMohm = pulse.measuredMohm
                    PreferenceManager.getDefaultSharedPreferences(ctx).edit {
                        putFloat(STRESS_CALIBRATED_RINT_MOHM, measuredMohm.toFloat())
                    }
                    updateStressMetrics(forceSample = true)

                    if (isAdded) {
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle(R.string.stress_pulse_result_title)
                            .setMessage(
                                getString(
                                    R.string.stress_pulse_result_success,
                                    pulse.deltaIMa,
                                    pulse.deltaVCellMv,
                                    measuredMohm.roundToInt()
                                )
                            )
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                } else if (isAdded) {
                    MaterialAlertDialogBuilder(ctx)
                        .setTitle(R.string.stress_pulse_result_title)
                        .setMessage(
                            getString(
                                R.string.stress_pulse_result_low_delta,
                                pulse.deltaIMa,
                                pulse.deltaVCellMv
                            )
                        )
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            } finally {
                if (_binding != null) {
                    MotionHelper.stopAnimator(pulseIconAnimator, binding.imgStressIcon)
                    pulseIconAnimator = null
                    binding.btnPulseTest.isEnabled = true
                    binding.btnPulseTest.setText(R.string.stress_btn_pulse)
                } else {
                    pulseIconAnimator?.cancel()
                    pulseIconAnimator = null
                }
                pulseTestJob = null
            }
        }
    }

    private fun updateStressMetrics(forceSample: Boolean) {
        val ctx = context ?: return
        if (_binding == null) return

        val pref = PreferenceManager.getDefaultSharedPreferences(ctx)
        val savedRint = pref.getFloat(STRESS_CALIBRATED_RINT_MOHM, -1f)
        batteryIntent = try {
            ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            batteryIntent
        }

        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING

        val level = (getBatteryLevel(ctx) ?: 50).coerceIn(0, 100)
        val rawVoltageMv = getVoltage(ctx).coerceAtLeast(3000.0)
        val tempC = getTemperatureInCelsius(ctx)
        val currentAbsMa = getChargeDischargeCurrent(ctx).coerceAtLeast(0)

        val designCapMah = pref.getInt(
            DESIGN_CAPACITY,
            ctx.resources.getInteger(R.integer.min_design_capacity)
        ).coerceAtLeast(1500)

        val cycles = pref.getFloat(NUMBER_OF_CYCLES, 0f)
        val residualMah = getResidualCapacityMah(ctx).let {
            if (it > 0.0) it else designCapMah.toDouble()
        }

        val eval = NativeCore.evaluateStressMetrics(
            context = ctx,
            isCharging = isCharging,
            level = level,
            rawVoltageMv = rawVoltageMv,
            tempC = tempC,
            currentAbsMa = currentAbsMa,
            designCapMah = designCapMah,
            cycles = cycles,
            residualMah = residualMah,
            savedRintMohm = savedRint,
            forceSample = forceSample
        )

        val cellMultiplier = eval.cellMultiplier
        val cellVoltageMv = rawVoltageMv / cellMultiplier.toDouble()
        val cRate = eval.cRate
        val realPowerWatt = eval.realPowerWatt
        val rIntCellMohm = eval.rIntCellMohm
        val voltageSagMv = eval.voltageSagMv
        val jouleHeatMw = eval.jouleHeatMw
        val wearMultiplier = eval.wearMultiplier
        val thermalStress = eval.thermalStress
        val voltageStress = eval.voltageStress
        val currentStress = eval.currentStress
        val compositeStress = eval.compositeStress

        val df2 = DecimalFormat("#.##")
        val df1 = DecimalFormat("#.#")

        val cellTypeLabel = if (cellMultiplier == 2) "Dual-Cell" else "Single-Cell"
        binding.tvCRateValue.text = df2.format(cRate)
        binding.tvRealPowerWatt.text = "${df2.format(realPowerWatt)} W · $cellTypeLabel"

        binding.tvStressScoreLabel.text = getString(R.string.stress_score_label, compositeStress)
        binding.tvStressScoreValue.text = "$compositeStress%"
        binding.progressStressScore.setProgressCompat(compositeStress, true)

        val statusColorRes = when {
            compositeStress <= 35 -> R.color.battery_info_voltage_normal
            compositeStress <= 65 -> R.color.battery_info_voltage_warning
            else -> R.color.battery_info_voltage_danger
        }
        val statusColor = ContextCompat.getColor(ctx, statusColorRes)
        MotionHelper.animateBadgeColors(binding.tvStressBadge, statusColor)
        binding.tvStressScoreValue.setTextColor(statusColor)
        binding.progressStressScore.setIndicatorColor(statusColor)
        MotionHelper.animateImageTint(binding.imgStressIcon, statusColor)

        binding.tvStressBadge.text = when {
            compositeStress <= 35 -> getString(R.string.stress_status_low)
            compositeStress <= 65 -> getString(R.string.stress_status_moderate)
            else -> getString(R.string.stress_status_high)
        }

        val unitPerCell = if (cellMultiplier == 2) "mΩ/sel (2S)" else "mΩ"
        binding.tvInternalImpedance.text = "~${rIntCellMohm.roundToInt()} $unitPerCell"
        binding.tvVoltageSag.text = "$voltageSagMv mV"
        binding.tvJouleHeat.text = "${df1.format(jouleHeatMw)} mW"
        binding.tvWearMultiplier.text = "${df2.format(wearMultiplier)}x"

        binding.tvVectorThermalLabel.text = getString(R.string.stress_vector_thermal, df1.format(tempC))
        binding.tvVectorThermalPct.text = "$thermalStress%"
        binding.progressVectorThermal.setProgressCompat(thermalStress, true)
        binding.progressVectorThermal.setIndicatorColor(getVectorColor(ctx, thermalStress))

        binding.tvVectorVoltageLabel.text = getString(R.string.stress_vector_voltage, cellVoltageMv.roundToInt())
        binding.tvVectorVoltagePct.text = "$voltageStress%"
        binding.progressVectorVoltage.setProgressCompat(voltageStress, true)
        binding.progressVectorVoltage.setIndicatorColor(getVectorColor(ctx, voltageStress))

        binding.tvVectorCurrentLabel.text = getString(
            R.string.stress_vector_current,
            df2.format(cRate),
            currentAbsMa
        )
        binding.tvVectorCurrentPct.text = "$currentStress%"
        binding.progressVectorCurrent.setProgressCompat(currentStress, true)
        binding.progressVectorCurrent.setIndicatorColor(getVectorColor(ctx, currentStress))

        when {
            compositeStress <= 35 -> {
                binding.tvStressDiagnostic.text = getString(
                    R.string.stress_diag_low,
                    df2.format(cRate),
                    rIntCellMohm.roundToInt(),
                    df1.format(jouleHeatMw)
                )
                binding.tvStressRecommendation.text = getString(R.string.stress_rec_low)
            }
            compositeStress <= 65 -> {
                binding.tvStressDiagnostic.text = getString(
                    R.string.stress_diag_moderate,
                    df2.format(cRate),
                    rIntCellMohm.roundToInt(),
                    voltageSagMv,
                    df2.format(wearMultiplier)
                )
                binding.tvStressRecommendation.text = getString(R.string.stress_rec_moderate)
            }
            else -> {
                binding.tvStressDiagnostic.text = getString(
                    R.string.stress_diag_high,
                    df2.format(cRate),
                    rIntCellMohm.roundToInt(),
                    df1.format(jouleHeatMw),
                    df2.format(wearMultiplier)
                )
                binding.tvStressRecommendation.text = getString(R.string.stress_rec_high)
            }
        }
    }

    private fun getVectorColor(context: Context, score: Int): Int {
        val res = when {
            score <= 35 -> R.color.battery_info_voltage_normal
            score <= 65 -> R.color.battery_info_voltage_warning
            else -> R.color.battery_info_voltage_danger
        }
        return ContextCompat.getColor(context, res)
    }

    override fun onDestroyView() {
        stopRealtimeMonitoring()
        cancelPulseTest()
        hasAnimatedEntrance = false
        super.onDestroyView()
        _binding = null
    }
}
