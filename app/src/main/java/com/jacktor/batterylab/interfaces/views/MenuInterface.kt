package com.jacktor.batterylab.interfaces.views


import android.os.Build
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
                when (position) {
                    0 -> {
                        clearMenu()
                        defaultMenu()
                    }

                    /*1 -> {
                        clearMenu()
                        toolbar.inflateMenu(R.menu.kernel_menu)

                        toolbar.menu.findItem(R.id.show_command).apply {
                            setOnMenuItemClickListener {
                                val binding = ShowScriptDialogBinding.inflate(
                                    LayoutInflater.from(this@inflateMenu), null, false
                                )

                                val dialog = MaterialAlertDialogBuilder(this@inflateMenu)
                                val clipboard =
                                    this@inflateMenu.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?

                                binding.script.setText(getKernelFromFile(this@inflateMenu))
                                binding.resetScript.text =
                                    this@inflateMenu.getString(R.string.reset_script).uppercase()

                                //Dialog
                                dialog.setView(binding.root.rootView)
                                dialog.apply {
                                    setIcon(
                                        AppCompatResources.getDrawable(
                                            this@inflateMenu, R.drawable.ic_script_24dp
                                        )
                                    )
                                    setTitle(getString(R.string.bash_script).uppercase())
                                    setCancelable(false)
                                    setNegativeButton(getString(R.string.copy_txt).uppercase()) { dialogBtn, _ ->
                                        clipboard!!.setPrimaryClip(
                                            ClipData.newPlainText(
                                                SCRIPT_FILE_NAME, binding.script.text
                                            )
                                        )
                                        dialogBtn.cancel()
                                    }
                                    setPositiveButton(getString(R.string.close).uppercase()) { dialogBtn, _ ->
                                        dialogBtn.cancel()
                                    }
                                    setNeutralButton(getString(R.string.execute).uppercase()) { dialogBtn, _ ->
                                        if (RootChecker().isDeviceRooted()) executeShellFile(
                                            this@inflateMenu
                                        )
                                        dialogBtn.cancel()
                                    }
                                    show()
                                }

                                //Check root
                                if (!RootChecker().isDeviceRooted()) {
                                    binding.resetScript.isEnabled = false
                                    binding.enableScript.isEnabled = false
                                }

                                // Reset script button
                                binding.resetScript.setOnClickListener {
                                    MaterialAlertDialogBuilder(this@inflateMenu).apply {
                                        setIcon(
                                            AppCompatResources.getDrawable(
                                                this@inflateMenu,
                                                R.drawable.ic_reset_all_settings_24dp
                                            )
                                        )
                                        setTitle(getString(R.string.reset_script))
                                        setMessage(getString(R.string.reset_script_dialog))
                                        setPositiveButton(R.string.cancel) { d, _ -> d.dismiss() }
                                        setNegativeButton(R.string.yes_continue) { dialogBtn, _ ->
                                            resetScript(this@inflateMenu)
                                            binding.script.setText(getKernelFromFile(this@inflateMenu))

                                            // Pastikan kernelInformation dijalankan dalam coroutine
                                            CoroutineScope(Dispatchers.Main).launch {
                                                KernelFragment.instance?.kernelInformation()
                                            }

                                            dialogBtn.cancel()
                                        }
                                        show()
                                    }
                                }


                                //Switch autostart script
                                binding.enableScript.isChecked = Prefs(this@inflateMenu).getBoolean(
                                    EXECUTE_SCRIPT_ON_BOOT, resources.getBoolean(
                                        R.bool
                                            .execute_script_on_boot
                                    )
                                )

                                binding.enableScript.setOnCheckedChangeListener { _, isChecked ->
                                    Prefs(this@inflateMenu).setBoolean(
                                        EXECUTE_SCRIPT_ON_BOOT,
                                        isChecked
                                    )

                                    if (isChecked) {
                                        Toast.makeText(
                                            this@inflateMenu,
                                            getString(R.string.enable_execute_script_on_boot),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            this@inflateMenu,
                                            getString(R.string.disable_execute_script_on_boot),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                true
                            }
                        }
                    }*/
                }
            }

            else -> defaultMenu()
        }
    }

    fun MainActivity.defaultMenu() {
        toolbar.inflateMenu(R.menu.main_menu)

        toolbar.menu.findItem(R.id.instruction).apply {
            isVisible = getAvailableCapacity(this@defaultMenu) > 0.0 &&
                    (fragment is BatteryInfoFragment /*|| fragment is KernelFragment*/)
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
                    getString(R.string.tip1) + getString(R.string.tip2) +
                            getString(R.string.tip3) + getString(R.string.tip4)
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
                        getString(R.string.instruction_message_enable_fast_charge_option) +
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setMessage(
                    getString(R.string.faq_how_does_the_app_work) + getString(R.string.faq_capacity_added) + getString(
                        R.string.faq_where_does_the_app_get_the_ccl
                    ) + getString(R.string.faq_why_is_ccl_not_displayed) + getString(R.string.faq_i_have_everything_in_zeros) + getString(
                        R.string.faq_units
                    ) + getString(R.string.faq_available_capacity) + getString(R.string.faq_residual_capacity_is_higher) + getString(
                        R.string.faq_battery_wear_changes_when_charger_is_disconnected
                    ) + getString(R.string.faq_battery_wear_not_change) + getString(R.string.faq_with_each_charge_battery_wear_changes)
                )
                else setMessage(
                    getString(R.string.faq_how_does_the_app_work) + getString(R.string.faq_capacity_added) + getString(
                        R.string.faq_where_does_the_app_get_the_ccl
                    ) + getString(R.string.faq_why_is_ccl_not_displayed) + getString(R.string.faq_i_have_everything_in_zeros) + getString(
                        R.string.faq_units
                    ) + getString(R.string.faq_available_capacity) + getString(R.string.faq_residual_capacity_is_higher) + getString(
                        R.string.faq_battery_wear_changes_when_charger_is_disconnected
                    ) + getString(R.string.faq_battery_wear_not_change) + getString(R.string.faq_with_each_charge_battery_wear_changes) + getString(
                        R.string.faq_where_does_the_app_get_the_number_of_cycles_android
                    ) + getString(R.string.faq_not_displayed_number_of_cycles_android)
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