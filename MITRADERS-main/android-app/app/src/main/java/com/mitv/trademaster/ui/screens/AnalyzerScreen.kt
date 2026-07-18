package com.mitv.trademaster.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mitv.trademaster.analysis.ChartAnalyzer
import com.mitv.trademaster.analysis.Direction
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AnalyzerScreen() {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<com.mitv.trademaster.analysis.AnalysisResult?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri = uri; result = null; errorMsg = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Chart Analyzer", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Detailed educational pattern breakdown of your chart", color = BrandSilverDim, fontSize = 12.sp)

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(PanelDark),
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(model = imageUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Tap below to select a chart screenshot", color = BrandSilverDim, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { pickImageLauncher.launch("image/*") },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandSilver),
                border = androidx.compose.foundation.BorderStroke(1.dp, LineSubtle),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) { Text("Select Screenshot", fontSize = 13.sp) }

            Button(
                onClick = {
                    if (imageUri == null) return@Button
                    errorMsg = null
                    isAnalyzing = true
                    scope.launch {
                        try {
                            val bitmap = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(imageUri!!)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                            }
                            if (bitmap == null) {
                                errorMsg = "Could not read image"
                            } else {
                                val r = withContext(Dispatchers.Default) { ChartAnalyzer.analyze(bitmap) }
                                result = r
                                authRepo.currentUser?.uid?.let { uid -> scope.launch { runCatching { firestoreRepo.incrementAnalysesRun(uid) } } }
                            }
                        } catch (e: Exception) {
                            errorMsg = "Analysis failed: ${e.message}"
                        } finally {
                            isAnalyzing = false
                        }
                    }
                },
                enabled = imageUri != null && !isAnalyzing,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (isAnalyzing) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
                else Text("Analyze", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        errorMsg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = BrandRed, fontSize = 12.sp)
        }

        result?.let { r ->
            Spacer(Modifier.height(18.dp))
            LeanCard(r)
            Spacer(Modifier.height(14.dp))
            DetailedSignalPanel(r)
        }

        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun LeanCard(r: com.mitv.trademaster.analysis.AnalysisResult) {
    val (color, icon, label) = when (r.direction) {
        Direction.UP -> Triple(BrandGreen, Icons.Filled.TrendingUp, "Educational Lean: UP")
        Direction.DOWN -> Triple(BrandRed, Icons.Filled.TrendingDown, "Educational Lean: DOWN")
        Direction.NEUTRAL -> Triple(BrandSilverDim, Icons.Filled.TrendingFlat, "No Clear Lean")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(r.leanStatement, color = BrandSilver, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun DetailedSignalPanel(r: com.mitv.trademaster.analysis.AnalysisResult) {
    val (color, label) = when (r.direction) {
        Direction.UP -> BrandGreen to "Bullish Lean"
        Direction.DOWN -> BrandRed to "Bearish Lean"
        Direction.NEUTRAL -> BrandSilverDim to "Neutral / No Clear Lean"
    }

    Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(10.dp))
                Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(r.confidence.name, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Trend Strength", color = BrandSilverDim, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { r.trendStrengthPercent / 100f },
                color = color, trackColor = LineSubtle,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(4.dp))
            Text("${r.trendStrengthPercent}%", color = BrandSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.height(18.dp))
            Text("Signal Breakdown", color = BrandSilverDim, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            r.signals.forEach { signal ->
                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(signal, color = BrandSilver, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = LineSubtle)
            Spacer(Modifier.height(14.dp))

            Text("Full Explanation", color = BrandSilverDim, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            Text(r.explanation, color = BrandSilverDim, fontSize = 12.sp, lineHeight = 18.sp)

            Spacer(Modifier.height(14.dp))
            Text(r.disclaimer, color = BrandSilverDim.copy(alpha = 0.6f), fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}
