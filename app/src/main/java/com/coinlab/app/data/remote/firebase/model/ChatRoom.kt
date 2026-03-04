package com.coinlab.app.data.remote.firebase.model

/**
 * v9.5 — P2P Chat Room
 *
 * Stored at: chats/{chatId}
 * Each participant has an entry in user_chats/{userId}/{chatId}
 */
data class ChatRoom(
    val id: String = "",
    val participants: Map<String, Boolean> = emptyMap(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val createdAt: Long = 0L
) {
    constructor() : this("", emptyMap(), emptyMap(), "", 0L, 0L)

    fun getOtherParticipantName(currentUserId: String): String {
        return participantNames.entries
            .firstOrNull { it.key != currentUserId }
            ?.value ?: "Kullanıcı"
    }

    fun getOtherParticipantId(currentUserId: String): String {
        return participants.keys
            .firstOrNull { it != currentUserId } ?: ""
    }
}
