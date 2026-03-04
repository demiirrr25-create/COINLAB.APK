package com.coinlab.app.data.remote.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v9.5 — FCM Token Manager
 *
 * Manages FCM token lifecycle: retrieval, storage in RTDB, and refresh on auth changes.
 * Token stored at: user_tokens/{userId}/token
 */
@Singleton
class FCMTokenManager @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
    private val messaging: FirebaseMessaging
) {
    /**
     * Retrieve current FCM token and save to RTDB under the authenticated user's ID.
     */
    suspend fun refreshToken() {
        try {
            val token = messaging.token.await()
            saveToken(token)
        } catch (_: Exception) { }
    }

    /**
     * Save token to RTDB.
     */
    private fun saveToken(token: String) {
        val userId = auth.currentUser?.uid ?: return
        database.reference
            .child("user_tokens")
            .child(userId)
            .setValue(mapOf(
                "token" to token,
                "platform" to "android",
                "updatedAt" to ServerValue.TIMESTAMP
            ))
    }

    /**
     * Subscribe to a topic for broadcast notifications.
     */
    fun subscribeToTopic(topic: String) {
        try {
            messaging.subscribeToTopic(topic)
        } catch (_: Exception) { }
    }

    /**
     * Remove token when user logs out.
     */
    fun clearToken() {
        val userId = auth.currentUser?.uid ?: return
        try {
            database.reference
                .child("user_tokens")
                .child(userId)
                .removeValue()
        } catch (_: Exception) { }
    }
}
