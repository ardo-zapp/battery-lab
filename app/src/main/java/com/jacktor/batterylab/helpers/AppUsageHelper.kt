package com.jacktor.batterylab.helpers

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.jacktor.batterylab.R
import com.jacktor.batterylab.interfaces.BatteryInfoInterface
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.DESIGN_CAPACITY
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_ACTIVE_SCREEN_DURATION_MS
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys.IDLE_DRAIN_ACTIVE_SCREEN_MAH
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class AppUsageWindow {
    TODAY,
    LAST_24H,
    LAST_7D
}

enum class DrainSeverity {
    EXTREME,
    HIGH,
    MODERATE,
    LOW
}

data class AppPowerEntry(
    val rank: Int,
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemHeadless: Boolean,
    val foregroundTimeMs: Long,
    val backgroundServiceMs: Long,
    val launchCount: Int,
    val lastTimeUsedMs: Long,
    val estimatedMah: Double,
    val estimatedWh: Double,
    val powerSharePercent: Double,
    val batteryPercentEquiv: Double,
    val drainRateMahPerHour: Double,
    val severity: DrainSeverity
)

data class AppPowerReport(
    val hasPermission: Boolean,
    val window: AppUsageWindow,
    val entries: List<AppPowerEntry>,
    val totalEstimatedMah: Double,
    val totalBatteryPercentEquiv: Double,
    val totalForegroundMs: Long,
    val totalBackgroundMs: Long,
    val trackedPackagesCount: Int,
    val designCapacityMah: Int
)

/**
 * Estimates per-application battery consumption and drain intensity using Android's
 * [UsageStatsManager] event timelines, app workload categories, and hardware-calibrated
 * discharge telemetry from Battery Lab without requiring root access.
 */
object AppUsageHelper {

    private data class EventPackageStats(
        var foregroundMs: Long = 0L,
        var bgServiceMs: Long = 0L,
        var launches: Int = 0,
        var lastTimeUsedMs: Long = 0L
    )

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context): Boolean {
        val packageSpecific = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(packageSpecific)
            true
        } catch (_: Throwable) {
            try {
                val generic = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(generic)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun openAppInfoSettings(context: Context, packageName: String): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun queryAppPowerReport(
        context: Context,
        window: AppUsageWindow,
        includeSystemApps: Boolean
    ): AppPowerReport {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val designCapacityMah = pref.getInt(
            DESIGN_CAPACITY,
            context.resources.getInteger(R.integer.min_design_capacity)
        ).coerceAtLeast(1000)

        if (!hasUsageStatsPermission(context)) {
            return AppPowerReport(
                hasPermission = false,
                window = window,
                entries = emptyList(),
                totalEstimatedMah = 0.0,
                totalBatteryPercentEquiv = 0.0,
                totalForegroundMs = 0L,
                totalBackgroundMs = 0L,
                trackedPackagesCount = 0,
                designCapacityMah = designCapacityMah
            )
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return AppPowerReport(
                hasPermission = true,
                window = window,
                entries = emptyList(),
                totalEstimatedMah = 0.0,
                totalBatteryPercentEquiv = 0.0,
                totalForegroundMs = 0L,
                totalBackgroundMs = 0L,
                trackedPackagesCount = 0,
                designCapacityMah = designCapacityMah
            )

        val endTime = System.currentTimeMillis()
        val startTime = resolveStartTime(window, endTime)
        val windowDurationMs = (endTime - startTime).coerceAtLeast(1L)
        val selfPackage = context.packageName
        val pm = context.packageManager

        val eventTelemetry = collectEventTelemetry(usm, startTime, endTime, selfPackage)
        val dailyBucketStats = collectDailyBucketFallback(usm, startTime, endTime, selfPackage)
        val unproratedBucketUpperBounds = collectUnproratedBucketBounds(usm, startTime, endTime, selfPackage)

        val allCandidatePackages = LinkedHashSet<String>().apply {
            addAll(eventTelemetry.keys)
            addAll(dailyBucketStats.keys)
            remove(selfPackage)
        }

        // Calibrate baseline screen-on current (mA) using actual hardware measurements recorded by Battery Lab
        val baselineActiveMa = resolveCalibratedActiveCurrentMa(context, designCapacityMah)
        val nominalVoltageV = (BatteryInfoInterface.lastMeasuredVoltageV)
            .takeIf { it in 3.2..4.55 } ?: 3.85

        data class RawPackageMetric(
            val packageName: String,
            val appName: String,
            val icon: Drawable?,
            val isSystemHeadless: Boolean,
            val fgMs: Long,
            val bgServiceMs: Long,
            val launches: Int,
            val lastUsedMs: Long,
            val estimatedMah: Double,
            val rateMahPerHour: Double
        )

        val rawMetrics = mutableListOf<RawPackageMetric>()
        var totalEstimatedMahAll = 0.0

        for (pkg in allCandidatePackages) {
            if (pkg == selfPackage) continue

            val ev = eventTelemetry[pkg]
            val bucket = dailyBucketStats[pkg]
            val upperBound = unproratedBucketUpperBounds[pkg]

            val evFgMs = ev?.foregroundMs ?: 0L
            val bucketFgMs = bucket?.foregroundMs ?: 0L
            val maxBucketFgMs = upperBound?.foregroundMs ?: 0L

            // Bound UsageEvents replay by kernel UsageStats totalTimeInForeground to absorb missing pause events.
            val rawFgMs = when {
                window == AppUsageWindow.LAST_7D -> max(evFgMs, bucketFgMs)
                evFgMs > 0L && maxBucketFgMs > 0L -> min(evFgMs, maxBucketFgMs)
                evFgMs > 0L || eventTelemetry.isNotEmpty() -> evFgMs
                else -> bucketFgMs
            }
            val fgMs = rawFgMs.coerceIn(0L, windowDurationMs)

            val evBgMs = ev?.bgServiceMs ?: 0L
            val bucketBgMs = bucket?.bgServiceMs ?: 0L
            val maxBucketBgMs = upperBound?.bgServiceMs ?: 0L
            val rawBgMs = when {
                window == AppUsageWindow.LAST_7D -> max(evBgMs, bucketBgMs)
                evBgMs > 0L && maxBucketBgMs > 0L -> min(evBgMs, maxBucketBgMs)
                evBgMs > 0L || eventTelemetry.isNotEmpty() -> evBgMs
                else -> bucketBgMs
            }.coerceIn(0L, windowDurationMs)

            // Discount FGS time that occurred while the app was already actively visible on screen
            val bgServiceMs = (rawBgMs - (fgMs * 0.45).toLong()).coerceAtLeast(0L)

            val launches = ev?.launches ?: 0
            if (fgMs < 15_000L && bgServiceMs < 30_000L && launches < 2) continue

            val appInfo = resolveApplicationInfo(pm, pkg) ?: continue
            val isSystemFlag = (appInfo.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            val hasLauncher = pm.getLaunchIntentForPackage(pkg) != null
            val isHeadlessSystem = isSystemFlag && !hasLauncher
            if (!includeSystemApps && isHeadlessSystem) continue

            val appName = try {
                pm.getApplicationLabel(appInfo).toString().ifBlank { pkg }
            } catch (_: Throwable) {
                pkg
            }

            val icon = try {
                val rawIcon = pm.getApplicationIcon(appInfo)
                rawIcon.constantState?.newDrawable(context.resources)?.mutate() ?: rawIcon.mutate()
            } catch (_: Throwable) {
                null
            }

            val (fgWorkloadFactor, bgBaseMa) = resolveWorkloadProfile(appInfo, pkg)
            val fgHours = fgMs / 3_600_000.0
            val bgHours = bgServiceMs / 3_600_000.0

            // Frequent app launches/switching add CPU frequency ramp & wake overhead
            val wakeChurnFactor = if (launches > 1 && fgHours > 0.01) {
                val launchesPerHour = launches / fgHours
                1.0 + (launchesPerHour / 65.0).coerceIn(0.0, 0.22)
            } else {
                1.0
            }

            val effectiveFgCurrentMa = baselineActiveMa * fgWorkloadFactor * wakeChurnFactor
            val estimatedMah = (fgHours * effectiveFgCurrentMa) +
                (bgHours * bgBaseMa) +
                (launches * 0.010)
            if (estimatedMah < 0.3) continue

            val activeOrBgHours = (fgHours + bgHours).coerceAtLeast(1.0 / 60.0)
            val rateMahPerHour = estimatedMah / activeOrBgHours
            val lastUsedMs = max(ev?.lastTimeUsedMs ?: 0L, bucket?.lastTimeUsedMs ?: 0L)

            totalEstimatedMahAll += estimatedMah
            rawMetrics += RawPackageMetric(
                packageName = pkg,
                appName = appName,
                icon = icon,
                isSystemHeadless = isHeadlessSystem,
                fgMs = fgMs,
                bgServiceMs = bgServiceMs,
                launches = launches,
                lastUsedMs = lastUsedMs,
                estimatedMah = estimatedMah,
                rateMahPerHour = rateMahPerHour
            )
        }

        val sorted = rawMetrics.sortedByDescending { it.estimatedMah }
        val denominator = totalEstimatedMahAll.coerceAtLeast(0.001)
        val entries = sorted.mapIndexed { index, item ->
            val sharePct = (item.estimatedMah / denominator) * 100.0
            val batteryPctEquiv = (item.estimatedMah / designCapacityMah.toDouble()) * 100.0
            val estimatedWh = (item.estimatedMah / 1000.0) * nominalVoltageV
            val severity = classifySeverity(
                window = window,
                batteryPctEquiv = batteryPctEquiv,
                rateMahPerHour = item.rateMahPerHour,
                baselineActiveMa = baselineActiveMa,
                bgServiceMs = item.bgServiceMs,
                fgMs = item.fgMs
            )
            AppPowerEntry(
                rank = index + 1,
                packageName = item.packageName,
                appName = item.appName,
                icon = item.icon,
                isSystemHeadless = item.isSystemHeadless,
                foregroundTimeMs = item.fgMs,
                backgroundServiceMs = item.bgServiceMs,
                launchCount = item.launches,
                lastTimeUsedMs = item.lastUsedMs,
                estimatedMah = item.estimatedMah,
                estimatedWh = estimatedWh,
                powerSharePercent = sharePct,
                batteryPercentEquiv = batteryPctEquiv,
                drainRateMahPerHour = item.rateMahPerHour,
                severity = severity
            )
        }

        return AppPowerReport(
            hasPermission = true,
            window = window,
            entries = entries,
            totalEstimatedMah = totalEstimatedMahAll,
            totalBatteryPercentEquiv = (totalEstimatedMahAll / designCapacityMah.toDouble()) * 100.0,
            totalForegroundMs = entries.sumOf { it.foregroundTimeMs },
            totalBackgroundMs = entries.sumOf { it.backgroundServiceMs },
            trackedPackagesCount = entries.size,
            designCapacityMah = designCapacityMah
        )
    }

    /**
     * Replays UsageEvents strictly inside [startTime, endTime] to avoid overlapping daily-bucket
     * bleed-over and closes foreground sessions whenever the screen turns off or keyguard locks.
     */
    private fun collectEventTelemetry(
        usm: UsageStatsManager,
        startTime: Long,
        endTime: Long,
        selfPackage: String
    ): Map<String, EventPackageStats> {
        val map = HashMap<String, EventPackageStats>()
        val events = try {
            usm.queryEvents(startTime, endTime)
        } catch (_: Throwable) {
            null
        } ?: return map

        val event = UsageEvents.Event()
        var activePkg: String? = null
        var activeStartMs = 0L
        var lastLaunchedPkg: String? = null
        var seenAnyFgBoundary = false
        val activeFgsStartMs = HashMap<String, Long>()

        val maxSingleSessionMs = 2L * 3_600_000L

        fun closeActiveForeground(untilMs: Long) {
            val pkg = activePkg ?: return
            if (pkg != selfPackage && untilMs > activeStartMs) {
                val stat = map.getOrPut(pkg) { EventPackageStats() }
                stat.foregroundMs += (untilMs - activeStartMs).coerceAtMost(maxSingleSessionMs)
                if (untilMs > stat.lastTimeUsedMs) stat.lastTimeUsedMs = untilMs
            }
            activePkg = null
            activeStartMs = 0L
            seenAnyFgBoundary = true
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp.coerceIn(startTime, endTime)
            val pkg = event.packageName
            val type = event.eventType

            val isFgResumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                type == UsageEvents.Event.MOVE_TO_FOREGROUND
            }

            val isFgPaused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED
            } else {
                @Suppress("DEPRECATION")
                type == UsageEvents.Event.MOVE_TO_BACKGROUND
            }

            when {
                isFgResumed && !pkg.isNullOrBlank() -> {
                    if (activePkg != pkg) {
                        closeActiveForeground(ts)
                        activePkg = pkg
                        activeStartMs = ts
                    }
                    seenAnyFgBoundary = true
                    if (pkg != selfPackage) {
                        val stat = map.getOrPut(pkg) { EventPackageStats() }
                        if (pkg != lastLaunchedPkg) {
                            stat.launches += 1
                            lastLaunchedPkg = pkg
                        }
                        if (ts > stat.lastTimeUsedMs) stat.lastTimeUsedMs = ts
                    }
                }

                isFgPaused && !pkg.isNullOrBlank() -> {
                    if (activePkg == pkg) {
                        closeActiveForeground(ts)
                    } else if (!seenAnyFgBoundary && activePkg == null && pkg != selfPackage && ts > startTime) {
                        // App was already in foreground across the window start boundary
                        val stat = map.getOrPut(pkg) { EventPackageStats() }
                        stat.foregroundMs += (ts - startTime)
                        if (ts > stat.lastTimeUsedMs) stat.lastTimeUsedMs = ts
                        seenAnyFgBoundary = true
                    }
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    type == UsageEvents.Event.FOREGROUND_SERVICE_START &&
                    !pkg.isNullOrBlank() -> {
                    if (pkg != selfPackage) {
                        activeFgsStartMs.putIfAbsent(pkg, ts)
                    }
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    type == UsageEvents.Event.FOREGROUND_SERVICE_STOP &&
                    !pkg.isNullOrBlank() -> {
                    val fgsStart = activeFgsStartMs.remove(pkg)
                    if (pkg != selfPackage && fgsStart != null && ts > fgsStart) {
                        val stat = map.getOrPut(pkg) { EventPackageStats() }
                        stat.bgServiceMs += (ts - fgsStart)
                    }
                }

                type == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                    type == UsageEvents.Event.KEYGUARD_SHOWN ||
                    type == UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    closeActiveForeground(ts)
                    lastLaunchedPkg = null
                }
            }
        }

        // Cap any still-open foreground / foreground-service interval at endTime
        closeActiveForeground(endTime)
        val maxSingleFgsMs = 3L * 3_600_000L
        for ((pkg, fgsStart) in activeFgsStartMs) {
            if (pkg != selfPackage && endTime > fgsStart) {
                val stat = map.getOrPut(pkg) { EventPackageStats() }
                stat.bgServiceMs += (endTime - fgsStart).coerceAtMost(maxSingleFgsMs)
            }
        }

        return map
    }

    /**
     * Collects unprorated daily bucket totals across buckets overlapping [startTime, endTime]
     * using strict `totalTimeInForeground` as a physical upper bound so missing pause events
     * in `UsageEvents` can never inflate foreground time beyond the Android kernel's per-activity counter.
     */
    private fun collectUnproratedBucketBounds(
        usm: UsageStatsManager,
        startTime: Long,
        endTime: Long,
        selfPackage: String
    ): Map<String, EventPackageStats> {
        val result = HashMap<String, EventPackageStats>()
        val statsList = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        } catch (_: Throwable) {
            null
        } ?: return result

        for (bucket in statsList) {
            val pkg = bucket.packageName ?: continue
            if (pkg == selfPackage) continue
            if (bucket.lastTimeStamp < startTime || bucket.firstTimeStamp > endTime) continue

            val rawFgMs = bucket.totalTimeInForeground.coerceAtLeast(0L)
            val rawBgMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bucket.totalTimeForegroundServiceUsed.coerceAtLeast(0L)
            } else {
                0L
            }

            val stat = result.getOrPut(pkg) { EventPackageStats() }
            stat.foregroundMs += rawFgMs
            stat.bgServiceMs += rawBgMs
            if (bucket.lastTimeUsed > stat.lastTimeUsedMs) {
                stat.lastTimeUsedMs = bucket.lastTimeUsed
            }
        }
        return result
    }

    private fun collectDailyBucketFallback(
        usm: UsageStatsManager,
        startTime: Long,
        endTime: Long,
        selfPackage: String
    ): Map<String, EventPackageStats> {
        val result = HashMap<String, EventPackageStats>()
        val statsList = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        } catch (_: Throwable) {
            null
        } ?: return result

        for (bucket in statsList) {
            val pkg = bucket.packageName ?: continue
            if (pkg == selfPackage) continue
            if (bucket.lastTimeStamp < startTime || bucket.firstTimeStamp > endTime) continue

            val spanMs = (bucket.lastTimeStamp - bucket.firstTimeStamp).coerceAtLeast(1L)
            val overlapMs = (minOf(bucket.lastTimeStamp, endTime) - maxOf(bucket.firstTimeStamp, startTime))
                .coerceAtLeast(0L)
            val overlapRatio = (overlapMs.toDouble() / spanMs.toDouble()).coerceIn(0.0, 1.0)
            if (overlapRatio <= 0.0) continue

            val rawFgMs = bucket.totalTimeInForeground.coerceAtLeast(0L)
            val rawBgMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bucket.totalTimeForegroundServiceUsed.coerceAtLeast(0L)
            } else {
                0L
            }

            val stat = result.getOrPut(pkg) { EventPackageStats() }
            stat.foregroundMs += (rawFgMs * overlapRatio).toLong()
            stat.bgServiceMs += (rawBgMs * overlapRatio).toLong()
            if (bucket.lastTimeUsed > stat.lastTimeUsedMs) {
                stat.lastTimeUsedMs = bucket.lastTimeUsed
            }
        }
        return result
    }

    /**
     * Resolves calibrated screen-on discharge baseline current (mA) by blending Battery Lab's
     * hardware active-screen tracking, measured discharge current, and battery capacity.
     */
    private fun resolveCalibratedActiveCurrentMa(context: Context, designCapacityMah: Int): Double {
        val capacityBaseline = (designCapacityMah * 0.085).coerceIn(260.0, 480.0)
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val activeScreenMah = pref.getFloat(IDLE_DRAIN_ACTIVE_SCREEN_MAH, 0f).toDouble()
        val activeScreenMs = pref.getLong(IDLE_DRAIN_ACTIVE_SCREEN_DURATION_MS, 0L)

        val measuredScreenMa = if (activeScreenMs >= 5 * 60_000L && activeScreenMah > 4.0) {
            (activeScreenMah / (activeScreenMs / 3_600_000.0)).coerceIn(220.0, 900.0)
        } else {
            null
        }

        val liveStableMa = when {
            BatteryInfoInterface.Companion.TimeEstimator.stableDischargeMa in 180.0..1500.0 ->
                BatteryInfoInterface.Companion.TimeEstimator.stableDischargeMa
            BatteryInfoInterface.averageDischargeCurrent in 180..1500 ->
                BatteryInfoInterface.averageDischargeCurrent.toDouble()
            else -> null
        }

        return when {
            measuredScreenMa != null && liveStableMa != null ->
                (measuredScreenMa * 0.65) + (liveStableMa * 0.35)
            measuredScreenMa != null -> measuredScreenMa
            liveStableMa != null -> (liveStableMa * 0.60) + (capacityBaseline * 0.40)
            else -> capacityBaseline
        }.coerceIn(240.0, 820.0)
    }

    /**
     * Maps Android app category and workload clues to (foreground multiplier, background service mA).
     */
    private fun resolveWorkloadProfile(appInfo: ApplicationInfo, pkg: String): Pair<Double, Double> {
        @Suppress("DEPRECATION")
        val legacyIsGame = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        val lowerPkg = pkg.lowercase(Locale.ROOT)

        if (legacyIsGame || appInfo.category == ApplicationInfo.CATEGORY_GAME) {
            return 1.65 to 55.0
        }

        return when (appInfo.category) {
            ApplicationInfo.CATEGORY_MAPS -> 1.40 to 80.0
            ApplicationInfo.CATEGORY_VIDEO -> 1.25 to 58.0
            ApplicationInfo.CATEGORY_IMAGE -> 1.28 to 45.0
            ApplicationInfo.CATEGORY_AUDIO -> 0.92 to 76.0
            ApplicationInfo.CATEGORY_SOCIAL -> 1.08 to 52.0
            ApplicationInfo.CATEGORY_NEWS -> 0.96 to 45.0
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> 0.82 to 42.0
            else -> when {
                lowerPkg.contains("camera") || lowerPkg.contains("cam") -> 1.38 to 48.0
                lowerPkg.contains("launcher") || lowerPkg.contains("inputmethod") ||
                    lowerPkg.contains("keyboard") -> 0.68 to 35.0
                else -> 1.0 to 50.0
            }
        }
    }

    private fun resolveStartTime(window: AppUsageWindow, nowMs: Long): Long {
        return when (window) {
            AppUsageWindow.TODAY -> {
                Calendar.getInstance().apply {
                    timeInMillis = nowMs
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            AppUsageWindow.LAST_24H -> nowMs - 24L * 3_600_000L
            AppUsageWindow.LAST_7D -> nowMs - 7L * 24L * 3_600_000L
        }
    }

    private fun resolveApplicationInfo(pm: PackageManager, pkg: String): ApplicationInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun classifySeverity(
        window: AppUsageWindow,
        batteryPctEquiv: Double,
        rateMahPerHour: Double,
        baselineActiveMa: Double,
        bgServiceMs: Long,
        fgMs: Long
    ): DrainSeverity {
        val windowScale = when (window) {
            AppUsageWindow.TODAY -> 1.0
            AppUsageWindow.LAST_24H -> 1.15
            AppUsageWindow.LAST_7D -> 3.2
        }
        val highIntensity = rateMahPerHour >= baselineActiveMa * 1.38 && fgMs >= 15 * 60_000L
        val severeBgDrain = bgServiceMs >= 45 * 60_000L && bgServiceMs > fgMs
        val moderateBgDrain = bgServiceMs >= 20 * 60_000L && bgServiceMs > (fgMs * 0.8).toLong()

        return when {
            batteryPctEquiv >= 9.0 * windowScale ||
                (batteryPctEquiv >= 5.0 * windowScale && (highIntensity || severeBgDrain)) ->
                DrainSeverity.EXTREME

            batteryPctEquiv >= 4.2 * windowScale ||
                (batteryPctEquiv >= 2.2 * windowScale && (highIntensity || severeBgDrain)) ->
                DrainSeverity.HIGH

            batteryPctEquiv >= 1.2 * windowScale ||
                (batteryPctEquiv >= 0.6 * windowScale && (moderateBgDrain || rateMahPerHour >= baselineActiveMa * 1.20)) ->
                DrainSeverity.MODERATE

            else -> DrainSeverity.LOW
        }
    }

    fun formatDurationCompact(durationMs: Long): String {
        val totalMinutes = (durationMs / 60_000.0).roundToInt()
        if (totalMinutes <= 0) {
            val sec = (durationMs / 1000L).coerceAtLeast(1L)
            return "${sec}s"
        }
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 -> String.format(Locale.getDefault(), "%dh %dm", hours, mins)
            hours > 0 -> String.format(Locale.getDefault(), "%dh", hours)
            else -> String.format(Locale.getDefault(), "%dm", mins)
        }
    }
}
