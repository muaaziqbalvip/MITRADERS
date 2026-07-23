package com.mitv.trademaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.ui.theme.BgBlack
import com.mitv.trademaster.ui.theme.BrandGreen
import com.mitv.trademaster.ui.theme.BrandSilver
import com.mitv.trademaster.ui.theme.BrandSilverDim
import com.mitv.trademaster.ui.theme.LineSubtle

/**
 * Top header bar shown across every main-shell tab. Clean typography-led
 * header (no banner image/icon) with a subtle brand-tinted gradient panel
 * and a thin accent underline for a premium, minimal look.
 */
@Composable
fun AppHeaderBar(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BrandGreen.copy(alpha = 0.08f),
                        BgBlack
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .background(BrandGreen)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "MI TRADE MASTER",
                    color = BrandSilver,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                val caption = if (!subtitle.isNullOrBlank()) subtitle else title
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        color = BrandSilverDim,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }
        HorizontalDivider(color = LineSubtle, thickness = 1.dp)
    }
}
