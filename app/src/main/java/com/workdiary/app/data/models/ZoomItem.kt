package com.workdiary.app.data.models

import android.graphics.Bitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.Date
import java.util.UUID

/**
 * Data-transfer object used to pass a photo and its calendar metadata into the
 * full-screen zoomable photo viewer.
 *
 * Mirrors `ZoomItem` in iOS (`ZoomItem.swift`), which is `Identifiable` and carries
 * a `UIImage` — the Android equivalent is a [Bitmap].
 *
 * ### Serialisation note
 * [Bitmap] is not natively serialisable with `kotlinx.serialization`. The [image]
 * field is therefore annotated with [@Transient][kotlinx.serialization.Transient] and
 * excluded from JSON serialisation. When navigating to the zoom screen, pass the
 * [ZoomItem] via a shared [androidx.lifecycle.ViewModel] or a [java.lang.ref.WeakReference]
 * rather than through a navigation argument bundle.
 *
 * @property id    Unique identifier — auto-generated on construction; used by Compose
 *                 `key {}` blocks and `LazyColumn` item keys. Matches the iOS auto-UUID.
 * @property image The photo bitmap to display. Not serialised (see note above).
 * @property date  The calendar date this photo belongs to, stored as epoch milliseconds.
 * @property index Which photo slot (0, 1, or 2) was tapped by the user.
 */
@Serializable
data class ZoomItem(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),

    /**
     * The photo to display in the full-screen viewer.
     *
     * Excluded from serialisation — see class KDoc for the recommended passing strategy.
     */
    @Transient
    val image: Bitmap? = null,

    @Serializable(with = DateSerializer::class)
    val date: Date,

    /**
     * Zero-based slot index: `0` = roster scan, `1` = PDF render, `2` = user photo.
     */
    val index: Int,
)
