# =========================
# Battery Lab - proguard-rules.pro (app)
# =========================

# --- Google Play Billing ---
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# --- DataStore Proto (protobuf-lite) ---
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# --- Tink (Crypto) ---
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# --- Kotlin Coroutines ---
-dontwarn kotlinx.coroutines.**

# --- Okio/Picasso ---
-dontwarn okio.**

# --- MIUI Autostart lib ---
-keep class xyz.kumaraswamy.autostart.** { *; }
-dontwarn xyz.kumaraswamy.autostart.**

# --- Room/Annotation-friendly keeps ---
-keepattributes *Annotation*, Signature

# --- Parcelable CREATOR ---
-keepclassmembers class ** implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# -- OkHttp optional Conscrypt support --
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# --- App Fragments/Services ---
-keep class com.jacktor.batterylab.fragments.** { *; }
-keep class com.jacktor.batterylab.services.** { *; }
