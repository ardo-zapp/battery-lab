# =========================
# Battery Lab - proguard-rules.pro
# =========================

# --- Crashlytics ---
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*, Signature

# --- Kotlin / Coroutines ---
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**

# --- Google Play Billing ---
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# --- MIUI Autostart ---
-keep class xyz.kumaraswamy.autostart.** { *; }
-dontwarn xyz.kumaraswamy.autostart.**

# --- WorkManager ---
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class * extends androidx.work.Worker { <init>(...); }
-keep class * extends androidx.work.CoroutineWorker { <init>(...); }

# --- Parcelable CREATOR ---
-keepclassmembers class ** implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# --- App Fragments/Services ---
-keep class com.jacktor.batterylab.fragments.** { *; }
-keep class com.jacktor.batterylab.services.** { *; }