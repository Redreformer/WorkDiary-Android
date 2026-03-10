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

/** Single DataStore delegate — the ONLY place this is defined. */
private val Context.workDiaryDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "workdiary_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.workDiaryDataStore

    @Provides
    @Singleton
    fun providePreferencesRepository(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>,
    ): PreferencesRepository = PreferencesRepository(context, dataStore)

    @Provides
    @Singleton
    fun provideCalendarManager(
        @ApplicationContext context: Context,
    ): CalendarManager = CalendarManager(context)

    @Provides
    @Singleton
    fun providePhotoManager(
        @ApplicationContext context: Context,
    ): PhotoManager = PhotoManager(context)

    @Provides
    @Singleton
    fun providePDFManager(
        @ApplicationContext context: Context,
    ): PDFManager = PDFManager(context)
}
