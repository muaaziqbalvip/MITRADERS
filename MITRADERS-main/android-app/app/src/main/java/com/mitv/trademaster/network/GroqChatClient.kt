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
    private const val TEXT_MODEL = "llama-3.1-8b-instant"          // fast text-only model
    private const val VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct" // supports image input

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS) // vision requests take a bit longer
        .build()

    private val SYSTEM_PROMPT = """
        You are the MI Trade Master AI Assistant. You have two roles in one chat:

        1) TRADING & LEARNING ASSISTANT
           - Explain trading concepts: candlestick patterns, support/resistance,
             trend, moving averages, chart reading, risk management, and
             trading psychology.
           - When a student sends a chart screenshot, describe what pattern
             structure is visible (trend direction, candle shapes, apparent
             support/resistance) as an educational observation with a
             confidence caveat — similar to the app's Analyzer tab. Never
             frame it as a guaranteed buy/sell instruction or tie it to a
             specific broker or short expiry.
           - Help students understand lessons in the app, clarify terms, and
             suggest what to study next based on their level.

        2) ACCOUNT & PAYMENT SUPPORT
           - Help with account activation status and how the monthly
             subscription works.
           - Explain how to submit a payment screenshot for admin approval.
           - Help navigate the app (Lessons, Analyzer, Practice, Account).
           - If a payment question can't be resolved by you, say an admin
             will confirm shortly, or the student can reach support directly
             on WhatsApp at +92 306 2015326.

        Figure out which role fits the student's message and respond
        accordingly. Keep responses short (2-6 sentences), friendly, and in
        the student's language (English, Urdu, Hindi, or Arabic — match
        their message).
    """.trimIndent()

    /** Text-only message — uses the fast text model. */
    fun sendMessage(apiKey: String, conversationHistory: List<Pair<String, String>>, newMessage: String): Result<String> {
        return try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            conversationHistory.forEach { (role, text) ->
                messages.put(JSONObject().put("role", role).put("content", text))
            }
            messages.put(JSONObject().put("role", "user").put("content", newMessage))

            val payload = JSONObject().apply {
                put("model", TEXT_MODEL)
                put("messages", messages)
                put("max_tokens", 400)
                put("temperature", 0.4)
            }
            executeRequest(apiKey, payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Message with an attached image (base64 data URL) — uses the vision
     * model. `imageBase64` should be a raw base64 string (no data: prefix);
     * this function adds the correct data URL wrapper.
     */
    fun sendMessageWithImage(apiKey: String, conversationHistory: List<Pair<String, String>>, newMessage: String, imageBase64: String, mimeType: String = "image/jpeg"): Result<String> {
        return try {
            val messages = JSONArray()
            messages.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            conversationHistory.forEach { (role, text) ->
                messages.put(JSONObject().put("role", role).put("content", text))
            }

            val contentArray = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", newMessage.ifBlank { "What do you see in this trading chart? Describe the pattern." }))
                put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:$mimeType;base64,$imageBase64")
                    )
                )
            }
            messages.put(JSONObject().put("role", "user").put("content", contentArray))

            val payload = JSONObject().apply {
                put("model", VISION_MODEL)
                put("messages", messages)
                put("max_tokens", 500)
                put("temperature", 0.4)
            }
            executeRequest(apiKey, payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeRequest(apiKey: String, payload: JSONObject): Result<String> {
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
            return Result.success(reply)
        }
    }
}
