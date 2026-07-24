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

/**
 * A single computed technical indicator reading, shown to the user as part
 * of "the bot looked at these indicators before deciding" — mirrors what a
 * real trading terminal shows (RSI, moving-average bias, volatility) so the
 * final call isn't a black box.
 */
data class IndicatorReading(
    val nameEn: String,
    val nameUr: String,
    val valueLabel: String, // e.g. "68 (Overbought)", "Rising", "High"
    val bias: Direction,
    val weight: Double,
)

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
    // ---- Next-candle prediction ----
    val nextCandlePrediction: Direction = Direction.NEUTRAL,
    val nextCandleConfidencePercent: Int = 0,
    val detectedPatterns: List<CandlePattern> = emptyList(),
    val predictedCloseRelativeToOpen: String = "", // human-readable e.g. "likely to close above current level"
    // ---- Indicators & richer context (for the "full signal bot" upgrade) ----
    val indicators: List<IndicatorReading> = emptyList(),
    val detectedPairName: String? = null, // OCR-detected symbol, e.g. "EUR/USD"
    val entryReferencePrice: Double? = null, // relative y-position turned into a 0-100 "level" for marking on the chart
    val supportLevelPercent: Int? = null, // where support sits, as % from top of image (for drawing a line)
    val resistanceLevelPercent: Int? = null,
    val microPatterns: List<String> = emptyList(), // very small/short-lived shapes (tiny wicks, micro-dojis, pin-bars) called out separately from the main named patterns
    val volatilityLabel: String = "",
    val candleIntervalMinutes: Int? = null,
    val tradeDurationMinutes: Int? = null,
    val exportCandles: List<ExportCandle> = emptyList(),
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
    // Approximate body boundaries within [top, bottom] — since the lightweight
    // pixel scan can't perfectly separate wick from body, we estimate the body
    // as the inner ~55% of the detected colored range, which is close enough
    // for named-pattern recognition (doji/hammer/engulfing all hinge on
    // body-to-wick RATIOS rather than exact pixels).
    val bodyTop: Int,
    val bodyBottom: Int,
) {
    val range get() = (bottom - top).coerceAtLeast(1)
    val bodySize get() = (bodyBottom - bodyTop).coerceAtLeast(0)
    val upperWick get() = (bodyTop - top).coerceAtLeast(0)
    val lowerWick get() = (bottom - bodyBottom).coerceAtLeast(0)
}

/**
 * Named candlestick pattern recognized in the recent candles, with the
 * textbook next-candle bias it implies. This is the piece that actually
 * answers "given this pattern, which way does the NEXT candle likely go" —
 * rather than just describing the last candle in isolation.
 */
data class CandlePattern(
    val nameEn: String,
    val nameUr: String,
    val descriptionEn: String,
    val descriptionUr: String,
    val nextCandleBias: Direction,
    val reliability: Double, // 0.0-1.0, how much weight this pattern gets in the final call
)

object ChartAnalyzer {

    fun analyze(bitmap: Bitmap): AnalysisResult = analyze(bitmap, candleIntervalMinutes = null, tradeDurationMinutes = null)

    /**
     * Full analysis pass: pattern recognition + indicator computation +
     * next-candle prediction, all time-aware when the user has supplied
     * their chart's candle interval and intended trade duration.
     *
     * [detectedPairName] is optional OCR output (see PairNameDetector) —
     * when supplied it's carried straight through to the result/export so
     * the downloadable image can show "EUR/USD" instead of nothing.
     */
    fun analyze(
        bitmap: Bitmap,
        candleIntervalMinutes: Int?,
        tradeDurationMinutes: Int?,
        detectedPairName: String? = null,
    ): AnalysisResult {
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
                detectedPairName = detectedPairName,
                candleIntervalMinutes = candleIntervalMinutes,
                tradeDurationMinutes = tradeDurationMinutes,
            )
        }

        val (direction, strength) = trendFromCandles(candles)
        val pattern = detectPattern(candles)
        val srNote = supportResistanceNote(candles, bitmap.height)
        val volatility = recentVolatility(candles)
        val detectedPatterns = detectNamedPatterns(candles)
        val microPatterns = detectMicroPatterns(candles)
        val indicators = computeIndicators(candles, direction, strength, volatility, srNote)
        val (nextDir, nextConfidence) = predictNextCandle(direction, strength, detectedPatterns, srNote, indicators)

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
            detectedPatterns.forEach { add("Pattern found: ${it.nameEn} → ${it.nextCandleBias.name.lowercase()} bias") }
            indicators.forEach { add("${it.nameEn}: ${it.valueLabel}") }
            microPatterns.forEach { add("Micro-signal: $it") }
        }

        val leanStatement = when (direction) {
            Direction.UP -> "Based on the visible structure, the pattern leans toward continued upward movement. This is an educational observation, not an instruction to buy."
            Direction.DOWN -> "Based on the visible structure, the pattern leans toward continued downward movement. This is an educational observation, not an instruction to sell."
            Direction.NEUTRAL -> "The visible structure doesn't show a clear directional lean right now — price looks range-bound or the signals are mixed."
        }

        val predictedCloseRelativeToOpen = when (nextDir) {
            Direction.UP -> "Next candle: likely to open near the current close and finish HIGHER (green candle lean)."
            Direction.DOWN -> "Next candle: likely to open near the current close and finish LOWER (red candle lean)."
            Direction.NEUTRAL -> "Next candle: signals are mixed — could open and close near the same level (indecisive candle)."
        }

        val tradeSuggestion = if (candleIntervalMinutes != null && tradeDurationMinutes != null && candleIntervalMinutes > 0 && tradeDurationMinutes > 0) {
            buildTradeSuggestion(direction, strength, volatility, srNote, candleIntervalMinutes, tradeDurationMinutes)
        } else null

        val matchedStrategies = detectStrategies(candles, direction, strength, srNote, volatility)

        // Support/resistance as % from top of image, for drawing reference lines on the exported chart.
        val recentSlice = candles.takeLast(min(candles.size, 20))
        val supportPct = if (recentSlice.isNotEmpty() && bitmap.height > 0) {
            ((recentSlice.map { it.bottom }.max().toDouble() / bitmap.height) * 100).toInt().coerceIn(0, 100)
        } else null
        val resistancePct = if (recentSlice.isNotEmpty() && bitmap.height > 0) {
            ((recentSlice.map { it.top }.min().toDouble() / bitmap.height) * 100).toInt().coerceIn(0, 100)
        } else null
        val entryRefPct = if (bitmap.height > 0) {
            (candles.last().let { (it.top + it.bottom) / 2.0 } / bitmap.height) * 100
        } else null

        val volatilityLabel = when {
            volatility > 0.7 -> "High"
            volatility > 0.35 -> "Moderate"
            else -> "Low"
        }

        val exportCandles = candles.takeLast(min(candles.size, 30)).map {
            ExportCandle(
                top = it.top,
                bottom = it.bottom,
                bodyTop = it.bodyTop,
                bodyBottom = it.bodyBottom,
                isBullish = it.isBullish,
            )
        }

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
            nextCandlePrediction = nextDir,
            nextCandleConfidencePercent = nextConfidence,
            detectedPatterns = detectedPatterns,
            predictedCloseRelativeToOpen = predictedCloseRelativeToOpen,
            indicators = indicators,
            detectedPairName = detectedPairName,
            entryReferencePrice = entryRefPct,
            supportLevelPercent = supportPct,
            resistanceLevelPercent = resistancePct,
            microPatterns = microPatterns,
            volatilityLabel = volatilityLabel,
            candleIntervalMinutes = candleIntervalMinutes,
            tradeDurationMinutes = tradeDurationMinutes,
            exportCandles = exportCandles,
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

    /**
     * Named candlestick-pattern recognition across the last 1-3 candles.
     * Each recognized pattern carries its own textbook next-candle bias and
     * reliability weight — this is the actual "look at THIS candle's shape
     * and the ones before it, and call which way the NEXT one likely goes"
     * logic. Multiple patterns can match; predictNextCandle() below combines
     * them all into one final weighted call instead of just picking the
     * first match.
     */
    private fun detectNamedPatterns(candles: List<Candle>): List<CandlePattern> {
        if (candles.size < 2) return emptyList()
        val patterns = mutableListOf<CandlePattern>()

        val c1 = candles.last() // most recent
        val c2 = candles[candles.size - 2]
        val c3 = candles.getOrNull(candles.size - 3)

        val avgRange = candles.takeLast(min(candles.size, 12)).map { it.range }.average().coerceAtLeast(1.0)
        val bodyRatio1 = c1.bodySize.toDouble() / c1.range

        // ---- Single-candle patterns ----

        // Doji: tiny body relative to range — market indecision, often precedes a reversal.
        if (bodyRatio1 < 0.12 && c1.range > avgRange * 0.5) {
            patterns.add(CandlePattern(
                nameEn = "Doji", nameUr = "دوجی",
                descriptionEn = "Open and close are nearly equal — the market is undecided. Often signals the current move is losing steam.",
                descriptionUr = "اوپننگ اور کلوزنگ تقریباً برابر ہیں — مارکیٹ غیر فیصلہ کن ہے۔ اکثر یہ موجودہ حرکت کے کمزور پڑنے کی نشاندہی کرتا ہے۔",
                nextCandleBias = Direction.NEUTRAL,
                reliability = 0.45,
            ))
        }

        // Hammer: small body near the top, long lower wick — bullish reversal signal at the bottom of a move.
        if (bodyRatio1 in 0.08..0.35 && c1.lowerWick > c1.bodySize * 2 && c1.upperWick < c1.bodySize) {
            patterns.add(CandlePattern(
                nameEn = "Hammer", nameUr = "ہیمر",
                descriptionEn = "Small body with a long lower wick — buyers stepped in and pushed price back up after a dip. A classic bullish-reversal shape.",
                descriptionUr = "چھوٹا باڈی لمبی نیچے کی وِک کے ساتھ — خریداروں نے قیمت کو نیچے سے واپس اوپر دھکیل دیا۔ یہ ایک کلاسک تیزی کے ریورسل کی شکل ہے۔",
                nextCandleBias = Direction.UP,
                reliability = 0.6,
            ))
        }

        // Shooting Star: small body near the bottom, long upper wick — bearish reversal at the top of a move.
        if (bodyRatio1 in 0.08..0.35 && c1.upperWick > c1.bodySize * 2 && c1.lowerWick < c1.bodySize) {
            patterns.add(CandlePattern(
                nameEn = "Shooting Star", nameUr = "شوٹنگ سٹار",
                descriptionEn = "Small body with a long upper wick — sellers rejected the higher price and pushed it back down. A classic bearish-reversal shape.",
                descriptionUr = "چھوٹا باڈی لمبی اوپر کی وِک کے ساتھ — بیچنے والوں نے اونچی قیمت کو مسترد کر کے واپس نیچے دھکیل دیا۔ یہ ایک کلاسک مندی کے ریورسل کی شکل ہے۔",
                nextCandleBias = Direction.DOWN,
                reliability = 0.6,
            ))
        }

        // Marubozu-style: very large body filling almost the whole range — strong conviction, continuation likely.
        if (bodyRatio1 > 0.85 && c1.range > avgRange * 1.1) {
            val dir = if (c1.isBullish) Direction.UP else Direction.DOWN
            patterns.add(CandlePattern(
                nameEn = if (c1.isBullish) "Bullish Marubozu" else "Bearish Marubozu",
                nameUr = if (c1.isBullish) "تیزی کا ماروبوزو" else "مندی کا ماروبوزو",
                descriptionEn = "A strong-bodied candle with almost no wicks — one side was in full control the entire candle, which often continues into the next one.",
                descriptionUr = "ایک مضبوط باڈی والی کینڈل جس میں تقریباً کوئی وِک نہیں — پوری کینڈل کے دوران ایک طرف کا مکمل کنٹرول رہا، جو اکثر اگلی کینڈل میں بھی جاری رہتا ہے۔",
                nextCandleBias = dir,
                reliability = 0.55,
            ))
        }

        // ---- Two-candle patterns ----

        // Bullish/Bearish Engulfing: current body fully engulfs the previous body, opposite color.
        if (c1.isBullish != c2.isBullish && c1.bodySize > c2.bodySize * 1.15) {
            val dir = if (c1.isBullish) Direction.UP else Direction.DOWN
            patterns.add(CandlePattern(
                nameEn = if (c1.isBullish) "Bullish Engulfing" else "Bearish Engulfing",
                nameUr = if (c1.isBullish) "تیزی کا اینگلفنگ" else "مندی کا اینگلفنگ",
                descriptionEn = "The latest candle's body fully swallows the previous one in the opposite color — a strong reversal signal in that new direction.",
                descriptionUr = "تازہ ترین کینڈل کا باڈی پچھلی کینڈل کو مخالف رنگ میں مکمل طور پر نگل جاتا ہے — یہ اس نئی سمت میں ایک مضبوط ریورسل سگنل ہے۔",
                nextCandleBias = dir,
                reliability = 0.68,
            ))
        }

        // Piercing Line (bullish) / Dark Cloud Cover (bearish): opens beyond prior close, closes back past its midpoint.
        if (c3 != null) {
            val prevMid = (c2.top + c2.bottom) / 2.0
            if (!c2.isBullish && c1.isBullish && c1.bodyBottom > c2.bodyBottom && (c1.bodyTop) < prevMid) {
                patterns.add(CandlePattern(
                    nameEn = "Piercing Line", nameUr = "پیئرسنگ لائن",
                    descriptionEn = "After a down candle, this candle opens lower but recovers back past the midpoint — buyers regaining control.",
                    descriptionUr = "نیچے کی کینڈل کے بعد، یہ کینڈل نیچے کھلتی ہے لیکن درمیانی نقطے سے آگے واپس آ جاتی ہے — خریدار دوبارہ کنٹرول حاصل کر رہے ہیں۔",
                    nextCandleBias = Direction.UP,
                    reliability = 0.52,
                ))
            }
            if (c2.isBullish && !c1.isBullish && c1.bodyTop < c2.bodyTop && (c1.bodyBottom) > prevMid) {
                patterns.add(CandlePattern(
                    nameEn = "Dark Cloud Cover", nameUr = "ڈارک کلاؤڈ کور",
                    descriptionEn = "After an up candle, this candle opens higher but falls back past the midpoint — sellers regaining control.",
                    descriptionUr = "اوپر کی کینڈل کے بعد، یہ کینڈل اونچی کھلتی ہے لیکن درمیانی نقطے سے آگے واپس گر جاتی ہے — بیچنے والے دوبارہ کنٹرول حاصل کر رہے ہیں۔",
                    nextCandleBias = Direction.DOWN,
                    reliability = 0.52,
                ))
            }
        }

        // ---- Three-candle patterns ----
        if (c3 != null) {
            // Morning Star: down, small indecisive middle, strong up — bullish reversal.
            val midBodyRatio = c2.bodySize.toDouble() / c2.range
            if (!c3.isBullish && midBodyRatio < 0.3 && c1.isBullish && c1.bodySize > c3.bodySize * 0.6) {
                patterns.add(CandlePattern(
                    nameEn = "Morning Star", nameUr = "مارننگ سٹار",
                    descriptionEn = "A down candle, then a small indecisive one, then a strong up candle — a well-known three-candle bullish reversal.",
                    descriptionUr = "ایک نیچے کی کینڈل، پھر ایک چھوٹی غیر فیصلہ کن کینڈل، پھر ایک مضبوط اوپر کی کینڈل — یہ ایک معروف تین کینڈل کا تیزی کا ریورسل ہے۔",
                    nextCandleBias = Direction.UP,
                    reliability = 0.72,
                ))
            }
            // Evening Star: up, small indecisive middle, strong down — bearish reversal.
            if (c3.isBullish && midBodyRatio < 0.3 && !c1.isBullish && c1.bodySize > c3.bodySize * 0.6) {
                patterns.add(CandlePattern(
                    nameEn = "Evening Star", nameUr = "ایوننگ سٹار",
                    descriptionEn = "An up candle, then a small indecisive one, then a strong down candle — a well-known three-candle bearish reversal.",
                    descriptionUr = "ایک اوپر کی کینڈل، پھر ایک چھوٹی غیر فیصلہ کن کینڈل، پھر ایک مضبوط نیچے کی کینڈل — یہ ایک معروف تین کینڈل کا مندی کا ریورسل ہے۔",
                    nextCandleBias = Direction.DOWN,
                    reliability = 0.72,
                ))
            }
            // Three White Soldiers: three consecutive strong bullish candles — strong continuation.
            if (c3.isBullish && c2.isBullish && c1.isBullish &&
                c3.bodySize.toDouble() / c3.range > 0.6 && c2.bodySize.toDouble() / c2.range > 0.6 && bodyRatio1 > 0.6) {
                patterns.add(CandlePattern(
                    nameEn = "Three White Soldiers", nameUr = "تین سفید سپاہی",
                    descriptionEn = "Three consecutive strong bullish candles — a powerful continuation signal, though it can also mean the move is getting stretched.",
                    descriptionUr = "تین لگاتار مضبوط تیزی کی کینڈلز — یہ ایک طاقتور تسلسل کا سگنل ہے، اگرچہ اس کا مطلب یہ بھی ہو سکتا ہے کہ حرکت زیادہ کھنچ چکی ہے۔",
                    nextCandleBias = Direction.UP,
                    reliability = 0.58,
                ))
            }
            // Three Black Crows: three consecutive strong bearish candles — strong continuation down.
            if (!c3.isBullish && !c2.isBullish && !c1.isBullish &&
                c3.bodySize.toDouble() / c3.range > 0.6 && c2.bodySize.toDouble() / c2.range > 0.6 && bodyRatio1 > 0.6) {
                patterns.add(CandlePattern(
                    nameEn = "Three Black Crows", nameUr = "تین کالے کوے",
                    descriptionEn = "Three consecutive strong bearish candles — a powerful continuation signal down, though it can also mean the drop is getting stretched.",
                    descriptionUr = "تین لگاتار مضبوط مندی کی کینڈلز — یہ نیچے کی طرف ایک طاقتور تسلسل کا سگنل ہے، اگرچہ اس کا مطلب یہ بھی ہو سکتا ہے کہ گراوٹ زیادہ کھنچ چکی ہے۔",
                    nextCandleBias = Direction.DOWN,
                    reliability = 0.58,
                ))
            }
        }

        return patterns
    }

    /**
     * Combines trend direction, named-pattern biases, and support/resistance
     * context into ONE final next-candle call with a confidence percentage —
     * this is the actual "will the next candle close up or down" answer.
     * Each recognized pattern votes with its own reliability weight; trend
     * direction acts as the baseline vote when no strong pattern overrides it.
     */
    private fun predictNextCandle(
        trendDirection: Direction,
        trendStrength: Double,
        patterns: List<CandlePattern>,
        srNote: String,
        indicators: List<IndicatorReading>,
    ): Pair<Direction, Int> {
        var upScore = 0.0
        var downScore = 0.0

        // Baseline vote from trend, weighted by measured strength.
        when (trendDirection) {
            Direction.UP -> upScore += trendStrength * 0.5
            Direction.DOWN -> downScore += trendStrength * 0.5
            Direction.NEUTRAL -> { upScore += 0.1; downScore += 0.1 }
        }

        // Each named pattern votes with its reliability weight.
        patterns.forEach { p ->
            when (p.nextCandleBias) {
                Direction.UP -> upScore += p.reliability
                Direction.DOWN -> downScore += p.reliability
                Direction.NEUTRAL -> { upScore += p.reliability * 0.2; downScore += p.reliability * 0.2 }
            }
        }

        // Each indicator votes with its own weight — this is what makes the
        // final call "trend + patterns + indicators combined" rather than
        // trend/pattern alone.
        indicators.forEach { ind ->
            when (ind.bias) {
                Direction.UP -> upScore += ind.weight
                Direction.DOWN -> downScore += ind.weight
                Direction.NEUTRAL -> { upScore += ind.weight * 0.15; downScore += ind.weight * 0.15 }
            }
        }

        // Support/resistance context nudges the relevant side slightly.
        if (srNote.contains("support")) upScore += 0.15
        if (srNote.contains("resistance")) downScore += 0.15

        val total = (upScore + downScore).coerceAtLeast(0.01)
        val upPct = (upScore / total * 100).coerceIn(0.0, 100.0)
        val downPct = 100.0 - upPct

        return if (upPct >= downPct) {
            Direction.UP to upPct.toInt().coerceIn(50, 96)
        } else {
            Direction.DOWN to downPct.toInt().coerceIn(50, 96)
        }
    }

    /**
     * Computes a small set of classic technical indicators from the
     * detected candle sequence — the goal is for the final call to be
     * "trend + patterns + indicators combined", the way a real analyst
     * cross-checks momentum before committing to a direction, rather than
     * slope alone.
     */
    private fun computeIndicators(
        candles: List<Candle>,
        direction: Direction,
        strength: Double,
        volatility: Double,
        srNote: String,
    ): List<IndicatorReading> {
        val readings = mutableListOf<IndicatorReading>()
        val recent = candles.takeLast(min(candles.size, 14))

        // ---- RSI-style momentum (simplified) ----
        if (recent.size >= 5) {
            var gains = 0.0
            var losses = 0.0
            for (c in recent) {
                if (c.isBullish) gains += c.bodySize else losses += c.bodySize
            }
            val totalMove = (gains + losses).coerceAtLeast(1.0)
            val rsiLike = (gains / totalMove * 100)
            val rsiLabel = when {
                rsiLike > 70 -> "${rsiLike.toInt()} (Overbought)"
                rsiLike < 30 -> "${rsiLike.toInt()} (Oversold)"
                else -> "${rsiLike.toInt()} (Neutral)"
            }
            val rsiBias = when {
                rsiLike > 70 -> Direction.DOWN
                rsiLike < 30 -> Direction.UP
                rsiLike > 55 -> Direction.UP
                rsiLike < 45 -> Direction.DOWN
                else -> Direction.NEUTRAL
            }
            readings.add(IndicatorReading(
                nameEn = "Momentum (RSI-style)", nameUr = "مومینٹم (RSI طرز)",
                valueLabel = rsiLabel, bias = rsiBias, weight = 0.5,
            ))
        }

        // ---- Moving-average style bias ----
        if (candles.size >= 6) {
            val third = candles.size / 3
            val earlyAvg = candles.take(third).map { (it.top + it.bottom) / 2.0 }.average()
            val lateAvg = candles.takeLast(third).map { (it.top + it.bottom) / 2.0 }.average()
            val maBias = when {
                lateAvg < earlyAvg - 2 -> Direction.UP
                lateAvg > earlyAvg + 2 -> Direction.DOWN
                else -> Direction.NEUTRAL
            }
            val maLabel = when (maBias) {
                Direction.UP -> "Fast MA above Slow MA"
                Direction.DOWN -> "Fast MA below Slow MA"
                Direction.NEUTRAL -> "Flat / crossing"
            }
            readings.add(IndicatorReading(
                nameEn = "Moving Average Bias", nameUr = "موونگ ایوریج رجحان",
                valueLabel = maLabel, bias = maBias, weight = 0.45,
            ))
        }

        // ---- Volatility reading ----
        val volLabel = when {
            volatility > 0.7 -> "High"
            volatility > 0.35 -> "Moderate"
            else -> "Low"
        }
        readings.add(IndicatorReading(
            nameEn = "Volatility", nameUr = "اتار چڑھاؤ",
            valueLabel = volLabel, bias = Direction.NEUTRAL, weight = 0.15,
        ))

        // ---- Support/Resistance strength ----
        val srBias = when {
            srNote.contains("support") -> Direction.UP
            srNote.contains("resistance") -> Direction.DOWN
            else -> Direction.NEUTRAL
        }
        val srLabel = when {
            srNote.contains("support") -> "Near Support"
            srNote.contains("resistance") -> "Near Resistance"
            else -> "Mid-Range"
        }
        readings.add(IndicatorReading(
            nameEn = "Support/Resistance", nameUr = "سپورٹ/ریزسٹنس",
            valueLabel = srLabel, bias = srBias, weight = 0.35,
        ))

        // ---- Trend strength as its own indicator row ----
        val trendLabel = when {
            strength > 0.6 -> "Strong (${(strength * 100).toInt()}%)"
            strength > 0.3 -> "Moderate (${(strength * 100).toInt()}%)"
            else -> "Weak (${(strength * 100).toInt()}%)"
        }
        readings.add(IndicatorReading(
            nameEn = "Trend Strength", nameUr = "رجحان کی طاقت",
            valueLabel = trendLabel, bias = direction, weight = 0.5,
        ))

        return readings
    }

    /**
     * Very small, short-lived shapes that don't rise to the level of a
     * full named pattern (Doji/Hammer/etc.) but are still worth surfacing —
     * e.g. a tiny pin-bar wick on the last few candles, or a sudden
     * single-candle spike. Called out separately so the user can see the
     * bot reading candle-by-candle detail, not just the big shapes.
     */
    private fun detectMicroPatterns(candles: List<Candle>): List<String> {
        if (candles.size < 3) return emptyList()
        val notes = mutableListOf<String>()
        val last3 = candles.takeLast(3)
        val avgRange = candles.takeLast(min(candles.size, 12)).map { it.range }.average().coerceAtLeast(1.0)

        last3.forEachIndexed { idx, c ->
            val label = when (idx) { 2 -> "Last candle"; 1 -> "2nd-last candle"; else -> "3rd-last candle" }
            if (c.upperWick > c.bodySize * 3 && c.bodySize > 0) {
                notes.add("$label: tiny upper wick spike — brief rejection at the high")
            }
            if (c.lowerWick > c.bodySize * 3 && c.bodySize > 0) {
                notes.add("$label: tiny lower wick spike — brief rejection at the low")
            }
            if (c.range < avgRange * 0.25) {
                notes.add("$label: micro-range candle — very low activity")
            }
        }

        val smallBodyCount = last3.count { it.bodySize.toDouble() / it.range < 0.2 }
        if (smallBodyCount >= 2) {
            notes.add("Coiling detected — multiple small-bodied candles in a row, often precedes a bigger move")
        }

        return notes
    }

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
            // Track per-row color density along this column: the BODY of a
            // candle is where the colored column is at its widest/densest
            // (the wick is a thin 1-pixel-wide line, the body is a filled
            // block many pixels wide). We approximate this by re-scanning a
            // small neighborhood around x for each colored row and counting
            // how many of those neighboring columns are also colored at that
            // same y — a thin wick will have low neighbor-density, a thick
            // body will have high neighbor-density.
            val coloredRows = mutableListOf<Int>()

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
                    coloredRows.add(y)
                    if (isGreen) greenCount++ else redCount++
                }
                y += 2 // vertical sampling for speed
            }

            if (top != -1) {
                // Estimate body as the densest contiguous ~55% vertical
                // segment of colored rows (wicks thin out toward the ends).
                val bodyMargin = ((bottom - top) * 0.22).toInt()
                val bodyTop = (top + bodyMargin).coerceAtMost(bottom)
                val bodyBottom = (bottom - bodyMargin).coerceAtLeast(bodyTop)

                candles.add(
                    Candle(
                        xCenter = x,
                        top = top,
                        bottom = bottom,
                        isBullish = greenCount >= redCount,
                        bodyTop = bodyTop,
                        bodyBottom = bodyBottom,
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
