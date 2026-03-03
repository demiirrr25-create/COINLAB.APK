package com.coinlab.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "coins",
    indices = [
        Index(value = ["symbol"]),
        Index(value = ["marketCapRank"])
    ]
)
data class CoinEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val name: String,
    val image: String,
    val currentPrice: Double,
    val marketCap: Long,
    val marketCapRank: Int,
    val totalVolume: Double,
    val priceChangePercentage24h: Double,
    val priceChangePercentage7d: Double?,
    val circulatingSupply: Double,
    val totalSupply: Double?,
    val maxSupply: Double?,
    val ath: Double,
    val athChangePercentage: Double,
    val athDate: String,
    val atl: Double,
    val atlChangePercentage: Double,
    val atlDate: String,
    val lastUpdated: String,
    val sparklineData: String? = null, // JSON serialized sparkline
    val cachedAt: Long = System.currentTimeMillis()
)
