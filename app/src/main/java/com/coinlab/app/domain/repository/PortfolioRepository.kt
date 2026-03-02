package com.coinlab.app.domain.repository

import com.coinlab.app.domain.model.PortfolioEntry
import kotlinx.coroutines.flow.Flow

interface PortfolioRepository {
    fun getAllEntries(): Flow<List<PortfolioEntry>>

    fun getEntriesByCoin(coinId: String): Flow<List<PortfolioEntry>>

    suspend fun addEntry(entry: PortfolioEntry)

    suspend fun updateEntry(entry: PortfolioEntry)

    suspend fun deleteEntry(entryId: Long)

    fun getTotalPortfolioValue(currency: String = "usd"): Flow<Double>

    fun getPortfolioDistribution(): Flow<Map<String, Double>>
}
