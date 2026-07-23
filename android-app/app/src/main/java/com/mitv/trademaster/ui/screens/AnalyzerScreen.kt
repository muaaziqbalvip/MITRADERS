package com.mitv.trademaster.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mitv.trademaster.analysis.AnnotatedChartExporter
import com.mitv.trademaster.analysis.ChartAnalyzer
import com.mitv.trademaster.analysis.Direction
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AnalyzerScreen(language: String = "en") {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<com.mitv.trademaster.analysis.AnalysisResult?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var candleIntervalInput by remember { mutableStateOf("") }
    var tradeDurationInput by remember { mutableStateOf("") }
    var capturedScreenBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isWaitingForScreenshot by remember { mutableStateOf(false) }
    var analyzedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var exportedUri by remember { mutableStateOf<Uri?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    // While waiting for a screenshot (user just granted screen-capture
    // permission or switched away to their chart app), poll for the next
    // frame that lands. Stops as soon as one arrives or the screen leaves
    // composition.
    LaunchedEffect(isWaitingForScreenshot) {
        if (!isWaitingForScreenshot) return@LaunchedEffect
        repeat(40) { // up to ~20 seconds
            kotlinx.coroutines.delay(500)
            val frame = com.mitv.trademaster.overlay.ScreenCaptureService.latestFrame
            if (frame != null) {
                capturedScreenBitmap = frame
                imageUri = null
                result = null
                errorMsg = null
                isWaitingForScreenshot = false
                return@LaunchedEffect
            }
        }
        isWaitingForScreenshot = false
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri = uri; capturedScreenBitmap = null; result = null; errorMsg = null
    }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingCameraUri != null) {
            imageUri = pendingCameraUri; capturedScreenBitmap = null; result = null; errorMsg = null
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createCameraCaptureUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val uri = createCameraCaptureUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(if (language == "ur") "چارٹ اینالائزر" else "Chart Analyzer", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (language == "ur") "آپ کے چارٹ کا تفصیلی تعلیمی پیٹرن جائزہ" else "Detailed educational pattern breakdown of your chart",
            color = BrandSilverDim, fontSize = 12.sp
        )

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(PanelDark),
            contentAlignment = Alignment.Center
        ) {
            if (capturedScreenBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = capturedScreenBitmap!!.asImageBitmap(), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            } else if (imageUri != null) {
                AsyncImage(model = imageUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (language == "ur") "چارٹ اسکرین شاٹ منتخب کرنے کے لیے نیچے ٹیپ کریں" else "Tap below to select a chart screenshot",
                        color = BrandSilverDim, fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { tapFeedback(); pickImageLauncher.launch("image/*") },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandSilver),
                border = androidx.compose.foundation.BorderStroke(1.dp, LineSubtle),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (language == "ur") "گیلری" else "Gallery", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = { tapFeedback(); launchCamera() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandSilver),
                border = androidx.compose.foundation.BorderStroke(1.dp, LineSubtle),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (language == "ur") "کیمرہ" else "Camera", fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                tapFeedback()
                if (!android.provider.Settings.canDrawOverlays(context) || !com.mitv.trademaster.overlay.ScreenCaptureService.isActive.value) {
                    val consentIntent = android.content.Intent(context, com.mitv.trademaster.overlay.ScreenCaptureConsentActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(consentIntent)
                    isWaitingForScreenshot = true
                } else {
                    val frame = com.mitv.trademaster.overlay.ScreenCaptureService.latestFrame
                    if (frame != null) {
                        capturedScreenBitmap = frame
                        imageUri = null
                        result = null
                        errorMsg = null
                    } else {
                        isWaitingForScreenshot = true
                    }
                }
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandGreen),
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandGreenDim),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.ScreenshotMonitor, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (language == "ur") "اسکرین شاٹ لیں (براہ راست)" else "Capture Screen (Direct)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        if (isWaitingForScreenshot) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (language == "ur") "دوسری اسکرین پر جائیں جہاں چارٹ نظر آتا ہے، پھر واپس آئیں — اسکرین شاٹ خودکار لیا جائے گا۔"
                else "Switch to the screen showing your chart, then come back — the screenshot will be captured automatically.",
                color = BrandSilverDim, fontSize = 11.sp, lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---------- Time-window inputs: candle interval + trade duration ----------
        Text(
            if (language == "ur") "ٹائم فریم کی تفصیل (اختیاری، لیکن تجویز کے لیے تجویز کردہ)" else "Time Window (optional, but recommended for a trade suggestion)",
            color = BrandSilverDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = candleIntervalInput,
                onValueChange = { v -> candleIntervalInput = v.filter { it.isDigit() }.take(3) },
                label = { Text(if (language == "ur") "کینڈل کتنے منٹ کی؟" else "Candle interval (min)", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandGreen, unfocusedBorderColor = LineSubtle,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BrandGreen,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = tradeDurationInput,
                onValueChange = { v -> tradeDurationInput = v.filter { it.isDigit() }.take(3) },
                label = { Text(if (language == "ur") "ٹریڈ کتنے منٹ کی؟" else "Trade duration (min)", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandGreen, unfocusedBorderColor = LineSubtle,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BrandGreen,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                tapFeedback()
                if (imageUri == null && capturedScreenBitmap == null) return@Button
                errorMsg = null
                isAnalyzing = true
                val candleInterval = candleIntervalInput.toIntOrNull()
                val tradeDuration = tradeDurationInput.toIntOrNull()
                scope.launch {
                    try {
                        val bitmap = capturedScreenBitmap ?: withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(imageUri!!)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                        }
                        if (bitmap == null) {
                            errorMsg = if (language == "ur") "تصویر نہیں پڑھی جا سکی" else "Could not read image"
                        } else {
                            val r = withContext(Dispatchers.Default) { ChartAnalyzer.analyze(bitmap, candleInterval, tradeDuration) }
                            result = r
                            analyzedBitmap = bitmap
                            exportedUri = null
                            authRepo.currentUser?.uid?.let { uid -> scope.launch { runCatching { firestoreRepo.incrementAnalysesRun(uid) } } }
                        }
                    } catch (e: Exception) {
                        errorMsg = (if (language == "ur") "تجزیہ ناکام: " else "Analysis failed: ") + e.message
                    } finally {
                        isAnalyzing = false
                    }
                }
            },
            enabled = (imageUri != null || capturedScreenBitmap != null) && !isAnalyzing,
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isAnalyzing) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
            else Text(if (language == "ur") "تجزیہ کریں" else "Analyze", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        errorMsg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = BrandRed, fontSize = 12.sp)
        }

        result?.let { r ->
            Spacer(Modifier.height(18.dp))
            NextCandlePredictorCircle(r, language)
            Spacer(Modifier.height(14.dp))
            DownloadAnalysisButton(
                language = language,
                isExporting = isExporting,
                exportedUri = exportedUri,
                onDownload = {
                    tapFeedback()
                    val bmp = analyzedBitmap ?: return@DownloadAnalysisButton
                    isExporting = true
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) { AnnotatedChartExporter.export(context, bmp, r) }
                        exportedUri = uri
                        isExporting = false
                        if (uri != null) {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Save / Share Analysis"))
                        }
                    }
                }
            )
            Spacer(Modifier.height(14.dp))
            LeanCard(r, language)
            r.tradeSuggestion?.let { suggestion ->
                Spacer(Modifier.height(14.dp))
                TradeSuggestionCard(suggestion, language)
            }
            if (r.matchedStrategies.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                StrategyMatchesCard(r.matchedStrategies, language)
            }
            Spacer(Modifier.height(14.dp))
            DetailedSignalPanel(r, language)
        }

        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun StrategyMatchesCard(strategies: List<com.mitv.trademaster.analysis.StrategyMatch>, language: String) {
    Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Psychology, contentDescription = null, tint = Color(0xFFE3B934), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (language == "ur") "مماثل حکمت عملیاں" else "Matching Strategies", color = Color(0xFFE3B934), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            strategies.forEachIndexed { idx, s ->
                if (idx > 0) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = LineSubtle)
                    Spacer(Modifier.height(10.dp))
                }
                val dirColor = when (s.direction) {
                    Direction.UP -> BrandGreen
                    Direction.DOWN -> BrandRed
                    Direction.NEUTRAL -> BrandSilverDim
                }
                Row(verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.padding(top = 3.dp).size(8.dp).clip(CircleShape).background(dirColor))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if (language == "ur") s.nameUr else s.nameEn, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(if (language == "ur") s.descriptionUr else s.descriptionEn, color = BrandSilverDim, fontSize = 11.5.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeSuggestionCard(s: com.mitv.trademaster.analysis.TradeSuggestion, language: String) {
    val (color, icon, dirLabel) = when (s.direction) {
        Direction.UP -> Triple(BrandGreen, Icons.Filled.TrendingUp, if (language == "ur") "اوپر" else "UP")
        Direction.DOWN -> Triple(BrandRed, Icons.Filled.TrendingDown, if (language == "ur") "نیچے" else "DOWN")
        Direction.NEUTRAL -> Triple(BrandSilverDim, Icons.Filled.TrendingFlat, if (language == "ur") "غیر واضح" else "UNCLEAR")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (language == "ur") "اگلے ${s.tradeDurationMinutes} منٹ کے لیے تجویز" else "Suggestion for the Next ${s.tradeDurationMinutes} Minutes",
                color = BrandSilverDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(dirLabel, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(
                        (if (language == "ur") "اعتماد: " else "Confidence: ") + "${s.confidencePercent}%",
                        color = BrandSilver, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${s.candleIntervalMinutes}m ${if (language == "ur") "کینڈل" else "candle"}",
                        color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { s.confidencePercent / 100f },
                color = color, trackColor = LineSubtle,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(14.dp))
            Text(
                if (language == "ur") s.reasoningUrdu else s.reasoning,
                color = BrandSilverDim, fontSize = 12.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (language == "ur") "یہ ایک تعلیمی تخمینہ ہے، ضمانت نہیں۔ اپنا رسک خود سنبھالیں۔"
                else "This is an educational estimate, not a guarantee. Manage your own risk.",
                color = BrandSilverDim.copy(alpha = 0.6f), fontSize = 10.sp, lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun LeanCard(r: com.mitv.trademaster.analysis.AnalysisResult, language: String) {
    val (color, icon, label) = when (r.direction) {
        Direction.UP -> Triple(BrandGreen, Icons.Filled.TrendingUp, if (language == "ur") "تعلیمی رجحان: اوپر" else "Educational Lean: UP")
        Direction.DOWN -> Triple(BrandRed, Icons.Filled.TrendingDown, if (language == "ur") "تعلیمی رجحان: نیچے" else "Educational Lean: DOWN")
        Direction.NEUTRAL -> Triple(BrandSilverDim, Icons.Filled.TrendingFlat, if (language == "ur") "کوئی واضح رجحان نہیں" else "No Clear Lean")
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
private fun DetailedSignalPanel(r: com.mitv.trademaster.analysis.AnalysisResult, language: String) {
    val (color, label) = when (r.direction) {
        Direction.UP -> BrandGreen to (if (language == "ur") "تیزی کا رجحان" else "Bullish Lean")
        Direction.DOWN -> BrandRed to (if (language == "ur") "مندی کا رجحان" else "Bearish Lean")
        Direction.NEUTRAL -> BrandSilverDim to (if (language == "ur") "غیر جانبدار / کوئی واضح رجحان نہیں" else "Neutral / No Clear Lean")
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
            Text(if (language == "ur") "رجحان کی طاقت" else "Trend Strength", color = BrandSilverDim, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { r.trendStrengthPercent / 100f },
                color = color, trackColor = LineSubtle,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(4.dp))
            Text("${r.trendStrengthPercent}%", color = BrandSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.height(18.dp))
            Text(if (language == "ur") "سگنل کی تفصیل" else "Signal Breakdown", color = BrandSilverDim, fontSize = 10.sp)
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

            Text(if (language == "ur") "مکمل وضاحت" else "Full Explanation", color = BrandSilverDim, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            Text(r.explanation, color = BrandSilverDim, fontSize = 12.sp, lineHeight = 18.sp)

            Spacer(Modifier.height(14.dp))
            Text(r.disclaimer, color = BrandSilverDim.copy(alpha = 0.6f), fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

/**
 * The centerpiece "analyzer brain" visual: a large circular gauge showing
 * the predicted NEXT candle direction and confidence percentage, with a
 * slowly rotating scan ring to give it a live "analyzing" feel. This is
 * the single most important number in the screen — everything else
 * (patterns, strategies, signals) is supporting detail for this call.
 */
@Composable
private fun NextCandlePredictorCircle(r: com.mitv.trademaster.analysis.AnalysisResult, language: String) {
    val (color, icon, dirLabel) = when (r.nextCandlePrediction) {
        Direction.UP -> Triple(BrandGreen, Icons.Filled.TrendingUp, if (language == "ur") "اوپر" else "UP")
        Direction.DOWN -> Triple(BrandRed, Icons.Filled.TrendingDown, if (language == "ur") "نیچے" else "DOWN")
        Direction.NEUTRAL -> Triple(BrandSilverDim, Icons.Filled.TrendingFlat, if (language == "ur") "غیر واضح" else "NEUTRAL")
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scanRing")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(3200, easing = LinearEasing)),
        label = "rotation"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = r.nextCandleConfidencePercent / 100f,
        animationSpec = tween(900), label = "progress"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = PanelDark),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (language == "ur") "اے آئی نیکسٹ کینڈل پریڈکٹر" else "AI NEXT CANDLE PREDICTOR",
                color = BrandSilverDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
            Spacer(Modifier.height(18.dp))

            Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                // Rotating dashed scan ring — purely decorative "AI analyzing" feel.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    rotate(rotation) {
                        drawArc(
                            color = color.copy(alpha = 0.25f),
                            startAngle = 0f, sweepAngle = 100f, useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = color.copy(alpha = 0.25f),
                            startAngle = 180f, sweepAngle = 100f, useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                // Confidence progress ring.
                Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    drawArc(
                        color = LineSubtle,
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = color,
                        startAngle = -90f, sweepAngle = 360f * animatedProgress, useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                // Center content: icon, direction, confidence %.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(dirLabel, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(
                        "${r.nextCandleConfidencePercent}% " + (if (language == "ur") "اعتماد" else "confidence"),
                        color = BrandSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                r.predictedCloseRelativeToOpen,
                color = BrandSilverDim, fontSize = 12.5.sp, lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f)
            )

            if (r.detectedPatterns.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(r.detectedPatterns) { p ->
                        val pColor = when (p.nextCandleBias) {
                            Direction.UP -> BrandGreen
                            Direction.DOWN -> BrandRed
                            Direction.NEUTRAL -> BrandSilverDim
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(pColor.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                if (language == "ur") p.nameUr else p.nameEn,
                                color = pColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadAnalysisButton(
    language: String,
    isExporting: Boolean,
    exportedUri: Uri?,
    onDownload: () -> Unit,
) {
    Button(
        onClick = onDownload,
        enabled = !isExporting,
        colors = ButtonDefaults.buttonColors(containerColor = PanelDark, contentColor = BrandGreen),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandGreenDim),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp)
    ) {
        if (isExporting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BrandGreen, strokeWidth = 2.dp)
        } else {
            Icon(
                if (exportedUri != null) Icons.Filled.Share else Icons.Filled.Download,
                contentDescription = null, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (exportedUri != null) {
                    if (language == "ur") "دوبارہ شیئر کریں" else "Share Again"
                } else {
                    if (language == "ur") "تجزیہ شدہ تصویر ڈاؤن لوڈ کریں" else "Download Analyzed Chart Image"
                },
                fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Creates a content:// URI (via FileProvider) in the app's cache dir for the camera to write the captured photo into. */
private fun createCameraCaptureUri(context: android.content.Context): Uri {
    val cacheDir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
    val file = java.io.File(cacheDir, "chart_capture_${System.currentTimeMillis()}.jpg")
    return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
