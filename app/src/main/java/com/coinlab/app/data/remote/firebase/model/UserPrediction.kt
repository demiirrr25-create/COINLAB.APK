package com.coinlab.app.data.remote.firebase.model

/**
 * v9.5 — User Prediction for a round
 *
 * Stored at: prediction_game/predictions/{roundId}/{userId}
 * prediction: "UP" or "DOWN"
 */
data class UserPrediction(
    val userId: String = "",
    val userName: String = "",
    val prediction: String = "",
    val timestamp: Long = 0L,
    val isCorrect: Boolean? = null
) {
    constructor() : this("", "", "", 0L, null)
}
