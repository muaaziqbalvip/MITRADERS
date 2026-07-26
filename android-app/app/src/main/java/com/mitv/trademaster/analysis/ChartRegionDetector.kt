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

        val rowStep = max(1, h / 400)
        val colStep = max(1, w / 400)

        // ---- Row density scan (find vertical chart band) ----
        val sampledYs = mutableListOf<Int>()
        val rowDensity = mutableListOf<Int>()
        var y = 0
        while (y < h) {
            var count = 0
            var x = 0
            while (x < w) {
                if (isCandleColored(bitmap.getPixel(x, y))) count++
                x += colStep
            }
            sampledYs.add(y)
            rowDensity.add(count)
            y += rowStep
        }

        val maxRowDensity = rowDensity.maxOrNull()?.coerceAtLeast(1) ?: 1
        // A row only counts as "chart" if it has a real amount of candle
        // color — 22% of peak density. This is strict enough that a stray
        // green logo/badge/button (a handful of colored pixels) in the
        // header or footer can't drag the detected region out to cover
        // them; a genuine candlestick row is densely packed with color
        // across many columns, not a sparse handful of pixels.
        val rowThreshold = (maxRowDensity * 0.22).toInt().coerceAtLeast(2)

        // Find every contiguous run of rows that clears the threshold, then
        // keep only the LARGEST run. UI chrome (a logo here, a badge there)
        // tends to produce small, scattered, non-contiguous hits; the real
        // chart is one tall, unbroken band. Picking the biggest contiguous
        // run — rather than the union of every hit from top to bottom — is
        // what actually excludes scattered header/footer color instead of
        // stretching the crop to include them.
        var bestRunStart = -1
        var bestRunEnd = -1
        var curRunStart = -1
        for (i in rowDensity.indices) {
            if (rowDensity[i] >= rowThreshold) {
                if (curRunStart == -1) curRunStart = i
            } else {
                if (curRunStart != -1) {
                    if (bestRunStart == -1 || (i - 1 - curRunStart) > (bestRunEnd - bestRunStart)) {
                        bestRunStart = curRunStart
                        bestRunEnd = i - 1
                    }
                    curRunStart = -1
                }
            }
        }
        if (curRunStart != -1) {
            val end = rowDensity.size - 1
            if (bestRunStart == -1 || (end - curRunStart) > (bestRunEnd - bestRunStart)) {
                bestRunStart = curRunStart
                bestRunEnd = end
            }
        }

        if (bestRunStart == -1) {
            // No confident candle band found — fall back to the full image
            // rather than guessing, so we never crop out real content.
            return Rect(0, 0, w, h)
        }

        var chartTop = sampledYs[bestRunStart]
        var chartBottom = sampledYs[bestRunEnd]

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
        val colThreshold = (maxColDensity * 0.03).toInt().coerceAtLeast(1)

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
        if (detectedH < h * 0.15 || detectedW < w * 0.4) {
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
