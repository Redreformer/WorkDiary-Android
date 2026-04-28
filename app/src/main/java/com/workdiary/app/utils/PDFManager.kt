package com.workdiary.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Finds and renders the duty-board PDF page matching a given duty number.
 *
 * Strategy (mirrors iOS PDFManager.swift):
 *  - Uses Android's native PdfRenderer to render the top 25% of each page.
 *  - Runs ML Kit OCR on the crop to extract the duty number from the header.
 *  - Saves the duty→pageIndex map to a sidecar .index file so the full scan
 *    only ever runs once per PDF (or whenever the PDF is replaced).
 *
 * This replaces the previous PDFBox text-extraction approach, which failed
 * silently on PDFs with custom/embedded font encoding (e.g. Optibus exports).
 */
@Singleton
class PDFManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "PDFManager"
        private const val RENDER_WIDTH = 1080   // full-quality render for display
        private const val INDEX_WIDTH  = 400    // smaller render for index OCR
    }

    // In-memory cache: pdfName → (dutyNumber → pageIndex)
    private val indexCache = mutableMapOf<String, Map<String, Int>>()

    suspend fun renderPDFPage(duty: String, date: LocalDate): Bitmap? = withContext(Dispatchers.IO) {
        val baseDuty = duty.trim().substringBefore(".")

        val pdfName = when (date.dayOfWeek) {
            java.time.DayOfWeek.SUNDAY   -> "SUN.pdf"
            java.time.DayOfWeek.SATURDAY -> "SAT.pdf"
            else                         -> "MF.pdf"
        }

        val pdfFile = File(context.filesDir, pdfName)
        if (!pdfFile.exists()) {
            Log.w(TAG, "PDF not found: ${pdfFile.absolutePath}")
            return@withContext null
        }

        val index = indexCache[pdfName] ?: buildIndex(pdfFile).also { indexCache[pdfName] = it }

        val pageIndex = findInIndex(index, baseDuty)
        if (pageIndex == null) {
            Log.w(TAG, "Duty '$baseDuty' not found. Index has ${index.size} entries: ${index.keys.take(10)}")
            return@withContext null
        }

        Log.d(TAG, "Duty '$baseDuty' → page $pageIndex")
        renderPage(pdfFile, pageIndex)
    }

    // ── Index lookup ──────────────────────────────────────────────────────────

    /**
     * Looks up [duty] in [index], tolerating leading-zero differences between
     * what OCR reads ("53") and what the PDF header may print ("0053").
     */
    private fun findInIndex(index: Map<String, Int>, duty: String): Int? {
        if (index.isEmpty()) return null
        index[duty]?.let { return it }

        // Normalise both sides to integer to handle leading zeros
        val asInt = duty.toIntOrNull()
        if (asInt != null) {
            for ((key, page) in index) {
                if (key.toIntOrNull() == asInt) return page
            }
        }

        // Prefix match fallback (e.g. duty "53" matches index entry "53A")
        for ((key, page) in index) {
            if (key.startsWith(duty) || duty.startsWith(key)) return page
        }
        return null
    }

    // ── Index build ───────────────────────────────────────────────────────────

    /**
     * Scans every page of [pdfFile] by rendering the top 25% and running
     * ML Kit OCR to extract the duty number from the header.
     *
     * Result is persisted to a sidecar .index file.  Subsequent calls
     * (including after app restart) return the cached file immediately
     * unless the PDF has been replaced.
     */
    private suspend fun buildIndex(pdfFile: File): Map<String, Int> {
        val indexFile = File(pdfFile.parent, "${pdfFile.name}.index")

        if (indexFile.exists() && indexFile.lastModified() >= pdfFile.lastModified()) {
            val cached = readIndexFile(indexFile)
            if (cached.isNotEmpty()) {
                Log.d(TAG, "Index loaded for ${pdfFile.name}: ${cached.size} duties")
                return cached
            }
        }

        Log.d(TAG, "Building index for ${pdfFile.name} (${pdfFile.length() / 1024} KB)…")
        val index = mutableMapOf<String, Int>()
        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)
            val total = renderer.pageCount
            Log.d(TAG, "Scanning $total pages with PdfRenderer + ML Kit OCR…")

            for (pageIndex in 0 until total) {
                val duty = extractDutyFromPageHeader(renderer, pageIndex)
                if (duty != null) {
                    index.putIfAbsent(duty, pageIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build index for ${pdfFile.name}", e)
        } finally {
            renderer?.close()
            fd?.close()
        }

        Log.d(TAG, "Index complete: ${index.size} duties — ${index.keys.take(10)}")

        if (index.isNotEmpty()) {
            try {
                indexFile.writeText(index.entries.joinToString("\n") { "${it.key}:${it.value}" })
            } catch (e: Exception) {
                Log.w(TAG, "Could not persist index file", e)
            }
        }

        return index
    }

    /**
     * Renders only the top 25 % of [pageIndex] at a small resolution and
     * OCRs it to find the duty number in the page header.
     */
    private suspend fun extractDutyFromPageHeader(renderer: PdfRenderer, pageIndex: Int): String? {
        var page: PdfRenderer.Page? = null
        return try {
            page = renderer.openPage(pageIndex)

            val scale      = INDEX_WIDTH.toFloat() / page.width
            val fullHeight = (page.height * scale).toInt()
            val cropHeight = (fullHeight * 0.25f).toInt().coerceAtLeast(80)

            val bitmap = Bitmap.createBitmap(INDEX_WIDTH, cropHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            // Scale the page to INDEX_WIDTH; only the top cropHeight rows are
            // written to the bitmap, giving us the header region cheaply.
            val matrix = Matrix().apply { setScale(scale, scale) }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val text = ocrBitmap(bitmap)
            bitmap.recycle()

            parseDutyNumber(text)
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract duty from page $pageIndex", e)
            null
        } finally {
            page?.close()
        }
    }

    /** Runs ML Kit OCR on [bitmap] and returns the raw text result. */
    private suspend fun ocrBitmap(bitmap: Bitmap): String = suspendCoroutine { cont ->
        InputImage.fromBitmap(bitmap, 0).let { image ->
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }
    }

    /**
     * Finds the first 1–4 digit number at the start of any line — the same
     * logic iOS applies with `trimmed.hasPrefix(baseDuty)`.
     */
    private fun parseDutyNumber(text: String): String? {
        for (line in text.lines()) {
            val trimmed = line.trim()
            val match = Regex("""^(\d{1,4})(?:\s|$)""").find(trimmed) ?: continue
            return match.groupValues[1]
        }
        return null
    }

    // ── Index file I/O ────────────────────────────────────────────────────────

    private fun readIndexFile(indexFile: File): Map<String, Int> {
        val index = mutableMapOf<String, Int>()
        indexFile.forEachLine { line ->
            val colon = line.indexOf(':')
            if (colon > 0) {
                index[line.substring(0, colon)] =
                    line.substring(colon + 1).toIntOrNull() ?: return@forEachLine
            }
        }
        return index
    }

    // ── Page render ───────────────────────────────────────────────────────────

    private fun renderPage(pdfFile: File, pageIndex: Int): Bitmap? {
        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        var bitmap: Bitmap? = null
        try {
            fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)
            if (pageIndex >= renderer.pageCount) {
                Log.w(TAG, "Page $pageIndex out of bounds (${renderer.pageCount} pages)")
                return null
            }
            page = renderer.openPage(pageIndex)
            val width  = RENDER_WIDTH
            val height = (width * page.height.toFloat() / page.width).toInt()
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            Log.d(TAG, "Rendered page $pageIndex at ${width}×$height")
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering page $pageIndex", e)
            bitmap?.recycle()
            return null
        } finally {
            page?.close()
            renderer?.close()
            fd?.close()
        }
    }

    // ── Debug helpers ─────────────────────────────────────────────────────────

    fun getStatus(): String {
        return listOf("MF.pdf", "SAT.pdf", "SUN.pdf").joinToString(", ") { name ->
            val f   = File(context.filesDir, name)
            val idx = File(context.filesDir, "$name.index")
            "$name=${if (f.exists()) "${f.length() / 1024}KB idx=${idx.exists()}" else "missing"}"
        }
    }

    fun listPdfFiles(): String {
        return context.filesDir.listFiles()
            ?.filter { it.extension == "pdf" }
            ?.joinToString(", ") { "${it.name}(${it.length() / 1024}KB)" }
            ?: "none"
    }
}
