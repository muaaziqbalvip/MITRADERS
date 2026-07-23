package com.mitv.trademaster.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Takes the ORIGINAL chart screenshot the user analyzed and draws a clean
 * overlay panel on top of it: next-candle prediction (UP/DOWN), confidence
 * percentage, and the top matched pattern — then saves the composited image
 * to the app's shareable downloads folder so it can be viewed, shared, or
 * kept as a record of "what the analyzer called, on this exact chart".
 *
 * This does NOT re-draw a synthetic chart — it overlays on top of the real
 * uploaded/captured image, so what the user downloads is recognizably
 * "their chart, plus our call" rather than a generic graphic.
 */
object AnnotatedChartExporter {

    /**
     * Draws the overlay panel onto [source] and writes the result to
     * `Android/data/<package>/files/Download/` (shareable via FileProvider,
     * matching the `downloads` entry already declared in file_paths.xml).
     * Returns the shareable content:// Uri, or null on failure.
     */
    fun export(context: Context, source: Bitmap, result: AnalysisResult): android.net.Uri? {
        return try {
            val output = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(output)
            val w = output.width
            val h = output.height

            val (accentColor, dirLabel, arrow) = when (result.nextCandlePrediction) {
                Direction.UP -> Triple(Color.parseColor("#34E39A"), "UP", "▲")
                Direction.DOWN -> Triple(Color.parseColor("#FF5C6A"), "DOWN", "▼")
                Direction.NEUTRAL -> Triple(Color.parseColor("#CDD6D8"), "NEUTRAL", "◆")
            }

            // ---- Semi-transparent panel background (bottom banner) ----
            val panelHeight = (h * 0.22f).coerceAtLeast(140f)
            val panelTop = h - panelHeight
            val panelPaint = Paint().apply {
                color = Color.parseColor("#CC05080A") // BgBlack at ~80% opacity
                isAntiAlias = true
            }
            canvas.drawRect(RectF(0f, panelTop, w.toFloat(), h.toFloat()), panelPaint)

            // Accent top-border on the panel
            val borderPaint = Paint().apply { color = accentColor; strokeWidth = h * 0.006f }
            canvas.drawLine(0f, panelTop, w.toFloat(), panelTop, borderPaint)

            // ---- "NEXT CANDLE" label ----
            val labelPaint = Paint().apply {
                color = Color.parseColor("#7C8B8F")
                textSize = h * 0.028f
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.08f
            }
            canvas.drawText("NEXT CANDLE PREDICTION", w * 0.05f, panelTop + h * 0.05f, labelPaint)

            // ---- Direction + arrow (big) ----
            val dirPaint = Paint().apply {
                color = accentColor
                textSize = h * 0.075f
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("$arrow $dirLabel", w * 0.05f, panelTop + h * 0.12f, dirPaint)

            // ---- Confidence percentage (right-aligned) ----
            val confPaint = Paint().apply {
                color = Color.WHITE
                textSize = h * 0.06f
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("${result.nextCandleConfidencePercent}%", w * 0.95f, panelTop + h * 0.10f, confPaint)
            val confLabelPaint = Paint().apply {
                color = Color.parseColor("#7C8B8F")
                textSize = h * 0.022f
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("CONFIDENCE", w * 0.95f, panelTop + h * 0.135f, confLabelPaint)

            // ---- Top matched pattern name, if any ----
            val patternName = result.detectedPatterns.maxByOrNull { it.reliability }?.nameEn
            if (patternName != null) {
                val patternPaint = Paint().apply {
                    color = Color.parseColor("#CDD6D8")
                    textSize = h * 0.026f
                    isAntiAlias = true
                }
                canvas.drawText("Pattern: $patternName", w * 0.05f, panelTop + h * 0.16f, patternPaint)
            }

            // ---- App watermark, bottom-right corner ----
            val watermarkPaint = Paint().apply {
                color = Color.parseColor("#4D7C8B8F")
                textSize = h * 0.018f
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("MI TRADE MASTER · Educational Analysis Only", w * 0.95f, h - h * 0.015f, watermarkPaint)

            // ---- Save to shareable downloads dir ----
            val dir = File(context.getExternalFilesDir("Download"), "").apply { mkdirs() }
            val file = File(dir, "MI_TradeMaster_Analysis_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> output.compress(Bitmap.CompressFormat.PNG, 100, out) }

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}
