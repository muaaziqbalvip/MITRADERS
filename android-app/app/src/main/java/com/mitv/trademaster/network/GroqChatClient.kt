package com.mitv.trademaster.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin client for Groq's OpenAI-compatible chat completion API. Used for a
 * small support assistant that helps students with account/payment
 * questions and redirects them to WhatsApp support or the admin when it
 * can't resolve something itself. This is a support assistant, not a
 * trading-signal engine — keep its system prompt scoped to app/account
 * help only.
 *
 * NOTE: shipping a Groq API key inside the APK is only appropriate for a
 * low-stakes support assistant like this. If usage grows, move this call
 * behind a small serverless function so the key isn't embedded client-side.
 */
object GroqChatClient {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.1-8b-instant" // small/fast model, per requirements

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val SYSTEM_PROMPT = """
        You are the MI Trade Master support assistant. You help students with:
        - Account activation status and how the 500 PKR/month subscription works
        - How to submit a payment screenshot for admin approval
        - How to navigate the app (Lessons, Analyzer, Practice, Account)
        - General encouragement and study tips

        You do NOT give specific buy/sell trade instructions or predict market
        outcomes. If asked for trading signals, redirect the student to the
        Analyzer tab in the app. If a question is about payment status specifically
        and you cannot resolve it, tell them an admin will confirm shortly, or
        they can reach support directly on WhatsApp at +92 306 2015326.

        Keep responses short (2-4 sentences), friendly, and in the student's
        language (English, Urdu, Hindi, or Arabic — match their message).
    """.trimIndent()

    fun sendMessage(apiKey: String, conversationHistory: List<Pair<String, String>>, newMessage: String): Result<String> {
        return try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            conversationHistory.forEach { (role, text) ->
                messages.put(JSONObject().put("role", role).put("content", text))
            }
            messages.put(JSONObject().put("role", "user").put("content", newMessage))

            val payload = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("max_tokens", 300)
                put("temperature", 0.4)
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
                val reply = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                Result.success(reply)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
