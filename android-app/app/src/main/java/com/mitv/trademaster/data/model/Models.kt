package com.mitv.trademaster.data.model

/**
 * Full student profile stored in Firestore at students/{uid}.
 * Photo fields store ImgBB URLs (not Firebase Storage — free plan).
 */
data class StudentProfile(
    val uid: String = "",
    val studentName: String = "",
    val fatherName: String = "",
    val studentIdCardNumber: String = "",
    val fatherAddress: String = "",
    val qualification: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String = "",          // ImgBB URL
    val idCardPhotoUrl: String = "",    // ImgBB URL (optional ID card photo)
    val preferredLanguage: String = "en", // en | ur
    val createdAt: Long = 0L,

    // Subscription
    val subscriptionStatus: String = "pending", // pending | active | expired
    val subscriptionActivatedAt: Long? = null,
    val subscriptionExpiresAt: Long? = null,
    val lastPaymentNote: String = "",

    // Activity tracking (for admin dashboard)
    val lastActiveAt: Long = 0L,
    val lessonsCompleted: Int = 0,
    val analysesRun: Int = 0,
)

data class Course(
    val id: String = "",
    val title: String = "",
    val titleUrdu: String = "",
    val description: String = "",
    val level: String = "beginner", // beginner | intermediate | advanced
    val order: Int = 0,
    val iconName: String = "School",
)

data class Lesson(
    val id: String = "",
    val courseId: String = "",
    val title: String = "",
    val titleUrdu: String = "",
    val contentText: String = "",       // lesson body, written by admin
    val contentTextUrdu: String = "",
    val videoUrlEn: String = "",        // direct MP4 link, English narration — empty if no video
    val videoUrlUr: String = "",        // direct MP4 link, Urdu narration — empty if no video
    val videoThumbUrl: String = "",     // optional poster image shown before play (ImgBB)
    val pdfUrl: String = "",            // ImgBB doesn't host PDFs; use an external link (Drive, etc.)
    val order: Int = 0,
)

data class PaymentSubmission(
    val id: String = "",
    val uid: String = "",
    val studentName: String = "",
    val screenshotUrl: String = "",     // ImgBB URL of payment proof
    val submittedAt: Long = 0L,
    val status: String = "pending",     // pending | approved | rejected
    val adminNote: String = "",
)

data class ChatMessage(
    val id: String = "",
    val uid: String = "",
    val sender: String = "user",        // user | ai | admin
    val text: String = "",
    val imageUrl: String = "",          // ImgBB URL if this message included a chart screenshot
    val timestamp: Long = 0L,
)

data class Announcement(
    val id: String = "",
    val title: String = "",
    val titleUrdu: String = "",
    val message: String = "",
    val messageUrdu: String = "",
    val createdAt: Long = 0L,
    val iconName: String = "Campaign", // Campaign | NewReleases | Star | Warning
)

data class PracticeTrade(
    val id: String = "",
    val uid: String = "",
    val direction: String = "",         // up | down
    val outcome: String = "",           // win | loss | pending
    val timestamp: Long = 0L,
)

data class QuizQuestion(
    val question: String = "",
    val questionUrdu: String = "",
    val options: List<String> = emptyList(),
    val optionsUrdu: List<String> = emptyList(),
    val correctIndex: Int = 0,
    val explanation: String = "",
    val explanationUrdu: String = "",
)

/**
 * A generated quiz for one course, cached at courses/{courseId}/quiz/generated
 * so the Groq call only happens once per course (first student to finish
 * triggers generation; everyone after reuses the saved copy).
 */
data class CourseQuiz(
    val courseId: String = "",
    val questions: List<QuizQuestion> = emptyList(),
    val generatedAt: Long = 0L,
)

/** A student's personal trading journal entry — stored per-user in Firestore under students/{uid}/journal. */
data class JournalEntry(
    val id: String = "",
    val uid: String = "",
    val pair: String = "",
    val direction: String = "",       // buy | sell
    val entryPrice: String = "",
    val exitPrice: String = "",
    val lotSize: String = "",
    val outcome: String = "",         // win | loss | breakeven | open
    val profitLoss: String = "",
    val notes: String = "",
    val emotionTag: String = "",      // confident | anxious | fomo | disciplined | revenge | neutral
    val timestamp: Long = 0L,
)
