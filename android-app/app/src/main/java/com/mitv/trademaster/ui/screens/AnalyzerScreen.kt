package com.mitv.trademaster.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.PhoneAndroid
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
import com.mitv.trademaster.R
import com.mitv.trademaster.analysis.ChartAnalyzer
import com.mitv.trademaster.analysis.Confidence
import com.mitv.trademaster.analysis.Direction
import com.mitv.trademaster.data.LicenseRepository
import com.mitv.trademaster.network.ApiClient
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

private enum class Mode { OFFLINE, ONLINE }

private data class UiResult(
    val direction: Direction,
    val confidence: Confidence,
    val pattern: String,
    val explanation: String,
    val disclaimer: String,
)

@Composable
fun AnalyzerScreen() {
    val context = LocalContext.current
    val repo = remember { LicenseRepository(context) }
    val licenseState by repo.licenseState.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(Mode.OFFLINE) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UiResult?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
        result = null
        errorMsg = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .padding(20.dp)
    ) {
        Text(context.getString(R.string.analyzer_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDark, RoundedCornerShape(14.dp))
                .padding(4.dp)
        ) {
            ModeChip(
                modifier = Modifier.weight(1f),
                selected = mode == Mode.OFFLINE,
                icon = Icons.Filled.PhoneAndroid,
                label = context.getString(R.string.analyzer_mode_offline),
                onClick = { mode = Mode.OFFLINE }
            )
            ModeChip(
                modifier = Modifier.weight(1f),
                selected = mode == Mode.ONLINE,
                icon = Icons.Filled.CloudQueue,
                label = context.getString(R.string.analyzer_mode_online),
                onClick = { mode = Mode.ONLINE }
            )
        }

        Spacer(Modifier.height(20.dp))

        // Image preview / picker
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PanelDark),
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
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
        ) {
            Text("Select Chart Screenshot")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (imageUri == null) return@Button
                errorMsg = null
                isAnalyzing = true
                scope.launch {
                    try {
                        if (mode == Mode.OFFLINE) {
                            val bitmap = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(imageUri!!)?.use {
                                    android.graphics.BitmapFactory.decodeStream(it)
                                }
                            }
                            if (bitmap == null) {
                                errorMsg = "Could not read image"
                            } else {
                                val r = withContext(Dispatchers.Default) { ChartAnalyzer.analyze(bitmap) }
                                result = UiResult(r.direction, r.confidence, r.pattern, r.explanation, r.disclaimer)
                            }
                        } else {
                            val licenseKey = licenseState?.licenseKey ?: ""
                            if (licenseKey.isBlank()) {
                                errorMsg = "No active license found"
                            } else {
                                val file = withContext(Dispatchers.IO) { uriToTempFile(context, imageUri!!) }
                                val reqFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                                val part = MultipartBody.Part.createFormData("image", file.name, reqFile)
                                val keyBody = licenseKey.toRequestBody("text/plain".toMediaTypeOrNull())
                                val response = ApiClient.api.analyzeChart(keyBody, part)
                                if (response.isSuccessful && response.body() != null) {
                                    val b = response.body()!!
                                    val dir = when (b.direction_lean) {
                                        "up" -> Direction.UP
                                        "down" -> Direction.DOWN
                                        else -> Direction.NEUTRAL
                                    }
                                    val conf = when (b.confidence) {
                                        "high" -> Confidence.HIGH
                                        "medium" -> Confidence.MEDIUM
                                        else -> Confidence.LOW
                                    }
                                    result = UiResult(dir, conf, b.pattern, b.explanation, b.disclaimer)
                                } else {
                                    errorMsg = "Server analysis failed. Try offline mode."
                                }
                            }
                        }
                    } catch (e: Exception) {
                        errorMsg = "Analysis failed: ${e.message ?: "network error"}"
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
            if (isAnalyzing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
            } else {
                Text(context.getString(R.string.analyzer_capture_btn), fontWeight = FontWeight.Bold)
            }
        }

        errorMsg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = BrandRed, fontSize = 12.sp)
        }

        result?.let { r ->
            Spacer(Modifier.height(20.dp))
            ResultCard(r, context)
        }
    }
}

@Composable
private fun ModeChip(modifier: Modifier, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BrandGreen.copy(alpha = 0.15f) else Color.Transparent)
            .padding(vertical = 10.dp)
            .then(Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.TextButton(onClick = onClick) {
            Icon(icon, contentDescription = null, tint = if (selected) BrandGreen else BrandSilverDim, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (selected) BrandGreen else BrandSilverDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ResultCard(r: UiResult, context: android.content.Context) {
    val (color, label) = when (r.direction) {
        Direction.UP -> BrandGreen to context.getString(R.string.analyzer_direction_up)
        Direction.DOWN -> BrandRed to context.getString(R.string.analyzer_direction_down)
        Direction.NEUTRAL -> BrandSilverDim to context.getString(R.string.analyzer_direction_neutral)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = PanelDark),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(10.dp))
                Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(12.dp))
            Text(r.pattern, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

            Spacer(Modifier.height(8.dp))
            Row {
                Text(context.getString(R.string.analyzer_confidence) + ": ", color = BrandSilverDim, fontSize = 12.sp)
                Text(r.confidence.name, color = BrandSilver, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))
            Text(r.explanation, color = BrandSilverDim, fontSize = 12.sp, lineHeight = 18.sp)

            Spacer(Modifier.height(14.dp))
            Text(r.disclaimer, color = BrandSilverDim.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

private fun uriToTempFile(context: android.content.Context, uri: Uri): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    val file = File.createTempFile("chart_", ".jpg", context.cacheDir)
    FileOutputStream(file).use { out ->
        inputStream?.copyTo(out)
    }
    return file
}
