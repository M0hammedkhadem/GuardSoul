package com.agon.app.account

import android.content.Context
import com.agon.app.data.settings.AppSettings
import com.agon.app.utils.AppLogger
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps Firebase Auth. We support three providers:
 *  - anonymous (no friction, default for first launch)
 *  - email + password (works offline, GDPR-friendly)
 *  - Google One Tap (best conversion, requires Google sign-in)
 *
 * The current user is mirrored into [AppSettings] so the rest of the
 * app can read it without going through the Firebase SDK on every
 * access.
 */
class AuthRepository(
    private val context: Context,
    private val settings: AppSettings
) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Sign in or sign up anonymously. Always succeeds if Firebase is OK. */
    suspend fun signInAnonymously(): UserSession {
        return try {
            val result = auth.signInAnonymously().await()
            val user = result.user ?: error("Anonymous auth returned null user")
            mirrorToSettings(user, "anonymous")
            sessionOf(user)
        } catch (e: Exception) {
            AppLogger.w("AuthRepository: anonymous sign-in failed: ${e.message}")
            UserSession.SignedOut
        }
    }

    suspend fun signInWithEmail(email: String, password: String): UserSession {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: error("Email sign-in returned null user")
            mirrorToSettings(user, "email")
            sessionOf(user)
        } catch (e: Exception) {
            AppLogger.w("AuthRepository: email sign-in failed: ${e.message}")
            throw e
        }
    }

    suspend fun signUpWithEmail(email: String, password: String, displayName: String?): UserSession {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: error("Email sign-up returned null user")
            if (!displayName.isNullOrBlank()) {
                user.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build()
                ).await()
            }
            mirrorToSettings(user, "email")
            sessionOf(user)
        } catch (e: Exception) {
            AppLogger.w("AuthRepository: email sign-up failed: ${e.message}")
            throw e
        }
    }

    suspend fun signInWithGoogle(idToken: String): UserSession {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: error("Google sign-in returned null user")
            mirrorToSettings(user, "google")
            sessionOf(user)
        } catch (e: Exception) {
            AppLogger.w("AuthRepository: Google sign-in failed: ${e.message}")
            throw e
        }
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun linkAnonymousWithEmail(email: String, password: String): UserSession {
        val current = auth.currentUser ?: error("No anonymous user to link")
        val credential = EmailAuthProvider.getCredential(email, password)
        val result = current.linkWithCredential(credential).await()
        val user = result.user ?: error("Link returned null user")
        mirrorToSettings(user, "email")
        return sessionOf(user)
    }

    fun signOut(): UserSession {
        auth.signOut()
        kotlinx.coroutines.runBlocking {
            settings.setAuthUserId("")
            settings.setAuthProvider("anonymous")
            settings.setAuthAnonymous(true)
        }
        return UserSession.SignedOut
    }

    /** Returns the current local view of the user. */
    fun currentSession(): UserSession {
        val user = auth.currentUser
        return if (user == null) UserSession.SignedOut else sessionOf(user)
    }

    private suspend fun mirrorToSettings(user: FirebaseUser, provider: String) {
        settings.setAuthUserId(user.uid)
        settings.setAuthProvider(provider)
        settings.setAuthAnonymous(provider == "anonymous")
    }

    private fun sessionOf(user: FirebaseUser): UserSession = UserSession.SignedIn(
        uid = user.uid,
        email = user.email.orEmpty(),
        displayName = user.displayName.orEmpty(),
        isAnonymous = user.isAnonymous,
        provider = when {
            user.isAnonymous -> "anonymous"
            user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } -> "google"
            else -> "email"
        }
    )
}

sealed class UserSession {
    data object SignedOut : UserSession()
    data class SignedIn(
        val uid: String,
        val email: String,
        val displayName: String,
        val isAnonymous: Boolean,
        val provider: String
    ) : UserSession() {
        val initials: String
            get() = displayName.trim().split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercase() }
                .joinToString("")
                .ifEmpty { email.firstOrNull()?.uppercase()?.toString() ?: "G" }
    }
}
