package com.mitv.trademaster.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.model.Course
import com.mitv.trademaster.data.model.Lesson
import com.mitv.trademaster.ui.theme.*
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayerView
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(course.id) {
        lessons = try { repo.getLessons(course.id) } catch (e: Exception) { emptyList() }
        selectedLesson = lessons.firstOrNull()
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = BrandSilver) }
            Spacer(Modifier.width(8.dp))
            Text(if (language == "ur" && course.titleUrdu.isNotBlank()) course.titleUrdu else course.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                item {
                    if (lesson.youtubeVideoId.isNotBlank()) {
                        YouTubeEmbed(videoId = lesson.youtubeVideoId)
                        Spacer(Modifier.height(16.dp))
                    }

                    Text(
                        if (language == "ur" && lesson.titleUrdu.isNotBlank()) lesson.titleUrdu else lesson.title,
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (language == "ur" && lesson.contentTextUrdu.isNotBlank()) lesson.contentTextUrdu else lesson.contentText,
                        color = BrandSilverDim, fontSize = 13.sp, lineHeight = 20.sp
                    )

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

                    Spacer(Modifier.height(24.dp))
                    Text(if (language == "ur") "دیگر اسباق" else "Other Lessons", color = BrandSilverDim, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                }

                items(lessons) { l ->
                    val isSelected = l.id == selectedLesson?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(if (isSelected) BrandGreen.copy(alpha = 0.1f) else PanelDark, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.size(8.dp).background(if (isSelected) BrandGreen else BrandSilverDim, androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (language == "ur" && l.titleUrdu.isNotBlank()) l.titleUrdu else l.title,
                            color = if (isSelected) BrandGreen else BrandSilver,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f).clickableNoIndication { selectedLesson = l }
                        )
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun YouTubeEmbed(videoId: String) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        factory = { context ->
            YTPlayerView(context).apply {
                lifecycleOwner.lifecycle.addObserver(this)
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer) {
                        youTubePlayer.cueVideo(videoId, 0f)
                    }
                })
            }
        }
    )
}

@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    )
