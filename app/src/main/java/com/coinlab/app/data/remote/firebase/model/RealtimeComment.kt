package com.coinlab.app.data.remote.firebase.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * v8.9 — Firebase Realtime Database comment model.
 * Path: community/comments/{postId}/{commentId}
 */
@IgnoreExtraProperties
data class RealtimeComment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val content: String = "",
    val mentions: List<String> = emptyList(),
    val createdAt: Long = 0L // epoch millis
) {
    constructor() : this(id = "")
}
