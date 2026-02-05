package com.jacktor.batterylab.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databases.History
import com.jacktor.batterylab.databases.HistoryDB
import com.jacktor.batterylab.databinding.HistoryRecyclerListItemBinding
import com.jacktor.batterylab.fragments.HistoryFragment
import com.jacktor.batterylab.helpers.HistoryHelper
import com.jacktor.batterylab.helpers.TextAppearanceHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys
import com.jacktor.premium.Premium
import java.text.DecimalFormat

class HistoryAdapter(private var historyList: MutableList<History>) :
    RecyclerView.Adapter<HistoryViewHolder>(), BatteryInfoInterface {

    private lateinit var binding: HistoryRecyclerListItemBinding
    private lateinit var pref: SharedPreferences

    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: HistoryAdapter? = null
    }

    override fun getItemCount() = historyList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {

        instance = this

        binding = HistoryRecyclerListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false
        )

        pref = PreferenceManager.getDefaultSharedPreferences(parent.context)

        return HistoryViewHolder(binding.root.rootView)
    }

    override fun onBindViewHolder(holderHistory: HistoryViewHolder, position: Int) {
        updateTextAppearance(holderHistory)

        //val context = holderHistory.itemView.context
        if (Premium.isPremium().value) {
            binding.apply {
                historyDate.text = historyList[itemCount - 1 - position].date
                historyResidualCapacity.text = getResidualCapacity(
                    holderHistory.itemView.context,
                    historyList[itemCount - 1 - position].residualCapacity
                )
                historyBatteryWear.text = getBatteryWear(
                    holderHistory.itemView.context,
                    historyList[itemCount - 1 - position].residualCapacity
                )
            }
        }
    }

    fun getHistoryList() = historyList

    private fun updateTextAppearance(holderHistory: HistoryViewHolder) {
        TextAppearanceHelper.setTextAppearanceArray(
            holderHistory.itemView.context,
            arrayListOf(
                binding.historyDate, binding.historyResidualCapacity,
                binding.historyBatteryWear
            ),
            pref.getString(PreferencesKeys.TEXT_STYLE, "0"),
            pref.getString(PreferencesKeys.TEXT_FONT, "6"),
            pref.getString(PreferencesKeys.TEXT_SIZE, "2")
        )
    }

    private fun getResidualCapacity(context: Context, residualCapacity: Int): String {

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val isCapacityInWh = pref.getBoolean(
            PreferencesKeys.CAPACITY_IN_WH,
            context.resources.getBoolean(R.bool.capacity_in_wh)
        )

        val designCapacity = pref.getInt(
            PreferencesKeys.DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        )

        var newResidualCapacity = residualCapacity / if (pref.getString(
                PreferencesKeys
                    .UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh"
            ) == "μAh"
        ) 1000.0
        else 100.0

        if (newResidualCapacity < 0.0) newResidualCapacity /= -1.0

        return if (isCapacityInWh) context.getString(
            R.string.residual_capacity_wh,
            DecimalFormat("#.#").format(getCapacityInWh(newResidualCapacity)),
            "${
                DecimalFormat("#.#").format(
                    ((newResidualCapacity / designCapacity)) * 100.0
                )
            }%"
        )
        else context.getString(
            R.string.residual_capacity, DecimalFormat("#.#").format(
                newResidualCapacity
            ), "${
                DecimalFormat("#.#").format(
                    (
                            (newResidualCapacity / designCapacity)) * 100.0
                )
            }%"
        )
    }

    private fun getBatteryWear(context: Context, residualCapacity: Int): String {

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val designCapacity = pref.getInt(
            PreferencesKeys.DESIGN_CAPACITY, context.resources
                .getInteger(R.integer.min_design_capacity)
        ).toDouble()

        val isCapacityInWh = pref.getBoolean(
            PreferencesKeys.CAPACITY_IN_WH,
            context.resources.getBoolean(R.bool.capacity_in_wh)
        )

        var newResidualCapacity = residualCapacity / if (pref.getString(
                PreferencesKeys
                    .UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh"
            ) == "μAh"
        ) 1000.0
        else 100.0

        if (newResidualCapacity < 0.0) newResidualCapacity /= -1.0

        return if (isCapacityInWh) context.getString(
            R.string.battery_wear_wh,
            if (newResidualCapacity > 0 && newResidualCapacity < designCapacity)
                "${
                    DecimalFormat("#.#").format(
                        100 - (newResidualCapacity / designCapacity) * 100
                    )
                }%" else "0%",
            if (newResidualCapacity > 0 && newResidualCapacity < designCapacity)
                DecimalFormat("#.#").format(
                    getCapacityInWh(designCapacity - newResidualCapacity)
                ) else "0"
        )
        else context.getString(
            R.string.battery_wear, if (newResidualCapacity > 0 &&
                newResidualCapacity < designCapacity
            ) "${
                DecimalFormat("#.#").format(
                    100 - ((newResidualCapacity / designCapacity) * 100)
                )
            }%" else "0%",
            if (newResidualCapacity > 0 && newResidualCapacity < designCapacity) DecimalFormat(
                "#.#"
            ).format(designCapacity - newResidualCapacity) else "0"
        )
    }

    fun update(context: Context) {

        pref = PreferenceManager.getDefaultSharedPreferences(context)

        if (HistoryHelper.getHistoryCount(context) > historyList.count()) {
            historyList = HistoryDB(context).readAll() as MutableList<History>
            notifyItemInserted(0)
        } else if (HistoryHelper.isHistoryEmpty(context) ||
            HistoryHelper.getHistoryCount(context) < historyList.count()
        ) {
            historyList = HistoryDB(context).readAll() as MutableList<History>
            notifyItemRangeChanged(0, itemCount - 1)
        }
    }

    fun remove(context: Context, position: Int) {
        if (position >= 0) {
            historyList.removeAt(itemCount - 1 - position)
            notifyItemRemoved(position)
            if (HistoryHelper.isHistoryEmpty(context)) {
                HistoryFragment.instance?.emptyHistory()
            }
        }
    }

    fun undoRemoving(context: Context, position: Int) {
        if (position >= 0) {
            historyList = HistoryDB(context).readAll() as MutableList<History>
            notifyItemInserted(position)
        }
    }
}