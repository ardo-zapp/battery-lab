package com.jacktor.batterylab.fragments.tab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databinding.IdleDrainFragmentBinding
import com.jacktor.batterylab.helpers.IdleDrainHelper
import com.jacktor.batterylab.helpers.IdleDrainStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IdleDrainFragment : Fragment(R.layout.idle_drain_fragment) {

    private var _binding: IdleDrainFragmentBinding? = null
    private val binding get() = _binding!!

    private var realtimeUpdateJob: Job? = null
    private var pref: SharedPreferences? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("idle_drain")) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                updateUI()
            }
        }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ctx = context ?: return
            batteryIntent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            updateUI()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = IdleDrainFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        setupButtons()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        pref?.registerOnSharedPreferenceChangeListener(prefListener)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(
            requireContext(),
            powerReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        updateUI()
        startRealtimeUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopRealtimeUpdates()
        pref?.unregisterOnSharedPreferenceChangeListener(prefListener)
        try {
            requireContext().unregisterReceiver(powerReceiver)
        } catch (_: Throwable) {
        }
    }

    private fun startRealtimeUpdates() {
        stopRealtimeUpdates()
        realtimeUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(1500L)
                val ctx = context ?: break
                batteryIntent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                updateUI()
            }
        }
    }

    private fun stopRealtimeUpdates() {
        realtimeUpdateJob?.cancel()
        realtimeUpdateJob = null
    }

    private fun setupButtons() {
        binding.btnRefresh.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            batteryIntent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            updateUI()
        }

        binding.btnReset.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.idle_drain_reset)
                .setMessage(R.string.idle_drain_reset_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    IdleDrainHelper.resetAll(ctx)
                    updateUI()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    fun updateUI() {
        val context = context ?: return
        if (_binding == null) return

        val stats = IdleDrainHelper.getIdleDrainStats(context)
        val df = DecimalFormat("#.#")

        // 1. Charging Notice
        binding.cardChargingNotice.visibility = if (stats.isCharging) View.VISIBLE else View.GONE

        // 2. Status Badge
        when (stats.status) {
            IdleDrainStatus.EXCELLENT -> {
                binding.tvStatusBadge.text = getString(R.string.idle_drain_status_excellent)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.green))
            }
            IdleDrainStatus.NORMAL -> {
                binding.tvStatusBadge.text = getString(R.string.idle_drain_status_normal)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary))
            }
            IdleDrainStatus.HIGH_DRAIN -> {
                binding.tvStatusBadge.text = getString(R.string.idle_drain_status_high)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.red))
            }
            IdleDrainStatus.NO_DATA -> {
                binding.tvStatusBadge.text = getString(R.string.idle_drain_status_no_data)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.dark_white))
            }
        }

        val hasData = stats.totalDurationMs >= IdleDrainHelper.MIN_IDLE_DURATION_MS || stats.totalDrainPercent > 0f

        // 3. Overview Primary Metrics
        if (hasData) {
            binding.tvDrainRateValue.text = df.format(stats.totalDrainRatePercentPerHour)
            binding.tvDrainRateMa.text = "~${df.format(stats.totalDrainRateMa)} mA"
            binding.tvDeepSleepPercent.text = "${df.format(stats.deepSleepPercent)}%"
            binding.progressDeepSleep.setProgressCompat(stats.deepSleepPercent.toInt().coerceIn(0, 100), true)
        } else {
            binding.tvDrainRateValue.text = "—"
            binding.tvDrainRateMa.text = "— mA"
            binding.tvDeepSleepPercent.text = "—%"
            binding.progressDeepSleep.setProgressCompat(0, true)
        }

        // 4. Since Unplugged Details
        if (hasData) {
            val totalPctStr = IdleDrainHelper.formatPercent(stats.totalDrainPercent)
            binding.tvTotalDuration.text = IdleDrainHelper.formatDuration(context, stats.totalDurationMs)
            binding.tvTotalDrain.text = "$totalPctStr% (~${df.format(stats.totalDrainMah)} mAh)"
            binding.tvTotalDeepSleep.text = "${IdleDrainHelper.formatDuration(context, stats.totalDeepSleepMs)} (${df.format(stats.deepSleepPercent)}%)"
            binding.tvTotalAwake.text = IdleDrainHelper.formatDuration(context, stats.totalAwakeMs)
        } else {
            binding.tvTotalDuration.text = "—"
            binding.tvTotalDrain.text = "—"
            binding.tvTotalDeepSleep.text = "—"
            binding.tvTotalAwake.text = "—"
        }

        // 5. Latest Session
        if (stats.lastDurationMs >= IdleDrainHelper.MIN_IDLE_DURATION_MS) {
            binding.cardLastSession.visibility = View.VISIBLE
            val formattedTime = if (stats.lastTimestamp > 0L) {
                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(stats.lastTimestamp))
            } else ""
            binding.tvLastSessionTime.text = if (formattedTime.isNotEmpty()) {
                getString(R.string.idle_drain_finished_at, formattedTime)
            } else ""
            binding.tvLastSessionDuration.text = IdleDrainHelper.formatDuration(context, stats.lastDurationMs)
            binding.tvLastSessionDrain.text = getString(
                R.string.idle_drain_rate_per_hour_format,
                IdleDrainHelper.formatPercent(stats.lastDrainPercent),
                df.format(stats.lastDrainRatePercentPerHour)
            )
            binding.tvLastSessionDeepSleep.text = "${df.format(stats.lastDeepSleepPercent)}%"
        } else {
            binding.cardLastSession.visibility = View.GONE
        }

        // 6. Diagnostics & Recommendations
        binding.tvDiagnosticSummary.text = stats.diagnosticSummary
        binding.tvRecommendation.text = stats.recommendation
    }

    override fun onDestroyView() {
        stopRealtimeUpdates()
        super.onDestroyView()
        _binding = null
    }
}
