package com.mitv.trademaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mitv.trademaster.data.SessionRepository
import com.mitv.trademaster.ui.screens.*
import com.mitv.trademaster.ui.theme.MiTradeMasterTheme

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val PROFILE_SETUP = "profile_setup"
    const val PAYMENT_PENDING = "payment_pending"
    const val MAIN = "main"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiTradeMasterTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionRepo = remember { SessionRepository(context) }
    val session by sessionRepo.session.collectAsState(initial = com.mitv.trademaster.data.SessionState())
    var language by remember { mutableStateOf("en") }

    LaunchedEffect(session.language) { language = session.language }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = { destination ->
                    val route = when (destination) {
                        SplashDestination.LOGIN -> Routes.LOGIN
                        SplashDestination.PROFILE_SETUP -> Routes.PROFILE_SETUP
                        SplashDestination.PAYMENT_PENDING -> Routes.PAYMENT_PENDING
                        SplashDestination.MAIN -> Routes.MAIN
                    }
                    navController.navigate(route) { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onAuthenticated = {
                    navController.navigate(Routes.PROFILE_SETUP) { popUpTo(Routes.LOGIN) { inclusive = true } }
                }
            )
        }
        composable(Routes.PROFILE_SETUP) {
            ProfileSetupScreen(
                onComplete = {
                    navController.navigate(Routes.PAYMENT_PENDING) { popUpTo(Routes.PROFILE_SETUP) { inclusive = true } }
                }
            )
        }
        composable(Routes.PAYMENT_PENDING) {
            PaymentScreen(
                studentName = "",
                onSubmitted = {
                    // Stay on a "submitted" state within PaymentScreen; admin will
                    // activate the account. Re-checking status happens on next
                    // app launch via SplashScreen's profile status check.
                }
            )
        }
        composable(Routes.MAIN) {
            MainShellScreen(
                language = language,
                onLanguageChanged = { language = it },
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }
    }
}
