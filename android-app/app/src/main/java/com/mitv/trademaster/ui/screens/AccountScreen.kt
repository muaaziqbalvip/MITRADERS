package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.R
import com.mitv.trademaster.data.LicenseRepository
import com.mitv.trademaster.ui.theme.*

@Composable
fun AccountScreen() {
    val context = LocalContext.current
    val repo = remember { LicenseRepository(context) }
    val state by repo.licenseState.collectAsState(initial = null)

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).padding(20.dp)) {
        Text(context.getString(R.string.account_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(64.dp).background(PanelDark, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(state?.userName?.ifBlank { "Trader" } ?: "—", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (state?.isActivated == true) context.getString(R.string.account_status_active) else context.getString(R.string.account_status_expired),
                    color = if (state?.isActivated == true) BrandGreen else BrandRed,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                InfoRow(context.getString(R.string.account_license_key), state?.licenseKey?.ifBlank { "—" } ?: "—")
                Spacer(Modifier.height(12.dp))
                InfoRow(context.getString(R.string.account_expires), state?.expiresAt?.take(10) ?: "Lifetime")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = BrandSilverDim, fontSize = 12.sp)
        Text(value, color = BrandSilver, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
