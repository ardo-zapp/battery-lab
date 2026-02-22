import com.android.build.api.dsl.ApplicationExtension
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    compileSdk = 36

    defaultConfig {
        val appId = "com.jacktor.batterylab"
        applicationId = appId

        minSdk = 26
        targetSdk = 36
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

    buildTypes {
        getByName("release") {
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

    (this as ExtensionAware).extensions.configure<CrashlyticsExtension>("firebaseCrashlytics") {
        mappingFileUploadEnabled = true
        nativeSymbolUploadEnabled = false
    }
}

dependencies {
    implementation(project(":premium"))

    implementation(platform("com.google.firebase:firebase-bom:34.8.0"))
    implementation("com.google.firebase:firebase-crashlytics-ktx:19.4.4")
    implementation("com.google.firebase:firebase-analytics-ktx:22.5.0")
    implementation("com.google.android.gms:play-services-ads:24.9.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("com.google.android.material:material:1.13.0")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    //noinspection NewerVersionAvailable
    implementation("com.squareup.picasso:picasso:2.8")
    implementation("com.jaredrummler:colorpicker:1.1.0")
    implementation("com.github.XomaDev:MIUI-autostart:v1.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}