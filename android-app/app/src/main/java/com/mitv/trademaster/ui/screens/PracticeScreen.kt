package com.mitv.trademaster.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private data class DemoCandle(val open: Float, val close: Float, val high: Float, val low: Float, val forming: Boolean = false) {
    val isBullish get() = close >= open
    val bodySize get() = kotlin.math.abs(close - open)
}

private enum class GameState { SETUP, PLAYING, FINISHED }

// Selectable timeframes — how long each candle takes to form (like a real demo platform).
private val TIMEFRAMES = listOf(5, 10, 15, 30)
private const val TICK_MS = 150L // how often the forming candle's live price updates — lower = smoother

@Composable
fun PracticeScreen(language: String) {
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()
    val soundManager = com.mitv.trademaster.util.rememberSoundManager()
    var gameState by remember { mutableStateOf(GameState.SETUP) }
    var startingBalance by remember { mutableStateOf(50f) }
    var tradeAmount by remember { mutableStateOf(10f) }
    var targetBalance by remember { mutableStateOf(100f) }
    var timeframeSeconds by remember { mutableStateOf(15) }

    var balance by remember { mutableStateOf(50f) }
    var candles by remember { mutableStateOf(generateInitialCandles()) }
    var formingCandle by remember { mutableStateOf<DemoCandle?>(null) }
    var wins by remember { mutableStateOf(0) }
    var losses by remember { mutableStateOf(0) }
    var lastExplanation by remember { mutableStateOf<String?>(null) }
    var isSettling by remember { mutableStateOf(false) } // brief "trade executing" pause after candle closes
    var pendingGuess by remember { mutableStateOf<Boolean?>(null) }
    var secondsLeft by remember { mutableStateOf(timeframeSeconds.toFloat()) }
    var showSettings by remember { mutableStateOf(false) }

    if (gameState == GameState.SETUP) {
        SetupScreen(
            language = language,
            startingBalance = startingBalance, onStartingBalanceChange = { startingBalance = it },
            tradeAmount = tradeAmount, onTradeAmountChange = { tradeAmount = it },
            targetBalance = targetBalance, onTargetBalanceChange = { targetBalance = it },
            timeframeSeconds = timeframeSeconds, onTimeframeChange = { timeframeSeconds = it },
            onStart = {
                balance = startingBalance
                candles = generateInitialCandles()
                formingCandle = null
                wins = 0; losses = 0; lastExplanation = null
                pendingGuess = null
                gameState = GameState.PLAYING
            }
        )
        return
    }

    if (gameState == GameState.FINISHED) {
        ResultScreen(language, balance >= targetBalance, balance, wins, losses) { gameState = GameState.SETUP }
        return
    }

    // ---------- Live candle-forming loop ----------
    // Instead of a fake ticking price line, we simulate the CURRENT candle actually
    // forming tick-by-tick (open fixed, high/low/close updating live) — this is what
    // gives a real trading-demo feel instead of a static chart with a dashed line.
    LaunchedEffect(candles.size, timeframeSeconds, gameState) {
        if (gameState != GameState.PLAYING) return@LaunchedEffect
        val open = candles.last().close
        var close = open
        var high = open
        var low = open
        val totalTicks = ((timeframeSeconds * 1000L) / TICK_MS).toInt().coerceAtLeast(4)

        secondsLeft = timeframeSeconds.toFloat()
        formingCandle = DemoCandle(open, close, high, low, forming = true)

        for (tick in 1..totalTicks) {
            delay(TICK_MS)
            val drift = Random.nextFloat() * 1.3f - 0.62f // very slight upward bias so charts don't feel dead-flat
            close = (close + drift).coerceAtLeast(0.5f)
            high = max(high, close)
            low = min(low, close)
            formingCandle = DemoCandle(open, close, high, low, forming = true)
            secondsLeft = (timeframeSeconds - (tick * TICK_MS) / 1000f).coerceAtLeast(0f)
        }

        // Candle closes — settle the trade.
        isSettling = true
        delay(500)
        val final = DemoCandle(open, close, high, low)
        val guess = pendingGuess
        if (guess != null) {
            val correct = guess == final.isBullish
            if (correct) { wins++; balance += tradeAmount; soundManager.playSuccess() } else { losses++; balance -= tradeAmount; soundManager.playError() }
            lastExplanation = explainCandle(final, language)
        } else {
            lastExplanation = null
        }
        candles = (candles + final).takeLast(24)
        formingCandle = null
        pendingGuess = null
        isSettling = false

        if (balance <= 0f || balance >= targetBalance) {
            delay(1000)
            gameState = GameState.FINISHED
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot()
                    Spacer(Modifier.width(6.dp))
                    Text(if (language == "ur") "پریکٹس موڈ" else "Practice Mode", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text(if (language == "ur") "ڈیمو بیلنس — کوئی اصل رقم نہیں" else "DEMO balance — no real money", color = BrandSilverDim, fontSize = 11.sp)
            }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Tune, contentDescription = null, tint = BrandSilverDim) }
            TextButton(onClick = { gameState = GameState.SETUP }) {
                Text(if (language == "ur") "دوبارہ ترتیب" else "Reset", color = BrandGreen, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(if (language == "ur") "ڈیمو بیلنس" else "Demo Balance", color = BrandSilverDim, fontSize = 10.sp)
                        Text("$${"%.2f".format(balance)}", color = if (balance >= startingBalance) BrandGreen else BrandRed, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (language == "ur") "ہدف" else "Target", color = BrandSilverDim, fontSize = 10.sp)
                        Text("$${"%.0f".format(targetBalance)}", color = BrandSilver, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (balance / targetBalance).coerceIn(0f, 1f) },
                    color = BrandGreen, trackColor = LineSubtle,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreCard(Modifier.weight(1f), if (language == "ur") "جیت" else "Wins", wins.toString(), BrandGreen)
            ScoreCard(Modifier.weight(1f), if (language == "ur") "ہار" else "Losses", losses.toString(), BrandRed)
            ScoreCard(Modifier.weight(1f), if (language == "ur") "ٹائم فریم" else "Timeframe", "${timeframeSeconds}s", BrandSilver)
        }

        Spacer(Modifier.height(14.dp))

        // ---------- Live chart with real forming candle + countdown ----------
        Box(modifier = Modifier.fillMaxWidth().height(220.dp).background(PanelDark, RoundedCornerShape(16.dp)).padding(12.dp)) {
            LiveCandleChart(candles = candles, formingCandle = formingCandle)
            // Countdown ring, top-right corner, only while a candle is actively forming.
            if (formingCandle != null && !isSettling) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    CountdownRing(secondsLeft = secondsLeft, totalSeconds = timeframeSeconds)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (isSettling) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = BrandGreen, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(if (language == "ur") "کینڈل بند ہو رہی ہے..." else "Candle closing...", color = BrandSilverDim, fontSize = 12.sp)
            }
        } else {
            Text(
                if (pendingGuess == null)
                    (if (language == "ur") "اگلی کینڈل کی سمت کا اندازہ لگائیں:" else "Guess the next candle's direction:")
                else
                    (if (language == "ur") "ٹریڈ رکھی گئی — کینڈل بند ہونے کا انتظار ہے" else "Trade placed — waiting for candle to close"),
                color = BrandSilverDim, fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { tapFeedback(); pendingGuess = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingGuess == true) BrandGreen else BrandGreen.copy(alpha = 0.15f),
                        contentColor = if (pendingGuess == true) Color(0xFF04120B) else BrandGreen
                    ),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(50.dp)
                ) { Icon(Icons.Filled.TrendingUp, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(if (language == "ur") "اوپر" else "Up") }

                Button(
                    onClick = { tapFeedback(); pendingGuess = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingGuess == false) BrandRed else BrandRed.copy(alpha = 0.15f),
                        contentColor = if (pendingGuess == false) Color.White else BrandRed
                    ),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(50.dp)
                ) { Icon(Icons.Filled.TrendingDown, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(if (language == "ur") "نیچے" else "Down") }
            }
        }

        lastExplanation?.let { explanation ->
            Spacer(Modifier.height(14.dp))
            Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(explanation, color = BrandSilverDim, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(60.dp))
    }

    if (showSettings) {
        TimeframeSettingsSheet(
            language = language,
            current = timeframeSeconds,
            onSelect = { timeframeSeconds = it; showSettings = false },
            onDismiss = { showSettings = false }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimeframeSettingsSheet(language: String, current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelDark) {
        Column(Modifier.padding(20.dp)) {
            Text(if (language == "ur") "کینڈل ٹائم فریم" else "Candle Timeframe", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                if (language == "ur") "ہر کینڈل بننے میں کتنا وقت لگے گا" else "How long each candle takes to form",
                color = BrandSilverDim, fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TIMEFRAMES.forEach { tf ->
                    val selected = tf == current
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) BrandGreen.copy(alpha = 0.15f) else BgBlack)
                            .clickable { onSelect(tf) }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${tf}s", color = if (selected) BrandGreen else BrandSilverDim, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CountdownRing(secondsLeft: Float, totalSeconds: Int) {
    val progress = (secondsLeft / totalSeconds).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(150), label = "countdown")
    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { 1f }, modifier = Modifier.size(44.dp), color = LineSubtle, strokeWidth = 3.dp, trackColor = Color.Transparent
        )
        CircularProgressIndicator(
            progress = { animatedProgress }, modifier = Modifier.size(44.dp),
            color = if (secondsLeft <= 3f) BrandRed else BrandGreen, strokeWidth = 3.dp, trackColor = Color.Transparent
        )
        Text(secondsLeft.toInt().toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AmountSelector(label: String, options: List<Float>, selected: Float, onSelect: (Float) -> Unit) {
    Text(label, color = BrandSilverDim, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { amount ->
            val isSelected = amount == selected
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) BrandGreen.copy(alpha = 0.15f) else PanelDark)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null, onClick = { onSelect(amount) }
                    ).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$${amount.toInt()}", color = if (isSelected) BrandGreen else BrandSilverDim, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun TimeframeSelectorInline(label: String, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, color = BrandSilverDim, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TIMEFRAMES.forEach { tf ->
            val isSelected = tf == selected
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) BrandGreen.copy(alpha = 0.15f) else PanelDark)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null, onClick = { onSelect(tf) }
                    ).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("${tf}s", color = if (isSelected) BrandGreen else BrandSilverDim, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ResultScreen(language: String, won: Boolean, finalBalance: Float, wins: Int, losses: Int, onRestart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(BgBlack).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (won) Icons.Filled.EmojiEvents else Icons.Filled.SentimentDissatisfied,
            contentDescription = null, tint = if (won) BrandGreen else BrandRed, modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (won) (if (language == "ur") "ہدف حاصل! 🎉" else "Target Reached! 🎉") else (if (language == "ur") "بیلنس ختم ہوگیا" else "Balance Depleted"),
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text("Final: $${"%.2f".format(finalBalance)} · $wins wins · $losses losses", color = BrandSilverDim, fontSize = 13.sp)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(0.7f).height(50.dp)
        ) { Text(if (language == "ur") "دوبارہ کھیلیں" else "Play Again", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ScoreCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(label, color = BrandSilverDim, fontSize = 10.sp)
        }
    }
}

@Composable
private fun LiveCandleChart(candles: List<DemoCandle>, formingCandle: DemoCandle?) {
    // Smooth "slide in" animation: whenever the candle count changes (a candle just
    // finalized and a new one starts forming), the whole chart nudges in from the
    // right instead of snapping — this is what makes it feel like a live feed.
    val slideOffset = remember { Animatable(0f) }
    val allCandles = if (formingCandle != null) candles + formingCandle else candles

    LaunchedEffect(candles.size) {
        slideOffset.snapTo(1f)
        slideOffset.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val totalSlots = 20
        val candleWidthPx = widthPx / totalSlots
        val shiftPx = candleWidthPx * slideOffset.value

        val allValues = allCandles.flatMap { listOf(it.high, it.low) }
        val maxV = allValues.maxOrNull() ?: 1f
        val minV = allValues.minOrNull() ?: 0f
        val range = max(maxV - minV, 0.01f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            fun yFor(v: Float) = size.height - ((v - minV) / range) * size.height

            // Faint horizontal grid lines for readability, like a real chart.
            val gridLines = 4
            repeat(gridLines + 1) { i ->
                val y = size.height * i / gridLines
                drawLine(
                    color = LineSubtle.copy(alpha = 0.4f),
                    start = Offset(0f, y), end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            val visibleCandles = allCandles.takeLast(totalSlots)
            val startSlot = totalSlots - visibleCandles.size

            visibleCandles.forEachIndexed { i, c ->
                val xCenter = (startSlot + i) * candleWidthPx + candleWidthPx / 2 + shiftPx
                val isFormingCandle = formingCandle != null && i == visibleCandles.lastIndex
                val color = if (c.isBullish) Color(0xFF34E39A) else Color(0xFFFF5C6A)
                val alpha = if (isFormingCandle) 0.85f else 1f

                drawLine(
                    color = color.copy(alpha = alpha),
                    start = Offset(xCenter, yFor(c.high)), end = Offset(xCenter, yFor(c.low)),
                    strokeWidth = 2f, cap = StrokeCap.Round
                )
                val bodyTop = yFor(max(c.open, c.close))
                val bodyBottom = yFor(min(c.open, c.close))
                val bodyHeight = max(bodyBottom - bodyTop, 2f)

                if (isFormingCandle) {
                    // Forming candle: outlined/hollow style so it visually reads as "not closed yet".
                    drawRect(
                        color = color,
                        topLeft = Offset(xCenter - candleWidthPx * 0.32f, bodyTop),
                        size = Size(candleWidthPx * 0.64f, bodyHeight),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                    )
                } else {
                    drawRect(
                        color = color,
                        topLeft = Offset(xCenter - candleWidthPx * 0.32f, bodyTop),
                        size = Size(candleWidthPx * 0.64f, bodyHeight)
                    )
                }
            }

            // Dashed horizontal line at the live/forming price — classic trading-platform touch.
            formingCandle?.let { fc ->
                val y = yFor(fc.close)
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                drawLine(
                    color = Color(0xFFCDD6D8).copy(alpha = 0.6f),
                    start = Offset(0f, y), end = Offset(size.width, y),
                    strokeWidth = 1.5f, pathEffect = dash
                )
            }
        }
    }
}

private fun generateInitialCandles(): List<DemoCandle> {
    var price = 100f
    val list = mutableListOf<DemoCandle>()
    repeat(16) {
        val open = price
        val close = open + Random.nextFloat() * 6f - 3f
        val high = max(open, close) + Random.nextFloat() * 2f
        val low = min(open, close) - Random.nextFloat() * 2f
        list.add(DemoCandle(open, close, high, low))
        price = close
    }
    return list
}

private fun explainCandle(c: DemoCandle, language: String): String {
    val wickTop = c.high - max(c.open, c.close)
    val wickBottom = min(c.open, c.close) - c.low
    val bigWick = wickTop > c.bodySize * 0.8f || wickBottom > c.bodySize * 0.8f

    return if (language == "ur") {
        when {
            c.isBullish && bigWick -> "یہ کینڈل سبز بنی کیونکہ قیمت آخر میں خریداری کے دباؤ سے اوپر بند ہوئی، لیکن لمبی بتی سے پتہ چلتا ہے کہ درمیان میں فروخت کا دباؤ بھی تھا۔"
            c.isBullish -> "یہ کینڈل سبز بنی کیونکہ بند ہونے کی قیمت کھلنے کی قیمت سے زیادہ رہی — خریداری کا دباؤ حاوی رہا۔"
            bigWick -> "یہ کینڈل سرخ بنی کیونکہ قیمت نیچے بند ہوئی، لمبی بتی دکھاتی ہے کہ قیمت میں کچھ اتار چڑھاؤ آیا۔"
            else -> "یہ کینڈل سرخ بنی کیونکہ بند ہونے کی قیمت کھلنے کی قیمت سے کم رہی — فروخت کا دباؤ حاوی رہا۔"
        }
    } else {
        when {
            c.isBullish && bigWick -> "This candle closed green — buying pressure won by the close, but the long wick shows sellers pushed back partway through."
            c.isBullish -> "This candle closed green because the close price ended higher than the open — buying pressure was dominant this round."
            bigWick -> "This candle closed red, and the long wick shows the price swung significantly before settling lower."
            else -> "This candle closed red because the close price ended lower than the open — selling pressure was dominant this round."
        }
    }
}

@Composable
private fun LiveDot() {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "liveAlpha")
    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(BrandGreen.copy(alpha = alpha)))
}

@Composable
private fun SetupScreen(
    language: String,
    startingBalance: Float, onStartingBalanceChange: (Float) -> Unit,
    tradeAmount: Float, onTradeAmountChange: (Float) -> Unit,
    targetBalance: Float, onTargetBalanceChange: (Float) -> Unit,
    timeframeSeconds: Int, onTimeframeChange: (Int) -> Unit,
    onStart: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(BgBlack).padding(20.dp)) {
        Text(if (language == "ur") "پریکٹس سیشن ترتیب دیں" else "Set Up Practice Session", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            if (language == "ur") "یہ سب ڈیمو رقم ہے — کوئی حقیقی پیسہ استعمال نہیں ہوتا" else "This is all virtual demo money — no real funds are used",
            color = BrandSilverDim, fontSize = 12.sp
        )

        Spacer(Modifier.height(24.dp))
        AmountSelector(if (language == "ur") "ابتدائی ڈیمو بیلنس" else "Starting Demo Balance", listOf(10f, 20f, 50f, 100f), startingBalance, onStartingBalanceChange)
        Spacer(Modifier.height(20.dp))
        AmountSelector(if (language == "ur") "فی ٹریڈ رقم" else "Amount Per Trade", listOf(5f, 10f, 20f, 25f), tradeAmount, onTradeAmountChange)
        Spacer(Modifier.height(20.dp))
        AmountSelector(if (language == "ur") "ہدف بیلنس" else "Target Balance", listOf(50f, 100f, 200f, 500f), targetBalance, onTargetBalanceChange)
        Spacer(Modifier.height(20.dp))
        TimeframeSelectorInline(if (language == "ur") "کینڈل ٹائم فریم" else "Candle Timeframe", timeframeSeconds, onTimeframeChange)

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (language == "ur") "پریکٹس شروع کریں" else "Start Practice", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth().background(PanelDark, RoundedCornerShape(12.dp)).padding(14.dp)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (language == "ur") "یہ ایک بے ترتیب تشبیہ ہے، تعلیمی مقصد کے لیے — حقیقی مارکیٹ سے مختلف ہو سکتی ہے۔"
                else "This is a randomized simulation for learning purposes — real markets can behave differently.",
                color = BrandSilverDim, fontSize = 10.sp, lineHeight = 14.sp
            )
        }
    }
}
