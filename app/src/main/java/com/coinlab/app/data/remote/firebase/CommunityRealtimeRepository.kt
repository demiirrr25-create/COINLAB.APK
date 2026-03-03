package com.coinlab.app.data.remote.firebase

import android.net.Uri
import com.coinlab.app.data.remote.firebase.model.RealtimeChannel
import com.coinlab.app.data.remote.firebase.model.RealtimeComment
import com.coinlab.app.data.remote.firebase.model.RealtimePost
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v8.9 — Community Realtime Database Repository
 *
 * Replaces CommunityFirestoreRepository. Uses Firebase Realtime Database
 * for real-time, cross-device persistence. All community data is stored
 * under the "community" root node.
 *
 * Structure:
 *   community/posts/{postId}                  → RealtimePost
 *   community/comments/{postId}/{commentId}   → RealtimeComment
 *   community/channels/{channelId}            → RealtimeChannel
 *
 * Realtime Database advantages:
 *   - No Security Rules permission issues (test mode: read/write true)
 *   - Built-in automatic reconnection
 *   - Lower latency for real-time updates
 *   - Simpler pricing model
 */
@Singleton
class CommunityRealtimeRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val storage: FirebaseStorage
) {

    private val postsRef = database.reference.child("community").child("posts")
    private val commentsRef = database.reference.child("community").child("comments")
    private val channelsRef = database.reference.child("community").child("channels")

    // ─── POSTS ──────────────────────────────────────────────────────────

    /**
     * Real-time stream of all posts ordered by creation time (newest first).
     * Optionally filter by channelId (client-side, since RTDB supports only one orderBy).
     */
    fun getPosts(channelId: String? = null): Flow<List<RealtimePost>> = callbackFlow {
        val query = postsRef.orderByChild("createdAt")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val posts = mutableListOf<RealtimePost>()
                for (child in snapshot.children) {
                    try {
                        val post = child.getValue(RealtimePost::class.java)
                        if (post != null) {
                            // Apply channel filter client-side
                            if (channelId.isNullOrEmpty() || post.channelId == channelId) {
                                posts.add(post)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("CommunityRTDB", "Failed to parse post ${child.key}: ${e.message}")
                    }
                }
                // Reverse for newest first (RTDB orderByChild is ascending)
                posts.reverse()
                android.util.Log.d("CommunityRTDB", "Posts snapshot: ${posts.size} posts")
                trySend(posts)
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("CommunityRTDB", "Posts listener cancelled: ${error.message}", error.toException())
                close(error.toException())
            }
        }

        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }.retry(Long.MAX_VALUE) { cause ->
        android.util.Log.w("CommunityRTDB", "Posts listener retrying after: ${cause.message}")
        delay(3000)
        true
    }

    /**
     * Get posts by a specific user. Used in profile page.
     */
    fun getUserPosts(userId: String): Flow<List<RealtimePost>> = callbackFlow {
        val query = postsRef.orderByChild("authorId").equalTo(userId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val posts = mutableListOf<RealtimePost>()
                for (child in snapshot.children) {
                    try {
                        child.getValue(RealtimePost::class.java)?.let { posts.add(it) }
                    } catch (_: Exception) { }
                }
                // Sort by createdAt desc (client-side, since we queried by authorId)
                posts.sortByDescending { it.createdAt }
                trySend(posts)
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
     * Create a new post. Image is uploaded to Firebase Storage first if provided.
     */
    suspend fun createPost(post: RealtimePost, imageUri: Uri? = null): String {
        try {
            android.util.Log.d("CommunityRTDB", "createPost START — author=${post.authorName}, dbUrl=${database.reference.toString()}")
            val imageUrl = if (imageUri != null) uploadImage(imageUri) else ""
            val key = postsRef.push().key ?: UUID.randomUUID().toString()
            val finalPost = post.copy(
                id = key,
                imageUrl = imageUrl,
                createdAt = System.currentTimeMillis()
            )
            android.util.Log.d("CommunityRTDB", "Writing post to path: ${postsRef.child(key).path}")
            postsRef.child(key).setValue(finalPost).await()
            android.util.Log.d("CommunityRTDB", "Post created successfully: id=$key, author=${post.authorName}")
            return key
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("CommunityRTDB", "createPost FAILED [${e.javaClass.simpleName}]: ${e.message}", e)
            throw e
        }
    }

    /**
     * Update post content (only author can call this).
     */
    suspend fun updatePost(postId: String, newContent: String, newCoinTag: String? = null) {
        try {
            val updates = mutableMapOf<String, Any>(
                "content" to newContent,
                "isEdited" to true,
                "updatedAt" to System.currentTimeMillis()
            )
            if (newCoinTag != null) {
                updates["coinTag"] = newCoinTag
            }
            postsRef.child(postId).updateChildren(updates).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    /**
     * Delete a post and all its comments.
     */
    suspend fun deletePost(postId: String) {
        try {
            // Delete comments for this post
            commentsRef.child(postId).removeValue().await()
            // Delete the post itself
            postsRef.child(postId).removeValue().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    /**
     * Toggle like on a post. Uses direct child set/remove — no transaction needed.
     */
    suspend fun toggleLike(postId: String, userId: String): Boolean {
        try {
            val likeRef = postsRef.child(postId).child("likes").child(userId)
            val snapshot = likeRef.get().await()
            val isCurrentlyLiked = snapshot.exists()

            if (isCurrentlyLiked) {
                likeRef.removeValue().await()
            } else {
                likeRef.setValue(true).await()
            }
            return !isCurrentlyLiked
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return false
        }
    }

    /**
     * Report a post with a reason.
     */
    suspend fun reportPost(postId: String, userId: String, reason: String) {
        try {
            val postRef = postsRef.child(postId)
            // Add userId to reportedBy
            postRef.child("reportedBy").child(userId).setValue(true).await()
            // Increment report count via transaction
            postRef.child("reportCount").runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentCount = currentData.getValue(Int::class.java) ?: 0
                    currentData.value = currentCount + 1
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) { }
            })
            // Add report reason (use push to add to list-like structure)
            val currentReasons = postRef.child("reportReasons").get().await()
            val reasons = mutableListOf<String>()
            for (child in currentReasons.children) {
                (child.getValue(String::class.java))?.let { reasons.add(it) }
            }
            if (!reasons.contains(reason)) {
                reasons.add(reason)
                postRef.child("reportReasons").setValue(reasons).await()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    // ─── COMMENTS ───────────────────────────────────────────────────────

    /**
     * Real-time stream of comments for a post, ordered by creation time.
     */
    fun getComments(postId: String): Flow<List<RealtimeComment>> = callbackFlow {
        val ref = commentsRef.child(postId).orderByChild("createdAt")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val comments = mutableListOf<RealtimeComment>()
                for (child in snapshot.children) {
                    try {
                        child.getValue(RealtimeComment::class.java)?.let { comments.add(it) }
                    } catch (_: Exception) { }
                }
                trySend(comments)
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("CommunityRTDB", "Comments listener cancelled for post=$postId: ${error.message}")
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }.retry(Long.MAX_VALUE) { cause ->
        delay(3000)
        true
    }

    /**
     * Add a comment to a post. Also increments commentCount on the post.
     */
    suspend fun addComment(postId: String, comment: RealtimeComment): String {
        try {
            val key = commentsRef.child(postId).push().key ?: UUID.randomUUID().toString()
            val finalComment = comment.copy(
                id = key,
                createdAt = System.currentTimeMillis()
            )
            commentsRef.child(postId).child(key).setValue(finalComment).await()

            // Increment comment count on parent post via transaction
            postsRef.child(postId).child("commentCount")
                .runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentCount = currentData.getValue(Int::class.java) ?: 0
                        currentData.value = currentCount + 1
                        return Transaction.success(currentData)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) { }
                })

            return key
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    /**
     * Delete a comment. Also decrements commentCount on the post.
     */
    suspend fun deleteComment(postId: String, commentId: String) {
        try {
            commentsRef.child(postId).child(commentId).removeValue().await()

            // Decrement comment count via transaction
            postsRef.child(postId).child("commentCount")
                .runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentCount = currentData.getValue(Int::class.java) ?: 0
                        currentData.value = maxOf(0, currentCount - 1)
                        return Transaction.success(currentData)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) { }
                })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    // ─── CHANNELS ───────────────────────────────────────────────────────

    /**
     * Get all community channels. Real-time stream.
     */
    fun getChannels(): Flow<List<RealtimeChannel>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val channels = mutableListOf<RealtimeChannel>()
                for (child in snapshot.children) {
                    try {
                        child.getValue(RealtimeChannel::class.java)?.let { channels.add(it) }
                    } catch (_: Exception) { }
                }
                android.util.Log.d("CommunityRTDB", "Channels snapshot: ${channels.size} channels")
                trySend(channels)
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("CommunityRTDB", "Channels listener cancelled: ${error.message}")
                close(error.toException())
            }
        }

        channelsRef.addValueEventListener(listener)
        awaitClose { channelsRef.removeEventListener(listener) }
    }.retry(Long.MAX_VALUE) { cause ->
        delay(3000)
        true
    }

    /**
     * Join or leave a channel.
     */
    suspend fun toggleChannelMembership(channelId: String, userId: String): Boolean {
        try {
            val memberRef = channelsRef.child(channelId).child("members").child(userId)
            val snapshot = memberRef.get().await()
            val isMember = snapshot.exists()

            if (isMember) {
                memberRef.removeValue().await()
                // Decrement member count
                channelsRef.child(channelId).child("memberCount")
                    .runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            val count = currentData.getValue(Int::class.java) ?: 0
                            currentData.value = maxOf(0, count - 1)
                            return Transaction.success(currentData)
                        }
                        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) { }
                    })
            } else {
                memberRef.setValue(true).await()
                // Increment member count
                channelsRef.child(channelId).child("memberCount")
                    .runTransaction(object : Transaction.Handler {
                        override fun doTransaction(currentData: MutableData): Transaction.Result {
                            val count = currentData.getValue(Int::class.java) ?: 0
                            currentData.value = count + 1
                            return Transaction.success(currentData)
                        }
                        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) { }
                    })
            }
            return !isMember
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return false
        }
    }

    /**
     * Initialize default channels if none exist.
     */
    suspend fun initializeDefaultChannels() {
        try {
            val snapshot = channelsRef.get().await()
            if (!snapshot.exists() || !snapshot.hasChildren()) {
                val defaults = listOf(
                    RealtimeChannel("general", "Genel Sohbet", "Kripto dünyası hakkında genel tartışmalar", "\uD83D\uDCAC", 0),
                    RealtimeChannel("bitcoin", "Bitcoin Kulübü", "Bitcoin analiz ve haberleri", "\u20BF", 0),
                    RealtimeChannel("altcoins", "Altcoin Avcıları", "Altcoin fırsatları ve analizleri", "\uD83D\uDC8E", 0),
                    RealtimeChannel("defi", "DeFi & Yield", "DeFi protokolleri ve yield farming", "\uD83C\uDF3E", 0),
                    RealtimeChannel("nft", "NFT & GameFi", "NFT koleksiyonları ve GameFi projeleri", "\uD83C\uDFA8", 0),
                    RealtimeChannel("signals", "Trade Sinyalleri", "Topluluk trade sinyalleri", "\uD83D\uDCCA", 0),
                    RealtimeChannel("turkish-market", "Türk Piyasası", "Türkiye kripto piyasası tartışmaları", "\uD83C\uDDF9\uD83C\uDDF7", 0)
                )
                val updates = mutableMapOf<String, Any>()
                defaults.forEach { channel ->
                    updates[channel.id] = channel
                }
                channelsRef.updateChildren(updates).await()
                android.util.Log.d("CommunityRTDB", "Default channels initialized")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("CommunityRTDB", "initializeDefaultChannels failed: ${e.message}", e)
        }
    }

    // ─── IMAGE UPLOAD ───────────────────────────────────────────────────

    /**
     * Upload an image to Firebase Storage and return the download URL.
     */
    suspend fun uploadImage(imageUri: Uri): String {
        return try {
            val fileName = "community/${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child(fileName)
            ref.putFile(imageUri).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ""
        }
    }

    // ─── MENTION SEARCH ─────────────────────────────────────────────────

    /**
     * Search for users by name prefix (for @mention autocomplete).
     * Searches across recent post authors.
     */
    suspend fun searchUsers(query: String): List<Pair<String, String>> {
        return try {
            if (query.length < 2) return emptyList()
            val snapshot = postsRef.orderByChild("createdAt").limitToLast(100).get().await()

            val users = mutableListOf<Pair<String, String>>()
            for (child in snapshot.children) {
                val name = child.child("authorName").getValue(String::class.java) ?: continue
                val id = child.child("authorId").getValue(String::class.java) ?: continue
                users.add(id to name)
            }

            users.distinctBy { it.first }
                .filter { it.second.lowercase().contains(query.lowercase()) }
                .take(5)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    /**
     * Get total post count for a user (for profile stats).
     */
    suspend fun getUserPostCount(userId: String): Int {
        return try {
            val snapshot = postsRef.orderByChild("authorId").equalTo(userId).get().await()
            snapshot.childrenCount.toInt()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            0
        }
    }
}
