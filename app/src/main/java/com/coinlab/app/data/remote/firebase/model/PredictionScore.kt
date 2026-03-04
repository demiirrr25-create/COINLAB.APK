package com.coinlab.app.data.remote.firebase.model

/**
 * v9.5 — Prediction Game Leaderboard Score
 *
 * Stored at: prediction_game/leaderboard/{userId}
 */
data class PredictionScore(
    val userId: String = "",
    val userName: String = "",
    val totalScore: Int = 0,
    val correctCount: Int = 0,
    val totalCount: Int = 0,
    val streak: Int = 0,
    val lastUpdated: Long = 0L
) {
    constructor() : this("", "", 0, 0, 0, 0, 0L)

    val accuracy: Double
        get() = if (totalCount > 0) (correctCount.toDouble() / totalCount) * 100 else 0.0
}
