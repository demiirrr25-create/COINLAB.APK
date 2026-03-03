package com.coinlab.app.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.coinlab.app.data.local.dao.CoinDao
import com.coinlab.app.domain.model.Coin

/**
 * PagingSource for loading coins from Room database in pages.
 * Works with pre-fetched data: coins are loaded from Binance → Room,
 * then this source reads from Room in batches for smooth infinite scroll.
 *
 * Page size: 50 coins per page
 * Total: up to 1000 coins
 */
class CoinPagingSource(
    private val coinDao: CoinDao
) : PagingSource<Int, Coin>() {

    override fun getRefreshKey(state: PagingState<Int, Coin>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Coin> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize
            val offset = page * pageSize

            val entities = coinDao.getCoinsPaged(limit = pageSize, offset = offset)
            val coins = entities.map { entity ->
                Coin(
                    id = entity.id,
                    symbol = entity.symbol,
                    name = entity.name,
                    image = entity.image,
                    currentPrice = entity.currentPrice,
                    marketCap = entity.marketCap,
                    marketCapRank = entity.marketCapRank,
                    totalVolume = entity.totalVolume,
                    priceChangePercentage24h = entity.priceChangePercentage24h,
                    priceChangePercentage7d = entity.priceChangePercentage7d,
                    circulatingSupply = entity.circulatingSupply,
                    totalSupply = entity.totalSupply,
                    maxSupply = entity.maxSupply,
                    ath = entity.ath,
                    athChangePercentage = entity.athChangePercentage,
                    athDate = entity.athDate,
                    atl = entity.atl,
                    atlChangePercentage = entity.atlChangePercentage,
                    atlDate = entity.atlDate,
                    sparklineIn7d = entity.sparklineData?.let { data ->
                        try { data.split(",").mapNotNull { it.trim().toDoubleOrNull() } }
                        catch (_: Exception) { null }
                    },
                    lastUpdated = entity.lastUpdated
                )
            }

            LoadResult.Page(
                data = coins,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (coins.isEmpty() || coins.size < pageSize) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
