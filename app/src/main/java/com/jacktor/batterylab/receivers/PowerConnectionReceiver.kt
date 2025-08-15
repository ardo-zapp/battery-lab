package com.jacktor.batterylab.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.R
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.AC_CONNECTED_SOUND
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.CUSTOM_VIBRATE_DURATION
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DISCONNECTED_SOUND
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.ENABLE_TOAST
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.ENABLE_VIBRATION
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.SOUND_DELAY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.USB_CONNECTED_SOUND
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.VIBRATE_MODE

class PowerConnectionReceiver : BroadcastReceiver() {

    private var prefs: SharedPreferences? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onReceive(context: Context, intent: Intent) {
        val pr = goAsync()
        initPrefs(context)

        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> handlePowerChange(context, isConnected = true, pr)
            Intent.ACTION_POWER_DISCONNECTED -> handlePowerChange(context, isConnected = false, pr)
            else -> pr.finish()
        }
    }

    private fun handlePowerChange(context: Context, isConnected: Boolean, pr: PendingResult) {
        val p = prefs ?: run { pr.finish(); return }

        val vibrationMode = p.getString(VIBRATE_MODE, "disconnected") ?: "disconnected"
        val duration = (p.getString(CUSTOM_VIBRATE_DURATION, "450")?.toLongOrNull() ?: 450L)
            .coerceIn(50L, 3000L)
        val delay = (p.getString(SOUND_DELAY, "550")?.toLongOrNull() ?: 550L)
            .coerceIn(0L, 5000L)

        if (p.getBoolean(ENABLE_VIBRATION, true)) {
            handleVibration(context, duration, isConnected, vibrationMode)
        }

        // main-thread delayed sound & toast
        mainHandler.postDelayed({
            if (isConnected) playPowerConnectedSound(context) else playPowerDisconnectedSound(
                context
            )

            if (p.getBoolean(ENABLE_TOAST, false)) showToast(context, isConnected)

            pr.finish()
        }, delay)
    }

    private fun handleVibration(
        context: Context,
        duration: Long,
        isConnected: Boolean,
        vibrationMode: String
    ) {
        val shouldVibrate =
            (isConnected && (vibrationMode == "connected" || vibrationMode == "both")) ||
                    (!isConnected && (vibrationMode == "disconnected" || vibrationMode == "both"))
        if (!shouldVibrate) return

        val vibrator = getVibratorCompat(context) ?: return
        try {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } catch (_: SecurityException) {
        }
    }

    private fun getVibratorCompat(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun playPowerConnectedSound(context: Context) {
        val prefKey = if (isAcConnected(context)) AC_CONNECTED_SOUND else USB_CONNECTED_SOUND
        playSoundFromPrefs(context, prefKey)
    }

    private fun playPowerDisconnectedSound(context: Context) {
        playSoundFromPrefs(context, DISCONNECTED_SOUND)
    }

    private fun playSoundFromPrefs(context: Context, prefKey: String) {
        val filePath = prefs?.getString(prefKey, "").orEmpty()
        if (filePath.isBlank()) return

        try {
            val uri = filePath.toUri()
            val ringtone: Ringtone? = RingtoneManager.getRingtone(context, uri)
            if (ringtone != null) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                ringtone.play()
            }
        } catch (_: Throwable) {
        }
    }

    private fun isAcConnected(context: Context): Boolean {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return plugged == BatteryManager.BATTERY_PLUGGED_AC
    }

    private fun showToast(context: Context, isConnected: Boolean) {
        val msgRes =
            if (isConnected) R.string.toast_power_connected else R.string.toast_power_disconnected
        Toast.makeText(context.applicationContext, msgRes, Toast.LENGTH_LONG).show()
    }

    private fun initPrefs(context: Context) {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        }
    }
}
