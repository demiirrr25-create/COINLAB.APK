package com.coinlab.app.data.remote.firebase

import com.coinlab.app.data.preferences.AuthPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v8.3 — Firebase Auth Manager
 *
 * Supports Google Sign-In via Firebase Authentication.
 * Falls back to anonymous auth for unauthenticated community features.
 *
 * Flow:
 *   1. App start → ensureAuthenticated() — anonymous if not signed in
 *   2. Google Sign-In → signInWithGoogle(idToken) → Firebase credential auth
 *   3. Community posts → authorId = Firebase UID, authorName = Google display name
 */
@Singleton
class FirebaseAuthManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val authPreferences: AuthPreferences
) {

    /**
     * Ensure user is authenticated with Firebase.
     * Signs in anonymously if no current user.
     * Throws exception on failure so callers can handle it.
     */
    suspend fun ensureAuthenticated() {
        if (firebaseAuth.currentUser != null) return
        try {
            firebaseAuth.signInAnonymously().await()
            android.util.Log.d("FirebaseAuth", "Anonymous sign-in successful, uid=${firebaseAuth.currentUser?.uid}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("FirebaseAuth", "Anonymous sign-in failed", e)
            throw e  // Propagate so ViewModel can handle
        }
    }

    /**
     * Sign in with Google ID token via Firebase.
     * Returns the FirebaseUser on success.
     */
    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        return authResult.user ?: throw Exception("Firebase Google sign-in returned null user")
    }

    /**
     * Get the current Firebase UID. Returns empty string if not authenticated.
     */
    fun getCurrentUserId(): String {
        return firebaseAuth.currentUser?.uid ?: ""
    }

    /**
     * Get current Firebase user.
     */
    fun getCurrentUser(): FirebaseUser? {
        return firebaseAuth.currentUser
    }

    /**
     * Get the current user's display name from local preferences.
     */
    suspend fun getCurrentUserName(): String {
        return try {
            val name = authPreferences.displayName.first()
            name.ifEmpty {
                firebaseAuth.currentUser?.displayName ?: "Anonim Kullanıcı"
            }
        } catch (_: Exception) {
            "Anonim Kullanıcı"
        }
    }

    /**
     * Get the current user's avatar from local preferences.
     */
    suspend fun getCurrentUserAvatar(): String {
        return try {
            val url = authPreferences.avatarUrl.first()
            url.ifEmpty {
                firebaseAuth.currentUser?.photoUrl?.toString() ?: ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Check if user is authenticated.
     */
    fun isAuthenticated(): Boolean {
        return firebaseAuth.currentUser != null
    }

    /**
     * Sign out from Firebase.
     */
    fun signOut() {
        firebaseAuth.signOut()
    }
}
