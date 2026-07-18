package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.ui.theme.BgBlack
import com.mitv.trademaster.ui.theme.BrandGreen

/**
 * Runs immediately after a successful sign-in (any provider). Checks
 * whether this account already has a student profile and what its
 * subscription status is, then routes accordingly — new users go to
 * profile setup, existing users go straight to payment-pending or main.
 * This prevents a returning user's profile/account data from being
 * silently overwritten by the signup flow.
 */
@Composable
fun PostLoginCheckScreen(onResolved: (SplashDestination) -> Unit) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }

    LaunchedEffect(Unit) {
        val user = authRepo.currentUser
        if (user == null) {
            onResolved(SplashDestination.LOGIN)
            return@LaunchedEffect
        }
        val profile = try { firestoreRepo.getStudentProfile(user.uid) } catch (e: Exception) { null }
        when {
            profile == null || profile.studentName.isBlank() -> onResolved(SplashDestination.PROFILE_SETUP)
            profile.subscriptionStatus != "active" -> onResolved(SplashDestination.PAYMENT_PENDING)
            else -> onResolved(SplashDestination.MAIN)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgBlack), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrandGreen)
    }
}
