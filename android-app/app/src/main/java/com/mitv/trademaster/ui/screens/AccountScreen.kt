package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
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
import com.mitv.trademaster.data.model.StudentProfile
import com.mitv.trademaster.ui.theme.*

@Composable
fun AccountScreen(language: String, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }
    var profile by remember { mutableStateOf<StudentProfile?>(null) }
    var showConfirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = authRepo.currentUser?.uid ?: return@LaunchedEffect
        profile = try { firestoreRepo.getStudentProfile(uid) } catch (e: Exception) { null }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(if (language == "ur") "اکاؤنٹ" else "Account", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(PanelDark), contentAlignment = Alignment.Center) {
                if (!profile?.photoUrl.isNullOrBlank()) {
                    AsyncImage(model = profile?.photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(profile?.studentName?.ifBlank { "—" } ?: "—", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (profile?.subscriptionStatus == "active") (if (language == "ur") "فعال" else "Active") else (if (language == "ur") "زیر التوا" else "Pending"),
                    color = if (profile?.subscriptionStatus == "active") BrandGreen else BrandRed, fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                InfoRow(if (language == "ur") "والد کا نام" else "Father's Name", profile?.fatherName?.ifBlank { "—" } ?: "—")
                Spacer(Modifier.height(12.dp))
                InfoRow(if (language == "ur") "شناختی کارڈ نمبر" else "ID Card Number", profile?.studentIdCardNumber?.ifBlank { "—" } ?: "—")
                Spacer(Modifier.height(12.dp))
                InfoRow(if (language == "ur") "قابلیت" else "Qualification", profile?.qualification?.ifBlank { "—" } ?: "—")
                Spacer(Modifier.height(12.dp))
                InfoRow(if (language == "ur") "ای میل" else "Email", profile?.email?.ifBlank { "—" } ?: "—")
                Spacer(Modifier.height(12.dp))
                InfoRow(if (language == "ur") "فون" else "Phone", profile?.phone?.ifBlank { "—" } ?: "—")
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { showConfirmSignOut = true },
            colors = ButtonDefaults.buttonColors(containerColor = BrandRed.copy(alpha = 0.12f), contentColor = BrandRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (language == "ur") "سائن آؤٹ" else "Sign Out", fontSize = 13.sp)
        }

        Spacer(Modifier.height(60.dp))
    }

    if (showConfirmSignOut) {
        AlertDialog(
            onDismissRequest = { showConfirmSignOut = false },
            containerColor = PanelDark,
            title = { Text(if (language == "ur") "سائن آؤٹ کریں؟" else "Sign Out?", color = Color.White) },
            text = { Text(if (language == "ur") "آپ کو دوبارہ لاگ ان کرنا ہوگا۔" else "You'll need to sign in again next time.", color = BrandSilverDim, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    authRepo.signOut()
                    showConfirmSignOut = false
                    onSignedOut()
                }) { Text(if (language == "ur") "سائن آؤٹ" else "Sign Out", color = BrandRed) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSignOut = false }) { Text(if (language == "ur") "منسوخ" else "Cancel", color = BrandSilverDim) }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = BrandSilverDim, fontSize = 12.sp)
        Text(value, color = BrandSilver, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.widthIn(max = 200.dp))
    }
}
