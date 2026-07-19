package com.mitv.trademaster.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * App-wide settings the admin can change without a new APK release, stored
 * at Firestore document config/app. Currently used for:
 *   - groqApiKey: rotatable Groq key for the support chat assistant + AI quiz generator
 *   - subscriptionPricePkr: shown on the payment screen
 *   - whatsappNumber: support contact shown in-app
 *   - jazzCashQrUrl / easyPaisaQrUrl: ImgBB-hosted QR images, one per payment method
 *
 * IMPORTANT: this is a LIVE Firestore listener, not a one-time fetch. Any
 * change the admin makes in the admin panel reaches every open app instance
 * within a second or two — no app restart or reinstall needed.
 */
data class AppConfig(
    val groqApiKey: String = "",
    val subscriptionPricePkr: Int = 500,
    val whatsappNumber: String = "923062015326",
    val jazzCashQrUrl: String = "",
    val easyPaisaQrUrl: String = "",
    val jazzCashNumber: String = "",
    val easyPaisaNumber: String = "",
)

class RemoteConfigRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun snapshotToConfig(snap: com.google.firebase.firestore.DocumentSnapshot?): AppConfig {
        if (snap == null || !snap.exists()) return AppConfig()
        return AppConfig(
            groqApiKey = snap.getString("groqApiKey") ?: "",
            subscriptionPricePkr = (snap.getLong("subscriptionPricePkr") ?: 500L).toInt(),
            whatsappNumber = snap.getString("whatsappNumber") ?: "923062015326",
            jazzCashQrUrl = snap.getString("jazzCashQrUrl") ?: (snap.getString("paymentQrUrl") ?: ""),
            easyPaisaQrUrl = snap.getString("easyPaisaQrUrl") ?: "",
            jazzCashNumber = snap.getString("jazzCashNumber") ?: "",
            easyPaisaNumber = snap.getString("easyPaisaNumber") ?: "",
        )
    }

    /**
     * Live-updating config — collect this instead of calling getConfig() repeatedly.
     * Implemented with a manual Channel instead of callbackFlow/awaitClose:
     * that API was unreliably resolving in this project's build environment,
     * so this sidesteps it entirely while behaving identically (live updates,
     * proper listener cleanup when the collector stops).
     */
    fun observeConfig(): Flow<AppConfig> = flow {
        val channel = Channel<AppConfig>(Channel.CONFLATED)
        val registration = db.collection("config").document("app")
            .addSnapshotListener { snap, _ -> channel.trySend(snapshotToConfig(snap)) }
        try {
            for (config in channel) emit(config)
        } finally {
            registration.remove()
        }
    }

    /** One-time fetch, kept for call sites that just need a snapshot (e.g. before sending a single request). */
    suspend fun getConfig(): AppConfig {
        return try {
            val snap = db.collection("config").document("app").get().await()
            snapshotToConfig(snap)
        } catch (e: Exception) {
            AppConfig()
        }
    }
}
