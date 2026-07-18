package com.mitv.trademaster.overlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

/**
 * A transparent, no-UI activity whose only job is to trigger the system's
 * "Allow MI Trade Master to record or cast your screen?" consent dialog.
 * This MUST happen from an Activity — a Service can't request it directly,
 * which is why the floating-bubble capture button was failing to get
 * permission before this activity existed.
 *
 * Launched from OverlayBubbleService's "Capture & Analyze" button (via a
 * FLAG_ACTIVITY_NEW_TASK intent, since services can't start activities
 * without that flag) or from the Home screen the first time. Once the
 * user grants permission, the result is forwarded to ScreenCaptureService
 * and this activity immediately finishes.
 */
class ScreenCaptureConsentActivity : Activity() {

    private val requestScreenCapture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            startForegroundService(serviceIntent)
            Toast.makeText(this, "Screen capture enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission was not granted", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        requestScreenCapture.launch(projectionManager.createScreenCaptureIntent())
    }
}
