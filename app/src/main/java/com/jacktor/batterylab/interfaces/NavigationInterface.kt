package com.jacktor.batterylab.interfaces

import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.R
import com.jacktor.batterylab.fragments.AboutFragment
import com.jacktor.batterylab.fragments.BackupSettingsFragment
import com.jacktor.batterylab.fragments.BatteryStatusInformationFragment
import com.jacktor.batterylab.fragments.BatteryInfoFragment
import com.jacktor.batterylab.fragments.DebugFragment
import com.jacktor.batterylab.fragments.FeedbackFragment
import com.jacktor.batterylab.fragments.HistoryFragment
import com.jacktor.batterylab.fragments.OverlayFragment
import com.jacktor.batterylab.fragments.PowerConnectionSettingsFragment
import com.jacktor.batterylab.fragments.SettingsFragment
import com.jacktor.batterylab.fragments.ToolsFragment
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import java.lang.ref.WeakReference

interface NavigationInterface : BatteryInfoInterface {

    companion object {
        var mainActivityRef: WeakReference<MainActivity>? = null
    }

    fun MainActivity.bottomNavigation(status: Int) {

        /*navigation.menu.findItem(R.id.charge_discharge_navigation).title = getString(
            if (status == BatteryManager.BATTERY_STATUS_CHARGING) R.string.charging
            else R.string.discharge
        )*/

        navigation.menu.findItem(R.id.charge_discharge_navigation).icon = ContextCompat.getDrawable(
            this,
            BatteryLevelHelper.batteryLevelIcon(
                getBatteryLevel(
                    this
                ),
                status == BatteryManager.BATTERY_STATUS_CHARGING
            )
        )

        navigation.setOnItemSelectedListener {

            when (it.itemId) {

                R.id.charge_discharge_navigation -> {

                    if (fragment !is BatteryInfoFragment) {

                        fragment = BatteryInfoFragment()

                        toolbar.navigationIcon = null

                        MainActivity.isLoadChargeDischarge = true

                        MainActivity.isLoadTools = false

                        MainActivity.isLoadHistory = false

                        MainActivity.isLoadSettings = false

                        MainActivity.isLoadDebug = false

                        clearMenu()

                        inflateMenu(-1)

                        loadFragment(fragment ?: BatteryInfoFragment())

                        this.showInterstitialAd()
                    }
                }

                R.id.tools_navigation -> {

                    if (fragment !is ToolsFragment) {

                        fragment = ToolsFragment()

                        toolbar.title = getString(R.string.tools)

                        toolbar.navigationIcon = null

                        MainActivity.isLoadChargeDischarge = false

                        MainActivity.isLoadTools = true

                        MainActivity.isLoadHistory = false

                        MainActivity.isLoadSettings = false

                        MainActivity.isLoadDebug = false

                        clearMenu()

                        inflateMenu(-1)

                        loadFragment(fragment ?: ToolsFragment())

                        this.showInterstitialAd()
                    }
                }

                R.id.history_navigation -> {

                    if (fragment !is HistoryFragment) {

                        fragment = HistoryFragment()

                        toolbar.title = getString(R.string.history)

                        toolbar.navigationIcon = null

                        MainActivity.isLoadChargeDischarge = false

                        MainActivity.isLoadTools = false

                        MainActivity.isLoadHistory = true

                        MainActivity.isLoadSettings = false

                        MainActivity.isLoadDebug = false

                        clearMenu()

                        inflateMenu(-1)

                        loadFragment(fragment ?: HistoryFragment())

                        this.showInterstitialAd()
                    }
                }

                R.id.settings_navigation -> {

                    when (fragment) {

                        null, is BatteryInfoFragment, is ToolsFragment, is HistoryFragment -> {

                            fragment = SettingsFragment()

                            toolbar.title = getString(R.string.settings)

                            toolbar.navigationIcon = null

                            MainActivity.isLoadChargeDischarge = false

                            MainActivity.isLoadTools = false

                            MainActivity.isLoadSettings = true

                            MainActivity.isLoadDebug = false

                            clearMenu()

                            loadFragment(fragment ?: SettingsFragment())

                            this.showInterstitialAd()
                        }
                    }
                }
            }

            true
        }
    }

    fun MainActivity.loadFragment(fragment: Fragment, isAddToBackStack: Boolean = false) {

        supportFragmentManager.beginTransaction().apply {

            replace(R.id.fragment_container, fragment)
            if (isAddToBackStack) addToBackStack(null)

            if (!MainActivity.isRecreate || fragment is BatteryInfoFragment || fragment is ToolsFragment)
                commit()
        }

        when {

            fragment !is BatteryStatusInformationFragment && fragment !is PowerConnectionSettingsFragment && fragment !is OverlayFragment
                    && fragment !is AboutFragment && fragment !is DebugFragment
                    && fragment !is FeedbackFragment && fragment !is BackupSettingsFragment -> {

                navigation.selectedItemId = when (fragment) {

                    is BatteryInfoFragment -> R.id.charge_discharge_navigation
                    is ToolsFragment -> R.id.tools_navigation
                    is HistoryFragment -> R.id.history_navigation
                    is SettingsFragment -> R.id.settings_navigation
                    else -> R.id.charge_discharge_navigation
                }
            }

            else -> {

                navigation.selectedItemId = R.id.settings_navigation

                clearMenu()

                toolbar.navigationIcon = ContextCompat.getDrawable(
                    this,
                    R.drawable.ic_arrow_back_24dp
                )
            }
        }
    }
}