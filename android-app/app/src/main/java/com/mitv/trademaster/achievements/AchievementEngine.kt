package com.mitv.trademaster.achievements

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mitv.trademaster.data.model.StudentProfile

data class Achievement(
    val id: String,
    val titleEn: String,
    val titleUr: String,
    val descriptionEn: String,
    val descriptionUr: String,
    val icon: ImageVector,
    val color: Color,
    val isUnlocked: (StudentProfile, quizPassedCount: Int) -> Boolean,
)

/**
 * All badges are computed live from data the app already tracks — no
 * separate "achievements" collection to keep in sync. This means a badge
 * can never drift out of sync with the stat it represents, and there's
 * nothing new to migrate if a stat's definition changes later.
 */
object AchievementEngine {

    val all: List<Achievement> = listOf(
        Achievement(
            id = "first_lesson",
            titleEn = "First Step", titleUr = "پہلا قدم",
            descriptionEn = "Complete your first lesson", descriptionUr = "اپنا پہلا سبق مکمل کریں",
            icon = Icons.Filled.School, color = Color(0xFF4FD1C5),
            isUnlocked = { profile, _ -> profile.lessonsCompleted >= 1 }
        ),
        Achievement(
            id = "ten_lessons",
            titleEn = "Dedicated Learner", titleUr = "لگن والا طالب علم",
            descriptionEn = "Complete 10 lessons", descriptionUr = "10 اسباق مکمل کریں",
            icon = Icons.Filled.MenuBook, color = Color(0xFF4FD1C5),
            isUnlocked = { profile, _ -> profile.lessonsCompleted >= 10 }
        ),
        Achievement(
            id = "first_course",
            titleEn = "Course Graduate", titleUr = "کورس گریجویٹ",
            descriptionEn = "Pass your first course quiz", descriptionUr = "اپنا پہلا کورس کوئز پاس کریں",
            icon = Icons.Filled.WorkspacePremium, color = Color(0xFFE3B934),
            isUnlocked = { _, quizPassedCount -> quizPassedCount >= 1 }
        ),
        Achievement(
            id = "three_courses",
            titleEn = "Rising Trader", titleUr = "ابھرتا ہوا ٹریڈر",
            descriptionEn = "Complete 3 courses", descriptionUr = "3 کورسز مکمل کریں",
            icon = Icons.Filled.EmojiEvents, color = Color(0xFFE3B934),
            isUnlocked = { _, quizPassedCount -> quizPassedCount >= 3 }
        ),
        Achievement(
            id = "first_analysis",
            titleEn = "Chart Reader", titleUr = "چارٹ ریڈر",
            descriptionEn = "Run your first chart analysis", descriptionUr = "اپنا پہلا چارٹ تجزیہ چلائیں",
            icon = Icons.Filled.Insights, color = Color(0xFF22C55E),
            isUnlocked = { profile, _ -> profile.analysesRun >= 1 }
        ),
        Achievement(
            id = "twentyfive_analysis",
            titleEn = "Pattern Hunter", titleUr = "پیٹرن ہنٹر",
            descriptionEn = "Run 25 chart analyses", descriptionUr = "25 چارٹ تجزیے چلائیں",
            icon = Icons.Filled.Insights, color = Color(0xFF22C55E),
            isUnlocked = { profile, _ -> profile.analysesRun >= 25 }
        ),
        Achievement(
            id = "first_practice",
            titleEn = "Practice Makes Perfect", titleUr = "مشق سے کمال",
            descriptionEn = "Complete your first practice trade", descriptionUr = "اپنی پہلی پریکٹس ٹریڈ مکمل کریں",
            icon = Icons.Filled.CandlestickChart, color = Color(0xFFE9192C),
            isUnlocked = { profile, _ -> profile.practiceTradesTotal >= 1 }
        ),
        Achievement(
            id = "fifty_practice_wins",
            titleEn = "Sharp Shooter", titleUr = "شارپ شوٹر",
            descriptionEn = "Win 50 practice trades", descriptionUr = "50 پریکٹس ٹریڈز جیتیں",
            icon = Icons.Filled.MilitaryTech, color = Color(0xFFE9192C),
            isUnlocked = { profile, _ -> profile.practiceTradesWon >= 50 }
        ),
        Achievement(
            id = "first_journal",
            titleEn = "Reflective Trader", titleUr = "خود احتسابی ٹریڈر",
            descriptionEn = "Log your first journal entry", descriptionUr = "اپنی پہلی جرنل اندراج لاگ کریں",
            icon = Icons.Filled.MenuBook, color = Color(0xFF8A97A8),
            isUnlocked = { profile, _ -> profile.journalEntriesLogged >= 1 }
        ),
        Achievement(
            id = "ten_journal",
            titleEn = "Disciplined Journalist", titleUr = "نظم و ضبط والا جرنلسٹ",
            descriptionEn = "Log 10 journal entries", descriptionUr = "10 جرنل اندراجات لاگ کریں",
            icon = Icons.Filled.MenuBook, color = Color(0xFF8A97A8),
            isUnlocked = { profile, _ -> profile.journalEntriesLogged >= 10 }
        ),
        Achievement(
            id = "all_courses",
            titleEn = "MI Trade Master", titleUr = "ایم آئی ٹریڈ ماسٹر",
            descriptionEn = "Complete all available courses", descriptionUr = "تمام دستیاب کورسز مکمل کریں",
            icon = Icons.Filled.Diamond, color = Color(0xFFE3B934),
            isUnlocked = { profile, _ -> profile.coursesCompletedCount >= 5 && profile.coursesCompletedCount > 0 }
        ),
    )

    fun unlockedCount(profile: StudentProfile, quizPassedCount: Int): Int =
        all.count { it.isUnlocked(profile, quizPassedCount) }
}
