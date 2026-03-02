package com.coinlab.app.domain.model

data class PortfolioEntry(
    val id: Long = 0,
    val coinId: String,
    val coinSymbol: String,
    val coinName: String,
    val coinImage: String,
    val amount: Double,
    val buyPrice: Double,
    val currency: String = "USD",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: TransactionType = TransactionType.BUY
)

enum class TransactionType {
    BUY, SELL
}
