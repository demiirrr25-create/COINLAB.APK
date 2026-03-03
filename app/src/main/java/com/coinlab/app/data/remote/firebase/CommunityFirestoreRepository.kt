package com.coinlab.app.data.remote.firebase

import android.net.Uri
import com.coinlab.app.data.remote.firebase.model.FirestoreChannel
import com.coinlab.app.data.remote.firebase.model.FirestoreComment
import com.coinlab.app.data.remote.firebase.model.FirestorePost
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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
 * v8.2.2 — Community Firestore Repository
 *
 * All community data is stored in Firebase Firestore for real-time,
 * cross-device persistence. Posts are visible to all CoinLab users
 * and remain forever.
 *
 * Collections:
 *   posts/{postId}                 → FirestorePost
 *   posts/{postId}/comments/{cId}  → FirestoreComment
 *   channels/{channelId}           → FirestoreChannel
 */
@Singleton
class CommunityFirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {

    private val postsCollection = firestore.collection("posts")
    private val channelsCollection = firestore.collection("channels")

    // ─── POSTS ──────────────────────────────────────────────────────────

    /**
     * Real-time stream of all posts ordered by creation time (newest first).
     * Optionally filter by channelId.
     */
    fun getPosts(channelId: String? = null): Flow<List<FirestorePost>> = callbackFlow {
        var query: Query = postsCollection.orderBy("createdAt", Query.Direction.DESCENDING)

        if (!channelId.isNullOrEmpty()) {
            query = query.whereEqualTo("channelId", channelId)
        }

        val listener: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("CommunityRepo", "Posts listener error: ${error.message}", error)
                close(error)
                return@addSnapshotListener
            }
            val posts = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(FirestorePost::class.java)
            } ?: emptyList()
            android.util.Log.d("CommunityRepo", "Posts snapshot: ${posts.size} posts, fromCache=${snapshot?.metadata?.isFromCache}")
            trySend(posts)
        }

        awaitClose { listener.remove() }
    }.retry(3) { cause ->
        val isRetryable = cause is FirebaseFirestoreException &&
            cause.code in listOf(
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.ABORTED,
                FirebaseFirestoreException.Code.INTERNAL
            )
        if (isRetryable) {
            android.util.Log.w("CommunityRepo", "Posts listener retrying after: ${cause.message}")
            delay(2000)
        }
        isRetryable
    }

    /**
     * Get posts by a specific user. Used in profile page.
     */
    fun getUserPosts(userId: String): Flow<List<FirestorePost>> = callbackFlow {
        val query = postsCollection
            .whereEqualTo("authorId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("CommunityRepo", "UserPosts listener error: ${error.message}", error)
                close(error)
                return@addSnapshotListener
            }
            val posts = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(FirestorePost::class.java)
            } ?: emptyList()
            trySend(posts)
        }

        awaitClose { listener.remove() }
    }.retry(3) { cause ->
        val isRetryable = cause is FirebaseFirestoreException &&
            cause.code in listOf(
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.ABORTED,
                FirebaseFirestoreException.Code.INTERNAL
            )
        if (isRetryable) delay(2000)
        isRetryable
    }

    /**
     * Create a new post. Image is uploaded to Firebase Storage first if provided.
     */
    suspend fun createPost(post: FirestorePost, imageUri: Uri? = null): String {
        try {
            val imageUrl = if (imageUri != null) uploadImage(imageUri) else ""
            val docRef = postsCollection.document()
            val finalPost = post.copy(
                id = docRef.id,
                imageUrl = imageUrl,
                createdAt = null // Let @ServerTimestamp fill this on the server
            )
            docRef.set(finalPost).await()
            return docRef.id
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
                "updatedAt" to Timestamp.now()
            )
            if (newCoinTag != null) {
                updates["coinTag"] = newCoinTag
            }
            postsCollection.document(postId).update(updates).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    /**
     * Delete a post and all its comments sub-collection.
     */
    suspend fun deletePost(postId: String) {
        try {
            // Delete comments sub-collection first
            val commentsSnapshot = postsCollection.document(postId)
                .collection("comments").get().await()
            for (doc in commentsSnapshot.documents) {
                doc.reference.delete().await()
            }
            // Delete the post itself
            postsCollection.document(postId).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    /**
     * Toggle like on a post. Uses arrayUnion/arrayRemove for atomic operations.
     */
    suspend fun toggleLike(postId: String, userId: String): Boolean {
        try {
            val docRef = postsCollection.document(postId)
            val doc = docRef.get().await()
            val likes = doc.get("likes") as? List<*> ?: emptyList<String>()
            val isCurrentlyLiked = likes.contains(userId)

            if (isCurrentlyLiked) {
                docRef.update("likes", FieldValue.arrayRemove(userId)).await()
            } else {
                docRef.update("likes", FieldValue.arrayUnion(userId)).await()
            }
            return !isCurrentlyLiked // Return new state
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
            postsCollection.document(postId).update(
                mapOf(
                    "reportedBy" to FieldValue.arrayUnion(userId),
                    "reportReasons" to FieldValue.arrayUnion(reason),
                    "reportCount" to FieldValue.increment(1)
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    // ─── COMMENTS ───────────────────────────────────────────────────────

    /**
     * Real-time stream of comments for a post, ordered by creation time.
     */
    fun getComments(postId: String): Flow<List<FirestoreComment>> = callbackFlow {
        val commentsRef = postsCollection.document(postId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val listener = commentsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("CommunityRepo", "Comments listener error for post=$postId: ${error.message}", error)
                close(error)
                return@addSnapshotListener
            }
            val comments = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(FirestoreComment::class.java)
            } ?: emptyList()
            trySend(comments)
        }

        awaitClose { listener.remove() }
    }.retry(3) { cause ->
        val isRetryable = cause is FirebaseFirestoreException &&
            cause.code in listOf(
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.ABORTED,
                FirebaseFirestoreException.Code.INTERNAL
            )
        if (isRetryable) delay(2000)
        isRetryable
    }

    /**
     * Add a comment to a post. Also increments commentCount on the post.
     */
    suspend fun addComment(postId: String, comment: FirestoreComment): String {
        try {
            val commentsRef = postsCollection.document(postId).collection("comments")
            val docRef = commentsRef.document()
            val finalComment = comment.copy(id = docRef.id, createdAt = null) // Let @ServerTimestamp fill on server
            docRef.set(finalComment).await()

            // Increment comment count on parent post
            postsCollection.document(postId)
                .update("commentCount", FieldValue.increment(1)).await()

            return docRef.id
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
            postsCollection.document(postId)
                .collection("comments").document(commentId).delete().await()

            postsCollection.document(postId)
                .update("commentCount", FieldValue.increment(-1)).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    // ─── CHANNELS ───────────────────────────────────────────────────────

    /**
     * Get all community channels. Real-time stream.
     */
    fun getChannels(): Flow<List<FirestoreChannel>> = callbackFlow {
        val listener = channelsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("CommunityRepo", "Channels listener error: ${error.message}", error)
                close(error)
                return@addSnapshotListener
            }
            val channels = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(FirestoreChannel::class.java)
            } ?: emptyList()
            trySend(channels)
        }

        awaitClose { listener.remove() }
    }.retry(3) { cause ->
        val isRetryable = cause is FirebaseFirestoreException &&
            cause.code in listOf(
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.ABORTED,
                FirebaseFirestoreException.Code.INTERNAL
            )
        if (isRetryable) delay(2000)
        isRetryable
    }

    /**
     * Join or leave a channel.
     */
    suspend fun toggleChannelMembership(channelId: String, userId: String): Boolean {
        try {
            val docRef = channelsCollection.document(channelId)
            val doc = docRef.get().await()
            val members = doc.get("members") as? List<*> ?: emptyList<String>()
            val isMember = members.contains(userId)

            if (isMember) {
                docRef.update(
                    mapOf(
                        "members" to FieldValue.arrayRemove(userId),
                        "memberCount" to FieldValue.increment(-1)
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "members" to FieldValue.arrayUnion(userId),
                        "memberCount" to FieldValue.increment(1)
                    )
                ).await()
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
            val snapshot = channelsCollection.get().await()
            if (snapshot.isEmpty) {
                val defaults = listOf(
                    FirestoreChannel("general", "Genel Sohbet", "Kripto dünyası hakkında genel tartışmalar", "\uD83D\uDCAC", 0),
                    FirestoreChannel("bitcoin", "Bitcoin Kulübü", "Bitcoin analiz ve haberleri", "\u20BF", 0),
                    FirestoreChannel("altcoins", "Altcoin Avcıları", "Altcoin fırsatları ve analizleri", "\uD83D\uDC8E", 0),
                    FirestoreChannel("defi", "DeFi & Yield", "DeFi protokolleri ve yield farming", "\uD83C\uDF3E", 0),
                    FirestoreChannel("nft", "NFT & GameFi", "NFT koleksiyonları ve GameFi projeleri", "\uD83C\uDFA8", 0),
                    FirestoreChannel("signals", "Trade Sinyalleri", "Topluluk trade sinyalleri", "\uD83D\uDCCA", 0),
                    FirestoreChannel("turkish-market", "Türk Piyasası", "Türkiye kripto piyasası tartışmaları", "\uD83C\uDDF9\uD83C\uDDF7", 0)
                )
                defaults.forEach { channel ->
                    channelsCollection.document(channel.id).set(channel).await()
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            val snapshot = postsCollection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .get().await()

            val users = snapshot.documents
                .mapNotNull { doc ->
                    val name = doc.getString("authorName") ?: return@mapNotNull null
                    val id = doc.getString("authorId") ?: return@mapNotNull null
                    id to name
                }
                .distinctBy { it.first }
                .filter { it.second.lowercase().contains(query.lowercase()) }
                .take(5)

            users
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
            val snapshot = postsCollection
                .whereEqualTo("authorId", userId)
                .get().await()
            snapshot.size()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            0
        }
    }
}
