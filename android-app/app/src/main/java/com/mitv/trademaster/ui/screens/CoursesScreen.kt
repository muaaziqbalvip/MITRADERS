package com.mitv.trademaster.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.SessionRepository
import com.mitv.trademaster.data.SessionState
import com.mitv.trademaster.data.model.Course
import com.mitv.trademaster.data.model.Lesson
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.launch

private val iconMap: Map<String, ImageVector> = mapOf(
    "School" to Icons.Filled.School,
    "CandlestickChart" to Icons.Filled.CandlestickChart,
    "Timeline" to Icons.Filled.Timeline,
    "ShowChart" to Icons.Filled.ShowChart,
    "AutoGraph" to Icons.Filled.AutoGraph,
    "Security" to Icons.Filled.Security,
    "Psychology" to Icons.Filled.Psychology,
    "Rule" to Icons.Filled.Rule,
)

private val levelColor: Map<String, Color> = mapOf(
    "beginner" to Color(0xFF34E39A),
    "intermediate" to Color(0xFFE3B934),
    "advanced" to Color(0xFFFF5C6A),
)

@Composable
fun CoursesScreen(language: String, onCourseSelected: (Course) -> Unit) {
    val context = LocalContext.current
    val repo = remember { FirestoreRepository() }
    val sessionRepo = remember { SessionRepository(context) }
    val session by sessionRepo.session.collectAsState(initial = SessionState())

    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var lessonsByCourse by remember { mutableStateOf<Map<String, List<Lesson>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            val loadedCourses = try { repo.getCourses() } catch (e: Exception) { emptyList() }
            courses = loadedCourses
            // Pull lesson counts per course so we can show real progress bars.
            val map = mutableMapOf<String, List<Lesson>>()
            loadedCourses.forEach { c ->
                map[c.id] = try { repo.getLessons(c.id) } catch (e: Exception) { emptyList() }
            }
            lessonsByCourse = map
            isLoading = false
        }
    }

    val totalLessons = lessonsByCourse.values.sumOf { it.size }
    val totalCompleted = lessonsByCourse.values.flatten().count { it.id in session.completedLessonIds }
    val overallProgress = if (totalLessons > 0) totalCompleted.toFloat() / totalLessons else 0f
    val animatedOverall by animateFloatAsState(targetValue = overallProgress, animationSpec = tween(600), label = "overall")

    Column(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(if (language == "ur") "ٹریڈنگ سیکھیں" else "Learn Trading", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (language == "ur") "بنیادی سے پرو تک، اپنی رفتار سے" else "From basics to pro, at your own pace",
                    color = BrandSilverDim, fontSize = 12.sp
                )
            }

            // ---------- Overall progress hero card ----------
            if (!isLoading && totalLessons > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(listOf(BrandGreen.copy(alpha = 0.18f), PanelDark)),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                                    CircularProgressIndicator(
                                        progress = { 1f }, modifier = Modifier.size(64.dp),
                                        color = LineSubtle, strokeWidth = 6.dp, trackColor = Color.Transparent
                                    )
                                    CircularProgressIndicator(
                                        progress = { animatedOverall }, modifier = Modifier.size(64.dp),
                                        color = BrandGreen, strokeWidth = 6.dp, trackColor = Color.Transparent
                                    )
                                    Text("${(animatedOverall * 100).toInt()}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        if (language == "ur") "آپ کی تعلیمی پیش رفت" else "Your learning progress",
                                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        (if (language == "ur") "$totalCompleted مکمل, کل " else "$totalCompleted completed of ") + "$totalLessons" + (if (language == "ur") " اسباق میں سے" else " lessons"),
                                        color = BrandSilverDim, fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandGreen)
                    }
                }
            } else if (courses.isEmpty()) {
                item {
                    Text(
                        if (language == "ur") "ابھی کوئی کورس دستیاب نہیں — جلد شامل کیا جائے گا۔" else "No courses yet — check back soon.",
                        color = BrandSilverDim, fontSize = 13.sp
                    )
                }
            } else {
                items(courses) { course ->
                    val courseLessons = lessonsByCourse[course.id].orEmpty()
                    val completedInCourse = courseLessons.count { it.id in session.completedLessonIds }
                    val courseProgress = if (courseLessons.isNotEmpty()) completedInCourse.toFloat() / courseLessons.size else 0f
                    val isDone = courseLessons.isNotEmpty() && completedInCourse == courseLessons.size
                    val hasStarted = completedInCourse > 0 && !isDone
                    val accent = levelColor[course.level] ?: BrandGreen

                    Card(
                        colors = CardDefaults.cardColors(containerColor = PanelDark),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onCourseSelected(course) }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(50.dp).background(accent.copy(alpha = 0.14f), RoundedCornerShape(15.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDone) {
                                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                                    } else {
                                        Icon(iconMap[course.iconName] ?: Icons.Filled.School, contentDescription = null, tint = accent)
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (language == "ur" && course.titleUrdu.isNotBlank()) course.titleUrdu else course.title,
                                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                                    )
                                    Text(course.description, color = BrandSilverDim, fontSize = 11.sp, maxLines = 1)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        course.level.replaceFirstChar { it.uppercase() },
                                        color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.background(accent.copy(alpha = 0.12f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                    if (hasStarted) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            if (language == "ur") "جاری" else "In progress",
                                            color = Color(0xFFE3B934), fontSize = 9.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (isDone) {
                                        Spacer(Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(12.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text(if (language == "ur") "مکمل" else "Done", color = BrandGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            if (courseLessons.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(LineSubtle)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(courseProgress.coerceIn(0f, 1f))
                                                .background(if (isDone) BrandGreen else accent, RoundedCornerShape(3.dp))
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text("$completedInCourse/${courseLessons.size}", color = BrandSilverDim, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
