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
    const val POST_LOGIN_CHECK = "post_login_check"
    const val PROFILE_SETUP = "profile_setup"
    const val PAYMENT_PENDING = "payment_pending"
    const val MAIN = "main"
    const val UPDATE_REQUIRED = "update_required"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deepLink = intent?.getStringExtra(com.mitv.trademaster.notifications.MitvMessagingService.EXTRA_DEEP_LINK)
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val sessionRepo = remember { SessionRepository(context) }
            val session by sessionRepo.session.collectAsState(initial = com.mitv.trademaster.data.SessionState())
            MiTradeMasterTheme(accentTheme = session.accentTheme) {
                AppRoot(initialDeepLink = deepLink)
            }
        }
    }
}

@Composable
fun AppRoot(initialDeepLink: String? = null) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionRepo = remember { SessionRepository(context) }
    val session by sessionRepo.session.collectAsState(initial = com.mitv.trademaster.data.SessionState())
    var language by remember { mutableStateOf("en") }

    LaunchedEffect(session.language) { language = session.language }

    var pendingUpdateInfo by remember { mutableStateOf<com.mitv.trademaster.update.UpdateInfo?>(null) }

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
                },
                onUpdateRequired = { updateInfo ->
                    pendingUpdateInfo = updateInfo
                    navController.navigate(Routes.UPDATE_REQUIRED) { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            )
        }
        composable(Routes.UPDATE_REQUIRED) {
            pendingUpdateInfo?.let { UpdateRequiredScreen(it) }
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onAuthenticated = {
                    // Don't assume this is a brand-new user — check Firestore
                    // for an existing profile/subscription first, same as
                    // the splash screen does, so returning users land on
                    // the correct screen instead of always re-filling the
                    // profile form.
                    navController.navigate(Routes.POST_LOGIN_CHECK) { popUpTo(Routes.LOGIN) { inclusive = true } }
                }
            )
        }
        composable(Routes.POST_LOGIN_CHECK) {
            PostLoginCheckScreen(
                onResolved = { destination ->
                    val route = when (destination) {
                        SplashDestination.LOGIN -> Routes.LOGIN
                        SplashDestination.PROFILE_SETUP -> Routes.PROFILE_SETUP
                        SplashDestination.PAYMENT_PENDING -> Routes.PAYMENT_PENDING
                        SplashDestination.MAIN -> Routes.MAIN
                    }
                    navController.navigate(route) { popUpTo(Routes.POST_LOGIN_CHECK) { inclusive = true } }
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
                language = language,
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
                },
                initialDeepLink = initialDeepLink,
            )
        }
    }
}
