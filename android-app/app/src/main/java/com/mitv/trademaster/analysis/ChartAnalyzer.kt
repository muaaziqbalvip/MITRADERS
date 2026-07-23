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
    val trendStrengthPercent: Int = 0,
    val signals: List<String> = emptyList(),
    val leanStatement: String = "",
    val disclaimer: String = "This is an educational pattern observation, not a guaranteed " +
        "outcome. Markets can move against any pattern. Always manage your own risk.",
    val tradeSuggestion: TradeSuggestion? = null,
    val matchedStrategies: List<StrategyMatch> = emptyList(),
)

/**
 * A named trading strategy the current chart structure resembles, with a
 * plain-language reason it matched. Multiple strategies can match at once
 * (e.g. a pullback in an uptrend is both "Trend Following" and "Support
 * Bounce") — showing all of them gives the student more to learn from
 * than collapsing to a single label.
 */
data class StrategyMatch(
    val nameEn: String,
    val nameUr: String,
    val descriptionEn: String,
    val descriptionUr: String,
    val direction: Direction,
)

/**
 * A time-window-scoped recommendation: "for the next [tradeMinutes] minutes,
 * given candles set at [candleIntervalMinutes] each, the lean is [direction]
 * with [confidencePercent]% confidence." Built on top of the same pattern
 * analysis above — this just reframes it against the two numbers the user
 * provides (their chart's candle interval, and how long they intend to
 * hold the trade), because momentum read from a 1-minute chart means
 * something different than the same slope read from a 15-minute chart.
 */
data class TradeSuggestion(
    val direction: Direction,
    val confidencePercent: Int,
    val candleIntervalMinutes: Int,
    val tradeDurationMinutes: Int,
    val reasoning: String,
    val reasoningUrdu: String,
)

private data class Candle(
    val xCenter: Int,
    val top: Int,
    val bottom: Int,
    val isBullish: Boolean,
)

object ChartAnalyzer {

    fun analyze(bitmap: Bitmap): AnalysisResult = analyze(bitmap, candleIntervalMinutes = null, tradeDurationMinutes = null)

    /**
     * Same pattern analysis as the base [analyze], plus an optional
     * time-scoped [TradeSuggestion] when the user has told us their chart's
     * candle interval and how long they plan to hold the trade.
     */
    fun analyze(bitmap: Bitmap, candleIntervalMinutes: Int?, tradeDurationMinutes: Int?): AnalysisResult {
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
        val volatility = recentVolatility(candles)

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

        val signals = buildList {
            add(when (direction) {
                Direction.UP -> "Trend slope: upward"
                Direction.DOWN -> "Trend slope: downward"
                Direction.NEUTRAL -> "Trend slope: flat / sideways"
            })
            add("Latest candle: $pattern")
            if (srNote.isNotBlank()) add(srNote)
            add("Candles analyzed: ${candles.size}")
        }

        val leanStatement = when (direction) {
            Direction.UP -> "Based on the visible structure, the pattern leans toward continued upward movement. This is an educational observation, not an instruction to buy."
            Direction.DOWN -> "Based on the visible structure, the pattern leans toward continued downward movement. This is an educational observation, not an instruction to sell."
            Direction.NEUTRAL -> "The visible structure doesn't show a clear directional lean right now — price looks range-bound or the signals are mixed."
        }

        val tradeSuggestion = if (candleIntervalMinutes != null && tradeDurationMinutes != null && candleIntervalMinutes > 0 && tradeDurationMinutes > 0) {
            buildTradeSuggestion(direction, strength, volatility, srNote, candleIntervalMinutes, tradeDurationMinutes)
        } else null

        val matchedStrategies = detectStrategies(candles, direction, strength, srNote, volatility)

        return AnalysisResult(
            direction = direction,
            confidence = confidence,
            pattern = pattern,
            explanation = explanation,
            srNote = srNote,
            candlesDetected = candles.size,
            trendStrengthPercent = (strength * 100).toInt().coerceIn(0, 100),
            signals = signals,
            leanStatement = leanStatement,
            tradeSuggestion = tradeSuggestion,
            matchedStrategies = matchedStrategies,
        )
    }

    /**
     * Checks the measured trend/volatility/S-R signals against a handful
     * of well-known, named strategies from technical analysis education.
     * This isn't a separate ML model — it's the same underlying
     * measurements (trend strength, support/resistance proximity,
     * volatility) reframed as "which textbook setup does this resemble",
     * which is more useful for a learning app than a bare confidence
     * number alone.
     */
    private fun detectStrategies(candles: List<Candle>, direction: Direction, strength: Double, srNote: String, volatility: Double): List<StrategyMatch> {
        val matches = mutableListOf<StrategyMatch>()

        // Trend Following: strong, clean directional move.
        if (strength > 0.55 && direction != Direction.NEUTRAL) {
            matches.add(StrategyMatch(
                nameEn = "Trend Following", nameUr = "رجحان کی پیروی",
                descriptionEn = "Price shows a clean, sustained directional move — the classic setup for riding an established trend rather than fighting it.",
                descriptionUr = "قیمت ایک صاف اور مستقل سمتی حرکت دکھا رہی ہے — یہ ایک قائم شدہ رجحان کے ساتھ چلنے کا کلاسک سیٹ اپ ہے۔",
                direction = direction,
            ))
        }

        // Support/Resistance Bounce: price near a level with direction favoring a bounce.
        if (srNote.contains("support") && direction == Direction.UP) {
            matches.add(StrategyMatch(
                nameEn = "Support Bounce", nameUr = "سپورٹ باؤنس",
                descriptionEn = "Price is trading near a recent support level and showing upward reaction — a common bounce setup, though support can also break.",
                descriptionUr = "قیمت حالیہ سپورٹ لیول کے قریب ہے اور اوپر کی طرف ردعمل دکھا رہی ہے — یہ ایک عام باؤنس سیٹ اپ ہے، اگرچہ سپورٹ ٹوٹ بھی سکتی ہے۔",
                direction = Direction.UP,
            ))
        }
        if (srNote.contains("resistance") && direction == Direction.DOWN) {
            matches.add(StrategyMatch(
                nameEn = "Resistance Rejection", nameUr = "ریزسٹنس ریجیکشن",
                descriptionEn = "Price is trading near a recent resistance level and showing downward reaction — a common rejection setup, though resistance can also break.",
                descriptionUr = "قیمت حالیہ ریزسٹنس لیول کے قریب ہے اور نیچے کی طرف ردعمل دکھا رہی ہے — یہ ایک عام ریجیکشن سیٹ اپ ہے۔",
                direction = Direction.DOWN,
            ))
        }

        // Breakout: high volatility with strong directional move — price moving fast through a level.
        if (volatility > 0.5 && strength > 0.5 && direction != Direction.NEUTRAL) {
            matches.add(StrategyMatch(
                nameEn = "Momentum Breakout", nameUr = "مومینٹم بریک آؤٹ",
                descriptionEn = "Larger, more volatile candles combined with a strong directional push resemble a breakout — fast moves that can continue or reverse sharply.",
                descriptionUr = "بڑی، زیادہ اتار چڑھاؤ والی کینڈلز مضبوط سمتی زور کے ساتھ ایک بریک آؤٹ سے مشابہت رکھتی ہیں — تیز حرکتیں جو جاری رہ سکتی ہیں یا اچانک پلٹ سکتی ہیں۔",
                direction = direction,
            ))
        }

        // Range/Consolidation: weak trend, low volatility.
        if (strength < 0.3 && volatility < 0.35) {
            matches.add(StrategyMatch(
                nameEn = "Range-Bound / Consolidation", nameUr = "محدود دائرہ / استحکام",
                descriptionEn = "Price is moving sideways without a clear trend — often better suited to range strategies than trend-following ones.",
                descriptionUr = "قیمت واضح رجحان کے بغیر سائیڈ ویز حرکت کر رہی ہے — یہ اکثر رینج حکمت عملیوں کے لیے زیادہ موزوں ہوتی ہے۔",
                direction = Direction.NEUTRAL,
            ))
        }

        return matches
    }

    /**
     * Turns the raw trend/volatility read into a suggestion scoped to the
     * user's specific time window. The core idea:
     *   - More trade-duration relative to candle-interval (i.e. the trade
     *     spans many candles) means today's momentum has more candles left
     *     to either confirm or fade — so confidence is discounted the
     *     longer the hold relative to the candle size.
     *   - High recent volatility relative to the trend strength lowers
     *     confidence (choppy conditions are less predictable than a clean
     *     trend).
     *   - Very short intervals (1-2 min) are inherently noisier than
     *     longer ones, so they get an extra small discount — this mirrors
     *     the real-world fact that 1-minute candle "signals" are far less
     *     reliable than 15-minute ones, without pretending either is a
     *     guarantee.
     */
    private fun buildTradeSuggestion(
        direction: Direction,
        strength: Double,
        volatility: Double,
        srNote: String,
        candleIntervalMinutes: Int,
        tradeDurationMinutes: Int,
    ): TradeSuggestion {
        val candlesInWindow = (tradeDurationMinutes.toDouble() / candleIntervalMinutes).coerceAtLeast(0.5)

        // Base confidence from trend strength, 0-100 scale.
        var confidencePercent = (strength * 100).coerceIn(0.0, 100.0)

        // Discount for how many candles the trade window spans — a
        // suggestion for "the next 1 candle" is inherently more grounded
        // in what we just measured than "the next 10 candles".
        val windowDiscount = when {
            candlesInWindow <= 1.5 -> 1.0
            candlesInWindow <= 3.0 -> 0.85
            candlesInWindow <= 6.0 -> 0.7
            else -> 0.55
        }
        confidencePercent *= windowDiscount

        // Discount for choppiness: high volatility relative to trend
        // strength means the recent move is less "clean".
        val choppinessDiscount = if (volatility > 0.5 && strength < 0.5) 0.8 else 1.0
        confidencePercent *= choppinessDiscount

        // Very short candle intervals are noisier in general.
        val intervalDiscount = when {
            candleIntervalMinutes <= 1 -> 0.85
            candleIntervalMinutes <= 2 -> 0.92
            else -> 1.0
        }
        confidencePercent *= intervalDiscount

        // Support/resistance proximity nudges confidence in the direction
        // it favors, same logic as the base analyzer.
        if (srNote.contains("support") && direction == Direction.UP) confidencePercent = (confidencePercent + 8).coerceAtMost(95.0)
        if (srNote.contains("resistance") && direction == Direction.DOWN) confidencePercent = (confidencePercent + 8).coerceAtMost(95.0)

        val finalDirection = if (confidencePercent < 35.0) Direction.NEUTRAL else direction
        val finalConfidence = confidencePercent.toInt().coerceIn(5, 95)

        val windowLabel = if (candlesInWindow <= 1.05) "the next candle" else "the next ${"%.0f".format(candlesInWindow)} candles"
        val reasoning = when (finalDirection) {
            Direction.UP -> "Reading a $candleIntervalMinutes-minute chart for a $tradeDurationMinutes-minute window ($windowLabel), momentum and structure lean upward. Confidence is discounted for how far this window extends past what was actually measured."
            Direction.DOWN -> "Reading a $candleIntervalMinutes-minute chart for a $tradeDurationMinutes-minute window ($windowLabel), momentum and structure lean downward. Confidence is discounted for how far this window extends past what was actually measured."
            Direction.NEUTRAL -> "For this $tradeDurationMinutes-minute window on a $candleIntervalMinutes-minute chart, the signal isn't strong enough to lean either way with reasonable confidence — conditions look choppy or the window is long relative to what was measured."
        }
        val reasoningUrdu = when (finalDirection) {
            Direction.UP -> "$candleIntervalMinutes منٹ کے چارٹ کو $tradeDurationMinutes منٹ کی ونڈو کے لیے پڑھتے ہوئے، رجحان اوپر کی طرف ہے۔ اعتماد کو اس بنیاد پر کم کیا گیا ہے کہ یہ ونڈو ناپے گئے وقت سے کتنی آگے ہے۔"
            Direction.DOWN -> "$candleIntervalMinutes منٹ کے چارٹ کو $tradeDurationMinutes منٹ کی ونڈو کے لیے پڑھتے ہوئے، رجحان نیچے کی طرف ہے۔ اعتماد کو اس بنیاد پر کم کیا گیا ہے کہ یہ ونڈو ناپے گئے وقت سے کتنی آگے ہے۔"
            Direction.NEUTRAL -> "$candleIntervalMinutes منٹ کے چارٹ پر اس $tradeDurationMinutes منٹ کی ونڈو کے لیے سگنل کسی بھی طرف مناسب اعتماد کے ساتھ کافی مضبوط نہیں ہے۔"
        }

        return TradeSuggestion(
            direction = finalDirection,
            confidencePercent = finalConfidence,
            candleIntervalMinutes = candleIntervalMinutes,
            tradeDurationMinutes = tradeDurationMinutes,
            reasoning = reasoning,
            reasoningUrdu = reasoningUrdu,
        )
    }

    /** Rough volatility proxy: how much candle ranges vary relative to their average, over the recent window. */
    private fun recentVolatility(candles: List<Candle>): Double {
        val recent = candles.takeLast(min(candles.size, 15))
        if (recent.size < 3) return 0.0
        val ranges = recent.map { (it.bottom - it.top).toDouble() }
        val avg = ranges.average().coerceAtLeast(1.0)
        val variance = ranges.map { (it - avg) * (it - avg) }.average()
        val stdDev = Math.sqrt(variance)
        return (stdDev / avg).coerceIn(0.0, 2.0)
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
