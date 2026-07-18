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
import androidx.compose.material.icons.filled.AddAPhoto
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
import com.mitv.trademaster.network.ImgBBUploader
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileSetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()

    var studentName by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    var idCardNumber by remember { mutableStateOf("") }
    var fatherAddress by remember { mutableStateOf("") }
    var qualification by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var idCardUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photoUri = it }
    val pickIdCard = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { idCardUri = it }

    Column(
        modifier = Modifier.fillMaxSize().background(BgBlack).verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("Complete Your Profile", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("This information helps us verify your student account", color = BrandSilverDim, fontSize = 12.sp)

        Spacer(Modifier.height(24.dp))

        // Profile photo picker
        Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Box(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(PanelDark),
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(model = photoUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = BrandSilverDim)
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(BrandGreen)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.IconButton(onClick = { pickPhoto.launch("image/*") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = Color(0xFF04120B), modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        MitvField(studentName, { studentName = it }, "Student Full Name")
        Spacer(Modifier.height(12.dp))
        MitvField(fatherName, { fatherName = it }, "Father's Name")
        Spacer(Modifier.height(12.dp))
        MitvField(idCardNumber, { idCardNumber = it }, "Student ID Card Number")
        Spacer(Modifier.height(12.dp))
        MitvField(fatherAddress, { fatherAddress = it }, "Father's Address")
        Spacer(Modifier.height(12.dp))
        MitvField(qualification, { qualification = it }, "Qualification")
        Spacer(Modifier.height(12.dp))
        MitvField(phone, { phone = it }, "Phone Number")

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { pickIdCard.launch("image/*") },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandSilver),
            border = androidx.compose.foundation.BorderStroke(1.dp, LineSubtle),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (idCardUri == null) "Upload ID Card Photo (optional)" else "ID Card Selected ✓", fontSize = 13.sp)
        }

        errorMsg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = BrandRed, fontSize = 12.sp)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (studentName.isBlank() || fatherName.isBlank()) {
                    errorMsg = "Please fill in at least your name and father's name"
                    return@Button
                }
                val uid = authRepo.currentUser?.uid ?: return@Button
                isSaving = true
                errorMsg = null
                scope.launch {
                    try {
                        val photoUrl = photoUri?.let { uri ->
                            withContext(Dispatchers.IO) { ImgBBUploader.uploadImage(context, uri) }.getOrNull()
                        } ?: ""
                        val idCardUrl = idCardUri?.let { uri ->
                            withContext(Dispatchers.IO) { ImgBBUploader.uploadImage(context, uri) }.getOrNull()
                        } ?: ""

                        val profile = StudentProfile(
                            uid = uid,
                            studentName = studentName,
                            fatherName = fatherName,
                            studentIdCardNumber = idCardNumber,
                            fatherAddress = fatherAddress,
                            qualification = qualification,
                            email = authRepo.currentUser?.email ?: "",
                            phone = phone,
                            photoUrl = photoUrl,
                            idCardPhotoUrl = idCardUrl,
                            createdAt = System.currentTimeMillis(),
                            subscriptionStatus = "pending",
                            lastActiveAt = System.currentTimeMillis(),
                        )
                        firestoreRepo.saveStudentProfile(profile)
                        isSaving = false
                        onComplete()
                    } catch (e: Exception) {
                        isSaving = false
                        errorMsg = "Could not save profile: ${e.message}"
                    }
                }
            },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
            else Text("Save & Continue", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun MitvField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = BrandSilverDim, fontSize = 12.sp) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandGreen,
            unfocusedBorderColor = LineSubtle,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = BrandGreen,
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
