package com.theblankstate.preamble.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.theblankstate.preamble.analytics.AnalyticsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

object AuthManager {
    /** Wraps Firebase sign-in result with whether this is a brand-new account. */
    data class SignInResult(val user: FirebaseUser, val isNewUser: Boolean)

    private const val WEB_CLIENT_ID = "195921517707-5r9flmh8kh2gjoff9l35akn33dm665vk.apps.googleusercontent.com"

    private val auth = FirebaseAuth.getInstance()
    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    fun isSignedIn(): Boolean = auth.currentUser != null

    suspend fun signInWithGoogle(context: Context): Result<SignInResult> {
        return try {
            trySignIn(context, filterByAuthorizedAccounts = false)
        } catch (e: GetCredentialException) {
            // Primary attempt failed (common on low-storage devices or fresh installs where
            // the full credential picker can't be shown). Retry with filterByAuthorizedAccounts=true,
            // which uses already-authorized Google accounts cached by Play Services — much lighter.
            android.util.Log.w("Preamble_Auth", "Primary CredentialManager attempt failed, retrying with authorized accounts", e)
            try {
                trySignIn(context, filterByAuthorizedAccounts = true)
            } catch (e2: GetCredentialException) {
                android.util.Log.w("Preamble_Auth", "Fallback CredentialManager attempt also failed", e2)
                Result.failure(e2)
            } catch (e2: Exception) {
                android.util.Log.w("Preamble_Auth", "Fallback sign-in failed", e2)
                Result.failure(e2)
            }
        } catch (e: Exception) {
            android.util.Log.w("Preamble_Auth", "Sign-in failed", e)
            Result.failure(e)
        }
    }

    private suspend fun trySignIn(context: Context, filterByAuthorizedAccounts: Boolean): Result<SignInResult> {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(WEB_CLIENT_ID)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(context, request)
        val credential = result.credential

        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val idToken = googleIdTokenCredential.idToken

        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(firebaseCredential).await()

        _currentUser.value = authResult.user

        val isNew = authResult.additionalUserInfo?.isNewUser ?: true

        authResult.user?.let { user ->
            AnalyticsManager.identifyUser(
                firebaseUid = user.uid,
                email = user.email,
                displayName = user.displayName
            )
        }

        val googlePhotoUrl = authResult.user?.photoUrl?.toString()
        com.theblankstate.preamble.data.UserProfileStore.fetchFromFirestore(context) {
            com.theblankstate.preamble.data.UserProfileStore.updatePhotoUrl(context, googlePhotoUrl)
        }

        runCatching {
            com.theblankstate.preamble.ai.AiMemoryRepository.get(context).migrateOnLogin()
        }

        return Result.success(SignInResult(authResult.user!!, isNew))
    }

    fun signOut() {
        // Sign-out se pehle PostHog session reset karo — naya anonymous ID milega
        AnalyticsManager.resetUser()
        auth.signOut()
        _currentUser.value = null
    }
}
