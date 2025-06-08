import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Versioning
    val versionMajor = 1
    val versionMinor = 1
    val versionPatch = 4

    compileSdk = 35
    buildToolsVersion = "35.0.0 rc1"

    defaultConfig {
        applicationId = "com.jacktor.batterylab"
        namespace = applicationId
        minSdk = 23
        targetSdk = 35
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "BUILD_DATE", "\"${getBuildDate()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en", "in")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appLabel"] = "@string/app_name"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        ignoreAssetsPattern = "*.md"
    }
}

fun getBuildDate(): String {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return dateFormat.format(Date())
}

dependencies {
    // Firebase and Google Services
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // AndroidX Libraries
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Kotlin Libraries
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Miscellaneous Libraries
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("com.squareup.picasso:picasso:2.8")
    implementation("com.jaredrummler:colorpicker:1.1.0")
    implementation("com.github.XomaDev:MIUI-autostart:v1.3")
    implementation("com.github.topjohnwu.libsu:core:5.0.1")

    // Testing Libraries
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Modules
    modules {
        module("com.google.guava:listenablefuture") {
            replacedBy("com.google.guava:guava", "listenablefuture is part of guava")
        }
    }
}
