package com.mitv.trademaster.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mitv.trademaster.ui.theme.*

private val SYMBOL_PRESETS = listOf(
    "FX:EURUSD" to "EUR/USD",
    "FX:GBPUSD" to "GBP/USD",
    "FX:USDJPY" to "USD/JPY",
    "OANDA:XAUUSD" to "Gold",
    "BINANCE:BTCUSDT" to "Bitcoin",
    "BINANCE:ETHUSDT" to "Ethereum",
    "NASDAQ:AAPL" to "Apple",
)

/**
 * Live, real chart data via TradingView's official free embeddable widget
 * (the same one used on countless finance sites — no API key or account
 * needed, just an internet connection). Rendered in a WebView since
 * TradingView doesn't offer a native Android SDK; the widget itself is
 * fully interactive (pinch-zoom, drawing tools, timeframe switching)
 * exactly like the tradingview.com website.
 */
@Composable
fun LiveChartsScreen(language: String) {
    var selectedSymbol by remember { mutableStateOf(SYMBOL_PRESETS.first().first) }
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()

    Column(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ShowChart, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(if (language == "ur") "لائیو چارٹس" else "Live Charts", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (language == "ur") "حقیقی وقت کی مارکیٹ ڈیٹا — TradingView" else "Real-time market data — powered by TradingView", color = BrandSilverDim, fontSize = 10.5.sp)
            }
        }

        // Symbol picker chips
        LazyRowSymbolPicker(selectedSymbol = selectedSymbol, onSelect = { tapFeedback(); selectedSymbol = it })

        Spacer(Modifier.height(4.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            TradingViewWebView(symbol = selectedSymbol)
        }

        Text(
            if (language == "ur") "چارٹ صرف تعلیمی مقاصد کے لیے ہے — یہ مالی مشورہ نہیں ہے۔"
            else "Chart is for educational purposes only — this is not financial advice.",
            color = BrandSilverDim.copy(alpha = 0.6f), fontSize = 9.5.sp,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun LazyRowSymbolPicker(selectedSymbol: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
    ) {
        items(SYMBOL_PRESETS) { (symbol, label) ->
            val isSelected = symbol == selectedSymbol
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) BrandGreen else PanelDark)
                    .clickable { onSelect(symbol) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(label, color = if (isSelected) Color(0xFF04120B) else BrandSilver, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TradingViewWebView(symbol: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.parseColor("#05070A"))
                // TradingView's widget script sets its own cookies from tradingview.com
                // while the page is hosted via loadDataWithBaseURL — without explicitly
                // allowing third-party cookies here, WebView silently blocks them and the
                // widget fails to initialize (shows a blank/white panel with no error).
                android.webkit.CookieManager.getInstance().let { cm ->
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(this, true)
                }
                loadDataWithBaseURL("https://s.tradingview.com", buildHtml(symbol), "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            webView.loadDataWithBaseURL("https://s.tradingview.com", buildHtml(symbol), "text/html", "UTF-8", null)
        }
    )
}

/**
 * Minimal HTML host page embedding TradingView's official "Advanced Chart"
 * widget script. This is TradingView's own public embed snippet (no API
 * key needed) — loaded via loadDataWithBaseURL so relative widget requests
 * resolve against tradingview.com correctly.
 */
private fun buildHtml(symbol: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
      <style>html,body{margin:0;padding:0;background:#05070A;height:100%;}</style>
    </head>
    <body>
      <div class="tradingview-widget-container" style="height:100%;width:100%">
        <div id="tv_chart" style="height:100%;width:100%"></div>
      </div>
      <script src="https://s3.tradingview.com/tv.js"></script>
      <script>
        new TradingView.widget({
          "autosize": true,
          "symbol": "$symbol",
          "interval": "5",
          "timezone": "Etc/UTC",
          "theme": "dark",
          "style": "1",
          "locale": "en",
          "toolbar_bg": "#0e141c",
          "enable_publishing": false,
          "hide_top_toolbar": false,
          "hide_legend": false,
          "save_image": false,
          "container_id": "tv_chart",
          "backgroundColor": "#05070A",
          "gridColor": "rgba(255,255,255,0.06)"
        });
      </script>
    </body>
    </html>
""".trimIndent()
