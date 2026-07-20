package com.mitv.trademaster.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.ui.theme.*

private data class TourStep(
    val icon: ImageVector,
    val titleEn: String,
    val titleUr: String,
    val bodyEn: String,
    val bodyUr: String,
    val accent: Color,
)

private fun tourSteps(accentDefault: Color) = listOf(
    TourStep(
        icon = Icons.Filled.RocketLaunch,
        titleEn = "Welcome to MI Trade Master",
        titleUr = "MI Trade Master میں خوش آمدید",
        bodyEn = "A quick 30-second tour of everything you can do here — then you're on your own.",
        bodyUr = "یہاں جو کچھ آپ کر سکتے ہیں اس کا ایک مختصر 30 سیکنڈ کا دورہ — پھر آپ خود سے استعمال کر سکتے ہیں۔",
        accent = accentDefault,
    ),
    TourStep(
        icon = Icons.Filled.Insights,
        titleEn = "Analyzer",
        titleUr = "اینالائزر",
        bodyEn = "Upload a chart screenshot and get an instant educational breakdown — trend strength, pattern signals, and a plain-English explanation.",
        bodyUr = "چارٹ کا اسکرین شاٹ اپلوڈ کریں اور فوری تعلیمی تجزیہ حاصل کریں — رجحان کی طاقت، پیٹرن سگنلز، اور آسان وضاحت۔",
        accent = Color(0xFF4FD1C5),
    ),
    TourStep(
        icon = Icons.Filled.School,
        titleEn = "Learn",
        titleUr = "سیکھیں",
        bodyEn = "Structured courses from beginner to pro. Finish every lesson in a course, pass its quiz, and the next course unlocks.",
        bodyUr = "ابتدائی سے پرو تک منظم کورسز۔ کورس کا ہر سبق مکمل کریں، اس کا کوئز پاس کریں، اور اگلا کورس کھل جائے گا۔",
        accent = BrandGreen,
    ),
    TourStep(
        icon = Icons.Filled.CandlestickChart,
        titleEn = "Practice",
        titleUr = "مشق",
        bodyEn = "Risk-free demo trading. Test your skills on simulated candles before you ever risk real money.",
        bodyUr = "بغیر خطرے کے ڈیمو ٹریڈنگ۔ اصل پیسے کا خطرہ مول لینے سے پہلے سمولیٹڈ کینڈلز پر اپنی مہارت آزمائیں۔",
        accent = Color(0xFFE3B934),
    ),
    TourStep(
        icon = Icons.Filled.MoreHoriz,
        titleEn = "More",
        titleUr = "مزید",
        bodyEn = "Support chat, your account, settings, and the spiritual corner all live here. You're all set — let's go!",
        bodyUr = "سپورٹ چیٹ، آپ کا اکاؤنٹ، سیٹنگز، اور روحانی گوشہ سب یہاں موجود ہیں۔ آپ تیار ہیں — چلیں!",
        accent = BrandSilver,
    ),
)

/**
 * Full-screen one-time walkthrough shown the first time a new user reaches
 * the main app shell. Purely a static explainer overlay (doesn't try to
 * spotlight-cutout actual nav bar positions, which would be fragile across
 * screen sizes) — five short cards the user taps through, each describing
 * one tab. Skippable at any point; either way it's marked seen so it never
 * appears again for this device.
 */
@Composable
fun AppTourOverlay(language: String, onFinished: () -> Unit) {
    val steps = remember { tourSteps(BrandGreen) }
    var stepIndex by remember { mutableStateOf(0) }
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()
    val step = steps[stepIndex]

    val progress by animateFloatAsState(
        targetValue = (stepIndex + 1f) / steps.size,
        animationSpec = tween(300),
        label = "tourProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip, top-right
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    tapFeedback()
                    onFinished()
                }) {
                    Text(
                        if (language == "ur") "چھوڑیں" else "Skip",
                        color = BrandSilverDim, fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = stepIndex,
                transitionSpec = { (fadeIn(tween(250)) togetherWith fadeOut(tween(150))) },
                label = "tourStep"
            ) { idx ->
                val s = steps[idx]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(s.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(s.icon, contentDescription = null, tint = s.accent, modifier = Modifier.size(42.dp))
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(
                        if (language == "ur") s.titleUr else s.titleEn,
                        color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        if (language == "ur") s.bodyUr else s.bodyEn,
                        color = BrandSilverDim, fontSize = 14.sp, lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // Progress dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.indices.forEach { i ->
                    val isActive = i == stepIndex
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (isActive) step.accent else LineSubtle)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (stepIndex > 0) {
                    OutlinedButton(
                        onClick = { tapFeedback(); stepIndex-- },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandSilver),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LineSubtle),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text(if (language == "ur") "پیچھے" else "Back", fontSize = 14.sp)
                    }
                }

                Button(
                    onClick = {
                        tapFeedback()
                        if (stepIndex < steps.lastIndex) stepIndex++ else onFinished()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = step.accent, contentColor = Color(0xFF04120B)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Text(
                        if (stepIndex < steps.lastIndex) (if (language == "ur") "اگلا" else "Next")
                        else (if (language == "ur") "شروع کریں" else "Get Started"),
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
