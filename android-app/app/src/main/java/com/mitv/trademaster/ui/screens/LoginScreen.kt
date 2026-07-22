package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()

    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showForgotPassword by remember { mutableStateOf(false) }

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
                .size(80.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF1A2226), Color(0xFF0A0F11))), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "MI Trade Master",
                modifier = Modifier.size(68.dp).clip(RoundedCornerShape(16.dp))
            )
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
                    onClick = { tapFeedback(); mode = m; errorMsg = null },
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
                        tapFeedback()
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

                if (mode == AuthMode.SIGN_IN) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { tapFeedback(); showForgotPassword = true }, modifier = Modifier.align(Alignment.End)) {
                        Text("Forgot Password?", color = BrandSilverDim, fontSize = 12.5.sp)
                    }
                }
            }

            AuthMode.PHONE -> {
                if (verificationId == null) {
                    MitvTextField(value = phone, onValueChange = { phone = it }, label = "Phone (+92XXXXXXXXXX)", icon = Icons.Filled.Phone, keyboardType = KeyboardType.Phone)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            tapFeedback()
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
                            tapFeedback()
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
                ) {
                    Image(painter = painterResource(id = R.drawable.ic_google_logo), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Google", fontSize = 12.sp)
                }

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
                ) {
                    Image(painter = painterResource(id = R.drawable.ic_github_logo), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showForgotPassword) {
        ForgotPasswordDialog(
            authRepo = authRepo,
            scope = scope,
            onDismiss = { showForgotPassword = false }
        )
    }
}

@Composable
private fun ForgotPasswordDialog(authRepo: AuthRepository, scope: kotlinx.coroutines.CoroutineScope, onDismiss: () -> Unit) {
    var resetEmail by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var sentSuccessfully by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = PanelDark) {
            Column(modifier = Modifier.padding(24.dp).width(320.dp)) {
                if (sentSuccessfully) {
                    Box(modifier = Modifier.size(56.dp).background(BrandGreen.copy(alpha = 0.14f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Check Your Email", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "If an account exists for $resetEmail, a password reset link has been sent. Check your inbox (and spam folder).",
                        color = BrandSilverDim, fontSize = 12.5.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Done", fontWeight = FontWeight.Bold) }
                } else {
                    Text("Reset Your Password", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enter the email linked to your account and we'll send you a link to reset your password.",
                        color = BrandSilverDim, fontSize = 12.5.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    MitvTextField(value = resetEmail, onValueChange = { resetEmail = it; errorMsg = null }, label = "Email", icon = Icons.Filled.Email, keyboardType = KeyboardType.Email)

                    errorMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = BrandRed, fontSize = 11.5.sp)
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandSilverDim),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LineSubtle),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }

                        Button(
                            onClick = {
                                if (resetEmail.isBlank() || !resetEmail.contains("@")) {
                                    errorMsg = "Enter a valid email address"
                                    return@Button
                                }
                                isSending = true
                                errorMsg = null
                                scope.launch {
                                    val result = authRepo.sendPasswordReset(resetEmail.trim())
                                    isSending = false
                                    result.fold(
                                        onSuccess = { sentSuccessfully = true },
                                        onFailure = { err -> errorMsg = err.message ?: "Could not send reset email" }
                                    )
                                }
                            },
                            enabled = !isSending,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSending) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF04120B), strokeWidth = 2.dp)
                            else Text("Send Link", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
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
