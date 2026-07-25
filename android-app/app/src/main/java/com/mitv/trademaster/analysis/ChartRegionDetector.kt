package com.mitv.trademaster.analysis

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Automatically finds the vertical (and horizontal) band of a trading-app
 * screenshot that actually contains the candlestick chart, so everything
 * else — BUY/SELL buttons, amount/payout panels, timeframe tabs, bottom
 * navigation bars, timers — that apps like Quotex/IQ Option/Olymp Trade
 * render around the chart gets excluded automatically, both from analysis
 * and from the exported "clean chart" image.
 *
 * Approach: scan every row for candle-colored pixels (the same green/red
 * detection ChartAnalyzer already uses). Trading-app UI chrome is almost
 * always solid-color panels or muted buttons — it has very low green/red
 * pixel density per row. The actual chart region is a tall, dense,
 * mostly-contiguous band of green/red pixels. We find that band's top and
 * bottom edge, with a small safety margin, and similarly narrow the left/
 * right edges to strip side toolbars if present.
 */
object ChartRegionDetector {

    /**
     * Returns the detected chart rectangle within [bitmap], or a
     * full-bitmap rect if no confident chart band could be found (so
     * callers can always safely crop with the result).
     */
    fun detectChartRegion(bitmap: Bitmap): Rect {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 20 || h < 20) return Rect(0, 0, w, h)

        val rowStep = max(1, h / 300)
        val colStep = max(1, w / 300)

        // ---- Row density scan (find vertical chart band) ----
        val rowDensity = IntArray(h)
        var y = 0
        while (y < h) {
            var count = 0
            var x = 0
            while (x < w) {
                if (isCandleColored(bitmap.getPixel(x, y))) count++
                x += colStep
            }
            rowDensity[y] = count
            y += rowStep
        }

        val maxRowDensity = rowDensity.maxOrNull()?.coerceAtLeast(1) ?: 1
        // A row is "part of the chart" if it has at least 8% of the peak
        // density seen anywhere — this is generous enough to include thin
        // upper wicks / sparse candle rows near the top of the chart, but
        // strict enough to exclude button/toolbar rows which are typically
        // near-zero candle-colored pixels.
        val rowThreshold = (maxRowDensity * 0.08).toInt().coerceAtLeast(1)

        var chartTop = -1
        var chartBottom = -1
        y = 0
        while (y < h) {
            if (rowDensity[y] >= rowThreshold) {
                if (chartTop == -1) chartTop = y
                chartBottom = y
            }
            y += rowStep
        }

        if (chartTop == -1) {
            // No confident candle band found — fall back to the full image
            // rather than guessing, so we never crop out real content.
            return Rect(0, 0, w, h)
        }

        // Small safety margins so we don't clip the tips of tall wicks
        // right at the detected edge.
        val vMargin = ((chartBottom - chartTop) * 0.03).toInt().coerceAtLeast(4)
        chartTop = (chartTop - vMargin).coerceAtLeast(0)
        chartBottom = (chartBottom + vMargin).coerceAtMost(h - 1)

        // ---- Column density scan (find horizontal chart band, e.g. strip side toolbars) ----
        val colDensity = IntArray(w)
        var x = 0
        while (x < w) {
            var count = 0
            var yy = chartTop
            while (yy <= chartBottom) {
                if (isCandleColored(bitmap.getPixel(x, yy))) count++
                yy += rowStep
            }
            colDensity[x] = count
            x += colStep
        }
        val maxColDensity = colDensity.maxOrNull()?.coerceAtLeast(1) ?: 1
        val colThreshold = (maxColDensity * 0.04).toInt().coerceAtLeast(1)

        var chartLeft = -1
        var chartRight = -1
        x = 0
        while (x < w) {
            if (colDensity[x] >= colThreshold) {
                if (chartLeft == -1) chartLeft = x
                chartRight = x
            }
            x += colStep
        }
        if (chartLeft == -1) { chartLeft = 0; chartRight = w - 1 }

        val hMargin = ((chartRight - chartLeft) * 0.01).toInt().coerceAtLeast(2)
        chartLeft = (chartLeft - hMargin).coerceAtLeast(0)
        chartRight = (chartRight + hMargin).coerceAtMost(w - 1)

        // Sanity floor: never return a sliver — if detection collapsed to
        // something implausibly small, prefer the full image.
        val detectedH = chartBottom - chartTop
        val detectedW = chartRight - chartLeft
        if (detectedH < h * 0.25 || detectedW < w * 0.5) {
            return Rect(0, 0, w, h)
        }

        return Rect(chartLeft, chartTop, chartRight, chartBottom)
    }

    /** Crops [bitmap] to the auto-detected chart region. Safe no-op-equivalent if detection falls back to full image. */
    fun cropToChartRegion(bitmap: Bitmap): Bitmap {
        val rect = detectChartRegion(bitmap)
        val w = (rect.right - rect.left).coerceAtLeast(1)
        val h = (rect.bottom - rect.top).coerceAtLeast(1)
        return try {
            Bitmap.createBitmap(bitmap, rect.left, rect.top, min(w, bitmap.width - rect.left), min(h, bitmap.height - rect.top))
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun isCandleColored(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val isGreen = g > r + 15 && g > b + 15 && g > 60
        val isRed = r > g + 15 && r > b + 15 && r > 60
        return isGreen || isRed
    }
}
