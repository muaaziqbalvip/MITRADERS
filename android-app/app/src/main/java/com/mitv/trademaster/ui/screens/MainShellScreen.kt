package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mitv.trademaster.R
import com.mitv.trademaster.ui.theme.*

private sealed class Tab(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Tab("home", R.string.nav_home, Icons.Filled.Home)
    object Analyzer : Tab("analyzer", R.string.nav_analyzer, Icons.Filled.Insights)
    object Learn : Tab("learn", R.string.nav_learn, Icons.Filled.School)
    object Account : Tab("account", R.string.nav_account, Icons.Filled.Person)
    object Settings : Tab("settings", R.string.nav_settings, Icons.Filled.Settings)
}

private val tabs = listOf(Tab.Home, Tab.Analyzer, Tab.Learn, Tab.Account, Tab.Settings)

@Composable
fun MainShellScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current

    Scaffold(
        containerColor = BgBlack,
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route

            NavigationBar(
                containerColor = PanelDark,
                contentColor = BrandSilver,
                tonalElevation = 0.dp
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(context.getString(tab.labelRes), fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandGreen,
                            selectedTextColor = BrandGreen,
                            unselectedIconColor = BrandSilverDim,
                            unselectedTextColor = BrandSilverDim,
                            indicatorColor = PanelDarker,
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).background(BgBlack)) {
            NavHost(navController = navController, startDestination = Tab.Home.route) {
                composable(Tab.Home.route) { HomeScreen() }
                composable(Tab.Analyzer.route) { AnalyzerScreen() }
                composable(Tab.Learn.route) { LearnScreen() }
                composable(Tab.Account.route) { AccountScreen() }
                composable(Tab.Settings.route) { SettingsScreen() }
            }
        }
    }
}

