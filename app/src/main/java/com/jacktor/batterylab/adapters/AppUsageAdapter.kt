package com.jacktor.batterylab.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databinding.ItemAppUsageBinding
import com.jacktor.batterylab.helpers.AppPowerEntry
import com.jacktor.batterylab.helpers.AppUsageHelper
import com.jacktor.batterylab.helpers.DrainSeverity
import com.jacktor.batterylab.helpers.MotionHelper
import java.text.DecimalFormat

class AppUsageAdapter(
    private val onItemClick: (AppPowerEntry) -> Unit
) : ListAdapter<AppPowerEntry, AppUsageAdapter.ViewHolder>(Diff) {

    private var maxSharePercent: Double = 100.0

    object Diff : DiffUtil.ItemCallback<AppPowerEntry>() {
        override fun areItemsTheSame(oldItem: AppPowerEntry, newItem: AppPowerEntry): Boolean =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AppPowerEntry, newItem: AppPowerEntry): Boolean =
            oldItem.rank == newItem.rank &&
                oldItem.foregroundTimeMs == newItem.foregroundTimeMs &&
                oldItem.backgroundServiceMs == newItem.backgroundServiceMs &&
                oldItem.launchCount == newItem.launchCount &&
                oldItem.estimatedMah.toInt() == newItem.estimatedMah.toInt() &&
                oldItem.severity == newItem.severity
    }

    fun submitEntries(entries: List<AppPowerEntry>) {
        maxSharePercent = (entries.maxOfOrNull { it.powerSharePercent } ?: 100.0).coerceAtLeast(1.0)
        submitList(entries)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppUsageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), maxSharePercent, onItemClick)
    }

    class ViewHolder(
        private val binding: ItemAppUsageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val pctFormat = DecimalFormat("#.#")

        fun bind(
            item: AppPowerEntry,
            maxShare: Double,
            onItemClick: (AppPowerEntry) -> Unit
        ) {
            val ctx = binding.root.context
            binding.tvAppRank.text = ctx.getString(R.string.app_usage_rank_fmt, item.rank)
            binding.tvAppName.text = item.appName

            binding.imgAppIcon.imageTintList = null
            binding.imgAppIcon.clearColorFilter()
            if (item.icon != null) {
                val safeIcon = item.icon.constantState?.newDrawable(ctx.resources)?.mutate()
                    ?: item.icon.mutate()
                safeIcon.colorFilter = null
                safeIcon.setTintList(null)
                binding.imgAppIcon.setImageDrawable(safeIcon)
            } else {
                binding.imgAppIcon.setImageResource(R.drawable.ic_data_usage_24)
            }

            val metaParts = buildList {
                if (item.foregroundTimeMs > 0L) {
                    add(
                        ctx.getString(
                            R.string.app_usage_fg_short,
                            AppUsageHelper.formatDurationCompact(item.foregroundTimeMs)
                        )
                    )
                }
                if (item.backgroundServiceMs > 0L) {
                    add(
                        ctx.getString(
                            R.string.app_usage_bg_short,
                            AppUsageHelper.formatDurationCompact(item.backgroundServiceMs)
                        )
                    )
                }
                if (item.launchCount > 0) {
                    add(ctx.getString(R.string.app_usage_launches_short, item.launchCount))
                }
            }
            binding.tvAppUsageMeta.text = metaParts.joinToString(" · ")
                .ifBlank { item.packageName }

            val displayBatteryPct = item.batteryPercentEquiv.coerceAtLeast(0.1)
            binding.tvAppSharePercent.text =
                ctx.getString(R.string.app_usage_percent_fmt, pctFormat.format(displayBatteryPct))
            binding.tvAppEstimatedMah.text =
                ctx.getString(R.string.app_usage_mah_fmt, item.estimatedMah.toInt().coerceAtLeast(1))

            val (badgeLabel, color) = labelAndColorFor(ctx, item.severity)
            binding.tvAppSeverityBadge.text = badgeLabel
            binding.tvAppSeverityBadge.setTextColor(color)
            binding.tvAppSeverityBadge.background?.mutate()?.let { bg ->
                bg.setTint(ColorUtils.setAlphaComponent(color, 30))
                binding.tvAppSeverityBadge.background = bg
            }

            val relativeProgress = ((item.powerSharePercent / maxShare) * 100.0)
                .toInt()
                .coerceIn(4, 100)
            binding.progressAppPower.setIndicatorColor(color)
            binding.progressAppPower.setProgressCompat(relativeProgress, MotionHelper.isAnimationsEnabled())

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }

        companion object {
            fun labelAndColorFor(context: Context, severity: DrainSeverity): Pair<String, Int> {
                val (labelRes, colorRes) = when (severity) {
                    DrainSeverity.EXTREME -> R.string.app_usage_severity_extreme to R.color.battery_info_voltage_danger
                    DrainSeverity.HIGH -> R.string.app_usage_severity_high to R.color.battery_info_voltage_warning
                    DrainSeverity.MODERATE -> R.string.app_usage_severity_moderate to R.color.md_theme_primary
                    DrainSeverity.LOW -> R.string.app_usage_severity_low to R.color.green
                }
                return context.getString(labelRes) to ContextCompat.getColor(context, colorRes)
            }
        }
    }
}
