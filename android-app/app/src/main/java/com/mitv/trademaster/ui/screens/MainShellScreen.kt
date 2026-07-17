package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    object More : Tab("more", "More", "مزید", Icons.Filled.MoreHoriz)
}

private val tabs = listOf(Tab.Home, Tab.Analyzer, Tab.Learn, Tab.Practice, Tab.More)

@Composable
fun MainShellScreen(language: String, onLanguageChanged: (String) -> Unit, onSignedOut: () -> Unit) {
    val navController = rememberNavController()
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var moreDestination by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = BgBlack,
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route

            NavigationBar(
                containerColor = PanelDark,
                contentColor = BrandSilver,
                tonalElevation = 0.dp,
                modifier = Modifier.height(72.dp)
            ) {
                tabs.forEach { tab ->
                    val isMore = tab == Tab.More
                    val selected = if (isMore) (moreDestination != null) else (currentRoute == tab.route && moreDestination == null)

                    NavigationBarItem(
                        selected = selected,
                        alwaysShowLabel = true,
                        onClick = {
                            if (isMore) {
                                showMoreSheet = true
                            } else {
                                moreDestination = null
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = {
                            Text(
                                if (language == "ur") tab.labelUr else tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandGreen, selectedTextColor = BrandGreen,
                            unselectedIconColor = BrandSilverDim, unselectedTextColor = BrandSilverDim,
                            indicatorColor = BrandGreen.copy(alpha = 0.12f),
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).background(BgBlack).fillMaxSize()) {
            if (moreDestination != null) {
                when (moreDestination) {
                    "chat" -> ChatSupportScreen()
                    "account" -> AccountScreen(language, onSignedOut)
                    "settings" -> SettingsScreen(language, onLanguageChanged)
                }
            } else {
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
                }
            }
        }
    }

    if (showMoreSheet) {
        MoreSheet(
            language = language,
            onDismiss = { showMoreSheet = false },
            onSelect = { dest -> moreDestination = dest; showMoreSheet = false }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(language: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelDark) {
        Column(Modifier.padding(20.dp)) {
            MoreItem(Icons.Filled.Chat, if (language == "ur") "سپورٹ چیٹ" else "Support Chat") { onSelect("chat") }
            MoreItem(Icons.Filled.Person, if (language == "ur") "اکاؤنٹ" else "Account") { onSelect("account") }
            MoreItem(Icons.Filled.Settings, if (language == "ur") "سیٹنگز" else "Settings") { onSelect("settings") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MoreItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgBlack, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
    Spacer(Modifier.height(8.dp))
}
