package com.coinlab.app.data.remote.firebase.model

/**
 * v9.5 — P2P Chat Message
 *
 * Stored at: messages/{chatId}/{messageId}
 */
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val imageBase64: String? = null,
    val timestamp: Long = 0L,
    val chatId: String = ""
) {
    // No-arg constructor for Firebase deserialization
    constructor() : this("", "", "", "", null, 0L, "")
}
