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
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
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

            // Sign-in ke baad PostHog mein user identify karo — Firebase UID link hoga
            authResult.user?.let { user ->
                AnalyticsManager.identifyUser(
                    firebaseUid = user.uid,
                    email = user.email,
                    displayName = user.displayName
                )
            }

            // Sync UserProfile from Firestore to get Preamble ID, then persist the
            // Google account photo URL captured at sign-in (Req 26) so it isn't
            // clobbered by the fetched profile, and publish it to the public directory.
            val googlePhotoUrl = authResult.user?.photoUrl?.toString()
            com.theblankstate.preamble.data.UserProfileStore.fetchFromFirestore(context) {
                com.theblankstate.preamble.data.UserProfileStore.updatePhotoUrl(context, googlePhotoUrl)
            }

            // Migrate locally-saved AI memories to the real uid + sync to Firestore
            runCatching {
                com.theblankstate.preamble.ai.AiMemoryRepository.get(context).migrateOnLogin()
            }

            Result.success(SignInResult(authResult.user!!, isNew))
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        // Sign-out se pehle PostHog session reset karo — naya anonymous ID milega
        AnalyticsManager.resetUser()
        auth.signOut()
        _currentUser.value = null
    }
}
