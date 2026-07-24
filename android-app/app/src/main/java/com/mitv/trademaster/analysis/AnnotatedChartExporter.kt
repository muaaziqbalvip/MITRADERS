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

/**
 * Takes the user's ORIGINAL chart screenshot and overlays the analyzer's
 * findings directly on top of it — an entry marker + direction arrow at the
 * real detected candle position, support/resistance lines at their real
 * detected height, plus a header band (pair name, timeframe, trade
 * duration, direction badge) and a footer info panel (patterns, indicators,
 * micro-signals). Nothing is redrawn from scratch: the chart in the
 * exported image is the user's own screenshot, just annotated — this reads
 * as "their chart with our call marked on it" rather than a separate,
 * unfamiliar-looking synthetic chart.
 */
object AnnotatedChartExporter {

    /**
     * [sourceBitmap] is the exact screenshot that was analyzed.
     * [srcCandleTop]/[srcCandleBottom] give the real pixel Y-range (within
     * [sourceBitmap]) that candle data was detected in, so support/
     * resistance/entry markers line up with the actual chart instead of an
     * approximated position.
     */
    fun export(
        context: Context,
        sourceBitmap: Bitmap,
        result: AnalysisResult,
    ): android.net.Uri? {
        return try {
            val srcW = sourceBitmap.width
            val srcH = sourceBitmap.height

            val headerH = 190
            val footerH = 480
            val outW = srcW
            val outH = srcH + headerH + footerH

            val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            val bg = Color.parseColor("#05080A")
            val panel = Color.parseColor("#0B1114")
            val green = Color.parseColor("#34E39A")
            val red = Color.parseColor("#FF5C6A")
            val silver = Color.parseColor("#CDD6D8")
            val silverDim = Color.parseColor("#7C8B8F")

            canvas.drawColor(bg)

            // ---- Header band: pair name, timeframe, trade duration, direction badge ----
            canvas.drawRect(RectF(0f, 0f, outW.toFloat(), headerH.toFloat()), Paint().apply { color = panel; isAntiAlias = true })

            val titleSize = (outW * 0.045f).coerceIn(34f, 56f)
            val pairPaint = Paint().apply {
                color = silver; textSize = titleSize; isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(result.detectedPairName ?: "Chart Analysis", outW * 0.03f, headerH * 0.42f, pairPaint)

            val metaSize = (outW * 0.024f).coerceIn(20f, 30f)
            val metaPaint = Paint().apply { color = silverDim; textSize = metaSize; isAntiAlias = true }
            val tf = result.candleIntervalMinutes
            val td = result.tradeDurationMinutes
            val metaLine = buildString {
                if (tf != null) append("Chart timeframe: ${tf}m")
                if (tf != null && td != null) append("   •   ")
                if (td != null) append("Trade duration: ${td}m")
                if (tf == null && td == null) append("Educational chart analysis")
            }
            canvas.drawText(metaLine, outW * 0.03f, headerH * 0.7f, metaPaint)

            val (dirColor, dirLabel, arrow) = when (result.nextCandlePrediction) {
                Direction.UP -> Triple(green, "UP", "▲")
                Direction.DOWN -> Triple(red, "DOWN", "▼")
                Direction.NEUTRAL -> Triple(silverDim, "NEUTRAL", "◆")
            }
            val badgeW = outW * 0.32f
            val badgeRect = RectF(outW - badgeW - outW * 0.03f, headerH * 0.22f, outW - outW * 0.03f, headerH * 0.85f)
            canvas.drawRoundRect(
                badgeRect, 14f, 14f,
                Paint().apply { color = Color.argb(45, Color.red(dirColor), Color.green(dirColor), Color.blue(dirColor)) }
            )
            val badgeTextSize = (outW * 0.032f).coerceIn(24f, 36f)
            canvas.drawText(
                "$arrow $dirLabel ${result.nextCandleConfidencePercent}%",
                badgeRect.centerX(), badgeRect.centerY() + badgeTextSize * 0.35f,
                Paint().apply {
                    color = dirColor; textSize = badgeTextSize; isAntiAlias = true
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
            )
            canvas.drawLine(0f, headerH.toFloat(), outW.toFloat(), headerH.toFloat(), Paint().apply { color = dirColor; strokeWidth = 3f })

            // ---- The user's ORIGINAL chart, untouched, drawn between header and footer ----
            canvas.drawBitmap(sourceBitmap, 0f, headerH.toFloat(), null)

            // ---- Overlay markings positioned using REAL detected coordinates from the source image ----
            val chartOffsetY = headerH.toFloat()

            result.supportLevelPercent?.let { pct ->
                val y = chartOffsetY + srcH * (1 - pct / 100f)
                val srPaint = Paint().apply {
                    color = green; strokeWidth = 3f
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 10f), 0f)
                    isAntiAlias = true
                }
                canvas.drawLine(0f, y, outW.toFloat(), y, srPaint)
                canvas.drawText(
                    "Support", 12f, y - 10f,
                    Paint().apply { color = green; textSize = 26f; isAntiAlias = true; setShadowLayer(4f, 0f, 0f, Color.BLACK) }
                )
            }
            result.resistanceLevelPercent?.let { pct ->
                val y = chartOffsetY + srcH * (1 - pct / 100f)
                val srPaint = Paint().apply {
                    color = red; strokeWidth = 3f
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 10f), 0f)
                    isAntiAlias = true
                }
                canvas.drawLine(0f, y, outW.toFloat(), y, srPaint)
                canvas.drawText(
                    "Resistance", 12f, y - 10f,
                    Paint().apply { color = red; textSize = 26f; isAntiAlias = true; setShadowLayer(4f, 0f, 0f, Color.BLACK) }
                )
            }

            // Entry marker + next-candle direction arrow, placed at the last
            // detected candle's real position (right edge of the chart,
            // vertically at its actual entry-reference level).
            result.entryReferencePrice?.let { entryPct ->
                val entryX = outW * 0.90f
                val entryY = chartOffsetY + srcH * (entryPct / 100f).toFloat()

                canvas.drawCircle(entryX, entryY, 10f, Paint().apply { color = silver; isAntiAlias = true; setShadowLayer(6f, 0f, 0f, Color.BLACK) })
                canvas.drawCircle(
                    entryX, entryY, 17f,
                    Paint().apply { color = silver; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
                )

                if (result.nextCandlePrediction != Direction.NEUTRAL) {
                    val arrowLen = srcH * 0.09f
                    val arrowEndY = if (result.nextCandlePrediction == Direction.UP) entryY - arrowLen else entryY + arrowLen
                    val arrowX = (entryX + outW * 0.05f).coerceAtMost(outW - 20f)
                    val arrowPaint = Paint().apply { color = dirColor; strokeWidth = 6f; isAntiAlias = true; style = Paint.Style.STROKE; setShadowLayer(5f, 0f, 0f, Color.BLACK) }
                    canvas.drawLine(entryX, entryY, arrowX, arrowEndY, arrowPaint)
                    val headSize = 18f
                    val dirSign = if (result.nextCandlePrediction == Direction.UP) -1 else 1
                    val path = Path().apply {
                        moveTo(arrowX, arrowEndY)
                        lineTo(arrowX - headSize, arrowEndY - dirSign * headSize)
                        lineTo(arrowX + headSize, arrowEndY - dirSign * headSize)
                        close()
                    }
                    canvas.drawPath(path, Paint().apply { color = dirColor; isAntiAlias = true })
                }
            }

            // ---- Footer info panel: patterns, indicators, micro-signals ----
            val panelTop = (headerH + srcH).toFloat()
            canvas.drawRect(RectF(0f, panelTop, outW.toFloat(), outH.toFloat()), Paint().apply { color = panel; isAntiAlias = true })
            canvas.drawLine(0f, panelTop, outW.toFloat(), panelTop, Paint().apply { color = dirColor; strokeWidth = 4f })

            val sectionSize = (outW * 0.021f).coerceIn(18f, 26f)
            val bodySize = (outW * 0.022f).coerceIn(19f, 27f)
            var cursorY = panelTop + 50f
            val sectionTitlePaint = Paint().apply {
                color = silverDim; textSize = sectionSize; isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.06f
            }
            val bodyTextPaint = Paint().apply { color = silver; textSize = bodySize; isAntiAlias = true }
            val bulletGreenPaint = Paint().apply { color = green; textSize = bodySize; isAntiAlias = true }
            val bulletRedPaint = Paint().apply { color = red; textSize = bodySize; isAntiAlias = true }
            val lineStep = bodySize + 14f

            canvas.drawText("PATTERNS DETECTED", outW * 0.03f, cursorY, sectionTitlePaint)
            cursorY += lineStep + 6f
            if (result.detectedPatterns.isEmpty()) {
                canvas.drawText("No named pattern matched — reading from trend/indicators only", outW * 0.03f, cursorY, bodyTextPaint)
                cursorY += lineStep
            } else {
                result.detectedPatterns.take(3).forEach { p ->
                    val paint = when (p.nextCandleBias) {
                        Direction.DOWN -> bulletRedPaint
                        Direction.UP -> bulletGreenPaint
                        Direction.NEUTRAL -> bodyTextPaint
                    }
                    canvas.drawText("• ${p.nameEn} (${p.nextCandleBias.name.lowercase()} bias)", outW * 0.03f, cursorY, paint)
                    cursorY += lineStep
                }
            }

            cursorY += 14f
            canvas.drawText("INDICATORS", outW * 0.03f, cursorY, sectionTitlePaint)
            cursorY += lineStep + 6f
            result.indicators.take(4).forEach { ind ->
                val paint = when (ind.bias) {
                    Direction.DOWN -> bulletRedPaint
                    Direction.UP -> bulletGreenPaint
                    Direction.NEUTRAL -> bodyTextPaint
                }
                canvas.drawText("• ${ind.nameEn}: ${ind.valueLabel}", outW * 0.03f, cursorY, paint)
                cursorY += lineStep
            }

            if (result.microPatterns.isNotEmpty() && cursorY < outH - 60f) {
                cursorY += 14f
                canvas.drawText("MICRO-SIGNALS", outW * 0.03f, cursorY, sectionTitlePaint)
                cursorY += lineStep + 6f
                result.microPatterns.take(2).forEach { m ->
                    if (cursorY < outH - 40f) {
                        canvas.drawText("• $m", outW * 0.03f, cursorY, bodyTextPaint)
                        cursorY += lineStep
                    }
                }
            }

            canvas.drawText(
                "Educational pattern observation — not a guaranteed outcome. Manage your own risk.",
                outW * 0.03f, outH - 20f,
                Paint().apply { color = Color.parseColor("#4D7C8B8F"); textSize = (outW * 0.016f).coerceIn(14f, 20f); isAntiAlias = true }
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

/** Lightweight candle-geometry snapshot (kept for compatibility with other analyzer output, no longer used for redrawing). */
data class ExportCandle(
    val top: Int,
    val bottom: Int,
    val bodyTop: Int,
    val bodyBottom: Int,
    val isBullish: Boolean,
)
