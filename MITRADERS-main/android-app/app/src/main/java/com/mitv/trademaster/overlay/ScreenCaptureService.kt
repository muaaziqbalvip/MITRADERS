package com.mitv.trademaster.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import com.mitv.trademaster.R
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Foreground service that owns the ONE MediaProjection session for the whole
 * floating-analyzer feature, and continuously refreshes [latestFrame].
 *
 * Design goal: this service is the single source of truth for "do we have
 * screen-capture permission right now". Nothing else should ever silently
 * re-request permission — see [ScreenCaptureConsentActivity] for the only
 * place that happens, and [OverlayBubbleService] for how it's consumed.
 */
class ScreenCaptureService : Service() {

    companion object {
        @Volatile var latestFrame: Bitmap? = null
        /** True once a MediaProjection is live and frames are flowing. */
        val isActive = MutableStateFlow(false)
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        private const val NOTIF_ID = 1002
        private const val CHANNEL_ID = "mitv_capture_channel"
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // User revoked capture from the system "Stop casting" control, or the
            // projection otherwise died. Shut everything down cleanly instead of
            // leaving a dead service/bubble around.
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        handler = Handler(handlerThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)

        if (resultCode != -1 && data != null && mediaProjection == null) {
            val manager = getSystemService(MediaProjectionManager::class.java)
            mediaProjection = manager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(projectionCallback, handler)
            try {
                setUpVirtualDisplay()
            } catch (e: Exception) {
                // Capture setup failed (e.g. device-specific display quirk) —
                // don't leave a half-initialized, permanently-stuck session.
                mediaProjection?.stop()
                mediaProjection = null
                isActive.value = false
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setUpVirtualDisplay() {
        // NOTE: windowManager.defaultDisplay.getRealMetrics() is unreliable when
        // called from a plain Service (it isn't tied to a specific Display on
        // some devices/OS versions and can silently return wrong/zero values,
        // which was causing the analyzer to sit forever waiting for a frame
        // that never arrives). resources.displayMetrics is the reliable source.
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        if (width <= 0 || height <= 0) {
            // Can't safely create a capture surface — bail out cleanly instead
            // of leaving a dead/zero-size ImageReader that never fires.
            stopSelf()
            return
        }

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "MitvScreenCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    latestFrame = imageToBitmap(image)
                    // isActive only flips to true once a REAL frame has landed —
                    // not merely when the projection/session was created. On
                    // devices with aggressive background throttling (MIUI,
                    // ColorOS, etc), the session can be created but the OS can
                    // delay or drop the first several frames, which previously
                    // left latestFrame null while callers assumed capture was
                    // already working.
                    if (!isActive.value) {
                        isActive.value = true
                    }
                }
            } catch (e: Exception) {
                // Dropped frame — non-fatal, the next tick will refresh latestFrame.
            } finally {
                image?.close()
            }
        }, handler)
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "MI Trade Master Capture", NotificationManager.IMPORTANCE_MIN)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MI Trade Master")
            .setContentText("Screen capture ready for analysis")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.unregisterCallback(projectionCallback)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        handlerThread?.quitSafely()
        latestFrame = null
        isActive.value = false
        // The capture session ending means the floating bubble has nothing to
        // show/analyze anymore — stop it too instead of leaving a dead bubble.
        stopService(Intent(this, OverlayBubbleService::class.java))
    }
}
