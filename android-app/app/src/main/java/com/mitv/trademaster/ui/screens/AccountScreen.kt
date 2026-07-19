package com.mitv.trademaster.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.mitv.trademaster.network.ImgBBUploader
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountScreen(language: String, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()

    var profile by remember { mutableStateOf<StudentProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    var showConfirmSignOut by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var editName by remember { mutableStateOf("") }
    var editFatherName by remember { mutableStateOf("") }
    var editIdCard by remember { mutableStateOf("") }
    var editAddress by remember { mutableStateOf("") }
    var editQualification by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var newPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { newPhotoUri = it }

    fun loadProfile() {
        scope.launch {
            val uid = authRepo.currentUser?.uid ?: return@launch
            isLoading = true
            profile = try { firestoreRepo.getStudentProfile(uid) } catch (e: Exception) { null }
            profile?.let {
                editName = it.studentName
                editFatherName = it.fatherName
                editIdCard = it.studentIdCardNumber
                editAddress = it.fatherAddress
                editQualification = it.qualification
                editPhone = it.phone
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadProfile() }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(if (language == "ur") "اکاؤنٹ" else "Account", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!isLoading && profile != null) {
                IconButton(onClick = { isEditing = !isEditing; newPhotoUri = null }) {
                    Icon(if (isEditing) Icons.Filled.Close else Icons.Filled.Edit, contentDescription = null, tint = BrandGreen)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandGreen)
            }
        } else {

        Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).background(Brush.linearGradient(listOf(PanelDark, PanelDarker))),
                contentAlignment = Alignment.Center
            ) {
                val displayPhoto: Any? = newPhotoUri ?: profile?.photoUrl?.takeIf { it.isNotBlank() }
                if (displayPhoto != null) {
                    AsyncImage(model = displayPhoto, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(38.dp))
                }
            }
            if (isEditing) {
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(BrandGreen), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { pickPhoto.launch("image/*") }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = Color(0xFF04120B), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (!isEditing) {
            Text(profile?.studentName?.ifBlank { "—" } ?: "—", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (profile?.subscriptionStatus == "active") BrandGreen else BrandRed))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (profile?.subscriptionStatus == "active") (if (language == "ur") "فعال رکنیت" else "Active Subscription") else (if (language == "ur") "زیر التوا" else "Pending"),
                    color = if (profile?.subscriptionStatus == "active") BrandGreen else BrandRed, fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    InfoRow(if (language == "ur") "والد کا نام" else "Father's Name", profile?.fatherName)
                    InfoDivider()
                    InfoRow(if (language == "ur") "شناختی کارڈ نمبر" else "ID Card Number", profile?.studentIdCardNumber)
                    InfoDivider()
                    InfoRow(if (language == "ur") "والد کا پتہ" else "Father's Address", profile?.fatherAddress)
                    InfoDivider()
                    InfoRow(if (language == "ur") "قابلیت" else "Qualification", profile?.qualification)
                    InfoDivider()
                    InfoRow(if (language == "ur") "ای میل" else "Email", profile?.email)
                    InfoDivider()
                    InfoRow(if (language == "ur") "فون" else "Phone", profile?.phone)
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            EditField(editName, { editName = it }, if (language == "ur") "طالب علم کا نام" else "Student Name")
            Spacer(Modifier.height(10.dp))
            EditField(editFatherName, { editFatherName = it }, if (language == "ur") "والد کا نام" else "Father's Name")
            Spacer(Modifier.height(10.dp))
            EditField(editIdCard, { editIdCard = it }, if (language == "ur") "شناختی کارڈ نمبر" else "ID Card Number")
            Spacer(Modifier.height(10.dp))
            EditField(editAddress, { editAddress = it }, if (language == "ur") "والد کا پتہ" else "Father's Address")
            Spacer(Modifier.height(10.dp))
            EditField(editQualification, { editQualification = it }, if (language == "ur") "قابلیت" else "Qualification")
            Spacer(Modifier.height(10.dp))
            EditField(editPhone, { editPhone = it }, if (language == "ur") "فون" else "Phone")

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    tapFeedback()
                    val uid = authRepo.currentUser?.uid ?: return@Button
                    val current = profile ?: return@Button
                    isSaving = true
                    scope.launch {
                        try {
                            val photoUrl = if (newPhotoUri != null) {
                                withContext(Dispatchers.IO) { ImgBBUploader.uploadImage(context, newPhotoUri!!) }.getOrNull() ?: current.photoUrl
                            } else current.photoUrl

                            val updated = current.copy(
                                studentName = editName,
                                fatherName = editFatherName,
                                studentIdCardNumber = editIdCard,
                                fatherAddress = editAddress,
                                qualification = editQualification,
                                phone = editPhone,
                                photoUrl = photoUrl,
                            )
                            firestoreRepo.saveStudentProfile(updated)
                            profile = updated
                            isEditing = false
                            newPhotoUri = null
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
                else Text(if (language == "ur") "تبدیلیاں محفوظ کریں" else "Save Changes", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))

        if (!isEditing) {
            Button(
                onClick = { tapFeedback(); showConfirmSignOut = true },
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed.copy(alpha = 0.12f), contentColor = BrandRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (language == "ur") "سائن آؤٹ" else "Sign Out", fontSize = 13.sp)
            }
        }

        } // end else (isLoading)

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
                    tapFeedback()
                    authRepo.signOut()
                    showConfirmSignOut = false
                    onSignedOut()
                }) { Text(if (language == "ur") "سائن آؤٹ" else "Sign Out", color = BrandRed) }
            },
            dismissButton = {
                TextButton(onClick = { tapFeedback(); showConfirmSignOut = false }) { Text(if (language == "ur") "منسوخ" else "Cancel", color = BrandSilverDim) }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = BrandSilverDim, fontSize = 12.sp)
        Text(value?.ifBlank { "—" } ?: "—", color = BrandSilver, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.widthIn(max = 200.dp))
    }
}

@Composable
private fun InfoDivider() {
    androidx.compose.material3.HorizontalDivider(color = LineSubtle)
}

@Composable
private fun EditField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = BrandSilverDim, fontSize = 12.sp) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandGreen, unfocusedBorderColor = LineSubtle,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = BrandGreen,
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
