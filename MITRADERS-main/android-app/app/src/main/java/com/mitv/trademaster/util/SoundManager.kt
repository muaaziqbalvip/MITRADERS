package com.mitv.trademaster.util

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * App-wide tap feedback — a soft system "click" tone plus a light haptic
 * tick, used on primary buttons (Up/Down trades, Mark Complete, nav tabs,
 * etc.) so the app feels tactile instead of silent/flat. Uses the system's
 * built-in click sound effect (no bundled audio asset needed, respects the
 * user's system sound settings automatically).
 */
class SoundManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun playClick() {
        audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.6f)
    }

    fun playSuccess() {
        audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN, 0.7f)
    }

    fun playError() {
        audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_INVALID, 0.7f)
    }
}

@Composable
fun rememberSoundManager(): SoundManager {
    val context = LocalContext.current
    return remember { SoundManager(context) }
}

/** Convenience combo: system click tone + a light haptic tick, in one call. */
@Composable
fun rememberTapFeedback(): () -> Unit {
    val sound = rememberSoundManager()
    val haptics = LocalHapticFeedback.current
    return {
        sound.playClick()
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}
