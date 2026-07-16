package com.mitv.trademaster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand palette — matches the app icon: deep black background,
// chrome/silver "MI" mark, emerald-green bullish accent, red bearish accent.
val BgBlack = Color(0xFF05080A)
val PanelDark = Color(0xFF0B1114)
val PanelDarker = Color(0xFF0F171B)
val LineSubtle = Color(0xFF1C2A2F)

val BrandGreen = Color(0xFF34E39A)
val BrandGreenDim = Color(0xFF1A7A56)
val BrandRed = Color(0xFFFF5C6A)
val BrandSilver = Color(0xFFCDD6D8)
val BrandSilverDim = Color(0xFF7C8B8F)

private val MitvColorScheme = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color(0xFF04120B),
    secondary = BrandSilver,
    background = BgBlack,
    onBackground = BrandSilver,
    surface = PanelDark,
    onSurface = BrandSilver,
    error = BrandRed,
)

object MitvType {
    val displayBold = TextStyle(fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = 0.5.sp)
    val heading = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)
    val body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp)
    val caption = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp)
    val mono = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 1.sp)
}

@Composable
fun MiTradeMasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MitvColorScheme,
        content = content
    )
}
