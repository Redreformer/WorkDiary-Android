package com.workdiary.app.utils

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Android port of the iOS `DutyScanner` class in CalendarView.swift.
 *
 * Uses **ML Kit Text Recognition** (equivalent of Apple Vision `VNRecognizeTextRequest`)
 * to scan a roster/duty-board photograph and extract:
 * - Duty number (1–4 digits, optionally prefixed with "Duty:" / "Rte:")
 * - Sign On time (e.g. "Sign On: 16:33" or "On: 15:02")
 * - Sign Off time (e.g. "Sign Off: 01:15" or "Off: 00:38")
 *
 * ### Usage
 * ```kotlin
 * val result = DutyScanner.scanImage(bitmap)
 * // result.scannedText → multi-line string with "Duty: 53\nSign On: 16:33\nSign Off: 00:38"
 * // result.dutyNumber  → "53" (null if not found)
 * ```
 */
object DutyScanner {

    // ── Result type ────────────────────────────────────────────────────────

    /**
     * Output of a scan operation.
     *
     * @property scannedText  Formatted multi-line string ready to append to the day note
     *                        (mirrors the `result` string in iOS DutyScanner).
     * @property dutyNumber   The raw duty number string (e.g. "53") or null if not found.
     *                        Used downstream to trigger a PDF page lookup.
     */
    data class ScanResult(
        val scannedText: String,
        val dutyNumber: String?,
    )

    // ── Public entry point ─────────────────────────────────────────────────

    /**
     * Scans [bitmap] for duty number, sign-on, and sign-off using ML Kit OCR.
     *
     * Mirrors `DutyScanner.scanImage(uiImage:completion:)` in CalendarView.swift.
     *
     * This is a **suspend function**; call it from a coroutine or `viewModelScope.launch`.
     *
     * @param bitmap  The roster/duty-board photo to scan.
     * @return        A [ScanResult] with extracted text and the parsed duty number.
     */
    suspend fun scanImage(bitmap: Bitmap): ScanResult = suspendCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // Crop to the top 90 % of the image — mirrors `regionOfInterest = CGRect(x:0, y:0, w:1, h:0.9)`
        val croppedBitmap = cropTopNinety(bitmap)
        val inputImage    = InputImage.fromBitmap(croppedBitmap, 0)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val allText = visionText.textBlocks
                    .flatMap { it.lines }
                    .mapNotNull { it.text }
                    .joinToString("\n")

                val result = parseOcrText(allText)
                continuation.resume(result)
            }
            .addOnFailureListener {
                continuation.resume(ScanResult(scannedText = "", dutyNumber = null))
            }
    }

    // ── Internal parsing logic ─────────────────────────────────────────────

    private fun parseOcrText(allText: String): ScanResult {
        val found = mutableMapOf<String, String>()
        var detectedDuty: String? = null

        // 1. DUTY — look for "Duty:" / "Rte:" label first; on swap screens try bare number
        //    before "On:" line (mirrors iOS strategy).
        detectedDuty = matchPattern("""(?i)(?:Duty|Rte):?\s*(\d{1,4})""", allText)
            ?: matchPattern("""(?m)^\s*(\d{1,4})\s*\n(?=.*On:)""", allText)

        // 2. SIGN ON — "Sign On: 16.33" or "On: 15:02" (period or colon as separator)
        matchPattern("""(?i)(?:Sign\s+)?On:?\s*([0-9]{1,2}[:.][0-9]{2})""", allText)
            ?.let { found["Sign On"] = "Sign On: ${it.replace('.', ':')}" }

        // 3. SIGN OFF — "Sign Off: 01.15" or "Off: 00:38"
        matchPattern("""(?i)(?:Sign\s+)?Off:?\s*([0-9]{1,2}[:.][0-9]{2})""", allText)
            ?.let { found["Sign Off"] = "Sign Off: ${it.replace('.', ':')}" }

        detectedDuty?.let { found["Duty"] = "Duty: $it" }

        val scannedText = listOf("Duty", "Sign On", "Sign Off")
            .mapNotNull { found[it] }
            .joinToString("\n")

        return ScanResult(scannedText = scannedText, dutyNumber = detectedDuty)
    }

    /** Returns the first capture group of [pattern] matched in [text], or null. */
    private fun matchPattern(pattern: String, text: String): String? {
        return Regex(pattern, setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
            .find(text)
            ?.groupValues
            ?.drop(1)           // skip full-match group
            ?.lastOrNull { it.isNotBlank() }
            ?.trim()
    }

    /** Crops the bitmap to the top 90 %, mirroring the iOS `regionOfInterest` crop. */
    private fun cropTopNinety(bitmap: Bitmap): Bitmap {
        val targetHeight = (bitmap.height * 0.9f).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, targetHeight)
    }
}
