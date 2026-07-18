package com.mitv.trademaster.overlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * The ONE place in the whole app that ever asks Android for screen-capture
 * (MediaProjection) permission. Transparent, no UI of its own.
 *
 * Flow:
 *  1. Home screen launches this activity (only when capture isn't already
 *     active — see HomeScreen's Start button).
 *  2. This activity immediately shows the system's native
 *     "Allow MI Trade Master to record or cast your screen?" dialog.
 *  3. If granted: starts ScreenCaptureService (which owns the
 *     MediaProjection for the rest of the session) and THEN starts
 *     OverlayBubbleService — in that order, so the bubble never appears
 *     without an active capture session behind it.
 *  4. If denied: nothing starts, a toast explains why, done.
 *  5. Either way, this activity finishes itself immediately after — it
 *     never lingers and never gets re-triggered from inside the bubble.
 */
class ScreenCaptureConsentActivity : ComponentActivity() {

    private val requestScreenCapture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            startForegroundService(captureIntent)
            startService(Intent(this, OverlayBubbleService::class.java))
            Toast.makeText(this, "Floating analyzer started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission was not granted — floating analyzer needs it to read your chart", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        requestScreenCapture.launch(projectionManager.createScreenCaptureIntent())
    }
}
