package com.coinlab.app.data.remote.firebase.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * v8.2.2 — Firestore comment sub-collection document model.
 * Collection: "posts/{postId}/comments"
 */
data class FirestoreComment(
    @DocumentId
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatar: String = "",
    val content: String = "",
    val mentions: List<String> = emptyList(),
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    constructor() : this(id = "")
}
