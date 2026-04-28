// ─────────────────────────────────────────────────────────────────────────────
// WorkDiary — App-level build.gradle.kts
// ─────────────────────────────────────────────────────────────────────────────

plugins {
    alias(libs.plugins.android.application)

    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // Hilt Gradle Plugin (must be applied without apply false for AGP 9)
    id("com.google.dagger.hilt.android")
    // Firebase Google Services Plugin
    id("com.google.gms.google-services")
}

android {
    namespace   = "com.workdiary.app"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.markwilliams.workdiary"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../workdiary-release.keystore")
            storePassword = project.findProperty("WORKDIARY_STORE_PASSWORD") as? String ?: System.getenv("WORKDIARY_STORE_PASSWORD") ?: ""
            keyAlias = "workdiary"
            keyPassword = project.findProperty("WORKDIARY_KEY_PASSWORD") as? String ?: System.getenv("WORKDIARY_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            isDebuggable         = true
            // Removed applicationIdSuffix = ".debug" to match Firebase config
            versionNameSuffix    = "-debug"
        }
        release {
            isMinifyEnabled      = true
            isShrinkResources    = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

// JVM target — set here rather than inside android{} so it works with both old and new DSL
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Room schema export — must be at top level, not inside defaultConfig
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental",    "true")
}

dependencies {


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

    // ── ML Kit Text Recognition (OCR for duty board photos + PDF index) ───────
    implementation(libs.mlkit.text.recognition)

    // ── DataStore (SharedPreferences replacement) ─────────────────────────────
    implementation(libs.androidx.datastore.preferences)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── WorkManager (background notifications / alarms) ───────────────────────
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    // ── Firebase (App Distribution & Analytics) ───────────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-analytics")

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
