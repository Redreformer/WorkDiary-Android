package com.workdiary.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for WorkDiary.
 *
 * Annotated with [HiltAndroidApp] to trigger Hilt's code generation and initialise the
 * dependency-injection component hierarchy. This class must be declared as the `android:name`
 * in [AndroidManifest.xml].
 *
 * Any app-wide initialisation that must happen before any Activity starts (e.g. logging,
 * analytics, crash reporting) should be placed in [onCreate].
 */
@HiltAndroidApp
class WorkDiaryApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // App-wide initialisation goes here (e.g. Timber, Firebase, etc.)
    }
}
