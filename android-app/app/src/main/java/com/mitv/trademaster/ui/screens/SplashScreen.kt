package com.mitv.trademaster.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.R
import com.mitv.trademaster.data.LicenseRepository
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: (isActivated: Boolean) -> Unit) {
    val context = LocalContext.current
    val repo = remember { LicenseRepository(context) }
    val scope = rememberCoroutineScope()

    val transition = rememberInfiniteTransition(label = "splash")
    val glow by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    var logoVisible by remember { mutableStateOf(false) }
    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.6f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(700),
        label = "logoAlpha"
    )

    var taglineVisible by remember { mutableStateOf(false) }
    val taglineAlpha by animateFloatAsState(
        targetValue = if (taglineVisible) 1f else 0f,
        animationSpec = tween(600),
        label = "taglineAlpha"
    )

    LaunchedEffect(Unit) {
        logoVisible = true
        delay(400)
        taglineVisible = true
        delay(1400)
        scope.launch {
            val state = repo.currentState()
            onFinished(state.isActivated)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(PanelDarker, BgBlack),
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Faint animated candlestick bars in the background
        CandlestickBackdrop(modifier = Modifier.fillMaxSize().alpha(0.12f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF1A2226), Color(0xFF0A0F11))),
                        RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .alpha(glow)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(BrandGreen.copy(alpha = 0.25f), Color.Transparent)
                            ),
                            RoundedCornerShape(28.dp)
                        )
                )
                Text(
                    "MI",
                    color = BrandSilver,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "MI TRADE MASTER",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(logoAlpha)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                stringResourceCompat(context, R.string.splash_tagline),
                color = BrandGreen,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.alpha(taglineAlpha)
            )
        }

        // Bottom loading indicator
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(taglineAlpha),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(3) { i ->
                PulsingDot(delayMillis = i * 200)
            }
        }
    }
}

@Composable
private fun PulsingDot(delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delayMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .background(BrandGreen, RoundedCornerShape(50))
    )
}

@Composable
private fun CandlestickBackdrop(modifier: Modifier = Modifier) {
    // Simple static-ish decorative bars (kept lightweight; no canvas heavy lifting)
    Row(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val heights = listOf(40, 70, 55, 90, 60, 100, 75, 50, 85, 65)
        heights.forEachIndexed { i, h ->
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(h.dp)
                    .background(
                        if (i % 2 == 0) BrandGreen else BrandRed,
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun stringResourceCompat(context: android.content.Context, resId: Int): String {
    return context.getString(resId)
}
