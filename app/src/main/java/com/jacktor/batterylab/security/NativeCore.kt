package com.jacktor.batterylab.security

import android.content.Context
import com.jacktor.premium.Premium
import com.jacktor.premium.data.model.PremiumTrustStatus
import kotlin.random.Random

/**
 * Open-source JNI bridge to the compiled prebuilt native engine (`libbatterylab_core.so`).
 * Core diagnostic algorithms, integrity verification, and premium enforcement reside natively.
 */
object NativeCore {

    const val TRUST_TAMPERED = 0
    const val TRUST_OPENSOURCE_DEBUG = 1
    const val TRUST_OWNER_DEBUG = 2
    const val TRUST_OFFICIAL_RELEASE = 3

    const val FEATURE_GENERAL = 0
    const val FEATURE_HISTORY = 1
    const val FEATURE_ADS = 2
    const val FEATURE_SETTINGS = 3
    const val FEATURE_SERVICE = 4
    const val FEATURE_NOTIFICATION = 5
    const val FEATURE_UNPLUGGED = 6
    const val FEATURE_MENU = 7
    const val FEATURE_OVERLAY = 8

    const val PERMIT_BASE_MAGIC = 0x5A3C96E12D4B87F3L
    const val PERMIT_STEP_MAGIC = 0x4F1BBCDCBEA5C395L

    init {
        System.loadLibrary("batterylab_core")
    }

    data class StressEval(
        val allowed: Boolean,
        val cellMultiplier: Int,
        val cRate: Double,
        val realPowerWatt: Double,
        val rIntCellMohm: Double,
        val voltageSagMv: Int,
        val jouleHeatMw: Double,
        val wearMultiplier: Double,
        val thermalStress: Int,
        val voltageStress: Int,
        val currentStress: Int,
        val compositeStress: Int,
        val statusLevel: Int
    )

    data class PulseEval(
        val success: Boolean,
        val deltaIMa: Int,
        val deltaVCellMv: Int,
        val measuredMohm: Double
    )

    data class IdleDiagnosticsEval(
        val allowed: Boolean,
        val totalDrainRatePercentPerHour: Double,
        val totalDrainRateMa: Double,
        val deepSleepPercent: Double,
        val lastDrainRatePercentPerHour: Double,
        val lastDeepSleepPercent: Double,
        val statusCode: Int
    )

    data class DualCellEval(
        val isDualCell: Boolean,
        val seriesCellCount: Int,
        val chargeCounterMultiplier: Int,
        val cachedKernelDesignMah: Double
    )

    data class FastChargeEval(
        val isFastCharge: Boolean,
        val isTurboCharge: Boolean,
        val fastChargeWatt: Double
    )

    data class BatteryWearEval(
        val wearPercent: Double,
        val wearCapacityMah: Double
    )

    @Suppress("NOTHING_TO_INLINE")
    inline fun isAppTampered(context: Context): Boolean {
        val appCtx = context.applicationContext
        val coreTampered = nativeVerifyIntegrity(appCtx, false) == TRUST_TAMPERED
        val premTampered = try {
            Premium.getTrustStatus(appCtx, false) == PremiumTrustStatus.TAMPERED
        } catch (_: Throwable) {
            true
        }
        return coreTampered || premTampered
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun hasPremiumAccess(context: Context, featureCode: Int = FEATURE_GENERAL): Boolean {
        val appCtx = context.applicationContext
        val coreTrust = nativeVerifyIntegrity(appCtx, false)
        if (coreTrust != TRUST_OFFICIAL_RELEASE && coreTrust != TRUST_OWNER_DEBUG) {
            return false
        }
        val premTrust = try {
            Premium.getTrustStatus(appCtx, false)
        } catch (_: Throwable) {
            return false
        }
        if (premTrust != PremiumTrustStatus.OFFICIAL_RELEASE &&
            premTrust != PremiumTrustStatus.OWNER_DEBUG
        ) {
            return false
        }
        val entitledNow = try {
            Premium.isPremiumNow(appCtx) && Premium.isPremium().value
        } catch (_: Throwable) {
            false
        }
        if (!entitledNow) return false
        val nonce = Random.nextLong()
        val permit = nativeComputeFeaturePermit(appCtx, true, featureCode, nonce)
        val expected = nonce xor (PERMIT_BASE_MAGIC + ((featureCode.toLong() and 0xFFFFFFFFL) * PERMIT_STEP_MAGIC))
        return permit == expected && nativeIsPremiumAllowed(appCtx, true, featureCode)
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun resolveHistoryDisplayCount(context: Context, totalCount: Int): Int {
        val appCtx = context.applicationContext
        val rawPremium = try {
            Premium.isPremiumNow(appCtx) && Premium.isPremium().value
        } catch (_: Throwable) {
            false
        }
        val limit = nativeResolveHistoryLimit(appCtx, rawPremium, totalCount)
        if (!hasPremiumAccess(appCtx, FEATURE_HISTORY)) {
            return limit.coerceAtMost(10).coerceAtLeast(0)
        }
        return limit
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun shouldSuppressAds(context: Context, premiumReady: Boolean): Boolean {
        if (!premiumReady) return false
        val appCtx = context.applicationContext
        if (isAppTampered(appCtx)) return false
        return hasPremiumAccess(appCtx, FEATURE_ADS) &&
            nativeShouldSuppressAds(appCtx, true, true)
    }

    fun evaluateStressMetrics(
        context: Context,
        isCharging: Boolean,
        level: Int,
        rawVoltageMv: Double,
        tempC: Double,
        currentAbsMa: Int,
        designCapMah: Int,
        cycles: Float,
        residualMah: Double,
        savedRintMohm: Float,
        forceSample: Boolean
    ): StressEval {
        val raw = nativeEvaluateStressMetrics(
            context.applicationContext,
            isCharging, level, rawVoltageMv, tempC, currentAbsMa,
            designCapMah, cycles, residualMah, savedRintMohm, forceSample
        )
        return StressEval(
            allowed = raw.getOrElse(0) { 0.0 } >= 0.5,
            cellMultiplier = raw.getOrElse(1) { 1.0 }.toInt(),
            cRate = raw.getOrElse(2) { 0.0 },
            realPowerWatt = raw.getOrElse(3) { 0.0 },
            rIntCellMohm = raw.getOrElse(4) { 0.0 },
            voltageSagMv = raw.getOrElse(5) { 0.0 }.toInt(),
            jouleHeatMw = raw.getOrElse(6) { 0.0 },
            wearMultiplier = raw.getOrElse(7) { 1.0 },
            thermalStress = raw.getOrElse(8) { 0.0 }.toInt(),
            voltageStress = raw.getOrElse(9) { 0.0 }.toInt(),
            currentStress = raw.getOrElse(10) { 0.0 }.toInt(),
            compositeStress = raw.getOrElse(11) { 0.0 }.toInt(),
            statusLevel = raw.getOrElse(12) { 0.0 }.toInt()
        )
    }

    fun evaluatePulseImpedance(
        context: Context,
        baseVoltageMv: Double,
        baseCurrentMa: Double,
        loadVoltageMv: Double,
        loadCurrentMa: Double
    ): PulseEval {
        val raw = nativeEvaluatePulseImpedance(
            context.applicationContext,
            baseVoltageMv, baseCurrentMa, loadVoltageMv, loadCurrentMa
        )
        return PulseEval(
            success = raw.getOrElse(0) { 0.0 } >= 0.5,
            deltaIMa = raw.getOrElse(1) { 0.0 }.toInt(),
            deltaVCellMv = raw.getOrElse(2) { 0.0 }.toInt(),
            measuredMohm = raw.getOrElse(3) { 0.0 }
        )
    }

    fun computeIdleSessionDrain(
        durationMs: Long,
        deepSleepMs: Long,
        startLevel: Int,
        endLevel: Int,
        startCap: Float,
        endCap: Float,
        designCap: Float,
        hasCounter: Boolean
    ): Pair<Float, Float> {
        val raw = nativeComputeIdleSessionDrain(
            durationMs, deepSleepMs, startLevel, endLevel,
            startCap, endCap, designCap, hasCounter
        )
        return (raw.getOrElse(0) { 0f } to raw.getOrElse(1) { 0f })
    }

    fun evaluateIdleDiagnostics(
        context: Context,
        totalDurationMs: Long,
        totalDeepSleepMs: Long,
        totalAwakeMs: Long,
        totalDrainPct: Float,
        totalDrainMah: Float,
        lastDurationMs: Long,
        lastDeepSleepMs: Long,
        lastDrainPct: Float,
        lastDrainMah: Float,
        designCap: Float,
        hasHwCounter: Boolean
    ): IdleDiagnosticsEval {
        val raw = nativeEvaluateIdleDiagnostics(
            context.applicationContext,
            totalDurationMs, totalDeepSleepMs, totalAwakeMs,
            totalDrainPct, totalDrainMah,
            lastDurationMs, lastDeepSleepMs,
            lastDrainPct, lastDrainMah,
            designCap, hasHwCounter
        )
        return IdleDiagnosticsEval(
            allowed = raw.getOrElse(0) { 0.0 } >= 0.5,
            totalDrainRatePercentPerHour = raw.getOrElse(1) { 0.0 },
            totalDrainRateMa = raw.getOrElse(2) { 0.0 },
            deepSleepPercent = raw.getOrElse(3) { 0.0 },
            lastDrainRatePercentPerHour = raw.getOrElse(4) { 0.0 },
            lastDeepSleepPercent = raw.getOrElse(5) { 0.0 },
            statusCode = raw.getOrElse(6) { 0.0 }.toInt()
        )
    }

    fun detectDualCellProfile(
        context: Context,
        voltageMv: Double,
        rawCcMah: Double,
        energyNwh: Long,
        level: Int,
        designMah: Double,
        nominalVoltage: Double,
        prefCycles: Float,
        androidCycles: Float,
        isHealthGood: Boolean
    ): DualCellEval {
        val raw = nativeDetectDualCellProfile(
            context.applicationContext,
            voltageMv, rawCcMah, energyNwh, level,
            designMah, nominalVoltage, prefCycles, androidCycles, isHealthGood
        )
        return DualCellEval(
            isDualCell = raw.getOrElse(0) { 0.0 } >= 0.5,
            seriesCellCount = raw.getOrElse(1) { 1.0 }.toInt(),
            chargeCounterMultiplier = raw.getOrElse(2) { 1.0 }.toInt(),
            cachedKernelDesignMah = raw.getOrElse(3) { -1.0 }
        )
    }

    fun evaluateFastCharge(
        plugged: Int,
        status: Int,
        liveCurrentMa: Double,
        voltageV: Double,
        maxChargeCurrentMa: Int,
        fastChargeMinMa: Int,
        minTurboCurrentMa: Int,
        peakChargeWatt: Double
    ): FastChargeEval {
        val raw = nativeEvaluateFastCharge(
            plugged, status, liveCurrentMa, voltageV,
            maxChargeCurrentMa, fastChargeMinMa, minTurboCurrentMa, peakChargeWatt
        )
        return FastChargeEval(
            isFastCharge = raw.getOrElse(0) { 0.0 } >= 0.5,
            isTurboCharge = raw.getOrElse(1) { 0.0 } >= 0.5,
            fastChargeWatt = raw.getOrElse(2) { 0.0 }
        )
    }

    fun computeBatteryWear(residualMah: Double, designMah: Double): BatteryWearEval {
        val raw = nativeComputeBatteryWear(residualMah, designMah)
        return BatteryWearEval(
            wearPercent = raw.getOrElse(0) { 0.0 },
            wearCapacityMah = raw.getOrElse(1) { 0.0 }
        )
    }

    external fun nativeVerifyIntegrity(context: Context, forceRefresh: Boolean): Int
    external fun nativeIsPremiumAllowed(context: Context, requestedPremium: Boolean, featureCode: Int): Boolean
    external fun nativeResolveHistoryLimit(context: Context, requestedPremium: Boolean, totalCount: Int): Int
    external fun nativeShouldSuppressAds(context: Context, requestedPremium: Boolean, premiumReady: Boolean): Boolean
    external fun nativeComputeFeaturePermit(context: Context, requestedPremium: Boolean, featureCode: Int, nonce: Long): Long

    external fun nativeEvaluateStressMetrics(
        context: Context,
        isCharging: Boolean,
        level: Int,
        rawVoltageMv: Double,
        tempC: Double,
        currentAbsMa: Int,
        designCapMah: Int,
        cycles: Float,
        residualMah: Double,
        savedRintMohm: Float,
        forceSample: Boolean
    ): DoubleArray

    external fun nativeEvaluatePulseImpedance(
        context: Context,
        baseVoltageMv: Double,
        baseCurrentMa: Double,
        loadVoltageMv: Double,
        loadCurrentMa: Double
    ): DoubleArray

    external fun nativeResetStressState()

    external fun nativeComputeIdleSessionDrain(
        durationMs: Long,
        deepSleepMs: Long,
        startLevel: Int,
        endLevel: Int,
        startCap: Float,
        endCap: Float,
        designCap: Float,
        hasCounter: Boolean
    ): FloatArray

    external fun nativeEvaluateIdleDiagnostics(
        context: Context,
        totalDurationMs: Long,
        totalDeepSleepMs: Long,
        totalAwakeMs: Long,
        totalDrainPct: Float,
        totalDrainMah: Float,
        lastDurationMs: Long,
        lastDeepSleepMs: Long,
        lastDrainPct: Float,
        lastDrainMah: Float,
        designCap: Float,
        hasHwCounter: Boolean
    ): DoubleArray

    external fun nativeDetectDualCellProfile(
        context: Context,
        voltageMv: Double,
        rawCcMah: Double,
        energyNwh: Long,
        level: Int,
        designMah: Double,
        nominalVoltage: Double,
        prefCycles: Float,
        androidCycles: Float,
        isHealthGood: Boolean
    ): DoubleArray

    external fun nativeResolveCellMultiplier(voltageMv: Double): Int
    external fun nativeResetDualCellCache()

    external fun nativeEvaluateFastCharge(
        plugged: Int,
        status: Int,
        liveCurrentMa: Double,
        voltageV: Double,
        maxChargeCurrentMa: Int,
        fastChargeMinMa: Int,
        minTurboCurrentMa: Int,
        peakChargeWatt: Double
    ): DoubleArray

    external fun nativeResetFastChargeState()

    external fun nativeComputeBatteryWear(residualMah: Double, designMah: Double): DoubleArray

    external fun nativeEstimateChargingRemainingSec(
        level: Int,
        status: Int,
        residualMah: Double,
        designMah: Double,
        currentMa: Double,
        seriesScale: Int,
        plugged: Int,
        nowMs: Long
    ): Long

    external fun nativeEstimateDischargeRemainingSec(
        level: Int,
        designMah: Double,
        residualMah: Double,
        availMah: Double,
        hwAvgMa: Double,
        emaDischargeMa: Double,
        sessionAvgMa: Double,
        dischargeSampleCount: Int,
        seriesScale: Int,
        nowMs: Long
    ): Long

    external fun nativeResetTimeEstimatorState()
}
