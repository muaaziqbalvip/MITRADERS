package com.mitv.trademaster.analysis

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device OCR (ML Kit Text Recognition, fully offline — no API key, no
 * network call) that scans a chart screenshot for a currency/instrument
 * pair name such as "EUR/USD", "XAU/USD", "BTC/USDT", "GBPJPY". Trading
 * apps almost always print the pair name somewhere in the header/corner of
 * the chart, so we look for the common patterns rather than trying to
 * read arbitrary text.
 */
object PairNameDetector {

    // Matches things like EUR/USD, EURUSD, XAU/USD, BTC/USDT, GBPJPY, US30, NAS100.
    private val slashPairRegex = Regex("""\b([A-Z]{3,6})\s*/\s*([A-Z]{3,6})\b""")
    private val glued6Regex = Regex("""\b([A-Z]{6})\b""")
    private val knownCurrencyCodes = setOf(
        "USD", "EUR", "GBP", "JPY", "AUD", "NZD", "CAD", "CHF", "CNY", "XAU", "XAG",
        "BTC", "ETH", "USDT", "PKR", "INR", "SGD", "HKD",
    )
    private val knownIndexNames = setOf("US30", "NAS100", "SPX500", "GER40", "UK100", "JPN225")

    /**
     * Runs OCR on [bitmap] and returns the best-guess pair/symbol string,
     * or null if nothing pair-like was found. Suspends off the main thread
     * internally via ML Kit's own async Task API.
     */
    suspend fun detect(bitmap: Bitmap): String? {
        val text = runOcr(bitmap) ?: return null
        return extractPairName(text)
    }

    private suspend fun runOcr(bitmap: Bitmap): String? = suspendCancellableCoroutine { cont ->
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result.text)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun extractPairName(rawText: String): String? {
        val upper = rawText.uppercase()

        // 1) Direct "EUR/USD" style match — highest confidence.
        slashPairRegex.find(upper)?.let { m ->
            val (a, b) = m.destructured
            if (a in knownCurrencyCodes || b in knownCurrencyCodes || a.length == 3) {
                return "$a/$b"
            }
        }

        // 2) Known index/commodity names printed as-is.
        knownIndexNames.forEach { idx -> if (upper.contains(idx)) return idx }

        // 3) Glued 6-letter pairs like "EURUSD" — split into known 3-letter halves.
        glued6Regex.findAll(upper).forEach { m ->
            val token = m.value
            val first = token.substring(0, 3)
            val second = token.substring(3, 6)
            if (first in knownCurrencyCodes && second in knownCurrencyCodes) {
                return "$first/$second"
            }
        }

        return null
    }
}
