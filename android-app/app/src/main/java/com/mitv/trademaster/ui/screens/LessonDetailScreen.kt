package com.mitv.trademaster.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.model.Course
import com.mitv.trademaster.data.model.Lesson
import com.mitv.trademaster.ui.theme.*
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView as YTPlayerView
import kotlinx.coroutines.launch

@Composable
fun LessonDetailScreen(course: Course, language: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { FirestoreRepository() }
    val authRepo = remember { AuthRepository(context) }
    var lessons by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var selectedLesson by remember { mutableStateOf<Lesson?>(null) }
    var showLessonList by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(course.id) {
        lessons = try { repo.getLessons(course.id) } catch (e: Exception) { emptyList() }
        selectedLesson = lessons.firstOrNull()
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = BrandSilver) }
            Spacer(Modifier.width(4.dp))
            Text(
                if (language == "ur" && course.titleUrdu.isNotBlank()) course.titleUrdu else course.title,
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f)
            )
            if (lessons.size > 1) {
                IconButton(onClick = { showLessonList = true }) { Icon(Icons.Filled.MenuBook, contentDescription = null, tint = BrandGreen) }
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
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                item {
                    if (lesson.youtubeVideoId.isNotBlank()) {
                        YouTubeEmbedWithFallback(videoId = lesson.youtubeVideoId, language = language)
                        Spacer(Modifier.height(20.dp))
                    }

                    // Book-page style card for lesson text
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PanelDark),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(22.dp)) {
                            // Chapter marker
                            Text(
                                (if (language == "ur") "سبق " else "Lesson ") + "${lessonIndex + 1} / ${lessons.size}",
                                color = BrandGreenDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (language == "ur" && lesson.titleUrdu.isNotBlank()) lesson.titleUrdu else lesson.title,
                                color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = LineSubtle, thickness = 1.dp)
                            Spacer(Modifier.height(16.dp))

                            val bodyText = if (language == "ur" && lesson.contentTextUrdu.isNotBlank()) lesson.contentTextUrdu else lesson.contentText
                            // Book-style paragraphs: split on blank lines / newlines and render each with spacing
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
                    Button(
                        onClick = {
                            val uid = authRepo.currentUser?.uid ?: return@Button
                            scope.launch { repo.incrementLessonsCompleted(uid) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen.copy(alpha = 0.15f), contentColor = BrandGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (language == "ur") "مکمل شدہ نشان زد کریں" else "Mark as Completed")
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
            onDismiss = { showLessonList = false },
            onSelect = { selectedLesson = it; showLessonList = false }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LessonListSheet(lessons: List<Lesson>, selectedLesson: Lesson?, language: String, onDismiss: () -> Unit, onSelect: (Lesson) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelDark) {
        Column(Modifier.padding(20.dp)) {
            Text(if (language == "ur") "اسباق" else "Lessons", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
            lessons.forEachIndexed { i, l ->
                val isSelected = l.id == selectedLesson?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) BrandGreen.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { onSelect(l) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(if (isSelected) BrandGreen else BrandSilverDim, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text("${i + 1}. ", color = BrandSilverDim, fontSize = 12.sp)
                    Text(
                        if (language == "ur" && l.titleUrdu.isNotBlank()) l.titleUrdu else l.title,
                        color = if (isSelected) BrandGreen else BrandSilver, fontSize = 13.sp, modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun YouTubeEmbedWithFallback(videoId: String, language: String) {
    var hasError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    if (hasError) {
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp).background(PanelDark, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.SmartDisplay, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    if (language == "ur") "ویڈیو یہاں چل نہیں سکی" else "This video couldn't play here",
                    color = BrandSilverDim, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandGreen),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandGreenDim),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (language == "ur") "یوٹیوب پر دیکھیں" else "Watch on YouTube", fontSize = 12.sp)
                }
            }
        }
        return
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        factory = { ctx ->
            YTPlayerView(ctx).apply {
                lifecycleOwner.lifecycle.addObserver(this)
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer) {
                        youTubePlayer.cueVideo(videoId, 0f)
                    }
                    override fun onError(
                        youTubePlayer: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer,
                        error: PlayerConstants.PlayerError
                    ) {
                        // Video unavailable, embedding disabled, or region-restricted.
                        // Fall back to an "open on YouTube" link instead of a broken player.
                        hasError = true
                    }
                })
            }
        }
    )
}
