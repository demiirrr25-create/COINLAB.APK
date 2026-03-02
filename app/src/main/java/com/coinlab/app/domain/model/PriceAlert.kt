package com.coinlab.app.domain.model

data class PriceAlert(
    val id: Long = 0,
    val coinId: String,
    val coinSymbol: String,
    val coinName: String,
    val coinImage: String,
    val targetPrice: Double,
    val currency: String = "USD",
    val isAbove: Boolean,
    val isActive: Boolean = true,
    val isTriggered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
