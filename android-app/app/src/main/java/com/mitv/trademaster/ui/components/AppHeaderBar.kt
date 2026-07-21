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
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(animation = tween(2200), repeatMode = RepeatMode.Reverse),
        label = "glowAlpha"
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(animation = tween(2200), repeatMode = RepeatMode.Reverse),
        label = "iconScale"
    )

    Column(modifier = Modifier.background(BgBlack)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Soft glow behind the icon, breathing slowly — gives the
                // header a "premium/alive" feel without being distracting.
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer { alpha = glowAlpha }
                        .background(BrandGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                )
                Image(
                    painter = painterResource(id = R.drawable.header_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .scale(iconScale)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = BrandSilverDim, fontSize = 11.sp)
                }
            }
        }
        HorizontalDivider(color = LineSubtle)
    }
}
