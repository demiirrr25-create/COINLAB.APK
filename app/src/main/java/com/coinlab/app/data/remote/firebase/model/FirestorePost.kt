package com.coinlab.app.data.remote.firebase.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * v8.2.2 — Firestore community post document model.
 * Collection: "posts"
 */
data class FirestorePost(
    @DocumentId
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val authorBadge: String = "",
    val content: String = "",
    val coinTag: String = "",
    val sentiment: String = "", // BULLISH, BEARISH, NEUTRAL
    val imageUrl: String = "",
    val likes: List<String> = emptyList(), // List of user IDs who liked
    val commentCount: Int = 0,
    val channelId: String = "",
    val mentions: List<String> = emptyList(), // @mentioned usernames
    val isEdited: Boolean = false,
    val reportCount: Int = 0,
    val reportedBy: List<String> = emptyList(), // List of user IDs who reported
    val reportReasons: List<String> = emptyList(),
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    // No-arg constructor for Firestore deserialization
    constructor() : this(id = "")
}
