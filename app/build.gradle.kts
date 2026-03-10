// ─────────────────────────────────────────────────────────────────────────────
// WorkDiary — App-level build.gradle.kts
// ─────────────────────────────────────────────────────────────────────────────

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace   = "com.workdiary.app"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.workdiary.app"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export — useful for migration history
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental",    "true")
        }
    }

    buildTypes {
        debug {
            isDebuggable         = true
            applicationIdSuffix  = ".debug"
            versionNameSuffix    = "-debug"
        }
        release {
            isMinifyEnabled      = true
            isShrinkResources    = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {

    // ── Kotlin stdlib ─────────────────────────────────────────────────────────
    implementation(libs.kotlin.stdlib)

    // ── Compose BOM (pins all Compose library versions together) ─────────────
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // ── Jetpack Compose core ──────────────────────────────────────────────────
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ── Material 3 ────────────────────────────────────────────────────────────
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // ── Activity + Lifecycle ──────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // ── Navigation Compose ────────────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Hilt dependency injection ─────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ── Room (local database) ─────────────────────────────────────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ── kotlinx.serialization ─────────────────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ── Coil (image loading) ──────────────────────────────────────────────────
    implementation(libs.coil.compose)

    // ── PdfiumAndroid (PDF rendering engine — PdfiumCore/PdfDocument API) ────
    implementation(libs.android.pdf.viewer)

    // ── ML Kit Text Recognition (OCR) ─────────────────────────────────────────
    implementation(libs.mlkit.text.recognition)

    // ── DataStore (SharedPreferences replacement) ─────────────────────────────
    implementation(libs.androidx.datastore.preferences)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── WorkManager (background notifications / alarms) ───────────────────────
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    // ── Splash screen API ─────────────────────────────────────────────────────
    implementation(libs.androidx.core.splashscreen)

    // ─────────────────────────────────────────────────────────────────────────
    // Test dependencies
    // ─────────────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
}
