package com.jacktor.batterylab.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.view.Display
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.jacktor.batterylab.MainApp
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.MainApp.Companion.isPowerConnected
import com.jacktor.batterylab.R
import com.jacktor.batterylab.adapters.HistoryAdapter
import com.jacktor.batterylab.databases.HistoryDB
import com.jacktor.batterylab.fragments.ChargeDischargeFragment
import com.jacktor.batterylab.fragments.HistoryFragment
import com.jacktor.batterylab.helpers.BatteryLevelHelper
import com.jacktor.batterylab.helpers.DateHelper
import com.jacktor.batterylab.helpers.HistoryHelper
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.capacityAdded
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.maxChargeCurrent
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.percentAdded
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.tempBatteryLevelWith
import com.jacktor.batterylab.interfaces.BatteryInfoInterface.Companion.tempCurrentCapacity
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
import com.jacktor.batterylab.utilities.Constants.CHECK_PREMIUM_JOB_ID
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
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DESIGN_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.FAST_CHARGE_SETTING
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.FULL_CHARGE_REMINDER_FREQUENCY
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
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.UPDATE_TEMP_SCREEN_TIME
import com.jacktor.batterylab.utilities.preferences.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class BatteryLabService() : Service(), NotificationInterface,
    BatteryInfoInterface {

    private lateinit var pref: Prefs
    private lateinit var powerConnectionReceiver: PowerConnectionReceiver

    private var screenTimeJob: Job? = null
    private var jobService: Job? = null
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isScreenTimeJob = false
    private var isJob = false
    private var currentCapacity = 0
    private var isReceiverRegistered = false

    var isFull = false
    var isStopService = false
    var isSaveNumberOfCharges = true
    var isPluggedOrUnplugged = false
    var batteryLevelWith = -1
    var seconds = 0
    var screenTime = 0L
    var secondsFullCharge = 0

    private val preferenceChangeListener =
        PreferenceChangeListener { prefs, key ->
            if (key == POWER_CONNECTION_SERVICE) {
                val isEnabled = prefs.getBoolean(key, false)
                if (isEnabled) {
                    registerPowerConnectionReceiver()
                } else {
                    unregisterPowerConnectionReceiver()
                }
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
            pref = Prefs(this)

            pref.addOnPreferenceChangeListener(preferenceChangeListener)

            // Register PowerConnectionReceiver
            powerConnectionReceiver = PowerConnectionReceiver()

            screenTime = if (MainApp.tempScreenTime > 0L) MainApp.tempScreenTime
            else if (MainApp.isUpdateApp) pref.getLong(UPDATE_TEMP_SCREEN_TIME, 0L)
            else screenTime

            MainApp.tempScreenTime = 0L
            MainApp.isUpdateApp = false

            pref.apply {
                if (contains(UPDATE_TEMP_SCREEN_TIME)) remove(UPDATE_TEMP_SCREEN_TIME)
            }

            batteryIntent = registerReceiver(
                null, IntentFilter(
                    Intent.ACTION_BATTERY_CHANGED
                )
            )

            when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {

                BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_PLUGGED_USB, BatteryManager.BATTERY_PLUGGED_WIRELESS -> {

                    isPowerConnected = true

                    batteryLevelWith = getBatteryLevel(applicationContext) ?: 0

                    tempBatteryLevelWith = batteryLevelWith

                    tempCurrentCapacity = getCurrentCapacity(applicationContext)

                    val status = batteryIntent?.getIntExtra(
                        BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN
                    ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

                    if (mainActivityRef?.get()?.fragment != null) {

                        if (mainActivityRef?.get()?.fragment is ChargeDischargeFragment) mainActivityRef?.get()?.topAppBar?.title =
                            getString(
                                if (status == BatteryManager.BATTERY_STATUS_CHARGING) R.string.charge else R.string.discharge
                            )

                        val chargeDischargeNavigation =
                            mainActivityRef?.get()?.navigation?.menu?.findItem(R.id.charge_discharge_navigation)

                        chargeDischargeNavigation?.title = getString(
                            if (status == BatteryManager.BATTERY_STATUS_CHARGING) R.string.charge else R.string.discharge
                        )

                        chargeDischargeNavigation?.icon = BatteryLevelHelper.batteryLevelIcon(
                            getBatteryLevel(applicationContext),
                            status == BatteryManager.BATTERY_STATUS_CHARGING
                        ).let {
                            ContextCompat.getDrawable(applicationContext, it)
                        }
                    }


                }
            }

            applicationContext.registerReceiver(
                PluggedReceiver(), IntentFilter(
                    Intent.ACTION_POWER_CONNECTED
                )
            )

            applicationContext.registerReceiver(
                UnpluggedReceiver(), IntentFilter(
                    Intent.ACTION_POWER_DISCONNECTED
                )
            )

            onCreateServiceNotification(applicationContext)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Register PowerConnectionReceiver
        var powerConnectionService = pref.getBoolean(POWER_CONNECTION_SERVICE, false)
        if (!isReceiverRegistered && powerConnectionService) {
            registerPowerConnectionReceiver()
        } else if (isReceiverRegistered && !powerConnectionService) {
            unregisterPowerConnectionReceiver()
        }

        if (screenTimeJob == null) screenTimeJob = CoroutineScope(Dispatchers.Default).launch {

            isScreenTimeJob = !isScreenTimeJob

            while (isScreenTimeJob && !isStopService) {

                val status = batteryIntent?.getIntExtra(
                    BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN
                )

                if ((status == BatteryManager.BATTERY_STATUS_DISCHARGING || status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) && !isPowerConnected) {

                    val displayManager =
                        getSystemService(DISPLAY_SERVICE) as? DisplayManager

                    if (displayManager != null) display@ for (display in displayManager.displays) if (display.state == Display.STATE_ON) {
                        screenTime++
                        break@display
                    }
                }

                delay(1000L)
            }
        }

        if (jobService == null) jobService = CoroutineScope(Dispatchers.Default).launch {

            isJob = !isJob

            while (isJob && !isStopService) {

                if (instance == null) instance = this@BatteryLabService

                if (wakeLock == null) {

                    if (powerManager == null) powerManager = getSystemService(
                        POWER_SERVICE
                    ) as PowerManager

                    wakeLock = powerManager?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, "${packageName}:service_wakelock"
                    )
                }

                if (wakeLock?.isHeld != true && !isFull && isPowerConnected) wakeLock?.acquire(
                    SERVICE_WAKELOCK_TIMEOUT
                )

                if ((getBatteryLevel(applicationContext)
                        ?: 0) < batteryLevelWith
                ) batteryLevelWith = getBatteryLevel(applicationContext) ?: 0

                batteryIntent = registerReceiver(
                    null, IntentFilter(
                        Intent.ACTION_BATTERY_CHANGED
                    )
                )

                val status = batteryIntent?.getIntExtra(
                    BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN
                )

                val temperature = getTemperatureInCelsius(applicationContext)

                if (!isPluggedOrUnplugged) {

                    BatteryInfoInterface.maximumTemperature = getMaximumTemperature(
                        applicationContext, BatteryInfoInterface.maximumTemperature
                    )

                    BatteryInfoInterface.minimumTemperature = getMinimumTemperature(
                        applicationContext, BatteryInfoInterface.minimumTemperature
                    )

                    BatteryInfoInterface.averageTemperature = getAverageTemperature(
                        applicationContext,
                        BatteryInfoInterface.maximumTemperature,
                        BatteryInfoInterface.minimumTemperature
                    )
                }

                if (pref.getBoolean(
                        NOTIFY_OVERHEAT_OVERCOOL, resources.getBoolean(
                            R.bool.notify_overheat_overcool
                        )
                    ) && (temperature >= pref.getInt(
                        OVERHEAT_DEGREES, resources.getInteger(
                            R.integer.overheat_degrees_default
                        )
                    ) || temperature <= pref.getInt(
                        OVERCOOL_DEGREES, resources.getInteger(
                            R.integer.overcool_degrees_default
                        )
                    ))
                ) withContext(Dispatchers.Main) {
                    onNotifyOverheatOvercool(applicationContext, temperature)
                }

                if (status == BatteryManager.BATTERY_STATUS_CHARGING && !isStopService && secondsFullCharge < 3600) batteryCharging()
                else if (status == BatteryManager.BATTERY_STATUS_FULL && isPowerConnected && !isFull && !isStopService) batteryCharged()
                else if (!isStopService) {

                    if (pref.getBoolean(
                            NOTIFY_BATTERY_IS_DISCHARGED, resources.getBoolean(
                                R.bool.notify_battery_is_discharged
                            )
                        ) && (getBatteryLevel(
                            applicationContext
                        ) ?: 0) <= pref.getInt(
                            BATTERY_LEVEL_NOTIFY_DISCHARGED, 20
                        )
                    ) withContext(Dispatchers.Main) {

                        onNotifyBatteryDischarged(applicationContext)
                    }

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
                                applicationContext, voltage.toInt()
                            )
                        }
                    }

                    withContext(Dispatchers.Main) {
                        try {
                            onUpdateServiceNotification(applicationContext)
                        } catch (_: NullPointerException) {
                        } finally {
                            delay(1.495.seconds)
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        pref.removeOnPreferenceChangeListener(preferenceChangeListener)

        if (isReceiverRegistered) {
            Receiver.unregister(this, powerConnectionReceiver)
        }

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

        MainApp.tempScreenTime = screenTime

        val numberOfCycles = if (batteryLevel == batteryLevelWith) pref.getFloat(
            NUMBER_OF_CYCLES, 0f
        ) + 0.01f else pref.getFloat(
            NUMBER_OF_CYCLES, 0f
        ) + (batteryLevel / 100f) - (batteryLevelWith / 100f)

        notificationManager?.cancelAll()

        if (!::pref.isInitialized) pref = Prefs(applicationContext)

        if (!isFull && seconds > 1) {

            pref.apply {

                setInt(LAST_CHARGE_TIME, seconds)

                setInt(BATTERY_LEVEL_WITH, batteryLevelWith)

                setInt(BATTERY_LEVEL_TO, batteryLevel)

                if (capacityAdded > 0) setFloat(CAPACITY_ADDED, capacityAdded.toFloat())

                if (percentAdded > 0) setInt(PERCENT_ADDED, percentAdded)

                if (isSaveNumberOfCharges) setFloat(NUMBER_OF_CYCLES, numberOfCycles)
            }

            percentAdded = 0

            capacityAdded = 0.0
        }

        if (BatteryInfoInterface.residualCapacity > 0 && isFull) {

            pref.apply {

                if (pref.getString(
                        UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY,
                        "μAh"
                    ) == "μAh"
                ) setInt(
                    RESIDUAL_CAPACITY, (getCurrentCapacity(applicationContext) * 1000.0).toInt()
                )
                else setInt(
                    RESIDUAL_CAPACITY, (getCurrentCapacity(applicationContext) * 100.0).toInt()
                )
            }

            HistoryHelper.addHistory(
                applicationContext, DateHelper.getDate(
                    DateHelper.getCurrentDay(),
                    DateHelper.getCurrentMonth(),
                    DateHelper.getCurrentYear()
                ), pref.getInt(
                    RESIDUAL_CAPACITY, 0
                )
            )
        }

        BatteryInfoInterface.batteryLevel = 0
        BatteryInfoInterface.tempBatteryLevel = 0

        if (isStopService) Toast.makeText(
            applicationContext, R.string.service_stopped_successfully, Toast.LENGTH_LONG
        ).show()

        ServiceHelper.cancelJob(applicationContext, NOTIFY_FULL_CHARGE_REMINDER_JOB_ID)
        ServiceHelper.cancelJob(applicationContext, CHECK_PREMIUM_JOB_ID)

        super.onDestroy()
        wakeLockRelease()
    }

    private suspend fun batteryCharging() {

        val batteryLevel = getBatteryLevel(applicationContext) ?: 0

        if (batteryLevel == 100) {
            if (secondsFullCharge >= 3600) batteryCharged()
            currentCapacity = (getCurrentCapacity(applicationContext) * if (pref.getString(
                    UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY, "μAh"
                ) == "μAh"
            ) 1000.0 else 100.0).toInt()
            secondsFullCharge++
        }

        val displayManager = getSystemService(DISPLAY_SERVICE) as? DisplayManager

        if (pref.getBoolean(
                NOTIFY_BATTERY_IS_CHARGED, resources.getBoolean(
                    R.bool.notify_battery_is_charged
                )
            ) && (getBatteryLevel(applicationContext) ?: 0) == pref.getInt(
                BATTERY_LEVEL_NOTIFY_CHARGED, 80
            )
        ) withContext(Dispatchers.Main) {

            onNotifyBatteryCharged(applicationContext)
        }

        if (displayManager != null) for (display in displayManager.displays) if (display.state == Display.STATE_ON) delay(
            if (getCurrentCapacity(applicationContext) > 0.0) 0.948.seconds else 0.954.seconds
        )
        else delay(
            if (getCurrentCapacity(applicationContext) > 0.0) 0.937.seconds else 0.934.seconds
        )

        seconds++

        withContext(Dispatchers.Main) {
            try {
                withContext(Dispatchers.Main) {
                    onUpdateServiceNotification(applicationContext)
                }
            } catch (_: RuntimeException) {
            } catch (_: NullPointerException) {
            }
        }
    }

    private suspend fun batteryCharged() {

        withContext(Dispatchers.Main) {
            val fullChargeReminderFrequency = pref.getString(
                FULL_CHARGE_REMINDER_FREQUENCY,
                "${resources.getInteger(R.integer.full_charge_reminder_frequency_default)}"
            )?.toInt()

            ServiceHelper.jobSchedule(
                applicationContext,
                FullChargeReminderJobService::class.java,
                NOTIFY_FULL_CHARGE_REMINDER_JOB_ID,
                fullChargeReminderFrequency?.minutes?.inWholeMilliseconds
                    ?: resources.getInteger(R.integer.full_charge_reminder_frequency_default).minutes.inWholeMilliseconds
            )
        }

        isFull = true

        if (currentCapacity == 0) currentCapacity =
            (getCurrentCapacity(applicationContext) * if (pref.getString(
                    UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY, "μAh"
                ) == "μAh"
            ) 1000.0
            else 100.0).toInt()

        val designCapacity = pref.getInt(
            DESIGN_CAPACITY, resources.getInteger(
                R.integer.min_design_capacity
            )
        ).toDouble() * if (pref.getString(
                UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY, "μAh"
            ) == "μAh"
        ) 1000.0
        else 100.0

        val residualCapacityCurrent = if (pref.getString(
                UNIT_OF_MEASUREMENT_OF_CURRENT_CAPACITY, "μAh"
            ) == "μAh"
        ) pref.getInt(RESIDUAL_CAPACITY, 0) / 1000
        else pref.getInt(RESIDUAL_CAPACITY, 0) / 100

        val residualCapacity =
            if (residualCapacityCurrent in 1..maxChargeCurrent || isTurboCharge(
                    applicationContext
                ) || pref.getBoolean(
                    FAST_CHARGE_SETTING, resources.getBoolean(R.bool.fast_charge_setting)
                )
            ) (currentCapacity.toDouble() + ((NOMINAL_BATTERY_VOLTAGE / 100.0) * designCapacity)).toInt()
            else currentCapacity

        val currentDate = DateHelper.getDate(
            DateHelper.getCurrentDay(),
            DateHelper.getCurrentMonth(),
            DateHelper.getCurrentYear()
        )

        if (pref.getBoolean(
                NOTIFY_BATTERY_IS_FULLY_CHARGED, resources.getBoolean(
                    R.bool.notify_battery_is_fully_charged
                )
            )
        ) withContext(Dispatchers.Main) {

            onNotifyBatteryFullyCharged(applicationContext)
        }

        val batteryLevel = getBatteryLevel(applicationContext) ?: 0

        val numberOfCycles = if (batteryLevel == batteryLevelWith) pref.getFloat(
            NUMBER_OF_CYCLES, 0f
        ) + 0.01f else pref.getFloat(
            NUMBER_OF_CYCLES, 0f
        ) + (batteryLevel / 100f) - (batteryLevelWith / 100f)

        pref.apply {

            val numberOfCharges = getLong(NUMBER_OF_CHARGES, 0)
            if (seconds > 1) setLong(NUMBER_OF_CHARGES, numberOfCharges + 1)

            setInt(LAST_CHARGE_TIME, seconds)
            setInt(RESIDUAL_CAPACITY, residualCapacity)
            setInt(BATTERY_LEVEL_WITH, batteryLevelWith)
            setInt(BATTERY_LEVEL_TO, batteryLevel)
            setLong(NUMBER_OF_FULL_CHARGES, getLong(NUMBER_OF_FULL_CHARGES, 0) + 1)
            setFloat(CAPACITY_ADDED, capacityAdded.toFloat())
            setInt(PERCENT_ADDED, percentAdded)

            if (isSaveNumberOfCharges) setFloat(NUMBER_OF_CYCLES, numberOfCycles)
        }

        withContext(Dispatchers.Main) {
            if (residualCapacity > 0 && seconds >= 10) {
                withContext(Dispatchers.IO) {
                    HistoryHelper.addHistory(
                        applicationContext, currentDate, residualCapacity
                    )
                }
                if (HistoryHelper.isHistoryNotEmpty(applicationContext)) {
                    val historyFragment = HistoryFragment.instance
                    historyFragment?.binding?.refreshEmptyHistory?.visibility = View.GONE
                    historyFragment?.binding?.emptyHistoryLayout?.visibility = View.GONE
                    historyFragment?.binding?.historyRecyclerView?.visibility = View.VISIBLE
                    historyFragment?.binding?.refreshHistory?.visibility = View.VISIBLE

                    mainActivityRef?.get()?.topAppBar?.menu?.findItem(R.id.history_premium)?.isVisible =
                        false
                    mainActivityRef?.get()?.topAppBar?.menu?.findItem(R.id.clear_history)?.isVisible =
                        true

                    if (HistoryHelper.getHistoryCount(applicationContext) == 1L) {
                        val historyDB = withContext(Dispatchers.IO) {
                            HistoryDB(applicationContext)
                        }
                        historyFragment?.historyAdapter =
                            HistoryAdapter(
                                withContext(Dispatchers.IO) {
                                    historyDB.readDB()
                                }
                            )
                        historyFragment?.historyAdapter?.itemCount?.let {
                            historyFragment.binding?.historyRecyclerView?.setItemViewCacheSize(
                                it
                            )
                        }
                        historyFragment?.binding?.historyRecyclerView?.adapter =
                            historyFragment.historyAdapter
                    } else HistoryAdapter.instance?.update(applicationContext)

                } else {
                    HistoryFragment.instance?.binding?.historyRecyclerView?.visibility =
                        View.GONE
                    HistoryFragment.instance?.binding?.refreshHistory?.visibility =
                        View.GONE
                    HistoryFragment.instance?.binding?.emptyHistoryLayout?.visibility =
                        View.VISIBLE
                    HistoryFragment.instance?.binding?.refreshEmptyHistory?.visibility =
                        View.VISIBLE
                    HistoryFragment.instance?.binding?.emptyHistoryText?.text =
                        resources.getText(R.string.empty_history_text)
                    mainActivityRef?.get()?.topAppBar?.menu?.findItem(R.id.history_premium)?.isVisible =
                        false
                    mainActivityRef?.get()?.topAppBar?.menu?.findItem(R.id.clear_history)?.isVisible =
                        false
                }
            } else {
                HistoryFragment.instance?.binding?.historyRecyclerView?.visibility =
                    View.GONE
                HistoryFragment.instance?.binding?.refreshHistory?.visibility = View.GONE
                HistoryFragment.instance?.binding?.emptyHistoryLayout?.visibility =
                    View.VISIBLE
                HistoryFragment.instance?.binding?.refreshEmptyHistory?.visibility =
                    View.VISIBLE
                HistoryFragment.instance?.binding?.emptyHistoryText?.text =
                    resources.getText(R.string.history_premium_feature)
                mainActivityRef?.get()?.topAppBar?.menu?.findItem(R.id.history_premium)?.isVisible =
                    true
                mainActivityRef?.get()?.topAppBar?.menu?.findItem(R.id.clear_history)?.isVisible =
                    false
            }
        }

        isSaveNumberOfCharges = false

        withContext(Dispatchers.Main) {
            try {
                onUpdateServiceNotification(applicationContext)
            } catch (_: NullPointerException) {
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
