package com.jacktor.batterylab.interfaces

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.graphics.Color
import android.media.AudioAttributes
import android.os.BatteryManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.MainApp.Companion.batteryIntent
import com.jacktor.batterylab.R
import com.jacktor.batterylab.helpers.StatusBarHelper
import com.jacktor.batterylab.services.BatteryLabService
import com.jacktor.batterylab.services.CloseNotificationBatteryStatusInformationService
import com.jacktor.batterylab.services.DisableNotificationBatteryStatusInformationService
import com.jacktor.batterylab.services.StopBatteryLabService
import com.jacktor.batterylab.utilities.Constants.CHARGED_CHANNEL_ID
import com.jacktor.batterylab.utilities.Constants.CLOSE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE
import com.jacktor.batterylab.utilities.Constants.DISABLE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE
import com.jacktor.batterylab.utilities.Constants.DISCHARGED_CHANNEL_ID
import com.jacktor.batterylab.utilities.Constants.DISCHARGED_VOLTAGE_CHANNEL_ID
import com.jacktor.batterylab.utilities.Constants.FULLY_CHARGED_CHANNEL_ID
import com.jacktor.batterylab.utilities.Constants.OPEN_APP_REQUEST_CODE
import com.jacktor.batterylab.utilities.Constants.OVERHEAT_OVERCOOL_CHANNEL_ID
import com.jacktor.batterylab.utilities.Constants.SERVICE_CHANNEL_ID
import com.jacktor.batterylab.utilities.Constants.STOP_SERVICE_REQUEST_CODE
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.BYPASS_DND
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CAPACITY_IN_WH
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IS_SHOW_BATTERY_INFORMATION
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.NUMBER_OF_CYCLES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERCOOL_DEGREES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.OVERHEAT_DEGREES
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SERVICE_TIME
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SHOW_BATTERY_INFORMATION
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SHOW_EXPANDED_NOTIFICATION
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SHOW_STOP_SERVICE
import com.jacktor.premium.Premium
import java.text.DecimalFormat

@SuppressLint("StaticFieldLeak")
interface NotificationInterface : BatteryInfoInterface {

    companion object {

        const val NOTIFICATION_SERVICE_ID = 101
        const val NOTIFICATION_BATTERY_STATUS_ID = 102
        const val NOTIFICATION_FULLY_CHARGED_ID = 103
        const val NOTIFICATION_BATTERY_OVERHEAT_OVERCOOL_ID = 104

        private lateinit var channelId: String
        private lateinit var stopService: PendingIntent

        var notificationBuilder: NotificationCompat.Builder? = null
        var notificationManager: NotificationManager? = null
        var isOverheatOvercool = false
        var isBatteryFullyCharged = false
        var isBatteryCharged = false
        var isBatteryDischarged = false
        var isBatteryDischargedVoltage = false
    }

    @SuppressLint("RestrictedApi")
    fun onCreateServiceNotification(context: Context) {

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val existed = isNotificationExists(context, NOTIFICATION_SERVICE_ID)
        if (existed && notificationBuilder != null) return

        channelId = onCreateNotificationChannel(context, SERVICE_CHANNEL_ID)

        val openApp = PendingIntent.getActivity(
            context, OPEN_APP_REQUEST_CODE, Intent(
                context,
                MainActivity::class.java
            ), PendingIntent.FLAG_IMMUTABLE
        )
        stopService = PendingIntent.getService(
            context, STOP_SERVICE_REQUEST_CODE, Intent(
                context,
                StopBatteryLabService::class.java
            ), PendingIntent.FLAG_IMMUTABLE
        )

        batteryIntent = context.registerReceiver(
            null, IntentFilter(
                Intent.ACTION_BATTERY_CHANGED
            )
        )

        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        notificationBuilder = NotificationCompat.Builder(context, channelId).apply {

            setOngoing(true)
            setCategory(Notification.CATEGORY_SERVICE)
            setSmallIcon(StatusBarHelper.stat(getBatteryLevel(context)))
            setOnlyAlertOnce(true)
            setSilent(true)

            color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                ContextCompat.getColor(context, android.R.color.system_accent1_300) else
                ContextCompat.getColor(context, R.color.blue)

            setContentIntent(openApp)

            if (Premium.isPremium().value) {
                if (pref.getBoolean(
                        SHOW_STOP_SERVICE, context.resources.getBoolean(
                            R.bool.show_stop_service
                        )
                    ) && mActions.isEmpty()
                )
                    addAction(0, context.getString(R.string.stop_service), stopService)
                else if (!pref.getBoolean(
                        SHOW_STOP_SERVICE, context.resources.getBoolean(
                            R.bool.show_stop_service
                        )
                    ) && mActions.isNotEmpty()
                ) mActions.clear()
            }
            val remoteViewsServiceContent = RemoteViews(
                context.packageName,
                R.layout.notification_content
            )

            val isShowBatteryInformation = pref.getBoolean(
                SHOW_BATTERY_INFORMATION,
                context.resources.getBoolean(R.bool.show_battery_information)
            )
            val isShowExpandedNotification = pref.getBoolean(
                SHOW_EXPANDED_NOTIFICATION, context.resources.getBoolean(
                    R.bool.show_expanded_notification
                )
            )
            if (isShowBatteryInformation) {
                remoteViewsServiceContent.setTextViewText(
                    R.id.notification_content_text,
                    if (getCurrentCapacity(context) > 0.0) {

                        val isCapacityInWh = pref.getBoolean(
                            CAPACITY_IN_WH,
                            context.resources.getBoolean(R.bool.capacity_in_wh)
                        )

                        if (isCapacityInWh) context.getString(
                            R.string.current_capacity_wh,
                            DecimalFormat("#.#").format(
                                getCapacityInWh(getCurrentCapacity(context))
                            )
                        )
                        else context.getString(
                            R.string.current_capacity,
                            DecimalFormat("#.#").format(getCurrentCapacity(context))
                        )
                    } else "${
                        context.getString(
                            R.string.battery_level_with_title,
                            (getBatteryLevel(context) ?: 0).toString()
                        )
                    }%"
                )
            } else remoteViewsServiceContent.setTextViewText(
                R.id.notification_content_text,
                context.getString(R.string.service_is_running)
            )

            setCustomContentView(remoteViewsServiceContent)

            val isShowBigContent = isShowBatteryInformation && isShowExpandedNotification

            if (isShowBigContent) {

                val remoteViewsServiceBigContent = RemoteViews(
                    context.packageName,
                    R.layout.service_notification_big_content
                )

                remoteViewsServiceBigContent.setViewVisibility(
                    R.id
                        .voltage_service_notification, if (getCurrentCapacity(context) == 0.0
                        || mActions.isNullOrEmpty()
                    ) View.VISIBLE else View.GONE
                )

                getNotificationMessage(context, status, remoteViewsServiceBigContent)

                setCustomBigContentView(remoteViewsServiceBigContent)
            } else {
                setCustomBigContentView(null)
            }

            setStyle(NotificationCompat.DecoratedCustomViewStyle())

            val showTime = pref.getBoolean(
                SERVICE_TIME, context.resources.getBoolean(
                    R.bool.service_time
                )
            )
            setShowWhen(showTime)
            setUsesChronometer(showTime)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                foregroundServiceBehavior = FOREGROUND_SERVICE_IMMEDIATE
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                notificationBuilder?.build()?.let {
                    BatteryLabService.instance?.startForeground(
                        NOTIFICATION_SERVICE_ID,
                        it, FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                }
            else notificationBuilder?.build()?.let {
                BatteryLabService.instance?.startForeground(NOTIFICATION_SERVICE_ID, it)
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) return
        }
    }

    @SuppressLint("RestrictedApi")
    fun onUpdateServiceNotification(context: Context) {
        if (notificationBuilder == null || !isNotificationExists(
                context,
                NOTIFICATION_SERVICE_ID
            )
        ) {
            onCreateServiceNotification(context)
        }

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        notificationManager = context.getSystemService(NOTIFICATION_SERVICE)
                as NotificationManager

        batteryIntent = context.registerReceiver(
            null, IntentFilter(
                Intent.ACTION_BATTERY_CHANGED
            )
        )

        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        notificationBuilder?.apply {

            setSmallIcon(StatusBarHelper.stat(getBatteryLevel(context)))

            color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                ContextCompat.getColor(context, android.R.color.system_accent1_300) else
                ContextCompat.getColor(context, R.color.blue)

            setOnlyAlertOnce(true)
            setSilent(true)

            if (Premium.isPremium().value) {
                if (pref.getBoolean(
                        SHOW_STOP_SERVICE, context.resources.getBoolean(
                            R.bool.show_stop_service
                        )
                    ) && mActions.isEmpty()
                )
                    addAction(0, context.getString(R.string.stop_service), stopService)
                else if (!pref.getBoolean(
                        SHOW_STOP_SERVICE, context.resources.getBoolean(
                            R.bool.show_stop_service
                        )
                    ) && mActions.isNotEmpty()
                ) mActions.clear()
            }

            val remoteViewsServiceContent = RemoteViews(
                context.packageName,
                R.layout.notification_content
            )

            val isShowBatteryInformation = pref.getBoolean(
                IS_SHOW_BATTERY_INFORMATION,
                context.resources.getBoolean(R.bool.is_show_battery_information)
            )

            val isShowExpandedNotification = pref.getBoolean(
                SHOW_EXPANDED_NOTIFICATION, context.resources.getBoolean(
                    R.bool.show_expanded_notification
                )
            )
            if (isShowBatteryInformation) {
                remoteViewsServiceContent.setTextViewText(
                    R.id.notification_content_text,
                    if (getCurrentCapacity(context) > 0.0) {

                        val isCapacityInWh = pref.getBoolean(
                            CAPACITY_IN_WH,
                            context.resources.getBoolean(R.bool.capacity_in_wh)
                        )

                        if (isCapacityInWh) context.getString(
                            R.string.current_capacity_wh,
                            DecimalFormat("#.#").format(
                                getCapacityInWh(getCurrentCapacity(context))
                            )
                        )
                        else context.getString(
                            R.string.current_capacity,
                            DecimalFormat("#.#").format(getCurrentCapacity(context))
                        )
                    } else "${
                        context.getString(
                            R.string.battery_level_with_title,
                            (getBatteryLevel(context) ?: 0).toString()
                        )
                    }%"
                )
            } else remoteViewsServiceContent.setTextViewText(
                R.id.notification_content_text,
                context.getString(R.string.service_is_running)
            )

            try {
                remoteViewsServiceContent.setViewVisibility(
                    R.id.notification_content_text,
                    View.INVISIBLE
                )
                remoteViewsServiceContent.setViewVisibility(
                    R.id.notification_content_text,
                    View.VISIBLE
                )
            } catch (_: Throwable) {
            }

            setCustomContentView(remoteViewsServiceContent)

            val isShowBigContent = isShowBatteryInformation && isShowExpandedNotification

            if (isShowBigContent) {

                val remoteViewsServiceBigContent = RemoteViews(
                    context.packageName,
                    R.layout.service_notification_big_content
                )

                remoteViewsServiceBigContent.setViewVisibility(
                    R.id
                        .voltage_service_notification, if (getCurrentCapacity(context) == 0.0
                        || mActions.isNullOrEmpty()
                    ) View.VISIBLE else View.GONE
                )

                getNotificationMessage(context, status, remoteViewsServiceBigContent)

                try {
                    remoteViewsServiceBigContent.setViewVisibility(
                        R.id.status_service_notification,
                        View.INVISIBLE
                    )
                    remoteViewsServiceBigContent.setViewVisibility(
                        R.id.status_service_notification,
                        View.VISIBLE
                    )
                } catch (_: Throwable) {
                }

                setCustomBigContentView(remoteViewsServiceBigContent)
            } else {
                setCustomBigContentView(null)
            }

            setStyle(NotificationCompat.DecoratedCustomViewStyle())

            val showTime = pref.getBoolean(
                SERVICE_TIME, context.resources.getBoolean(
                    R.bool.service_time
                )
            )
            setShowWhen(showTime)
            setUsesChronometer(showTime)
        }

        notificationManager?.notify(NOTIFICATION_SERVICE_ID, notificationBuilder?.build())
    }

    fun onNotifyOverheatOvercool(context: Context, temperature: Double) {

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val temperatureInFahrenheit = getTemperatureInFahrenheit(context)

        notificationManager = context.getSystemService(NOTIFICATION_SERVICE)
                as? NotificationManager

        val channelId = onCreateNotificationChannel(context, OVERHEAT_OVERCOOL_CHANNEL_ID)

        val remoteViewsContent = RemoteViews(context.packageName, R.layout.notification_content)

        when {

            temperature >= pref.getInt(
                OVERHEAT_DEGREES, context.resources.getInteger(
                    R.integer.overheat_degrees_default
                )
            ) ->
                remoteViewsContent.setTextViewText(
                    R.id.notification_content_text,
                    context.getString(
                        R.string.battery_overheating, DecimalFormat().format(
                            temperature
                        ), DecimalFormat().format(temperatureInFahrenheit)
                    )
                )

            temperature <= pref.getInt(
                OVERCOOL_DEGREES, context.resources.getInteger(
                    R.integer.overcool_degrees_default
                )
            ) ->
                remoteViewsContent.setTextViewText(
                    R.id.notification_content_text,
                    context.getString(
                        R.string.battery_overcooling, DecimalFormat().format(
                            temperature
                        ), DecimalFormat().format(temperatureInFahrenheit)
                    )
                )

            else -> return
        }

        val close = PendingIntent.getService(
            context,
            CLOSE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(
                context,
                CloseNotificationBatteryStatusInformationService::class.java
            ),
            PendingIntent.FLAG_IMMUTABLE
        )

        val disable = PendingIntent.getService(
            context,
            DISABLE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(
                context,
                DisableNotificationBatteryStatusInformationService::class.java
            ),
            PendingIntent.FLAG_IMMUTABLE
        )

        isOverheatOvercool = true
        isBatteryFullyCharged = false
        isBatteryCharged = false
        isBatteryDischarged = false
        isBatteryDischargedVoltage = false

        val notificationBuilder = NotificationCompat.Builder(
            context, channelId
        ).apply {

            if (pref.getBoolean(
                    BYPASS_DND, context.resources.getBoolean(
                        R.bool.bypass_dnd_mode
                    )
                )
            )
                setCategory(NotificationCompat.CATEGORY_ALARM)

            setAutoCancel(true)
            setOngoing(false)

            addAction(0, context.getString(R.string.close), close)
            addAction(0, context.getString(R.string.disable), disable)

            priority = NotificationCompat.PRIORITY_MAX

            setSmallIcon(R.drawable.ic_overheat_overcool_24)

            color = ContextCompat.getColor(context, R.color.overheat_overcool)

            setContentTitle(context.getString(R.string.battery_status_information))

            setCustomContentView(remoteViewsContent)

            setStyle(NotificationCompat.DecoratedCustomViewStyle())

            setShowWhen(true)

            setSound(
                ("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                        "${context.packageName}/${R.raw.overheat_overcool}").toUri()
            )
        }

        notificationManager?.notify(
            NOTIFICATION_BATTERY_OVERHEAT_OVERCOOL_ID,
            notificationBuilder.build()
        )
    }

    fun onNotifyBatteryFullyCharged(context: Context, isReminder: Boolean = false) {

        if (!isReminder && isNotificationExists(context, NOTIFICATION_FULLY_CHARGED_ID)) return

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        notificationManager = context.getSystemService(NOTIFICATION_SERVICE)
                as? NotificationManager

        val channelId = onCreateNotificationChannel(context, FULLY_CHARGED_CHANNEL_ID)

        val remoteViewsContent = RemoteViews(context.packageName, R.layout.notification_content)

        remoteViewsContent.setTextViewText(
            R.id.notification_content_text, context.getString(
                R.string.battery_is_fully_charged
            )
        )

        val close = PendingIntent.getService(
            context,
            CLOSE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(
                context,
                CloseNotificationBatteryStatusInformationService::class.java
            ),
            PendingIntent.FLAG_IMMUTABLE
        )

        val disable = PendingIntent.getService(
            context,
            DISABLE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(
                context,
                DisableNotificationBatteryStatusInformationService::class.java
            ),
            PendingIntent.FLAG_IMMUTABLE
        )

        isOverheatOvercool = false
        isBatteryFullyCharged = true
        isBatteryCharged = false
        isBatteryDischarged = false
        isBatteryDischargedVoltage = false

        val notificationBuilder = NotificationCompat.Builder(
            context, channelId
        ).apply {

            if (pref.getBoolean(
                    BYPASS_DND, context.resources.getBoolean(
                        R.bool.bypass_dnd_mode
                    )
                )
            )
                setCategory(NotificationCompat.CATEGORY_ALARM)

            setAutoCancel(true)
            setOngoing(false)

            addAction(0, context.getString(R.string.close), close)
            addAction(0, context.getString(R.string.disable), disable)

            priority = NotificationCompat.PRIORITY_MAX

            setSmallIcon(R.drawable.ic_battery_is_fully_charged_24dp)

            color = ContextCompat.getColor(context, R.color.battery_charged)

            setContentTitle(context.getString(R.string.battery_status_information))

            setCustomContentView(remoteViewsContent)

            setStyle(NotificationCompat.DecoratedCustomViewStyle())

            setShowWhen(true)

            setSound(
                ("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                        "${context.packageName}/${R.raw.battery_is_fully_charged}").toUri()
            )
        }

        notificationManager?.notify(NOTIFICATION_FULLY_CHARGED_ID, notificationBuilder.build())
    }

    fun onNotifyBatteryCharged(context: Context) {

        if (isNotificationExists(context, NOTIFICATION_BATTERY_STATUS_ID)) return

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val batteryLevel = getBatteryLevel(context)

        notificationManager = context.getSystemService(NOTIFICATION_SERVICE)
                as? NotificationManager

        val channelId = onCreateNotificationChannel(context, CHARGED_CHANNEL_ID)

        val remoteViewsContent = RemoteViews(context.packageName, R.layout.notification_content)

        remoteViewsContent.setTextViewText(
            R.id.notification_content_text,
            "${context.getString(R.string.battery_is_charged_notification, batteryLevel)}%"
        )

        val close = PendingIntent.getService(
            context,
            CLOSE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(
                context,
                CloseNotificationBatteryStatusInformationService::class.java
            ),
            PendingIntent.FLAG_IMMUTABLE
        )

        val disable = PendingIntent.getService(
            context,
            DISABLE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(
                context,
                DisableNotificationBatteryStatusInformationService::class.java
            ),
            PendingIntent.FLAG_IMMUTABLE
        )

        isOverheatOvercool = false
        isBatteryFullyCharged = false
        isBatteryCharged = true
        isBatteryDischarged = false
        isBatteryDischargedVoltage = false

        val notificationBuilder = NotificationCompat.Builder(
            context, channelId
        ).apply {

            if (pref.getBoolean(
                    BYPASS_DND, context.resources.getBoolean(
                        R.bool.bypass_dnd_mode
                    )
                )
            )
                setCategory(NotificationCompat.CATEGORY_ALARM)

            setAutoCancel(true)
            setOngoing(false)

            addAction(0, context.getString(R.string.close), close)
            addAction(0, context.getString(R.string.disable), disable)

            priority = NotificationCompat.PRIORITY_MAX

            setSmallIcon(
                when (batteryLevel) {

                    in 0..29 -> R.drawable.ic_battery_is_charged_20_24dp
                    in 30..49 -> R.drawable.ic_battery_is_charged_30_24dp
                    in 50..59 -> R.drawable.ic_battery_is_charged_50_24dp
                    in 60..79 -> R.drawable.ic_battery_is_charged_60_24dp
                    in 80..89 -> R.drawable.ic_battery_is_charged_80_24dp
                    in 90..95 -> R.drawable.ic_battery_is_charged_90_24dp
                    else -> R.drawable.ic_battery_is_fully_charged_24dp
                }
            )

            color = ContextCompat.getColor(context, R.color.battery_charged)

            setContentTitle(context.getString(R.string.battery_status_information))

            setCustomContentView(remoteViewsContent)

            setStyle(NotificationCompat.DecoratedCustomViewStyle())

            setShowWhen(true)

            setLights(Color.GREEN, 1500, 500)

            setSound(
                ("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                        "${context.packageName}/${R.raw.battery_is_charged}").toUri()
            )
        }

        notificationManager?.notify(NOTIFICATION_BATTERY_STATUS_ID, notificationBuilder.build())
    }

    fun onNotifyBatteryDischarged(context: Context) {

        if (isNotificationExists(context, NOTIFICATION_BATTERY_STATUS_ID)) return

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val batteryLevel = getBatteryLevel(context) ?: 0

        notificationManager = context.getSystemService(NOTIFICATION_SERVICE)
                as? NotificationManager

        val channelId = onCreateNotificationChannel(context, DISCHARGED_CHANNEL_ID)

        val remoteViewsContent = RemoteViews(context.packageName, R.layout.notification_content)

        remoteViewsContent.setTextViewText(
            R.id.notification_content_text,
            "${
                context.getString(
                    R.string.battery_is_discharged_notification,
                    batteryLevel
                )
            }%"
        )

        val close = PendingIntent.getService(
            context,
            CLOSE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(
                context,
                CloseNotificationBatteryStatusInformationService::class.java
            ),
            PendingIntent.FLAG_IMMUTABLE
        )

        val disable = PendingIntent.getService(
            context,
            DISABLE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(
                context,
                DisableNotificationBatteryStatusInformationService::class.java
            ),
            PendingIntent.FLAG_IMMUTABLE
        )

        isOverheatOvercool = false
        isBatteryFullyCharged = false
        isBatteryCharged = false
        isBatteryDischarged = true
        isBatteryDischargedVoltage = false

        val notificationBuilder = NotificationCompat.Builder(
            context, channelId
        ).apply {

            if (pref.getBoolean(
                    BYPASS_DND, context.resources.getBoolean(
                        R.bool.bypass_dnd_mode
                    )
                )
            )
                setCategory(NotificationCompat.CATEGORY_ALARM)

            setAutoCancel(true)
            setOngoing(false)

            addAction(0, context.getString(R.string.close), close)
            addAction(0, context.getString(R.string.disable), disable)

            priority = NotificationCompat.PRIORITY_MAX

            setSmallIcon(
                when (batteryLevel) {

                    in 0..9 -> R.drawable.ic_battery_discharged_9_24dp
                    in 10..29 -> R.drawable.ic_battery_is_discharged_20_24dp
                    in 30..49 -> R.drawable.ic_battery_is_discharged_30_24dp
                    in 50..59 -> R.drawable.ic_battery_is_discharged_50_24dp
                    in 60..79 -> R.drawable.ic_battery_is_discharged_60_24dp
                    in 80..89 -> R.drawable.ic_battery_is_discharged_80_24dp
                    in 90..99 -> R.drawable.ic_battery_is_discharged_100_24dp
                    else -> R.drawable.ic_battery_discharged_9_24dp
                }
            )

            color = ContextCompat.getColor(context, R.color.battery_discharged)

            setContentTitle(context.getString(R.string.battery_status_information))

            setCustomContentView(remoteViewsContent)

            setStyle(NotificationCompat.DecoratedCustomViewStyle())

            setShowWhen(true)

            setLights(Color.RED, 1000, 500)

            setSound(
                ("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                        "${context.packageName}/${R.raw.battery_is_discharged}").toUri()
            )
        }

        notificationManager?.notify(NOTIFICATION_BATTERY_STATUS_ID, notificationBuilder.build())
    }

    fun onNotifyBatteryDischargedVoltage(context: Context, voltage: Int) {

        if (isNotificationExists(context, NOTIFICATION_BATTERY_STATUS_ID)) return

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val batteryLevel = getBatteryLevel(context) ?: 0

        notificationManager = context.getSystemService(NOTIFICATION_SERVICE)
                as? NotificationManager

        val channelId = onCreateNotificationChannel(context, DISCHARGED_VOLTAGE_CHANNEL_ID)

        val remoteViewsContent = RemoteViews(context.packageName, R.layout.notification_content)

        remoteViewsContent.setTextViewText(
            R.id.notification_content_text, context.getString(
                R.string.battery_is_discharged_notification_voltage, voltage
            )
        )

        val close = PendingIntent.getService(
            context,
            CLOSE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(
                context,
                CloseNotificationBatteryStatusInformationService::class.java
            ),
            PendingIntent.FLAG_IMMUTABLE
        )

        val disable = PendingIntent.getService(
            context,
            DISABLE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(
                context,
                DisableNotificationBatteryStatusInformationService::class.java
            ),
            PendingIntent.FLAG_IMMUTABLE
        )

        isOverheatOvercool = false
        isBatteryFullyCharged = false
        isBatteryCharged = false
        isBatteryDischarged = false
        isBatteryDischargedVoltage = true

        val notificationBuilder = NotificationCompat.Builder(
            context, channelId
        ).apply {

            if (pref.getBoolean(
                    BYPASS_DND, context.resources.getBoolean(
                        R.bool.bypass_dnd_mode
                    )
                )
            )
                setCategory(NotificationCompat.CATEGORY_ALARM)

            setAutoCancel(true)
            setOngoing(false)

            addAction(0, context.getString(R.string.close), close)
            addAction(0, context.getString(R.string.disable), disable)

            priority = NotificationCompat.PRIORITY_MAX

            setSmallIcon(
                when (batteryLevel) {

                    in 2800..3399 -> R.drawable.ic_battery_discharged_9_24dp
                    in 3400..3599 -> R.drawable.ic_battery_is_discharged_20_24dp
                    in 3600..3799 -> R.drawable.ic_battery_is_discharged_30_24dp
                    in 3800..3999 -> R.drawable.ic_battery_is_discharged_50_24dp
                    in 4000..4099 -> R.drawable.ic_battery_is_discharged_60_24dp
                    in 4100..4199 -> R.drawable.ic_battery_is_discharged_80_24dp
                    in 4200..4399 -> R.drawable.ic_battery_is_discharged_100_24dp
                    else -> R.drawable.ic_battery_discharged_9_24dp
                }
            )

            color = ContextCompat.getColor(context, R.color.battery_discharged)

            setContentTitle(context.getString(R.string.battery_status_information))

            setCustomContentView(remoteViewsContent)

            setStyle(NotificationCompat.DecoratedCustomViewStyle())

            setShowWhen(true)

            setLights(Color.RED, 1000, 500)

            setSound(
                ("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                        "${context.packageName}/${R.raw.battery_is_discharged}").toUri()
            )
        }

        notificationManager?.notify(NOTIFICATION_BATTERY_STATUS_ID, notificationBuilder.build())
    }

    private fun onCreateNotificationChannel(context: Context, notificationChannelId: String):
            String {

        val notificationService =
            context.getSystemService(NOTIFICATION_SERVICE) as? NotificationManager

        val soundAttributes = AudioAttributes.Builder().apply {

            setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)

            setUsage(AudioAttributes.USAGE_NOTIFICATION)
        }

        when (notificationChannelId) {

            SERVICE_CHANNEL_ID -> {

                val channelName = context.getString(R.string.service)

                notificationService?.createNotificationChannel(
                    NotificationChannel(
                        notificationChannelId, channelName, NotificationManager.IMPORTANCE_LOW
                    ).apply {

                        setShowBadge(false)
                    })
            }

            OVERHEAT_OVERCOOL_CHANNEL_ID -> {

                val channelName = context.getString(R.string.overheat_overcool)

                notificationService?.createNotificationChannel(
                    NotificationChannel(
                        notificationChannelId, channelName, NotificationManager.IMPORTANCE_HIGH
                    ).apply {

                        setShowBadge(true)

                        setSound(
                            ("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                    "${context.packageName}/${R.raw.overheat_overcool}").toUri(),
                            soundAttributes.build()
                        )
                    })
            }

            FULLY_CHARGED_CHANNEL_ID -> {

                val channelName = context.getString(R.string.fully_charged)

                notificationService?.createNotificationChannel(
                    NotificationChannel(
                        notificationChannelId, channelName, NotificationManager.IMPORTANCE_HIGH
                    ).apply {

                        setShowBadge(true)

                        setSound(
                            ("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                    "${context.packageName}/${R.raw.battery_is_fully_charged}").toUri(),
                            soundAttributes.build()
                        )
                    })
            }

            CHARGED_CHANNEL_ID -> {

                val channelName = context.getString(R.string.charged)

                notificationService?.createNotificationChannel(
                    NotificationChannel(
                        notificationChannelId, channelName, NotificationManager.IMPORTANCE_HIGH
                    ).apply {

                        setShowBadge(true)

                        enableLights(true)

                        lightColor = Color.GREEN

                        setSound(
                            ("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                    "${context.packageName}/${R.raw.battery_is_charged}").toUri(),
                            soundAttributes.build()
                        )
                    })
            }

            DISCHARGED_CHANNEL_ID -> {

                val channelName = context.getString(R.string.discharged)

                notificationService?.createNotificationChannel(
                    NotificationChannel(
                        notificationChannelId, channelName, NotificationManager.IMPORTANCE_HIGH
                    ).apply {

                        setShowBadge(true)

                        enableLights(true)

                        lightColor = Color.RED

                        setSound(
                            ("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                    "${context.packageName}/${R.raw.battery_is_discharged}").toUri(),
                            soundAttributes.build()
                        )
                    })
            }

            DISCHARGED_VOLTAGE_CHANNEL_ID -> {

                val channelName = context.getString(R.string.discharged_voltage)

                notificationService?.createNotificationChannel(
                    NotificationChannel(
                        notificationChannelId, channelName, NotificationManager.IMPORTANCE_HIGH
                    ).apply {

                        setShowBadge(true)

                        enableLights(true)

                        lightColor = Color.RED

                        setSound(
                            ("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                    "${context.packageName}/${R.raw.battery_is_discharged}").toUri(),
                            soundAttributes.build()
                        )
                    })
            }
        }

        return notificationChannelId
    }

    private fun getNotificationMessage(context: Context, status: Int?, remoteViews: RemoteViews) {

        when (status) {

            BatteryManager.BATTERY_STATUS_CHARGING -> getBatteryStatusCharging(
                context,
                batteryIntent, remoteViews
            )

            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> getBatteryStatusNotCharging(
                context,
                remoteViews
            )

            BatteryManager.BATTERY_STATUS_FULL -> getBatteryStatusFull(context, remoteViews)

            BatteryManager.BATTERY_STATUS_DISCHARGING -> getBatteryStatusDischarging(
                context,
                remoteViews
            )

            else -> getBatteryStatusUnknown(context, remoteViews)
        }
    }

    private fun getBatteryStatusCharging(
        context: Context, batteryIntent: Intent?,
        remoteViews: RemoteViews
    ) {

        val batteryLabServiceContext = BatteryLabService.instance

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val isCapacityInWh = pref.getBoolean(
            CAPACITY_IN_WH, context.resources.getBoolean(
                R.bool.capacity_in_wh
            )
        )

        val isChargingDischargeCurrentInWatt = pref.getBoolean(
            PreferencesKeys.CHARGING_DISCHARGE_CURRENT_IN_WATT,
            context.resources.getBoolean(R.bool.charging_discharge_current_in_watt)
        )

        remoteViews.apply {

            setViewVisibility(R.id.number_of_cycles_service_notification, View.GONE)

            setViewVisibility(R.id.charging_time_service_notification, View.VISIBLE)

            setViewVisibility(R.id.source_of_power_service_notification, View.VISIBLE)

            setViewVisibility(
                R.id.current_capacity_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setViewVisibility(
                R.id.residual_capacity_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setViewVisibility(
                R.id.battery_wear_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setTextViewText(
                R.id.status_service_notification, context.getString(
                    R.string.status, context.getString(R.string.charging)
                )
            )

            setTextViewText(
                R.id.battery_level_service_notification, context.getString(
                    R.string.battery_level_with_title, try {
                        "${getBatteryLevel(context)}%"
                    } catch (_: RuntimeException) {
                        R.string.unknown
                    }
                )
            )

            setTextViewText(
                R.id.charging_time_service_notification, getChargingTime(
                    context,
                    batteryLabServiceContext?.seconds ?: 0
                )
            )

            setTextViewText(
                R.id.source_of_power_service_notification,
                context.getString(R.string.plugged_type_title) + ": " + getSourceOfPower(
                    context,
                    batteryIntent?.getIntExtra(
                        BatteryManager.EXTRA_PLUGGED,
                        -1
                    ) ?: -1
                )
            )

            setTextViewText(
                R.id.current_capacity_service_notification, context.getString(
                    if (isCapacityInWh) R.string.current_capacity_wh else R.string.current_capacity,
                    DecimalFormat("#.#").format(
                        if (isCapacityInWh) getCapacityInWh(
                            getCurrentCapacity(context)
                        ) else getCurrentCapacity(context)
                    )
                )
            )

            setTextViewText(
                R.id.capacity_added_service_notification, getCapacityAdded(
                    context
                )
            )

            setTextViewText(
                R.id.residual_capacity_service_notification,
                getResidualCapacity(context)
            )

            setTextViewText(
                R.id.battery_wear_service_notification, getBatteryWear(
                    context
                )
            )

            if (isChargingDischargeCurrentInWatt)
                setTextViewText(
                    R.id.charge_discharge_current_service_notification,
                    context.getString(
                        R.string.charge_current_watt,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                getChargeDischargeCurrent(context), true
                            )
                        )
                    )
                )
            else setTextViewText(
                R.id.charge_discharge_current_service_notification,
                context.getString(
                    R.string.charge_current,
                    "${getChargeDischargeCurrent(context)}"
                )
            )

            setTextViewText(
                R.id.temperature_service_notification, context.getString(
                    R.string
                        .temperature_with_title,
                    DecimalFormat().format(getTemperatureInCelsius(context)),
                    DecimalFormat().format(getTemperatureInFahrenheit(context))
                )
            )

            setTextViewText(
                R.id.voltage_service_notification, context.getString(
                    R.string.voltage,
                    DecimalFormat("#.#").format(getVoltage(context))
                )
            )
        }
    }

    private fun getBatteryStatusNotCharging(context: Context, remoteViews: RemoteViews) {

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val batteryLabServiceContext = BatteryLabService.instance

        val isCapacityInWh = pref.getBoolean(
            CAPACITY_IN_WH, context.resources.getBoolean(
                R.bool.capacity_in_wh
            )
        )

        val isChargingDischargeCurrentInWatt = pref.getBoolean(
            PreferencesKeys.CHARGING_DISCHARGE_CURRENT_IN_WATT,
            context.resources.getBoolean(R.bool.charging_discharge_current_in_watt)
        )

        remoteViews.apply {

            setViewVisibility(R.id.charging_time_service_notification, View.VISIBLE)

            setViewVisibility(
                R.id.current_capacity_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setViewVisibility(
                R.id.residual_capacity_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setViewVisibility(
                R.id.battery_wear_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setTextViewText(
                R.id.status_service_notification, context.getString(
                    R.string.status, context.getString(R.string.not_charging)
                )
            )

            setTextViewText(
                R.id.battery_level_service_notification, context.getString(
                    R.string.battery_level_with_title, try {
                        "${getBatteryLevel(context)}%"
                    } catch (_: RuntimeException) {
                        R.string.unknown
                    }
                )
            )

            setTextViewText(
                R.id.number_of_cycles_service_notification, context.getString(
                    R.string.number_of_cycles, DecimalFormat("#.##").format(
                        pref.getFloat(
                            NUMBER_OF_CYCLES, 0f
                        )
                    )
                )
            )

            setTextViewText(
                R.id.charging_time_service_notification, getChargingTime(
                    context,
                    batteryLabServiceContext?.seconds ?: 0
                )
            )

            setTextViewText(
                R.id.current_capacity_service_notification, context.getString(
                    if (isCapacityInWh) R.string.current_capacity_wh else R.string.current_capacity,
                    DecimalFormat("#.#").format(
                        if (isCapacityInWh) getCapacityInWh(
                            getCurrentCapacity(context)
                        ) else getCurrentCapacity(context)
                    )
                )
            )

            setTextViewText(
                R.id.capacity_added_service_notification, getCapacityAdded(
                    context
                )
            )

            setTextViewText(
                R.id.residual_capacity_service_notification,
                getResidualCapacity(context)
            )

            setTextViewText(
                R.id.battery_wear_service_notification, getBatteryWear(
                    context
                )
            )

            if (isChargingDischargeCurrentInWatt)
                setTextViewText(
                    R.id.charge_discharge_current_service_notification,
                    context.getString(
                        R.string.discharge_current_watt,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                getChargeDischargeCurrent(context)
                            )
                        )
                    )
                )
            else setTextViewText(
                R.id.charge_discharge_current_service_notification,
                context.getString(
                    R.string.discharge_current,
                    "${getChargeDischargeCurrent(context)}"
                )
            )

            setTextViewText(
                R.id.temperature_service_notification, context.getString(
                    R.string
                        .temperature_with_title,
                    DecimalFormat().format(getTemperatureInCelsius(context)),
                    DecimalFormat().format(getTemperatureInFahrenheit(context))
                )
            )

            setTextViewText(
                R.id.voltage_service_notification, context.getString(
                    R.string.voltage,
                    DecimalFormat("#.#").format(getVoltage(context))
                )
            )
        }
    }

    private fun getBatteryStatusFull(context: Context, remoteViews: RemoteViews) {

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val batteryLabServiceContext = BatteryLabService.instance

        val isCapacityInWh = pref.getBoolean(
            CAPACITY_IN_WH, context.resources.getBoolean(
                R.bool.capacity_in_wh
            )
        )

        val isChargingDischargeCurrentInWatt = pref.getBoolean(
            PreferencesKeys.CHARGING_DISCHARGE_CURRENT_IN_WATT,
            context.resources.getBoolean(R.bool.charging_discharge_current_in_watt)
        )

        remoteViews.apply {

            setViewVisibility(R.id.charging_time_service_notification, View.VISIBLE)

            setViewVisibility(
                R.id.current_capacity_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setViewVisibility(
                R.id.residual_capacity_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setViewVisibility(
                R.id.battery_wear_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setTextViewText(
                R.id.status_service_notification, context.getString(
                    R.string.status, context.getString(R.string.full)
                )
            )

            setTextViewText(
                R.id.battery_level_service_notification, context.getString(
                    R.string.battery_level_with_title, try {
                        "${getBatteryLevel(context)}%"
                    } catch (_: RuntimeException) {
                        R.string.unknown
                    }
                )
            )

            setTextViewText(
                R.id.number_of_cycles_service_notification, context.getString(
                    R.string.number_of_cycles, DecimalFormat("#.##").format(
                        pref.getFloat(
                            NUMBER_OF_CYCLES, 0f
                        )
                    )
                )
            )

            setTextViewText(
                R.id.charging_time_service_notification, getChargingTime(
                    context,
                    batteryLabServiceContext?.seconds ?: 0
                )
            )

            setTextViewText(
                R.id.current_capacity_service_notification, context.getString(
                    if (isCapacityInWh) R.string.current_capacity_wh else R.string.current_capacity,
                    DecimalFormat("#.#").format(
                        if (isCapacityInWh) getCapacityInWh(
                            getCurrentCapacity(context)
                        ) else getCurrentCapacity(context)
                    )
                )
            )

            setTextViewText(
                R.id.capacity_added_service_notification, getCapacityAdded(
                    context
                )
            )

            setTextViewText(
                R.id.residual_capacity_service_notification,
                getResidualCapacity(context)
            )

            setTextViewText(
                R.id.battery_wear_service_notification, getBatteryWear(
                    context
                )
            )

            if (isChargingDischargeCurrentInWatt)
                setTextViewText(
                    R.id.charge_discharge_current_service_notification,
                    context.getString(
                        R.string.discharge_current_watt,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                getChargeDischargeCurrent(context)
                            )
                        )
                    )
                )
            else setTextViewText(
                R.id.charge_discharge_current_service_notification,
                context.getString(
                    R.string.discharge_current,
                    "${getChargeDischargeCurrent(context)}"
                )
            )

            setTextViewText(
                R.id.temperature_service_notification, context.getString(
                    R.string
                        .temperature_with_title,
                    DecimalFormat().format(getTemperatureInCelsius(context)),
                    DecimalFormat().format(getTemperatureInFahrenheit(context))
                )
            )

            setTextViewText(
                R.id.voltage_service_notification, context.getString(
                    R.string.voltage,
                    DecimalFormat("#.#").format(getVoltage(context))
                )
            )
        }
    }

    private fun getBatteryStatusDischarging(context: Context, remoteViews: RemoteViews) {

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val isCapacityInWh = pref.getBoolean(
            CAPACITY_IN_WH, context.resources.getBoolean(
                R.bool.capacity_in_wh
            )
        )

        val isChargingDischargeCurrentInWatt = pref.getBoolean(
            PreferencesKeys.CHARGING_DISCHARGE_CURRENT_IN_WATT,
            context.resources.getBoolean(R.bool.charging_discharge_current_in_watt)
        )

        remoteViews.apply {

            setViewVisibility(
                R.id.current_capacity_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setViewVisibility(
                R.id.residual_capacity_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setViewVisibility(
                R.id.battery_wear_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setTextViewText(
                R.id.status_service_notification, context.getString(
                    R.string.status, context.getString(R.string.discharging)
                )
            )

            setTextViewText(
                R.id.battery_level_service_notification, context.getString(
                    R.string.battery_level_with_title, try {
                        "${getBatteryLevel(context)}%"
                    } catch (_: RuntimeException) {
                        R.string.unknown
                    }
                )
            )

            setTextViewText(
                R.id.number_of_cycles_service_notification, context.getString(
                    R.string.number_of_cycles, DecimalFormat("#.##").format(
                        pref.getFloat(
                            NUMBER_OF_CYCLES, 0f
                        )
                    )
                )
            )

            setTextViewText(
                R.id.current_capacity_service_notification, context.getString(
                    if (isCapacityInWh) R.string.current_capacity_wh else R.string.current_capacity,
                    DecimalFormat("#.#").format(
                        if (isCapacityInWh) getCapacityInWh(
                            getCurrentCapacity(context)
                        ) else getCurrentCapacity(context)
                    )
                )
            )

            setTextViewText(
                R.id.capacity_added_service_notification, getCapacityAdded(
                    context
                )
            )

            setTextViewText(
                R.id.residual_capacity_service_notification,
                getResidualCapacity(context)
            )

            setTextViewText(
                R.id.battery_wear_service_notification, getBatteryWear(
                    context
                )
            )

            if (isChargingDischargeCurrentInWatt)
                setTextViewText(
                    R.id.charge_discharge_current_service_notification,
                    context.getString(
                        R.string.discharge_current_watt,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                getChargeDischargeCurrent(context)
                            )
                        )
                    )
                )
            else setTextViewText(
                R.id.charge_discharge_current_service_notification,
                context.getString(
                    R.string.discharge_current,
                    "${getChargeDischargeCurrent(context)}"
                )
            )

            setTextViewText(
                R.id.temperature_service_notification, context.getString(
                    R.string
                        .temperature_with_title,
                    DecimalFormat().format(getTemperatureInCelsius(context)),
                    DecimalFormat().format(getTemperatureInFahrenheit(context))
                )
            )

            setTextViewText(
                R.id.voltage_service_notification, context.getString(
                    R.string.voltage,
                    DecimalFormat("#.#").format(getVoltage(context))
                )
            )
        }
    }

    private fun getBatteryStatusUnknown(context: Context, remoteViews: RemoteViews) {

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        val batteryLabServiceContext = BatteryLabService.instance

        val isChargingDischargeCurrentInWatt = pref.getBoolean(
            PreferencesKeys.CHARGING_DISCHARGE_CURRENT_IN_WATT,
            context.resources.getBoolean(R.bool.charging_discharge_current_in_watt)
        )

        remoteViews.apply {

            setViewVisibility(R.id.charging_time_service_notification, View.VISIBLE)

            setViewVisibility(
                R.id.current_capacity_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setViewVisibility(
                R.id.residual_capacity_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setViewVisibility(
                R.id.battery_wear_service_notification,
                if (getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE
            )

            setTextViewText(
                R.id.status_service_notification, context.getString(
                    R.string.status, context.getString(R.string.unknown)
                )
            )

            setTextViewText(
                R.id.battery_level_service_notification, context.getString(
                    R.string.battery_level_with_title, try {
                        "${getBatteryLevel(context)}%"
                    } catch (_: RuntimeException) {
                        R.string.unknown
                    }
                )
            )

            setTextViewText(
                R.id.number_of_cycles_service_notification, context.getString(
                    R.string.number_of_cycles, DecimalFormat("#.##").format(
                        pref.getFloat(
                            NUMBER_OF_CYCLES, 0f
                        )
                    )
                )
            )

            setTextViewText(
                R.id.charging_time_service_notification, getChargingTime(
                    context,
                    batteryLabServiceContext?.seconds ?: 0
                )
            )

            setTextViewText(
                R.id.current_capacity_service_notification, context.getString(
                    R.string.current_capacity, DecimalFormat("#.#").format(
                        getCurrentCapacity(
                            context
                        )
                    )
                )
            )

            setTextViewText(
                R.id.capacity_added_service_notification, getCapacityAdded(
                    context
                )
            )

            setTextViewText(
                R.id.residual_capacity_service_notification,
                getResidualCapacity(context)
            )

            setTextViewText(
                R.id.battery_wear_service_notification, getBatteryWear(
                    context
                )
            )

            if (isChargingDischargeCurrentInWatt)
                setTextViewText(
                    R.id.charge_discharge_current_service_notification,
                    context.getString(
                        R.string.discharge_current_watt,
                        DecimalFormat("#.##").format(
                            getChargeDischargeCurrentInWatt(
                                getChargeDischargeCurrent(context)
                            )
                        )
                    )
                )
            else setTextViewText(
                R.id.charge_discharge_current_service_notification,
                context.getString(
                    R.string.discharge_current,
                    "${getChargeDischargeCurrent(context)}"
                )
            )

            setTextViewText(
                R.id.temperature_service_notification, context.getString(
                    R.string
                        .temperature_with_title,
                    DecimalFormat().format(getTemperatureInCelsius(context)),
                    DecimalFormat().format(getTemperatureInFahrenheit(context))
                )
            )

            setTextViewText(
                R.id.voltage_service_notification, context.getString(
                    R.string.voltage,
                    DecimalFormat("#.#").format(getVoltage(context))
                )
            )
        }
    }

    private fun isNotificationExists(context: Context, notificationID: Int): Boolean {
        val nm = context.getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        val list = try {
            nm?.activeNotifications
        } catch (_: Throwable) {
            null
        }
        return list?.any { it.id == notificationID } ?: false
    }
}
