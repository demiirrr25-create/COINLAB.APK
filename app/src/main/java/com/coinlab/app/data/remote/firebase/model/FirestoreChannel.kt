package com.coinlab.app.data.remote.firebase.model

import com.google.firebase.firestore.DocumentId

/**
 * v8.2.2 — Firestore channel document model.
 * Collection: "channels"
 */
data class FirestoreChannel(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val icon: String = "",
    val memberCount: Int = 0,
    val members: List<String> = emptyList() // List of user IDs
) {
    constructor() : this(id = "")
}
