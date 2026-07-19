package com.mitv.trademaster.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.mitv.trademaster.data.model.ChatMessage
import com.mitv.trademaster.data.model.Course
import com.mitv.trademaster.data.model.Lesson
import com.mitv.trademaster.data.model.PaymentSubmission
import com.mitv.trademaster.data.model.StudentProfile
import kotlinx.coroutines.tasks.await

/**
 * All Firestore reads/writes go through this repository. Collections used:
 *   students/{uid}                -> StudentProfile
 *   courses/{courseId}            -> Course
 *   courses/{courseId}/lessons/{lessonId} -> Lesson
 *   payments/{paymentId}          -> PaymentSubmission
 *   chats/{uid}/messages/{msgId}  -> ChatMessage
 *
 * Security rules should restrict students/{uid} writes to the owning uid,
 * and courses/lessons/payments admin-write / student-read as appropriate —
 * see the admin.html README section for the exact rules to paste in.
 */
class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()

    // ------------------------------------------------------------------
    // Student profile
    // ------------------------------------------------------------------

    suspend fun getStudentProfile(uid: String): StudentProfile? {
        val snap = db.collection("students").document(uid).get().await()
        return snap.toObject<StudentProfile>()
    }

    suspend fun saveStudentProfile(profile: StudentProfile) {
        db.collection("students").document(profile.uid).set(profile).await()
    }

    suspend fun updateLastActive(uid: String) {
        db.collection("students").document(uid)
            .update("lastActiveAt", System.currentTimeMillis())
            .await()
    }

    suspend fun incrementAnalysesRun(uid: String) {
        db.collection("students").document(uid)
            .update("analysesRun", com.google.firebase.firestore.FieldValue.increment(1))
            .await()
    }

    suspend fun incrementLessonsCompleted(uid: String) {
        db.collection("students").document(uid)
            .update("lessonsCompleted", com.google.firebase.firestore.FieldValue.increment(1))
            .await()
    }

    // ------------------------------------------------------------------
    // Courses & lessons (admin-authored content, student-read)
    // ------------------------------------------------------------------

    suspend fun getCourses(): List<Course> {
        val snap = db.collection("courses").orderBy("order").get().await()
        return snap.documents.mapNotNull { it.toObject<Course>()?.copy(id = it.id) }
    }

    suspend fun getLessons(courseId: String): List<Lesson> {
        val snap = db.collection("courses").document(courseId)
            .collection("lessons").orderBy("order").get().await()
        return snap.documents.mapNotNull { it.toObject<Lesson>()?.copy(id = it.id) }
    }

    /**
     * Live-updating course list. Any add/edit/delete the admin does in the
     * admin panel reaches this within a second or two — no need to leave
     * and re-enter the screen, no app restart.
     */
    fun observeCourses(): kotlinx.coroutines.flow.Flow<List<Course>> = kotlinx.coroutines.flow.callbackFlow {
        val registration = db.collection("courses").orderBy("order")
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toObject<Course>()?.copy(id = it.id) } ?: emptyList()
                trySend(list)
            }
        kotlinx.coroutines.channels.awaitClose { registration.remove() }
    }

    /** Live-updating lessons for a course — same reasoning as [observeCourses]. */
    fun observeLessons(courseId: String): kotlinx.coroutines.flow.Flow<List<Lesson>> = kotlinx.coroutines.flow.callbackFlow {
        val registration = db.collection("courses").document(courseId)
            .collection("lessons").orderBy("order")
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toObject<Lesson>()?.copy(id = it.id) } ?: emptyList()
                trySend(list)
            }
        kotlinx.coroutines.channels.awaitClose { registration.remove() }
    }

    // ------------------------------------------------------------------
    // Announcements (admin-authored, shown on Home screen)
    // ------------------------------------------------------------------

    suspend fun getAnnouncements(): List<com.mitv.trademaster.data.model.Announcement> {
        val snap = db.collection("announcements").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(10).get().await()
        return snap.documents.mapNotNull { it.toObject<com.mitv.trademaster.data.model.Announcement>()?.copy(id = it.id) }
    }

    // ------------------------------------------------------------------
    // Payment submissions
    // ------------------------------------------------------------------

    suspend fun submitPayment(submission: PaymentSubmission) {
        db.collection("payments").add(submission).await()
    }

    // ------------------------------------------------------------------
    // Chat (student <-> AI / admin support)
    // ------------------------------------------------------------------

    suspend fun sendChatMessage(uid: String, message: ChatMessage) {
        db.collection("chats").document(uid)
            .collection("messages").add(message).await()
    }

    fun listenToChat(uid: String, onUpdate: (List<ChatMessage>) -> Unit) =
        db.collection("chats").document(uid)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snap, _ ->
                val messages = snap?.documents?.mapNotNull { it.toObject<ChatMessage>()?.copy(id = it.id) } ?: emptyList()
                onUpdate(messages)
            }
}
