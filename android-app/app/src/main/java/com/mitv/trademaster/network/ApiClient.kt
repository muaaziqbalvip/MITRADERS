package com.mitv.trademaster.network

import com.mitv.trademaster.BuildConfig
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

data class LicenseVerifyRequest(val license_key: String, val device_id: String)

data class LicenseVerifyResponse(
    val valid: Boolean,
    val user_name: String?,
    val expires_at: String?,
    val activated_at: String?,
)

data class AnalysisResponse(
    val direction_lean: String,     // "up" | "down" | "neutral"
    val confidence: String,         // "low" | "medium" | "high"
    val pattern: String,
    val explanation: String,
    val sr_note: String?,
    val candles_detected: Int?,
    val disclaimer: String,
)

interface MitvApi {
    @POST("api/license/verify")
    suspend fun verifyLicense(@Body req: LicenseVerifyRequest): Response<LicenseVerifyResponse>

    @Multipart
    @POST("api/analyze")
    suspend fun analyzeChart(
        @Part("license_key") licenseKey: RequestBody,
        @Part image: MultipartBody.Part,
    ): Response<AnalysisResponse>
}

object ApiClient {
    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: MitvApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MitvApi::class.java)
}
