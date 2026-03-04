package com.coinlab.app.data.remote.firebase.model

/**
 * v9.5 — Social Trading Signal
 *
 * Stored at: trading_signals/{signalId}
 * signalType: "BUY" or "SELL"
 * confidence: 1-5 (star rating)
 */
data class TradingSignal(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val coinId: String = "",
    val coinName: String = "",
    val coinSymbol: String = "",
    val signalType: String = "BUY",
    val entryPrice: Double = 0.0,
    val targetPrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val confidence: Int = 3,
    val description: String = "",
    val timestamp: Long = 0L,
    val likes: Map<String, Boolean> = emptyMap(),
    val status: String = "active"
) {
    constructor() : this("", "", "", "", "", "", "BUY", 0.0, 0.0, 0.0, 3, "", 0L, emptyMap(), "active")

    val likeCount: Int get() = likes.size
    val isBuy: Boolean get() = signalType == "BUY"

    val potentialProfit: Double
        get() = if (entryPrice > 0) {
            ((targetPrice - entryPrice) / entryPrice) * 100
        } else 0.0

    val potentialLoss: Double
        get() = if (entryPrice > 0) {
            ((stopLoss - entryPrice) / entryPrice) * 100
        } else 0.0
}
