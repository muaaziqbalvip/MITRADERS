package com.mitv.trademaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mitv.trademaster.ui.screens.LicenseActivationScreen
import com.mitv.trademaster.ui.screens.MainShellScreen
import com.mitv.trademaster.ui.screens.SplashScreen
import com.mitv.trademaster.ui.theme.MiTradeMasterTheme

object Routes {
    const val SPLASH = "splash"
    const val LICENSE = "license"
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

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = { isActivated ->
                    val dest = if (isActivated) Routes.MAIN else Routes.LICENSE
                    navController.navigate(dest) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.LICENSE) {
            LicenseActivationScreen(
                onActivated = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LICENSE) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
            MainShellScreen()
        }
    }
}
