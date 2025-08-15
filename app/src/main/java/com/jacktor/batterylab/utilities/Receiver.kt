package com.jacktor.batterylab.utilities

import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.jacktor.batterylab.receivers.PowerConnectionReceiver

object Receiver {

    fun register(context: Context, receiver: PowerConnectionReceiver, filter: IntentFilter) {
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregister(context: Context, receiver: PowerConnectionReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            //Log.d("Receiver", "Receiver is not registered")
        }
    }
}