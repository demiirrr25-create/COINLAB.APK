package com.coinlab.app.data.remote.dto

import com.coinlab.app.domain.model.MarketChart

data class MarketChartDto(
    val prices: List<List<Double>>?,
    val market_caps: List<List<Double>>?,
    val total_volumes: List<List<Double>>?
)

fun MarketChartDto.toDomain(): MarketChart {
    return MarketChart(
        prices = prices?.map { Pair(it[0].toLong(), it[1]) } ?: emptyList(),
        marketCaps = market_caps?.map { Pair(it[0].toLong(), it[1]) } ?: emptyList(),
        totalVolumes = total_volumes?.map { Pair(it[0].toLong(), it[1]) } ?: emptyList()
    )
}
