package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.R
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.AuthResult
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.launch

private enum class AuthMode { SIGN_IN, SIGN_UP, PHONE }

@Composable
fun LoginScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val authRepo = remember { AuthRepository(context) }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val webClientId = context.getString(R.string.default_web_client_id)

    fun handleResult(result: AuthResult) {
        isLoading = false
        when (result) {
            is AuthResult.Success -> onAuthenticated()
            is AuthResult.Error -> errorMsg = result.message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PanelDarker, BgBlack)))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF1A2226), Color(0xFF0A0F11))), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("MI", color = BrandSilver, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(16.dp))
        Text("MI TRADE MASTER", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(4.dp))
        Text("Beginner to Pro Trader", color = BrandGreen, fontSize = 12.sp)

        Spacer(Modifier.height(32.dp))

        // Mode tabs
        Row(
            modifier = Modifier.fillMaxWidth().background(PanelDark, RoundedCornerShape(12.dp)).padding(4.dp)
        ) {
            listOf(AuthMode.SIGN_IN to "Sign In", AuthMode.SIGN_UP to "Sign Up", AuthMode.PHONE to "Phone").forEach { (m, label) ->
                val selected = mode == m
                TextButton(
                    onClick = { mode = m; errorMsg = null },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label, color = if (selected) BrandGreen else BrandSilverDim, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        when (mode) {
            AuthMode.SIGN_IN, AuthMode.SIGN_UP -> {
                MitvTextField(value = email, onValueChange = { email = it }, label = "Email", icon = Icons.Filled.Email, keyboardType = KeyboardType.Email)
                Spacer(Modifier.height(12.dp))
                MitvTextField(value = password, onValueChange = { password = it }, label = "Password", icon = Icons.Filled.Lock, isPassword = true)
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        errorMsg = null
                        if (email.isBlank() || password.isBlank()) { errorMsg = "Please fill all fields"; return@Button }
                        isLoading = true
                        scope.launch {
                            val result = if (mode == AuthMode.SIGN_IN) authRepo.signInWithEmail(email, password)
                                         else authRepo.signUpWithEmail(email, password)
                            handleResult(result)
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
                    else Text(if (mode == AuthMode.SIGN_IN) "Sign In" else "Create Account", fontWeight = FontWeight.Bold)
                }
            }

            AuthMode.PHONE -> {
                if (verificationId == null) {
                    MitvTextField(value = phone, onValueChange = { phone = it }, label = "Phone (+92XXXXXXXXXX)", icon = Icons.Filled.Phone, keyboardType = KeyboardType.Phone)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            errorMsg = null
                            if (phone.isBlank() || activity == null) { errorMsg = "Enter a valid phone number"; return@Button }
                            isLoading = true
                            authRepo.startPhoneVerification(
                                phoneNumber = phone,
                                activity = activity,
                                onCodeSent = { id -> isLoading = false; verificationId = id },
                                onError = { msg -> isLoading = false; errorMsg = msg },
                                onAutoVerified = { onAuthenticated() }
                            )
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
                        else Text("Send OTP", fontWeight = FontWeight.Bold)
                    }
                } else {
                    MitvTextField(value = otpCode, onValueChange = { otpCode = it }, label = "Enter OTP", icon = Icons.Filled.Lock, keyboardType = KeyboardType.Number)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            errorMsg = null
                            isLoading = true
                            scope.launch {
                                val result = authRepo.confirmPhoneCode(verificationId!!, otpCode)
                                handleResult(result)
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
                        else Text("Verify & Continue", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        errorMsg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = BrandRed, fontSize = 12.sp)
        }

        if (mode != AuthMode.PHONE) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = LineSubtle)
                Text("  or continue with  ", color = BrandSilverDim, fontSize = 11.sp)
                HorizontalDivider(modifier = Modifier.weight(1f), color = LineSubtle)
            }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (activity == null) return@OutlinedButton
                        errorMsg = null
                        isLoading = true
                        scope.launch {
                            val result = authRepo.signInWithGoogle(activity, webClientId)
                            handleResult(result)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandSilver),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LineSubtle),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) { Text("Google", fontSize = 12.sp) }

                OutlinedButton(
                    onClick = {
                        if (activity == null) return@OutlinedButton
                        errorMsg = null
                        isLoading = true
                        scope.launch {
                            val result = authRepo.signInWithGitHub(activity)
                            handleResult(result)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandSilver),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LineSubtle),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) { Text("GitHub", fontSize = 12.sp) }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun MitvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = BrandSilverDim) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = BrandGreenDim) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
