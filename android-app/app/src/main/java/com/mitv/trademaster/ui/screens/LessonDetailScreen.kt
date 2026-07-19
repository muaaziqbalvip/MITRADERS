package com.mitv.trademaster.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.SessionRepository
import com.mitv.trademaster.data.SessionState
import com.mitv.trademaster.data.model.Course
import com.mitv.trademaster.data.model.Lesson
import com.mitv.trademaster.ui.theme.*
import com.mitv.trademaster.ui.components.Mp4LessonPlayer
import kotlinx.coroutines.launch

/**
 * If [resumeLessonId] is provided (e.g. from Home's "Continue where you left
 * off" card), that lesson opens first instead of lesson #1.
 */
@Composable
fun LessonDetailScreen(course: Course, language: String, onBack: () -> Unit, resumeLessonId: String? = null) {
    val context = LocalContext.current
    val repo = remember { FirestoreRepository() }
    val authRepo = remember { AuthRepository(context) }
    val sessionRepo = remember { SessionRepository(context) }
    val soundManager = com.mitv.trademaster.util.rememberSoundManager()
    val session by sessionRepo.session.collectAsState(initial = SessionState())

    var lessons by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var selectedLesson by remember { mutableStateOf<Lesson?>(null) }
    var showLessonList by remember { mutableStateOf(false) }
    var justCompleted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(course.id) {
        var hasSetInitialLesson = false
        repo.observeLessons(course.id).collect { loaded ->
            lessons = loaded
            if (!hasSetInitialLesson) {
                val resumeTarget = resumeLessonId?.let { id -> loaded.find { it.id == id } }
                selectedLesson = resumeTarget ?: loaded.firstOrNull()
                hasSetInitialLesson = true
            } else {
                // Keep viewing the same lesson (now with live-updated content) if it still exists.
                selectedLesson = loaded.find { it.id == selectedLesson?.id } ?: selectedLesson
            }
        }
    }

    // Remember "last viewed lesson" for this course whenever the student switches lessons,
    // so Home can show a "Continue where you left off" card.
    LaunchedEffect(selectedLesson?.id) {
        selectedLesson?.let { sessionRepo.setLastLesson(course.id, it.id) }
        justCompleted = false
    }

    val completedCount = lessons.count { it.id in session.completedLessonIds }
    val courseProgress = if (lessons.isNotEmpty()) completedCount.toFloat() / lessons.size else 0f
    val animatedProgress by animateFloatAsState(targetValue = courseProgress, animationSpec = tween(500), label = "lessonProgress")

    Column(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        // ---------- Header with course progress bar ----------
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = BrandSilver) }
                Spacer(Modifier.width(2.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (language == "ur" && course.titleUrdu.isNotBlank()) course.titleUrdu else course.title,
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                    if (lessons.isNotEmpty()) {
                        Text(
                            "$completedCount/${lessons.size} " + (if (language == "ur") "مکمل" else "completed"),
                            color = BrandSilverDim, fontSize = 10.sp
                        )
                    }
                }
                if (lessons.size > 1) {
                    IconButton(onClick = { showLessonList = true }) { Icon(Icons.Filled.MenuBook, contentDescription = null, tint = BrandGreen) }
                }
            }
            if (lessons.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(LineSubtle)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .background(BrandGreen, RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        if (lessons.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (language == "ur") "اس کورس میں ابھی کوئی سبق شامل نہیں" else "No lessons in this course yet",
                    color = BrandSilverDim, fontSize = 13.sp
                )
            }
            return@Column
        }

        selectedLesson?.let { lesson ->
            val lessonIndex = lessons.indexOf(lesson)
            val isLessonDone = lesson.id in session.completedLessonIds
            val wordCount = remember(lesson.id, language) {
                val text = if (language == "ur" && lesson.contentTextUrdu.isNotBlank()) lesson.contentTextUrdu else lesson.contentText
                text.split(Regex("\\s+")).count { it.isNotBlank() }
            }
            val readMinutes = (wordCount / 180).coerceAtLeast(1)

            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                item {
                    if (lesson.videoUrlEn.isNotBlank() || lesson.videoUrlUr.isNotBlank()) {
                        Mp4LessonPlayer(
                            videoUrlEn = lesson.videoUrlEn,
                            videoUrlUr = lesson.videoUrlUr,
                            posterUrl = lesson.videoThumbUrl,
                            language = language
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    // Book-page style card for lesson text
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PanelDark),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(22.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    (if (language == "ur") "سبق " else "Lesson ") + "${lessonIndex + 1} / ${lessons.size}",
                                    color = BrandGreenDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "$readMinutes " + (if (language == "ur") "منٹ" else "min read"),
                                    color = BrandSilverDim, fontSize = 10.sp
                                )
                                if (isLessonDone) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (language == "ur" && lesson.titleUrdu.isNotBlank()) lesson.titleUrdu else lesson.title,
                                color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = LineSubtle, thickness = 1.dp)
                            Spacer(Modifier.height(16.dp))

                            val bodyText = if (language == "ur" && lesson.contentTextUrdu.isNotBlank()) lesson.contentTextUrdu else lesson.contentText
                            bodyText.split("\n").filter { it.isNotBlank() }.forEach { paragraph ->
                                Text(
                                    paragraph.trim(),
                                    color = BrandSilver,
                                    fontSize = 14.sp,
                                    lineHeight = 24.sp,
                                    fontFamily = FontFamily.Serif,
                                    modifier = Modifier.padding(bottom = 14.dp)
                                )
                            }

                            if (bodyText.isBlank()) {
                                Text(
                                    if (language == "ur") "اس سبق کا متن ابھی شامل نہیں کیا گیا۔" else "No text content has been added for this lesson yet.",
                                    color = BrandSilverDim, fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }

                    if (lesson.pdfUrl.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lesson.pdfUrl))) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandGreen),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandGreenDim),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (language == "ur") "PDF کتاب کھولیں" else "Open PDF Book")
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Mark-complete button: reflects real completion state, celebrates on first completion.
                    Button(
                        onClick = {
                            if (isLessonDone) return@Button
                            val uid = authRepo.currentUser?.uid ?: return@Button
                            scope.launch {
                                val wasNew = sessionRepo.markLessonComplete(lesson.id)
                                if (wasNew) {
                                    repo.incrementLessonsCompleted(uid)
                                    justCompleted = true
                                    soundManager.playSuccess()
                                }
                            }
                        },
                        colors = if (isLessonDone)
                            ButtonDefaults.buttonColors(containerColor = BrandGreen.copy(alpha = 0.9f), contentColor = Color(0xFF04120B))
                        else
                            ButtonDefaults.buttonColors(containerColor = BrandGreen.copy(alpha = 0.15f), contentColor = BrandGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(if (isLessonDone) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isLessonDone)
                                (if (language == "ur") "مکمل ہو گیا ✓" else "Completed ✓")
                            else
                                (if (language == "ur") "مکمل شدہ نشان زد کریں" else "Mark as Completed")
                        )
                    }

                    AnimatedVisibility(visible = justCompleted, enter = fadeIn(), exit = fadeOut()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        ) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFE3B934), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (language == "ur") "شاباش! اگلے سبق کی طرف بڑھیں۔" else "Nice work! Ready for the next lesson.",
                                color = Color(0xFFE3B934), fontSize = 12.sp
                            )
                        }
                    }

                    // Prev / Next navigation
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { if (lessonIndex > 0) selectedLesson = lessons[lessonIndex - 1] },
                            enabled = lessonIndex > 0,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandSilver),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LineSubtle),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Filled.ArrowBackIos, contentDescription = null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(if (language == "ur") "پچھلا" else "Previous", fontSize = 12.sp) }

                        OutlinedButton(
                            onClick = { if (lessonIndex < lessons.size - 1) selectedLesson = lessons[lessonIndex + 1] },
                            enabled = lessonIndex < lessons.size - 1,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandSilver),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LineSubtle),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text(if (language == "ur") "اگلا" else "Next", fontSize = 12.sp); Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    }

                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    if (showLessonList) {
        LessonListSheet(
            lessons = lessons, selectedLesson = selectedLesson, language = language,
            completedIds = session.completedLessonIds,
            onDismiss = { showLessonList = false },
            onSelect = { selectedLesson = it; showLessonList = false }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LessonListSheet(
    lessons: List<Lesson>,
    selectedLesson: Lesson?,
    language: String,
    completedIds: Set<String>,
    onDismiss: () -> Unit,
    onSelect: (Lesson) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelDark) {
        Column(Modifier.padding(20.dp)) {
            Text(if (language == "ur") "اسباق" else "Lessons", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
            lessons.forEachIndexed { i, l ->
                val isSelected = l.id == selectedLesson?.id
                val isDone = l.id in completedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) BrandGreen.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { onSelect(l) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDone) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(16.dp))
                    } else {
                        Box(modifier = Modifier.size(8.dp).background(if (isSelected) BrandGreen else BrandSilverDim, CircleShape))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${i + 1}. ", color = BrandSilverDim, fontSize = 12.sp)
                    Text(
                        if (language == "ur" && l.titleUrdu.isNotBlank()) l.titleUrdu else l.title,
                        color = if (isSelected) BrandGreen else if (isDone) BrandSilver else BrandSilver, fontSize = 13.sp, modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
