package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val repo = remember { LicenseRepository(context) }
    val scope = rememberCoroutineScope()

    var showLangSheet by remember { mutableStateOf(false) }
    var showConfirmDeactivate by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).padding(20.dp)) {
        Text(context.getString(R.string.settings_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        SettingsRow(icon = Icons.Filled.Language, label = context.getString(R.string.settings_language), value = "System default") {
            showLangSheet = true
        }
        SettingsDivider()
        SettingsRow(icon = Icons.Filled.RadioButtonChecked, label = context.getString(R.string.settings_dot_size), value = "Medium") { }
        SettingsDivider()
        SettingsRow(icon = Icons.Filled.RestartAlt, label = context.getString(R.string.settings_dot_position), value = "") { }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { showConfirmDeactivate = true },
            colors = ButtonDefaults.buttonColors(containerColor = BrandRed.copy(alpha = 0.12f), contentColor = BrandRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(context.getString(R.string.settings_logout), fontSize = 13.sp)
        }
    }

    if (showLangSheet) {
        ModalBottomSheet(onDismissRequest = { showLangSheet = false }, containerColor = PanelDark) {
            Column(Modifier.padding(20.dp)) {
                listOf("English" to "en", "اردو (Urdu)" to "ur", "हिन्दी (Hindi)" to "hi", "العربية (Arabic)" to "ar").forEach { (label, _) ->
                    TextButton(onClick = { showLangSheet = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(label, color = BrandSilver, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Language changes apply on next app restart.",
                    color = BrandSilverDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }

    if (showConfirmDeactivate) {
        AlertDialog(
            onDismissRequest = { showConfirmDeactivate = false },
            containerColor = PanelDark,
            title = { Text("Deactivate License?", color = Color.White) },
            text = { Text("You'll need to re-enter your license key to use this device again.", color = BrandSilverDim, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deactivate() }
                    showConfirmDeactivate = false
                }) { Text("Deactivate", color = BrandRed) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeactivate = false }) { Text("Cancel", color = BrandSilverDim) }
            }
        )
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelDark, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(value, color = BrandSilverDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(Modifier.height(10.dp))
}
