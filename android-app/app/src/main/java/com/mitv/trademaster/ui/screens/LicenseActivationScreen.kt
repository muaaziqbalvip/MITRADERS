package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.R
import com.mitv.trademaster.data.LicenseRepository
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LicenseActivationScreen(onActivated: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { LicenseRepository(context) }
    val scope = rememberCoroutineScope()

    var keyInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PanelDarker, BgBlack)))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF1A2226), Color(0xFF0A0F11))),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(32.dp))
            }

            Spacer(Modifier.height(24.dp))

            Text(
                context.getString(R.string.license_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                context.getString(R.string.license_subtitle),
                color = BrandSilverDim,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = keyInput,
                onValueChange = {
                    keyInput = it.uppercase()
                    errorMsg = null
                },
                placeholder = { Text(context.getString(R.string.license_key_hint), color = BrandSilverDim) },
                leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null, tint = BrandGreenDim) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                isError = errorMsg != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandGreen,
                    unfocusedBorderColor = LineSubtle,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = BrandGreen,
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(errorMsg!!, color = BrandRed, fontSize = 12.sp)
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (keyInput.isBlank()) {
                        errorMsg = context.getString(R.string.license_invalid)
                        return@Button
                    }
                    isLoading = true
                    scope.launch {
                        val result = repo.activate(keyInput)
                        isLoading = false
                        result.onSuccess {
                            onActivated()
                        }.onFailure {
                            errorMsg = it.message ?: context.getString(R.string.license_invalid)
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
                } else {
                    Text(context.getString(R.string.license_activate_btn), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                context.getString(R.string.license_no_key_help),
                color = BrandSilverDim,
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
