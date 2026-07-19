package com.mitv.trademaster.network

import com.mitv.trademaster.data.model.QuizQuestion
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Generates a bilingual multiple-choice quiz for a course by feeding Groq
 * the full text of every lesson in that course. Called once per course
 * (the first student to finish it triggers generation); the result is
 * cached in Firestore by FirestoreRepository.saveCourseQuiz so later
 * students reuse it instead of re-calling the API.
 */
object GroqQuizClient {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.1-8b-instant"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // quiz generation reads a lot of text, give it room
        .build()

    /**
     * [lessonsText] should be each lesson's title + body concatenated, e.g.
     * "Lesson 1: Candlesticks\n<body text>\n\nLesson 2: ...". Returns 8-10
     * questions covering the whole course, in both English and Urdu.
     */
    fun generateQuiz(apiKey: String, courseTitle: String, lessonsText: String): Result<List<QuizQuestion>> {
        return try {
            val prompt = """
                You are creating a course-completion quiz for a trading education app.
                Course: "$courseTitle"

                Below is the full content of every lesson in this course. Read it
                carefully and write EXACTLY 8 multiple-choice questions that test
                real understanding of the material (not trivia about wording).
                Cover different lessons — don't cluster all questions on one topic.

                Each question needs exactly 4 options, one correct answer, and a
                one-sentence explanation of why it's correct. Provide BOTH an
                English version and an Urdu (Roman script is NOT acceptable —
                use actual Urdu script) version of the question, options, and
                explanation.

                Respond with ONLY a raw JSON array (no markdown fences, no preamble),
                in exactly this shape:
                [
                  {
                    "question": "English question text",
                    "questionUrdu": "اردو سوال کا متن",
                    "options": ["A", "B", "C", "D"],
                    "optionsUrdu": ["اے", "بی", "سی", "ڈی"],
                    "correctIndex": 0,
                    "explanation": "Why this is correct, one sentence.",
                    "explanationUrdu": "یہ کیوں درست ہے، ایک جملہ۔"
                  }
                ]

                LESSON CONTENT:
                $lessonsText
            """.trimIndent()

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", "You output only valid raw JSON, never markdown code fences or commentary."))
                put(JSONObject().put("role", "user").put("content", prompt))
            }

            val payload = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("max_tokens", 4000)
                put("temperature", 0.5)
            }

            val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return Result.failure(Exception("Groq API error (${response.code}): $bodyStr"))
                }
                val json = JSONObject(bodyStr)
                val rawContent = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                val cleaned = rawContent.trim()
                    .removePrefix("```json").removePrefix("```")
                    .removeSuffix("```").trim()

                val arr = JSONArray(cleaned)
                val questions = (0 until arr.length()).mapNotNull { i ->
                    try {
                        val q = arr.getJSONObject(i)
                        val opts = q.getJSONArray("options")
                        val optsUr = q.optJSONArray("optionsUrdu") ?: opts
                        QuizQuestion(
                            question = q.getString("question"),
                            questionUrdu = q.optString("questionUrdu", q.getString("question")),
                            options = (0 until opts.length()).map { opts.getString(it) },
                            optionsUrdu = (0 until optsUr.length()).map { optsUr.getString(it) },
                            correctIndex = q.getInt("correctIndex"),
                            explanation = q.optString("explanation", ""),
                            explanationUrdu = q.optString("explanationUrdu", q.optString("explanation", "")),
                        )
                    } catch (e: Exception) {
                        null // skip any single malformed question rather than failing the whole quiz
                    }
                }

                if (questions.isEmpty()) Result.failure(Exception("No valid questions parsed from AI response"))
                else Result.success(questions)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
