import com.android.build.api.dsl.ApplicationExtension
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

fun getBuildDate(): String {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return dateFormat.format(Date())
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

configure<ApplicationExtension> {
    val versionMajor = 2
    val versionMinor = 0
    val versionPatch = 1

    compileSdk = 37

    defaultConfig {
        val appId = "com.jacktor.batterylab"
        applicationId = appId

        minSdk = 26
        targetSdk = 37
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "BUILD_DATE", "\"${getBuildDate()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        androidResources {
            @Suppress("UnstableApiUsage")
            localeFilters.addAll(listOf("en", "in"))
        }
    }

    namespace = "com.jacktor.batterylab"

    val localProps = Properties().apply {
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            FileInputStream(localFile).use { load(it) }
        }
    }
    val releaseStorePath = localProps.getProperty("signing.storeFile")
        ?: System.getenv("ANDROID_KEYSTORE_PATH")
    val releaseStorePass = localProps.getProperty("signing.storePassword")
        ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val releaseKeyAlias = localProps.getProperty("signing.keyAlias")
        ?: System.getenv("ANDROID_KEY_ALIAS")
    val releaseKeyPass = localProps.getProperty("signing.keyPassword")
        ?: System.getenv("ANDROID_KEY_PASSWORD")
    val hasReleaseSigning = !releaseStorePath.isNullOrBlank() &&
        file(releaseStorePath).exists() &&
        !releaseStorePass.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPass.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = releaseStorePass
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPass
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appLabel"] = "@string/app_name"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            versionNameSuffix =
                ".${SimpleDateFormat("ddMMyyyy", Locale.getDefault()).format(Date())}-dev"
            manifestPlaceholders["appLabel"] = "@string/app_name_dev"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        ignoreAssetsPattern = "*.md"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    (this as ExtensionAware).extensions.configure<CrashlyticsExtension>("firebaseCrashlytics") {
        mappingFileUploadEnabled = true
        nativeSymbolUploadEnabled = false
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    // Dependencies required by premium.aar
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.datastore:datastore:1.3.0")
    implementation("androidx.datastore:datastore-core:1.3.0")
    implementation("com.google.crypto.tink:tink-android:1.24.0")
    implementation("com.google.protobuf:protobuf-javalite:4.36.2")
    implementation("com.google.protobuf:protobuf-kotlin-lite:4.36.2")

    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx:19.4.4")
    implementation("com.google.firebase:firebase-analytics-ktx:22.5.0")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("com.google.android.material:material:1.14.0")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    //noinspection NewerVersionAvailable
    implementation("com.squareup.picasso:picasso:2.8")
    implementation("com.jaredrummler:colorpicker:1.1.0")
    implementation("com.github.XomaDev:MIUI-autostart:v1.3")
}

val premiumModule = rootProject.findProject(":premium")

if (premiumModule != null) {
    val syncPremiumAar = tasks.register<Copy>("syncPremiumAar") {
        dependsOn(":premium:bundleReleaseAar")
        from(premiumModule.layout.buildDirectory.file("outputs/aar/premium-release.aar"))
        into(layout.projectDirectory.dir("libs"))
        rename { "premium.aar" }
    }

    tasks.named("preBuild") {
        dependsOn(syncPremiumAar)
    }
}

val coreModule = rootProject.findProject(":core")

if (coreModule != null) {
    val syncCoreNativeLibs = tasks.register<Sync>("syncCoreNativeLibs") {
        dependsOn(":core:stripReleaseDebugSymbols")
        from(coreModule.layout.buildDirectory.dir("intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib"))
        into(layout.projectDirectory.dir("src/main/jniLibs"))
        include("**/*.so")
    }

    tasks.named("preBuild") {
        dependsOn(syncCoreNativeLibs)
    }
}
