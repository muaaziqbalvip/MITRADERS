package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.data.SessionRepository
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(language: String, onLanguageChanged: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionRepo = remember { SessionRepository(context) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(if (language == "ur") "سیٹنگز" else "Settings", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Text(if (language == "ur") "زبان" else "Language", color = BrandSilverDim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("en" to "English", "ur" to "اردو").forEach { (code, label) ->
                val selected = language == code
                Row(
                    modifier = Modifier
                        .background(if (selected) BrandGreen.copy(alpha = 0.15f) else PanelDark, RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    TextButton(onClick = {
                        scope.launch { sessionRepo.setLanguage(code) }
                        onLanguageChanged(code)
                    }) {
                        Text(label, color = if (selected) BrandGreen else BrandSilverDim, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(if (language == "ur") "ایپ کے بارے میں" else "About", color = BrandSilverDim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("MI Trade Master", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text("Version 2.0.0", color = BrandSilverDim, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (language == "ur")
                        "MI Traders کی طرف سے تیار کردہ — بنیادی سے پرو ٹریڈر بننے تک کا سفر۔"
                    else
                        "Built by MI Traders — your journey from beginner to pro trader.",
                    color = BrandSilverDim, fontSize = 12.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "App developed with AI-assisted engineering.",
                    color = BrandSilverDim.copy(alpha = 0.6f), fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(60.dp))
    }
}
