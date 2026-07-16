package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.ui.theme.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * A paper-trading simulator: generates a random-walk candle series, lets
 * the student "predict" the next candle's direction, then reveals the
 * outcome and tracks a running win/loss score — all with virtual points,
 * never real money. Used purely to practice reading candle structure and
 * to demonstrate why short-term direction guessing is hard (win rate
 * naturally hovers near 50%, reinforcing the risk-management lessons).
 */
private data class DemoCandle(val open: Float, val close: Float, val high: Float, val low: Float) {
    val isBullish get() = close >= open
}

@Composable
fun PracticeScreen(language: String) {
    var candles by remember { mutableStateOf(generateInitialCandles()) }
    var wins by remember { mutableStateOf(0) }
    var losses by remember { mutableStateOf(0) }
    var lastResult by remember { mutableStateOf<Boolean?>(null) }
    var awaitingReveal by remember { mutableStateOf(false) }
    var pendingGuess by remember { mutableStateOf<Boolean?>(null) } // true = up

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).padding(20.dp)) {
        Text(
            if (language == "ur") "پریکٹس موڈ" else "Practice Mode",
            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
        )
        Text(
            if (language == "ur") "فرضی پوائنٹس کے ساتھ مشق کریں — کوئی اصل رقم شامل نہیں"
            else "Practice with virtual points — no real money involved",
            color = BrandSilverDim, fontSize = 12.sp
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreCard(modifier = Modifier.weight(1f), label = if (language == "ur") "جیت" else "Wins", value = wins.toString(), color = BrandGreen)
            ScoreCard(modifier = Modifier.weight(1f), label = if (language == "ur") "ہار" else "Losses", value = losses.toString(), color = BrandRed)
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp).background(PanelDark, RoundedCornerShape(16.dp)).padding(12.dp)
        ) {
            CandleChart(candles)
        }

        Spacer(Modifier.height(16.dp))

        if (!awaitingReveal) {
            Text(
                if (language == "ur") "اگلی کینڈل کی سمت کا اندازہ لگائیں:" else "Guess the next candle's direction:",
                color = BrandSilverDim, fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { pendingGuess = true; awaitingReveal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Filled.TrendingUp, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (language == "ur") "اوپر" else "Up")
                }
                Button(
                    onClick = { pendingGuess = false; awaitingReveal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed.copy(alpha = 0.15f), contentColor = BrandRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Filled.TrendingDown, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (language == "ur") "نیچے" else "Down")
                }
            }
        } else {
            LaunchedEffect(pendingGuess) {
                val next = generateNextCandle(candles.last())
                val wasUp = next.isBullish
                val correct = (pendingGuess == wasUp)
                candles = (candles + next).takeLast(20)
                if (correct) wins++ else losses++
                lastResult = correct
                awaitingReveal = false
                pendingGuess = null
            }
            CircularProgressIndicator(color = BrandGreen, modifier = Modifier.size(28.dp))
        }

        lastResult?.let { correct ->
            Spacer(Modifier.height(12.dp))
            Text(
                if (correct) (if (language == "ur") "صحیح اندازہ! 🎉" else "Correct guess! 🎉")
                else (if (language == "ur") "غلط اندازہ — یہ بالکل نارمل ہے" else "Wrong guess — totally normal"),
                color = if (correct) BrandGreen else BrandSilverDim,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().background(PanelDark, RoundedCornerShape(12.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (language == "ur") "یہ ایک بے ترتیب تشبیہ ہے — حقیقی مارکیٹ سے مختلف ہو سکتی ہے۔"
                else "This is a randomized simulation — real markets can behave differently.",
                color = BrandSilverDim, fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ScoreCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = BrandSilverDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CandleChart(candles: List<DemoCandle>) {
    val allValues = candles.flatMap { listOf(it.high, it.low) }
    val maxV = allValues.maxOrNull() ?: 1f
    val minV = allValues.minOrNull() ?: 0f
    val range = max(maxV - minV, 0.01f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val candleWidth = size.width / candles.size
        candles.forEachIndexed { i, c ->
            val xCenter = i * candleWidth + candleWidth / 2
            val color = if (c.isBullish) Color(0xFF34E39A) else Color(0xFFFF5C6A)

            fun yFor(v: Float) = size.height - ((v - minV) / range) * size.height

            drawLine(
                color = color,
                start = Offset(xCenter, yFor(c.high)),
                end = Offset(xCenter, yFor(c.low)),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            val bodyTop = yFor(max(c.open, c.close))
            val bodyBottom = yFor(min(c.open, c.close))
            drawRect(
                color = color,
                topLeft = Offset(xCenter - candleWidth * 0.3f, bodyTop),
                size = androidx.compose.ui.geometry.Size(candleWidth * 0.6f, max(bodyBottom - bodyTop, 2f))
            )
        }
    }
}

private fun generateInitialCandles(): List<DemoCandle> {
    var price = 100f
    val list = mutableListOf<DemoCandle>()
    repeat(15) {
        val open = price
        val close = open + Random.nextFloat() * 6f - 3f
        val high = max(open, close) + Random.nextFloat() * 2f
        val low = min(open, close) - Random.nextFloat() * 2f
        list.add(DemoCandle(open, close, high, low))
        price = close
    }
    return list
}

private fun generateNextCandle(last: DemoCandle): DemoCandle {
    val open = last.close
    val close = open + Random.nextFloat() * 6f - 3f
    val high = max(open, close) + Random.nextFloat() * 2f
    val low = min(open, close) - Random.nextFloat() * 2f
    return DemoCandle(open, close, high, low)
}
