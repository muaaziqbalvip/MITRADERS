package com.mitv.trademaster.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Builds a full "signal bot" style report image from scratch: instead of
 * overlaying text on the user's original screenshot, this REDRAWS the
 * detected candles as a clean, simplified candlestick chart on a plain
 * background, then annotates it with everything the analyzer found —
 * pair name, chart timeframe, trade duration, next-candle direction arrow,
 * entry marker, support/resistance lines, matched patterns, and key
 * indicator readings. The result reads like a single self-contained
 * trading-signal card rather than a screenshot with a sticker on it.
 */
object AnnotatedChartExporter {

    /**
     * [rawCandles] are the same lightweight per-column candle samples the
     * analyzer used internally — passed through here so the redrawn chart
     * matches what was actually analyzed, not a separate re-detection.
     */
    fun export(
        context: Context,
        rawCandles: List<ExportCandle>,
        result: AnalysisResult,
    ): android.net.Uri? {
        return try {
            val w = 1280
            val h = 1600
            val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            val bg = Color.parseColor("#05080A")
            val panel = Color.parseColor("#0B1114")
            val line = Color.parseColor("#1C2A2F")
            val green = Color.parseColor("#34E39A")
            val red = Color.parseColor("#FF5C6A")
            val silver = Color.parseColor("#CDD6D8")
            val silverDim = Color.parseColor("#7C8B8F")

            canvas.drawColor(bg)

            // ---- Header band: pair name, timeframe, trade duration ----
            val headerH = 190f
            val headerPaint = Paint().apply { color = panel; isAntiAlias = true }
            canvas.drawRect(RectF(0f, 0f, w.toFloat(), headerH), headerPaint)
            canvas.drawLine(0f, headerH, w.toFloat(), headerH, Paint().apply { color = line; strokeWidth = 3f })

            val pairPaint = Paint().apply {
                color = silver; textSize = 56f; isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(result.detectedPairName ?: "Chart Analysis", 36f, 80f, pairPaint)

            val metaPaint = Paint().apply { color = silverDim; textSize = 30f; isAntiAlias = true }
            val tf = result.candleIntervalMinutes
            val td = result.tradeDurationMinutes
            val metaLine = buildString {
                if (tf != null) append("Chart timeframe: ${tf}m")
                if (tf != null && td != null) append("   •   ")
                if (td != null) append("Trade duration: ${td}m")
                if (tf == null && td == null) append("Educational chart analysis")
            }
            canvas.drawText(metaLine, 36f, 128f, metaPaint)

            val brandPaint = Paint().apply {
                color = green; textSize = 26f; isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("MI TRADE MASTER", w - 36f, 60f, brandPaint)
            val brandSubPaint = Paint().apply {
                color = silverDim; textSize = 22f; isAntiAlias = true; textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("AI Chart Analyzer", w - 36f, 90f, brandSubPaint)

            // Big direction badge, top-right of header.
            val (dirColor, dirLabel, arrow) = when (result.nextCandlePrediction) {
                Direction.UP -> Triple(green, "UP", "▲")
                Direction.DOWN -> Triple(red, "DOWN", "▼")
                Direction.NEUTRAL -> Triple(silverDim, "NEUTRAL", "◆")
            }
            val badgeRect = RectF(w - 260f, 108f, w - 36f, 172f)
            canvas.drawRoundRect(
                badgeRect, 14f, 14f,
                Paint().apply { color = Color.argb(40, Color.red(dirColor), Color.green(dirColor), Color.blue(dirColor)) }
            )
            val badgeTextPaint = Paint().apply {
                color = dirColor; textSize = 34f; isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$arrow $dirLabel ${result.nextCandleConfidencePercent}%", badgeRect.centerX(), badgeRect.centerY() + 12f, badgeTextPaint)

            // ---- Chart area: redrawn candles on plain background ----
            val chartTop = headerH + 30f
            val chartBottom = h - 560f
            val chartLeft = 70f
            val chartRight = w - 70f
            val chartH = chartBottom - chartTop
            val chartW = chartRight - chartLeft

            val gridPaint = Paint().apply { color = line; strokeWidth = 1.5f; isAntiAlias = true }
            for (i in 1..3) {
                val y = chartTop + chartH * i / 4
                canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            }

            if (rawCandles.isNotEmpty()) {
                val minVal = rawCandles.minOf { it.top }
                val maxVal = rawCandles.maxOf { it.bottom }
                val valRange = (maxVal - minVal).coerceAtLeast(1)

                fun mapY(v: Int): Float = chartBottom - ((v - minVal).toFloat() / valRange) * chartH

                val count = rawCandles.size
                val slotW = chartW / count
                val bodyW = (slotW * 0.55f).coerceAtLeast(2f)

                rawCandles.forEachIndexed { i, c ->
                    val cx = chartLeft + slotW * i + slotW / 2f
                    val color = if (c.isBullish) green else red
                    val wickPaint = Paint().apply { this.color = color; strokeWidth = 2f; isAntiAlias = true }
                    val bodyPaint = Paint().apply { this.color = color; isAntiAlias = true }

                    canvas.drawLine(cx, mapY(c.top), cx, mapY(c.bottom), wickPaint)
                    val bodyTop = mapY(max(c.bodyTop, c.top))
                    val bodyBottom = mapY(min(c.bodyBottom, c.bottom))
                    canvas.drawRect(
                        RectF(
                            cx - bodyW / 2,
                            min(bodyTop, bodyBottom),
                            cx + bodyW / 2,
                            max(bodyTop, bodyBottom).coerceAtLeast(min(bodyTop, bodyBottom) + 3f)
                        ),
                        bodyPaint
                    )
                }

                result.supportLevelPercent?.let { pct ->
                    val v = minVal + (valRange * (1 - pct / 100.0)).toInt()
                    val y = mapY(v).coerceIn(chartTop, chartBottom)
                    val srPaint = Paint().apply {
                        color = green; strokeWidth = 2.5f
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
                    }
                    canvas.drawLine(chartLeft, y, chartRight, y, srPaint)
                    canvas.drawText("Support", chartLeft + 8f, y - 10f, Paint().apply { color = green; textSize = 24f; isAntiAlias = true })
                }
                result.resistanceLevelPercent?.let { pct ->
                    val v = minVal + (valRange * (1 - pct / 100.0)).toInt()
                    val y = mapY(v).coerceIn(chartTop, chartBottom)
                    val srPaint = Paint().apply {
                        color = red; strokeWidth = 2.5f
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
                    }
                    canvas.drawLine(chartLeft, y, chartRight, y, srPaint)
                    canvas.drawText("Resistance", chartLeft + 8f, y - 10f, Paint().apply { color = red; textSize = 24f; isAntiAlias = true })
                }

                val lastCandle = rawCandles.last()
                val lastX = chartLeft + slotW * (count - 1) + slotW / 2f
                val entryY = mapY((lastCandle.top + lastCandle.bottom) / 2)

                canvas.drawCircle(lastX, entryY, 8f, Paint().apply { color = silver; isAntiAlias = true })
                canvas.drawCircle(
                    lastX, entryY, 14f,
                    Paint().apply { color = silver; style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true }
                )

                val arrowLen = 90f
                val arrowEndY = if (result.nextCandlePrediction == Direction.UP) entryY - arrowLen else entryY + arrowLen
                val arrowX = (lastX + 70f).coerceAtMost(chartRight - 20f)
                if (result.nextCandlePrediction != Direction.NEUTRAL) {
                    val arrowPaint = Paint().apply { color = dirColor; strokeWidth = 5f; isAntiAlias = true; style = Paint.Style.STROKE }
                    canvas.drawLine(lastX, entryY, arrowX, arrowEndY, arrowPaint)
                    val headSize = 16f
                    val dirSign = if (result.nextCandlePrediction == Direction.UP) -1 else 1
                    val path = Path().apply {
                        moveTo(arrowX, arrowEndY)
                        lineTo(arrowX - headSize, arrowEndY - dirSign * headSize)
                        lineTo(arrowX + headSize, arrowEndY - dirSign * headSize)
                        close()
                    }
                    canvas.drawPath(path, Paint().apply { color = dirColor; isAntiAlias = true })
                }
            } else {
                canvas.drawText(
                    "No candle data to redraw", w / 2f, (chartTop + chartBottom) / 2,
                    Paint().apply { color = silverDim; textSize = 30f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
                )
            }

            // ---- Bottom info panel: patterns, indicators, micro-signals ----
            val panelTop = chartBottom + 20f
            canvas.drawRect(RectF(0f, panelTop, w.toFloat(), h.toFloat()), Paint().apply { color = panel; isAntiAlias = true })
            canvas.drawLine(0f, panelTop, w.toFloat(), panelTop, Paint().apply { color = dirColor; strokeWidth = 4f })

            var cursorY = panelTop + 50f
            val sectionTitlePaint = Paint().apply {
                color = silverDim; textSize = 26f; isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.06f
            }
            val bodyTextPaint = Paint().apply { color = silver; textSize = 27f; isAntiAlias = true }
            val bulletGreenPaint = Paint().apply { color = green; textSize = 27f; isAntiAlias = true }
            val bulletRedPaint = Paint().apply { color = red; textSize = 27f; isAntiAlias = true }

            canvas.drawText("PATTERNS DETECTED", 36f, cursorY, sectionTitlePaint)
            cursorY += 42f
            if (result.detectedPatterns.isEmpty()) {
                canvas.drawText("No named pattern matched — reading from trend/indicators only", 36f, cursorY, bodyTextPaint)
                cursorY += 40f
            } else {
                result.detectedPatterns.take(4).forEach { p ->
                    val paint = when (p.nextCandleBias) {
                        Direction.DOWN -> bulletRedPaint
                        Direction.UP -> bulletGreenPaint
                        Direction.NEUTRAL -> bodyTextPaint
                    }
                    canvas.drawText("• ${p.nameEn} (${p.nextCandleBias.name.lowercase()} bias)", 36f, cursorY, paint)
                    cursorY += 40f
                }
            }

            cursorY += 12f
            canvas.drawText("INDICATORS", 36f, cursorY, sectionTitlePaint)
            cursorY += 42f
            result.indicators.take(5).forEach { ind ->
                val paint = when (ind.bias) {
                    Direction.DOWN -> bulletRedPaint
                    Direction.UP -> bulletGreenPaint
                    Direction.NEUTRAL -> bodyTextPaint
                }
                canvas.drawText("• ${ind.nameEn}: ${ind.valueLabel}", 36f, cursorY, paint)
                cursorY += 40f
            }

            if (result.microPatterns.isNotEmpty()) {
                cursorY += 12f
                canvas.drawText("MICRO-SIGNALS", 36f, cursorY, sectionTitlePaint)
                cursorY += 42f
                result.microPatterns.take(3).forEach { m ->
                    canvas.drawText("• $m", 36f, cursorY, bodyTextPaint)
                    cursorY += 40f
                }
            }

            canvas.drawText(
                "Educational pattern observation — not a guaranteed outcome. Manage your own risk.",
                36f, h - 24f,
                Paint().apply { color = Color.parseColor("#4D7C8B8F"); textSize = 20f; isAntiAlias = true }
            )

            val dir = File(context.getExternalFilesDir("Download"), "").apply { mkdirs() }
            val file = File(dir, "MI_TradeMaster_Analysis_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> output.compress(Bitmap.CompressFormat.PNG, 100, out) }

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}

/** Lightweight candle-geometry snapshot passed from the analyzer to the exporter for redrawing. */
data class ExportCandle(
    val top: Int,
    val bottom: Int,
    val bodyTop: Int,
    val bodyBottom: Int,
    val isBullish: Boolean,
)
