// ─────────────────────────────────────────────────────────────────────────────
// WorkDiary — Root-level build.gradle.kts
// Plugin declarations with versions - use libs.versions.toml
// ─────────────────────────────────────────────────────────────────────────────

plugins {
    // All plugins declared at root level via version catalog
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false

    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // KSP for Room - must be declared at root for classloader compatibility
    alias(libs.plugins.ksp) apply false
    // Hilt Gradle Plugin - version defined in libs.versions.toml
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    // Google Services Plugin for Firebase
    id("com.google.gms.google-services") version "4.4.4" apply false
}