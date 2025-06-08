package com.jacktor.batterylab.helpers

import android.app.ForegroundServiceStartNotAllowedException
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkRequest
import android.os.Build
import android.widget.Toast
import androidx.preference.Preference
import com.jacktor.batterylab.MainApp
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.services.OverlayService
import com.jacktor.batterylab.utilities.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

object ServiceHelper {

    private var isStartedBatteryLabService = false
    private var isStartedOverlayService = false

    fun startService(
        context: Context, serviceName: Class<*>,
        isStartOverlayServiceFromSettings: Boolean = false
    ) {

        CoroutineScope(Dispatchers.Main).launch {

            try {

                if (serviceName == BatteryLabService::class.java) {

                    isStartedBatteryLabService = true

                    delay(2.5.seconds)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(Intent(context, serviceName))
                    } else startService(context, serviceName)

                    delay(1.seconds)
                    isStartedBatteryLabService = false
                } else if (serviceName == OverlayService::class.java) {

                    isStartedOverlayService = true

                    if (!isStartOverlayServiceFromSettings) delay(3600L)

                    context.startService(Intent(context, serviceName))
                    isStartedBatteryLabService = false
                }
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && e is ForegroundServiceStartNotAllowedException
                ) return@launch
                else Toast.makeText(context, e.message ?: e.toString(), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun isStartedBatteryLabService() = isStartedBatteryLabService

    fun isStartedOverlayService() = isStartedOverlayService

    fun stopService(context: Context, serviceName: Class<*>) =
        context.stopService(Intent(context, serviceName))

    fun restartService(context: Context, serviceName: Class<*>, preference: Preference? = null) {

        CoroutineScope(Dispatchers.Main).launch {
            if (serviceName == BatteryLabService::class.java)
                MainApp.tempScreenTime = BatteryLabService.instance?.screenTime ?: 0L

            stopService(context, serviceName)

            if (serviceName == BatteryLabService::class.java) delay(2.5.seconds)

            startService(context, serviceName)

            delay(1.seconds)
            preference?.isEnabled = true
        }
    }

    fun jobSchedule(
        context: Context, jobName: Class<*>, jobId: Int, periodic: Long,
        isRequiredNetwork: Boolean = false
    ) {

        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler

        val serviceComponent = ComponentName(context, jobName)

        val jobInfo = JobInfo.Builder(jobId, serviceComponent).apply {
            if (isRequiredNetwork) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    setRequiredNetwork(NetworkRequest.Builder().apply {
                        addCapability(NET_CAPABILITY_INTERNET)
                        addCapability(NET_CAPABILITY_VALIDATED)
                    }.build())
                else setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            }

            setPeriodic(periodic)

        }.build()

        if (!isJobSchedule(context, jobId)) jobScheduler?.schedule(jobInfo)
    }

    private fun isJobSchedule(context: Context, jobId: Int): Boolean {

        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler

        jobScheduler?.allPendingJobs?.forEach {

            if (it.id == jobId) return true
        }

        return false
    }

    fun cancelJob(context: Context, jobId: Int) {

        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler

        if (isJobSchedule(context, jobId)) jobScheduler?.cancel(jobId)
    }

    fun cancelAllJobs(context: Context) {

        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler

        if (jobScheduler?.allPendingJobs?.isNotEmpty() == true) jobScheduler.cancelAll()
    }
}
