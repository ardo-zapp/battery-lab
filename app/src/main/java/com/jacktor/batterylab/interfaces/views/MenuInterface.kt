package com.jacktor.batterylab.interfaces.views


import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.R
import com.jacktor.batterylab.fragments.BatteryInfoFragment
import com.jacktor.batterylab.fragments.HistoryFragment
import com.jacktor.batterylab.fragments.ToolsFragment
import com.jacktor.batterylab.helpers.HistoryHelper
import com.jacktor.premium.Premium


interface MenuInterface {

    fun MainActivity.inflateMenu(position: Int) {

        when (fragment) {
            is HistoryFragment -> {

                toolbar.inflateMenu(R.menu.history_menu)

                toolbar.menu.findItem(R.id.clear_history).apply {

                    isVisible = Premium.isPremium().value && HistoryHelper.isHistoryNotEmpty(
                        this@inflateMenu
                    )

                    setOnMenuItemClickListener {

                        HistoryHelper.clearHistory(this@inflateMenu, this)

                        true
                    }
                }

                toolbar.menu.findItem(R.id.history_premium).apply {
                    isVisible = !Premium.isPremium().value

                    setOnMenuItemClickListener {
                        premiumLauncher.launch(Premium.intentForPremium(this@inflateMenu))
                        true
                    }
                }
            }

            is ToolsFragment -> {
                clearMenu()
                defaultMenu()
            }

            else -> defaultMenu()
        }
    }

    fun MainActivity.defaultMenu() {
        toolbar.inflateMenu(R.menu.main_menu)

        toolbar.menu.findItem(R.id.instruction).apply {
            isVisible = fragment is BatteryInfoFragment || fragment is ToolsFragment
            setOnMenuItemClickListener {
                showInstruction()
                true
            }
        }

        toolbar.menu.findItem(R.id.faq).setOnMenuItemClickListener {
            showFaq()
            true
        }

        toolbar.menu.findItem(R.id.tips).setOnMenuItemClickListener {
            MaterialAlertDialogBuilder(this@defaultMenu).apply {
                setIcon(R.drawable.ic_tips_for_extending_battery_life_24dp)
                setTitle(getString(R.string.tips_dialog_title))
                setMessage(
                    getString(R.string.tip1) +
                        getString(R.string.tip2) +
                        getString(R.string.tip3) +
                        getString(R.string.tip4) +
                        getString(R.string.tip5)
                )
                setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                show()
            }
            true
        }

        toolbar.menu.findItem(R.id.premium).setOnMenuItemClickListener {
            premiumLauncher.launch(Premium.intentForPremium(this@defaultMenu))
            true
        }
    }

    fun MainActivity.clearMenu() = toolbar.menu.clear()

    fun MainActivity.showInstruction() {

        MaterialAlertDialogBuilder(this).apply {

            setIcon(R.drawable.ic_instruction_not_supported_24dp)
            setTitle(getString(R.string.instruction))
            setMessage(
                getString(R.string.instruction_message) +
                        getString(R.string.instruction_message_do_not_kill_the_service) +
                        getString(
                            R.string.instruction_message_dont_kill_my_app
                        )
            )

            setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }

            setCancelable(false)

            show()
        }
    }

    fun MainActivity.showFaq() {
        if (showFaqDialog == null) {
            showFaqDialog = MaterialAlertDialogBuilder(this).apply {
                setIcon(R.drawable.ic_faq_question_24dp)
                setTitle(getString(R.string.faq))
                setMessage(
                    getString(R.string.faq_how_does_the_app_work) +
                        getString(R.string.faq_capacity_added) +
                        getString(R.string.faq_dual_cell_voltage) +
                        getString(R.string.faq_idle_drain) +
                        getString(R.string.faq_stress_impedance) +
                        getString(R.string.faq_where_does_the_app_get_the_ccl) +
                        getString(R.string.faq_why_is_ccl_not_displayed) +
                        getString(R.string.faq_i_have_everything_in_zeros) +
                        getString(R.string.faq_units) +
                        getString(R.string.faq_available_capacity) +
                        getString(R.string.faq_residual_capacity_is_higher) +
                        getString(R.string.faq_battery_wear_changes_when_charger_is_disconnected) +
                        getString(R.string.faq_battery_wear_not_change) +
                        getString(R.string.faq_with_each_charge_battery_wear_changes) +
                        getString(R.string.faq_where_does_the_app_get_the_number_of_cycles_android) +
                        getString(R.string.faq_not_displayed_number_of_cycles_android)
                )
                setPositiveButton(android.R.string.ok) { _, _ ->
                    showFaqDialog = null
                }
                setCancelable(false)
                show()
            }
        }
    }
}