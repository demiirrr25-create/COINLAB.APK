package com.coinlab.app.data.remote.firebase

import com.coinlab.app.data.remote.firebase.model.ChatMessage
import com.coinlab.app.data.remote.firebase.model.ChatRoom
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v9.5 — P2P Chat Repository
 *
 * Firebase Realtime Database-based messaging.
 * Structure:
 *   chats/{chatId}                → ChatRoom
 *   messages/{chatId}/{messageId} → ChatMessage
 *   user_chats/{userId}/{chatId}  → true
 */
@Singleton
class ChatRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) {
    private val chatsRef = database.reference.child("chats")
    private val messagesRef = database.reference.child("messages")
    private val userChatsRef = database.reference.child("user_chats")

    /**
     * Get or create a chat room between two users.
     */
    suspend fun getOrCreateChat(
        otherUserId: String,
        otherUserName: String,
        currentUserName: String
    ): String {
        val currentUserId = auth.currentUser?.uid ?: throw Exception("Not authenticated")

        // Check if chat already exists
        val existingChats = userChatsRef.child(currentUserId).get().await()
        for (chatSnapshot in existingChats.children) {
            val chatId = chatSnapshot.key ?: continue
            val chatRoom = chatsRef.child(chatId).get().await()
                .getValue(ChatRoom::class.java) ?: continue
            if (chatRoom.participants.containsKey(otherUserId)) {
                return chatId
            }
        }

        // Create new chat
        val chatId = chatsRef.push().key ?: throw Exception("Failed to create chat")
        val chatRoom = ChatRoom(
            id = chatId,
            participants = mapOf(currentUserId to true, otherUserId to true),
            participantNames = mapOf(currentUserId to currentUserName, otherUserId to otherUserName),
            lastMessage = "",
            lastMessageTime = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )

        chatsRef.child(chatId).setValue(chatRoom).await()
        userChatsRef.child(currentUserId).child(chatId).setValue(true).await()
        userChatsRef.child(otherUserId).child(chatId).setValue(true).await()

        return chatId
    }

    /**
     * Send a message in a chat room.
     */
    suspend fun sendMessage(chatId: String, text: String, imageBase64: String? = null) {
        val currentUser = auth.currentUser ?: throw Exception("Not authenticated")
        val messageId = messagesRef.child(chatId).push().key
            ?: throw Exception("Failed to create message")

        val message = ChatMessage(
            id = messageId,
            senderId = currentUser.uid,
            senderName = currentUser.displayName ?: "Anonim",
            text = text,
            imageBase64 = imageBase64,
            timestamp = System.currentTimeMillis(),
            chatId = chatId
        )

        messagesRef.child(chatId).child(messageId).setValue(message).await()

        // Update chat room's last message
        val updates = mapOf<String, Any>(
            "lastMessage" to text,
            "lastMessageTime" to ServerValue.TIMESTAMP
        )
        chatsRef.child(chatId).updateChildren(updates).await()
    }

    /**
     * Real-time stream of chat rooms for the current user.
     */
    fun getChats(): Flow<List<ChatRoom>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chatIds = snapshot.children.mapNotNull { it.key }
                if (chatIds.isEmpty()) {
                    trySend(emptyList())
                    return
                }

                // Fetch each chat room
                val chatRooms = mutableListOf<ChatRoom>()
                var loaded = 0
                for (chatId in chatIds) {
                    chatsRef.child(chatId).get().addOnSuccessListener { chatSnapshot ->
                        chatSnapshot.getValue(ChatRoom::class.java)?.let { chatRooms.add(it) }
                        loaded++
                        if (loaded >= chatIds.size) {
                            chatRooms.sortByDescending { it.lastMessageTime }
                            trySend(chatRooms.toList())
                        }
                    }.addOnFailureListener {
                        loaded++
                        if (loaded >= chatIds.size) {
                            chatRooms.sortByDescending { it.lastMessageTime }
                            trySend(chatRooms.toList())
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        userChatsRef.child(currentUserId).addValueEventListener(listener)
        awaitClose { userChatsRef.child(currentUserId).removeEventListener(listener) }
    }.retry(Long.MAX_VALUE) { cause ->
        delay(3000)
        true
    }

    /**
     * Real-time stream of messages in a chat room.
     */
    fun getMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val query = messagesRef.child(chatId).orderByChild("timestamp")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = mutableListOf<ChatMessage>()
                for (child in snapshot.children) {
                    try {
                        child.getValue(ChatMessage::class.java)?.let { messages.add(it) }
                    } catch (_: Exception) { }
                }
                trySend(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }.retry(Long.MAX_VALUE) { cause ->
        delay(3000)
        true
    }

    /**
     * Get current user ID.
     */
    fun getCurrentUserId(): String = auth.currentUser?.uid ?: ""
    fun getCurrentUserName(): String = auth.currentUser?.displayName ?: "Anonim"
}
