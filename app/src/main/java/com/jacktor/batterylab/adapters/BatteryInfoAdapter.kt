// BatteryInfoAdapter.kt
package com.jacktor.batterylab.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jacktor.batterylab.databinding.ItemBatteryCountsBinding
import com.jacktor.batterylab.databinding.ItemBatteryHeaderBinding
import com.jacktor.batterylab.databinding.ItemBatteryStatBinding
import com.jacktor.batterylab.helpers.TextAppearanceHelper
import com.jacktor.batterylab.models.BatteryInfoRow
import androidx.core.view.isVisible

class BatteryInfoAdapter(
    private val textStyle: () -> Triple<String, String, String>,
    private val applySubtitle: (view: TextView) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_COUNTS = 1
        private const val TYPE_STAT = 2

        private const val PAYLOAD_STYLE_ONLY = "STYLE_ONLY"
    }

    private val items = mutableListOf<BatteryInfoRow>()

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
        is BatteryInfoRow.Stat -> TYPE_STAT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(ItemBatteryHeaderBinding.inflate(inflater, parent, false))
            TYPE_COUNTS -> CountsVH(ItemBatteryCountsBinding.inflate(inflater, parent, false))
            else -> StatVH(ItemBatteryStatBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.any { it == PAYLOAD_STYLE_ONLY }) {
            when (holder) {
                is HeaderVH -> styleAll(holder.itemView)
                is CountsVH -> styleAll(holder.itemView)
                is StatVH -> {
                    styleAll(holder.itemView)
                    listOf(
                        holder.b.tvSecondary1,
                        holder.b.tvSecondary2,
                        holder.b.tvSecondary3,
                        holder.b.tvSecondary4
                    )
                        .filter { it.isVisible }
                        .forEach { applySubtitle(it) }
                }
            }
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is BatteryInfoRow.Header -> (holder as HeaderVH).bind(item)
            is BatteryInfoRow.Counts -> (holder as CountsVH).bind(item)
            is BatteryInfoRow.Stat -> (holder as StatVH).bind(item)
        }
    }

    // ===== ViewHolders =====
    inner class HeaderVH(private val b: ItemBatteryHeaderBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(data: BatteryInfoRow.Header) {
            styleAll(b.root)

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
    }

    inner class CountsVH(private val b: ItemBatteryCountsBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(data: BatteryInfoRow.Counts) {
            styleAll(b.root)

            b.tvNumberOfCharges.text = data.charges
            b.tvNumberOfFullCharges.text = data.fullCharges
            b.tvNumberOfCycles.text = data.cycles
            b.tvNumberOfCyclesAndroid.apply {
                text = data.cyclesAndroid
                visibility = if (data.showCyclesAndroid) View.VISIBLE else View.GONE
            }
        }
    }

    inner class StatVH(val b: ItemBatteryStatBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(data: BatteryInfoRow.Stat) {
            styleAll(b.root)

            b.imgIcon.setImageResource(data.icon)
            b.imgIcon.setColorFilter(ContextCompat.getColor(b.root.context, data.iconTint))
            b.tvTitle.text = data.title
            b.tvPrimary.text = data.primary

            val secondaryViews =
                listOf(b.tvSecondary1, b.tvSecondary2, b.tvSecondary3, b.tvSecondary4)
            secondaryViews.forEach { it.visibility = View.GONE }

            data.secondary.take(secondaryViews.size).forEachIndexed { i, value ->
                val v = secondaryViews[i]
                v.text = value
                v.visibility = View.VISIBLE
                applySubtitle(v)
            }
        }
    }

    // ===== Utilities =====

    private fun styleAll(root: View) {
        val (style, font, size) = textStyle()
        val all = ArrayList<TextView>(8)

        fun collect(v: View) {
            if (v is TextView) all.add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i))
        }
        collect(root)

        all.forEach { tv ->
            TextAppearanceHelper.setTextAppearance(
                root.context,
                tv as AppCompatTextView,
                style,
                font,
                size,
                subTitle = false
            )
        }
    }
}
