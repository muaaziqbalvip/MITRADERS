package com.mitv.trademaster.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionStore by preferencesDataStore(name = "mitv_session")

data class SessionState(
    val language: String = "en",
    val profileComplete: Boolean = false,
    val accentTheme: String = "green",
    val completedLessonIds: Set<String> = emptySet(),
    val lastLessonByCourse: Map<String, String> = emptyMap(),
    val religionAsked: Boolean = false,
    val isMuslim: Boolean = false,
    val quizPassedCourseIds: Set<String> = emptySet(),
)

class SessionRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val PROFILE_COMPLETE = booleanPreferencesKey("profile_complete")
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
        val COMPLETED_LESSONS = stringSetPreferencesKey("completed_lessons")
        val LAST_LESSON_MAP = stringPreferencesKey("last_lesson_map") // "courseId:lessonId,courseId2:lessonId2"
        val RELIGION_ASKED = booleanPreferencesKey("religion_asked")
        val IS_MUSLIM = booleanPreferencesKey("is_muslim")
        val QUIZ_PASSED_COURSES = stringSetPreferencesKey("quiz_passed_courses")
    }

    val session: Flow<SessionState> = context.sessionStore.data.map { prefs ->
        val lastLessonRaw = prefs[Keys.LAST_LESSON_MAP] ?: ""
        val lastLessonMap = lastLessonRaw.split(",").filter { it.contains(":") }
            .associate { val (c, l) = it.split(":", limit = 2); c to l }
        SessionState(
            language = prefs[Keys.LANGUAGE] ?: "en",
            profileComplete = prefs[Keys.PROFILE_COMPLETE] ?: false,
            accentTheme = prefs[Keys.ACCENT_THEME] ?: "green",
            completedLessonIds = prefs[Keys.COMPLETED_LESSONS] ?: emptySet(),
            lastLessonByCourse = lastLessonMap,
            religionAsked = prefs[Keys.RELIGION_ASKED] ?: false,
            isMuslim = prefs[Keys.IS_MUSLIM] ?: false,
            quizPassedCourseIds = prefs[Keys.QUIZ_PASSED_COURSES] ?: emptySet(),
        )
    }

    suspend fun setLanguage(lang: String) {
        context.sessionStore.edit { it[Keys.LANGUAGE] = lang }
    }

    suspend fun setProfileComplete(complete: Boolean) {
        context.sessionStore.edit { it[Keys.PROFILE_COMPLETE] = complete }
    }

    suspend fun setAccentTheme(theme: String) {
        context.sessionStore.edit { it[Keys.ACCENT_THEME] = theme }
    }

    /** Records the one-time "are you Muslim?" answer so we never ask again. */
    suspend fun setReligionAnswer(isMuslim: Boolean) {
        context.sessionStore.edit {
            it[Keys.RELIGION_ASKED] = true
            it[Keys.IS_MUSLIM] = isMuslim
        }
    }

    /** Marks a lesson complete locally. Returns true if it was newly marked (not already complete). */
    suspend fun markLessonComplete(lessonId: String): Boolean {
        var wasNew = false
        context.sessionStore.edit { prefs ->
            val current = prefs[Keys.COMPLETED_LESSONS] ?: emptySet()
            if (lessonId !in current) {
                wasNew = true
                prefs[Keys.COMPLETED_LESSONS] = current + lessonId
            }
        }
        return wasNew
    }

    /** Remembers the last lesson viewed in a course, so the student can resume from Home. */
    suspend fun setLastLesson(courseId: String, lessonId: String) {
        context.sessionStore.edit { prefs ->
            val raw = prefs[Keys.LAST_LESSON_MAP] ?: ""
            val map = raw.split(",").filter { it.contains(":") }
                .associate { val (c, l) = it.split(":", limit = 2); c to l }
                .toMutableMap()
            map[courseId] = lessonId
            prefs[Keys.LAST_LESSON_MAP] = map.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    /** Marks a course's quiz as passed — this is what unlocks the next course. */
    suspend fun markQuizPassed(courseId: String) {
        context.sessionStore.edit { prefs ->
            val current = prefs[Keys.QUIZ_PASSED_COURSES] ?: emptySet()
            prefs[Keys.QUIZ_PASSED_COURSES] = current + courseId
        }
    }
}
