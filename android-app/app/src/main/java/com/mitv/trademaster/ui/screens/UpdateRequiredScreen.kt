package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.update.ApkUpdater
import com.mitv.trademaster.update.UpdateInfo
import com.mitv.trademaster.ui.theme.*

@Composable
fun UpdateRequiredScreen(updateInfo: UpdateInfo, language: String = "en") {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var downloadedFile by remember { mutableStateOf<java.io.File?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PanelDarker, BgBlack)))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(BrandGreen.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(34.dp))
        }

        Spacer(Modifier.height(24.dp))
        Text(if (language == "ur") "اپڈیٹ ضروری ہے" else "Update Required", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (language == "ur") "نیا ورژن (${updateInfo.latestVersionName}) دستیاب ہے۔ جاری رکھنے کے لیے براہ کرم اپ ڈیٹ کریں۔"
            else "A new version (${updateInfo.latestVersionName}) is available. Please update to continue using MI Trade Master.",
            color = BrandSilverDim, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (updateInfo.releaseNotes.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (language == "ur") "کیا نیا ہے" else "What's new", color = BrandSilverDim, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(updateInfo.releaseNotes, color = BrandSilver, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        if (downloadedFile != null) {
            Button(
                onClick = { ApkUpdater.installApk(context, downloadedFile!!) },
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (language == "ur") "اپڈیٹ انسٹال کریں" else "Install Update", fontWeight = FontWeight.Bold) }
        } else if (isDownloading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                color = BrandGreen, trackColor = LineSubtle,
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text((if (language == "ur") "ڈاؤن لوڈ ہو رہا ہے... " else "Downloading... ") + "$progress%", color = BrandSilverDim, fontSize = 12.sp)
        } else {
            Button(
                onClick = {
                    if (updateInfo.apkUrl.isBlank()) {
                        errorMsg = if (language == "ur") "اپڈیٹ لنک سیٹ نہیں ہے۔ براہ کرم سپورٹ سے رابطہ کریں۔" else "Update link not configured. Please contact support."
                        return@Button
                    }
                    isDownloading = true
                    errorMsg = null
                    ApkUpdater.startDownload(
                        context = context,
                        apkUrl = updateInfo.apkUrl,
                        onProgress = { p -> progress = p },
                        onComplete = { file ->
                            isDownloading = false
                            if (file != null) {
                                downloadedFile = file
                            } else {
                                errorMsg = if (language == "ur") "ڈاؤن لوڈ ناکام ہو گیا۔ اپنا کنکشن چیک کریں اور دوبارہ کوشش کریں۔" else "Download failed. Please check your connection and try again."
                            }
                        }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (language == "ur") "اپڈیٹ ڈاؤن لوڈ کریں" else "Download Update", fontWeight = FontWeight.Bold) }
        }

        errorMsg?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = BrandRed, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            if (language == "ur") "آپ کا اکاؤنٹ، پیش رفت اور سیٹنگز محفوظ رہیں گی — یہ صرف ایپ کو اپڈیٹ کرتا ہے۔"
            else "Your account, progress, and settings are kept — this only updates the app itself.",
            color = BrandSilverDim.copy(alpha = 0.7f), fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
