package com.mitv.trademaster.network

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.mitv.trademaster.BuildConfig
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Uploads images to ImgBB (https://api.imgbb.com) instead of Firebase
 * Storage, since Storage requires the paid (Blaze) plan. ImgBB's free
 * tier is used for student profile photos, ID card photos, and payment
 * screenshots. Returns the hosted image URL to store in Firestore.
 */
object ImgBBUploader {

    private const val UPLOAD_URL = "https://api.imgbb.com/1/upload"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads a local content Uri and returns the hosted URL on success.
     * Must be called from a background thread / coroutine (Dispatchers.IO).
     */
    fun uploadImage(context: Context, uri: Uri): Result<String> {
        return try {
            val file = uriToTempFile(context, uri)
            val base64Image = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)

            val formBody = okhttp3.FormBody.Builder()
                .add("key", BuildConfig.IMGBB_API_KEY)
                .add("image", base64Image)
                .build()

            val request = Request.Builder()
                .url(UPLOAD_URL)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return Result.failure(Exception("ImgBB upload failed (${response.code}): $bodyStr"))
                }
                val json = JSONObject(bodyStr)
                val url = json.getJSONObject("data").getString("url")
                Result.success(url)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            file?.delete()
        }
    }

    private var file: File? = null

    private fun uriToTempFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
        FileOutputStream(tempFile).use { out -> inputStream?.copyTo(out) }
        file = tempFile
        return tempFile
    }
}
