package com.mitv.trademaster.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that owns the MediaProjection session used for the
 * "capture & analyze" feature. Must be started with the result Intent from
 * MediaProjectionManager.createScreenCaptureIntent(), which requires a
 * one-time user consent dialog triggered from an Activity.
 *
 * `latestFrame` is a simple in-memory holder read by OverlayBubbleService;
 * a production build would wire up an ImageReader + VirtualDisplay here.
 * Kept intentionally minimal so this stays a starting point you can extend.
 */
class ScreenCaptureService : Service() {

    companion object {
        var latestFrame: Bitmap? = null
    }

    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode != -1 && data != null) {
            val manager = getSystemService(MediaProjectionManager::class.java)
            mediaProjection = manager.getMediaProjection(resultCode, data)
            // TODO: set up ImageReader + VirtualDisplay to continuously
            // update `latestFrame` from the projection surface.
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        val channelId = "mitv_capture_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "MI Trade Master Capture", NotificationManager.IMPORTANCE_MIN)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MI Trade Master")
            .setContentText("Screen capture ready for analysis")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1002, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
    }
}
