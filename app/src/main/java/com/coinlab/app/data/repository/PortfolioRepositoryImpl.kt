package com.coinlab.app.data.repository

import com.coinlab.app.data.local.dao.CoinDao
import com.coinlab.app.data.local.dao.PortfolioDao
import com.coinlab.app.data.local.entity.PortfolioEntryEntity
import com.coinlab.app.data.remote.api.CoinGeckoApi
import com.coinlab.app.domain.model.PortfolioEntry
import com.coinlab.app.domain.model.TransactionType
import com.coinlab.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PortfolioRepositoryImpl @Inject constructor(
    private val portfolioDao: PortfolioDao,
    private val coinDao: CoinDao,
    private val api: CoinGeckoApi
) : PortfolioRepository {

    override fun getAllEntries(): Flow<List<PortfolioEntry>> {
        return portfolioDao.getAllEntries().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEntriesByCoin(coinId: String): Flow<List<PortfolioEntry>> {
        return portfolioDao.getEntriesByCoin(coinId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addEntry(entry: PortfolioEntry) {
        portfolioDao.insert(entry.toEntity())
    }

    override suspend fun updateEntry(entry: PortfolioEntry) {
        portfolioDao.update(entry.toEntity())
    }

    override suspend fun deleteEntry(entryId: Long) {
        portfolioDao.deleteById(entryId)
    }

    override fun getTotalPortfolioValue(currency: String): Flow<Double> = flow {
        try {
            val coinIds = portfolioDao.getDistinctCoinIds()
            if (coinIds.isEmpty()) {
                emit(0.0)
                return@flow
            }

            val idsParam = coinIds.joinToString(",")
            val prices = api.getSimplePrice(ids = idsParam, currencies = currency.lowercase())

            var totalValue = 0.0
            for (coinId in coinIds) {
                val netAmount = portfolioDao.getNetAmount(coinId) ?: 0.0
                val price = prices[coinId]?.get(currency.lowercase()) ?: 0.0
                totalValue += netAmount * price
            }

            emit(totalValue)
        } catch (e: Exception) {
            emit(0.0)
        }
    }

    override fun getPortfolioDistribution(): Flow<Map<String, Double>> = flow {
        try {
            val coinIds = portfolioDao.getDistinctCoinIds()
            val distribution = mutableMapOf<String, Double>()

            for (coinId in coinIds) {
                val netAmount = portfolioDao.getNetAmount(coinId) ?: 0.0
                if (netAmount > 0) {
                    distribution[coinId] = netAmount
                }
            }

            emit(distribution)
        } catch (e: Exception) {
            emit(emptyMap())
        }
    }
}

private fun PortfolioEntryEntity.toDomain(): PortfolioEntry {
    return PortfolioEntry(
        id = id,
        coinId = coinId,
        coinSymbol = coinSymbol,
        coinName = coinName,
        coinImage = coinImage,
        amount = amount,
        buyPrice = buyPrice,
        currency = currency,
        note = note,
        timestamp = timestamp,
        type = if (type == "SELL") TransactionType.SELL else TransactionType.BUY
    )
}

private fun PortfolioEntry.toEntity(): PortfolioEntryEntity {
    return PortfolioEntryEntity(
        id = id,
        coinId = coinId,
        coinSymbol = coinSymbol,
        coinName = coinName,
        coinImage = coinImage,
        amount = amount,
        buyPrice = buyPrice,
        currency = currency,
        note = note,
        timestamp = timestamp,
        type = type.name
    )
}
