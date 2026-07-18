package com.mitv.trademaster.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.SessionRepository
import com.mitv.trademaster.data.SessionState
import com.mitv.trademaster.data.model.Announcement
import com.mitv.trademaster.data.model.Course
import com.mitv.trademaster.data.model.Lesson
import com.mitv.trademaster.data.model.StudentProfile
import com.mitv.trademaster.overlay.OverlayBubbleService
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.launch

private val announcementIcons: Map<String, ImageVector> = mapOf(
    "Campaign" to Icons.Filled.Campaign,
    "NewReleases" to Icons.Filled.NewReleases,
    "Star" to Icons.Filled.Star,
    "Warning" to Icons.Filled.WarningAmber,
)

@Composable
fun HomeScreen(language: String, onContinueLesson: (Course, Lesson) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val firestoreRepo = remember { FirestoreRepository() }
    val sessionRepo = remember { SessionRepository(context) }
    val session by sessionRepo.session.collectAsState(initial = SessionState())
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<StudentProfile?>(null) }
    var announcements by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    var overlayActive by remember { mutableStateOf(false) }
    var continueCourse by remember { mutableStateOf<Course?>(null) }
    var continueLesson by remember { mutableStateOf<Lesson?>(null) }

    LaunchedEffect(Unit) {
        val uid = authRepo.currentUser?.uid ?: return@LaunchedEffect
        profile = try { firestoreRepo.getStudentProfile(uid) } catch (e: Exception) { null }
        announcements = try { firestoreRepo.getAnnouncements() } catch (e: Exception) { emptyList() }
        scope.launch { runCatching { firestoreRepo.updateLastActive(uid) } }
    }

    // Resolve "continue where you left off" — most recently viewed lesson across all courses.
    LaunchedEffect(session.lastLessonByCourse) {
        val entry = session.lastLessonByCourse.entries.firstOrNull() ?: return@LaunchedEffect
        val (courseId, lessonId) = entry
        val courses = try { firestoreRepo.getCourses() } catch (e: Exception) { emptyList() }
        val course = courses.find { it.id == courseId } ?: return@LaunchedEffect
        val lessons = try { firestoreRepo.getLessons(courseId) } catch (e: Exception) { emptyList() }
        val lesson = lessons.find { it.id == lessonId } ?: return@LaunchedEffect
        // Only show as "continue" if not already fully completed.
        if (lesson.id !in session.completedLessonIds || lessons.any { it.id !in session.completedLessonIds }) {
            continueCourse = course
            continueLesson = lesson
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BgBlack).verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(PanelDark), contentAlignment = Alignment.Center) {
                if (!profile?.photoUrl.isNullOrBlank()) {
                    AsyncImage(model = profile?.photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(profile?.studentName?.take(1)?.uppercase() ?: "S", color = BrandGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (language == "ur") "خوش آمدید" else "Welcome back", color = BrandSilverDim, fontSize = 12.sp)
                Text(profile?.studentName?.ifBlank { "Trader" } ?: "Trader", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(18.dp))

        val isActive = profile?.subscriptionStatus == "active"
        Row(
            modifier = Modifier.background(if (isActive) BrandGreen.copy(alpha = 0.12f) else BrandRed.copy(alpha = 0.12f), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isActive) BrandGreen else BrandRed))
            Spacer(Modifier.width(8.dp))
            Text(
                if (isActive) (if (language == "ur") "فعال رکنیت" else "Active Subscription") else (if (language == "ur") "رکنیت زیر التوا" else "Subscription Pending"),
                color = if (isActive) BrandGreen else BrandRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
        }

        // ---------- Continue where you left off ----------
        if (continueCourse != null && continueLesson != null) {
            Spacer(Modifier.height(18.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().clickable { onContinueLesson(continueCourse!!, continueLesson!!) }
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(BrandGreen.copy(alpha = 0.16f), PanelDark)), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).background(BrandGreen.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = BrandGreen) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (language == "ur") "جہاں سے چھوڑا وہاں سے جاری رکھیں" else "Continue where you left off",
                                color = BrandSilverDim, fontSize = 11.sp
                            )
                            Text(
                                if (language == "ur" && continueLesson!!.titleUrdu.isNotBlank()) continueLesson!!.titleUrdu else continueLesson!!.title,
                                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = BrandGreen)
                    }
                }
            }
        }

        // Announcements — admin controlled, only shown if any exist
        if (announcements.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(if (language == "ur") "اعلانات" else "Announcements", color = BrandSilverDim, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(announcements) { a -> AnnouncementCard(a, language) }
            }
        }

        Spacer(Modifier.height(20.dp))

        Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).background(Brush.linearGradient(listOf(BrandGreen.copy(alpha = 0.2f), Color.Transparent)), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Bolt, contentDescription = null, tint = BrandGreen) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (language == "ur") "فلوٹنگ اینالائزر" else "Floating Analyzer", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            if (overlayActive) (if (language == "ur") "فعال" else "Active — bubble is on screen") else (if (language == "ur") "غیر فعال" else "Inactive"),
                            color = if (overlayActive) BrandGreen else BrandSilverDim, fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!Settings.canDrawOverlays(context)) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                            return@Button
                        }
                        if (overlayActive) {
                            context.stopService(Intent(context, OverlayBubbleService::class.java))
                            overlayActive = false
                        } else {
                            context.startService(Intent(context, OverlayBubbleService::class.java))
                            overlayActive = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (overlayActive) BrandRed.copy(alpha = 0.15f) else BrandGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(if (overlayActive) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null, tint = if (overlayActive) BrandRed else Color(0xFF04120B))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (overlayActive) (if (language == "ur") "بند کریں" else "Stop Floating Analyzer") else (if (language == "ur") "شروع کریں" else "Start Floating Analyzer"),
                        color = if (overlayActive) BrandRed else Color(0xFF04120B), fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(modifier = Modifier.weight(1f), label = if (language == "ur") "مکمل اسباق" else "Lessons Done", value = (profile?.lessonsCompleted ?: 0).toString())
            StatCard(modifier = Modifier.weight(1f), label = if (language == "ur") "تجزیے" else "Analyses Run", value = (profile?.analysesRun ?: 0).toString())
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().background(PanelDark, RoundedCornerShape(12.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (language == "ur") "تعلیمی چارٹ تجزیہ — مالیاتی مشورہ نہیں" else "Educational chart analysis — not financial advice",
                color = BrandSilverDim, fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun AnnouncementCard(a: Announcement, language: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(240.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(announcementIcons[a.iconName] ?: Icons.Filled.Campaign, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (language == "ur" && a.titleUrdu.isNotBlank()) a.titleUrdu else a.title,
                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (language == "ur" && a.messageUrdu.isNotBlank()) a.messageUrdu else a.message,
                color = BrandSilverDim, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 3
            )
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, color = BrandGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, color = BrandSilverDim, fontSize = 11.sp)
        }
    }
}
