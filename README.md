# Battery Lab

[![Play Store](https://img.shields.io/endpoint?color=green&logo=google-play&logoColor=green&url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Dcom.jacktor.batterylab%26gl%3DUS%26hl%3Den%26l%3DAndroid%26m%3D%24version)](https://play.google.com/store/apps/details?id=com.jacktor.batterylab)
[![License](https://img.shields.io/github/license/ardo-zapp/battery-lab)](https://github.com/ardo-zapp/battery-lab/blob/main/LICENSE)
[![GitHub stars](https://img.shields.io/github/stars/ardo-zapp/battery-lab)](https://github.com/ardo-zapp/battery-lab/stargazers)
[![GitHub issues](https://img.shields.io/github/issues/ardo-zapp/battery-lab)](https://github.com/ardo-zapp/battery-lab/issues)

Battery Lab is a Material 3 Android battery monitoring and diagnostics application (`com.jacktor.batterylab`) that operates without root access. It combines customizable power-connection audio alerts with real-time charging telemetry, battery health estimation, idle drain analysis, internal resistance diagnostics, and persistent status notifications.

---

## Overview

Battery Lab originated from two open-source Android projects and has since expanded into an integrated telemetry and diagnostics application:

- **[BNotifier](https://github.com/ardo-zapp/BNotifier)** — inspired custom charger-connected sound notifications.
- **[Capacity Info](https://github.com/Ph03niX-X/CapacityInfo)** — upstream Apache-2.0 foundation for capacity tracking, wear estimation, charge/discharge monitoring, history, notifications, and overlay features.

> **Repository Purpose & Licensing Scope**  
> This repository is published publicly to comply with the Apache License 2.0 obligations of [Capacity Info](https://github.com/Ph03niX-X/CapacityInfo). It is not intended as a free-for-all modification base or community-maintained fork. The `:premium` module (`com.jacktor.premium`) is **closed-source** and is distributed in this repository solely as the prebuilt binary [`app/libs/premium.aar`](app/libs/premium.aar).

> **Redistribution Notice**  
> Redistributing compiled APKs (including renamed, repackaged, or modified builds) to any application store (such as Google Play Store, F-Droid, or Amazon Appstore) without prior permission from the author is strictly prohibited.

> **Root-Free Operation**  
> All telemetry, diagnostics, and background monitoring run using standard Android APIs and readable system properties without requiring root privileges.

---

## Features

### Real-Time Battery Telemetry & Health
- **Live Status & Estimations**: Tracks battery percentage, health status, wear, residual capacity, design capacity (`mAh` / `Wh`), temperature, and estimated remaining charge/discharge time.
- **Dual-Cell & Voltage Normalization**: Evaluates battery voltage across both Single-Cell (`1S`) and Dual-Cell (`2S`) series architectures with dynamic warning and danger thresholds.
- **Current, Power & Dynamic Fast-Charge Detection**: Computes smoothed min/average/maximum charge and discharge current (`mA`) and power (`W`), and automatically detects Fast Charge and Turbo Charge sessions in `BatteryLabService` from live wattage, current thresholds, and supported kernel telemetry.
- **Cycle Tracking**: Records application-tracked charge/full-charge counts alongside Android 14+ system battery cycle counts when exposed by the device.

### Diagnostic Tools
- **Idle Drain Analyzer** (`IdleDrainFragment`): Measures screen-off vs. screen-on drain rates (`%/h` and `mA`), Deep Doze ratios, and idle consumption behavior to help identify excessive standby drain.
- **Stress & Impedance Analyzer** (`StressImpedanceFragment`): Estimates internal resistance (`R_int` in `mOhm`), voltage sag under load, and battery stress index, with optional active load pulse calibration.

### Charging History, Alerts & Overlay
- **Charging History**: SQLite-backed session history (`HistoryDB`) logging charged capacity, residual capacity, charging duration, and cycle snapshots.
- **Power Connection Alerts**: Configurable sounds/ringtones and alerts when power is connected or disconnected, when fully charged, or when custom charge/discharge/temperature thresholds are reached.
- **Persistent Service Notification & Floating Overlay (Premium)**: Customizable foreground status notification with battery metrics and an optional system-wide floating telemetry overlay (`SYSTEM_ALERT_WINDOW`).
- **Customization & Backup**: Material 3 dynamic theming, light/dark themes, customizable card typography, localized strings (`en`, `in`), and local preference backup/restore (`BackupSettingsFragment`).

---

## Project Architecture

The public repository contains the `:app` module (`com.jacktor.batterylab`), which depends on the required closed-source `:premium` module distributed as the prebuilt binary `app/libs/premium.aar`:

```text
battery-lab/
└── app/                         # Application module (com.jacktor.batterylab)
    ├── libs/
    │   └── premium.aar          # Required closed-source :premium module binary
    │                            # (Play Billing, Tink-encrypted Proto DataStore & premium UI)
    └── src/main/
        ├── java/.../
        │   ├── adapters/        # RecyclerView & ViewPager2 adapters
        │   ├── databases/       # SQLite schema & history persistence (HistoryDB)
        │   ├── fragments/       # Main screens, settings, history, and tool tabs
        │   ├── helpers/         # Motion, theme, idle drain, history & formatting helpers
        │   ├── interfaces/      # Telemetry, navigation, notification, overlay & menu logic
        │   ├── models/          # UI row and contributor models
        │   ├── receivers/       # Boot, power connection, and update broadcast receivers
        │   ├── services/        # Foreground monitoring, overlay & reminder job services
        │   └── utilities/       # Backup, constants, preference keys & AdMob helpers
        └── res/                 # Material 3 layouts, animations, XML prefs & resources
```

---

## Tech Stack & Requirements

| Component | Details |
|---|---|
| Language | Kotlin `2.4.21` (JVM Toolchain 21) |
| Build System | Gradle `9.3.1`, Android Gradle Plugin `9.1.1` |
| Android SDK | `minSdk 26` (Android 8.0) / `compileSdk 37` / `targetSdk 37` |
| UI & Architecture | ViewBinding, Material Components `1.14.0`, AndroidX AppCompat/Preference/WorkManager/ViewPager2 |
| Async & Storage | Kotlin Coroutines `1.11.0`, SQLite (`HistoryDB`), Proto DataStore `1.3.0` + Tink `1.24.0` |
| Platform Services | Google Play Billing `9.1.0`, Play In-App Updates `2.1.0`, Firebase Analytics & Crashlytics, Google Mobile Ads |

---

## Building from Source

### Prerequisites
- JDK 21
- Android SDK with Platform API 37 installed
- `app/google-services.json` configured for your Firebase project (required by `com.google.gms.google-services`)
- `app/libs/premium.aar` (already provided in this repository)

### Build & Install

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Install debug APK to a connected ADB device
./gradlew :app:installDebug
```

`:app` links directly against the prebuilt `app/libs/premium.aar` included in the repository.

---

## Known Device Behavior (HyperOS / MIUI)

On Xiaomi, POCO, and Redmi devices running **HyperOS** or **MIUI**, aggressive background process restrictions can prevent foreground service notifications or standby measurements from updating reliably after prolonged sleep.

Recommended device settings:

1. **Battery Saver / Optimization**: Set Battery Lab to **No restrictions**.
2. **Recents Lock**: Lock Battery Lab in the Recents view so the system does not terminate the monitoring service.
3. **Autostart**: Enable **Autostart** for Battery Lab in system app permissions (if available).
4. **Background Activity**: Allow unrestricted background activity in app-specific battery settings.

Exact menu names vary depending on device model and OS version.

---

## Download

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.jacktor.batterylab">
    <img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
         alt="Get it on Google Play" width="260"/>
  </a>
</p>

---

## Maintenance & License

This repository is updated periodically alongside personal commitments to reflect upstream compliance updates and released builds. Bug reports may be submitted through GitHub Issues, though response times can vary.

The open-source `:app` codebase is licensed under the [Apache License 2.0](LICENSE) in compliance with [Capacity Info](https://github.com/Ph03niX-X/CapacityInfo). The `:premium` module (`app/libs/premium.aar`) remains proprietary and closed-source.
