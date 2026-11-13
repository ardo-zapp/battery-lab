// BatteryInfoAdapter.kt
package com.jacktor.batterylab.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databinding.ItemBatteryCountsBinding
import com.jacktor.batterylab.databinding.ItemBatteryHeaderBinding
import com.jacktor.batterylab.databinding.ItemBatterySectionBinding
import com.jacktor.batterylab.databinding.ItemBatteryStatBinding
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import com.jacktor.batterylab.helpers.TextAppearanceHelper
import com.jacktor.batterylab.models.BatteryInfoRow

class BatteryInfoAdapter(
    private val textStyle: () -> Triple<String, String, String>,
    private val applySubtitle: (view: TextView) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_COUNTS = 1
        private const val TYPE_STAT = 2
        private const val TYPE_SECTION = 3

        private const val PAYLOAD_STYLE_ONLY = "STYLE_ONLY"
    }

    private val items = mutableListOf<BatteryInfoRow>()
    val currentItems: List<BatteryInfoRow> get() = items

    fun submit(list: List<BatteryInfoRow>) {
        val old = items.toList()
        val new = list.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = new.size
            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                val a = old[o]
                val b = new[n]
                return when {
                    a is BatteryInfoRow.Header && b is BatteryInfoRow.Header -> true
                    a is BatteryInfoRow.Counts && b is BatteryInfoRow.Counts -> true
                    a is BatteryInfoRow.Section && b is BatteryInfoRow.Section -> a.title == b.title
                    a is BatteryInfoRow.Stat && b is BatteryInfoRow.Stat -> a.title == b.title
                    else -> false
                }
            }

            override fun areContentsTheSame(o: Int, n: Int): Boolean = old[o] == new[n]
            override fun getChangePayload(o: Int, n: Int): Any? = null
        })
        items.clear()
        items.addAll(new)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is BatteryInfoRow.Header -> TYPE_HEADER
        is BatteryInfoRow.Counts -> TYPE_COUNTS
        is BatteryInfoRow.Section -> TYPE_SECTION
        is BatteryInfoRow.Stat -> TYPE_STAT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val holder = when (viewType) {
            TYPE_HEADER -> HeaderVH(ItemBatteryHeaderBinding.inflate(inflater, parent, false))
            TYPE_COUNTS -> CountsVH(ItemBatteryCountsBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> SectionVH(ItemBatterySectionBinding.inflate(inflater, parent, false))
            else -> StatVH(ItemBatteryStatBinding.inflate(inflater, parent, false))
        }
        TextAppearanceHelper.applyToView(holder.itemView)
        return holder
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.any { it == PAYLOAD_STYLE_ONLY }) {
            holder.itemView.setTag(R.id.tag_typeface_applied, null)
            TextAppearanceHelper.applyToView(holder.itemView)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is BatteryInfoRow.Header -> (holder as HeaderVH).bind(item)
            is BatteryInfoRow.Counts -> (holder as CountsVH).bind(item)
            is BatteryInfoRow.Section -> (holder as SectionVH).bind(item)
            is BatteryInfoRow.Stat -> (holder as StatVH).bind(item)
        }
    }

    private var pulseAnimator: android.animation.AnimatorSet? = null

    private fun startPulseAnimation(view: View) {
        if (pulseAnimator != null) return
        val pulseX = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 1.25f).apply {
            duration = 850
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        val pulseY = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 1.25f).apply {
            duration = 850
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        pulseAnimator = android.animation.AnimatorSet().apply {
            playTogether(pulseX, pulseY)
            start()
        }
    }

    private fun stopPulseAnimation(view: View) {
        pulseAnimator?.cancel()
        pulseAnimator = null
        view.scaleX = 1.0f
        view.scaleY = 1.0f
    }

    private var lastAnimatedLevel = -1

    fun bindHeader(b: ItemBatteryHeaderBinding, data: BatteryInfoRow.Header) {
        val targetLevel = data.level.coerceIn(0, 100)
        if (lastAnimatedLevel != targetLevel) {
            val start = if (lastAnimatedLevel == -1) 0 else lastAnimatedLevel
            lastAnimatedLevel = targetLevel

            val progressAnim = android.animation.ValueAnimator.ofInt(start, targetLevel).apply {
                duration = 850
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { va ->
                    val v = va.animatedValue as Int
                    b.circularBatteryProgress.progress = v
                }
            }
            val countAnim = android.animation.ValueAnimator.ofInt(start, targetLevel).apply {
                duration = 850
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { va ->
                    val v = va.animatedValue as Int
                    b.tvHeroBatteryPercent.text = "$v%"
                }
            }
            android.animation.AnimatorSet().apply {
                playTogether(progressAnim, countAnim)
                start()
            }

            // Spring scale pop on center icon
            b.imgBatteryStatusIcon.apply {
                scaleX = 0.7f
                scaleY = 0.7f
                animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(500)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                    .start()
            }
        } else {
            b.circularBatteryProgress.progress = targetLevel
            b.tvHeroBatteryPercent.text = "$targetLevel%"
        }

        val context = b.root.context
        val tintRes = when {
            data.level >= 100 -> R.color.battery_info_level_normal
            data.isCharging -> R.color.battery_info_level_charging
            data.level <= 20 -> R.color.battery_info_level_low
            else -> R.color.battery_info_level_normal
        }
        val tint = ContextCompat.getColor(context, tintRes)

        b.circularBatteryProgress.setIndicatorColor(tint)
        val iconRes = BatteryLevelHelper.batteryLevelIcon(data.level, data.isCharging)
        b.imgBatteryStatusIcon.setImageResource(iconRes)
        b.imgBatteryStatusIcon.setColorFilter(tint)

        b.tvHeroBatteryStatus.apply {
            text = data.statusText
            setTextColor(tint)
            backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(tint, 32))
        }

        b.tvHeroTemp.text = data.temperatureText
        b.tvHeroVoltage.text = data.voltageText
        b.tvHeroTime.text = data.timeHeroText

        val tempTint = ContextCompat.getColor(context, data.tempTint)
        b.imgHeroTemp.setColorFilter(tempTint)

        val voltageTint = ContextCompat.getColor(context, data.voltageTint)
        b.imgHeroVoltage.setColorFilter(voltageTint)

        val timeTint = ContextCompat.getColor(context, data.timeTint)
        b.imgHeroTime.setColorFilter(timeTint)

        if (data.isCharging && data.level < 100) {
            startPulseAnimation(b.imgBatteryStatusIcon)
        } else {
            stopPulseAnimation(b.imgBatteryStatusIcon)
        }

        b.tvChargingTime.apply {
            text = data.chargingTime
            visibility = if (data.chargingTime.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        b.tvChargingTimeRemaining.apply {
            text = data.chargingTimeRemaining
            visibility =
                if (data.chargingTimeRemaining.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        b.tvRemainingBatteryTime.apply {
            text = data.remainingBatteryTime
            visibility =
                if (data.remainingBatteryTime.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        b.tvScreenTime.text = data.screenTime
        b.tvLastChargeTime.text = data.lastChargeTime
    }

    // ===== ViewHolders =====
    inner class HeaderVH(private val b: ItemBatteryHeaderBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(data: BatteryInfoRow.Header) {
            bindHeader(b, data)
        }
    }

    inner class CountsVH(private val b: ItemBatteryCountsBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(data: BatteryInfoRow.Counts) {
            val primary = ContextCompat.getColor(b.root.context, R.color.md_theme_primary)
            b.imgCountsIcon.setColorFilter(primary)
            b.containerCountsIcon.backgroundTintList =
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 28))

            b.tvNumberOfCharges.text = data.charges
            b.tvNumberOfFullCharges.text = data.fullCharges
            b.tvNumberOfCycles.text = data.cycles
            b.rowNumberOfCyclesAndroid.visibility =
                if (data.showCyclesAndroid) View.VISIBLE else View.GONE
            b.tvNumberOfCyclesAndroid.text = data.cyclesAndroid.orEmpty()
        }
    }

    inner class SectionVH(private val b: ItemBatterySectionBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(data: BatteryInfoRow.Section) {
            b.tvSectionTitle.text = data.title.uppercase(java.util.Locale.getDefault())
        }
    }

    inner class StatVH(val b: ItemBatteryStatBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(data: BatteryInfoRow.Stat) {
            val color = ContextCompat.getColor(b.root.context, data.iconTint)
            b.imgIcon.setImageResource(data.icon)
            b.imgIcon.setColorFilter(color)
            b.containerIcon.backgroundTintList =
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 28))

            b.tvTitle.text = data.title
            b.tvPrimary.text = data.primary

            val secondaryViews =
                listOf(
                    b.tvSecondary1,
                    b.tvSecondary2,
                    b.tvSecondary3,
                    b.tvSecondary4,
                    b.tvSecondary5
                )
            secondaryViews.forEach { it.visibility = View.GONE }

            data.secondary.take(secondaryViews.size).forEachIndexed { i, value ->
                val v = secondaryViews[i]
                v.text = value
                v.visibility = View.VISIBLE
            }

            b.layoutSecondaryContainer.visibility =
                if (data.secondary.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ===== Utilities =====

    private fun styleAll(root: View) {
        TextAppearanceHelper.applyToView(root)
    }
}
