package com.mitv.trademaster.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * App-wide tap feedback — a crisp, reliably-audible tone plus a light
 * haptic tick. Uses ToneGenerator on the music stream at high volume
 * instead of the system "touch sound" effect, because touch sounds are
 * often muted or very quiet depending on the user's system settings —
 * this guarantees a snappy, noticeable click every time.
 */
class SoundManager(context: Context) {
    // 90/100 volume on the music stream — loud and immediate without being jarring.
    private val toneGenerator = try { ToneGenerator(AudioManager.STREAM_MUSIC, 90) } catch (e: Exception) { null }

    fun playClick() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
    }

    fun playSuccess() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 160)
    }

    fun playError() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 220)
    }
}

@Composable
fun rememberSoundManager(): SoundManager {
    val context = LocalContext.current
    return remember { SoundManager(context) }
}

/** Convenience combo: loud click tone + a light haptic tick, in one call. */
@Composable
fun rememberTapFeedback(): () -> Unit {
    val sound = rememberSoundManager()
    val haptics = LocalHapticFeedback.current
    return {
        sound.playClick()
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

/**
 * Wraps any onClick lambda so it fires the tap sound + haptic first, then
 * runs the original action. Use this to retrofit tap feedback onto existing
 * Button/clickable onClick lambdas with a single-line change:
 *   onClick = withTapFeedback { doTheThing() }
 */
@Composable
fun withTapFeedback(action: () -> Unit): () -> Unit {
    val tapFeedback = rememberTapFeedback()
    return {
        tapFeedback()
        action()
    }
}
