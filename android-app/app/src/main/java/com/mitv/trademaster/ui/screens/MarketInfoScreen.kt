package com.mitv.trademaster.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mitv.trademaster.ui.theme.*

private enum class MarketInfoTab { CALENDAR, NEWS }

/**
 * Economic Calendar + Forex News, both via TradingView's free public
 * embeddable widgets (same widget family as the live chart screen — no
 * API key, no account, just an internet connection).
 */
@Composable
fun MarketInfoScreen(language: String) {
    var tab by remember { mutableStateOf(MarketInfoTab.CALENDAR) }
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()

    Column(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (language == "ur") "مارکیٹ کی معلومات" else "Market Info", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(PanelDark, RoundedCornerShape(12.dp)).padding(4.dp)
        ) {
            TabChip(
                label = if (language == "ur") "اقتصادی کیلنڈر" else "Economic Calendar",
                icon = Icons.Filled.CalendarMonth,
                selected = tab == MarketInfoTab.CALENDAR,
                modifier = Modifier.weight(1f)
            ) { tapFeedback(); tab = MarketInfoTab.CALENDAR }
            TabChip(
                label = if (language == "ur") "فاریکس نیوز" else "Forex News",
                icon = Icons.Filled.Newspaper,
                selected = tab == MarketInfoTab.NEWS,
                modifier = Modifier.weight(1f)
            ) { tapFeedback(); tab = MarketInfoTab.NEWS }
        }

        Spacer(Modifier.height(10.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                MarketInfoTab.CALENDAR -> EconomicCalendarWebView()
                MarketInfoTab.NEWS -> ForexNewsWebView()
            }
        }
    }
}

@Composable
private fun TabChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BrandGreen.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) BrandGreen else BrandSilverDim, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) BrandGreen else BrandSilverDim, fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EconomicCalendarWebView() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.parseColor("#05070A"))
                loadDataWithBaseURL(
                    "https://s.tradingview.com", """
                    <!DOCTYPE html><html><head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <style>html,body{margin:0;padding:0;background:#05070A;height:100%;}</style>
                    </head><body>
                    <div class="tradingview-widget-container" style="height:100%;width:100%">
                      <div class="tradingview-widget-container__widget"></div>
                    </div>
                    <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-events.js" async>
                    {
                      "colorTheme": "dark",
                      "isTransparent": false,
                      "width": "100%",
                      "height": "100%",
                      "locale": "en",
                      "importanceFilter": "-1,0,1"
                    }
                    </script>
                    </body></html>
                """.trimIndent(), "text/html", "UTF-8", null
                )
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ForexNewsWebView() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.parseColor("#05070A"))
                loadDataWithBaseURL(
                    "https://s.tradingview.com", """
                    <!DOCTYPE html><html><head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <style>html,body{margin:0;padding:0;background:#05070A;height:100%;}</style>
                    </head><body>
                    <div class="tradingview-widget-container" style="height:100%;width:100%">
                      <div class="tradingview-widget-container__widget"></div>
                    </div>
                    <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-timeline.js" async>
                    {
                      "feedMode": "market",
                      "market": "forex",
                      "colorTheme": "dark",
                      "isTransparent": false,
                      "displayMode": "regular",
                      "width": "100%",
                      "height": "100%",
                      "locale": "en"
                    }
                    </script>
                    </body></html>
                """.trimIndent(), "text/html", "UTF-8", null
                )
            }
        }
    )
}
