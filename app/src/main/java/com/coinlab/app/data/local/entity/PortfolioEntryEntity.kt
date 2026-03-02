package com.coinlab.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portfolio_entries")
data class PortfolioEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val coinId: String,
    val coinSymbol: String,
    val coinName: String,
    val coinImage: String,
    val amount: Double,
    val buyPrice: Double,
    val currency: String = "USD",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "BUY" // BUY or SELL
)
