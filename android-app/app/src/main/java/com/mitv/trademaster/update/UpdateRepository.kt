package com.mitv.trademaster.update

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Version metadata the admin sets in Firestore at config/app_version:
 *   latestVersionCode   (int)    - increment this on every release
 *   latestVersionName   (string) - e.g. "4.1.0", shown to the user
 *   apkUrl              (string) - direct download link to the new APK
 *   forceUpdate         (bool)   - if true, the app blocks usage until updated
 *   releaseNotes        (string) - shown on the update screen
 *
 * If forceUpdate is false but a newer version exists, the app can show a
 * dismissible "update available" banner instead (left as a hook for the
 * caller — see MainActivity's handling of UpdateInfo.forceUpdate).
 */
data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
    val forceUpdate: Boolean,
    val releaseNotes: String,
)

class UpdateRepository {
    private val db = FirebaseFirestore.getInstance()

    /**
     * Live-updating version check: emits a new UpdateInfo (or null) the
     * instant the admin changes config/app_version in Firestore — no app
     * restart needed. Use this instead of the one-shot [checkForUpdate]
     * wherever the app is already open and should react in real time.
     */
    fun observeUpdateInfo(currentVersionCode: Int): kotlinx.coroutines.flow.Flow<UpdateInfo?> = kotlinx.coroutines.flow.flow {
        val channel = kotlinx.coroutines.channels.Channel<UpdateInfo?>(kotlinx.coroutines.channels.Channel.CONFLATED)
        val registration = db.collection("config").document("app_version")
            .addSnapshotListener { snap, _ ->
                val info = try {
                    if (snap == null || !snap.exists()) null
                    else {
                        val latestCode = (snap.getLong("latestVersionCode") ?: return@addSnapshotListener).toInt()
                        if (latestCode <= currentVersionCode) null
                        else UpdateInfo(
                            latestVersionCode = latestCode,
                            latestVersionName = snap.getString("latestVersionName") ?: "",
                            apkUrl = snap.getString("apkUrl") ?: "",
                            forceUpdate = snap.getBoolean("forceUpdate") ?: false,
                            releaseNotes = snap.getString("releaseNotes") ?: "",
                        )
                    }
                } catch (e: Exception) {
                    null
                }
                channel.trySend(info)
            }
        try {
            for (info in channel) emit(info)
        } finally {
            registration.remove()
        }
    }

    /**
     * Returns UpdateInfo only if the remote version is newer than
     * [currentVersionCode]. Returns null if up to date, not configured, or
     * on any network/parsing error (fails open — never blocks app startup
     * on an update-check failure).
     */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? {
        return try {
            val snap = db.collection("config").document("app_version").get().await()
            if (!snap.exists()) return null

            val latestCode = (snap.getLong("latestVersionCode") ?: return null).toInt()
            if (latestCode <= currentVersionCode) return null

            UpdateInfo(
                latestVersionCode = latestCode,
                latestVersionName = snap.getString("latestVersionName") ?: "",
                apkUrl = snap.getString("apkUrl") ?: "",
                forceUpdate = snap.getBoolean("forceUpdate") ?: false,
                releaseNotes = snap.getString("releaseNotes") ?: "",
            )
        } catch (e: Exception) {
            null
        }
    }
}
