package com.coinlab.app.data.remote.firebase

import com.coinlab.app.data.remote.firebase.model.PredictionRound
import com.coinlab.app.data.remote.firebase.model.PredictionScore
import com.coinlab.app.data.remote.firebase.model.UserPrediction
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
 * v9.5 — Price Prediction Game Repository
 *
 * Firebase RTDB-based prediction game.
 * Structure:
 *   prediction_game/rounds/{roundId}                    → PredictionRound
 *   prediction_game/predictions/{roundId}/{userId}      → UserPrediction
 *   prediction_game/leaderboard/{userId}                → PredictionScore
 */
@Singleton
class PredictionGameRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) {
    private val gameRef = database.reference.child("prediction_game")
    private val roundsRef = gameRef.child("rounds")
    private val predictionsRef = gameRef.child("predictions")
    private val leaderboardRef = gameRef.child("leaderboard")

    /**
     * Get active prediction round (real-time).
     */
    fun getActiveRound(): Flow<PredictionRound?> = callbackFlow {
        val query = roundsRef.orderByChild("status").equalTo("active").limitToLast(1)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var activeRound: PredictionRound? = null
                for (child in snapshot.children) {
                    try {
                        activeRound = child.getValue(PredictionRound::class.java)
                    } catch (_: Exception) { }
                }
                trySend(activeRound)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }.retry(Long.MAX_VALUE) { delay(3000); true }

    /**
     * Create a new prediction round.
     */
    suspend fun createRound(
        coinId: String,
        coinName: String,
        coinSymbol: String,
        currentPrice: Double,
        durationMinutes: Int = 5
    ): String {
        val roundId = roundsRef.push().key ?: throw Exception("Failed to create round")
        val now = System.currentTimeMillis()

        val round = PredictionRound(
            id = roundId,
            coinId = coinId,
            coinName = coinName,
            coinSymbol = coinSymbol,
            startPrice = currentPrice,
            startTime = now,
            endTime = now + (durationMinutes * 60 * 1000L),
            status = "active",
            createdBy = auth.currentUser?.uid ?: ""
        )

        roundsRef.child(roundId).setValue(round).await()
        return roundId
    }

    /**
     * Make a prediction (UP or DOWN).
     */
    suspend fun makePrediction(roundId: String, prediction: String) {
        val currentUser = auth.currentUser ?: throw Exception("Not authenticated")

        val userPrediction = UserPrediction(
            userId = currentUser.uid,
            userName = currentUser.displayName ?: "Anonim",
            prediction = prediction,
            timestamp = System.currentTimeMillis()
        )

        predictionsRef.child(roundId).child(currentUser.uid).setValue(userPrediction).await()
    }

    /**
     * Get predictions for a round (real-time).
     */
    fun getRoundPredictions(roundId: String): Flow<List<UserPrediction>> = callbackFlow {
        val ref = predictionsRef.child(roundId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val predictions = mutableListOf<UserPrediction>()
                for (child in snapshot.children) {
                    try {
                        child.getValue(UserPrediction::class.java)?.let { predictions.add(it) }
                    } catch (_: Exception) { }
                }
                trySend(predictions)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }.retry(Long.MAX_VALUE) { delay(3000); true }

    /**
     * Complete a round and calculate results.
     */
    suspend fun completeRound(roundId: String, endPrice: Double) {
        val round = roundsRef.child(roundId).get().await()
            .getValue(PredictionRound::class.java) ?: return

        val direction = if (endPrice >= round.startPrice) "UP" else "DOWN"

        // Update round
        roundsRef.child(roundId).updateChildren(mapOf(
            "endPrice" to endPrice,
            "status" to "completed"
        )).await()

        // Score predictions
        val predictions = predictionsRef.child(roundId).get().await()
        for (child in predictions.children) {
            val pred = child.getValue(UserPrediction::class.java) ?: continue
            val isCorrect = pred.prediction == direction

            // Update prediction result
            predictionsRef.child(roundId).child(pred.userId)
                .child("isCorrect").setValue(isCorrect).await()

            // Update leaderboard
            updateLeaderboard(pred.userId, pred.userName, isCorrect)
        }
    }

    private suspend fun updateLeaderboard(userId: String, userName: String, isCorrect: Boolean) {
        val scoreRef = leaderboardRef.child(userId)
        val existing = scoreRef.get().await().getValue(PredictionScore::class.java)

        val newScore = if (existing != null) {
            PredictionScore(
                userId = userId,
                userName = userName,
                totalScore = existing.totalScore + if (isCorrect) 10 else 0,
                correctCount = existing.correctCount + if (isCorrect) 1 else 0,
                totalCount = existing.totalCount + 1,
                streak = if (isCorrect) existing.streak + 1 else 0,
                lastUpdated = System.currentTimeMillis()
            )
        } else {
            PredictionScore(
                userId = userId,
                userName = userName,
                totalScore = if (isCorrect) 10 else 0,
                correctCount = if (isCorrect) 1 else 0,
                totalCount = 1,
                streak = if (isCorrect) 1 else 0,
                lastUpdated = System.currentTimeMillis()
            )
        }

        scoreRef.setValue(newScore).await()
    }

    /**
     * Get leaderboard (real-time).
     */
    fun getLeaderboard(): Flow<List<PredictionScore>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val scores = mutableListOf<PredictionScore>()
                for (child in snapshot.children) {
                    try {
                        child.getValue(PredictionScore::class.java)?.let { scores.add(it) }
                    } catch (_: Exception) { }
                }
                scores.sortByDescending { it.totalScore }
                trySend(scores)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        leaderboardRef.addValueEventListener(listener)
        awaitClose { leaderboardRef.removeEventListener(listener) }
    }.retry(Long.MAX_VALUE) { delay(3000); true }

    fun getCurrentUserId(): String = auth.currentUser?.uid ?: ""
    fun getCurrentUserName(): String = auth.currentUser?.displayName ?: "Anonim"
}
