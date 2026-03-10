// ─────────────────────────────────────────────────────────────────────────────
// WorkDiary — Root-level build.gradle.kts
//
// Plugin version declarations only.
// No dependencies are declared here — those belong in app/build.gradle.kts.
//
// Version alignment matrix:
// ┌─────────────────────────────┬───────────┬─────────────────────────────────┐
// │ Library / Tool              │ Version   │ Notes                           │
// ├─────────────────────────────┼───────────┼─────────────────────────────────┤
// │ Android Gradle Plugin (AGP) │ 8.5.2     │ Requires JDK 17+                │
// │ Kotlin                      │ 2.0.21    │ K2 compiler enabled via compose │
// │ KSP (Kotlin Symbol Processing) │ 2.0.21-1.0.28 │ Must match Kotlin exactly │
// │ Hilt (Dagger)               │ 2.51.1    │ Matches app/build.gradle.kts    │
// │ kotlinx.serialization       │ 2.0.21    │ Bundled with Kotlin plugin       │
// └─────────────────────────────┴───────────┴─────────────────────────────────┘
// ─────────────────────────────────────────────────────────────────────────────

plugins {
    // Android Application / Library plugin — do NOT apply here; applied in submodules.
    id("com.android.application")        version "8.5.2"  apply false
    id("com.android.library")            version "8.5.2"  apply false

    // Kotlin — do NOT apply here; applied in submodules.
    id("org.jetbrains.kotlin.android")   version "2.0.21" apply false

    // Jetpack Compose Kotlin compiler plugin (replaces composeOptions.kotlinCompilerExtensionVersion).
    // Required from Kotlin 2.x onwards.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false

    // kotlinx.serialization (JSON encoding for Holiday, ZoomItem, DayNote, etc.)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false

    // KSP — annotation processor used by Room and Hilt.
    // KSP version format: <kotlin-version>-<ksp-patch>
    id("com.google.devtools.ksp")        version "2.0.21-1.0.28" apply false

    // Hilt dependency injection (Dagger-based).
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}
