package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mitv.trademaster.data.model.Course
import com.mitv.trademaster.ui.theme.*

private sealed class Tab(val route: String, val label: String, val labelUr: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Tab("home", "Home", "ہوم", Icons.Filled.Home)
    object Analyzer : Tab("analyzer", "Analyzer", "تجزیہ", Icons.Filled.Insights)
    object Learn : Tab("learn", "Learn", "سیکھیں", Icons.Filled.School)
    object Practice : Tab("practice", "Practice", "مشق", Icons.Filled.CandlestickChart)
    object Chat : Tab("chat", "Support", "سپورٹ", Icons.Filled.Chat)
    object Account : Tab("account", "Account", "اکاؤنٹ", Icons.Filled.Person)
    object Settings : Tab("settings", "Settings", "سیٹنگز", Icons.Filled.Settings)
}

private val tabs = listOf(Tab.Home, Tab.Analyzer, Tab.Learn, Tab.Practice, Tab.Chat, Tab.Account, Tab.Settings)

@Composable
fun MainShellScreen(language: String, onLanguageChanged: (String) -> Unit, onSignedOut: () -> Unit) {
    val navController = rememberNavController()
    var selectedCourse by remember { mutableStateOf<Course?>(null) }

    Scaffold(
        containerColor = BgBlack,
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route

            NavigationBar(containerColor = PanelDark, contentColor = BrandSilver, tonalElevation = 0.dp) {
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
                        label = { Text(if (language == "ur") tab.labelUr else tab.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandGreen, selectedTextColor = BrandGreen,
                            unselectedIconColor = BrandSilverDim, unselectedTextColor = BrandSilverDim,
                            indicatorColor = PanelDarker,
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).background(BgBlack)) {
            NavHost(navController = navController, startDestination = Tab.Home.route) {
                composable(Tab.Home.route) { HomeScreen(language) }
                composable(Tab.Analyzer.route) { AnalyzerScreen() }
                composable(Tab.Learn.route) {
                    if (selectedCourse == null) {
                        CoursesScreen(language) { course -> selectedCourse = course }
                    } else {
                        LessonDetailScreen(course = selectedCourse!!, language = language, onBack = { selectedCourse = null })
                    }
                }
                composable(Tab.Practice.route) { PracticeScreen(language) }
                composable(Tab.Chat.route) { ChatSupportScreen() }
                composable(Tab.Account.route) { AccountScreen(language, onSignedOut) }
                composable(Tab.Settings.route) { SettingsScreen(language, onLanguageChanged) }
            }
        }
    }
}
