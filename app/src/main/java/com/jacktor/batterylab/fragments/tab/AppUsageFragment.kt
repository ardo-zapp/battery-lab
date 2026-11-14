package com.jacktor.batterylab.fragments.tab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jacktor.batterylab.R
import com.jacktor.batterylab.adapters.AppUsageAdapter
import com.jacktor.batterylab.databinding.AppUsageFragmentBinding
import com.jacktor.batterylab.databinding.DialogAppUsageDetailBinding
import com.jacktor.batterylab.helpers.AppPowerEntry
import com.jacktor.batterylab.helpers.AppPowerReport
import com.jacktor.batterylab.helpers.AppUsageHelper
import com.jacktor.batterylab.helpers.AppUsageWindow
import com.jacktor.batterylab.helpers.DrainSeverity
import com.jacktor.batterylab.helpers.MotionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

class AppUsageFragment : Fragment(R.layout.app_usage_fragment) {

    private var _binding: AppUsageFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AppUsageAdapter
    private var loadJob: Job? = null
    private var selectedWindow = AppUsageWindow.LAST_24H
    private var includeSystemApps = false
    private var hasAnimatedEntrance = false

    private val pctFormat = DecimalFormat("#.#")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AppUsageFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AppUsageAdapter { entry ->
            showAppDetailDialog(entry)
        }
        binding.rvAppUsage.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAppUsage.adapter = adapter

        binding.swipeRefreshAppUsage.apply {
            setColorSchemeResources(R.color.swipe_refresh_layout_progress)
            setProgressBackgroundColorSchemeResource(R.color.swipe_refresh_layout_progress_background)
            setOnRefreshListener { loadReport(showSpinner = false) }
        }

        MotionHelper.bindPressScale(binding.btnGrantUsagePermission)
        binding.btnGrantUsagePermission.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            if (!AppUsageHelper.openUsageAccessSettings(ctx)) {
                Toast.makeText(ctx, R.string.app_usage_permission_open_failed, Toast.LENGTH_SHORT)
                    .show()
            }
        }

        binding.chipGroupWindow.setOnCheckedStateChangeListener { _, checkedIds ->
            val newWindow = when (checkedIds.firstOrNull()) {
                R.id.chipWindowToday -> AppUsageWindow.TODAY
                R.id.chipWindow7d -> AppUsageWindow.LAST_7D
                else -> AppUsageWindow.LAST_24H
            }
            if (newWindow != selectedWindow) {
                selectedWindow = newWindow
                loadReport()
            }
        }

        binding.chipIncludeSystem.setOnCheckedChangeListener { _, isChecked ->
            if (includeSystemApps != isChecked) {
                includeSystemApps = isChecked
                loadReport()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadReport()
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        _binding = null
        super.onDestroyView()
    }

    private fun loadReport(showSpinner: Boolean = true) {
        val ctx = context ?: return
        if (_binding == null) return

        loadJob?.cancel()
        if (showSpinner) {
            binding.progressLoadingApps.visibility = View.VISIBLE
        }

        val window = selectedWindow
        val includeSystem = includeSystemApps
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                AppUsageHelper.queryAppPowerReport(ctx, window, includeSystem)
            }
            if (_binding == null || !isAdded) return@launch
            bindReport(report)
        }
    }

    private fun bindReport(report: AppPowerReport) {
        val ctx = context ?: return
        binding.progressLoadingApps.visibility = View.GONE
        binding.swipeRefreshAppUsage.isRefreshing = false

        val hasPermission = report.hasPermission
        binding.cardUsagePermission.isVisible = !hasPermission
        binding.cardAppUsageOverview.isVisible = hasPermission
        binding.scrollUsageFilters.isVisible = hasPermission
        binding.tvAppUsageNote.isVisible = hasPermission

        if (!hasPermission) {
            binding.rvAppUsage.visibility = View.GONE
            binding.cardAppUsageEmpty.visibility = View.GONE
            adapter.submitEntries(emptyList())
            maybeAnimateEntrance()
            return
        }

        binding.tvTrackedAppsBadge.text =
            getString(R.string.app_usage_tracked_count, report.trackedPackagesCount)
        val badgeColor = ContextCompat.getColor(ctx, R.color.md_theme_primary)
        MotionHelper.animateBadgeColors(binding.tvTrackedAppsBadge, badgeColor)

        binding.tvTotalAppMah.text = getString(
            R.string.app_usage_mah_fmt,
            report.totalEstimatedMah.toInt().coerceAtLeast(0)
        )
        binding.tvTotalBatteryPct.text = getString(
            R.string.app_usage_battery_pct_fmt,
            pctFormat.format(report.totalBatteryPercentEquiv),
            report.designCapacityMah
        )
        binding.tvTotalActiveTime.text = if (report.totalForegroundMs > 0L) {
            AppUsageHelper.formatDurationCompact(report.totalForegroundMs)
        } else {
            "0m"
        }
        binding.tvTotalBgTime.text = getString(
            R.string.app_usage_total_bg_fmt,
            if (report.totalBackgroundMs > 0L) {
                AppUsageHelper.formatDurationCompact(report.totalBackgroundMs)
            } else {
                "0m"
            }
        )

        val top = report.entries.firstOrNull()
        if (top != null) {
            binding.layoutTopConsumer.visibility = View.VISIBLE
            val (_, topColor) = AppUsageAdapter.ViewHolder.labelAndColorFor(ctx, top.severity)
            binding.tvTopConsumerSummary.text = getString(
                R.string.app_usage_top_consumer_fmt,
                top.appName,
                pctFormat.format(top.batteryPercentEquiv.coerceAtLeast(0.1)),
                top.estimatedMah.toInt().coerceAtLeast(1)
            )
            binding.tvTopConsumerSummary.setTextColor(topColor)
            binding.layoutTopConsumer.background?.mutate()?.let { bg ->
                bg.setTint(ColorUtils.setAlphaComponent(topColor, 26))
                binding.layoutTopConsumer.background = bg
            }
        } else {
            binding.layoutTopConsumer.visibility = View.GONE
        }

        val isEmpty = report.entries.isEmpty()
        binding.cardAppUsageEmpty.isVisible = isEmpty
        binding.rvAppUsage.isVisible = !isEmpty
        adapter.submitEntries(report.entries)

        maybeAnimateEntrance()
    }

    private fun maybeAnimateEntrance() {
        if (!hasAnimatedEntrance) {
            hasAnimatedEntrance = true
            MotionHelper.animateStaggeredChildren(binding.containerAppUsage)
        }
    }

    private fun showAppDetailDialog(entry: AppPowerEntry) {
        val ctx = context ?: return
        val dialogBinding = DialogAppUsageDetailBinding.inflate(layoutInflater)
        val (severityLabel, severityColor) = AppUsageAdapter.ViewHolder.labelAndColorFor(ctx, entry.severity)

        dialogBinding.imgDialogAppIcon.imageTintList = null
        dialogBinding.imgDialogAppIcon.clearColorFilter()
        if (entry.icon != null) {
            val freshIcon = entry.icon.constantState?.newDrawable(ctx.resources)?.mutate()
                ?: entry.icon.mutate()
            freshIcon.colorFilter = null
            freshIcon.setTintList(null)
            dialogBinding.imgDialogAppIcon.setImageDrawable(freshIcon)
        } else {
            dialogBinding.imgDialogAppIcon.setImageResource(R.drawable.ic_data_usage_24)
        }

        dialogBinding.tvDialogAppName.text = entry.appName
        dialogBinding.tvDialogPackageName.text = entry.packageName
        dialogBinding.tvDialogSeverityBadge.text = severityLabel
        dialogBinding.tvDialogSeverityBadge.setTextColor(severityColor)
        dialogBinding.tvDialogSeverityBadge.background?.mutate()?.let { bg ->
            bg.setTint(ColorUtils.setAlphaComponent(severityColor, 32))
            dialogBinding.tvDialogSeverityBadge.background = bg
        }

        val fgText = if (entry.foregroundTimeMs > 0L) {
            AppUsageHelper.formatDurationCompact(entry.foregroundTimeMs)
        } else {
            "0m"
        }
        val bgText = if (entry.backgroundServiceMs > 0L) {
            AppUsageHelper.formatDurationCompact(entry.backgroundServiceMs)
        } else {
            "0m"
        }

        val advice = when {
            entry.backgroundServiceMs >= 1_200_000L && entry.backgroundServiceMs > entry.foregroundTimeMs * 0.6 ->
                getString(R.string.app_usage_advice_background)
            entry.severity == DrainSeverity.EXTREME || entry.severity == DrainSeverity.HIGH ->
                getString(R.string.app_usage_advice_heavy)
            else -> getString(R.string.app_usage_advice_normal)
        }

        val batteryPctStr = pctFormat.format(entry.batteryPercentEquiv.coerceAtLeast(0.1))
        val sharePctStr = pctFormat.format(entry.powerSharePercent)
        val mahInt = entry.estimatedMah.toInt().coerceAtLeast(1)
        val rateInt = entry.drainRateMahPerHour.toInt().coerceAtLeast(1)
        val whFormat = DecimalFormat("0.00")

        dialogBinding.tvDialogMetricDrain.text = getString(
            R.string.app_usage_dialog_metric_drain,
            batteryPctStr,
            mahInt
        )
        dialogBinding.tvDialogMetricShare.text = getString(
            R.string.app_usage_dialog_metric_share,
            sharePctStr
        )
        dialogBinding.tvDialogMetricForeground.text = getString(
            R.string.app_usage_dialog_metric_fg,
            fgText
        )
        dialogBinding.tvDialogMetricBackground.text = getString(
            R.string.app_usage_dialog_metric_bg,
            bgText
        )
        dialogBinding.tvDialogMetricRate.text = getString(
            R.string.app_usage_dialog_metric_rate,
            rateInt
        )
        dialogBinding.tvDialogMetricEnergy.text = getString(
            R.string.app_usage_dialog_metric_energy,
            whFormat.format(entry.estimatedWh),
            entry.launchCount
        )
        dialogBinding.tvDialogRecommendation.text = advice

        MaterialAlertDialogBuilder(ctx)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.app_usage_open_app_info) { _, _ ->
                if (!AppUsageHelper.openAppInfoSettings(ctx, entry.packageName)) {
                    Toast.makeText(ctx, R.string.app_usage_open_app_info_failed, Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton(R.string.premium_features_close, null)
            .show()
    }
}
