package com.mitv.trademaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitv.trademaster.achievements.Achievement
import com.mitv.trademaster.achievements.AchievementEngine
import com.mitv.trademaster.data.AuthRepository
import com.mitv.trademaster.data.FirestoreRepository
import com.mitv.trademaster.data.SessionRepository
import com.mitv.trademaster.data.SessionState
import com.mitv.trademaster.data.model.StudentProfile
import com.mitv.trademaster.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class PerformanceTab { STATS, BADGES, CERTIFICATE }

@Composable
fun PerformanceScreen(language: String) {
    val context = LocalContext.current
    val authRepo = remember { AuthRepository(context) }
    val repo = remember { FirestoreRepository() }
    val sessionRepo = remember { SessionRepository(context) }
    val tapFeedback = com.mitv.trademaster.util.rememberTapFeedback()

    var tab by remember { mutableStateOf(PerformanceTab.STATS) }
    var profile by remember { mutableStateOf<StudentProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val session by sessionRepo.session.collectAsState(initial = SessionState())

    LaunchedEffect(Unit) {
        val uid = authRepo.currentUser?.uid
        if (uid != null) {
            profile = try { withContext(Dispatchers.IO) { repo.getStudentProfile(uid) } } catch (e: Exception) { null }
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Insights, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (language == "ur") "کارکردگی اور کامیابیاں" else "Performance & Achievements", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(PanelDark, RoundedCornerShape(12.dp)).padding(4.dp)) {
            PerfTabChip(if (language == "ur") "اعداد و شمار" else "Stats", Icons.Filled.BarChart, tab == PerformanceTab.STATS, Modifier.weight(1f)) { tapFeedback(); tab = PerformanceTab.STATS }
            PerfTabChip(if (language == "ur") "بیجز" else "Badges", Icons.Filled.EmojiEvents, tab == PerformanceTab.BADGES, Modifier.weight(1f)) { tapFeedback(); tab = PerformanceTab.BADGES }
            PerfTabChip(if (language == "ur") "سرٹیفکیٹ" else "Certificate", Icons.Filled.WorkspacePremium, tab == PerformanceTab.CERTIFICATE, Modifier.weight(1f)) { tapFeedback(); tab = PerformanceTab.CERTIFICATE }
        }

        Spacer(Modifier.height(6.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandGreen) }
        } else {
            val p = profile ?: StudentProfile()
            val quizPassedCount = session.quizPassedCourseIds.size
            when (tab) {
                PerformanceTab.STATS -> StatsTab(p, quizPassedCount, language)
                PerformanceTab.BADGES -> BadgesTab(p, quizPassedCount, language)
                PerformanceTab.CERTIFICATE -> CertificateTab(p, quizPassedCount, language)
            }
        }
    }
}

@Composable
private fun PerfTabChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) BrandGreen.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onClick() }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) BrandGreen else BrandSilverDim, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) BrandGreen else BrandSilverDim, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// =========================================================================
// STATS TAB — AI-style performance summary derived from tracked activity
// =========================================================================

@Composable
private fun StatsTab(profile: StudentProfile, quizPassedCount: Int, language: String) {
    val winRate = if (profile.practiceTradesTotal > 0) (profile.practiceTradesWon * 100 / profile.practiceTradesTotal) else 0
    val insight = buildPerformanceInsight(profile, winRate, language)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = BrandGreen.copy(alpha = 0.08f)), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, BrandGreen.copy(alpha = 0.25f)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(if (language == "ur") "AI کارکردگی کا خلاصہ" else "AI Performance Summary", color = BrandGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(insight, color = BrandSilver, fontSize = 12.5.sp, lineHeight = 19.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox(Modifier.weight(1f), if (language == "ur") "اسباق مکمل" else "Lessons Done", profile.lessonsCompleted.toString(), Icons.Filled.MenuBook, Color(0xFF4FD1C5))
            StatBox(Modifier.weight(1f), if (language == "ur") "کورسز مکمل" else "Courses Passed", quizPassedCount.toString(), Icons.Filled.WorkspacePremium, Color(0xFFE3B934))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox(Modifier.weight(1f), if (language == "ur") "چارٹ تجزیے" else "Analyses Run", profile.analysesRun.toString(), Icons.Filled.Insights, Color(0xFF22C55E))
            StatBox(Modifier.weight(1f), if (language == "ur") "جرنل اندراجات" else "Journal Entries", profile.journalEntriesLogged.toString(), Icons.Filled.Edit, Color(0xFF8A97A8))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox(Modifier.weight(1f), if (language == "ur") "پریکٹس ٹریڈز" else "Practice Trades", profile.practiceTradesTotal.toString(), Icons.Filled.CandlestickChart, Color(0xFFE9192C))
            StatBox(Modifier.weight(1f), if (language == "ur") "کامیابی کی شرح" else "Win Rate", if (profile.practiceTradesTotal > 0) "$winRate%" else "—", Icons.Filled.TrendingUp, if (winRate >= 50) BrandGreen else Color(0xFFE3B934))
        }

        Spacer(Modifier.height(60.dp))
    }
}

/** Simple rule-based "AI-style" summary — no external API call needed, purely derived from tracked stats. */
private fun buildPerformanceInsight(profile: StudentProfile, winRate: Int, language: String): String {
    val hasPractice = profile.practiceTradesTotal >= 5
    val hasJournal = profile.journalEntriesLogged >= 1
    val hasCourses = profile.lessonsCompleted >= 1

    return when {
        !hasCourses -> if (language == "ur") "ابھی شروعات کریں! پہلا سبق مکمل کرکے اپنی سیکھنے کی رفتار قائم کریں۔" else "You're just getting started! Complete your first lesson to build momentum."
        hasPractice && winRate >= 60 -> if (language == "ur") "بہترین! آپ کی پریکٹس کامیابی کی شرح $winRate% ہے — مضبوط پیٹرن پہچان ظاہر کرتی ہے۔ جرنل میں اپنی جیتنے والی ٹریڈز کی وجوہات نوٹ کرتے رہیں۔" else "Excellent! Your practice win rate of $winRate% shows strong pattern recognition. Keep logging why your winning trades work in your journal."
        hasPractice && winRate < 40 -> if (language == "ur") "آپ کی پریکٹس کامیابی کی شرح $winRate% ہے — سبق دوبارہ دیکھیں اور رسک کیلکولیٹر استعمال کریں تاکہ سائز بہتر ہو۔" else "Your practice win rate is $winRate% — consider revisiting the lessons and using the Risk Calculator to size trades better."
        !hasJournal && hasPractice -> if (language == "ur") "آپ پریکٹس کر رہے ہیں لیکن ابھی تک جرنل استعمال نہیں کیا — ٹریڈز لاگ کرنے سے آپ کے پیٹرن واضح ہوں گے۔" else "You're practicing but haven't used the Journal yet — logging trades will reveal your patterns over time."
        else -> if (language == "ur") "اچھی پیش رفت جاری ہے۔ مزید کورسز مکمل کریں اور پریکٹس موڈ میں اپنی مہارت آزماتے رہیں۔" else "Good progress so far. Keep completing courses and testing your skills in Practice mode."
    }
}

@Composable
private fun StatBox(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(label, color = BrandSilverDim, fontSize = 10.sp)
        }
    }
}

// =========================================================================
// BADGES TAB
// =========================================================================

@Composable
private fun BadgesTab(profile: StudentProfile, quizPassedCount: Int, language: String) {
    val unlockedCount = AchievementEngine.unlockedCount(profile, quizPassedCount)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
            Text("$unlockedCount", color = Color(0xFFE3B934), fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("/${AchievementEngine.all.size}", color = BrandSilverDim, fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Text(if (language == "ur") "بیجز حاصل" else "badges earned", color = BrandSilverDim, fontSize = 12.sp)
        }

        AchievementEngine.all.forEach { achievement ->
            val unlocked = achievement.isUnlocked(profile, quizPassedCount)
            AchievementCard(achievement, unlocked, language)
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun AchievementCard(achievement: Achievement, unlocked: Boolean, language: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelDark), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (unlocked) 1f else 0.45f)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(achievement.color.copy(alpha = if (unlocked) 0.18f else 0.08f)), contentAlignment = Alignment.Center) {
                Icon(if (unlocked) achievement.icon else Icons.Filled.Lock, contentDescription = null, tint = if (unlocked) achievement.color else BrandSilverDim, modifier = Modifier.size(if (unlocked) 22.dp else 18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (language == "ur") achievement.titleUr else achievement.titleEn, color = if (unlocked) Color.White else BrandSilverDim, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(if (language == "ur") achievement.descriptionUr else achievement.descriptionEn, color = BrandSilverDim, fontSize = 11.sp, lineHeight = 15.sp)
            }
            if (unlocked) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// =========================================================================
// CERTIFICATE TAB
// =========================================================================

@Composable
private fun CertificateTab(profile: StudentProfile, quizPassedCount: Int, language: String) {
    val hasEarnedAny = quizPassedCount >= 1
    val studentName = profile.studentName.ifBlank { if (language == "ur") "طالب علم" else "Student" }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!hasEarnedAny) {
            Spacer(Modifier.height(40.dp))
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = BrandSilverDim, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(14.dp))
            Text(
                if (language == "ur") "ابھی تک کوئی سرٹیفکیٹ نہیں — کوئی بھی کورس مکمل کرکے اس کا کوئز پاس کریں تاکہ آپ کا سرٹیفکیٹ جاری ہو"
                else "No certificate yet — complete a course and pass its quiz to earn your first certificate",
                color = BrandSilverDim, fontSize = 12.5.sp, textAlign = TextAlign.Center
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelDark),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE3B934)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE3B934).copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color(0xFFE3B934), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (language == "ur") "سند تکمیل" else "CERTIFICATE OF COMPLETION",
                        color = Color(0xFFE3B934), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(if (language == "ur") "یہ سند دی جاتی ہے:" else "This certifies that", color = BrandSilverDim, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(studentName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (language == "ur") "نے MI Trade Master میں $quizPassedCount کورس(ز) کامیابی سے مکمل کیے"
                        else "has successfully completed $quizPassedCount course${if (quizPassedCount != 1) "s" else ""} on MI Trade Master",
                        color = BrandSilver, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(color = LineSubtle, modifier = Modifier.width(140.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("MI TRADE MASTER", color = BrandGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
                        color = BrandSilverDim, fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (language == "ur") "مزید کورسز مکمل کرنے پر یہ سند خودکار طور پر اپڈیٹ ہو جائے گی۔"
                else "This certificate updates automatically as you complete more courses.",
                color = BrandSilverDim.copy(alpha = 0.7f), fontSize = 10.sp, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(60.dp))
    }
}
