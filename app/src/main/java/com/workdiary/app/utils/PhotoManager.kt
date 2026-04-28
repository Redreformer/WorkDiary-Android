package com.workdiary.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android equivalent of the photo-storage helpers scattered across `CalendarView.swift`.
 *
 * Manages reading, writing, and deleting the three per-day photo slots.
 *
 * ### File naming convention (mirrors iOS)
 * `photo_{profile}_{epochSeconds}_{index}.jpg`
 *
 * `epochSeconds` is the Unix timestamp of the **start of day in UTC** — identical to the
 * iOS formula `Calendar.current.startOfDay(for: date).timeIntervalSince1970`.
 *
 * ### Storage location
 * iOS stores files in `FileManager.default.urls(.documentDirectory)`.
 * Android uses `Context.filesDir` (private, not user-visible) — content is shared
 * externally via FileProvider when needed.
 *
 * ### Slots
 * | Index | Purpose                                       |
 * |-------|-----------------------------------------------|
 * | 0     | Roster scan — triggers OCR + PDF lookup       |
 * | 1     | PDF render — auto-populated from slot 0 scan  |
 * | 2     | Manual user photo or "Search Duty Board" result |
 *
 * @see DutyScanner
 * @see PDFManager
 */
@Singleton
class PhotoManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ── File path helpers ─────────────────────────────────────────────────────

    /**
     * Returns the [File] for a photo slot.
     *
     * Mirrors `CalendarView.getImagePath(for:index:)` in CalendarView.swift.
     */
    fun getPhotoFile(profile: String, date: LocalDate, index: Int): File {
        val epochSeconds = date.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
        val fileName = "photo_${profile}_${epochSeconds}_$index.jpg"
        return File(context.filesDir, fileName)
    }

    /**
     * Returns a [FileProvider] content URI for [file], suitable for passing to
     * `ActivityResultContracts.TakePicture` as the output URI.
     *
     * The authority `{applicationId}.fileprovider` must match `file_paths.xml`.
     */
    fun getFileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Loads and decodes the photo for [date] / [index], or returns `null` if missing.
     *
     * Mirrors `CalendarView.loadImageForDate(_:index:)` in CalendarView.swift.
     *
     * Runs on [Dispatchers.IO].
     */
    suspend fun loadPhoto(profile: String, date: LocalDate, index: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = getPhotoFile(profile, date, index)
            if (!file.exists()) return@withContext null
            BitmapFactory.decodeFile(file.absolutePath)
        }

    /**
     * Returns whether a photo exists for the given slot (without decoding it).
     */
    fun photoExists(profile: String, date: LocalDate, index: Int): Boolean =
        getPhotoFile(profile, date, index).exists()

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Saves raw JPEG [bytes] to [index] slot for [date].
     *
     * Mirrors the `try? data.write(to:)` pattern in CalendarView.swift.
     *
     * Runs on [Dispatchers.IO].
     */
    suspend fun savePhotoBytes(
        profile: String,
        date: LocalDate,
        index: Int,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        val file = getPhotoFile(profile, date, index)
        FileOutputStream(file).use { it.write(bytes) }
    }

    /**
     * Saves a [Bitmap] (JPEG at 80 % quality) to the given slot.
     *
     * Mirrors the `img.jpegData(compressionQuality: 0.8)` call in CalendarView.swift.
     */
    suspend fun saveBitmap(
        profile: String,
        date: LocalDate,
        index: Int,
        bitmap: Bitmap,
    ) = withContext(Dispatchers.IO) {
        val file = getPhotoFile(profile, date, index)
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        }
    }

    /**
     * Decodes a [Uri] (content:// or file://) to a [Bitmap].
     *
     * Used by gallery/file-picker result handlers.
     */
    suspend fun loadBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes the photo file for [date] / [index].
     *
     * Mirrors the `try? FileManager.default.removeItem(at:)` call in
     * `CalendarView.deleteImageForDate(date:index:)`.
     */
    suspend fun deletePhoto(profile: String, date: LocalDate, index: Int) =
        withContext(Dispatchers.IO) {
            getPhotoFile(profile, date, index).delete()
        }

    // ── Temp file for camera ──────────────────────────────────────────────────

    /**
     * Creates (or re-creates) a temporary file in [Context.cacheDir] for the camera
     * to write a captured photo into.
     *
     * The caller must call [getFileProviderUri] on the returned [File] to obtain a
     * content URI suitable for `TakePicture`.
     */
    fun createCameraTempFile(): File {
        val dir  = context.cacheDir
        val file = File(dir, "camera_capture_temp.jpg")
        if (file.exists()) file.delete()
        file.createNewFile()
        return file
    }
}
