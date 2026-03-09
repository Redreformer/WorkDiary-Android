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

// ─────────────────────────────────────────────────────────────────────────────
// Version constants (override via libs.versions.toml for multi-module projects)
// ─────────────────────────────────────────────────────────────────────────────

val composeBoM         = "2024.06.00"
val roomVersion        = "2.6.1"
val hiltVersion        = "2.51.1"
val hiltNavVersion     = "1.2.0"
val navVersion         = "2.7.7"
val coilVersion        = "2.7.0"
val serializationVer   = "1.7.1"
val pdfiumVersion      = "1.0.3"          // io.legere:pdfiumandroid

dependencies {

    // ── Kotlin stdlib ─────────────────────────────────────────────────────────
    implementation(libs.kotlin.stdlib)

    // ── Compose BOM (pins all Compose library versions together) ─────────────
    val composeBom = platform("androidx.compose:compose-bom:$composeBoM")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // ── Jetpack Compose core ──────────────────────────────────────────────────
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ── Material 3 ────────────────────────────────────────────────────────────
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ── Activity + Lifecycle ──────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // ── Navigation Compose ────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:$navVersion")

    // ── Hilt dependency injection ─────────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:$hiltNavVersion")

    // ── Room (local database) ─────────────────────────────────────────────────
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ── kotlinx.serialization ─────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVer")

    // ── Coil (image loading) ──────────────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:$coilVersion")

    // ── PdfiumAndroid (PDF text extraction + rendering) ───────────────────────
    // Used by PDFManager equivalent to search duty board PDFs.
    // Note: android-pdf-viewer wraps pdfium-android and provides a ready-to-use View.
    implementation("com.github.barteksc:android-pdf-viewer:3.2.0-beta.1")

    // ── DataStore (SharedPreferences replacement) ─────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ── WorkManager (background notifications / alarms) ───────────────────────
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:$hiltNavVersion")
    ksp("androidx.hilt:hilt-compiler:$hiltNavVersion")

    // ── Splash screen API ─────────────────────────────────────────────────────
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ─────────────────────────────────────────────────────────────────────────
    // Test dependencies
    // ─────────────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
}
