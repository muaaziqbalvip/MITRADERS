package com.mitv.trademaster.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.mitv.trademaster.R
import java.io.File
import java.io.FileOutputStream

/**
 * Takes the user's ORIGINAL chart screenshot (already auto-cropped to just
 * the candlestick chart area by [ChartRegionDetector]) and overlays the
 * analyzer's findings directly on top of it — an entry marker + direction
 * arrow at the real detected candle position, support/resistance lines at
 * their real detected height, an MI TRADE MASTER logo + brand header, and a
 * long-form footer report (patterns, indicators, micro-signals) with full
 * detail rather than a one-line summary. Nothing is redrawn from scratch:
 * the chart in the exported image is the user's own screenshot, just
 * annotated — this reads as "their chart with our call marked on it"
 * rather than a separate, unfamiliar-looking synthetic chart.
 */
object AnnotatedChartExporter {

    /**
     * Builds the full annotated report bitmap in memory, without touching
     * disk — used both for the on-screen preview (shown right after
     * analysis) and as the first step of [export] (which then saves +
     * shares it). Building this only once and reusing it for both preview
     * and download avoids re-rendering the same image twice.
     */
    fun buildAnnotatedBitmap(context: Context, sourceBitmap: Bitmap, result: AnalysisResult): Bitmap {
        val srcW = sourceBitmap.width
        val srcH = sourceBitmap.height

        val headerH = 210
        // Footer height grows with how much detail there is to show, so
        // long pattern/indicator/micro-signal lists never get clipped —
        // this is the "long detail, everything shown" report footer.
        val patternLines = result.detectedPatterns.size.coerceAtLeast(1)
        val indicatorLines = result.indicators.size
        val microLines = result.microPatterns.size
        val totalLines = patternLines * 2 + indicatorLines + microLines
        val footerH = (420 + totalLines * 46).coerceIn(420, 1600)

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

        // ---- Header band: MI Trade Master logo + pair name + timeframe/duration + direction badge ----
        canvas.drawRect(RectF(0f, 0f, outW.toFloat(), headerH.toFloat()), Paint().apply { color = panel; isAntiAlias = true })

        val logoSize = (outW * 0.11f).coerceIn(56f, 96f)
        val logoMargin = outW * 0.03f
        val logoBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull()
        var textStartX = logoMargin
        if (logoBitmap != null) {
            val logoRect = RectF(logoMargin, headerH * 0.15f, logoMargin + logoSize, headerH * 0.15f + logoSize)
            canvas.drawBitmap(logoBitmap, null, logoRect, Paint().apply { isAntiAlias = true })
            textStartX = logoRect.right + outW * 0.025f
        }

        val titleSize = (outW * 0.038f).coerceIn(28f, 44f)
        canvas.drawText(
            "MI TRADE MASTER", textStartX, headerH * 0.32f,
            Paint().apply {
                color = green; textSize = titleSize; isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )

        val pairSize = (outW * 0.04f).coerceIn(26f, 40f)
        canvas.drawText(
            result.detectedPairName ?: "Chart Analysis", textStartX, headerH * 0.58f,
            Paint().apply {
                color = silver; textSize = pairSize; isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )

        val metaSize = (outW * 0.022f).coerceIn(18f, 26f)
        val tf = result.candleIntervalMinutes
        val td = result.tradeDurationMinutes
        val metaLine = buildString {
            if (tf != null) append("Chart timeframe: ${tf}m")
            if (tf != null && td != null) append("   •   ")
            if (td != null) append("Trade duration: ${td}m")
            if (tf == null && td == null) append("Educational chart analysis")
        }
        canvas.drawText(
            metaLine, textStartX, headerH * 0.8f,
            Paint().apply { color = silverDim; textSize = metaSize; isAntiAlias = true }
        )

        val (dirColor, dirLabel, arrow) = when (result.nextCandlePrediction) {
            Direction.UP -> Triple(green, "UP", "▲")
            Direction.DOWN -> Triple(red, "DOWN", "▼")
            Direction.NEUTRAL -> Triple(silverDim, "NEUTRAL", "◆")
        }
        val badgeW = outW * 0.33f
        val badgeRect = RectF(outW - badgeW - outW * 0.03f, headerH * 0.28f, outW - outW * 0.03f, headerH * 0.86f)
        canvas.drawRoundRect(
            badgeRect, 14f, 14f,
            Paint().apply { color = Color.argb(45, Color.red(dirColor), Color.green(dirColor), Color.blue(dirColor)) }
        )
        val badgeTextSize = (outW * 0.03f).coerceIn(22f, 34f)
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

        // ---- The user's ORIGINAL (auto-cropped) chart, untouched, drawn between header and footer ----
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
                val arrowPaint = Paint().apply {
                    color = dirColor; strokeWidth = 6f; isAntiAlias = true
                    style = Paint.Style.STROKE; setShadowLayer(5f, 0f, 0f, Color.BLACK)
                }
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

        // ---- Footer info panel: FULL long-form report — every pattern, every indicator, every micro-signal ----
        val panelTop = (headerH + srcH).toFloat()
        canvas.drawRect(RectF(0f, panelTop, outW.toFloat(), outH.toFloat()), Paint().apply { color = panel; isAntiAlias = true })
        canvas.drawLine(0f, panelTop, outW.toFloat(), panelTop, Paint().apply { color = dirColor; strokeWidth = 4f })

        val sectionSize = (outW * 0.021f).coerceIn(18f, 26f)
        val bodySize = (outW * 0.02f).coerceIn(17f, 24f)
        var cursorY = panelTop + 50f
        val sectionTitlePaint = Paint().apply {
            color = silverDim; textSize = sectionSize; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.06f
        }
        val bodyTextPaint = Paint().apply { color = silver; textSize = bodySize; isAntiAlias = true }
        val descTextPaint = Paint().apply { color = silverDim; textSize = bodySize * 0.88f; isAntiAlias = true }
        val bulletGreenPaint = Paint().apply { color = green; textSize = bodySize; isAntiAlias = true; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val bulletRedPaint = Paint().apply { color = red; textSize = bodySize; isAntiAlias = true; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val lineStep = bodySize + 12f

        canvas.drawText("PATTERNS DETECTED — FULL DETAIL", outW * 0.03f, cursorY, sectionTitlePaint)
        cursorY += lineStep + 8f
        if (result.detectedPatterns.isEmpty()) {
            canvas.drawText("No named pattern matched — reading from trend/indicators only", outW * 0.03f, cursorY, bodyTextPaint)
            cursorY += lineStep
        } else {
            result.detectedPatterns.forEach { p ->
                val paint = when (p.nextCandleBias) {
                    Direction.DOWN -> bulletRedPaint
                    Direction.UP -> bulletGreenPaint
                    Direction.NEUTRAL -> bodyTextPaint
                }
                canvas.drawText(
                    "● ${p.nameEn}  —  ${p.nextCandleBias.name} bias  (${(p.reliability * 100).toInt()}% reliability)",
                    outW * 0.03f, cursorY, paint
                )
                cursorY += lineStep
                canvas.drawText(wrapText(p.descriptionEn, 78), outW * 0.045f, cursorY, descTextPaint)
                cursorY += lineStep
            }
        }

        cursorY += 16f
        canvas.drawText("INDICATORS — FULL READOUT", outW * 0.03f, cursorY, sectionTitlePaint)
        cursorY += lineStep + 8f
        result.indicators.forEach { ind ->
            val paint = when (ind.bias) {
                Direction.DOWN -> bulletRedPaint
                Direction.UP -> bulletGreenPaint
                Direction.NEUTRAL -> bodyTextPaint
            }
            canvas.drawText("● ${ind.nameEn}: ${ind.valueLabel}", outW * 0.03f, cursorY, paint)
            cursorY += lineStep
        }

        if (result.microPatterns.isNotEmpty()) {
            cursorY += 16f
            canvas.drawText("MICRO-SIGNALS — WICKS & SMALL PATTERNS", outW * 0.03f, cursorY, sectionTitlePaint)
            cursorY += lineStep + 8f
            result.microPatterns.forEach { m ->
                canvas.drawText("● $m", outW * 0.03f, cursorY, bodyTextPaint)
                cursorY += lineStep
            }
        }

        cursorY += 20f
        canvas.drawText(
            "Educational pattern observation — not a guaranteed outcome. Manage your own risk.",
            outW * 0.03f, (outH - 22f).coerceAtLeast(cursorY),
            Paint().apply { color = Color.parseColor("#4D7C8B8F"); textSize = (outW * 0.016f).coerceIn(14f, 20f); isAntiAlias = true }
        )

        return output
    }

    /** Builds the report bitmap and saves it to the app's shareable downloads folder, returning a content:// Uri. */
    fun export(context: Context, sourceBitmap: Bitmap, result: AnalysisResult): android.net.Uri? {
        return try {
            val output = buildAnnotatedBitmap(context, sourceBitmap, result)
            saveAndShare(context, output)
        } catch (e: Exception) {
            null
        }
    }

    /** Saves an already-built annotated bitmap (e.g. one already shown as an on-screen preview) without re-rendering it. */
    fun saveAndShare(context: Context, bitmap: Bitmap): android.net.Uri? {
        return try {
            val dir = File(context.getExternalFilesDir("Download"), "").apply { mkdirs() }
            val file = File(dir, "MI_TradeMaster_Analysis_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    /** Truncates overly long descriptions to a single readable line instead of running off the edge of the canvas. */
    private fun wrapText(text: String, maxCharsPerLine: Int): String {
        return if (text.length <= maxCharsPerLine) text else text.take(maxCharsPerLine - 1) + "…"
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
