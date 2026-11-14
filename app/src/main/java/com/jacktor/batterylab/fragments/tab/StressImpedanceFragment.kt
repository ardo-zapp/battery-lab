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
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DESIGN_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.RESIDUAL_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.STRESS_CALIBRATED_RINT_MOHM
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UNIT_OF_MEASUREMENT_OF_available_capacity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class StressImpedanceFragment : Fragment(R.layout.stress_impedance_fragment), BatteryInfoInterface {

    private var _binding: StressImpedanceFragmentBinding? = null
    private val binding get() = _binding!!

    private var realtimeJob: Job? = null
    private var pulseTestJob: Job? = null
    private var pulseIconAnimator: AnimatorSet? = null
    private var hasAnimatedEntrance = false

    companion object {
        private var lastSampledCurrentSignedMa: Double? = null
        private var lastSampledCellVoltageMv: Double? = null
        private var emaInternalResistanceMohm: Double? = null
    }

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
            lastSampledCurrentSignedMa = null
            lastSampledCellVoltageMv = null
            emaInternalResistanceMohm = null
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
                // 1. Sample resting baseline (un-smoothed)
                val baseSamples = mutableListOf<Pair<Double, Double>>()
                repeat(3) {
                    val v = getVoltage(ctx)
                    val i = readRawInstantCurrentMa(ctx)
                    if (v > 0.0) baseSamples += v to i
                    delay(150L)
                }

                // 2. Apply controlled 2.0s compute load pulse and sample at end of pulse
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
                val cellMultiplier = resolveBatteryCellMultiplier(baseVoltageMv).toDouble()

                val loadVoltageMv = loadedSamples.map { it.first }.average()
                val loadCurrentMa = loadedSamples.map { it.second }.average()

                val signedDeltaIMa = loadCurrentMa - baseCurrentMa
                val signedDeltaVCellMv = (baseVoltageMv - loadVoltageMv) / cellMultiplier
                val deltaIMa = abs(signedDeltaIMa)
                val deltaVCellMv = abs(signedDeltaVCellMv)

                if (deltaIMa >= 80.0 && deltaVCellMv >= 1.0 && signedDeltaIMa * signedDeltaVCellMv > 0.0) {
                    val measuredMohm = ((deltaVCellMv / deltaIMa) * 1000.0).coerceIn(25.0, 400.0)
                    emaInternalResistanceMohm = measuredMohm
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
                                    deltaIMa.roundToInt(),
                                    deltaVCellMv.roundToInt(),
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
                                deltaIMa.roundToInt(),
                                deltaVCellMv.roundToInt()
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
        if (emaInternalResistanceMohm == null) {
            val savedRint = pref.getFloat(STRESS_CALIBRATED_RINT_MOHM, -1f)
            if (savedRint > 0f) {
                emaInternalResistanceMohm = savedRint.toDouble()
            }
        }
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
        val cellMultiplier = resolveBatteryCellMultiplier(rawVoltageMv)
        val cellVoltageMv = rawVoltageMv / cellMultiplier.toDouble()

        val tempC = getTemperatureInCelsius(ctx)
        val currentAbsMa = getChargeDischargeCurrent(ctx).coerceAtLeast(0)
        val signedCurrentMa = if (isCharging) currentAbsMa.toDouble() else -currentAbsMa.toDouble()

        val designCapMah = pref.getInt(
            DESIGN_CAPACITY,
            ctx.resources.getInteger(R.integer.min_design_capacity)
        ).coerceAtLeast(1500)

        // 1. C-Rate & Real Power (W)
        val cRate = currentAbsMa.toDouble() / designCapMah.toDouble()
        val realPowerWatt = (currentAbsMa.toDouble() * rawVoltageMv) / 1_000_000.0

        // 2. Estimasi Impedansi Internal (Rint per sel dalam mΩ):
        // Kombinasi pembacaan diferensial ΔV/ΔI saat arus berubah (>= 120 mA) + model elektro-termal sel
        val cycles = pref.getFloat(NUMBER_OF_CYCLES, 0f)
        val rawResidual = pref.getInt(RESIDUAL_CAPACITY, 0).toDouble()
        val isMicroAh = pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh"
        val residualMah = if (rawResidual > 0.0) {
            if (isMicroAh && rawResidual > 10_000.0) rawResidual / 1000.0 else rawResidual
        } else designCapMah.toDouble()
        val healthRatio = (residualMah / designCapMah.toDouble()).coerceIn(0.65, 1.05)

        // Baseline fisik Rint sel Li-Po modern (~55–75 mΩ baru, naik seiring siklus, suhu dingin, & SoC ekstrem)
        val baseCellMohm = (420_000.0 / designCapMah.toDouble()).coerceIn(45.0, 95.0)
        val wearFactor = (1.0 + (1.0 - healthRatio) * 1.35 + (cycles / 800.0) * 0.25).coerceIn(0.95, 2.2)
        val tempFactor = when {
            tempC < 20.0 -> 1.0 + (20.0 - tempC) * 0.035
            tempC > 30.0 -> (1.0 - (tempC - 30.0) * 0.006).coerceAtLeast(0.85)
            else -> 1.0
        }
        val socFactor = if (level < 20) 1.0 + (20 - level) * 0.02 else 1.0
        val modeledRintMohm = (baseCellMohm * wearFactor * tempFactor * socFactor).coerceIn(38.0, 320.0)

        val prevI = lastSampledCurrentSignedMa
        val prevV = lastSampledCellVoltageMv
        if (prevI != null && prevV != null && !isCharging) {
            val deltaIMa = signedCurrentMa - prevI
            val deltaVMv = cellVoltageMv - prevV
            // Hanya perbarui impedansi diferensial saat pengosongan (discharging) agar langkah regulasi CC/CV charger tidak mendistorsi ΔV/ΔI
            if (abs(deltaIMa) >= 140.0 && deltaIMa * deltaVMv > 0.0 && abs(deltaVMv) <= 180.0) {
                val minValidMohm = (modeledRintMohm * 0.55).coerceAtLeast(32.0)
                val maxValidMohm = (modeledRintMohm * 1.65).coerceAtMost(290.0)
                val measuredMohm = ((abs(deltaVMv) / abs(deltaIMa)) * 1000.0)
                    .coerceIn(minValidMohm, maxValidMohm)
                val blended = measuredMohm * 0.35 + modeledRintMohm * 0.65
                emaInternalResistanceMohm = emaInternalResistanceMohm?.let {
                    it * 0.75 + blended * 0.25
                } ?: blended
            }
        }
        if (forceSample || lastSampledCurrentSignedMa == null ||
            abs(signedCurrentMa - (lastSampledCurrentSignedMa ?: 0.0)) >= 120.0
        ) {
            lastSampledCurrentSignedMa = signedCurrentMa
            lastSampledCellVoltageMv = cellVoltageMv
        }

        val rIntCellMohm = (emaInternalResistanceMohm ?: modeledRintMohm).coerceIn(35.0, 350.0)
        val totalPackRintOhm = (rIntCellMohm * cellMultiplier) / 1000.0

        // 3. Voltage Sag (I * R) & Disipasi Panas Internal (I² * R)
        val currentAmp = currentAbsMa / 1000.0
        val voltageSagMv = (currentAmp * rIntCellMohm).roundToInt()
        val jouleHeatMw = (currentAmp.pow(2) * (totalPackRintOhm * 1000.0))

        // 4. Rincian 3 Vektor Tekanan Elektrokimia (0..100)
        // Vektor A: Tekanan Termal (Arrhenius SEI growth)
        val thermalStress = when {
            tempC in 20.0..31.0 -> ((tempC - 20.0) * 1.2).coerceIn(5.0, 15.0)
            tempC > 31.0 -> (15.0 + (tempC - 31.0).pow(1.45) * 4.2).coerceIn(15.0, 100.0)
            else -> (15.0 + (20.0 - tempC) * 3.5).coerceIn(10.0, 85.0)
        }.roundToInt().coerceIn(0, 100)

        // Vektor B: Tekanan Tegangan Katoda / Anoda (per sel)
        val voltageStress = when {
            cellVoltageMv in 3700.0..4100.0 -> (10.0 + abs(cellVoltageMv - 3880.0) * 0.04).coerceIn(8.0, 22.0)
            cellVoltageMv > 4100.0 -> (22.0 + ((cellVoltageMv - 4100.0) / 380.0).pow(1.6) * 72.0).coerceIn(22.0, 100.0)
            else -> (22.0 + ((3700.0 - cellVoltageMv) / 350.0).pow(1.5) * 75.0).coerceIn(22.0, 100.0)
        }.roundToInt().coerceIn(0, 100)

        // Vektor C: Tekanan Arus / C-Rate
        val currentStress = (cRate * 85.0).pow(1.15).roundToInt().coerceIn(2, 100)

        // Skor Stres Komposit (0..100)
        val compositeStress = (thermalStress * 0.40 + voltageStress * 0.35 + currentStress * 0.25)
            .roundToInt()
            .coerceIn(0, 100)

        // Faktor Keausan Siklus (Cycle Aging Multiplier relatif terhadap standar 1.0x)
        val arrheniusFactor = exp(0.048 * (tempC - 25.0)).coerceIn(0.65, 3.5)
        val voltageAgingFactor = when {
            cellVoltageMv > 4200.0 -> 1.0 + ((cellVoltageMv - 4200.0) / 280.0).pow(1.5) * 0.95
            cellVoltageMv < 3550.0 -> 1.0 + ((3550.0 - cellVoltageMv) / 250.0) * 0.55
            else -> 0.72
        }
        val cRateAgingFactor = (0.85 + cRate * 0.65).coerceIn(0.80, 2.4)
        val wearMultiplier = (arrheniusFactor * voltageAgingFactor * cRateAgingFactor).coerceIn(0.45, 4.5)

        val df2 = DecimalFormat("#.##")
        val df1 = DecimalFormat("#.#")

        // Bind UI
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

        // Bind 3 Vectors
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

        // Diagnostics & Recommendations
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
