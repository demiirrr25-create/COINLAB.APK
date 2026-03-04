package com.coinlab.app.domain.model

data class OhlcData(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0
)

data class MarketChart(
    val prices: List<Pair<Long, Double>>,
    val marketCaps: List<Pair<Long, Double>>,
    val totalVolumes: List<Pair<Long, Double>>,
    val ohlc: List<OhlcData> = emptyList()
)
