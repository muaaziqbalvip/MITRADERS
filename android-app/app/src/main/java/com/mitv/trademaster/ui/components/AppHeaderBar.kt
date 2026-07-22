package com.mitv.trademaster.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.R
import com.mitv.trademaster.ui.theme.BgBlack
import com.mitv.trademaster.ui.theme.BrandGreen
import com.mitv.trademaster.ui.theme.BrandSilverDim
import com.mitv.trademaster.ui.theme.LineSubtle

/**
 * Top header bar shown across every main-shell tab: the uploaded
 * header_icon banner with a slow, subtle breathing glow/scale animation
 * behind it, plus the current section title. Kept as one shared component
 * so the animation and layout stay consistent everywhere it's used instead
 * of being re-implemented per screen.
 */
@Composable
fun AppHeaderBar(title: String, subtitle: String? = null) {
    val infiniteTransition = rememberInfiniteTransition(label = "headerGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(animation = tween(2200), repeatMode = RepeatMode.Reverse),
        label = "glowAlpha"
    )
    val bannerScale by infiniteTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(animation = tween(2200), repeatMode = RepeatMode.Reverse),
        label = "bannerScale"
    )

    Column(modifier = Modifier.background(BgBlack)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            // Soft ambient glow behind the whole banner, breathing slowly.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .graphicsLayer { alpha = glowAlpha }
                    .background(BrandGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            )
            // The banner is a wide 21:9 image (full "MI TRADE MASTER" logo
            // lockup) — it must be shown with Fit, never Crop, or it gets
            // sliced down to an unrecognizable sliver of the artwork.
            Image(
                painter = painterResource(id = R.drawable.header_icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .scale(bannerScale)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle, color = BrandSilverDim, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 0.dp)
            )
        } else if (title.isNotBlank()) {
            Text(
                title, color = BrandSilverDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 0.dp)
            )
        }
        HorizontalDivider(color = LineSubtle)
    }
}
