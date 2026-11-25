package com.jacktor.batterylab

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
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
import com.jacktor.batterylab.helpers.SetupPermissionHelper
import com.jacktor.batterylab.helpers.ThemeHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.interfaces.BatteryOptimizationsInterface
import com.jacktor.batterylab.interfaces.CheckUpdateInterface
import com.jacktor.batterylab.interfaces.ManufacturerInterface
import com.jacktor.batterylab.interfaces.NavigationInterface
import com.jacktor.batterylab.interfaces.NavigationInterface.Companion.mainActivityRef
import com.jacktor.batterylab.interfaces.SettingsInterface
import com.jacktor.batterylab.security.NativeCore
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.services.OverlayService
import com.jacktor.batterylab.utilities.AdMob
import com.jacktor.batterylab.utilities.ApplicationBackup
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
import com.jacktor.premium.Premium
import com.jacktor.premium.utilities.Constants.BILLING_RESULT_CANCELLED
import com.jacktor.premium.utilities.Constants.BILLING_RESULT_FAILED
import com.jacktor.premium.utilities.Constants.BILLING_RESULT_RESTORE_FAILED
import com.jacktor.premium.utilities.Constants.BILLING_RESULT_RESTORE_SUCCESS
import com.jacktor.premium.utilities.Constants.BILLING_RESULT_SUCCESS
import com.jacktor.premium.utilities.PremiumFlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
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
    private var isAdLoading = false
    private var adRetryAttempt = 0
    private var adRetryJob: Job? = null
    private var isConsentRequested = false

    var isBatteryOptDialogVisible = false
    var hasBatteryOptJustGranted = false

    lateinit var premiumLauncher: ActivityResultLauncher<Intent>

    val updateFlowResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { _ -> }

    companion object {
        private const val AD_COOLDOWN_MS = 45_000L
        private const val AD_LAUNCH_GRACE_PERIOD_MS = 10_000L
        private const val AD_COUNTER_THRESHOLD = 3
        private const val AD_BILLING_FALLBACK_MS = 15_000L
        private const val AD_MAX_RETRY_ATTEMPTS = 3
        private var adsCounter = 0
        private var appLaunchTimestamp: Long = 0L
        private var lastInterstitialAdShownTime: Long = 0L
        private val isMobileAdsInitStarted = AtomicBoolean(false)

        @Volatile
        private var isMobileAdsInitialized = false
        private val adsInitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var tempFragment: Fragment? = null
        var isLoadChargeDischarge = false
        var isLoadTools = false
        var isLoadHistory = false
        var isLoadSettings = false
        var isLoadDebug = false
        var isRecreate = false
        var isOnBackPressed = true
        private var hasShownTamperDialog = false
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
        val fromSetupWizard =
            intent?.getBooleanExtra(SetupWizardActivity.EXTRA_FROM_SETUP_WIZARD, false) == true
        intent?.removeExtra(SetupWizardActivity.EXTRA_FROM_SETUP_WIZARD)

        if (isFirstLaunch) {
            val options = ActivityOptions.makeCustomAnimation(
                this, android.R.anim.fade_in, android.R.anim.fade_out
            )
            startActivity(Intent(this, OnboardingActivity::class.java), options.toBundle())
            finish()
            return
        }

        if (savedInstanceState == null &&
            !fromSetupWizard &&
            SetupPermissionHelper.hasMissingEssentialPermissions(this)
        ) {
            val options = ActivityOptions.makeCustomAnimation(
                this, android.R.anim.fade_in, android.R.anim.fade_out
            )
            startActivity(
                Intent(this, SetupWizardActivity::class.java).apply {
                    putExtra(SetupWizardActivity.EXTRA_MISSING_ONLY, true)
                },
                options.toBundle()
            )
            finish()
            return
        }

        if (appLaunchTimestamp == 0L) {
            appLaunchTimestamp = android.os.SystemClock.elapsedRealtime()
        }

        enableEdgeToEdge()

        mainActivityRef = WeakReference(this)

        initViewBinding()
        setupPremiumLauncher()
        initFirebase()
        loadPreferences()
        applyTheme()
        loadAdsIfNeeded()
        val shortcutNavId = setupFragment(intent)
        setupToolbar()
        setupBottomNavigation(getBatteryStatus())
        shortcutNavId?.let { navigation.menu.findItem(it)?.isChecked = true }
        loadInitialFragment()
        handleBackPressed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
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
                        val reason = result.data
                            ?.getStringExtra(com.jacktor.premium.utilities.Constants.EXTRA_BILLING_ERROR_REASON)
                            ?.takeIf { it.isNotBlank() }
                        Toast.makeText(
                            this,
                            reason ?: getString(R.string.premium_purchase_failed),
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

    private fun isVerifiedPremiumForAds(): Boolean =
        Premium.premiumStatusReady().value &&
                NativeCore.hasPremiumAccess(this, NativeCore.FEATURE_ADS)

    private fun loadAdsIfNeeded() {
        lifecycleScope.launch {
            Premium.isPremium().combine(Premium.premiumStatusReady()) { premium, ready ->
                Pair(premium, ready)
            }.collectLatest { (_, ready) ->
                BatteryLabService.instance?.refreshPremiumStatus()
                invalidateOptionsMenu()
                if (!ready || !NativeCore.hasPremiumAccess(this@MainActivity, NativeCore.FEATURE_ADS)) {
                    requestConsentAndInitializeAds()
                } else {
                    adRetryJob?.cancel()
                    adRetryJob = null
                    interstitialAd = null
                    isAdLoading = false
                }
            }
        }
    }

    private fun requestConsentAndInitializeAds() {
        if (isFinishing || isDestroyed || isVerifiedPremiumForAds()) return

        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        if (consentInformation.canRequestAds()) {
            initializeMobileAdsSdk()
        }

        if (isConsentRequested) return
        isConsentRequested = true

        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                if (isFinishing || isDestroyed) return@requestConsentInfoUpdate
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                    if (formError != null) {
                        Log.w(
                            TAG,
                            "UMP consent form error (${formError.errorCode}): ${formError.message}"
                        )
                    }
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAdsSdk()
                    }
                }
            },
            { formError ->
                Log.w(
                    TAG,
                    "UMP consent info update failed (${formError.errorCode}): ${formError.message}"
                )
                isConsentRequested = false
                if (consentInformation.canRequestAds()) {
                    initializeMobileAdsSdk()
                }
            }
        )
    }

    private fun initializeMobileAdsSdk() {
        if (isMobileAdsInitialized) {
            loadInterstitialAdIfNeeded()
            return
        }
        if (isMobileAdsInitStarted.compareAndSet(false, true)) {
            adsInitScope.launch {
                MobileAds.initialize(applicationContext) {
                    isMobileAdsInitialized = true
                    mainActivityRef?.get()?.let { act ->
                        act.runOnUiThread {
                            if (!act.isFinishing && !act.isDestroyed) {
                                act.loadInterstitialAdIfNeeded()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupFragment(launchIntent: Intent?): Int? {
        val shortcutNavId = applyShortcutDestination(launchIntent)
        if (shortcutNavId != null) return shortcutNavId

        fragment = tempFragment ?: when {
            isLoadChargeDischarge || shouldLoadChargeDischarge() -> BatteryInfoFragment()
            isLoadHistory || shouldLoadHistory() -> HistoryFragment()
            isLoadTools || shouldLoadTools() -> ToolsFragment()
            shouldLoadBackupSettings() -> BackupSettingsFragment()
            shouldLoadDebug() -> DebugFragment()
            else -> SettingsFragment()
        }
        return null
    }

    private fun applyShortcutDestination(targetIntent: Intent?): Int? {
        return when (targetIntent?.action) {
            Constants.SHORTCUT_ACTION_BATTERY_INFO -> {
                ShortcutManagerCompat.reportShortcutUsed(this, Constants.SHORTCUT_ID_BATTERY_INFO)
                isLoadChargeDischarge = true
                isLoadTools = false
                isLoadHistory = false
                isLoadSettings = false
                isLoadDebug = false
                fragment = BatteryInfoFragment()
                R.id.charge_discharge_navigation
            }

            Constants.SHORTCUT_ACTION_IDLE_DRAIN -> {
                ShortcutManagerCompat.reportShortcutUsed(this, Constants.SHORTCUT_ID_IDLE_DRAIN)
                isLoadChargeDischarge = false
                isLoadTools = true
                isLoadHistory = false
                isLoadSettings = false
                isLoadDebug = false
                fragment = ToolsFragment.newInstance(0)
                R.id.tools_navigation
            }

            Constants.SHORTCUT_ACTION_STRESS_IMPEDANCE -> {
                isLoadChargeDischarge = false
                isLoadTools = true
                isLoadHistory = false
                isLoadSettings = false
                isLoadDebug = false
                fragment = ToolsFragment.newInstance(1)
                R.id.tools_navigation
            }

            Constants.SHORTCUT_ACTION_APP_POWER -> {
                ShortcutManagerCompat.reportShortcutUsed(this, Constants.SHORTCUT_ID_APP_POWER)
                isLoadChargeDischarge = false
                isLoadTools = true
                isLoadHistory = false
                isLoadSettings = false
                isLoadDebug = false
                fragment = ToolsFragment.newInstance(2)
                R.id.tools_navigation
            }

            Constants.SHORTCUT_ACTION_HISTORY -> {
                ShortcutManagerCompat.reportShortcutUsed(this, Constants.SHORTCUT_ID_HISTORY)
                isLoadChargeDischarge = false
                isLoadTools = false
                isLoadHistory = true
                isLoadSettings = false
                isLoadDebug = false
                fragment = HistoryFragment()
                R.id.history_navigation
            }

            else -> null
        }
    }

    private fun handleShortcutIntent(targetIntent: Intent?) {
        val action = targetIntent?.action ?: return
        if (!::navigation.isInitialized) return

        val toolsTabIndex = when (action) {
            Constants.SHORTCUT_ACTION_IDLE_DRAIN -> 0
            Constants.SHORTCUT_ACTION_STRESS_IMPEDANCE -> 1
            Constants.SHORTCUT_ACTION_APP_POWER -> 2
            else -> -1
        }

        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        val currentFragment = fragment
        if (toolsTabIndex >= 0 && currentFragment is ToolsFragment) {
            val shortcutId = when (toolsTabIndex) {
                0 -> Constants.SHORTCUT_ID_IDLE_DRAIN
                2 -> Constants.SHORTCUT_ID_APP_POWER
                else -> null
            }
            shortcutId?.let { ShortcutManagerCompat.reportShortcutUsed(this, it) }
            currentFragment.selectTab(toolsTabIndex)
            navigation.menu.findItem(R.id.tools_navigation)?.isChecked = true
            toolbar.title = getString(R.string.tools)
            toolbar.navigationIcon = null
            return
        }

        val navId = applyShortcutDestination(targetIntent) ?: return
        navigation.menu.findItem(navId)?.isChecked = true
        setupToolbar()
        clearMenu()
        if (fragment !is SettingsFragment) inflateMenu(-1)
        loadFragment(fragment ?: BatteryInfoFragment())
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
            is HistoryFragment -> com.jacktor.batterylab.helpers.HistoryHelper.getHistoryTitle(this)
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

        isBatteryOptFlowActive = false
        isBatteryOptDialogVisible = false
        hasBatteryOptJustGranted = isIgnoringBatteryOptimizations()

        logScreenView()
        handlePremiumStatus()
        warnIfAppTampered()
        handleTempFragment()
        resetRecreateFlag()
        updateBatteryStatusUI()
        ensureDesignCapacityValid()
        updateToolbarMenuIfNeeded()
        handleImportSettings()
        startMainServicesIfNeeded()
        startOverlayIfEnabled()
        checkForUpdatesIfNeeded()
        maybeRequestRateTheApp()
        ensureAdsReadyOnResume()
        isShowRequestIgnoringBatteryOptimizationsDialog = true
    }

    private fun ensureAdsReadyOnResume() {
        if (isVerifiedPremiumForAds()) return
        if (!isMobileAdsInitialized) {
            requestConsentAndInitializeAds()
        } else if (interstitialAd == null && !isAdLoading) {
            adRetryAttempt = 0
            loadInterstitialAdIfNeeded()
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        tempFragment = fragment
        isRecreate = true
        recreate()
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
            is HistoryFragment -> com.jacktor.batterylab.helpers.HistoryHelper.getHistoryTitle(this)
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
        val prefs = pref ?: return
        val min = resources.getInteger(R.integer.min_design_capacity)
        val max = resources.getInteger(R.integer.max_design_capacity)
        val current = prefs.getInt(DESIGN_CAPACITY, min)
        if (!prefs.contains(DESIGN_CAPACITY) || current < min || current > max) {
            prefs.edit { putInt(DESIGN_CAPACITY, getDesignCapacity(this@MainActivity)) }
        }
    }

    private fun updateToolbarMenuIfNeeded() {
        if (fragment is BatteryInfoFragment) {
            toolbar.menu.findItem(R.id.instruction)?.isVisible = getAvailableCapacity(this) > 0.0
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

    private fun warnIfAppTampered() {
        if (hasShownTamperDialog) return
        if (NativeCore.isAppTampered(this)) {
            hasShownTamperDialog = true
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tamper_warning_title)
                .setMessage(R.string.tamper_warning_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun startOverlayIfEnabled() {
        if (NativeCore.hasPremiumAccess(this, NativeCore.FEATURE_OVERLAY) &&
            pref!!.getBoolean(ENABLED_OVERLAY, resources.getBoolean(R.bool.enabled_overlay)) &&
            OverlayService.instance == null &&
            !ServiceHelper.isStartedOverlayService()
        ) {
            ServiceHelper.startService(this, OverlayService::class.java)
        }
    }

    private fun checkForUpdatesIfNeeded() {
        val enabledCheckUpdate = pref?.getBoolean(
            com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IS_ENABLE_CHECK_UPDATE,
            resources.getBoolean(R.bool.is_enable_check_update)
        ) ?: true
        if (enabledCheckUpdate && isInstalledGooglePlay && isGooglePlay(this) && isCheckUpdateFromGooglePlay) {
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
        adRetryJob?.cancel()
        adRetryJob = null
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
        ApplicationBackup.restorePreservedPrefs(pref, prefArrays)
        toolbar.menu.clear()
        isRestoreImportSettings = true
        this.prefArrays = null
        intent.removeExtra(IMPORT_RESTORE_SETTINGS_EXTRA)
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

    private fun loadInterstitialAdIfNeeded() {
        if (isFinishing || isDestroyed || interstitialAd != null || isAdLoading || !isMobileAdsInitialized) {
            return
        }
        if (isVerifiedPremiumForAds()) return
        if (!UserMessagingPlatform.getConsentInformation(this).canRequestAds()) return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, AdMob.AD_UNIT_ID, adRequest, adLoadCallback())
    }

    private fun adLoadCallback(): InterstitialAdLoadCallback {
        return object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                isAdLoading = false
                adRetryAttempt = 0
                adRetryJob?.cancel()
                adRetryJob = null
                interstitialAd = if (isVerifiedPremiumForAds()) null else ad
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                isAdLoading = false
                interstitialAd = null
                Log.w(TAG, "Interstitial ad failed to load (${adError.code}): ${adError.message}")
                if (adRetryAttempt < AD_MAX_RETRY_ATTEMPTS && !isFinishing && !isDestroyed) {
                    adRetryAttempt++
                    val delayMs = (5_000L shl (adRetryAttempt - 1)).coerceAtMost(30_000L)
                    adRetryJob?.cancel()
                    adRetryJob = lifecycleScope.launch {
                        delay(delayMs)
                        loadInterstitialAdIfNeeded()
                    }
                }
            }
        }
    }

    fun showInterstitialAd(force: Boolean = false) {
        if (isFinishing || isDestroyed) return
        val premiumReady = Premium.premiumStatusReady().value
        val suppressAds = NativeCore.shouldSuppressAds(this, premiumReady)
        if (suppressAds) return

        adsCounter++
        val now = android.os.SystemClock.elapsedRealtime()
        val elapsedSinceLaunch = if (appLaunchTimestamp > 0L) now - appLaunchTimestamp else 0L
        val isGracePassed = elapsedSinceLaunch >= AD_LAUNCH_GRACE_PERIOD_MS
        val isCooldownPassed =
            lastInterstitialAdShownTime == 0L || (now - lastInterstitialAdShownTime) >= AD_COOLDOWN_MS
        val isBillingClear =
            premiumReady || (!Premium.isPremium().value && elapsedSinceLaunch >= AD_BILLING_FALLBACK_MS)

        if (interstitialAd == null) {
            if (!isMobileAdsInitialized) {
                requestConsentAndInitializeAds()
            } else if (!isAdLoading) {
                adRetryAttempt = 0
                loadInterstitialAdIfNeeded()
            }
            return
        }

        if ((adsCounter >= AD_COUNTER_THRESHOLD || force) &&
            (isGracePassed || force) &&
            (isCooldownPassed || force) &&
            !suppressAds &&
            isBillingClear
        ) {
            val adToShow = interstitialAd ?: return
            interstitialAd = null
            adsCounter = 0
            adToShow.fullScreenContentCallback = adContentCallback()
            adToShow.show(this)
        }
    }

    private fun adContentCallback(): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitialAdIfNeeded()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Interstitial ad failed to show (${adError.code}): ${adError.message}")
                interstitialAd = null
                loadInterstitialAdIfNeeded()
            }

            override fun onAdShowedFullScreenContent() {
                lastInterstitialAdShownTime = android.os.SystemClock.elapsedRealtime()
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
}
