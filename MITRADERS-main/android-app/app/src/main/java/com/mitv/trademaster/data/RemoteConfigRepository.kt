package com.mitv.trademaster.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Fetches app-wide settings that the admin can change without a new APK
 * release, stored at Firestore document config/app. Currently used for:
 *   - groqApiKey: rotatable Groq key for the support chat assistant
 *   - subscriptionPricePkr: shown on the payment screen
 *   - whatsappNumber: support contact shown in-app
 *   - paymentQrUrl: ImgBB-hosted QR code image for payment
 *
 * Cached in-memory for the process lifetime after first fetch to avoid
 * refetching on every chat message.
 */
data class AppConfig(
    val groqApiKey: String = "",
    val subscriptionPricePkr: Int = 500,
    val whatsappNumber: String = "923062015326",
    val paymentQrUrl: String = "",
)

class RemoteConfigRepository {

    private val db = FirebaseFirestore.getInstance()
    private var cached: AppConfig? = null

    suspend fun getConfig(forceRefresh: Boolean = false): AppConfig {
        if (!forceRefresh && cached != null) return cached!!

        return try {
            val snap = db.collection("config").document("app").get().await()
            val config = AppConfig(
                groqApiKey = snap.getString("groqApiKey") ?: "",
                subscriptionPricePkr = (snap.getLong("subscriptionPricePkr") ?: 500L).toInt(),
                whatsappNumber = snap.getString("whatsappNumber") ?: "923062015326",
                paymentQrUrl = snap.getString("paymentQrUrl") ?: "",
            )
            cached = config
            config
        } catch (e: Exception) {
            cached ?: AppConfig()
        }
    }
}
