package com.mitv.trademaster.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WhatsApp
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
fun PaymentScreen(studentName: String, onSubmitted: () -> Unit) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }
    val configRepo = remember { RemoteConfigRepository() }
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<com.mitv.trademaster.data.AppConfig?>(null) }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val pickScreenshot = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { screenshotUri = it }

    LaunchedEffect(Unit) {
        config = withContext(Dispatchers.IO) { configRepo.getConfig() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BgBlack).verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Icon(Icons.Filled.QrCode2, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text("Activate Your Subscription", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(
            "PKR ${config?.subscriptionPricePkr ?: 500} / month — full access to all lessons, analyzer, and practice tools",
            color = BrandSilverDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(20.dp))

        // QR Code
        Box(
            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(PanelDark),
            contentAlignment = Alignment.Center
        ) {
            if (!config?.paymentQrUrl.isNullOrBlank()) {
                AsyncImage(model = config?.paymentQrUrl, contentDescription = "Payment QR Code", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp))
            } else {
                Text("QR code not set yet\nContact support on WhatsApp", color = BrandSilverDim, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        Spacer(Modifier.height(24.dp))

        if (submitted) {
            Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Payment Submitted", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Our team will confirm your payment and activate your account shortly. Please be patient.",
                        color = BrandSilverDim, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Text("After paying, upload a screenshot of your payment below:", color = BrandSilverDim, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)).background(PanelDark),
                contentAlignment = Alignment.Center
            ) {
                if (screenshotUri != null) {
                    AsyncImage(model = screenshotUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Upload, contentDescription = null, tint = BrandSilverDim)
                        Spacer(Modifier.height(6.dp))
                        Text("Tap to select payment screenshot", color = BrandSilverDim, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { pickScreenshot.launch("image/*") }) {
                Text("Choose Screenshot", color = BrandGreen, fontSize = 13.sp)
            }

            errorMsg?.let {
                Text(it, color = BrandRed, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    val uid = authRepo.currentUser?.uid ?: return@Button
                    val uri = screenshotUri
                    if (uri == null) { errorMsg = "Please select a screenshot first"; return@Button }
                    isSubmitting = true
                    errorMsg = null
                    scope.launch {
                        try {
                            val uploadResult = withContext(Dispatchers.IO) { ImgBBUploader.uploadImage(context, uri) }
                            val url = uploadResult.getOrElse {
                                errorMsg = "Upload failed: ${it.message}"
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
                                )
                            )
                            isSubmitting = false
                            submitted = true
                        } catch (e: Exception) {
                            isSubmitting = false
                            errorMsg = "Could not submit: ${e.message}"
                        }
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
                else Text("Submit for Approval", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))

        val whatsapp = config?.whatsappNumber ?: "923062015326"
        OutlinedButton(
            onClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$whatsapp"))
                context.startActivity(intent)
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF25D366)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.WhatsApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Contact Support on WhatsApp", fontSize = 13.sp)
        }

        Spacer(Modifier.height(40.dp))
    }
}
