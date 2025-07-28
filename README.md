# Battery Lab    

[![GitHub release](https://img.shields.io/github/v/release/ardo-zapp/battery-lab?label=Latest%20Release)](https://github.com/ardo-zapp/battery-lab/releases)  
[![GitHub issues](https://img.shields.io/github/issues/ardo-zapp/battery-lab)](https://github.com/ardo-zapp/battery-lab/issues)  
[![GitHub stars](https://img.shields.io/github/stars/ardo-zapp/battery-lab)](https://github.com/ardo-zapp/battery-lab/stargazers)  
[![License](https://img.shields.io/github/license/ardo-zapp/battery-lab)](https://github.com/ardo-zapp/battery-lab/blob/main/LICENSE)  

Battery Lab is a modern battery monitoring app for Android that combines charger-connected sound alerts from BNotifier and detailed battery statistics from Capacity Info.

---

## 📸 Screenshots

<p align="center">
  <!-- <img src="screenshots/screen1.png" width="200"/> -->
</p>

---

## ℹ️ About

Battery Lab is built upon two open-source Android tools:

- **BNotifier** — provides custom charger‑connected notification sound  
  - [BNotifier on GitHub](https://github.com/ardo-zapp/BNotifier)
- **Capacity Info** — detailed battery health metrics: capacity, wear, temperature, voltage, current, cycle count, history, and premium overlay & notifications  
  - [Capacity Info on GitHub](https://github.com/Ph03niX-X/CapacityInfo)

> **⚠️ Redistribution Notice**  
> You are **strictly prohibited** from redistributing any compiled APKs (including renamed or modified versions) to any app store (e.g., Google Play Store, F‑Droid, Amazon Appstore) **without prior permission**.

> 📝 **Note**  
> Battery Lab does **not require root access**. Previous plans for "Battery Calibration" and "Kernel Experiment" have been **discontinued** to ensure stability and broader compatibility.

---

## 🚀 Features

- 🔊 **Custom Charger‑Connected Sound** — plays sound when power is connected (inspired by BNotifier)  
- 📈 **Battery Health Metrics** — includes real capacity, wear, voltage, temperature, charge/discharge current, cycle count, and battery history  
- 📬 **Custom Notifications & Overlay (Premium)** — notifications for charge level, temperature, and more, with optional floating overlay    
- ⚙️ **Lightweight & Efficient** — minimal battery and memory impact  

---

## 🧩 Feature Implementation Status

<div align="center">

| Status | Feature                                      |
|--------|----------------------------------------------|
| ✅     | Custom Charger‑Connected Sound                      |
| ✅     | Battery Health Metrics (Capacity Info)            |
| ❌     | Battery Calibration (Discontinued)             |
| ❌     | Kernel Experiment (Discontinued)             |

</div>

Legend:  
✅ – Implemented successfully  
❌ – Discontinued / Dropped  

---

## ⚠️ Known Issues on HyperOS / MIUI

Users on Xiaomi/POCO/Redmi devices running **HyperOS** or **MIUI** may experience issues where notifications stop updating after sleep mode. Notifications resume only after they are manually dismissed and recreated. This issue stems from aggressive background restrictions in the ROM firmware.

### 🔧 Suggested Workarounds

1. **Disable Battery Optimization**  
   Set Battery Lab → “No restrictions” in Battery Saver settings.  
2. **Lock the App in Recents**  
   From Recents screen, lock Battery Lab to prevent system kill.  
3. **Enable Autostart** in app permissions (if available).  
4. **Allow Background Activity** in app-specific battery settings.

These steps vary by device and OS version.

---

## 📲 Download from Google Play:

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.jacktor.batterylab">
    <img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"     
         alt="Get it on Google Play" width="323" height="125"/>    
  </a>
</p>

---

## 💤 Maintenance Status

> ⚠️ This repository is updated **infrequently** due to limited time and real-life priorities.  
> Feature requests, issues, and suggestions are still welcome — please note that responses and updates may take longer than usual.

Thanks for your understanding and continued support!
