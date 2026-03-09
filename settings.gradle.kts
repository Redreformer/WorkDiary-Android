// ─────────────────────────────────────────────────────────────────────────────
// WorkDiary — settings.gradle.kts
// Single-module Android project.
// ─────────────────────────────────────────────────────────────────────────────

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Required for android-pdf-viewer (PdfiumAndroid wrapper)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "WorkDiary"
include(":app")
