package com.coinlab.app.data.remote.firebase

import com.coinlab.app.data.remote.firebase.model.TradingSignal
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
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
 * v9.5 — Social Trading Signal Repository
 *
 * Firebase RTDB-based trading signals.
 * Structure:
 *   trading_signals/{signalId} → TradingSignal
 */
@Singleton
class TradingSignalRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) {
    private val signalsRef = database.reference.child("trading_signals")

    /**
     * Create a new trading signal.
     */
    suspend fun createSignal(
        coinId: String,
        coinName: String,
        coinSymbol: String,
        signalType: String,
        entryPrice: Double,
        targetPrice: Double,
        stopLoss: Double,
        confidence: Int,
        description: String
    ): String {
        val currentUser = auth.currentUser ?: throw Exception("Not authenticated")
        val signalId = signalsRef.push().key ?: throw Exception("Failed to create signal")

        val signal = TradingSignal(
            id = signalId,
            authorId = currentUser.uid,
            authorName = currentUser.displayName ?: "Anonim",
            coinId = coinId,
            coinName = coinName,
            coinSymbol = coinSymbol,
            signalType = signalType,
            entryPrice = entryPrice,
            targetPrice = targetPrice,
            stopLoss = stopLoss,
            confidence = confidence,
            description = description,
            timestamp = System.currentTimeMillis(),
            status = "active"
        )

        signalsRef.child(signalId).setValue(signal).await()
        return signalId
    }

    /**
     * Get all trading signals (real-time, newest first).
     */
    fun getSignals(): Flow<List<TradingSignal>> = callbackFlow {
        val query = signalsRef.orderByChild("timestamp")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val signals = mutableListOf<TradingSignal>()
                for (child in snapshot.children) {
                    try {
                        child.getValue(TradingSignal::class.java)?.let { signals.add(it) }
                    } catch (_: Exception) { }
                }
                signals.sortByDescending { it.timestamp }
                trySend(signals)
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
     * Toggle like on a signal.
     */
    suspend fun toggleLike(signalId: String) {
        val userId = auth.currentUser?.uid ?: return
        val likeRef = signalsRef.child(signalId).child("likes").child(userId)

        val existing = likeRef.get().await()
        if (existing.exists()) {
            likeRef.removeValue().await()
        } else {
            likeRef.setValue(true).await()
        }
    }

    fun getCurrentUserId(): String = auth.currentUser?.uid ?: ""
}
