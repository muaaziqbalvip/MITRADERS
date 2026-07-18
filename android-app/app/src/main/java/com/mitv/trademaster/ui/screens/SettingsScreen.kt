package com.mitv.trademaster.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.data.SessionRepository
import com.mitv.trademaster.overlay.ScreenCaptureConsentActivity
import com.mitv.trademaster.overlay.ScreenCaptureService
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(language: String, onLanguageChanged: (String) -> Unit) {
    val context = LocalContext.current
    val sessionRepo = remember { SessionRepository(context) }
    val session by sessionRepo.session.collectAsState(initial = com.mitv.trademaster.data.SessionState())
    val scope = rememberCoroutineScope()

    // Re-check permission states whenever this screen recomposes (e.g. after
    // returning from a system settings screen or consent dialog).
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var screenCaptureGranted by remember { mutableStateOf(ScreenCaptureService.latestFrame != null) }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(if (language == "ur") "سیٹنگز" else "Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // ---------- Permissions ----------
        Text(if (language == "ur") "اجازتیں" else "Permissions", color = BrandSilverDim, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        PermissionRow(
            icon = Icons.Filled.PictureInPicture,
            title = if (language == "ur") "دوسری ایپس پر ڈسپلے" else "Display Over Other Apps",
            subtitle = if (language == "ur") "فلوٹنگ اینالائزر بلبلے کے لیے درکار" else "Required for the floating analyzer bubble",
            granted = overlayGranted,
            onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
        )
        Spacer(Modifier.height(10.dp))

        PermissionRow(
            icon = Icons.Filled.Notifications,
            title = if (language == "ur") "نوٹیفیکیشنز" else "Notifications",
            subtitle = if (language == "ur") "اعلانات اور اپڈیٹس کے لیے" else "For announcements and update alerts",
            granted = notificationsGranted,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(intent)
                }
            }
        )
        Spacer(Modifier.height(10.dp))

        PermissionRow(
            icon = Icons.Filled.ScreenShare,
            title = if (language == "ur") "اسکرین کیپچر" else "Screen Capture",
            subtitle = if (language == "ur") "چارٹ اینالائزر پینل کے لیے درکار" else "Required for the Analyzer panel to read your screen",
            granted = screenCaptureGranted,
            onClick = {
                context.startActivity(Intent(context, ScreenCaptureConsentActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            }
        )

        Spacer(Modifier.height(10.dp))
        Text(
            if (language == "ur") "نوٹ: اسکرین کیپچر کی اجازت ہر بار ایپ دوبارہ شروع ہونے پر دوبارہ دینی پڑ سکتی ہے — یہ Android کا معمول کا رویہ ہے۔"
            else "Note: screen capture permission may need to be re-granted each time the app restarts — this is normal Android behavior for privacy.",
            color = BrandSilverDim.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 14.sp
        )

        Spacer(Modifier.height(28.dp))

        // ---------- Language ----------
        Text(if (language == "ur") "زبان" else "Language", color = BrandSilverDim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("en" to "English", "ur" to "اردو").forEach { (code, label) ->
                val selected = language == code
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) BrandGreen.copy(alpha = 0.15f) else PanelDark)
                        .clickable {
                            scope.launch { sessionRepo.setLanguage(code) }
                            onLanguageChanged(code)
                        }.padding(horizontal = 18.dp, vertical = 10.dp)
                ) { Text(label, color = if (selected) BrandGreen else BrandSilverDim, fontSize = 13.sp) }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ---------- Theme ----------
        Text(if (language == "ur") "تھیم رنگ" else "Theme Color", color = BrandSilverDim, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ThemeAccents.forEach { (key, colors) ->
                val (primary, _) = colors
                val selected = session.accentTheme == key
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(primary)
                        .clickable { scope.launch { sessionRepo.setAccentTheme(key) } },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF04120B))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (language == "ur") "نوٹ: تھیم رنگ ایپ ری اسٹارٹ پر مکمل طور پر لاگو ہوگا۔" else "Note: theme color fully applies after restarting the app.",
            color = BrandSilverDim.copy(alpha = 0.7f), fontSize = 10.sp
        )

        Spacer(Modifier.height(28.dp))

        // ---------- About ----------
        Text(if (language == "ur") "ایپ کے بارے میں" else "About", color = BrandSilverDim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("MI Trade Master", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text("Version ${com.mitv.trademaster.BuildConfig.VERSION_NAME}", color = BrandSilverDim, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (language == "ur") "MI Traders کی طرف سے تیار کردہ — بنیادی سے پرو ٹریڈر بننے تک کا سفر۔" else "Built by MI Traders — your journey from beginner to pro trader.",
                    color = BrandSilverDim, fontSize = 12.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Text("App developed with AI-assisted engineering.", color = BrandSilverDim.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun PermissionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PanelDark, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background((if (granted) BrandGreen else BrandSilverDim).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (granted) BrandGreen else BrandSilverDim, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = BrandSilverDim, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.background((if (granted) BrandGreen else BrandRed).copy(alpha = 0.12f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(if (granted) "ON" else "OFF", color = if (granted) BrandGreen else BrandRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
