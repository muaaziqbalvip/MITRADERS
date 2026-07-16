package com.mitv.trademaster.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.R
import com.mitv.trademaster.data.LicenseRepository
import com.mitv.trademaster.ui.theme.*
import com.mitv.trademaster.overlay.OverlayBubbleService

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repo = remember { LicenseRepository(context) }
    val licenseState by repo.licenseState.collectAsState(initial = null)

    var overlayActive by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            context.getString(R.string.home_welcome),
            color = BrandSilverDim,
            fontSize = 13.sp
        )
        Text(
            licenseState?.userName?.ifBlank { "Trader" } ?: "Trader",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        // Overlay control card
        Card(
            colors = CardDefaults.cardColors(containerColor = PanelDark),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Brush.linearGradient(listOf(BrandGreen.copy(alpha = 0.2f), Color.Transparent)),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = BrandGreen)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Floating Analyzer", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            if (overlayActive) "Active — bubble is on screen" else "Inactive",
                            color = if (overlayActive) BrandGreen else BrandSilverDim,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (overlayActive) BrandRed.copy(alpha = 0.15f) else BrandGreen
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(
                        if (overlayActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = if (overlayActive) BrandRed else Color(0xFF04120B)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        context.getString(if (overlayActive) R.string.home_stop_overlay else R.string.home_start_overlay),
                        color = if (overlayActive) BrandRed else Color(0xFF04120B),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Quick stats row (placeholder structure for future real data)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(modifier = Modifier.weight(1f), label = "Analyses Today", value = "—")
            StatCard(modifier = Modifier.weight(1f), label = "Plan", value = licenseState?.let { if (it.isActivated) "Active" else "—" } ?: "—")
        }

        Spacer(Modifier.height(20.dp))

        // Disclaimer strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDark, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                context.getString(R.string.home_disclaimer_short),
                color = BrandSilverDim,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelDark),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(value, color = BrandGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, color = BrandSilverDim, fontSize = 11.sp)
        }
    }
}
