package com.coinlab.app.data.remote.firebase.model

/**
 * v9.5 — Price Prediction Game Round
 *
 * Stored at: prediction_game/rounds/{roundId}
 * Status: "active", "waiting", "completed"
 */
data class PredictionRound(
    val id: String = "",
    val coinId: String = "bitcoin",
    val coinName: String = "Bitcoin",
    val coinSymbol: String = "BTC",
    val startPrice: Double = 0.0,
    val endPrice: Double? = null,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val status: String = "active",
    val createdBy: String = ""
) {
    constructor() : this("", "bitcoin", "Bitcoin", "BTC", 0.0, null, 0L, 0L, "active", "")

    val isActive: Boolean get() = status == "active"
    val isCompleted: Boolean get() = status == "completed"
    val direction: String?
        get() {
            val end = endPrice ?: return null
            return if (end >= startPrice) "UP" else "DOWN"
        }
}
