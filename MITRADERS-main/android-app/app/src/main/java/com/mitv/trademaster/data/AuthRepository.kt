package com.mitv.trademaster.data

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GithubAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Wraps Firebase Authentication for all four sign-in methods used by the
 * app: Email/Password, Google (via Credential Manager — the modern,
 * non-deprecated API), GitHub (OAuth provider), and Phone (SMS OTP).
 */
class AuthRepository(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun signOut() = auth.signOut()

    // ------------------------------------------------------------------
    // Email / Password
    // ------------------------------------------------------------------

    suspend fun signUpWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let { AuthResult.Success(it) }
                ?: AuthResult.Error("Sign-up failed: no user returned")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Sign-up failed")
        }
    }

    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.let { AuthResult.Success(it) }
                ?: AuthResult.Error("Sign-in failed: no user returned")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Sign-in failed")
        }
    }

    suspend fun sendPasswordReset(email: String): AuthResult {
        return try {
            auth.sendPasswordResetEmail(email).await()
            AuthResult.Success(auth.currentUser ?: return AuthResult.Error("Reset email sent"))
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Could not send reset email")
        }
    }

    // ------------------------------------------------------------------
    // Google Sign-In (Credential Manager)
    // ------------------------------------------------------------------

    /**
     * Requires `resValue "string" "default_web_client_id"` (Web client ID
     * from Firebase Console → Authentication → Sign-in method → Google →
     * Web SDK configuration) to be set in build.gradle.kts.
     */
    suspend fun signInWithGoogle(activity: Activity, webClientId: String): AuthResult {
        return try {
            val credentialManager = CredentialManager.create(activity)
            val option = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val result = auth.signInWithCredential(firebaseCredential).await()

            result.user?.let { AuthResult.Success(it) }
                ?: AuthResult.Error("Google sign-in failed: no user returned")
        } catch (e: GetCredentialException) {
            AuthResult.Error(e.message ?: "Google sign-in was cancelled or failed")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Google sign-in failed")
        }
    }

    // ------------------------------------------------------------------
    // GitHub Sign-In (OAuth provider, opens a web popup via Firebase)
    // ------------------------------------------------------------------

    suspend fun signInWithGitHub(activity: Activity): AuthResult {
        return try {
            val provider = com.google.firebase.auth.OAuthProvider.newBuilder("github.com")
                // Request the user's email explicitly — GitHub accounts can have
                // a private/no public email, which otherwise breaks account
                // linking against an existing Email/Google account.
                .setScopes(listOf("read:user", "user:email"))
                .build()

            // If a sign-in is already pending (e.g. user backgrounded the
            // app during the GitHub web popup and returned), reuse it
            // instead of starting a duplicate flow.
            val pending = auth.pendingAuthResult
            val result = if (pending != null) {
                pending.await()
            } else {
                auth.startActivityForSignInWithProvider(activity, provider).await()
            }
            result.user?.let { AuthResult.Success(it) }
                ?: AuthResult.Error("GitHub sign-in failed: no user returned")
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            AuthResult.Error(
                "An account with this email already exists using a different sign-in method " +
                "(e.g. Google or Email). Please sign in with that method instead."
            )
        } catch (e: com.google.firebase.auth.FirebaseAuthWebException) {
            AuthResult.Error(
                "GitHub sign-in couldn't complete. This is usually a callback URL " +
                "mismatch — check that the GitHub OAuth App's callback URL exactly " +
                "matches what Firebase Console shows for the GitHub provider."
            )
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "GitHub sign-in failed")
        }
    }

    // ------------------------------------------------------------------
    // Phone Sign-In (SMS OTP)
    // ------------------------------------------------------------------

    fun startPhoneVerification(
        phoneNumber: String,
        activity: Activity,
        onCodeSent: (verificationId: String) -> Unit,
        onError: (String) -> Unit,
        onAutoVerified: (FirebaseUser?) -> Unit,
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { onAutoVerified(it.user) }
                    .addOnFailureListener { onError(it.message ?: "Auto-verification failed") }
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                onError(e.message ?: "Phone verification failed")
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                onCodeSent(verificationId)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    suspend fun confirmPhoneCode(verificationId: String, code: String): AuthResult {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            val result = auth.signInWithCredential(credential).await()
            result.user?.let { AuthResult.Success(it) }
                ?: AuthResult.Error("Phone sign-in failed: no user returned")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Invalid code")
        }
    }
}
