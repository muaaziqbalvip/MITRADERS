package com.mitv.trademaster.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
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
import com.mitv.trademaster.analysis.Confidence
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

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).padding(20.dp)) {
        Text("Chart Analyzer", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Educational pattern analysis on your chart screenshots", color = BrandSilverDim, fontSize = 12.sp)

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(PanelDark),
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(model = imageUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Tap below to select a chart screenshot", color = BrandSilverDim, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { pickImageLauncher.launch("image/*") },
            colors = ButtonDefaults.buttonColors(containerColor = PanelDark, contentColor = BrandSilver),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Select Chart Screenshot") }

        Spacer(Modifier.height(12.dp))

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
                            authRepo.currentUser?.uid?.let { uid ->
                                scope.launch { runCatching { firestoreRepo.incrementAnalysesRun(uid) } }
                            }
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
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isAnalyzing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
            else Text("Analyze Chart", fontWeight = FontWeight.Bold)
        }

        errorMsg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = BrandRed, fontSize = 12.sp)
        }

        result?.let { r ->
            Spacer(Modifier.height(20.dp))
            ResultCard(r)
        }
    }
}

@Composable
private fun ResultCard(r: com.mitv.trademaster.analysis.AnalysisResult) {
    val (color, label) = when (r.direction) {
        Direction.UP -> BrandGreen to "Bullish Lean"
        Direction.DOWN -> BrandRed to "Bearish Lean"
        Direction.NEUTRAL -> BrandSilverDim to "Neutral / No Clear Lean"
    }

    Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(10.dp))
                Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(r.pattern, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row {
                Text("Confidence: ", color = BrandSilverDim, fontSize = 12.sp)
                Text(r.confidence.name, color = BrandSilver, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(r.explanation, color = BrandSilverDim, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(14.dp))
            Text(r.disclaimer, color = BrandSilverDim.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}
