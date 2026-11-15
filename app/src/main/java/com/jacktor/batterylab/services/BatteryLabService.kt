package com.jacktor.batterylab.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainApp
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.MainApp.Companion.isPowerConnected
import com.jacktor.batterylab.R
import com.jacktor.batterylab.adapters.HistoryAdapter
import com.jacktor.batterylab.databases.History
import com.jacktor.batterylab.databases.HistoryDB
import com.jacktor.batterylab.fragments.BatteryInfoFragment
import com.jacktor.batterylab.fragments.HistoryFragment
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import com.jacktor.batterylab.helpers.DateHelper
import com.jacktor.batterylab.helpers.HistoryHelper
import com.jacktor.batterylab.helpers.IdleDrainHelper
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.capacityAdded
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.coulombAddedMah
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.lastSessionPluggedType
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.lastSessionStatus
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.maxChargeCurrent
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.percentAdded
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.tempBatteryLevelWith
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.tempAvailableCapacity
import com.jacktor.batterylab.interfaces.NavigationInterface.Companion.mainActivityRef
import com.jacktor.batterylab.interfaces.NotificationInterface
import com.jacktor.batterylab.interfaces.NotificationInterface.Companion.isBatteryCharged
import com.jacktor.batterylab.interfaces.NotificationInterface.Companion.isBatteryDischarged
import com.jacktor.batterylab.interfaces.NotificationInterface.Companion.isBatteryDischargedVoltage
import com.jacktor.batterylab.interfaces.NotificationInterface.Companion.isBatteryFullyCharged
import com.jacktor.batterylab.interfaces.NotificationInterface.Companion.isOverheatOvercool
import com.jacktor.batterylab.interfaces.NotificationInterface.Companion.notificationBuilder
import com.jacktor.batterylab.interfaces.NotificationInterface.Companion.notificationManager
import com.jacktor.batterylab.receivers.PluggedReceiver
import com.jacktor.batterylab.receivers.PowerConnectionReceiver
import com.jacktor.batterylab.receivers.UnpluggedReceiver
import com.jacktor.batterylab.utilities.Constants.NOMINAL_BATTERY_VOLTAGE
import com.jacktor.batterylab.utilities.Constants.NOTIFY_FULL_CHARGE_REMINDER_JOB_ID
import com.jacktor.batterylab.utilities.Constants.SERVICE_WAKELOCK_TIMEOUT
import com.jacktor.batterylab.utilities.Receiver
import com.jacktor.batterylab.utilities.preferences.PreferenceChangeListener
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_NOTIFY_CHARGED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_NOTIFY_DISCHARGED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_TO
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_LEVEL_WITH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BATTERY_NOTIFY_DISCHARGED_VOLTAGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_COULOMB_ADDED_MAH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_PLUGGED_TYPE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_START_CAPACITY_MAH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CHARGING_START_LEVEL
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DESIGN_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.FULL_CHARGE_REMINDER_FREQUENCY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IS_ENABLE_WAKELOCK
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.LAST_CHARGE_TIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_BATTERY_IS_CHARGED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_BATTERY_IS_DISCHARGED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_BATTERY_IS_DISCHARGED_VOLTAGE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_BATTERY_IS_FULLY_CHARGED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_OVERHEAT_OVERCOOL
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CHARGES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_FULL_CHARGES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERCOOL_DEGREES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERHEAT_DEGREES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.PERCENT_ADDED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.POWER_CONNECTION_SERVICE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.RESIDUAL_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UNIT_OF_MEASUREMENT_OF_available_capacity
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UPDATE_TEMP_SCREEN_TIME
import com.jacktor.batterylab.security.NativeCore
import com.jacktor.premium.Premium
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class BatteryLabService : Service(), NotificationInterface, BatteryInfoInterface {

    private lateinit var pref: SharedPreferences
    private lateinit var powerConnectionReceiver: PowerConnectionReceiver
    private var pluggedReceiver: PluggedReceiver? = null
    private var unpluggedReceiver: UnpluggedReceiver? = null

    private var screenTimeJob: Job? = null
    private var jobService: Job? = null
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isScreenTimeJob = false
    private var isJob = false
    var availableCapacity = 0
    private var isReceiverRegistered = false

    var isFull = false
    var isStopService = false
    var isSaveNumberOfCharges = true
    var isPluggedOrUnplugged = false
    var batteryLevelWith = -1
    var seconds = 0
    private var lastCoulombTickMs = 0L
    private var screenTimeBase: Long = 0L
    private var screenOnStartTime: Long = -1L

    @Synchronized
    fun pauseScreenTime() {
        if (screenOnStartTime > 0L) {
            val elapsed = (SystemClock.elapsedRealtime() - screenOnStartTime) / 1000L
            screenTimeBase += elapsed
            screenOnStartTime = -1L
        }
    }

    @Synchronized
    fun resumeScreenTime() {
        val pm = powerManager ?: (getSystemService(POWER_SERVICE) as? PowerManager)
        val isInteractive = pm?.isInteractive ?: false
        if (isInteractive && !isPowerConnected && screenOnStartTime <= 0L) {
            screenOnStartTime = SystemClock.elapsedRealtime()
        }
    }

    var screenTime: Long
        @Synchronized
        get() {
            val base = screenTimeBase
            val start = screenOnStartTime
            val pm = powerManager ?: (getSystemService(POWER_SERVICE) as? PowerManager)
            val isInteractive = pm?.isInteractive ?: false
            return if (isInteractive && !isPowerConnected && start > 0L) {
                val elapsed = (SystemClock.elapsedRealtime() - start) / 1000L
                base + elapsed
            } else {
                base
            }
        }
        @Synchronized
        set(value) {
            screenTimeBase = value
            val pm = powerManager ?: (getSystemService(POWER_SERVICE) as? PowerManager)
            val isInteractive = pm?.isInteractive ?: false
            screenOnStartTime = if (isInteractive && !isPowerConnected) {
                SystemClock.elapsedRealtime()
            } else {
                -1L
            }
        }
    var secondsFullCharge = 0
    private var isPremium = false

    private val screenAwakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val context = ctx ?: applicationContext
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    pauseScreenTime()
                    IdleDrainHelper.onScreenOff(context)
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT, Intent.ACTION_USER_UNLOCKED -> {
                    resumeScreenTime()
                    IdleDrainHelper.onScreenOn(context)
                }
            }
            try {
                onUpdateServiceNotification(applicationContext)
            } catch (_: Throwable) {
                onCreateServiceNotification(applicationContext)
            }
        }
    }

    private val preferenceChangeListener =
        PreferenceChangeListener { prefs, key ->
            if (key == POWER_CONNECTION_SERVICE) {
                val isEnabled = prefs.getBoolean(key, false)
                if (isEnabled) registerPowerConnectionReceiver()
                else unregisterPowerConnectionReceiver()
            }
        }

    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: BatteryLabService? = null
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        if (instance == null) {
            super.onCreate()
            instance = this
            pref = PreferenceManager.getDefaultSharedPreferences(this)
            if (pref.contains("is_fast_charge_setting")) {
                pref.edit { remove("is_fast_charge_setting") }
            }
            pref.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

            // Register PowerConnectionReceiver
            powerConnectionReceiver = PowerConnectionReceiver()

            batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

            val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            isPowerConnected = isPlugged(plugged)

            if (isPowerConnected) {
                val currentLevel = getBatteryLevel(applicationContext) ?: 0
                val savedStart = pref.getInt(CHARGING_START_LEVEL, -1)
                val startLevel = if (savedStart in 0..currentLevel) savedStart else currentLevel
                val savedStartCap = pref.getFloat(CHARGING_START_CAPACITY_MAH, 0f).toDouble()
                val restoredStartCap = if (savedStart in 0..currentLevel && savedStartCap > 1.0) {
                    savedStartCap
                } else {
                    getAvailableCapacity(applicationContext)
                }
                pref.edit {
                    putInt(CHARGING_START_LEVEL, startLevel)
                    if (isPlugged(plugged)) putInt(CHARGING_PLUGGED_TYPE, plugged)
                    if (savedStart !in 0..currentLevel || savedStartCap <= 1.0) {
                        putFloat(CHARGING_START_CAPACITY_MAH, restoredStartCap.toFloat())
                        putFloat(CHARGING_COULOMB_ADDED_MAH, 0f)
                    }
                }
                batteryLevelWith = startLevel
                tempBatteryLevelWith = startLevel
                tempAvailableCapacity = restoredStartCap
                lastSessionPluggedType = if (isPlugged(plugged)) plugged else 0
                coulombAddedMah = pref.getFloat(CHARGING_COULOMB_ADDED_MAH, 0f)
                    .toDouble().coerceAtLeast(0.0)
                lastCoulombTickMs = SystemClock.elapsedRealtime()

                val status = batteryIntent?.getIntExtra(
                    BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN
                ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
                lastSessionStatus = status

                if (mainActivityRef?.get()?.fragment != null) {
                    if (mainActivityRef?.get()?.fragment is BatteryInfoFragment)
                        mainActivityRef?.get()?.toolbar?.title =
                            getString(R.string.battery_info)

                    val chargeDischargeNavigation =
                        mainActivityRef?.get()?.navigation?.menu?.findItem(R.id.charge_discharge_navigation)

                    chargeDischargeNavigation?.icon = BatteryLevelHelper.batteryLevelIcon(
                        getBatteryLevel(applicationContext),
                        status == BatteryManager.BATTERY_STATUS_CHARGING
                    ).let { ContextCompat.getDrawable(applicationContext, it) }
                }
            }

            val initialScreenTime = if (MainApp.tempScreenTime > 0L) MainApp.tempScreenTime
            else if (MainApp.isUpdateApp) pref.getLong(UPDATE_TEMP_SCREEN_TIME, 0L)
            else 0L

            screenTime = initialScreenTime

            MainApp.tempScreenTime = 0L
            MainApp.isUpdateApp = false
            pref.apply {
                if (contains(UPDATE_TEMP_SCREEN_TIME)) edit {
                    remove(
                        UPDATE_TEMP_SCREEN_TIME
                    )
                }
            }

            pluggedReceiver = PluggedReceiver().also {
                applicationContext.registerReceiver(
                    it,
                    IntentFilter(Intent.ACTION_POWER_CONNECTED)
                )
            }
            unpluggedReceiver = UnpluggedReceiver().also {
                applicationContext.registerReceiver(
                    it,
                    IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
                )
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_USER_UNLOCKED)
            }

            ContextCompat.registerReceiver(
                this,
                screenAwakeReceiver,
                filter, ContextCompat.RECEIVER_NOT_EXPORTED
            )

            IdleDrainHelper.syncWithSystemState(applicationContext)

            onCreateServiceNotification(applicationContext)

        }
    }

    fun refreshPremiumStatus() {
        isPremium = NativeCore.hasPremiumAccess(this, NativeCore.FEATURE_SERVICE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Premium
        refreshPremiumStatus()
        Premium.revalidate(this) //Revalidate

        // PowerConnectionReceiver on/off
        val powerConnectionService = pref.getBoolean(POWER_CONNECTION_SERVICE, false)
        if (!isReceiverRegistered && powerConnectionService) registerPowerConnectionReceiver()
        else if (isReceiverRegistered && !powerConnectionService) unregisterPowerConnectionReceiver()

        // Job screen time
        if (screenTimeJob == null) screenTimeJob = CoroutineScope(Dispatchers.Default).launch {
            isScreenTimeJob = !isScreenTimeJob
            var lastInteractiveState: Boolean? = null
            while (isScreenTimeJob && !isStopService) {
                val pm = powerManager ?: (getSystemService(POWER_SERVICE) as? PowerManager)
                powerManager = pm
                val interactive = pm?.isInteractive ?: false
                if (interactive && !isPowerConnected) {
                    resumeScreenTime()
                } else {
                    pauseScreenTime()
                }
                if (lastInteractiveState != interactive) {
                    lastInteractiveState = interactive
                    IdleDrainHelper.syncWithSystemState(applicationContext)
                }
                delay(1000L)
            }
        }

        if (jobService == null) jobService = CoroutineScope(Dispatchers.Default).launch {
            isJob = !isJob
            while (isJob && !isStopService) {
                if (instance == null) instance = this@BatteryLabService

                val useWakeLock = pref.getBoolean(
                    IS_ENABLE_WAKELOCK,
                    resources.getBoolean(R.bool.is_enable_wakelock)
                )
                if (useWakeLock) {
                    if (powerManager == null)
                        powerManager = getSystemService(POWER_SERVICE) as PowerManager
                    if (wakeLock == null)
                        wakeLock = powerManager?.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "${packageName}:service_wakelock"
                        )

                    if (wakeLock?.isHeld != true && !isFull && isPowerConnected) {
                        try {
                            wakeLock?.acquire(SERVICE_WAKELOCK_TIMEOUT)
                        } catch (_: Throwable) {
                        }
                    }
                } else {
                    wakeLockRelease()
                }

                if (isPowerConnected) {
                    getBatteryLevel(applicationContext)?.let { lvl ->
                        if (batteryLevelWith < 0) {
                            val savedStart = pref.getInt(CHARGING_START_LEVEL, -1)
                            batteryLevelWith = if (savedStart in 0..lvl) savedStart else lvl
                            pref.edit {
                                putInt(CHARGING_START_LEVEL, batteryLevelWith)
                            }
                        } else if (lvl < batteryLevelWith) {
                            batteryLevelWith = lvl
                            pref.edit {
                                putInt(CHARGING_START_LEVEL, lvl)
                            }
                        }
                    }
                }

                batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val status = batteryIntent?.getIntExtra(
                    BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN
                )
                val pluggedNow = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                if (isPlugged(pluggedNow)) {
                    lastSessionPluggedType = pluggedNow
                    if (pref.getInt(CHARGING_PLUGGED_TYPE, 0) != pluggedNow) {
                        pref.edit { putInt(CHARGING_PLUGGED_TYPE, pluggedNow) }
                    }
                }
                if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                ) {
                    lastSessionStatus = status
                }
                if (isPlugged(pluggedNow) && !isPowerConnected) {
                    isPowerConnected = true
                    val lvl = (getBatteryLevel(applicationContext) ?: 0).coerceIn(0, 100)
                    val savedStart = pref.getInt(CHARGING_START_LEVEL, -1)
                    val startLvl = if (savedStart in 0..lvl) savedStart else lvl
                    val savedStartCap = pref.getFloat(CHARGING_START_CAPACITY_MAH, 0f).toDouble()
                    val startCap = if (savedStart in 0..lvl && savedStartCap > 1.0) savedStartCap
                    else getAvailableCapacity(applicationContext)
                    batteryLevelWith = startLvl
                    tempBatteryLevelWith = startLvl
                    tempAvailableCapacity = startCap
                    if (lastCoulombTickMs == 0L) lastCoulombTickMs = SystemClock.elapsedRealtime()
                    pref.edit {
                        putInt(CHARGING_START_LEVEL, startLvl)
                        putInt(CHARGING_PLUGGED_TYPE, pluggedNow)
                        putFloat(CHARGING_START_CAPACITY_MAH, startCap.toFloat())
                    }
                }

                val temperature = getTemperatureInCelsius(applicationContext)

                if (!isPluggedOrUnplugged) {
                    BatteryInfoInterface.maximumTemperature =
                        getMaximumTemperature(
                            applicationContext,
                            BatteryInfoInterface.maximumTemperature
                        )
                    BatteryInfoInterface.minimumTemperature =
                        getMinimumTemperature(
                            applicationContext,
                            BatteryInfoInterface.minimumTemperature
                        )
                    BatteryInfoInterface.averageTemperature =
                        getAverageTemperature(
                            applicationContext,
                            BatteryInfoInterface.maximumTemperature,
                            BatteryInfoInterface.minimumTemperature
                        )
                }

                // Overheat/overcool
                if (pref.getBoolean(
                        NOTIFY_OVERHEAT_OVERCOOL,
                        resources.getBoolean(R.bool.notify_overheat_overcool)
                    ) &&
                    (temperature >= pref.getInt(
                        OVERHEAT_DEGREES,
                        resources.getInteger(R.integer.overheat_degrees_default)
                    ) ||
                            temperature <= pref.getInt(
                        OVERCOOL_DEGREES,
                        resources.getInteger(R.integer.overcool_degrees_default)
                    ))
                ) withContext(Dispatchers.Main) {
                    onNotifyOverheatOvercool(
                        applicationContext,
                        temperature
                    )
                }

                when {
                    status == BatteryManager.BATTERY_STATUS_CHARGING && !isStopService && secondsFullCharge < 3600 -> batteryCharging()
                    status == BatteryManager.BATTERY_STATUS_FULL && isPowerConnected && !isFull && !isStopService -> batteryCharged()
                    !isStopService -> {
                        // Discharged by level
                        if (pref.getBoolean(
                                NOTIFY_BATTERY_IS_DISCHARGED,
                                resources.getBoolean(R.bool.notify_battery_is_discharged)
                            ) &&
                            (getBatteryLevel(applicationContext) ?: 0) <= pref.getInt(
                                BATTERY_LEVEL_NOTIFY_DISCHARGED,
                                20
                            )
                        ) withContext(Dispatchers.Main) {
                            onNotifyBatteryDischarged(
                                applicationContext
                            )
                        }

                        // Discharged by voltage
                        if (pref.getBoolean(
                                NOTIFY_BATTERY_IS_DISCHARGED_VOLTAGE,
                                resources.getBoolean(R.bool.notify_battery_is_discharged_voltage)
                            )
                        ) {
                            val voltage = getVoltage(applicationContext)
                            if (voltage <= pref.getInt(
                                    BATTERY_NOTIFY_DISCHARGED_VOLTAGE,
                                    resources.getInteger(R.integer.battery_notify_discharged_voltage_min)
                                )
                            ) withContext(Dispatchers.Main) {
                                onNotifyBatteryDischargedVoltage(
                                    applicationContext,
                                    voltage.toInt()
                                )
                            }
                        }

                        // Update notif
                        withContext(Dispatchers.Main) {
                            try {
                                onUpdateServiceNotification(applicationContext)
                            } catch (_: NullPointerException) {
                                onCreateServiceNotification(applicationContext)
                            } finally {
                                delay(1.5.seconds)
                            }
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        pauseScreenTime()
        try {
            unregisterReceiver(screenAwakeReceiver)
        } catch (_: Throwable) {
        }
        try {
            pluggedReceiver?.let { applicationContext.unregisterReceiver(it) }
            pluggedReceiver = null
        } catch (_: Throwable) {
        }
        try {
            unpluggedReceiver?.let { applicationContext.unregisterReceiver(it) }
            unpluggedReceiver = null
        } catch (_: Throwable) {
        }
        pref.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)

        if (isReceiverRegistered) Receiver.unregister(this, powerConnectionReceiver)

        instance = null
        isScreenTimeJob = false
        isJob = false
        screenTimeJob?.cancel()
        jobService?.cancel()
        screenTimeJob = null
        jobService = null
        notificationBuilder = null

        isOverheatOvercool = false
        isBatteryFullyCharged = false
        isBatteryCharged = false
        isBatteryDischarged = false
        isBatteryDischargedVoltage = false

        MainApp.isUpdateApp = false

        val batteryLevel = getBatteryLevel(applicationContext) ?: 0
        if (!isStopService) MainApp.tempScreenTime = screenTime

        val savedStart = pref.getInt(CHARGING_START_LEVEL, -1)
        val startLevel = if (batteryLevelWith in 0..batteryLevel) batteryLevelWith
        else if (savedStart in 0..batteryLevel) savedStart
        else pref.getInt(BATTERY_LEVEL_WITH, batteryLevel)

        val numberOfCycles = when {
            batteryLevel == startLevel -> pref.getFloat(NUMBER_OF_CYCLES, 0f) + 0.01f
            batteryLevel > startLevel && startLevel >= 0 ->
                pref.getFloat(NUMBER_OF_CYCLES, 0f) + (batteryLevel / 100f) - (startLevel / 100f)
            else -> pref.getFloat(NUMBER_OF_CYCLES, 0f)
        }

        notificationManager?.cancelAll()

        if (!::pref.isInitialized) pref =
            PreferenceManager.getDefaultSharedPreferences(applicationContext)

        if (!isFull && seconds > 1) {
            BatteryInfoInterface.saveLastChargeSnapshot(
                this, applicationContext, pref, seconds, startLevel, batteryLevel, false
            )
            pref.edit {
                putInt(LAST_CHARGE_TIME, seconds)
                putInt(BATTERY_LEVEL_WITH, startLevel)
                putInt(BATTERY_LEVEL_TO, batteryLevel)
            }
            val finalCap = kotlin.math.max(capacityAdded, coulombAddedMah)
            if (finalCap > 0) pref.edit { putFloat(CAPACITY_ADDED, finalCap.toFloat()) }
            if (percentAdded > 0) pref.edit { putInt(PERCENT_ADDED, percentAdded) }
            if (coulombAddedMah > 0) pref.edit {
                putFloat(CHARGING_COULOMB_ADDED_MAH, coulombAddedMah.toFloat())
            }
            if (isSaveNumberOfCharges) pref.edit { putFloat(NUMBER_OF_CYCLES, numberOfCycles) }
            percentAdded = 0
            capacityAdded = 0.0
            coulombAddedMah = 0.0
        }

        lastCoulombTickMs = 0L

        BatteryInfoInterface.batteryLevel = 0
        BatteryInfoInterface.tempBatteryLevel = 0

        if (isStopService) Toast.makeText(
            applicationContext,
            R.string.service_stopped_successfully,
            Toast.LENGTH_LONG
        ).show()

        ServiceHelper.cancelJob(applicationContext, NOTIFY_FULL_CHARGE_REMINDER_JOB_ID)
        super.onDestroy()
        wakeLockRelease()
    }

    private suspend fun batteryCharging() {
        val chargeCurrentMa = getChargeDischargeCurrent(applicationContext)
        updateAutoFastChargeState(applicationContext)

        val nowMs = SystemClock.elapsedRealtime()
        if (lastCoulombTickMs > 0L && chargeCurrentMa in 50..25_000) {
            val dtMs = (nowMs - lastCoulombTickMs).coerceIn(0L, 5000L)
            if (dtMs > 0L) {
                coulombAddedMah += chargeCurrentMa.toDouble() * (dtMs / 3_600_000.0)
            }
        }
        lastCoulombTickMs = nowMs

        val batteryLevel = getBatteryLevel(applicationContext) ?: 0

        if (batteryLevel == 100) {
            if (!isFull && secondsFullCharge >= 120) batteryCharged()
            availableCapacity = (getAvailableCapacity(applicationContext) *
                    if (pref.getString(
                            UNIT_OF_MEASUREMENT_OF_available_capacity,
                            "μAh"
                        ) == "μAh"
                    ) 1000.0 else 1.0
                    ).toInt()
            secondsFullCharge++
        } else {
            if (secondsFullCharge > 0) secondsFullCharge = 0
        }

        if (seconds % 5 == 0 && (coulombAddedMah > 0.0 || capacityAdded > 0.0 || percentAdded > 0)) {
            val bestAdded = kotlin.math.max(capacityAdded, coulombAddedMah)
            pref.edit {
                if (coulombAddedMah > 0.0) putFloat(CHARGING_COULOMB_ADDED_MAH, coulombAddedMah.toFloat())
                if (bestAdded > 0.0) putFloat(CAPACITY_ADDED, bestAdded.toFloat())
                if (percentAdded > 0) putInt(PERCENT_ADDED, percentAdded)
            }
        }


        if (pref.getBoolean(
                NOTIFY_BATTERY_IS_CHARGED,
                resources.getBoolean(R.bool.notify_battery_is_charged)
            ) &&
            (getBatteryLevel(applicationContext) ?: 0) == pref.getInt(
                BATTERY_LEVEL_NOTIFY_CHARGED,
                80
            )
        ) withContext(Dispatchers.Main) { onNotifyBatteryCharged(applicationContext) }

        delay(0.95.seconds)
        seconds++

        withContext(Dispatchers.Main) {
            try {
                onUpdateServiceNotification(applicationContext)
            } catch (_: Throwable) {
                onCreateServiceNotification(applicationContext)
            }
        }
    }

    private suspend fun batteryCharged() {
        withContext(Dispatchers.Main) {
            val freq = pref.getString(
                FULL_CHARGE_REMINDER_FREQUENCY,
                "${resources.getInteger(R.integer.full_charge_reminder_frequency_default)}"
            )?.toInt()
            ServiceHelper.jobSchedule(
                applicationContext,
                FullChargeReminderJobService::class.java,
                NOTIFY_FULL_CHARGE_REMINDER_JOB_ID,
                (freq?.minutes?.inWholeMilliseconds)
                    ?: resources.getInteger(R.integer.full_charge_reminder_frequency_default).minutes.inWholeMilliseconds
            )
        }

        isFull = true

        val isMicroAh = pref.getString(UNIT_OF_MEASUREMENT_OF_available_capacity, "μAh") == "μAh"
        val factor = if (isMicroAh) 1000.0 else 1.0

        if (availableCapacity == 0) {
            availableCapacity = (getAvailableCapacity(applicationContext) * factor).toInt()
        }

        val designCapacity =
            pref.getInt(DESIGN_CAPACITY, resources.getInteger(R.integer.min_design_capacity))
                .toDouble() * factor

        val residualCapacity = if (availableCapacity > 0) availableCapacity else designCapacity.toInt()
        val residualCapacityAverage = getResidualCapacityAverage(applicationContext, residualCapacity)

        val currentDate = DateHelper.getDate(
            DateHelper.getCurrentDay(),
            DateHelper.getCurrentMonth(),
            DateHelper.getCurrentYear()
        )

        if (pref.getBoolean(
                NOTIFY_BATTERY_IS_FULLY_CHARGED,
                resources.getBoolean(R.bool.notify_battery_is_fully_charged)
            )
        ) {
            withContext(Dispatchers.Main) { onNotifyBatteryFullyCharged(applicationContext) }
        }

        val batteryLevel = getBatteryLevel(applicationContext) ?: 0
        val savedStart = pref.getInt(CHARGING_START_LEVEL, -1)
        val startLevel = if (batteryLevelWith in 0..batteryLevel) batteryLevelWith
        else if (savedStart in 0..batteryLevel) savedStart
        else pref.getInt(BATTERY_LEVEL_WITH, batteryLevel)

        val numberOfCycles = when {
            batteryLevel == startLevel -> pref.getFloat(NUMBER_OF_CYCLES, 0f) + 0.01f
            batteryLevel > startLevel && startLevel >= 0 ->
                pref.getFloat(NUMBER_OF_CYCLES, 0f) + (batteryLevel / 100f) - (startLevel / 100f)
            else -> pref.getFloat(NUMBER_OF_CYCLES, 0f)
        }

        val finalAddedPct = if (percentAdded > 0) percentAdded else (batteryLevel - startLevel).coerceAtLeast(0)
        val finalAddedCap = kotlin.math.max(capacityAdded, coulombAddedMah)
        capacityAdded = finalAddedCap
        percentAdded = finalAddedPct

        pref.apply {
            val numberOfCharges = getLong(NUMBER_OF_CHARGES, 0)
            if (seconds > 1) edit { putLong(NUMBER_OF_CHARGES, numberOfCharges + 1) }

            BatteryInfoInterface.saveLastChargeSnapshot(
                this@BatteryLabService, applicationContext, this,
                seconds, startLevel, batteryLevel, true
            )

            edit {
                putInt(LAST_CHARGE_TIME, seconds)
                putInt(RESIDUAL_CAPACITY, residualCapacityAverage)
                putBoolean(BatteryInfoInterface.RESIDUAL_DUAL_CELL_VERIFIED_PREF, true)
                putInt(BATTERY_LEVEL_WITH, startLevel)
                putInt(BATTERY_LEVEL_TO, batteryLevel)
                putLong(NUMBER_OF_FULL_CHARGES, getLong(NUMBER_OF_FULL_CHARGES, 0) + 1)
                putFloat(CAPACITY_ADDED, finalAddedCap.toFloat())
                putInt(PERCENT_ADDED, finalAddedPct)
            }
            if (isSaveNumberOfCharges) edit { putFloat(NUMBER_OF_CYCLES, numberOfCycles) }
        }

        withContext(Dispatchers.Main) {
            if (residualCapacityAverage > 0 && seconds >= 10) {
                withContext(Dispatchers.IO) {
                    HistoryHelper.addHistory(
                        applicationContext,
                        currentDate,
                        residualCapacityAverage
                    )
                }
            }
            HistoryFragment.instance?.updateHistoryUI()
        }

        isSaveNumberOfCharges = false

        withContext(Dispatchers.Main) {
            try {
                onUpdateServiceNotification(applicationContext)
            } catch (_: Throwable) {
                onCreateServiceNotification(applicationContext)
            } finally {
                wakeLockRelease()
            }
        }
    }

    fun wakeLockRelease() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: RuntimeException) {
        }
    }

    fun registerPowerConnectionReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        Receiver.register(this, powerConnectionReceiver, filter)
        isReceiverRegistered = true
    }

    fun unregisterPowerConnectionReceiver() {
        Receiver.unregister(this, powerConnectionReceiver)
        isReceiverRegistered = false
    }
}
