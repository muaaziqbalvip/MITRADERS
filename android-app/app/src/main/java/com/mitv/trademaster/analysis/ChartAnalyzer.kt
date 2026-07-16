package com.mitv.trademaster.analysis

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.min

/**
 * On-device (offline) chart pattern analyzer.
 *
 * Scope note: this performs GENERAL technical-analysis pattern recognition
 * (trend direction from candle midpoint slope, basic candlestick pattern
 * tagging, support/resistance proximity) on a chart screenshot. It targets
 * swing-style charts (15-minute candles and above) and intentionally does
 * NOT special-case very short expiries.
 *
 * Output is always framed as an OBSERVATION + historical lean + confidence
 * band — never a guaranteed instruction. Keep that framing if you extend
 * this file. This mirrors the contract of the server-side
 * `analysis_engine.py` used for the "online / advanced" mode.
 */

enum class Direction { UP, DOWN, NEUTRAL }
enum class Confidence { LOW, MEDIUM, HIGH }

data class AnalysisResult(
    val direction: Direction,
    val confidence: Confidence,
    val pattern: String,
    val explanation: String,
    val srNote: String,
    val candlesDetected: Int,
    val disclaimer: String = "This is an educational pattern observation, not a guaranteed " +
        "outcome. Markets can move against any pattern. Always manage your own risk.",
)

private data class Candle(
    val xCenter: Int,
    val top: Int,
    val bottom: Int,
    val isBullish: Boolean,
)

object ChartAnalyzer {

    fun analyze(bitmap: Bitmap): AnalysisResult {
        val candles = segmentCandles(bitmap)

        if (candles.size < 3) {
            return AnalysisResult(
                direction = Direction.NEUTRAL,
                confidence = Confidence.LOW,
                pattern = "Could not clearly detect candles",
                explanation = "The screenshot didn't contain enough clearly distinguishable " +
                    "candlesticks to analyze. Try cropping closer to the chart area with good contrast.",
                srNote = "",
                candlesDetected = candles.size,
            )
        }

        val (direction, strength) = trendFromCandles(candles)
        val pattern = detectPattern(candles)
        val srNote = supportResistanceNote(candles, bitmap.height)

        var confidence = when {
            strength > 0.6 -> Confidence.HIGH
            strength > 0.3 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        if (srNote.contains("support") && direction != Direction.DOWN && confidence == Confidence.LOW) {
            confidence = Confidence.MEDIUM
        }
        if (srNote.contains("resistance") && direction != Direction.UP && confidence == Confidence.LOW) {
            confidence = Confidence.MEDIUM
        }

        val explanation = "Detected ${candles.size} candles in view. Recent price structure shows a " +
            "${direction.name.lowercase()} lean based on candle midpoint slope. Latest candle pattern: $pattern. $srNote."

        return AnalysisResult(
            direction = direction,
            confidence = confidence,
            pattern = pattern,
            explanation = explanation,
            srNote = srNote,
            candlesDetected = candles.size,
        )
    }

    private fun segmentCandles(bitmap: Bitmap): List<Candle> {
        val w = bitmap.width
        val h = bitmap.height
        val candles = mutableListOf<Candle>()

        // Sample every few columns for performance on large screenshots
        val step = max(1, w / 400)

        var x = 0
        while (x < w) {
            var top = -1
            var bottom = -1
            var greenCount = 0
            var redCount = 0

            var y = 0
            while (y < h) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val isGreen = g > r + 15 && g > b + 15 && g > 60
                val isRed = r > g + 15 && r > b + 15 && r > 60

                if (isGreen || isRed) {
                    if (top == -1) top = y
                    bottom = y
                    if (isGreen) greenCount++ else redCount++
                }
                y += 2 // vertical sampling for speed
            }

            if (top != -1) {
                candles.add(
                    Candle(
                        xCenter = x,
                        top = top,
                        bottom = bottom,
                        isBullish = greenCount >= redCount,
                    )
                )
            }
            x += step
        }
        return candles
    }

    private fun max(a: Int, b: Int) = if (a > b) a else b

    private fun trendFromCandles(candles: List<Candle>): Pair<Direction, Double> {
        if (candles.size < 3) return Direction.NEUTRAL to 0.0

        val recent = candles.takeLast(min(candles.size, 15))
        val midpoints = recent.map { (it.top + it.bottom) / 2.0 }

        // Simple linear regression slope
        val n = midpoints.size
        val xs = (0 until n).map { it.toDouble() }
        val xMean = xs.average()
        val yMean = midpoints.average()
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            num += (xs[i] - xMean) * (midpoints[i] - yMean)
            den += (xs[i] - xMean) * (xs[i] - xMean)
        }
        val slope = if (den != 0.0) num / den else 0.0
        val normalized = -slope // image y grows downward; negative slope = price rising

        return when {
            normalized > 0.8 -> Direction.UP to min(abs(normalized) / 5.0, 1.0)
            normalized < -0.8 -> Direction.DOWN to min(abs(normalized) / 5.0, 1.0)
            else -> Direction.NEUTRAL to 0.2
        }
    }

    private fun detectPattern(candles: List<Candle>): String {
        if (candles.size < 2) return "Insufficient candle data"

        val last = candles.last()
        val prev = candles[candles.size - 2]

        val lastRange = max(last.bottom - last.top, 1)
        // We don't track body vs wick separately in the lightweight scan,
        // so approximate using overall candle height relative to local average
        val avgRange = candles.takeLast(10).map { it.bottom - it.top }.average()

        if (lastRange > avgRange * 1.8) {
            return "Long-range candle (possible strong move or rejection)"
        }
        if (prev.isBullish != last.isBullish && lastRange > (prev.bottom - prev.top) * 1.2) {
            val dir = if (last.isBullish) "Bullish" else "Bearish"
            return "$dir engulfing-style pattern"
        }
        if (lastRange < avgRange * 0.4) {
            return "Small-range / indecision candle"
        }
        return "Standard trend continuation candle"
    }

    private fun supportResistanceNote(candles: List<Candle>, imgHeight: Int): String {
        if (candles.size < 5) return "Not enough data to assess support/resistance"

        val recentSlice = candles.takeLast(min(candles.size, 20))
        val lows = recentSlice.map { it.bottom }
        val highs = recentSlice.map { it.top }

        val currentLow = candles.last().bottom
        val currentHigh = candles.last().top
        val recentSupport = lows.max()
        val recentResistance = highs.min()

        return when {
            abs(currentLow - recentSupport) < imgHeight * 0.03 -> "Price is trading near a recent support zone"
            abs(currentHigh - recentResistance) < imgHeight * 0.03 -> "Price is trading near a recent resistance zone"
            else -> "Price is trading mid-range, away from recent extremes"
        }
    }
}
