package com.coinlab.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for caching dynamic coin metadata from Binance + CoinGecko.
 * This replaces the hardcoded BinanceCoinMapper data.
 * TTL: 24 hours — coin metadata (name, image, rank) doesn't change frequently.
 */
@Entity(
    tableName = "coin_metadata",
    indices = [
        Index(value = ["symbol"]),
        Index(value = ["binanceSymbol"], unique = true),
        Index(value = ["marketCapRank"])
    ]
)
data class CoinMetadataEntity(
    @PrimaryKey val coinId: String,
    val symbol: String,
    val name: String,
    val image: String,
    val binanceSymbol: String,
    val marketCapRank: Int,
    val circulatingSupply: Double,
    val cachedAt: Long = System.currentTimeMillis()
)
