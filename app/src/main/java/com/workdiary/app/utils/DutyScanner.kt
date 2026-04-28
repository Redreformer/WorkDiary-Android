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
        // Safety: Check for invalid bitmap
        if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) {
            android.util.Log.w("DutyScanner", "Invalid bitmap provided to scanImage")
            continuation.resume(ScanResult(scannedText = "", dutyNumber = null))
            return@suspendCoroutine
        }
        
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
            .addOnFailureListener { e ->
                android.util.Log.e("DutyScanner", "OCR failed", e)
                continuation.resume(ScanResult(scannedText = "", dutyNumber = null))
            }
    }

    // ── Internal parsing logic ─────────────────────────────────────────────

    private fun parseOcrText(allText: String): ScanResult {
        val found = mutableMapOf<String, String>()
        var detectedDuty: String? = null

        // 1. DUTY — mirrors iOS DutyScanner exactly (two strategies):
        //    a) "Duty: 53" or "Rte: 1099" labelled format  → daily + month screenshots
        //    b) Bare number at start of line, followed by a line containing "On:"
        //       → swap screenshot (e.g. "54\nOn: 15:38 …")
        detectedDuty = matchPattern("""(?i)(?:Duty|Rte):?\s*(\d{1,4})""", allText)
            ?: matchPattern("""(?m)^\s*(\d{1,4})\s*\n(?=.*On:)""", allText)

        // 2. SIGN ON — "Sign On: 16.33" or "On: 15:02" (period or colon as separator).
        //
        // Primary: label and time are on the same line or separated only by whitespace.
        // Fallback: ML Kit sometimes interleaves column labels before values on the daily
        //   screenshot (e.g. "Sign On:\nStart:\n15.30"), so we do a proximity search that
        //   allows up to 40 characters of any content between the label and the time.
        val signOnTime = matchPattern("""(?i)(?:Sign\s+)?On:?\s*([0-9]{1,2}[:.][0-9]{2})""", allText)
            ?: proximitySearch("""(?i)Sign\s+On""", allText)
        signOnTime?.let { found["Sign On"] = "Sign On: ${it.replace('.', ':')}" }

        // 3. SIGN OFF — "Sign Off: 01.15" or "Off: 00:38" — same two-pass approach.
        val signOffTime = matchPattern("""(?i)(?:Sign\s+)?Off:?\s*([0-9]{1,2}[:.][0-9]{2})""", allText)
            ?: proximitySearch("""(?i)Sign\s+Off""", allText)
        signOffTime?.let { found["Sign Off"] = "Sign Off: ${it.replace('.', ':')}" }

        detectedDuty?.let { found["Duty"] = "Duty: $it" }

        val scannedText = listOf("Duty", "Sign On", "Sign Off")
            .mapNotNull { found[it] }
            .joinToString("\n")

        return ScanResult(scannedText = scannedText, dutyNumber = detectedDuty)
    }

    /**
     * Finds the label matched by [labelPattern] in [text], then returns the first
     * HH:MM or HH.MM time value found within the next 100 characters that is NOT
     * on the same line as a different field's label.
     *
     * This handles daily-screenshot layouts where ML Kit separates the "Sign On:" /
     * "Sign Off:" label from its value by inserting an adjacent column's label between
     * them (e.g. "Sign On:\nStart:\n15.30").
     *
     * The 100-char window (up from 40) is needed for the Stagecoach web-page layout
     * where "17.01" can be 50+ chars after the "Sign On:" label.
     *
     * Times that appear on the same line as another label (e.g. "Start: 17.03") are
     * skipped — they belong to a different field and are a common source of false
     * positives on the Stagecoach two-column duty card.
     */
    private fun proximitySearch(labelPattern: String, text: String): String? {
        val labelRegex = Regex(labelPattern, setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
        val labelMatch = labelRegex.find(text) ?: return null
        val searchFrom = labelMatch.range.last + 1
        val searchTo   = (searchFrom + 100).coerceAtMost(text.length)
        val window     = text.substring(searchFrom, searchTo)

        val timeRegex = Regex("""[0-9]{1,2}[:.][0-9]{2}""")
        // Matches when the text before a time on the same line is a label, e.g. "Start: "
        // Starts with a letter so it won't match a bare ": " leftover from our own label.
        val otherLabelRe = Regex("""^[A-Za-z][A-Za-z ]*:\s*$""")

        for (match in timeRegex.findAll(window)) {
            val timePos   = match.range.first
            val lineStart = (window.lastIndexOf('\n', timePos - 1) + 1).coerceAtLeast(0)
            val beforeTime = window.substring(lineStart, timePos).trimStart()
            // If another field's label is the only text before this time on its line
            // (e.g. "Start: 17.03"), the time belongs to that other field — skip it.
            if (otherLabelRe.matches(beforeTime)) continue
            return match.value
        }
        return null
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
        // Safety: Ensure we have valid dimensions
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return bitmap
        }
        val targetHeight = (bitmap.height * 0.9f).toInt().coerceAtLeast(1).coerceAtMost(bitmap.height)
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, targetHeight)
        } catch (e: Exception) {
            // If crop fails, return original
            android.util.Log.w("DutyScanner", "Failed to crop bitmap, using original", e)
            bitmap
        }
    }
}
