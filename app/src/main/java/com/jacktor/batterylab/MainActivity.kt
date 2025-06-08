package com.jacktor.batterylab

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.MainApp.Companion.isGooglePlay
import com.jacktor.batterylab.MainApp.Companion.isInstalledGooglePlay
import com.jacktor.batterylab.databinding.ActivityMainBinding
import com.jacktor.batterylab.fragments.AboutFragment
import com.jacktor.batterylab.fragments.BackupSettingsFragment
import com.jacktor.batterylab.fragments.BatteryStatusInformationFragment
import com.jacktor.batterylab.fragments.ChargeDischargeFragment
import com.jacktor.batterylab.fragments.DebugFragment
import com.jacktor.batterylab.fragments.FeedbackFragment
import com.jacktor.batterylab.fragments.HistoryFragment
import com.jacktor.batterylab.fragments.ToolsFragment
import com.jacktor.batterylab.fragments.OverlayFragment
import com.jacktor.batterylab.fragments.PowerConnectionSettingsFragment
import com.jacktor.batterylab.fragments.SettingsFragment
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.helpers.ThemeHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.interfaces.BatteryOptimizationsInterface
import com.jacktor.batterylab.interfaces.CheckUpdateInterface
import com.jacktor.batterylab.interfaces.ManufacturerInterface
import com.jacktor.batterylab.interfaces.NavigationInterface
import com.jacktor.batterylab.interfaces.NavigationInterface.Companion.mainActivityRef
import com.jacktor.batterylab.interfaces.SettingsInterface
import com.jacktor.batterylab.interfaces.views.MenuInterface
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.services.OverlayService
import com.jacktor.batterylab.utilities.Constants
import com.jacktor.batterylab.utilities.Constants.IMPORT_RESTORE_SETTINGS_EXTRA
import com.jacktor.batterylab.utilities.Constants.POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AUTO_START_OPEN_APP
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_TO
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_WITH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DESIGN_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.ENABLED_OVERLAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IS_REQUEST_RATE_THE_APP
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.LAST_CHARGE_TIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CHARGES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_FULL_CHARGES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.PERCENT_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.RESIDUAL_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.TAB_ON_APPLICATION_LAUNCH
import com.jacktor.batterylab.utilities.preferences.Prefs
import com.jacktor.batterylab.views.CenteredTopAppBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class MainActivity() : AppCompatActivity(), BatteryInfoInterface, SettingsInterface,
    MenuInterface, ManufacturerInterface, NavigationInterface,
    CheckUpdateInterface, BatteryOptimizationsInterface {

    var pref: Prefs? = null
    private var isDoubleBackToExitPressedOnce = false
    private var isRestoreImportSettings = false
    private var prefArrays: HashMap<*, *>? = null
    private var showRequestNotificationPermissionDialog: MaterialAlertDialogBuilder? = null
    var showFaqDialog: MaterialAlertDialogBuilder? = null
    var showXiaomiAutostartDialog: MaterialAlertDialogBuilder? = null
    var showHuaweiInformation: MaterialAlertDialogBuilder? = null
    var showRequestIgnoringBatteryOptimizationsDialog: MaterialAlertDialogBuilder? = null


    val updateFlowResultLauncher = registerForActivityResult(
        ActivityResultContracts
            .StartIntentSenderForResult()
    ) { _ -> }

    var isCheckUpdateFromGooglePlay = true
    var isShowRequestIgnoringBatteryOptimizationsDialog = true
    var isShowXiaomiBackgroundActivityControlDialog = false

    private lateinit var binding: ActivityMainBinding
    lateinit var topAppBar: CenteredTopAppBar
    lateinit var navigation: BottomNavigationView

    var fragment: Fragment? = null

    private var adsCounter = 0

    companion object {
        var tempFragment: Fragment? = null
        var isLoadChargeDischarge = false
        var isLoadKernel = false
        var isLoadHistory = false
        var isLoadSettings = false
        var isLoadDebug = false
        var isRecreate = false
        var isOnBackPressed = true

        private var TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //throw RuntimeException("Test Crash") // Force a crash
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pref = Prefs(this)

        ThemeHelper.setTheme(this)

        MainApp.Companion.currentTheme = ThemeHelper.currentTheme(resources.configuration)

        fragment = tempFragment


        batteryIntent = registerReceiver(
            null, IntentFilter(
                Intent.ACTION_BATTERY_CHANGED
            )
        )

        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        topAppBar = findViewById(R.id.topAppBar)
        navigation = binding.navigation

        prefArrays = MainApp.Companion.getSerializable(
            this, IMPORT_RESTORE_SETTINGS_EXTRA,
            HashMap::class.java
        )

        if (fragment == null)

            fragment = when {
                isLoadChargeDischarge || (pref!!.getString(TAB_ON_APPLICATION_LAUNCH, "0")
                        != "1" && pref!!.getString(TAB_ON_APPLICATION_LAUNCH, "0") != "2"
                        && prefArrays == null && !isLoadKernel && !isLoadHistory && !isLoadSettings
                        && !isLoadDebug) -> ChargeDischargeFragment()

                isLoadHistory || (pref!!.getString(TAB_ON_APPLICATION_LAUNCH, "0") == "1"
                        /*&& HistoryHelper.isHistoryNotEmpty(this)*/
                        && prefArrays == null && !isLoadChargeDischarge && !isLoadHistory &&
                        !isLoadSettings && !isLoadDebug) -> HistoryFragment()

                isLoadKernel || (pref!!.getString(TAB_ON_APPLICATION_LAUNCH, "0") == "2" &&
                        prefArrays == null && !isLoadChargeDischarge && !isLoadHistory &&
                        !isLoadSettings && !isLoadDebug) ->
                    ToolsFragment()

                !isLoadChargeDischarge && !isLoadKernel &&
                        !isLoadHistory && !isLoadSettings && !isLoadDebug && prefArrays != null ->
                    BackupSettingsFragment()

                (isLoadDebug && !isLoadChargeDischarge && !isLoadKernel && !isLoadHistory
                        && !isLoadSettings
                        && prefArrays == null) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                        && prefArrays != null) || (Build.VERSION
                    .SDK_INT >= Build.VERSION_CODES.R && prefArrays != null && !isInstalledGooglePlay) -> DebugFragment()

                else -> SettingsFragment()
            }

        topAppBar.title = when (fragment) {

            is ChargeDischargeFragment -> getString(
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) R.string.charge
                else R.string.discharge
            )

            is ToolsFragment -> getString(R.string.tools)
            is HistoryFragment -> getString(R.string.history)
            is SettingsFragment -> getString(R.string.settings)
            is DebugFragment -> getString(R.string.debug)
            else -> getString(R.string.app_name)
        }

        topAppBar.navigationIcon = null

        if (fragment !is SettingsFragment) inflateMenu(-1)

        topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        bottomNavigation(status)



        if (!isRecreate || fragment !is SettingsFragment)
            loadFragment(
                fragment ?: ChargeDischargeFragment(), fragment is
                        BatteryStatusInformationFragment || fragment is PowerConnectionSettingsFragment || fragment is BackupSettingsFragment
                        || fragment is OverlayFragment || fragment is DebugFragment ||
                        fragment is AboutFragment || fragment is FeedbackFragment
            )

        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    backPressed()
                }
            })

    }

    override fun onResume() {

        super.onResume()

        tempFragment = null

        if (isRecreate) isRecreate = false

        batteryIntent = registerReceiver(
            null, IntentFilter(
                Intent.ACTION_BATTERY_CHANGED
            )
        )

        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        if (fragment !is ChargeDischargeFragment) {

            navigation.menu.findItem(R.id.charge_discharge_navigation).title = getString(
                if (
                    status == BatteryManager.BATTERY_STATUS_CHARGING) R.string.charge else
                    R.string.discharge
            )

            navigation.menu.findItem(R.id.charge_discharge_navigation).icon = ContextCompat
                .getDrawable(
                    this, BatteryLevelHelper.batteryLevelIcon(
                        getBatteryLevel(this),
                        status == BatteryManager.BATTERY_STATUS_CHARGING
                    )
                )
        }

        topAppBar.title = when (fragment) {

            is ChargeDischargeFragment -> getString(
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) R.string.charge
                else R.string.discharge
            )

            is HistoryFragment -> getString(R.string.history)
            is ToolsFragment -> getString(R.string.tools)
            is SettingsFragment -> getString(R.string.settings)
            is BatteryStatusInformationFragment -> getString(R.string.battery_status_information)
            is PowerConnectionSettingsFragment -> getString(R.string.power_connection)
            is OverlayFragment -> getString(R.string.overlay)
            is AboutFragment -> getString(R.string.about)
            is FeedbackFragment -> getString(R.string.feedback)
            is DebugFragment -> getString(R.string.debug)
            is BackupSettingsFragment -> getString(R.string.backup)
            else -> getString(R.string.app_name)
        }

        if (!pref?.contains(DESIGN_CAPACITY)!! ||
            pref?.getInt(DESIGN_CAPACITY, resources.getInteger(R.integer.min_design_capacity))!! <
            resources.getInteger(R.integer.min_design_capacity) || pref?.getInt(
                DESIGN_CAPACITY,
                resources.getInteger(R.integer.min_design_capacity)
            )!! > resources.getInteger(
                R.integer.max_design_capacity
            )
        ) {

            pref!!.setInt(DESIGN_CAPACITY, getDesignCapacity(this@MainActivity))
        }

        if (showRequestNotificationPermissionDialog == null) checkManufacturer()


        if (fragment is ChargeDischargeFragment)
            topAppBar.menu.findItem(R.id.instruction).isVisible = getCurrentCapacity(
                this
            ) > 0.0


        val prefArrays = MainApp.Companion.getSerializable(
            this, IMPORT_RESTORE_SETTINGS_EXTRA,
            HashMap::class.java
        )

        if (prefArrays != null) importSettings(prefArrays)

        ServiceHelper.startService(this, BatteryLabService::class.java)

        if (pref!!.getBoolean(
                AUTO_START_OPEN_APP, resources.getBoolean(
                    R.bool
                        .auto_start_open_app
                )
            ) &&  BatteryLabService.instance == null &&
            !ServiceHelper.isStartedBatteryLabService()
        )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat
                    .checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_DENIED
            )
                requestNotificationPermission()
            else checkBatteryOptimizations()

        if (pref!!.getBoolean(ENABLED_OVERLAY, resources.getBoolean(R.bool.enabled_overlay))
            && OverlayService.instance == null && !ServiceHelper.isStartedOverlayService()
        )
            ServiceHelper.startService(this, OverlayService::class.java)

        if (isInstalledGooglePlay && isGooglePlay(this) && isCheckUpdateFromGooglePlay)
            checkUpdateFromGooglePlay()

        val numberOfFullCharges = pref!!.getLong(NUMBER_OF_FULL_CHARGES, 0)
        if ((isInstalledGooglePlay && isGooglePlay(this) &&
                    numberOfFullCharges > 0 && numberOfFullCharges % 3 == 0L) &&
            pref!!.getBoolean(
                IS_REQUEST_RATE_THE_APP,
                resources.getBoolean(R.bool.is_request_rate_the_app)
            )
        ) requestRateTheApp()

        isShowRequestIgnoringBatteryOptimizationsDialog = true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {

        super.onConfigurationChanged(newConfig)

        val newTheme = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK or
                newConfig.uiMode and Configuration.UI_MODE_NIGHT_YES or
                newConfig.uiMode and Configuration.UI_MODE_NIGHT_NO

        if (newTheme != MainApp.Companion.currentTheme) {

            tempFragment = fragment

            isRecreate = true

            recreate()
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>,
        grantResults: IntArray
    ) {

        when (requestCode) {
            POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE ->
                if ((grantResults.isNotEmpty() && grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED) || (grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_DENIED)
                ) checkManufacturer()

            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onStop() {
        showRequestIgnoringBatteryOptimizationsDialog = null
        //Toast.makeText(this, "${BatteryLabService.instance}", Toast.LENGTH_LONG).show()
        super.onStop()
    }

    override fun onDestroy() {
        mainActivityRef?.clear()
        fragment = null

        showFaqDialog = null

        if (!isRecreate) {

            tempFragment = null

            isLoadChargeDischarge = false
            isLoadKernel = false
            isLoadHistory = false
            isLoadSettings = false
            isLoadDebug = false
        }

        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        if (showRequestNotificationPermissionDialog == null)
            showRequestNotificationPermissionDialog =
                MaterialAlertDialogBuilder(this).apply {
                    setIcon(R.drawable.ic_instruction_not_supported_24dp)
                    setTitle(R.string.information)
                    setMessage(R.string.request_notification_message)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        requestPermissions(
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE
                        )

                        showRequestNotificationPermissionDialog = null
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(2.5.seconds)
                            checkBatteryOptimizations()
                        }
                    }
                    setCancelable(false)
                    show()
                }
    }

    private fun checkBatteryOptimizations() {
        if (showRequestNotificationPermissionDialog == null) checkManufacturer()

        if (!isIgnoringBatteryOptimizations() && !isShowXiaomiBackgroundActivityControlDialog
            && isShowRequestIgnoringBatteryOptimizationsDialog &&
            showRequestIgnoringBatteryOptimizationsDialog == null &&
            showXiaomiAutostartDialog == null && showHuaweiInformation == null
        )
            showRequestIgnoringBatteryOptimizationsDialog()
    }

    private fun importSettings(prefArrays: HashMap<*, *>?) {

        val prefsTempList = arrayListOf(
            BATTERY_LEVEL_TO,
            BATTERY_LEVEL_WITH,
            DESIGN_CAPACITY,
            CAPACITY_ADDED,
            LAST_CHARGE_TIME,
            PERCENT_ADDED,
            RESIDUAL_CAPACITY
        )

        if (prefArrays != null)
            prefsTempList.forEach {

                with(prefArrays) {

                    when {

                        !containsKey(it) -> pref?.remove(it)

                        else -> {

                            forEach {

                                when (it.key as String) {

                                    NUMBER_OF_CHARGES -> pref?.setLong(
                                        it.key as String,
                                        it.value as Long
                                    )

                                    BATTERY_LEVEL_TO, BATTERY_LEVEL_WITH, LAST_CHARGE_TIME,
                                    DESIGN_CAPACITY, RESIDUAL_CAPACITY, PERCENT_ADDED ->
                                        pref?.setInt(it.key as String, it.value as Int)

                                    CAPACITY_ADDED, NUMBER_OF_CYCLES ->
                                        pref?.setFloat(
                                            it.key as String,
                                            it.value as Float
                                        )
                                }
                            }
                        }
                    }
                }
            }

        topAppBar.menu.clear()

        isRestoreImportSettings = true

        this.prefArrays = null

        intent.removeExtra(IMPORT_RESTORE_SETTINGS_EXTRA)
    }

    fun backPressed() {
        if (isOnBackPressed) {
            if (topAppBar.title != getString(R.string.settings) && !isRestoreImportSettings && ((fragment != null
                        && fragment !is SettingsFragment && fragment !is ChargeDischargeFragment
                        && fragment !is ToolsFragment && fragment !is HistoryFragment &&
                        fragment !is DebugFragment && fragment !is BackupSettingsFragment) || ((
                        fragment is BackupSettingsFragment || fragment is DebugFragment) &&
                        supportFragmentManager.backStackEntryCount > 0))
            ) {

                fragment = SettingsFragment()

                topAppBar.title = getString(
                    if (fragment !is DebugFragment) R.string.settings
                    else R.string.debug
                )

                if (fragment is SettingsFragment) topAppBar.navigationIcon = null

                supportFragmentManager.popBackStack()
            } else if (topAppBar.title != getString(R.string.settings) &&
                (fragment is BackupSettingsFragment && supportFragmentManager.backStackEntryCount == 0)
                || isRestoreImportSettings
            ) {

                fragment = SettingsFragment()

                topAppBar.title = getString(R.string.settings)

                topAppBar.navigationIcon = null

                isRestoreImportSettings = false

                loadFragment(fragment ?: SettingsFragment())
            } else {

                if (isDoubleBackToExitPressedOnce) finish()
                else {

                    isDoubleBackToExitPressedOnce = true

                    Toast.makeText(
                        this@MainActivity, R.string.press_the_back_button_again,
                        Toast.LENGTH_LONG
                    ).show()

                    CoroutineScope(Dispatchers.Main).launch {

                        delay(3.seconds)
                        isDoubleBackToExitPressedOnce = false
                    }
                }
            }
        }
    }

    private fun requestRateTheApp() {
        Snackbar.make(
            topAppBar, getString(R.string.do_you_like_the_app),
            Snackbar.LENGTH_LONG
        ).apply {
            setAction(getString(R.string.rate_the_app)) {
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(Constants.GOOGLE_PLAY_APP_LINK)
                        )
                    )
                    pref!!.setBoolean(IS_REQUEST_RATE_THE_APP, false)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        this@MainActivity, getString(
                            R.string.unknown_error
                        ), Toast.LENGTH_LONG
                    ).show()
                }
            }
            show()
        }
    }
}
