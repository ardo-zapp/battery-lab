package com.jacktor.batterylab

import android.Manifest
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.MainApp.Companion.isGooglePlay
import com.jacktor.batterylab.MainApp.Companion.isInstalledGooglePlay
import com.jacktor.batterylab.databinding.ActivityMainBinding
import com.jacktor.batterylab.fragments.AboutFragment
import com.jacktor.batterylab.fragments.BackupSettingsFragment
import com.jacktor.batterylab.fragments.BatteryInfoFragment
import com.jacktor.batterylab.fragments.BatteryStatusInformationFragment
import com.jacktor.batterylab.fragments.DebugFragment
import com.jacktor.batterylab.fragments.FeedbackFragment
import com.jacktor.batterylab.fragments.HistoryFragment
import com.jacktor.batterylab.fragments.OverlayFragment
import com.jacktor.batterylab.fragments.PowerConnectionSettingsFragment
import com.jacktor.batterylab.fragments.SettingsFragment
import com.jacktor.batterylab.fragments.ToolsFragment
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
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.services.OverlayService
import com.jacktor.batterylab.utilities.AdMob
import com.jacktor.batterylab.utilities.Constants
import com.jacktor.batterylab.utilities.Constants.IMPORT_RESTORE_SETTINGS_EXTRA
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
import com.jacktor.premium.utilities.Constants.BILLING_RESULT_CANCELLED
import com.jacktor.premium.utilities.Constants.BILLING_RESULT_FAILED
import com.jacktor.premium.utilities.Constants.BILLING_RESULT_RESTORE_FAILED
import com.jacktor.premium.utilities.Constants.BILLING_RESULT_RESTORE_SUCCESS
import com.jacktor.premium.utilities.Constants.BILLING_RESULT_SUCCESS
import com.jacktor.premium.utilities.PremiumFlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

class MainActivity : AppCompatActivity(),
    BatteryInfoInterface,
    SettingsInterface,
    com.jacktor.batterylab.interfaces.views.MenuInterface,
    ManufacturerInterface,
    NavigationInterface,
    CheckUpdateInterface,
    BatteryOptimizationsInterface {

    var pref: SharedPreferences? = null
    private var prefArrays: HashMap<*, *>? = null
    private var isRestoreImportSettings = false

    private var showRequestNotificationPermissionDialog: MaterialAlertDialogBuilder? = null
    var showFaqDialog: MaterialAlertDialogBuilder? = null
    var showXiaomiAutostartDialog: MaterialAlertDialogBuilder? = null
    var showHuaweiInformation: MaterialAlertDialogBuilder? = null
    var showRequestIgnoringBatteryOptimizationsDialog: AlertDialog? = null
    var showFailedRequestIgnoringBatteryOptimizationsDialog: AlertDialog? = null

    private var isDoubleBackToExitPressedOnce = false
    var isCheckUpdateFromGooglePlay = true
    var isShowRequestIgnoringBatteryOptimizationsDialog = true
    var isShowXiaomiBackgroundActivityControlDialog = false
    var isBatteryOptFlowActive = false

    private var firebaseAnalytics: FirebaseAnalytics? = null

    private lateinit var binding: ActivityMainBinding
    lateinit var toolbar: MaterialToolbar
    lateinit var navigation: BottomNavigationView
    var fragment: Fragment? = null

    private var interstitialAd: InterstitialAd? = null
    private var adsCounter = 0

    var isBatteryOptDialogVisible = false
    var hasBatteryOptJustGranted = false

    lateinit var premiumLauncher: ActivityResultLauncher<Intent>
    private val requestPostNotificationsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onPostNotificationsResult(granted)
        }

    val updateFlowResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { _ -> }

    companion object {
        var tempFragment: Fragment? = null
        var isLoadChargeDischarge = false
        var isLoadTools = false
        var isLoadHistory = false
        var isLoadSettings = false
        var isLoadDebug = false
        var isRecreate = false
        var isOnBackPressed = true
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
        if (isFirstLaunch) {
            val options = ActivityOptions.makeCustomAnimation(
                this, android.R.anim.fade_in, android.R.anim.fade_out
            )
            startActivity(Intent(this, OnboardingActivity::class.java), options.toBundle())
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        enableEdgeToEdge()

        mainActivityRef = WeakReference(this)

        initViewBinding()
        setupPremiumLauncher()
        initFirebase()
        loadPreferences()
        applyTheme()
        loadAdsIfNeeded()
        setupFragment()
        setupToolbar()
        setupBottomNavigation(getBatteryStatus())
        loadInitialFragment()
        handleBackPressed()
        checkNotificationPermissionOrBatteryOptimization()
    }

    private fun initViewBinding() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        toolbar = findViewById(R.id.toolbar)
        navigation = binding.navigation
    }

    private fun setupPremiumLauncher() {
        premiumLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                when (result.resultCode) {
                    BILLING_RESULT_SUCCESS -> {
                        Toast.makeText(
                            this,
                            getString(R.string.premium_purchase_success),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    BILLING_RESULT_CANCELLED -> {
                        Toast.makeText(
                            this,
                            getString(R.string.premium_purchase_cancelled),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    BILLING_RESULT_FAILED -> {
                        Toast.makeText(
                            this,
                            getString(R.string.premium_purchase_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    BILLING_RESULT_RESTORE_SUCCESS -> {
                        Toast.makeText(
                            this,
                            getString(R.string.premium_restore_success),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    BILLING_RESULT_RESTORE_FAILED -> {
                        Toast.makeText(
                            this,
                            getString(R.string.premium_restore_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
    }

    private fun initFirebase() {
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
    }

    private fun loadPreferences() {
        pref = PreferenceManager.getDefaultSharedPreferences(this)
        prefArrays =
            MainApp.getSerializable(this, IMPORT_RESTORE_SETTINGS_EXTRA, HashMap::class.java)
    }

    private fun applyTheme() {
        ThemeHelper.setTheme(this)
        MainApp.currentTheme = ThemeHelper.currentTheme(resources.configuration)
    }

    private fun loadAdsIfNeeded() {
        val premiumManager = (applicationContext as MainApp).billingManager
        lifecycleScope.launch {
            premiumManager.isPremium.combine(premiumManager.premiumStatusReady) { premium, ready ->
                Pair(premium, ready)
            }.collectLatest { _ ->
                loadInterstitialAd()
            }
        }
    }

    private fun setupFragment() {
        fragment = tempFragment ?: when {
            isLoadChargeDischarge || shouldLoadChargeDischarge() -> BatteryInfoFragment()
            isLoadHistory || shouldLoadHistory() -> HistoryFragment()
            isLoadTools || shouldLoadTools() -> ToolsFragment()
            shouldLoadBackupSettings() -> BackupSettingsFragment()
            shouldLoadDebug() -> DebugFragment()
            else -> SettingsFragment()
        }
    }

    private fun tabOnLaunch(): String? = pref?.getString(TAB_ON_APPLICATION_LAUNCH, "0")

    private fun shouldLoadChargeDischarge(): Boolean {
        val tab = tabOnLaunch()
        return tab != "1" && tab != "2" && prefArrays == null && !isLoadTools && !isLoadHistory &&
                !isLoadSettings && !isLoadDebug
    }

    private fun shouldLoadHistory(): Boolean {
        val tab = tabOnLaunch()
        return tab == "1" && prefArrays == null && !isLoadChargeDischarge && !isLoadTools &&
                !isLoadSettings && !isLoadDebug
    }

    private fun shouldLoadTools(): Boolean {
        val tab = tabOnLaunch()
        return tab == "2" && prefArrays == null && !isLoadChargeDischarge && !isLoadHistory &&
                !isLoadSettings && !isLoadDebug
    }

    private fun shouldLoadBackupSettings(): Boolean {
        return !isLoadChargeDischarge && !isLoadTools && !isLoadHistory &&
                !isLoadSettings && !isLoadDebug && prefArrays != null
    }

    private fun shouldLoadDebug(): Boolean {
        return (isLoadDebug && !isLoadChargeDischarge && !isLoadTools && !isLoadHistory &&
                !isLoadSettings && prefArrays == null) ||
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && prefArrays != null) ||
                (prefArrays != null && !isInstalledGooglePlay)
    }

    private fun setupToolbar() {
        toolbar.title = when (fragment) {
            is BatteryInfoFragment -> getString(R.string.battery_info)
            is ToolsFragment -> getString(R.string.tools)
            is HistoryFragment -> getString(R.string.history)
            is SettingsFragment -> getString(R.string.settings)
            is DebugFragment -> getString(R.string.debug)
            else -> getString(R.string.app_name)
        }
        toolbar.navigationIcon = null
        if (fragment !is SettingsFragment) inflateMenu(-1)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupBottomNavigation(status: Int) {
        bottomNavigation(status)
    }

    private fun loadInitialFragment() {
        if (!isRecreate || fragment !is SettingsFragment) {
            val isOverlayFragment = fragment is BatteryStatusInformationFragment ||
                    fragment is PowerConnectionSettingsFragment ||
                    fragment is BackupSettingsFragment ||
                    fragment is OverlayFragment ||
                    fragment is DebugFragment ||
                    fragment is AboutFragment ||
                    fragment is FeedbackFragment
            loadFragment(fragment ?: BatteryInfoFragment(), isOverlayFragment)
        }
    }

    private fun handleBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                backPressed()
            }
        })
    }

    private fun getBatteryStatus(): Int {
        batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    }

    override fun onResume() {
        super.onResume()

        if (needsPostNotifications()) return

        if (!isIgnoringBatteryOptimizations()) {
            isBatteryOptFlowActive = true
            hasBatteryOptJustGranted = false
            ensureBatteryOptDialog()
            return
        }

        isBatteryOptFlowActive = false
        isBatteryOptDialogVisible = false
        hasBatteryOptJustGranted = true
        checkManufacturer()

        logScreenView()
        handlePremiumStatus()
        handleTempFragment()
        resetRecreateFlag()
        updateBatteryStatusUI()
        ensureDesignCapacityValid()
        updateToolbarMenuIfNeeded()
        handleImportSettings()
        startMainServicesIfNeeded()
        checkNotificationPermissionOrBatteryOptimization()
        startOverlayIfEnabled()
        checkForUpdatesIfNeeded()
        maybeRequestRateTheApp()
        isShowRequestIgnoringBatteryOptimizationsDialog = true
    }

    private fun ensureBatteryOptDialog() {
        if (showRequestIgnoringBatteryOptimizationsDialog == null && !isBatteryOptDialogVisible) {
            isBatteryOptDialogVisible = true
            showRequestIgnoringBatteryOptimizationsDialog()
        }
    }

    private fun logScreenView() {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, localClassName)
            putString(FirebaseAnalytics.Param.SCREEN_NAME, TAG)
        }
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    private fun handlePremiumStatus() {
        lifecycleScope.launch {
            if (PremiumFlag.get(this@MainActivity)) {
                PremiumFlag.clearPremiumChanged(this@MainActivity)
                applyPremiumUI()
            }
        }
    }

    private fun handleTempFragment() {
        tempFragment = null
    }

    private fun resetRecreateFlag() {
        if (isRecreate) isRecreate = false
    }

    private fun updateBatteryStatusUI() {
        val status = getBatteryStatus()
        if (fragment !is BatteryInfoFragment) {
            navigation.menu.findItem(R.id.charge_discharge_navigation).apply {
                icon = ContextCompat.getDrawable(
                    this@MainActivity,
                    BatteryLevelHelper.batteryLevelIcon(
                        getBatteryLevel(this@MainActivity),
                        status == BatteryManager.BATTERY_STATUS_CHARGING
                    )
                )
            }
        }
        toolbar.title = when (fragment) {
            is BatteryInfoFragment -> getString(R.string.battery_info)
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
    }

    private fun ensureDesignCapacityValid() {
        val min = resources.getInteger(R.integer.min_design_capacity)
        val max = resources.getInteger(R.integer.max_design_capacity)
        val current = pref?.getInt(DESIGN_CAPACITY, min) ?: min
        if (!pref?.contains(DESIGN_CAPACITY)!! || current < min || current > max) {
            pref?.edit { putInt(DESIGN_CAPACITY, getDesignCapacity(this@MainActivity)) }
        }
    }

    private fun updateToolbarMenuIfNeeded() {
        if (fragment is BatteryInfoFragment) {
            toolbar.menu.findItem(R.id.instruction).isVisible = getCurrentCapacity(this) > 0.0
        }
    }

    private fun handleImportSettings() {
        val imported =
            MainApp.getSerializable(this, IMPORT_RESTORE_SETTINGS_EXTRA, HashMap::class.java)
        if (imported != null) importSettings(imported)
    }

    private fun startMainServicesIfNeeded() {
        ServiceHelper.startService(this, BatteryLabService::class.java)
    }

    private fun needsPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
    }

    private fun checkNotificationPermissionOrBatteryOptimization() {
        if (needsPostNotifications()) {
            requestNotificationPermission()
            return
        }
        if (!isIgnoringBatteryOptimizations()) {
            ensureBatteryOptDialog()
            return
        }
        isBatteryOptFlowActive = false
        isBatteryOptDialogVisible = false
        hasBatteryOptJustGranted = true
        checkManufacturer()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        if (showRequestNotificationPermissionDialog != null) return
        showRequestNotificationPermissionDialog =
            createPermissionDialog(
                messageRes = R.string.request_notification_message,
                onConfirm = {
                    showRequestNotificationPermissionDialog = null
                    requestPostNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
    }

    @Suppress("SameParameterValue")
    private fun createPermissionDialog(
        messageRes: Int,
        onConfirm: () -> Unit
    ): MaterialAlertDialogBuilder {
        return MaterialAlertDialogBuilder(this).apply {
            setIcon(R.drawable.ic_instruction_not_supported_24dp)
            setTitle(R.string.information)
            setMessage(messageRes)
            setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }
            setCancelable(false)
            show()
        }
    }

    private fun startOverlayIfEnabled() {
        if (pref!!.getBoolean(ENABLED_OVERLAY, resources.getBoolean(R.bool.enabled_overlay)) &&
            OverlayService.instance == null &&
            !ServiceHelper.isStartedOverlayService()
        ) {
            ServiceHelper.startService(this, OverlayService::class.java)
        }
    }

    private fun checkForUpdatesIfNeeded() {
        if (isInstalledGooglePlay && isGooglePlay(this) && isCheckUpdateFromGooglePlay) {
            checkUpdateFromGooglePlay()
        }
    }

    private fun maybeRequestRateTheApp() {
        val numberOfFullCharges = pref!!.getLong(NUMBER_OF_FULL_CHARGES, 0)
        val shouldRequest = isInstalledGooglePlay &&
                isGooglePlay(this) &&
                numberOfFullCharges > 0 &&
                numberOfFullCharges % 3 == 0L &&
                pref!!.getBoolean(
                    IS_REQUEST_RATE_THE_APP,
                    resources.getBoolean(R.bool.is_request_rate_the_app)
                )
        if (shouldRequest) requestRateTheApp()
    }

    private fun requestRateTheApp() {
        Snackbar.make(
            toolbar,
            getString(R.string.do_you_like_the_app),
            Snackbar.LENGTH_LONG
        ).setAction(getString(R.string.rate_the_app)) {
            openPlayStoreForRating()
        }.show()
    }

    private fun openPlayStoreForRating() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Constants.GOOGLE_PLAY_APP_LINK.toUri()))
            pref?.edit { putBoolean(IS_REQUEST_RATE_THE_APP, false) }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this@MainActivity, getString(R.string.unknown_error), Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onStop() {
        showRequestIgnoringBatteryOptimizationsDialog = null
        super.onStop()
    }

    override fun onDestroy() {
        clearMainActivityRef()
        cleanupDialogs()
        resetStateIfNotRecreate()
        super.onDestroy()
    }

    private fun clearMainActivityRef() {
        mainActivityRef?.clear()
        fragment = null
    }

    private fun cleanupDialogs() {
        showFaqDialog = null
    }

    private fun resetStateIfNotRecreate() {
        if (!isRecreate) {
            tempFragment = null
            isLoadChargeDischarge = false
            isLoadTools = false
            isLoadHistory = false
            isLoadSettings = false
            isLoadDebug = false
        }
    }

    private fun importSettings(prefArrays: HashMap<*, *>?) {
        val prefsToImport = listOf(
            BATTERY_LEVEL_TO, BATTERY_LEVEL_WITH, DESIGN_CAPACITY,
            CAPACITY_ADDED, LAST_CHARGE_TIME, PERCENT_ADDED, RESIDUAL_CAPACITY
        )
        prefArrays?.let {
            prefsToImport.forEach { key ->
                if (!it.containsKey(key)) {
                    pref?.edit { remove(key)?.apply() }
                } else {
                    importSinglePref(key, it)
                }
            }
        }
        toolbar.menu.clear()
        isRestoreImportSettings = true
        this.prefArrays = null
        intent.removeExtra(IMPORT_RESTORE_SETTINGS_EXTRA)
    }

    @Suppress("unused")
    private fun importSinglePref(key: String, source: Map<*, *>) {
        source.forEach {
            when (it.key as String) {
                NUMBER_OF_CHARGES -> pref?.edit()?.putLong(it.key as String, it.value as Long)
                    ?.apply()

                BATTERY_LEVEL_TO, BATTERY_LEVEL_WITH, LAST_CHARGE_TIME,
                DESIGN_CAPACITY, RESIDUAL_CAPACITY, PERCENT_ADDED ->
                    pref?.edit()?.putInt(it.key as String, it.value as Int)?.apply()

                CAPACITY_ADDED, NUMBER_OF_CYCLES ->
                    pref?.edit { putFloat(it.key as String, it.value as Float)?.apply() }
            }
        }
    }

    fun backPressed() {
        if (!isOnBackPressed) return
        when {
            shouldPopBackStackToSettings() -> navigateToSettingsFragment()
            shouldResetToSettings() -> resetToSettingsFragment()
            else -> handleDoubleBackToExit()
        }
    }

    private fun shouldPopBackStackToSettings(): Boolean {
        return toolbar.title != getString(R.string.settings) &&
                !isRestoreImportSettings &&
                ((fragment != null && fragment !is SettingsFragment &&
                        fragment !is BatteryInfoFragment && fragment !is ToolsFragment &&
                        fragment !is HistoryFragment && fragment !is DebugFragment &&
                        fragment !is BackupSettingsFragment) ||
                        ((fragment is BackupSettingsFragment || fragment is DebugFragment) &&
                                supportFragmentManager.backStackEntryCount > 0))
    }

    private fun shouldResetToSettings(): Boolean {
        return (toolbar.title != getString(R.string.settings) &&
                fragment is BackupSettingsFragment &&
                supportFragmentManager.backStackEntryCount == 0) || isRestoreImportSettings
    }

    private fun navigateToSettingsFragment() {
        fragment = SettingsFragment()
        toolbar.title =
            getString(if (fragment !is DebugFragment) R.string.settings else R.string.debug)
        toolbar.navigationIcon = null
        supportFragmentManager.popBackStack()
    }

    private fun resetToSettingsFragment() {
        fragment = SettingsFragment()
        toolbar.title = getString(R.string.settings)
        toolbar.navigationIcon = null
        isRestoreImportSettings = false
        loadFragment(fragment ?: SettingsFragment())
    }

    private fun handleDoubleBackToExit() {
        if (isDoubleBackToExitPressedOnce) {
            finish()
        } else {
            isDoubleBackToExitPressedOnce = true
            Toast.makeText(this, R.string.press_the_back_button_again, Toast.LENGTH_LONG).show()
            CoroutineScope(Dispatchers.Main).launch {
                delay(3.seconds)
                isDoubleBackToExitPressedOnce = false
            }
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, AdMob.AD_UNIT_ID, adRequest, adLoadCallback())
    }

    private fun adLoadCallback(): InterstitialAdLoadCallback {
        return object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                if (adsCounter >= 3) showInterstitialAd()
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                interstitialAd = null
            }
        }
    }

    fun showInterstitialAd(force: Boolean = false) {
        val premiumManager = (applicationContext as MainApp).billingManager
        val isPremiumUser = premiumManager.isPremium.value
        val premiumReady = premiumManager.premiumStatusReady.value
        if (isPremiumUser && premiumReady) return
        adsCounter++
        if ((adsCounter >= 3 || force) && !isPremiumUser && premiumReady && interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = adContentCallback()
            interstitialAd?.show(this)
        }
    }

    private fun adContentCallback(): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitialAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {}
            override fun onAdShowedFullScreenContent() {
                adsCounter = 0
                interstitialAd = null
            }
        }
    }

    private fun applyPremiumUI() {
        navigation.selectedItemId = R.id.charge_discharge_navigation
        invalidateOptionsMenu()
        recreate()
    }

    @Suppress("unused")
    private fun onPostNotificationsResult(granted: Boolean) {
        isBatteryOptFlowActive = false
        if (!isIgnoringBatteryOptimizations()) {
            ensureBatteryOptDialog()
        } else {
            checkManufacturer()
        }
    }
}
