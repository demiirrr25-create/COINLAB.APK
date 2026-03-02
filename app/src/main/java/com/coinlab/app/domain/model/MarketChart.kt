package com.coinlab.app.domain.model

data class MarketChart(
    val prices: List<Pair<Long, Double>>,
    val marketCaps: List<Pair<Long, Double>>,
    val totalVolumes: List<Pair<Long, Double>>
)
