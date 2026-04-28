package com.workdiary.app.utils

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt EntryPoint that exposes [PhotoManager] to Compose composables that
 * cannot receive it via @HiltViewModel injection.
 *
 * Usage:
 * ```kotlin
 * val photoManager = PhotoManagerProvider.get(context)
 * ```
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PhotoManagerEntryPoint {
    fun photoManager(): PhotoManager
}

object PhotoManagerProvider {
    fun get(context: Context): PhotoManager =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PhotoManagerEntryPoint::class.java,
        ).photoManager()
}
