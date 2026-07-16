package com.mitv.trademaster.data

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mitv.trademaster.network.ApiClient
import com.mitv.trademaster.network.LicenseVerifyRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mitv_prefs")

data class LicenseState(
    val isActivated: Boolean = false,
    val licenseKey: String = "",
    val userName: String = "",
    val expiresAt: String? = null,
)

class LicenseRepository(private val context: Context) {

    private object Keys {
        val ACTIVATED = booleanPreferencesKey("license_activated")
        val KEY = stringPreferencesKey("license_key")
        val USER_NAME = stringPreferencesKey("license_user_name")
        val EXPIRES = stringPreferencesKey("license_expires_at")
    }

    val licenseState: Flow<LicenseState> = context.dataStore.data.map { prefs ->
        LicenseState(
            isActivated = prefs[Keys.ACTIVATED] ?: false,
            licenseKey = prefs[Keys.KEY] ?: "",
            userName = prefs[Keys.USER_NAME] ?: "",
            expiresAt = prefs[Keys.EXPIRES],
        )
    }

    suspend fun currentState(): LicenseState = licenseState.first()

    private fun deviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"

    /**
     * Verifies a license key against the backend. Returns a Result so the
     * UI layer can show a friendly error without crashing on network issues.
     */
    suspend fun activate(licenseKey: String): Result<LicenseState> {
        return try {
            val response = ApiClient.api.verifyLicense(
                LicenseVerifyRequest(license_key = licenseKey.trim().uppercase(), device_id = deviceId())
            )
            if (response.isSuccessful && response.body()?.valid == true) {
                val body = response.body()!!
                val newState = LicenseState(
                    isActivated = true,
                    licenseKey = licenseKey.trim().uppercase(),
                    userName = body.user_name ?: "",
                    expiresAt = body.expires_at,
                )
                context.dataStore.edit { prefs ->
                    prefs[Keys.ACTIVATED] = true
                    prefs[Keys.KEY] = newState.licenseKey
                    prefs[Keys.USER_NAME] = newState.userName
                    newState.expiresAt?.let { prefs[Keys.EXPIRES] = it }
                }
                Result.success(newState)
            } else {
                Result.failure(Exception("Invalid or expired license key"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deactivate() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVATED] = false
        }
    }
}
