package com.jacktor.batterylab.services

import android.app.job.JobParameters
import android.app.job.JobService
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.ServiceHelper
import com.jacktor.batterylab.interfaces.NotificationInterface
import com.jacktor.batterylab.utilities.Constants
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_BATTERY_IS_FULLY_CHARGED
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NOTIFY_FULL_CHARGE_REMINDER


class FullChargeReminderJobService() : JobService(), NotificationInterface {

    override fun onStartJob(params: JobParameters?): Boolean {

        val pref = PreferenceManager.getDefaultSharedPreferences(this)

        val isNotifyBatteryIsFullyCharged = pref.getBoolean(
            NOTIFY_BATTERY_IS_FULLY_CHARGED,
            resources.getBoolean(R.bool.notify_battery_is_fully_charged)
        )

        val isNotifyFullyChargeReminder = pref.getBoolean(
            NOTIFY_FULL_CHARGE_REMINDER,
            resources.getBoolean(R.bool.notify_full_charge_reminder_default_value)
        )

        if (!isNotifyFullyChargeReminder || BatteryLabService.instance == null)
            ServiceHelper.cancelJob(this, Constants.NOTIFY_FULL_CHARGE_REMINDER_JOB_ID)
        else if (isNotifyBatteryIsFullyCharged && BatteryLabService.instance!!.isFull)
            onNotifyBatteryFullyCharged(this, true)

        return false
    }

    override fun onStopJob(params: JobParameters?) = false

    init {
        Configuration.Builder().setJobSchedulerJobIdRange(2000, 3000).build()
    }
}