package com.workdiary.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.workdiary.app.PreferencesRepository
import com.workdiary.app.utils.CalendarManager
import com.workdiary.app.utils.PDFManager
import com.workdiary.app.utils.PhotoManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Module-level DataStore extension property.
// Uses the same name ("workdiary_prefs") as the one inside PreferencesRepository
// so they resolve to the same on-disk file. The `preferencesDataStore` delegate
// guarantees a single instance per name per process via a static map.
private val Context.appDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "workdiary_prefs")
