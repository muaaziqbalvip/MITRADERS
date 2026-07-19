package com.mitv.trademaster.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.mitv.trademaster.ui.theme.BgBlack
import com.mitv.trademaster.ui.theme.BrandGreen
import com.mitv.trademaster.ui.theme.BrandSilver
import com.mitv.trademaster.ui.theme.BrandSilverDim
import com.mitv.trademaster.ui.theme.PanelDark
import kotlinx.coroutines.delay

/**
 * A fully custom, branded MP4 lesson player — replaces the old YouTube embed.
 * Admin sets a direct MP4 link per language (English / Urdu) from the admin panel;
 * this composable plays whichever matches [language], with a poster image,
 * play/pause, seek bar, mute, and fullscreen toggle styled to match the app.
 */
@Composable
fun Mp4LessonPlayer(
    videoUrlEn: String,
    videoUrlUr: String,
    posterUrl: String,
    language: String,
    modifier: Modifier = Modifier,
) {
    val activeUrl = if (language == "ur" && videoUrlUr.isNotBlank()) videoUrlUr else videoUrlEn
    if (activeUrl.isBlank()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasStarted by remember(activeUrl) { mutableStateOf(false) }
    var isPlaying by remember(activeUrl) { mutableStateOf(false) }
    var isBuffering by remember(activeUrl) { mutableStateOf(true) }
    var hasError by remember(activeUrl) { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isFullscreen by remember { mutableStateOf(false) }

    val exoPlayer = remember(activeUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(activeUrl))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
            override fun onPlayerError(error: PlaybackException) { hasError = true }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_DESTROY -> exoPlayer.release()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.release()
        }
    }

    // Poll playback position for the seek bar while playing.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(250)
        }
    }

    // Auto-hide controls a couple seconds after playback starts.
    LaunchedEffect(isPlaying, showControls) {
        if (isPlaying && showControls) {
            delay(2800)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isFullscreen) 340.dp else 200.dp)
            .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 16.dp))
            .background(Color.Black)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
    ) {
        if (hasError) {
            Column(
                modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    if (language == "ur") "ویڈیو چل نہیں سکی" else "This video couldn't play",
                    color = BrandSilverDim, fontSize = 12.sp
                )
            }
            return@Box
        }

        if (!hasStarted) {
            // Poster / thumbnail state, before the student taps play.
            if (posterUrl.isNotBlank()) {
                AsyncImage(
                    model = posterUrl, contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            } else {
                Box(modifier = Modifier.fillMaxSize().background(PanelDark))
            }
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape).background(BrandGreen.copy(alpha = 0.92f)).align(Alignment.Center)
                    .clickable {
                        hasStarted = true
                        exoPlayer.playWhenReady = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF04120B), modifier = Modifier.size(30.dp))
            }
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Translate, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (language == "ur") "اردو نریشن" else "English narration",
                    color = Color.White, fontSize = 10.sp
                )
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                }
            )

            if (isBuffering) {
                CircularProgressIndicator(color = BrandGreen, modifier = Modifier.align(Alignment.Center).size(32.dp), strokeWidth = 3.dp)
            }

            // ---------- Custom branded controls overlay ----------
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))) {
                    // Center play/pause
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).align(Alignment.Center)
                            .clickable {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp)
                        )
                    }

                    // Bottom bar: seek slider + time + mute + fullscreen
                    Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Slider(
                            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                            onValueChange = { frac -> exoPlayer.seekTo((frac * durationMs).toLong()) },
                            colors = SliderDefaults.colors(thumbColor = BrandGreen, activeTrackColor = BrandGreen, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().height(20.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(formatMs(positionMs) + " / " + formatMs(durationMs), color = Color.White, fontSize = 10.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(
                                if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)
                                    .clickable {
                                        isMuted = !isMuted
                                        exoPlayer.volume = if (isMuted) 0f else 1f
                                    }
                            )
                            Spacer(Modifier.width(14.dp))
                            Icon(
                                if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)
                                    .clickable { isFullscreen = !isFullscreen }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
