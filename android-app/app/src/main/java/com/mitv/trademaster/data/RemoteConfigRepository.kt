package com.mitv.trademaster.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * App-wide settings the admin can change without a new APK release, stored
 * at Firestore document config/app. Currently used for:
 *   - groqApiKey: rotatable Groq key for the support chat assistant
 *   - subscriptionPricePkr: shown on the payment screen
 *   - whatsappNumber: support contact shown in-app
 *   - paymentQrUrl: ImgBB-hosted QR code image for payment
 *
 * IMPORTANT: this is a LIVE Firestore listener, not a one-time fetch. Any
 * change the admin makes in the admin panel reaches every open app instance
 * within a second or two — no app restart or reinstall needed. (Previously
 * this cached the first read for the whole process lifetime, which is why
 * admin changes felt like they "never" showed up.)
 */
data class AppConfig(
    val groqApiKey: String = "",
    val subscriptionPricePkr: Int = 500,
    val whatsappNumber: String = "923062015326",
    val paymentQrUrl: String = "",
)

class RemoteConfigRepository {

    private val db = FirebaseFirestore.getInstance()

    /** Live-updating config — collect this instead of calling getConfig() repeatedly. */
    fun observeConfig(): Flow<AppConfig> = callbackFlow {
        val registration = db.collection("config").document("app")
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    trySend(
                        AppConfig(
                            groqApiKey = snap.getString("groqApiKey") ?: "",
                            subscriptionPricePkr = (snap.getLong("subscriptionPricePkr") ?: 500L).toInt(),
                            whatsappNumber = snap.getString("whatsappNumber") ?: "923062015326",
                            paymentQrUrl = snap.getString("paymentQrUrl") ?: "",
                        )
                    )
                } else {
                    trySend(AppConfig())
                }
            }
        awaitClose { registration.remove() }
    }

    /** One-time fetch, kept for call sites that just need a snapshot (e.g. before sending a single request). */
    suspend fun getConfig(): AppConfig {
        return try {
            val snap = db.collection("config").document("app").get().await()
            AppConfig(
                groqApiKey = snap.getString("groqApiKey") ?: "",
                subscriptionPricePkr = (snap.getLong("subscriptionPricePkr") ?: 500L).toInt(),
                whatsappNumber = snap.getString("whatsappNumber") ?: "923062015326",
                paymentQrUrl = snap.getString("paymentQrUrl") ?: "",
            )
        } catch (e: Exception) {
            AppConfig()
        }
    }
}
