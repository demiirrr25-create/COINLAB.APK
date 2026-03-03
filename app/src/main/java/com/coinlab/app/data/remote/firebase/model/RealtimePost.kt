package com.coinlab.app.data.remote.firebase.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * v8.9 — Firebase Realtime Database post model.
 * Path: community/posts/{postId}
 *
 * Uses Long (epoch millis) instead of Firestore Timestamp.
 * Uses Map<String, Boolean> for likes (RTDB best practice, avoids array fragmentation).
 */
@IgnoreExtraProperties
data class RealtimePost(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val authorBadge: String = "",
    val content: String = "",
    val coinTag: String = "",
    val sentiment: String = "", // BULLISH, BEARISH, NEUTRAL
    val imageUrl: String = "",
    val likes: Map<String, Boolean> = emptyMap(), // userId → true
    val commentCount: Int = 0,
    val channelId: String = "",
    val mentions: List<String> = emptyList(),
    val isEdited: Boolean = false,
    val reportCount: Int = 0,
    val reportedBy: Map<String, Boolean> = emptyMap(), // userId → true
    val reportReasons: List<String> = emptyList(),
    val createdAt: Long = 0L, // epoch millis
    val updatedAt: Long = 0L
) {
    /** Convert likes map to list of user IDs (for UI compatibility). */
    fun likesList(): List<String> = likes.keys.toList()

    /** Convert reportedBy map to list of user IDs. */
    fun reportedByList(): List<String> = reportedBy.keys.toList()

    /** No-arg constructor for Firebase deserialization. */
    constructor() : this(id = "")
}
