package com.mitv.trademaster.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Upload
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
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.RemoteConfigRepository
import com.mitv.trademaster.data.model.PaymentSubmission
import com.mitv.trademaster.network.ImgBBUploader
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PaymentScreen(studentName: String, language: String = "en", onSubmitted: () -> Unit) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }
    val configRepo = remember { RemoteConfigRepository() }
    val scope = rememberCoroutineScope()
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()

    var selectedMethod by remember { mutableStateOf("jazzcash") } // jazzcash | easypaisa
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val pickScreenshot = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { screenshotUri = it }

    val config by configRepo.observeConfig().collectAsState(initial = null)

    Column(
        modifier = Modifier.fillMaxSize().background(BgBlack).verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(BrandGreen.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.QrCode2, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (language == "ur") "اپنی سبسکرپشن فعال کریں" else "Activate Your Subscription",
            color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold
        )
        Text(
            if (language == "ur") "PKR ${config?.subscriptionPricePkr ?: 500} / ماہانہ — تمام اسباق، اینالائزر اور پریکٹس ٹولز تک مکمل رسائی"
            else "PKR ${config?.subscriptionPricePkr ?: 500} / month — full access to all lessons, analyzer, and practice tools",
            color = BrandSilverDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        // ---------- Payment method selector: JazzCash / EasyPaisa ----------
        Row(
            modifier = Modifier.fillMaxWidth().background(PanelDark, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PaymentMethodTab(
                label = "JazzCash",
                color = Color(0xFFE9192C),
                selected = selectedMethod == "jazzcash",
                modifier = Modifier.weight(1f)
            ) { tapFeedback(); selectedMethod = "jazzcash" }
            PaymentMethodTab(
                label = "EasyPaisa",
                color = Color(0xFF2FAE4E),
                selected = selectedMethod == "easypaisa",
                modifier = Modifier.weight(1f)
            ) { tapFeedback(); selectedMethod = "easypaisa" }
        }

        Spacer(Modifier.height(20.dp))

        val activeQrUrl = if (selectedMethod == "jazzcash") config?.jazzCashQrUrl else config?.easyPaisaQrUrl
        val activeNumber = if (selectedMethod == "jazzcash") config?.jazzCashNumber else config?.easyPaisaNumber
        val methodColor = if (selectedMethod == "jazzcash") Color(0xFFE9192C) else Color(0xFF2FAE4E)
        val methodLabel = if (selectedMethod == "jazzcash") "JazzCash" else "EasyPaisa"

        // QR Code for the selected method
        Box(
            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(PanelDark),
            contentAlignment = Alignment.Center
        ) {
            if (!activeQrUrl.isNullOrBlank()) {
                AsyncImage(model = activeQrUrl, contentDescription = "$methodLabel QR Code", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.QrCode2, contentDescription = null, tint = methodColor.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (language == "ur") "$methodLabel QR کوڈ ابھی سیٹ نہیں ہوا\nواٹس ایپ پر سپورٹ سے رابطہ کریں"
                        else "$methodLabel QR code not set yet\nContact support on WhatsApp",
                        color = BrandSilverDim, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        if (!activeNumber.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Smartphone, contentDescription = null, tint = methodColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("$methodLabel: $activeNumber", color = BrandSilver, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(24.dp))

        if (submitted) {
            Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(if (language == "ur") "ادائیگی جمع ہو گئی" else "Payment Submitted", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (language == "ur") "ہماری ٹیم آپ کی ادائیگی کی تصدیق کرے گی اور جلد آپ کا اکاؤنٹ فعال کر دے گی۔ براہ کرم صبر کریں۔"
                        else "Our team will confirm your payment and activate your account shortly. Please be patient.",
                        color = BrandSilverDim, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Text(
                if (language == "ur") "ادائیگی کے بعد نیچے اپنی ادائیگی کا اسکرین شاٹ اپلوڈ کریں:" else "After paying, upload a screenshot of your payment below:",
                color = BrandSilverDim, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)).background(PanelDark)
                    .clickable { tapFeedback(); pickScreenshot.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (screenshotUri != null) {
                    AsyncImage(model = screenshotUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Upload, contentDescription = null, tint = BrandSilverDim)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (language == "ur") "ادائیگی کا اسکرین شاٹ منتخب کرنے کے لیے ٹیپ کریں" else "Tap to select payment screenshot",
                            color = BrandSilverDim, fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { tapFeedback(); pickScreenshot.launch("image/*") }) {
                Text(if (language == "ur") "اسکرین شاٹ منتخب کریں" else "Choose Screenshot", color = BrandGreen, fontSize = 13.sp)
            }

            errorMsg?.let {
                Text(it, color = BrandRed, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    tapFeedback()
                    val uid = authRepo.currentUser?.uid ?: return@Button
                    val uri = screenshotUri
                    if (uri == null) { errorMsg = if (language == "ur") "براہ کرم پہلے اسکرین شاٹ منتخب کریں" else "Please select a screenshot first"; return@Button }
                    isSubmitting = true
                    errorMsg = null
                    scope.launch {
                        try {
                            val uploadResult = withContext(Dispatchers.IO) { ImgBBUploader.uploadImage(context, uri) }
                            val url = uploadResult.getOrElse {
                                errorMsg = (if (language == "ur") "اپلوڈ ناکام: " else "Upload failed: ") + it.message
                                isSubmitting = false
                                return@launch
                            }
                            firestoreRepo.submitPayment(
                                PaymentSubmission(
                                    uid = uid,
                                    studentName = studentName,
                                    screenshotUrl = url,
                                    submittedAt = System.currentTimeMillis(),
                                    status = "pending",
                                    adminNote = "Method: $methodLabel",
                                )
                            )
                            isSubmitting = false
                            submitted = true
                        } catch (e: Exception) {
                            isSubmitting = false
                            errorMsg = (if (language == "ur") "جمع نہیں ہو سکا: " else "Could not submit: ") + e.message
                        }
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
                else Text(if (language == "ur") "منظوری کے لیے جمع کریں" else "Submit for Approval", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))

        val whatsapp = config?.whatsappNumber ?: "923062015326"
        OutlinedButton(
            onClick = {
                tapFeedback()
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$whatsapp"))
                context.startActivity(intent)
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF25D366)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (language == "ur") "واٹس ایپ پر سپورٹ سے رابطہ کریں" else "Contact Support on WhatsApp", fontSize = 13.sp)
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PaymentMethodTab(label: String, color: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) color else BrandSilverDim,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
