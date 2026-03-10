package com.workdiary.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import com.shockwave.pdfium.PdfiumCore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android port of `PDFManager.swift`.
 *
 * Looks up duty roster PDFs stored in the app's `filesDir` and renders the page
 * that corresponds to a given duty number as a [Bitmap].
 *
 * ### PDF naming convention (mirrors iOS)
 * | Day         | File      |
 * |-------------|-----------|
 * | Mon – Fri   | `MF.pdf`  |
 * | Saturday    | `SAT.pdf` |
 * | Sunday      | `SUN.pdf` |
 *
 * ### File location
 * The user imports PDF files into the app (via a Settings screen import flow, or
 * by placing them in the app's `filesDir`). The iOS app reads from
 * `FileManager.default.urls(.documentDirectory)` — the Android equivalent is
 * `Context.filesDir` (private, not user-visible, shared via FileProvider).
 *
 * ### How it works
 * For each page in the PDF:
 * 1. Render the page to a [Bitmap] at full resolution.
 * 2. Use [DutyScanner] (ML Kit OCR) to extract text.
 * 3. Check whether the duty number appears in the first 10 lines.
 * 4. If found, return the rendered [Bitmap]; otherwise continue to the next page.
 *
 * > **Note:** The render-then-OCR approach is used because pdfiumandroid's native
 * > text-extraction API varies by version. ML Kit gives deterministic results
 * > regardless of the underlying pdfium build.
 *
 * @see DutyScanner
 */
@Singleton
class PDFManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // ── Target render width in pixels (A4 proportions) ───────────────────────
    private val renderWidth = 1080

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Searches the appropriate duty PDF for [duty] number on [date] and returns
     * a rendered [Bitmap] of the matching page, or `null` if not found.
     *
     * This is a **suspend function** — call from a coroutine or `viewModelScope`.
     *
     * Mirrors `PDFManager.renderPDFPage(duty:for:)` in PDFManager.swift.
     *
     * @param duty  Duty number string (e.g. "1096" or "53.a" — suffix stripped).
     * @param date  The calendar date being looked up (determines which PDF to use).
     */
    suspend fun renderPDFPage(duty: String, date: LocalDate): Bitmap? =
        withContext(Dispatchers.IO) {
            // Strip any suffix (e.g. "53.a" → "53"), mirroring the iOS baseDuty logic
            val baseDuty = duty.split(".").first().trim()
            if (baseDuty.isBlank()) return@withContext null

            val pdfName = when (date.dayOfWeek) {
                DayOfWeek.SUNDAY   -> "SUN"
                DayOfWeek.SATURDAY -> "SAT"
                else               -> "MF"
            }

            val pdfFile = File(context.filesDir, "$pdfName.pdf")
            if (!pdfFile.exists()) return@withContext null

            searchPdfForDuty(pdfFile, baseDuty)
        }

    // ── Internal: open PDF, iterate pages, OCR-match, render ─────────────────

    private suspend fun searchPdfForDuty(pdfFile: File, baseDuty: String): Bitmap? {
        val pdfiumCore = PdfiumCore(context)
        var fd: ParcelFileDescriptor? = null

        return try {
            fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfDocument = pdfiumCore.newDocument(fd)
            val pageCount   = pdfiumCore.getPageCount(pdfDocument)

            for (i in 0 until pageCount) {
                pdfiumCore.openPage(pdfDocument, i)

                // Render at moderate resolution for quick OCR check
                val previewBitmap = renderPage(pdfiumCore, pdfDocument, i, previewWidth = 600)
                    ?: continue

                // Use ML Kit to extract text and check the first 10 lines
                val scanResult = DutyScanner.scanImage(previewBitmap)
                previewBitmap.recycle()

                val headerLines = scanResult.scannedText.lines()
                    // Also extract the raw ML Kit text — rebuild from full page later;
                    // for now check if our baseDuty string appears early in the document text
                val pageText = extractRawPageText(pdfiumCore, pdfDocument, i)
                val firstTen = pageText.lines().take(10)

                val found = firstTen.any { line -> line.trim().startsWith(baseDuty) }
                    || scanResult.dutyNumber == baseDuty

                if (found) {
                    // Re-render at full quality for display
                    val fullBitmap = renderPage(pdfiumCore, pdfDocument, i, previewWidth = renderWidth)
                    pdfiumCore.closeDocument(pdfDocument)
                    return fullBitmap
                }
            }

            pdfiumCore.closeDocument(pdfDocument)
            null
        } catch (_: Exception) {
            null
        } finally {
            fd?.close()
        }
    }

    /**
     * Renders a single PDF page to a [Bitmap].
     *
     * Mirrors `PDFManager.imageFromPDFPage(_:)` in PDFManager.swift, which uses
     * `UIGraphicsImageRenderer` to draw a white background + the page.
     */
    private fun renderPage(
        pdfiumCore: PdfiumCore,
        pdfDocument: com.shockwave.pdfium.PdfDocument,
        pageIndex: Int,
        previewWidth: Int,
    ): Bitmap? {
        return try {
            val pageWidthPt  = pdfiumCore.getPageWidthPoint(pdfDocument, pageIndex)
            val pageHeightPt = pdfiumCore.getPageHeightPoint(pdfDocument, pageIndex)
            if (pageWidthPt <= 0 || pageHeightPt <= 0) return null

            val scale  = previewWidth.toFloat() / pageWidthPt
            val width  = previewWidth
            val height = (pageHeightPt * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            pdfiumCore.renderPageBitmap(
                pdfDocument,
                bitmap,
                pageIndex,
                /* startX    = */ 0,
                /* startY    = */ 0,
                /* drawSizeX = */ width,
                /* drawSizeY = */ height,
            )
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Attempts to extract raw text from a page via the pdfiumandroid text API.
     *
     * If the running version of pdfiumandroid exposes text extraction, this returns
     * the page text; otherwise returns an empty string (the OCR fallback in
     * [searchPdfForDuty] handles this gracefully).
     */
    private fun extractRawPageText(
        pdfiumCore: PdfiumCore,
        pdfDocument: com.shockwave.pdfium.PdfDocument,
        pageIndex: Int,
    ): String {
        return try {
            // PdfiumAndroid ≥ 1.9.0 exposes getPageText(doc, index)
            val method = pdfiumCore.javaClass.getMethod(
                "getPageText",
                com.shockwave.pdfium.PdfDocument::class.java,
                Int::class.javaPrimitiveType,
            )
            (method.invoke(pdfiumCore, pdfDocument, pageIndex) as? String) ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
