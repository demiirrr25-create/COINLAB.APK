package com.coinlab.app.domain.model

data class OhlcData(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)
