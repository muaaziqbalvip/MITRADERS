package com.mitv.trademaster.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.mitv.trademaster.R
import com.mitv.trademaster.analysis.ChartAnalyzer
import com.mitv.trademaster.analysis.Direction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Floating overlay: a small round bubble showing the app icon that expands
 * into an analysis panel with a Capture & Analyze button, a result
 * indicator, and an explicit Close button that stops the whole service.
 *
 * v2 simplification (per product decision): the old "auto-tap at a saved
 * screen position" toggle has been removed. The panel now only ever shows
 * an analysis result — it does not interact with any other app's UI.
 */
class OverlayBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var isPanelExpanded = false
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("mitv_overlay", Context.MODE_PRIVATE)
        startForegroundNotification()
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        val channelId = "mitv_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "MI Trade Master Analyzer", NotificationManager.IMPORTANCE_MIN)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MI Trade Master")
            .setContentText("Floating analyzer is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1001, notification)
    }

    private fun addBubble() {
        val size = (56 * resources.displayMetrics.density).toInt()

        val bubble = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0B1114"))
                setStroke(3, Color.parseColor("#34E39A"))
            }
            elevation = 12f
        }

        val iconView = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val padding = (6 * resources.displayMetrics.density).toInt()
        bubble.addView(iconView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
            setMargins(padding, padding, padding, padding)
        })

        val params = WindowManager.LayoutParams(
            size, size, overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = prefs.getInt("bubble_x", 20)
        params.y = prefs.getInt("bubble_y", 200)

        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var isDragging = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) isDragging = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt("bubble_x", params.x).putInt("bubble_y", params.y).apply()
                    if (!isDragging) toggleExpandedPanel(params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        bubbleView = bubble
    }

    private fun toggleExpandedPanel(bubbleParams: WindowManager.LayoutParams) {
        if (isPanelExpanded) {
            panelView?.let { runCatching { windowManager.removeView(it) } }
            panelView = null
            isPanelExpanded = false
            return
        }

        val density = resources.displayMetrics.density
        val panelWidth = (250 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(Color.parseColor("#0B1114"))
                setStroke(1, Color.parseColor("#1C2A2F"))
            }
        }

        // Header row: title + close button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "MI Trade Master"
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#7C8B8F"))
            textSize = 16f
            setPadding((8 * density).toInt(), 0, (4 * density).toInt(), 0)
        }
        closeBtn.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
                stopSelf() // fully closes the overlay + service
            }
            true
        }
        headerRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(closeBtn)
        container.addView(headerRow)

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
        }
        val signalDot = View(this).apply {
            val dotSize = (14 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#7C8B8F")) }
        }
        val signalLabel = TextView(this).apply {
            text = "  No analysis yet"
            setTextColor(Color.parseColor("#CDD6D8"))
            textSize = 12f
        }
        statusRow.addView(signalDot)
        statusRow.addView(signalLabel)
        container.addView(statusRow)

        val captureBtn = TextView(this).apply {
            text = "📸  Capture & Analyze"
            setTextColor(Color.parseColor("#04120B"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
            background = GradientDrawable().apply { cornerRadius = 10 * density; setColor(Color.parseColor("#34E39A")) }
        }
        container.addView(captureBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (6 * density).toInt()
        })

        val disclaimer = TextView(this).apply {
            text = "Educational analysis only — not financial advice."
            setTextColor(Color.parseColor("#7C8B8F"))
            textSize = 9f
            setPadding(0, (12 * density).toInt(), 0, 0)
        }
        container.addView(disclaimer)

        captureBtn.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
                runAnalysis(signalDot, signalLabel)
            }
            true
        }

        val panelParams = WindowManager.LayoutParams(
            panelWidth, WindowManager.LayoutParams.WRAP_CONTENT, overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        panelParams.gravity = Gravity.TOP or Gravity.START
        panelParams.x = (bubbleParams.x - panelWidth / 3).coerceAtLeast(0)
        panelParams.y = bubbleParams.y + (60 * density).toInt()

        windowManager.addView(container, panelParams)
        panelView = container
        isPanelExpanded = true
    }

    private fun runAnalysis(signalDot: View, signalLabel: TextView) {
        val bitmap = ScreenCaptureService.latestFrame
        if (bitmap == null) {
            signalLabel.text = "  Open app once to enable capture"
            return
        }
        CoroutineScope(Dispatchers.Default).launch {
            val result = ChartAnalyzer.analyze(bitmap)
            val color = when (result.direction) {
                Direction.UP -> Color.parseColor("#34E39A")
                Direction.DOWN -> Color.parseColor("#FF5C6A")
                Direction.NEUTRAL -> Color.parseColor("#7C8B8F")
            }
            val text = when (result.direction) {
                Direction.UP -> "  Bullish lean (${result.confidence.name.lowercase()})"
                Direction.DOWN -> "  Bearish lean (${result.confidence.name.lowercase()})"
                Direction.NEUTRAL -> "  No clear lean"
            }
            signalDot.post {
                (signalDot.background as GradientDrawable).setColor(color)
                signalLabel.text = text
            }
        }
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
    }
}
