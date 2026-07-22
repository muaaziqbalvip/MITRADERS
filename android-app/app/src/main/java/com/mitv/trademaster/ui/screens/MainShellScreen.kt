package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mitv.trademaster.data.model.Course
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.launch

private sealed class Tab(val route: String, val label: String, val labelUr: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Tab("home", "Home", "ہوم", Icons.Filled.Home)
    object Analyzer : Tab("analyzer", "Analyzer", "تجزیہ", Icons.Filled.Insights)
    object Learn : Tab("learn", "Learn", "سیکھیں", Icons.Filled.School)
    object Practice : Tab("practice", "Practice", "مشق", Icons.Filled.CandlestickChart)
    object More : Tab("more", "More", "مزید", Icons.Filled.MoreHoriz)
}

private val tabs = listOf(Tab.Home, Tab.Analyzer, Tab.Learn, Tab.Practice, Tab.More)

@Composable
fun MainShellScreen(
    language: String,
    onLanguageChanged: (String) -> Unit,
    onSignedOut: () -> Unit,
    initialDeepLink: String? = null,
    updateInfo: com.mitv.trademaster.update.UpdateInfo? = null,
    onDismissUpdateBanner: () -> Unit = {},
) {
    val navController = rememberNavController()
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var showQuizForCourse by remember { mutableStateOf<Course?>(null) }
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()
    var pendingResumeLessonId by remember { mutableStateOf<String?>(null) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var moreDestination by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionRepo = remember { com.mitv.trademaster.data.SessionRepository(context) }
    val session by sessionRepo.session.collectAsState(initial = com.mitv.trademaster.data.SessionState())
    val scope = rememberCoroutineScope()
    // Shown once, the very first time a new user reaches the main shell.
    // Starts as "already seen" (true) so it never flashes on screen before
    // the real persisted value loads in; only flips to show the tour once
    // we've confirmed from disk that this device genuinely hasn't seen it.
    var showTour by remember { mutableStateOf(false) }
    LaunchedEffect(session.hasSeenTour) {
        if (!session.hasSeenTour) showTour = true
    }

    LaunchedEffect(initialDeepLink) {
        when (initialDeepLink) {
            "lessons", "learn" -> navController.navigate(Tab.Learn.route)
            "analyzer" -> navController.navigate(Tab.Analyzer.route)
            "practice" -> navController.navigate(Tab.Practice.route)
            "chat", "support" -> moreDestination = "chat"
            "payment", "account" -> moreDestination = "account"
            "settings" -> moreDestination = "settings"
        }
    }

    Scaffold(
        containerColor = BgBlack,
        topBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            val activeTab = if (moreDestination != null) Tab.More else tabs.firstOrNull { it.route == currentRoute }
            val headerTitle = when {
                moreDestination == "chat" -> if (language == "ur") "سپورٹ چیٹ" else "Support Chat"
                moreDestination == "islamic" -> if (language == "ur") "روحانی گوشہ" else "Spiritual Corner"
                moreDestination == "account" -> if (language == "ur") "اکاؤنٹ" else "Account"
                moreDestination == "settings" -> if (language == "ur") "سیٹنگز" else "Settings"
                else -> activeTab?.let { if (language == "ur") it.labelUr else it.label } ?: "MI Trade Master"
            }
            com.mitv.trademaster.ui.components.AppHeaderBar(title = headerTitle)
        },
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route

            NavigationBar(
                containerColor = PanelDark,
                contentColor = BrandSilver,
                tonalElevation = 0.dp
            ) {
                tabs.forEach { tab ->
                    val isMore = tab == Tab.More
                    val selected = if (isMore) (moreDestination != null) else (currentRoute == tab.route && moreDestination == null)

                    NavigationBarItem(
                        selected = selected,
                        alwaysShowLabel = true,
                        onClick = {
                            tapFeedback()
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
        Column(modifier = Modifier.padding(padding).background(BgBlack).fillMaxSize()) {
            if (updateInfo != null && !updateInfo.forceUpdate) {
                UpdateAvailableBanner(updateInfo = updateInfo, language = language, onDismiss = onDismissUpdateBanner)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (moreDestination != null) {
                when (moreDestination) {
                    "chat" -> ChatSupportScreen(language)
                    "account" -> AccountScreen(language, onSignedOut)
                    "settings" -> SettingsScreen(language, onLanguageChanged)
                    "islamic" -> IslamicScreen(language)
                }
            } else {
                NavHost(navController = navController, startDestination = Tab.Home.route) {
                    composable(Tab.Home.route) {
                        HomeScreen(
                            language = language,
                            onContinueLesson = { course, lesson ->
                                selectedCourse = course
                                pendingResumeLessonId = lesson.id
                                navController.navigate(Tab.Learn.route)
                            }
                        )
                    }
                    composable(Tab.Analyzer.route) { AnalyzerScreen(language) }
                    composable(Tab.Learn.route) {
                        val course = showQuizForCourse
                        if (course != null) {
                            QuizScreen(
                                course = course,
                                language = language,
                                onBack = { showQuizForCourse = null },
                                onPassed = { showQuizForCourse = null }
                            )
                        } else if (selectedCourse == null) {
                            CoursesScreen(language) { course2 -> selectedCourse = course2 }
                        } else {
                            LessonDetailScreen(
                                course = selectedCourse!!,
                                language = language,
                                onBack = { selectedCourse = null; pendingResumeLessonId = null },
                                resumeLessonId = pendingResumeLessonId,
                                onAllLessonsComplete = { showQuizForCourse = selectedCourse }
                            )
                        }
                    }
                    composable(Tab.Practice.route) { PracticeScreen(language) }
                }
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

    if (showTour) {
        AppTourOverlay(
            language = language,
            onFinished = {
                showTour = false
                scope.launch { sessionRepo.markTourSeen() }
            }
        )
    }
}

@Composable
private fun UpdateAvailableBanner(updateInfo: com.mitv.trademaster.update.UpdateInfo, language: String, onDismiss: () -> Unit) {
    var showUpdateDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandGreen.copy(alpha = 0.14f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (language == "ur") "نیا ورژن ${updateInfo.latestVersionName} دستیاب ہے" else "New version ${updateInfo.latestVersionName} available",
                color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
            )
        }
        TextButton(onClick = { showUpdateDialog = true }) {
            Text(if (language == "ur") "اپڈیٹ کریں" else "Update", color = BrandGreen, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(16.dp))
        }
    }

    if (showUpdateDialog) {
        Dialog(onDismissRequest = { showUpdateDialog = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = BgBlack, modifier = Modifier.fillMaxWidth()) {
                Box {
                    UpdateRequiredScreen(updateInfo = updateInfo, language = language)
                    IconButton(onClick = { showUpdateDialog = false }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = BrandSilverDim)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(language: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelDark) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                if (language == "ur") "مزید" else "More",
                color = BrandSilverDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 14.dp)
            )
            MoreItem(Icons.Filled.Chat, if (language == "ur") "سپورٹ چیٹ" else "Support Chat") { onSelect("chat") }
            MoreItem(Icons.Filled.AutoAwesome, if (language == "ur") "روحانی گوشہ" else "Spiritual Corner") { onSelect("islamic") }
            MoreItem(Icons.Filled.Person, if (language == "ur") "اکاؤنٹ" else "Account") { onSelect("account") }
            MoreItem(Icons.Filled.Settings, if (language == "ur") "سیٹنگز" else "Settings") { onSelect("settings") }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MoreItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgBlack, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = { tapFeedback(); onClick() }
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).background(BrandGreen.copy(alpha = 0.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(18.dp))
    }
    Spacer(Modifier.height(8.dp))
}
