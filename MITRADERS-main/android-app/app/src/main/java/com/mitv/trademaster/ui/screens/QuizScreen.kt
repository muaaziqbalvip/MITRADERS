package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.SessionRepository
import com.mitv.trademaster.data.model.Course
import com.mitv.trademaster.data.model.CourseQuiz
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.launch

private const val PASS_THRESHOLD = 0.7f // 70% correct required to pass

private sealed class QuizLoadState {
    data object Loading : QuizLoadState()
    data class Error(val message: String) : QuizLoadState()
    data class NotReady(val message: String) : QuizLoadState()
    data class Ready(val quiz: CourseQuiz) : QuizLoadState()
}

@Composable
fun QuizScreen(course: Course, language: String, onBack: () -> Unit, onPassed: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { FirestoreRepository() }
    val sessionRepo = remember { SessionRepository(context) }
    val soundManager = com.mitv.trademaster.util.rememberSoundManager()

    var loadState by remember { mutableStateOf<QuizLoadState>(QuizLoadState.Loading) }
    var answers by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) } // questionIndex -> selectedOption
    var submitted by remember { mutableStateOf(false) }
    var scorePercent by remember { mutableStateOf(0) }

    // The quiz is generated once, from the admin panel, using a Groq key that
    // never ships inside the app. The app only ever reads the cached result —
    // this avoids exposing the API key to every device and avoids every
    // student's phone independently calling Groq (slow + wasteful + the
    // permission-denied / mid-navigation-cancel crashes that happened when
    // generation ran on-device).
    suspend fun loadQuiz() {
        loadState = QuizLoadState.Loading
        try {
            val cached = repo.getCourseQuiz(course.id)
            loadState = if (cached != null && cached.questions.isNotEmpty()) {
                QuizLoadState.Ready(cached)
            } else {
                QuizLoadState.NotReady(
                    if (language == "ur") "کوئز ابھی تیار نہیں ہوا — جلد دستیاب ہوگا"
                    else "Quiz isn't ready yet — check back soon"
                )
            }
        } catch (e: Exception) {
            loadState = QuizLoadState.Error((if (language == "ur") "خرابی: " else "Error: ") + e.message)
        }
    }

    LaunchedEffect(course.id) {
        loadQuiz()
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = BrandSilver) }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(if (language == "ur") "کورس کوئز" else "Course Quiz", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    if (language == "ur" && course.titleUrdu.isNotBlank()) course.titleUrdu else course.title,
                    color = BrandSilverDim, fontSize = 11.sp
                )
            }
        }

        when (val state = loadState) {
            is QuizLoadState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandGreen)
                }
            }
            is QuizLoadState.NotReady -> {
                val scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.HourglassEmpty, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(state.message, color = BrandSilverDim, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { scope.launch { loadQuiz() } },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandGreen),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandGreenDim),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(if (language == "ur") "دوبارہ چیک کریں" else "Check Again") }
                    }
                }
            }
            is QuizLoadState.Error -> {
                val scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = BrandRed, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { scope.launch { loadQuiz() } },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(if (language == "ur") "دوبارہ کوشش کریں" else "Try Again") }
                    }
                }
            }
            is QuizLoadState.Ready -> {
                val quiz = state.quiz
                if (!submitted) {
                    QuizForm(
                        quiz = quiz, language = language, answers = answers,
                        onAnswer = { qIndex, optIndex ->
                            soundManager.playClick()
                            answers = answers + (qIndex to optIndex)
                        },
                        onSubmit = {
                            val correct = quiz.questions.indices.count { i -> answers[i] == quiz.questions[i].correctIndex }
                            scorePercent = (correct * 100 / quiz.questions.size)
                            submitted = true
                            if (scorePercent / 100f >= PASS_THRESHOLD) soundManager.playSuccess() else soundManager.playError()
                        }
                    )
                } else {
                    val scope = rememberCoroutineScope()
                    QuizResult(
                        quiz = quiz, language = language, answers = answers, scorePercent = scorePercent,
                        passed = scorePercent / 100f >= PASS_THRESHOLD,
                        onRetry = { submitted = false; answers = emptyMap() },
                        onContinue = {
                            soundManager.playClick()
                            scope.launch {
                                sessionRepo.markQuizPassed(course.id)
                                onPassed()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuizForm(
    quiz: CourseQuiz,
    language: String,
    answers: Map<Int, Int>,
    onAnswer: (Int, Int) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text(
            if (language == "ur") "${quiz.questions.size} سوالات — پاس ہونے کے لیے 70% درست چاہیے"
            else "${quiz.questions.size} questions — 70% correct needed to pass",
            color = BrandSilverDim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp)
        )

        quiz.questions.forEachIndexed { qIndex, q ->
            val qText = if (language == "ur") q.questionUrdu else q.question
            val opts = if (language == "ur") q.optionsUrdu else q.options
            val selected = answers[qIndex]

            Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("${qIndex + 1}. $qText", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    opts.forEachIndexed { optIndex, opt ->
                        val isSelected = selected == optIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(if (isSelected) BrandGreen.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { onAnswer(qIndex, optIndex) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(18.dp).clip(CircleShape)
                                    .background(if (isSelected) BrandGreen else PanelDark)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(opt, color = if (isSelected) Color.White else BrandSilver, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Button(
            onClick = onSubmit,
            enabled = answers.size == quiz.questions.size,
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(
                if (answers.size == quiz.questions.size)
                    (if (language == "ur") "کوئز جمع کریں" else "Submit Quiz")
                else
                    (if (language == "ur") "${answers.size}/${quiz.questions.size} جواب دیے گئے" else "${answers.size}/${quiz.questions.size} answered"),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun QuizResult(
    quiz: CourseQuiz,
    language: String,
    answers: Map<Int, Int>,
    scorePercent: Int,
    passed: Boolean,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    val accent = if (passed) BrandGreen else BrandRed

    Column(modifier = Modifier.fillMaxSize().fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(
                if (passed) Icons.Filled.WorkspacePremium else Icons.Filled.Replay,
                contentDescription = null, tint = accent, modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("$scorePercent%", color = accent, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(
            if (passed) (if (language == "ur") "مبارک ہو! آپ پاس ہو گئے" else "Congratulations! You passed")
            else (if (language == "ur") "اتنے نمبر کافی نہیں — دوبارہ کوشش کریں" else "Not quite enough — give it another try"),
            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))

        quiz.questions.forEachIndexed { qIndex, q ->
            val qText = if (language == "ur") q.questionUrdu else q.question
            val opts = if (language == "ur") q.optionsUrdu else q.options
            val explanation = if (language == "ur") q.explanationUrdu else q.explanation
            val userAnswer = answers[qIndex]
            val wasCorrect = userAnswer == q.correctIndex

            Card(
                colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row {
                        Icon(
                            if (wasCorrect) Icons.Filled.CheckCircle else Icons.Filled.Lightbulb,
                            contentDescription = null, tint = if (wasCorrect) BrandGreen else Color(0xFFE3B934),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${qIndex + 1}. $qText", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (if (language == "ur") "درست جواب: " else "Correct answer: ") + opts.getOrElse(q.correctIndex) { "" },
                        color = BrandGreen, fontSize = 12.sp
                    )
                    if (explanation.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(explanation, color = BrandSilverDim, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (passed) {
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen, contentColor = Color(0xFF04120B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(if (language == "ur") "اگلا کورس کھولیں" else "Unlock Next Course", fontWeight = FontWeight.Bold) }
        } else {
            OutlinedButton(
                onClick = onRetry,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandGreen),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandGreenDim),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(if (language == "ur") "کوئز دوبارہ دیں" else "Retake Quiz", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(40.dp))
    }
}
