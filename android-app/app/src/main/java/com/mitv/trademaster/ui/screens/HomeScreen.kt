package com.mitv.trademaster.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.model.StudentProfile
import com.mitv.trademaster.overlay.OverlayBubbleService
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(language: String) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<StudentProfile?>(null) }
    var overlayActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = authRepo.currentUser?.uid ?: return@LaunchedEffect
        profile = try { firestoreRepo.getStudentProfile(uid) } catch (e: Exception) { null }
        scope.launch { runCatching { firestoreRepo.updateLastActive(uid) } }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BgBlack).verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(PanelDark),
                contentAlignment = Alignment.Center
            ) {
                if (!profile?.photoUrl.isNullOrBlank()) {
                    AsyncImage(model = profile?.photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(profile?.studentName?.take(1)?.uppercase() ?: "S", color = BrandGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (language == "ur") "خوش آمدید" else "Welcome back", color = BrandSilverDim, fontSize = 12.sp)
                Text(profile?.studentName?.ifBlank { "Trader" } ?: "Trader", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Subscription status chip
        val isActive = profile?.subscriptionStatus == "active"
        Row(
            modifier = Modifier
                .background(if (isActive) BrandGreen.copy(alpha = 0.12f) else BrandRed.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isActive) BrandGreen else BrandRed))
            Spacer(Modifier.width(8.dp))
            Text(
                if (isActive) (if (language == "ur") "فعال رکنیت" else "Active Subscription")
                else (if (language == "ur") "رکنیت زیر التوا" else "Subscription Pending"),
                color = if (isActive) BrandGreen else BrandRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(20.dp))

        Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).background(Brush.linearGradient(listOf(BrandGreen.copy(alpha = 0.2f), Color.Transparent)), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Bolt, contentDescription = null, tint = BrandGreen) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (language == "ur") "فلوٹنگ اینالائزر" else "Floating Analyzer", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            if (overlayActive) (if (language == "ur") "فعال" else "Active — bubble is on screen") else (if (language == "ur") "غیر فعال" else "Inactive"),
                            color = if (overlayActive) BrandGreen else BrandSilverDim, fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!Settings.canDrawOverlays(context)) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                            return@Button
                        }
                        if (overlayActive) {
                            context.stopService(Intent(context, OverlayBubbleService::class.java))
                            overlayActive = false
                        } else {
                            context.startService(Intent(context, OverlayBubbleService::class.java))
                            overlayActive = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (overlayActive) BrandRed.copy(alpha = 0.15f) else BrandGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(if (overlayActive) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null, tint = if (overlayActive) BrandRed else Color(0xFF04120B))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (overlayActive) (if (language == "ur") "بند کریں" else "Stop Floating Analyzer") else (if (language == "ur") "شروع کریں" else "Start Floating Analyzer"),
                        color = if (overlayActive) BrandRed else Color(0xFF04120B), fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(modifier = Modifier.weight(1f), label = if (language == "ur") "مکمل اسباق" else "Lessons Done", value = (profile?.lessonsCompleted ?: 0).toString())
            StatCard(modifier = Modifier.weight(1f), label = if (language == "ur") "تجزیے" else "Analyses Run", value = (profile?.analysesRun ?: 0).toString())
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().background(PanelDark, RoundedCornerShape(12.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (language == "ur") "تعلیمی چارٹ تجزیہ — مالیاتی مشورہ نہیں" else "Educational chart analysis — not financial advice",
                color = BrandSilverDim, fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, color = BrandGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, color = BrandSilverDim, fontSize = 11.sp)
        }
    }
}
