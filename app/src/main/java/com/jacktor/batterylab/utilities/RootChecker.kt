package com.jacktor.batterylab.utilities

import java.io.File

class RootChecker {
    fun isDeviceRooted(): Boolean {
        val files = arrayOf(
            "/system/xbin/su",
            "/system/bin/su",
            "/system/app/Superuser.apk",
            "/system/xbin/busybox",
            "/system/sd/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/su/bin/su",
            "/magisk/.core",
            "/sbin/su",
        )
        for (file in files) {
            if (File(file).exists())
                return true
        }
        return false
    }
}
