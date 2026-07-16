package com.mitv.trademaster.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Placeholder receiver (declared in the manifest for BOOT_COMPLETED).
 * Intentionally does not auto-start the overlay after boot — the user
 * must explicitly start the floating analyzer from the Home screen each
 * time, which keeps behavior predictable and avoids surprising background
 * activity.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op by design.
    }
}
